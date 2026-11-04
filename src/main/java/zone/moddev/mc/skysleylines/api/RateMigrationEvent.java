package zone.moddev.mc.skysleylines.api;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Operator-driven whole-world migration. Same callback restrictions as account transactions. */
public final class RateMigrationEvent {
    private RateMigrationEvent() {}
    public static final class Pre extends Event implements ICancellableEvent {
        private final RateMigration plan;
        public Pre(RateMigration plan) { this.plan = plan; }
        public RateMigration plan() { return plan; }
    }
    public static final class Post extends Event {
        private final RateMigration plan;
        public Post(RateMigration plan) { this.plan = plan; }
        public RateMigration plan() { return plan; }
    }
}
