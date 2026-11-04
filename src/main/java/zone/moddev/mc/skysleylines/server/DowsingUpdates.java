package zone.moddev.mc.skysleylines.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import zone.moddev.mc.skysleylines.api.LeyLinesApi;
import zone.moddev.mc.skysleylines.item.DowsingHands;
import zone.moddev.mc.skysleylines.item.DowsingReading;
import zone.moddev.mc.skysleylines.network.DowsingPayload;

public final class DowsingUpdates {
    private DowsingUpdates() {}
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 5 != 0 || !DowsingHands.hasRods(player)) return;
        var field = LeyLinesApi.forLevel(player.serverLevel()).field();
        var pos = player.blockPosition();
        var reading = DowsingReading.sample(field, pos.getX(), pos.getY(), pos.getZ(), player.getYRot());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new DowsingPayload(player.getUUID(), player.level().dimension().location(), reading));
    }
}
