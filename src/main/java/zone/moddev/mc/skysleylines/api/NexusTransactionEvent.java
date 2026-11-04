package zone.moddev.mc.skysleylines.api;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.minecraft.server.level.ServerLevel;

/** Fired synchronously on NeoForge.EVENT_BUS on the server thread. Queries are allowed; nested mutations are rejected. */
public final class NexusTransactionEvent {
    private NexusTransactionEvent() {}
    public static final class Pre extends Event implements ICancellableEvent {
        private final ServerLevel level;
        private final PowerResult.Receipt proposed;
        public Pre(ServerLevel level, PowerResult.Receipt proposed) { this.level = level; this.proposed = proposed; }
        public ServerLevel level() { return level; }
        public PowerResult.Receipt proposed() { return proposed; }
    }
    public static final class Post extends Event {
        private final ServerLevel level;
        private final PowerResult.Receipt receipt;
        public Post(ServerLevel level, PowerResult.Receipt receipt) { this.level = level; this.receipt = receipt; }
        public ServerLevel level() { return level; }
        public PowerResult.Receipt receipt() { return receipt; }
    }
}
