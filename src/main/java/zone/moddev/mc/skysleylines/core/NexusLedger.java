package zone.moddev.mc.skysleylines.core;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.skysleylines.api.*;
import static zone.moddev.mc.skysleylines.api.PowerResult.Status.*;

/** Sparse exact accounts. The owning server thread serializes all access. */
public final class NexusLedger {
    public record Balance(int capacity, MagicAmount raw, Map<ResourceLocation, Long> currencies, long tick) {
        public Balance {
            if (capacity <= 0 || capacity > 1_000_000 || tick < 0 || raw == null) throw new IllegalArgumentException("Invalid nexus balance");
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
    private final int rechargeTicks;
    private Map<NexusId, Balance> balances = new TreeMap<>();
    private Map<ResourceLocation, MagicAmount> rates = new TreeMap<>();
    private Set<ResourceLocation> active = Set.of();
    private long revision;
    private boolean transaction;
    public NexusLedger(int rechargeTicks) {
        if (rechargeTicks <= 0 || rechargeTicks > 1_728_000) throw new IllegalArgumentException("Invalid recovery time");
        this.rechargeTicks = rechargeTicks;
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
        if (transaction || rate.isZero() || rates.putIfAbsent(id, rate) != null) throw new IllegalArgumentException("Invalid/duplicate currency rate");
    }
    /** New currencies start at zero; known currencies keep their saved world rates. */
    public boolean bindCurrencies(Map<ResourceLocation, MagicAmount> configured) {
        if (transaction) throw new IllegalStateException("Nested ledger mutation");
        configured.forEach((id, rate) -> { Objects.requireNonNull(id); if (rate == null || rate.isZero()) throw new IllegalArgumentException("Invalid currency rate"); });
        boolean added = configured.keySet().stream().anyMatch(id -> !rates.containsKey(id));
        long next = added ? Math.incrementExact(revision) : revision;
        configured.forEach(rates::putIfAbsent);
        active = Set.copyOf(configured.keySet()); revision = next;
        return added;
    }
    public void restore(NexusId id, Balance balance) {
        if (transaction) throw new IllegalStateException("Nested ledger mutation");
        validate(balance, rates);
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
    private static void validate(Balance b, Map<ResourceLocation, MagicAmount> rates) {
        if (b.raw.add(converted(b, rates)).compareTo(MagicAmount.of(b.capacity)) > 0) throw new IllegalArgumentException("Account exceeds shared capacity");
    }
    private static AccountSnapshot snapshot(Balance b, Map<ResourceLocation, MagicAmount> rates) {
        return new AccountSnapshot(b.capacity, b.raw, b.currencies, b.raw.add(converted(b, rates)), b.tick);
    }
    private Balance projected(NexusId id, int capacity, long now) {
        if (capacity <= 0 || capacity > 1_000_000 || now < 0) throw new IllegalArgumentException("Invalid quote");
        Balance stored = balances.get(id);
        if (stored == null) return new Balance(capacity, MagicAmount.of(capacity), Map.of(), now);
        if (stored.capacity != capacity) throw new IllegalStateException("Saved capacity differs from immutable world profile");
        long tick = Math.max(now, stored.tick);
        long elapsed = Math.min(tick - stored.tick, rechargeTicks);
        MagicAmount roomForRaw = MagicAmount.of(capacity).subtract(converted(stored, rates));
        MagicAmount raw = stored.raw.add(MagicAmount.fraction(elapsed * capacity, rechargeTicks)).min(roomForRaw);
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
                validate(after, rates);
                var receipt = new PowerResult.Receipt(Math.incrementExact(revision), id, request, snapshot(before, rates), snapshot(after, rates));
                try { if (!hooks.before(receipt)) return PowerResult.failed(CANCELLED); }
                catch (RuntimeException failedCheck) { report(hooks, failedCheck); return PowerResult.failed(CHECK_FAILED); }
                balances.put(id, after); revision = receipt.revision();
                try { hooks.after(receipt); } catch (RuntimeException observer) { report(hooks, observer); }
                return PowerResult.committed(receipt);
            } catch (ArithmeticException overflow) { return PowerResult.failed(OVERFLOW); }
        } finally { transaction = false; }
    }
    private static void report(Hooks hooks, RuntimeException failure) {
        try { hooks.failure(failure); } catch (RuntimeException ignored) { /* Diagnostics cannot change committed payment semantics. */ }
    }
    /** Only full accounts containing entirely raw magic can be omitted. */
    public void pruneRecovered(long now) {
        if (transaction) throw new IllegalStateException("Nested ledger mutation");
        balances.entrySet().removeIf(e -> e.getValue().currencies.isEmpty() && projected(e.getKey(), e.getValue().capacity, now).raw.equals(MagicAmount.of(e.getValue().capacity)));
    }
    public RateMigration previewMigration(Map<ResourceLocation, MagicAmount> configured, long now) {
        var target = new TreeMap<>(rates);
        configured.forEach((id, rate) -> {
            if (!active.contains(id) || rate == null || rate.isZero()) throw new IllegalArgumentException("Invalid migration currency");
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
            validate(next, target);
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
            plan.newRates().values().forEach(rate -> { if (rate.isZero()) throw new IllegalArgumentException("Zero migration rate"); });
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
