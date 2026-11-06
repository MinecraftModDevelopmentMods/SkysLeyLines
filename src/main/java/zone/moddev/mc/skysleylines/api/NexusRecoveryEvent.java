package zone.moddev.mc.skysleylines.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Protection hooks for an atomic transfer from a retired pin to an active nexus. */
public abstract class NexusRecoveryEvent extends Event {
    public record Account(NexusId id, BlockPos position, AccountSnapshot before, AccountSnapshot after) {}
    public record Receipt(long revision, ResourceLocation currency, long units, PowerRequest.Consumer consumer,
                          Account source, Account destination) {}

    private final ServerLevel level;
    private final Receipt receipt;

    protected NexusRecoveryEvent(ServerLevel level, Receipt receipt) {
        this.level = level;
        this.receipt = receipt;
    }

    public ServerLevel level() { return level; }
    public Receipt receipt() { return receipt; }

    public static final class Pre extends NexusRecoveryEvent implements ICancellableEvent {
        public Pre(ServerLevel level, Receipt receipt) { super(level, receipt); }
    }

    public static final class Post extends NexusRecoveryEvent {
        public Post(ServerLevel level, Receipt receipt) { super(level, receipt); }
    }
}
