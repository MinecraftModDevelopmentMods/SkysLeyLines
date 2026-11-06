package zone.moddev.mc.skysleylines.integration.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import zone.moddev.mc.skysleylines.api.PlayerPowerEvent;

public final class LeyKubeEvents {
    static final EventGroup GROUP = EventGroup.of("LeyLinesEvents");
    private static final EventHandler CHANGE = GROUP.server("playerPoolChanged", () -> PoolChanged.class);
    private static final EventHandler EMPTY = GROUP.server("playerPoolEmpty", () -> PoolChanged.class);
    private static final EventHandler JOB = GROUP.server("energyJobFinished", () -> JobFinished.class);

    private LeyKubeEvents() {}

    public static final class PoolChanged implements KubeEvent {
        private final MinecraftServer server;
        private final String playerId;
        private final String currency;
        private final Map<String, Object> before;
        private final Map<String, Object> after;
        private final Map<String, Object> receipt;

        PoolChanged(MinecraftServer server, String playerId, String currency, Map<String, Object> before,
                    Map<String, Object> after, Map<String, Object> receipt) {
            this.server = server;
            this.playerId = playerId;
            this.currency = currency;
            this.before = before;
            this.after = after;
            this.receipt = receipt;
        }

        public MinecraftServer getServer() { return server; }
        public String getPlayerId() { return playerId; }
        public String getCurrency() { return currency; }
        public Map<String, Object> getBefore() { return before; }
        public Map<String, Object> getAfter() { return after; }
        public Map<String, Object> getReceipt() { return receipt; }
    }

    public static final class JobFinished implements KubeEvent {
        private final MinecraftServer server;
        private final Map<String, Object> job;

        JobFinished(MinecraftServer server, Map<String, Object> job) {
            this.server = server;
            this.job = job;
        }

        public MinecraftServer getServer() { return server; }
        public Map<String, Object> getJob() { return job; }
    }

    static void changed(PlayerPowerEvent.Post event) {
        if (!CHANGE.hasListeners() && !EMPTY.hasListeners()) return;
        var receipt = event.receipt();
        receipt.changes().forEach((id, change) -> {
            if (change.before().equals(change.after())) return;
            var script = new PoolChanged(event.server(), id.toString(), receipt.currency().toString(),
                    LeyPlayerScripts.pool(change.before()), LeyPlayerScripts.pool(change.after()), LeyPlayerScripts.receipt(receipt));
            if (CHANGE.hasListeners()) CHANGE.post(ScriptType.SERVER, script);
            if (change.before().balance() > 0 && change.after().balance() == 0 && EMPTY.hasListeners())
                EMPTY.post(ScriptType.SERVER, script);
        });
    }

    static void finished(PlayerPowerEvent.JobFinished event) {
        if (JOB.hasListeners()) JOB.post(ScriptType.SERVER, new JobFinished(event.server(), LeyPlayerScripts.job(event.job())));
    }
}
