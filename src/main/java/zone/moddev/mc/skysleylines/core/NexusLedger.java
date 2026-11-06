package zone.moddev.mc.skysleylines.core;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.skysleylines.api.*;
import static zone.moddev.mc.skysleylines.api.PowerResult.Status.*;

/** Sparse exact accounts. The owning server thread serializes all access. */
public final class NexusLedger {
    public static final int CAPACITY_SCALE_ONE = 10_000;
    private static final int MAX_CAPACITY = 1_000_000;

    /** Stored capacity is the unscaled capacity derived from the saved profile and nexus strength. */
    public record Balance(int capacity, MagicAmount raw, Map<ResourceLocation, Long> currencies, long tick) {
        public Balance {
            if (capacity <= 0 || capacity > MAX_CAPACITY || tick < 0 || raw == null) throw new IllegalArgumentException("Invalid nexus balance");
            var copy = new TreeMap<ResourceLocation, Long>();
            currencies.forEach((id, units) -> {
                Objects.requireNonNull(id);
                if (units == null || units < 0) throw new IllegalArgumentException("Invalid currency balance");
                if (units > 0) copy.put(id, units);
            });
            currencies = Collections.unmodifiableMap(copy);
        }
    }
    public interface Hooks {
        default boolean before(PowerResult.Receipt proposed) { return true; }
        default void after(PowerResult.Receipt receipt) {}
        default void failure(RuntimeException failure) {}
    }
    public static final Hooks NO_HOOKS = new Hooks() {};
    private int rechargeTicks;
    private int capacityScale;
    private Map<NexusId, Balance> balances = new TreeMap<>();
    private Map<ResourceLocation, MagicAmount> rates = new TreeMap<>();
    private Set<ResourceLocation> active = Set.of();
    private long revision;
    private boolean transaction;
    public NexusLedger(int rechargeTicks) {
        this(rechargeTicks, CAPACITY_SCALE_ONE);
    }
    public NexusLedger(int rechargeTicks, int capacityScale) {
        checkRechargeTicks(rechargeTicks);
        checkCapacityScale(capacityScale);
        this.rechargeTicks = rechargeTicks;
        this.capacityScale = capacityScale;
    }
    private static void checkRechargeTicks(int ticks) {
        if (ticks <= 0 || ticks > 1_728_000) throw new IllegalArgumentException("Invalid recovery time");
    }
    private static void checkCapacityScale(int scale) {
        if (scale <= 0 || scale > CAPACITY_SCALE_ONE * 100) throw new IllegalArgumentException("Invalid nexus capacity multiplier");
    }
    public int rechargeTicks() { return rechargeTicks; }
    public int capacityScale() { return capacityScale; }
    public double capacityMultiplier() { return (double) capacityScale / CAPACITY_SCALE_ONE; }
    public int scaledCapacity(int base) { return scaledCapacity(base, capacityScale); }
    private static int scaledCapacity(int base, int scale) {
        long rounded = ((long) base * scale + CAPACITY_SCALE_ONE / 2) / CAPACITY_SCALE_ONE;
        return (int) Math.clamp(rounded, 1, MAX_CAPACITY);
    }
    public Map<NexusId, Balance> entries() { return Collections.unmodifiableMap(new TreeMap<>(balances)); }
    public Map<ResourceLocation, MagicAmount> rates() { return Collections.unmodifiableMap(new TreeMap<>(rates)); }
    public Set<ResourceLocation> activeCurrencies() { return active; }
    public long revision() { return revision; }
    public void restoreRevision(long value) {
        if (transaction || value < 0) throw new IllegalArgumentException("Invalid ledger revision");
        revision = value;
    }
    public void restoreRate(ResourceLocation id, MagicAmount rate) {
        if (transaction) throw new IllegalStateException("Nested ledger mutation");
        LeySaveLimits.checkRate(id, rate);
        if (rates.putIfAbsent(id, rate) != null) throw new IllegalArgumentException("Duplicate currency rate: " + id);
    }
    /** New currencies start at zero; known currencies keep their saved world rates. */
    public boolean bindCurrencies(Map<ResourceLocation, MagicAmount> configured) {
        if (transaction) throw new IllegalStateException("Nested ledger mutation");
        configured.forEach(LeySaveLimits::checkRate);
        boolean added = configured.keySet().stream().anyMatch(id -> !rates.containsKey(id));
        long next = added ? Math.incrementExact(revision) : revision;
        configured.forEach(rates::putIfAbsent);
        active = Set.copyOf(configured.keySet()); revision = next;
        return added;
    }
    public void restore(NexusId id, Balance balance) {
        restore(id, balance, false);
    }
    public void restore(NexusId id, Balance balance, boolean allowsRetainedCharge) {
        if (transaction) throw new IllegalStateException("Nested ledger mutation");
        LeySaveLimits.checkAmount(balance.raw, "Raw balance for " + id);
        validate(balance, rates, allowsRetainedCharge ? MAX_CAPACITY : balance.capacity);
        if (balances.putIfAbsent(id, balance) != null) throw new IllegalArgumentException("Duplicate nexus balance");
    }
    private static MagicAmount converted(Balance b, Map<ResourceLocation, MagicAmount> rates) {
        MagicAmount result = MagicAmount.ZERO;
        for (var entry : b.currencies.entrySet()) {
            var rate = rates.get(entry.getKey());
            if (rate == null) throw new IllegalArgumentException("Balance references unknown currency: " + entry.getKey());
            result = result.add(MagicAmount.of(entry.getValue()).divide(rate));
        }
        return result;
    }
    private static void validate(Balance b, Map<ResourceLocation, MagicAmount> rates, int capacity) {
        if (b.raw.add(converted(b, rates)).compareTo(MagicAmount.of(capacity)) > 0) throw new IllegalArgumentException("Account exceeds shared capacity");
    }
    private AccountSnapshot snapshot(Balance b, Map<ResourceLocation, MagicAmount> rates) {
        return new AccountSnapshot(scaledCapacity(b.capacity), b.raw, b.currencies, b.raw.add(converted(b, rates)), b.tick);
    }
    private MagicAmount rawLimit(int capacity, Balance balance) {
        var maximum = MagicAmount.of(capacity);
        var reserved = converted(balance, rates);
        return maximum.compareTo(reserved) > 0 ? maximum.subtract(reserved) : MagicAmount.ZERO;
    }
    private Balance projected(NexusId id, int capacity, long now) {
        if (capacity <= 0 || capacity > MAX_CAPACITY || now < 0) throw new IllegalArgumentException("Invalid quote");
        int effectiveCapacity = scaledCapacity(capacity);
        Balance stored = balances.get(id);
        if (stored == null) return new Balance(capacity, MagicAmount.of(effectiveCapacity), Map.of(), now);
        if (stored.capacity != capacity) throw new IllegalStateException("Saved capacity differs from immutable world profile");
        long tick = Math.max(now, stored.tick);
        if (tick == stored.tick) return stored;
        long elapsed = Math.min(tick - stored.tick, rechargeTicks);
        MagicAmount roomForRaw = rawLimit(effectiveCapacity, stored);
        MagicAmount raw = stored.raw;
        if (raw.compareTo(roomForRaw) < 0) {
            raw = raw.add(MagicAmount.fraction(elapsed * effectiveCapacity, rechargeTicks)).min(roomForRaw);
        }
        return new Balance(capacity, raw, stored.currencies, tick);
    }
    public AccountSnapshot quote(NexusId id, int capacity, long now) { return snapshot(projected(id, capacity, now), rates); }
    public PowerResult transact(NexusId id, int capacity, long now, PowerRequest request, Hooks hooks) {
        if (transaction) return PowerResult.failed(REENTRANT);
        transaction = true;
        try {
            if (request.amount().isZero() || (request.operation() != PowerRequest.Operation.CONVERT && !request.amount().isWhole())) return PowerResult.failed(INVALID_AMOUNT);
            var rate = rates.get(request.currency());
            if (rate == null) return PowerResult.failed(UNKNOWN_CURRENCY);
            if (!active.contains(request.currency()) && request.operation() != PowerRequest.Operation.RETURN_TO_RAW) return PowerResult.failed(INACTIVE_CURRENCY);
            Balance before = projected(id, capacity, now);
            MagicAmount raw = before.raw;
            var currencies = new TreeMap<>(before.currencies);
            long held = currencies.getOrDefault(request.currency(), 0L);
            try {
                switch (request.operation()) {
                    case CONVERT -> {
                        MagicAmount output = request.amount().multiply(rate);
                        if (!output.isWhole()) return PowerResult.failed(NON_INTEGRAL_OUTPUT);
                        if (raw.compareTo(request.amount()) < 0) return PowerResult.failed(INSUFFICIENT_RAW);
                        long units = output.wholeLongExact();
                        currencies.put(request.currency(), Math.addExact(held, units));
                        raw = raw.subtract(request.amount());
                    }
                    case SPEND, RETURN_TO_RAW -> {
                        long units = request.amount().wholeLongExact();
                        if (held < units) return PowerResult.failed(INSUFFICIENT_CURRENCY);
                        currencies.put(request.currency(), held - units);
                        if (request.operation() == PowerRequest.Operation.RETURN_TO_RAW) raw = raw.add(request.amount().divide(rate));
                    }
                }
                Balance after = new Balance(capacity, raw, currencies, before.tick);
                LeySaveLimits.checkAmount(after.raw, "Raw balance for " + id);
                var previous = snapshot(before, rates);
                var next = snapshot(after, rates);
                if (next.total().compareTo(previous.total()) > 0) throw new IllegalStateException("Transaction increased total nexus charge");
                var receipt = new PowerResult.Receipt(Math.incrementExact(revision), id, request, previous, next);
                try { if (!hooks.before(receipt)) return PowerResult.failed(CANCELLED); }
                catch (RuntimeException failedCheck) { report(hooks, failedCheck); return PowerResult.failed(CHECK_FAILED); }
                balances.put(id, after); revision = receipt.revision();
                try { hooks.after(receipt); } catch (RuntimeException observer) { report(hooks, observer); }
                return PowerResult.committed(receipt);
            } catch (ArithmeticException overflow) { return PowerResult.failed(OVERFLOW); }
        } finally { transaction = false; }
    }
    /** Keep node, pool, and drop observers under the same mutation guard. */
    public <T> T exclusive(T rejected, java.util.function.Supplier<T> action) {
        if (transaction) return rejected;
        transaction = true;
        try { return action.get(); } finally { transaction = false; }
    }

    public boolean mutationInProgress() { return transaction; }

    public MagicAmount savedRate(ResourceLocation currency) { return rates.get(currency); }

    /** Called only inside exclusive, after all companion changes have been prepared. */
    public Runnable prepareDeposit(NexusId id, int capacity, long now, ResourceLocation currency, long units) {
        if (!transaction) throw new IllegalStateException("Deposit requires a coordinated mutation");
        Balance before = projected(id, capacity, now);
        var currencies = new TreeMap<>(before.currencies);
        currencies.put(currency, Math.addExact(currencies.getOrDefault(currency, 0L), units));
        Balance after = new Balance(capacity, before.raw, currencies, before.tick);
        LeySaveLimits.checkAmount(after.raw, "Raw balance for " + id);
        validate(after, rates, scaledCapacity(capacity));
        long next = Math.incrementExact(revision);
        return () -> {
            balances.put(id, after);
            revision = next;
        };
    }

    public enum RecoveryStatus { SUCCESS, REENTRANT, UNKNOWN_CURRENCY, INVALID_AMOUNT, INSUFFICIENT_CURRENCY,
        CAPACITY, OVERFLOW, CANCELLED, CHECK_FAILED }

    public interface RecoveryHooks {
        boolean before(NexusRecoveryEvent.Receipt receipt);
        void after(NexusRecoveryEvent.Receipt receipt);
        void failure(RuntimeException failure);
    }

    public RecoveryStatus recover(NexusId from, net.minecraft.core.BlockPos fromPos, NexusId to,
                                  net.minecraft.core.BlockPos toPos, int targetCapacity, long now,
                                  ResourceLocation currency, long units, PowerRequest.Consumer consumer, RecoveryHooks hooks) {
        return exclusive(RecoveryStatus.REENTRANT, () -> {
            if (units <= 0 || from.equals(to)) return RecoveryStatus.INVALID_AMOUNT;
            var rate = rates.get(currency);
            if (rate == null) return RecoveryStatus.UNKNOWN_CURRENCY;
            var source = balances.get(from);
            if (source == null || source.currencies.getOrDefault(currency, 0L) < units) return RecoveryStatus.INSUFFICIENT_CURRENCY;
            var target = projected(to, targetCapacity, now);
            var targetSnapshot = snapshot(target, rates);
            if (targetSnapshot.total().add(MagicAmount.of(units).divide(rate)).compareTo(MagicAmount.of(targetSnapshot.capacity())) > 0)
                return RecoveryStatus.CAPACITY;
            try {
                var debited = new TreeMap<>(source.currencies);
                debited.compute(currency, (id, held) -> held - units);
                var credited = new TreeMap<>(target.currencies);
                credited.merge(currency, units, Math::addExact);
                var sourceAfter = new Balance(source.capacity, source.raw, debited, source.tick);
                var targetAfter = new Balance(targetCapacity, target.raw, credited, target.tick);
                LeySaveLimits.checkAmount(targetAfter.raw, "Raw balance for " + to);
                var receipt = new NexusRecoveryEvent.Receipt(Math.incrementExact(revision), currency, units, consumer,
                        new NexusRecoveryEvent.Account(from, fromPos, snapshot(source, rates), snapshot(sourceAfter, rates)),
                        new NexusRecoveryEvent.Account(to, toPos, targetSnapshot, snapshot(targetAfter, rates)));
                try {
                    if (!hooks.before(receipt)) return RecoveryStatus.CANCELLED;
                } catch (RuntimeException failure) {
                    hooks.failure(failure);
                    return RecoveryStatus.CHECK_FAILED;
                }
                balances.put(from, sourceAfter);
                balances.put(to, targetAfter);
                revision = receipt.revision();
                try { hooks.after(receipt); } catch (RuntimeException failure) { hooks.failure(failure); }
                return RecoveryStatus.SUCCESS;
            } catch (ArithmeticException failure) {
                return RecoveryStatus.OVERFLOW;
            }
        });
    }

    private static void report(Hooks hooks, RuntimeException failure) {
        try { hooks.failure(failure); } catch (RuntimeException ignored) { /* Diagnostics cannot change committed payment semantics. */ }
    }
    /** Only full accounts containing entirely raw magic can be omitted. */
    public void pruneRecovered(long now) {
        if (transaction) throw new IllegalStateException("Nested ledger mutation");
        balances.entrySet().removeIf(e -> e.getValue().currencies.isEmpty()
                && projected(e.getKey(), e.getValue().capacity, now).raw.equals(MagicAmount.of(scaledCapacity(e.getValue().capacity))));
    }
    /** Settle at the current rate and commit the new interval only after a successful backup. */
    public boolean changeRechargeTicks(int ticks, long now, Runnable backup) {
        return changeLimits(ticks, capacityScale, now, backup);
    }

    /** Settle using the previous limits before preparing the replacement ledger. */
    public boolean changeLimits(int ticks, int scale, long now, Runnable backup) {
        if (transaction) throw new IllegalStateException("Nested ledger mutation");
        checkRechargeTicks(ticks);
        checkCapacityScale(scale);
        if (ticks == rechargeTicks && scale == capacityScale) return false;
        transaction = true;
        try {
            long nextRevision = Math.incrementExact(revision);
            var next = new TreeMap<NexusId, Balance>();
            balances.forEach((id, stored) -> {
                Balance settled = projected(id, stored.capacity, now);
                int newCapacity = scaledCapacity(stored.capacity, scale);
                if (newCapacity < scaledCapacity(stored.capacity)) {
                    var raw = settled.raw.min(rawLimit(newCapacity, settled));
                    settled = new Balance(settled.capacity, raw, settled.currencies, settled.tick);
                }
                LeySaveLimits.checkAmount(settled.raw, "Raw balance for " + id);
                next.put(id, settled);
            });
            backup.run();
            balances = next;
            rechargeTicks = ticks;
            capacityScale = scale;
            revision = nextRevision;
            return true;
        } finally {
            transaction = false;
        }
    }
    public RateMigration previewMigration(Map<ResourceLocation, MagicAmount> configured, long now) {
        var target = new TreeMap<>(rates);
        configured.forEach((id, rate) -> {
            if (!active.contains(id)) throw new IllegalArgumentException("Inactive migration currency: " + id);
            LeySaveLimits.checkRate(id, rate);
            target.put(id, rate);
        });
        var snapshots = new TreeMap<NexusId, AccountSnapshot>();
        migrated(target, now).forEach((id, b) -> snapshots.put(id, snapshot(b, target)));
        return new RateMigration(revision, rates, target, snapshots);
    }
    private Map<NexusId, Balance> migrated(Map<ResourceLocation, MagicAmount> target, long now) {
        Map<NexusId, Balance> result = new TreeMap<>();
        for (var entry : balances.entrySet()) {
            Balance old = projected(entry.getKey(), entry.getValue().capacity, now);
            MagicAmount raw = old.raw;
            var currencies = new TreeMap<ResourceLocation, Long>();
            for (var currency : old.currencies.entrySet()) {
                MagicAmount value = MagicAmount.of(currency.getValue()).divide(rates.get(currency.getKey()));
                long units = value.multiply(target.get(currency.getKey())).floorLongExact();
                raw = raw.add(value.subtract(MagicAmount.of(units).divide(target.get(currency.getKey()))));
                if (units > 0) currencies.put(currency.getKey(), units);
            }
            Balance next = new Balance(old.capacity, raw, currencies, old.tick);
            LeySaveLimits.checkAmount(next.raw, "Migrated raw balance for " + entry.getKey());
            if (!snapshot(old, rates).total().equals(snapshot(next, target).total())) throw new IllegalStateException("Migration changed energy");
            result.put(entry.getKey(), next);
        }
        return result;
    }
    /** Prepare everything and run backup/check before swapping accounts and rates together. */
    public boolean applyMigration(RateMigration plan, long now, MigrationHooks hooks) {
        if (transaction) return false;
        transaction = true;
        try {
            if (plan.revision() != revision || !plan.oldRates().equals(rates) || !plan.newRates().keySet().equals(rates.keySet())) return false;
            plan.newRates().forEach(LeySaveLimits::checkRate);
            var next = migrated(plan.newRates(), now);
            long nextRevision = Math.incrementExact(revision);
            var accounts = new TreeMap<NexusId, AccountSnapshot>();
            next.forEach((id, b) -> accounts.put(id, snapshot(b, plan.newRates())));
            var effective = new RateMigration(revision, rates, plan.newRates(), accounts);
            if (!hooks.before(effective)) return false;
            hooks.backup();
            rates = new TreeMap<>(plan.newRates()); balances = next; revision = nextRevision;
            try { hooks.after(effective); } catch (RuntimeException observer) {
                try { hooks.failure(observer); } catch (RuntimeException ignored) { /* Already committed. */ }
            }
            return true;
        } finally { transaction = false; }
    }
    public interface MigrationHooks {
        boolean before(RateMigration plan);
        void backup();
        void after(RateMigration plan);
        void failure(RuntimeException failure);
    }
}
