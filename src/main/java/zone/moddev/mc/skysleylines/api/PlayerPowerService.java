package zone.moddev.mc.skysleylines.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** World-wide converted power pools. All methods require the owning server thread. */
public interface PlayerPowerService {
    enum Operation { WITHDRAW, DEPOSIT, TRANSFER, CONVERT, CONFIGURE, ADJUST, SPEND, DEATH, PICKUP }
    enum Status {
        SUCCESS, RUNNING, CANCELLED, CHECK_FAILED, REENTRANT, UNKNOWN_CURRENCY, DORMANT,
        INVALID_AMOUNT, NO_NEXUS, INSUFFICIENT_POWER, CAPACITY, OUT_OF_RANGE, UNAVAILABLE,
        POLICY, OVERFLOW, STORAGE_FAILED, EXPIRED, MOVED, DISCONNECTED, DIED, RELOADED
    }
    enum DeathMode { DROP, KEEP, LOSE }

    /** Code defaults for a currency. Pack and server policy can override individual fields. */
    record CurrencyPolicy(boolean instant, MagicAmount transferRateLimit, MagicAmount conversionRateLimit,
                          boolean sameDimension, int maxDistance, DeathMode death, boolean honorKeepInventory,
                          boolean ownerOnly, int dropLifetime) {
        public static final CurrencyPolicy DEFAULT = new CurrencyPolicy(true, null, null, false, 0,
                DeathMode.DROP, true, false, 6000);

        public CurrencyPolicy {
            java.util.Objects.requireNonNull(death);
            if ((transferRateLimit != null && transferRateLimit.isZero())
                    || (conversionRateLimit != null && conversionRateLimit.isZero())
                    || maxDistance < 0 || maxDistance > 60_000_000 || dropLifetime < 1 || dropLifetime > 1_728_000)
                throw new IllegalArgumentException("Invalid currency policy");
        }
    }

    /** Positive whole-unit capacity. Disabling retains the balance. A null auto rate disables built-in recharge. */
    record Settings(boolean enabled, long capacity, MagicAmount autoRate) {
        public Settings {
            if (capacity < 0 || (autoRate != null && autoRate.isZero())) throw new IllegalArgumentException("Invalid pool settings");
        }
    }
    record Pool(long balance, Settings settings, boolean active) {}
    record Snapshot(UUID player, Map<ResourceLocation, Pool> pools) {
        public Snapshot { pools = Map.copyOf(pools); }
    }
    record Change(UUID player, Pool before, Pool after) {}
    record Receipt(Operation operation, ResourceLocation currency, MagicAmount amount, PowerRequest.Consumer consumer,
                   Optional<NexusId> nexus, Optional<UUID> drop, Map<UUID, Change> changes,
                   Optional<AccountSnapshot> nexusBefore, Optional<AccountSnapshot> nexusAfter, Optional<GlobalPos> anchor) {
        public Receipt { changes = Map.copyOf(changes); }
    }
    record Result(Status status, Optional<Receipt> receipt) {
        public boolean succeeded() { return status == Status.SUCCESS; }
        public static Result of(Status status) { return new Result(status, Optional.empty()); }
        public static Result committed(Receipt receipt) { return new Result(Status.SUCCESS, Optional.of(receipt)); }
    }

    /** Select exactly one schedule. The rate uses the same units as the request amount. */
    record Timing(int ticks, MagicAmount perTick) {
        public Timing {
            if (ticks < 0 || (perTick != null && (ticks != 0 || perTick.isZero()))) throw new IllegalArgumentException("Invalid energy timing");
        }
        public static Timing instant() { return new Timing(0, null); }
        public static Timing duration(int ticks) {
            if (ticks <= 0) throw new IllegalArgumentException("Duration must be positive");
            return new Timing(ticks, null);
        }
        public static Timing rate(MagicAmount amount) { return new Timing(0, java.util.Objects.requireNonNull(amount)); }
        public boolean isInstant() { return ticks == 0 && perTick == null; }
    }
    record Job(UUID id, Operation operation, ResourceLocation currency, MagicAmount total,
               MagicAmount completed, Status status, PowerRequest.Consumer consumer) {}
    record Submission(Status status, Optional<UUID> jobId, Optional<Receipt> receipt) {}

    Snapshot inspect(UUID player);
    Result configure(UUID player, ResourceLocation currency, Settings settings, PowerRequest.Consumer consumer);
    /** Trusted adjustment. May reduce a dormant or over-capacity balance. */
    Result setBalance(UUID player, ResourceLocation currency, long units, PowerRequest.Consumer consumer);
    Result adjust(UUID player, ResourceLocation currency, long delta, PowerRequest.Consumer consumer);
    Result spend(UUID player, ResourceLocation currency, long units, PowerRequest.Consumer consumer);

    Submission withdraw(ServerPlayer player, ResourceLocation currency, long units, Timing timing, PowerRequest.Consumer consumer);
    Submission deposit(ServerPlayer player, ResourceLocation currency, long units, Timing timing, PowerRequest.Consumer consumer);
    Submission transfer(ServerPlayer from, ServerPlayer to, ResourceLocation currency, long units, Timing timing, PowerRequest.Consumer consumer);
    Submission convert(ServerLevel level, BlockPos pos, ResourceLocation currency, MagicAmount raw, Timing timing, PowerRequest.Consumer consumer);
    Optional<Job> job(UUID id);
    boolean cancel(UUID id);
}
