package zone.moddev.mc.skysleylines.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import zone.moddev.mc.skysleylines.api.*;
import zone.moddev.mc.skysleylines.api.PlayerPowerService.*;
import static zone.moddev.mc.skysleylines.api.PlayerPowerService.Status.*;

/** Due-order, server-thread scheduling. Every tranche uses current balances and protection hooks. */
final class EnergyJobs {
    private static final int MAX_ACTIVE = 4096;
    private static final int TICK_BUDGET = 256;
    private static final int FINISHED_HISTORY = 1024;
    private static final PowerRequest.Consumer AUTO = PlayerEnergy.system("auto_recharge");
    private final PlayerEnergy power;
    private final Map<UUID, Work> active = new LinkedHashMap<>();
    private final Map<UUID, Job> finished = new LinkedHashMap<>();
    private final NavigableSet<Work> due = new TreeSet<>(Comparator.comparingLong((Work job) -> job.nextTick)
            .thenComparingLong(job -> job.order));
    private final Map<AutoKey, UUID> auto = new HashMap<>();
    private long order;

    private record AutoKey(UUID player, ResourceLocation currency) {}
    private record Endpoint(UUID player, ResourceLocation dimension) {
        Endpoint(ServerPlayer player) { this(player.getUUID(), player.serverLevel().dimension().location()); }
    }
    private static final class Work {
        final UUID id = UUID.randomUUID();
        final Operation operation;
        final ResourceLocation currency;
        final MagicAmount total;
        final MagicAmount speed;
        final PowerRequest.Consumer consumer;
        final Endpoint first;
        final Endpoint second;
        final ResourceLocation dimension;
        final BlockPos position;
        final long order;
        final AutoKey automatic;
        MagicAmount completed = MagicAmount.ZERO;
        MagicAmount conversionRate;
        NexusId nexus;
        BlockPos nexusChunk;
        long nextTick;
        long elapsed;

        Work(Operation operation, ResourceLocation currency, MagicAmount total, MagicAmount speed,
             PowerRequest.Consumer consumer, Endpoint first, Endpoint second, ResourceLocation dimension,
             BlockPos position, long order, AutoKey automatic) {
            this.operation = operation;
            this.currency = Objects.requireNonNull(currency);
            this.total = Objects.requireNonNull(total);
            this.speed = speed;
            this.consumer = Objects.requireNonNull(consumer);
            this.first = first;
            this.second = second;
            this.dimension = dimension;
            this.position = position;
            this.order = order;
            this.automatic = automatic;
        }

        Job view(Status status) { return new Job(id, operation, currency, total, completed, status, consumer); }
    }

    EnergyJobs(PlayerEnergy power) { this.power = power; }

    Submission submit(Operation operation, ServerPlayer from, ServerPlayer to, ServerLevel level, BlockPos pos,
                      ResourceLocation currency, long units, Timing timing, PowerRequest.Consumer consumer) {
        power.checkThread();
        if (units <= 0) return submission(INVALID_AMOUNT);
        return submit(operation, from, to, level, pos, currency, MagicAmount.of(units), timing, consumer);
    }

    Submission submit(Operation operation, ServerPlayer from, ServerPlayer to, ServerLevel level, BlockPos pos,
                      ResourceLocation currency, MagicAmount amount, Timing timing, PowerRequest.Consumer consumer) {
        return submit(operation, from, to, level, pos, currency, amount, timing, consumer, null);
    }

    private Submission submit(Operation operation, ServerPlayer from, ServerPlayer to, ServerLevel level, BlockPos pos,
                              ResourceLocation currency, MagicAmount amount, Timing timing, PowerRequest.Consumer consumer,
                              AutoKey automatic) {
        power.checkThread();
        if (power.busy()) return submission(REENTRANT);
        if (amount.isZero() || (from != null && from == to)) return submission(INVALID_AMOUNT);
        if ((from != null && power.online(from.getUUID()) != from) || (to != null && power.online(to.getUUID()) != to)
                || (level != null && power.level(level.dimension().location()) != level)) return submission(UNAVAILABLE);
        var speed = timing.isInstant() ? null : timing.perTick() == null ? amount.divide(MagicAmount.of(timing.ticks())) : timing.perTick();
        var job = new Work(operation, currency, amount, speed, consumer, from == null ? null : new Endpoint(from),
                to == null ? null : new Endpoint(to), level == null ? null : level.dimension().location(),
                pos == null ? null : pos.immutable(), order++, automatic);
        var check = resolve(job);
        if (check.status != SUCCESS) return submission(check.status);
        if (operation == Operation.CONVERT) {
            job.conversionRate = power.rate(currency);
            MagicAmount output = amount.multiply(job.conversionRate);
            if (output.compareTo(MagicAmount.ONE) < 0 || (timing.isInstant() && !output.isWhole())) return submission(INVALID_AMOUNT);
        }
        if (check.node != null) {
            job.nexus = check.node.nexus().id();
            job.nexusChunk = BlockPos.containing(check.node.nexus().x(), check.pos.getY(), check.node.nexus().z());
        }
        if (timing.isInstant()) {
            var tranche = tranche(job, check, amount, false);
            return new Submission(tranche.status, Optional.empty(), tranche.receipt);
        }
        if (active.size() >= MAX_ACTIVE) return submission(UNAVAILABLE);
        job.nextTick = power.now() + 1;
        active.put(job.id, job);
        due.add(job);
        if (automatic != null) auto.put(automatic, job.id);
        return new Submission(RUNNING, Optional.of(job.id), Optional.empty());
    }

    private static Submission submission(Status status) { return new Submission(status, Optional.empty(), Optional.empty()); }

    private record Resolved(Status status, ServerLevel level, BlockPos pos, NexusSnapshot node) {
        Resolved(Status status) { this(status, null, null, null); }
    }

    private Status validateEndpoint(Endpoint endpoint, ServerPlayer player, ResourceLocation currency) {
        if (endpoint == null) return SUCCESS;
        if (player == null) return DISCONNECTED;
        if (!player.isAlive()) return DIED;
        if (!player.serverLevel().dimension().location().equals(endpoint.dimension())) return MOVED;
        return power.pool(endpoint.player(), currency).active() ? SUCCESS : DORMANT;
    }

    private Resolved resolve(Work job) {
        var rate = power.rate(job.currency);
        if (rate == null) return new Resolved(UNKNOWN_CURRENCY);
        if (!power.currencyActive(job.currency)) return new Resolved(DORMANT);
        if (job.conversionRate != null && !job.conversionRate.equals(rate)) return new Resolved(POLICY);
        ServerPlayer first = job.first == null ? null : power.online(job.first.player());
        ServerPlayer second = job.second == null ? null : power.online(job.second.player());
        Status check = validateEndpoint(job.first, first, job.currency);
        if (check != SUCCESS) return new Resolved(check);
        check = validateEndpoint(job.second, second, job.currency);
        if (check != SUCCESS) return new Resolved(check);
        var rule = power.rule(job.first == null ? null : job.first.player(), job.currency);
        var limit = job.operation == Operation.CONVERT ? rule.conversionLimit() : rule.transferLimit();
        if (job.speed == null ? !rule.instant() : limit != null && job.speed.compareTo(limit) > 0) return new Resolved(POLICY);
        if (job.operation == Operation.TRANSFER) {
            boolean sameDimension = first.serverLevel() == second.serverLevel();
            if ((rule.sameDimension() || rule.maxDistance() > 0) && !sameDimension) return new Resolved(OUT_OF_RANGE);
            if (rule.maxDistance() > 0 && first.distanceToSqr(second) > (double) rule.maxDistance() * rule.maxDistance())
                return new Resolved(OUT_OF_RANGE);
            return new Resolved(SUCCESS);
        }
        ServerLevel level = power.level(job.dimension);
        var pos = first == null ? job.position : first.blockPosition();
        if (level == null || !level.hasChunkAt(pos) || (job.nexusChunk != null && !level.hasChunkAt(job.nexusChunk)))
            return new Resolved(UNAVAILABLE);
        var node = LeyLinesApi.forLevel(level).power().quoteAt(pos.getX(), pos.getY(), pos.getZ()).orElse(null);
        if (node == null) return new Resolved(job.nexus == null ? NO_NEXUS : MOVED);
        if (job.nexus != null && !job.nexus.equals(node.nexus().id())) return new Resolved(MOVED);
        return new Resolved(SUCCESS, level, pos, node);
    }

    private record Tranche(Status status, MagicAmount moved, Optional<Receipt> receipt) {
        Tranche(Status status) { this(status, MagicAmount.ZERO, Optional.empty()); }
    }

    private Tranche tranche(Work job, Resolved resolved, MagicAmount amount, boolean allowPartial) {
        var node = resolved.node;
        var account = node == null ? null : node.account();
        var currency = job.currency;
        var rate = power.rate(currency);
        var source = job.first == null ? null : job.first.player();
        long funds;
        long room;
        switch (job.operation) {
            case WITHDRAW -> {
                funds = account.currency(currency);
                room = power.room(source, currency);
            }
            case DEPOSIT -> {
                funds = power.pool(source, currency).balance();
                var capacity = MagicAmount.of(account.capacity());
                room = capacity.compareTo(account.total()) <= 0 ? 0
                        : floor(capacity.subtract(account.total()).multiply(rate), Long.MAX_VALUE - account.currency(currency));
            }
            case TRANSFER -> {
                funds = power.pool(source, currency).balance();
                room = power.room(job.second.player(), currency);
            }
            case CONVERT -> {
                funds = floor(account.raw().multiply(rate), Long.MAX_VALUE);
                room = Long.MAX_VALUE - account.currency(currency);
                amount = amount.multiply(rate);
            }
            default -> throw new IllegalStateException("Unexpected energy operation " + job.operation);
        }
        long available = Math.min(funds, room);
        boolean shortfall = amount.compareTo(MagicAmount.of(available)) > 0;
        Status status = shortfall ? (funds <= room ? INSUFFICIENT_POWER : CAPACITY) : SUCCESS;
        if (job.operation == Operation.CONVERT && shortfall && funds == Long.MAX_VALUE && room < Long.MAX_VALUE) status = OVERFLOW;
        if (shortfall && (!allowPartial || available == 0)) return new Tranche(status);
        long units = shortfall ? available : amount.wholeLongExact();
        var actual = job.operation == Operation.CONVERT ? MagicAmount.of(units).divide(rate) : MagicAmount.of(units);
        var result = switch (job.operation) {
            case WITHDRAW, CONVERT -> power.useNode(job.operation, source, resolved.level, resolved.pos, node, currency, actual, job.consumer);
            case DEPOSIT -> power.deposit(source, GlobalPos.of(resolved.level.dimension(), resolved.pos), node, currency, units, job.consumer);
            case TRANSFER -> power.movePlayers(source, job.second.player(), currency, units, job.consumer);
            default -> throw new IllegalStateException("Unexpected energy operation " + job.operation);
        };
        if (!result.succeeded()) return new Tranche(result.status());
        return new Tranche(status, actual, result.receipt());
    }

    static long floor(MagicAmount amount, long limit) { return amount.min(MagicAmount.of(limit)).floorLongExact(); }

    static MagicAmount earned(MagicAmount total, MagicAmount speed, long elapsed, MagicAmount unitsPerAmount) {
        var output = speed.multiply(MagicAmount.of(elapsed)).min(total).multiply(unitsPerAmount);
        return new MagicAmount(output.numerator().divide(output.denominator()), java.math.BigInteger.ONE).divide(unitsPerAmount);
    }

    void tick(List<ServerPlayer> online) {
        long now = power.now();
        for (int i = 0; i < TICK_BUDGET && !due.isEmpty() && due.first().nextTick <= now; i++) {
            var job = due.pollFirst();
            var result = resolve(job);
            if (result.status != SUCCESS) { finish(job, result.status); continue; }
            var rate = job.conversionRate == null ? MagicAmount.ONE : job.conversionRate;
            // Advance by one scheduled tick even under backlog, so a slow scheduler cannot exceed a rate ceiling.
            long elapsed = ++job.elapsed;
            var entitled = earned(job.total, job.speed, elapsed, rate);
            var remaining = entitled.subtract(job.completed);
            if (!remaining.isZero()) {
                var moved = tranche(job, result, remaining, true);
                job.completed = job.completed.add(moved.moved);
                if (moved.status != SUCCESS) { finish(job, moved.status); continue; }
            }
            if (job.speed.multiply(MagicAmount.of(elapsed)).compareTo(job.total) >= 0) {
                finish(job, SUCCESS);
            } else {
                job.nextTick = now + 1;
                due.add(job);
            }
        }
        if (now % 20 == 0) for (var player : online) tryAuto(player);
    }

    private void tryAuto(ServerPlayer player) {
        if (!player.isAlive()) return;
        var snapshot = power.inspect(player.getUUID());
        for (var entry : new TreeMap<>(snapshot.pools()).entrySet()) {
            var currency = entry.getKey();
            var pool = entry.getValue();
            var speed = pool.settings().autoRate();
            long room = power.room(player.getUUID(), currency);
            var key = new AutoKey(player.getUUID(), currency);
            if (pool.active() && speed != null && room > 0 && !auto.containsKey(key)) {
                submit(Operation.WITHDRAW, player, null, player.serverLevel(), player.blockPosition(), currency,
                        MagicAmount.of(room), Timing.rate(speed), AUTO, key);
            }
        }
    }

    Optional<Job> read(UUID id) {
        var running = active.get(id);
        return Optional.ofNullable(running == null ? finished.get(id) : running.view(RUNNING));
    }

    boolean cancel(UUID id, Status status) {
        var job = active.get(id);
        if (job == null) return false;
        finish(job, status);
        return true;
    }

    void stopFor(UUID player, Status status) {
        for (var job : List.copyOf(active.values())) {
            if ((job.first != null && job.first.player().equals(player))
                    || (job.second != null && job.second.player().equals(player))) finish(job, status);
        }
    }

    void policyChanged() {
        for (var job : List.copyOf(active.values())) finish(job, RELOADED);
    }

    private void finish(Work job, Status status) {
        active.remove(job.id);
        due.remove(job);
        if (job.automatic != null) auto.remove(job.automatic);
        var report = job.view(status);
        if (finished.size() == FINISHED_HISTORY) finished.remove(finished.keySet().iterator().next());
        finished.put(job.id, report);
        power.jobFinished(report);
    }
}
