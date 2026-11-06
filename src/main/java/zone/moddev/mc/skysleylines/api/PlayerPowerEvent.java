package zone.moddev.mc.skysleylines.api;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Server-thread checks and notifications. Inspect freely in a listener. Nested mutations are rejected. */
public abstract class PlayerPowerEvent extends Event {
    private final MinecraftServer server;
    private final PlayerPowerService.Receipt receipt;

    protected PlayerPowerEvent(MinecraftServer server, PlayerPowerService.Receipt receipt) {
        this.server = server;
        this.receipt = receipt;
    }
    public MinecraftServer server() { return server; }
    public PlayerPowerService.Receipt receipt() { return receipt; }

    public static final class Pre extends PlayerPowerEvent implements ICancellableEvent {
        public Pre(MinecraftServer server, PlayerPowerService.Receipt receipt) { super(server, receipt); }
    }
    public static final class Post extends PlayerPowerEvent {
        public Post(MinecraftServer server, PlayerPowerService.Receipt receipt) { super(server, receipt); }
    }
    public static final class JobFinished extends Event {
        private final MinecraftServer server;
        private final PlayerPowerService.Job job;

        public JobFinished(MinecraftServer server, PlayerPowerService.Job job) {
            this.server = server;
            this.job = job;
        }
        public MinecraftServer server() { return server; }
        public PlayerPowerService.Job job() { return job; }
    }
}
