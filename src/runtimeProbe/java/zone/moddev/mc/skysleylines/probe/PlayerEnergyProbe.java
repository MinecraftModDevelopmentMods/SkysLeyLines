package zone.moddev.mc.skysleylines.probe;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import zone.moddev.mc.skysleylines.api.*;
import zone.moddev.mc.skysleylines.api.PlayerPowerService.Settings;

final class PlayerEnergyProbe {
    private static final ResourceLocation AETHER = ResourceLocation.parse("leyruntimeprobe:aether");
    private static final PowerRequest.Consumer ACCEPT = source("player_probe");
    private static final PowerRequest.Consumer DENY = source("deny_player_probe");

    private PlayerEnergyProbe() {}

    static void run(ServerStartedEvent event) {
        if (!Boolean.getBoolean("skysleylines.playerEnergyProbe")) return;
        var server = event.getServer();
        var field = LeyLinesApi.forLevel(server.overworld());
        var nexus = field.field().nearestNexus(0, 0, 4096).orElseThrow();
        int x = (int) Math.round(nexus.x()), z = (int) Math.round(nexus.z());
        int y = field.field().sampleColumn(x, z).naturalSurfaceY();
        var node = field.power();
        var power = LeyLinesApi.playerPower(server);
        var player = UUID.randomUUID();
        int[] checks = {0};
        NeoForge.EVENT_BUS.addListener((PlayerPowerEvent.Pre pre) -> {
            if (!pre.receipt().consumer().equals(DENY)) return;
            require(node.convertAt(x, y, z, AETHER, MagicAmount.ONE, ACCEPT).status()
                    == PowerResult.Status.REENTRANT, "Nested node write from wallet Pre");
            require(power.setBalance(player, AETHER, 1, ACCEPT).status()
                    == PlayerPowerService.Status.REENTRANT, "Nested wallet write from wallet Pre");
            checks[0]++;
            pre.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((NexusTransactionEvent.Pre pre) -> {
            if (!pre.proposed().request().consumer().equals(ACCEPT)) return;
            require(power.setBalance(player, AETHER, 1, ACCEPT).status()
                    == PlayerPowerService.Status.REENTRANT, "Nested wallet write from nexus Pre");
            checks[0]++;
        });
        require(power.configure(player, AETHER, new Settings(true, 4, null), ACCEPT).succeeded(), "Pool configure failed");
        require(power.setBalance(player, AETHER, 4, ACCEPT).succeeded(), "Pool credit failed");
        var before = node.quoteAt(x, y, z).orElseThrow().account();
        require(power.spend(player, AETHER, 4, DENY).status() == PlayerPowerService.Status.CANCELLED, "Pool Pre cancellation ignored");
        require(power.inspect(player).pools().get(AETHER).balance() == 4, "Cancelled spend changed the pool");
        require(node.quoteAt(x, y, z).orElseThrow().account().equals(before), "Nested write changed nexus");
        require(node.convertAt(x, y, z, AETHER, MagicAmount.ONE, ACCEPT).succeeded(), "Outer nexus write failed");
        require(power.inspect(player).pools().get(AETHER).balance() == 4 && checks[0] == 2, "Protection checks were skipped");
        System.out.println("PLAYER_ENERGY_JAVA_PROTECTION_PASS");
    }

    private static PowerRequest.Consumer source(String path) {
        return new PowerRequest.Consumer(ResourceLocation.fromNamespaceAndPath("leyruntimeprobe", path));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
