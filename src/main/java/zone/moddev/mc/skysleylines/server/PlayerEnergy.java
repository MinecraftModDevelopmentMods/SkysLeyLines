package zone.moddev.mc.skysleylines.server;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import zone.moddev.mc.skysleylines.api.*;
import zone.moddev.mc.skysleylines.core.LeySaveLimits;
import zone.moddev.mc.skysleylines.core.NexusLedger;
import zone.moddev.mc.skysleylines.server.PlayerEnergyData.Drop;
import zone.moddev.mc.skysleylines.server.PlayerEnergyData.StoredPool;
import static zone.moddev.mc.skysleylines.api.PlayerPowerService.Status.*;

public final class PlayerEnergy implements PlayerPowerService {
    private static final PowerRequest.Consumer DEATH = system("death");
    private static final PowerRequest.Consumer PICKUP = system("pickup");
    static final Settings ABSENT = new Settings(false, 0, null);
    private final MinecraftServer server;
    private final LeyWorldData world;
    private final PlayerEnergyData data;
    private final NexusLedger ledger;
    private final EnergyJobs jobs;
    private final Map<UUID, LivingDeathEvent> deaths = new HashMap<>();
    private final Map<UUID, EnergyOrb> orbs = new HashMap<>();
    private final NavigableSet<UUID> expirations;
    private PlayerEnergyPolicy policy;
    private UUID lastProjection;

    PlayerEnergy(MinecraftServer server, LeyWorldData world) {
        this.server = server;
        this.world = world;
        data = world.playerEnergy;
        ledger = world.ledger();
        jobs = new EnergyJobs(this);
        expirations = new TreeSet<>(Comparator.<UUID>comparingLong(id -> data.drops.get(id).expires()).thenComparing(Comparator.naturalOrder()));
        expirations.addAll(data.drops.keySet());
        try { policy = PlayerEnergyPolicy.read(server); }
        catch (IOException | RuntimeException failure) { throw new IllegalStateException("Cannot load player energy policy", failure); }
    }

    static PowerRequest.Consumer system(String path) {
        return new PowerRequest.Consumer(ResourceLocation.fromNamespaceAndPath("skysleylines", path));
    }
    void checkThread() {
        if (!server.isSameThread()) throw new IllegalStateException("Player power requires the server thread");
    }
    long now() { return server.overworld().getGameTime(); }
    boolean busy() { return ledger.mutationInProgress(); }
    boolean currencyActive(ResourceLocation currency) { return ledger.activeCurrencies().contains(currency); }
    MagicAmount rate(ResourceLocation currency) { return ledger.savedRate(currency); }
    ServerPlayer online(UUID id) { return server.getPlayerList().getPlayer(id); }
    ServerLevel level(ResourceLocation id) { return server.getLevel(ResourceKey.create(Registries.DIMENSION, id)); }

    void reload() {
        try {
            var updated = PlayerEnergyPolicy.read(server);
            if (!policy.sameRules(updated)) {
                policy = updated;
                jobs.policyChanged();
            }
        } catch (IOException | RuntimeException failure) {
            LogUtils.getLogger().error("Keeping previous player energy policy after failed reload", failure);
        }
    }

    PlayerEnergyPolicy.Rule rule(UUID player, ResourceLocation currency) {
        var stored = player == null ? null : data.pool(player, currency);
        return policy.rule(currency, stored == null ? ABSENT : stored.settings());
    }
    private Pool pool(ResourceLocation currency, StoredPool stored) {
        if (stored == null) return new Pool(0, ABSENT, false);
        var settings = policy.rule(currency, stored.settings()).settings();
        return new Pool(stored.units(), settings, settings.enabled() && ledger.activeCurrencies().contains(currency));
    }
    Pool pool(UUID player, ResourceLocation currency) { return pool(currency, data.pool(player, currency)); }
    long room(UUID player, ResourceLocation currency) {
        Pool pool = pool(player, currency);
        return Math.max(0L, pool.settings().capacity() - pool.balance());
    }

    @Override public Snapshot inspect(UUID player) {
        checkThread();
        Objects.requireNonNull(player);
        var result = new TreeMap<ResourceLocation, Pool>();
        data.players.getOrDefault(player, Map.of()).forEach((currency, stored) -> result.put(currency, pool(currency, stored)));
        return new Snapshot(player, result);
    }

    private Result guarded(Supplier<Result> action) {
        checkThread();
        return ledger.exclusive(Result.of(REENTRANT), () -> {
            try { return action.get(); }
            catch (ArithmeticException failure) { return Result.of(OVERFLOW); }
        });
    }

    @Override public Result configure(UUID player, ResourceLocation currency, Settings settings, PowerRequest.Consumer consumer) {
        return guarded(() -> {
            if (rate(currency) == null) return Result.of(UNKNOWN_CURRENCY);
            if (settings.autoRate() != null) LeySaveLimits.checkAmount(settings.autoRate(), "Player recharge rate");
            return update(player, currency, new StoredPool(settings, pool(player, currency).balance()), Operation.CONFIGURE,
                    MagicAmount.ZERO, consumer);
        });
    }
    @Override public Result setBalance(UUID player, ResourceLocation currency, long units, PowerRequest.Consumer consumer) {
        return guarded(() -> setBalanceLocked(player, currency, units, Operation.ADJUST, consumer));
    }
    @Override public Result adjust(UUID player, ResourceLocation currency, long delta, PowerRequest.Consumer consumer) {
        return guarded(() -> setBalanceLocked(player, currency, Math.addExact(pool(player, currency).balance(), delta), Operation.ADJUST, consumer));
    }
    @Override public Result spend(UUID player, ResourceLocation currency, long units, PowerRequest.Consumer consumer) {
        return guarded(() -> {
            if (units <= 0) return Result.of(INVALID_AMOUNT);
            if (rate(currency) == null) return Result.of(UNKNOWN_CURRENCY);
            var pool = pool(player, currency);
            if (!pool.active()) return Result.of(DORMANT);
            if (units > pool.balance()) return Result.of(INSUFFICIENT_POWER);
            return setBalanceLocked(player, currency, pool.balance() - units, Operation.SPEND, consumer);
        });
    }
    private Result setBalanceLocked(UUID player, ResourceLocation currency, long units, Operation operation, PowerRequest.Consumer consumer) {
        if (units < 0) return Result.of(INVALID_AMOUNT);
        if (rate(currency) == null) return Result.of(UNKNOWN_CURRENCY);
        var before = pool(player, currency);
        if (data.pool(player, currency) == null || (units > before.balance() && !before.active())) return Result.of(DORMANT);
        if (units > before.balance() && units > before.settings().capacity()) return Result.of(CAPACITY);
        return update(player, currency, new StoredPool(data.pool(player, currency).settings(), units), operation,
                MagicAmount.of(Math.abs(units - before.balance())), consumer);
    }
    private Result update(UUID player, ResourceLocation currency, StoredPool next, Operation operation, MagicAmount amount,
                          PowerRequest.Consumer consumer) {
        var changes = Map.of(player, change(player, currency, next));
        var receipt = receipt(operation, currency, amount, consumer, null, null, changes, null, null, null);
        if (next.equals(data.pool(player, currency))) return Result.committed(receipt);
        return commit(receipt, () -> data.put(player, currency, next));
    }

    private Change change(UUID player, ResourceLocation currency, StoredPool next) {
        return new Change(player, pool(player, currency), pool(currency, next));
    }
    private StoredPool withBalance(UUID player, ResourceLocation currency, long units) {
        return new StoredPool(data.pool(player, currency).settings(), units);
    }
    private static Receipt receipt(Operation operation, ResourceLocation currency, MagicAmount amount,
                                   PowerRequest.Consumer consumer, NexusId nexus, UUID drop, Map<UUID, Change> changes,
                                   AccountSnapshot before, AccountSnapshot after, GlobalPos anchor) {
        return new Receipt(operation, currency, amount, Objects.requireNonNull(consumer), Optional.ofNullable(nexus),
                Optional.ofNullable(drop), changes, Optional.ofNullable(before), Optional.ofNullable(after), Optional.ofNullable(anchor));
    }
    private Status before(Receipt receipt) {
        try { return NeoForge.EVENT_BUS.post(new PlayerPowerEvent.Pre(server, receipt)).isCanceled() ? CANCELLED : SUCCESS; }
        catch (RuntimeException failure) {
            LogUtils.getLogger().error("Player power protection check failed", failure);
            return CHECK_FAILED;
        }
    }
    private void post(Receipt receipt) {
        try { NeoForge.EVENT_BUS.post(new PlayerPowerEvent.Post(server, receipt)); }
        catch (RuntimeException failure) { LogUtils.getLogger().error("Player power observer failed after commit", failure); }
    }
    private Status prepareSave() {
        try { world.preparePlayerSave(); return SUCCESS; }
        catch (RuntimeException failure) {
            LogUtils.getLogger().error("Player power storage preparation failed", failure);
            return STORAGE_FAILED;
        }
    }
    private Result commit(Receipt receipt, Runnable change) {
        Status check = before(receipt);
        if (check != SUCCESS) return Result.of(check);
        check = prepareSave();
        if (check != SUCCESS) return Result.of(check);
        change.run();
        world.setDirty();
        post(receipt);
        return Result.committed(receipt);
    }

    @Override public Submission withdraw(ServerPlayer player, ResourceLocation currency, long units, Timing timing, PowerRequest.Consumer consumer) {
        return jobs.submit(Operation.WITHDRAW, player, null, player.serverLevel(), player.blockPosition(), currency, units, timing, consumer);
    }
    @Override public Submission deposit(ServerPlayer player, ResourceLocation currency, long units, Timing timing, PowerRequest.Consumer consumer) {
        return jobs.submit(Operation.DEPOSIT, player, null, player.serverLevel(), player.blockPosition(), currency, units, timing, consumer);
    }
    @Override public Submission transfer(ServerPlayer from, ServerPlayer to, ResourceLocation currency, long units, Timing timing, PowerRequest.Consumer consumer) {
        return jobs.submit(Operation.TRANSFER, from, to, null, null, currency, units, timing, consumer);
    }
    @Override public Submission convert(ServerLevel level, BlockPos pos, ResourceLocation currency, MagicAmount raw, Timing timing, PowerRequest.Consumer consumer) {
        return jobs.submit(Operation.CONVERT, null, null, level, pos, currency, raw, timing, consumer);
    }
    @Override public Optional<Job> job(UUID id) { checkThread(); return jobs.read(id); }
    @Override public boolean cancel(UUID id) { checkThread(); return !busy() && jobs.cancel(id, CANCELLED); }

    Result movePlayers(UUID from, UUID to, ResourceLocation currency, long units, PowerRequest.Consumer consumer) {
        return guarded(() -> {
            var debit = withBalance(from, currency, pool(from, currency).balance() - units);
            var credit = withBalance(to, currency, pool(to, currency).balance() + units);
            var receipt = receipt(Operation.TRANSFER, currency, MagicAmount.of(units), consumer, null, null,
                    Map.of(from, change(from, currency, debit), to, change(to, currency, credit)), null, null, null);
            return commit(receipt, () -> {
                data.put(from, currency, debit);
                data.put(to, currency, credit);
            });
        });
    }

    Result deposit(UUID player, GlobalPos anchor, NexusSnapshot node, ResourceLocation currency, long units,
                   PowerRequest.Consumer consumer) {
        return guarded(() -> {
            var id = node.nexus().id();
            var debit = withBalance(player, currency, pool(player, currency).balance() - units);
            var current = node.account();
            var amounts = new TreeMap<>(current.currencies());
            amounts.merge(currency, units, Math::addExact);
            var amount = MagicAmount.of(units);
            var next = new AccountSnapshot(current.capacity(), current.raw(), amounts,
                    current.total().add(amount.divide(rate(currency))), current.tick());
            Runnable deposit = ledger.prepareDeposit(id, node.nexus().capacity(world.profile()), now(), currency, units);
            var receipt = receipt(Operation.DEPOSIT, currency, amount, consumer, id, null,
                    Map.of(player, change(player, currency, debit)), current, next, anchor);
            return commit(receipt, () -> {
                deposit.run();
                data.put(player, currency, debit);
            });
        });
    }

    Result useNode(Operation operation, UUID player, ServerLevel level, BlockPos pos, NexusSnapshot node,
                   ResourceLocation currency, MagicAmount amount, PowerRequest.Consumer consumer) {
        boolean withdraw = operation == Operation.WITHDRAW;
        var request = new PowerRequest(pos.getX(), pos.getY(), pos.getZ(),
                withdraw ? PowerRequest.Operation.SPEND : PowerRequest.Operation.CONVERT, currency, amount, consumer);
        var credit = withdraw ? withBalance(player, currency, pool(player, currency).balance() + amount.wholeLongExact()) : null;
        var changes = withdraw ? Map.of(player, change(player, currency, credit)) : Map.<UUID, Change>of();
        class Checks implements NexusLedger.Hooks {
            Receipt receipt;
            Status denied = SUCCESS;
            @Override public boolean before(PowerResult.Receipt proposed) {
                receipt = receipt(operation, currency, amount, consumer, proposed.nexus(), null,
                        changes, proposed.before(), proposed.after(), GlobalPos.of(level.dimension(), pos));
                if (NeoForge.EVENT_BUS.post(new NexusTransactionEvent.Pre(level, proposed)).isCanceled()) return false;
                denied = PlayerEnergy.this.before(receipt);
                if (denied == SUCCESS && withdraw) denied = prepareSave();
                return denied == SUCCESS;
            }
            @Override public void after(PowerResult.Receipt committed) {
                if (withdraw) data.put(player, currency, credit);
                world.setDirty();
                post(receipt);
                NeoForge.EVENT_BUS.post(new NexusTransactionEvent.Post(level, committed));
            }
            @Override public void failure(RuntimeException failure) {
                LogUtils.getLogger().error("Nexus listener failed during player power operation", failure);
            }
        }
        var checks = new Checks();
        var result = ledger.transact(node.nexus().id(), node.nexus().capacity(world.profile()), now(), request, checks);
        if (result.succeeded()) return Result.committed(checks.receipt);
        if (checks.denied != SUCCESS) return Result.of(checks.denied);
        return Result.of(switch (result.status()) {
            case INSUFFICIENT_RAW, INSUFFICIENT_CURRENCY -> INSUFFICIENT_POWER;
            case INACTIVE_CURRENCY -> DORMANT;
            case NON_INTEGRAL_OUTPUT, INVALID_AMOUNT -> INVALID_AMOUNT;
            default -> Status.valueOf(result.status().name());
        });
    }

    void jobFinished(Job job) {
        ledger.exclusive(false, () -> {
            try { NeoForge.EVENT_BUS.post(new PlayerPowerEvent.JobFinished(server, job)); }
            catch (RuntimeException failure) { LogUtils.getLogger().error("Energy job observer failed", failure); }
            return true;
        });
    }

    private record DeathDebit(ResourceLocation currency, StoredPool pool, Drop drop, Receipt receipt) {}

    void rememberDeath(LivingDeathEvent event, ServerPlayer player) { deaths.put(player.getUUID(), event); }

    void checkDeath(ServerPlayer player, boolean respawn) {
        UUID owner = player.getUUID();
        var event = deaths.get(owner);
        if (event == null) return;
        if (event.isCanceled() || player.isAlive()) { deaths.remove(owner); return; }
        if (!respawn && player.deathTime <= 0) return;
        deaths.remove(owner);
        jobs.stopFor(owner, DIED);
        ledger.exclusive(false, () -> {
            var level = player.serverLevel();
            var anchor = GlobalPos.of(level.dimension(), player.blockPosition());
            var debits = new ArrayList<DeathDebit>();
            for (var entry : new TreeMap<>(inspect(owner).pools()).entrySet()) {
                var currency = entry.getKey();
                var pool = entry.getValue();
                if (!pool.active() || pool.balance() == 0) continue;
                var rule = rule(owner, currency);
                if (rule.death() == DeathMode.KEEP
                        || (rule.honorKeepInventory() && level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY))) continue;
                var dropId = rule.death() == DeathMode.DROP ? UUID.randomUUID() : null;
                var debit = withBalance(owner, currency, 0);
                var drop = dropId == null ? null : new Drop(owner, level.dimension().location(), player.position(),
                        now() + rule.lifetime(), rule.ownerOnly(), Map.of(currency, pool.balance()));
                var receipt = receipt(Operation.DEATH, currency, MagicAmount.of(pool.balance()), DEATH, null, dropId,
                        Map.of(owner, change(owner, currency, debit)), null, null, anchor);
                if (before(receipt) == SUCCESS) debits.add(new DeathDebit(currency, debit, drop, receipt));
            }
            if (debits.isEmpty() || prepareSave() != SUCCESS) return false;
            for (var debit : debits) {
                if (debit.drop != null) {
                    var id = debit.receipt.drop().orElseThrow();
                    data.drops.put(id, debit.drop);
                    expirations.add(id);
                }
                data.put(owner, debit.currency, debit.pool);
            }
            world.setDirty();
            for (var debit : debits) post(debit.receipt);
            return true;
        });
    }

    void changedDimension(ServerPlayer player) { jobs.stopFor(player.getUUID(), MOVED); }

    void logout(ServerPlayer player) {
        checkDeath(player, false);
        deaths.remove(player.getUUID());
        jobs.stopFor(player.getUUID(), DISCONNECTED);
    }

    void pickup(UUID id, ServerPlayer player) {
        var drop = data.drops.get(id);
        if (drop == null || drop.expires() <= now() || !player.isAlive()
                || !drop.dimension().equals(player.serverLevel().dimension().location())
                || player.distanceToSqr(drop.position()) > 2.25
                || (drop.ownerOnly() && !drop.owner().equals(player.getUUID()))) return;
        var receiver = player.getUUID();
        for (var currency : new TreeSet<>(drop.balances().keySet())) {
            guarded(() -> {
                var current = data.drops.get(id);
                var before = pool(receiver, currency);
                if (!before.active()) return Result.of(DORMANT);
                long held = current.balances().get(currency);
                long moved = Math.min(held, room(receiver, currency));
                if (moved <= 0) return Result.of(CAPACITY);
                var credit = withBalance(receiver, currency, before.balance() + moved);
                var balances = new TreeMap<>(current.balances());
                if (moved == held) balances.remove(currency); else balances.put(currency, held - moved);
                var receipt = receipt(Operation.PICKUP, currency, MagicAmount.of(moved), PICKUP, null, id,
                        Map.of(receiver, change(receiver, currency, credit)), null, null,
                        GlobalPos.of(player.serverLevel().dimension(), BlockPos.containing(drop.position())));
                return commit(receipt, () -> {
                    if (balances.isEmpty()) removeDrop(id);
                    else data.drops.put(id, new Drop(current.owner(), current.dimension(), current.position(),
                            current.expires(), current.ownerOnly(), balances));
                    data.put(receiver, currency, credit);
                });
            });
        }
    }

    private void removeDrop(UUID id) {
        expirations.remove(id);
        data.drops.remove(id);
        var orb = orbs.remove(id);
        if (orb != null) orb.discard();
    }

    boolean validProjection(UUID id, EnergyOrb orb) {
        if (orbs.get(id) != orb) return false;
        var drop = data.drops.get(id);
        return drop != null && drop.expires() > now() && orb.level().hasChunkAt(orb.blockPosition());
    }

    void tick() {
        for (var player : server.getPlayerList().getPlayers()) checkDeath(player, false);
        jobs.tick(server.getPlayerList().getPlayers());
        for (int i = 0; i < 64 && !expirations.isEmpty() && data.drops.get(expirations.first()).expires() <= now(); i++) {
            removeDrop(expirations.first());
            world.setDirty();
        }
        for (int i = 0; i < Math.min(32, data.drops.size()); i++) {
            var id = lastProjection == null ? data.drops.firstKey() : data.drops.higherKey(lastProjection);
            if (id == null) { lastProjection = null; break; }
            lastProjection = id;
            var drop = data.drops.get(id);
            var existing = orbs.get(id);
            if (drop == null || drop.expires() <= now() || (existing != null && !existing.isRemoved())) continue;
            var level = level(drop.dimension());
            if (level == null || !level.hasChunkAt(BlockPos.containing(drop.position()))) continue;
            var orb = new EnergyOrb(level, id, drop.position());
            if (level.addFreshEntity(orb)) orbs.put(id, orb);
        }
        orbs.values().removeIf(EnergyOrb::isRemoved);
    }
}
