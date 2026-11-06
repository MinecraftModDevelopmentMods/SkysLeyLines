package zone.moddev.mc.skysleylines.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class PlayerEnergyEvents {
    private PlayerEnergyEvents() {}

    public static void tick(ServerTickEvent.Post event) { LeyServerContext.playerEnergy(event.getServer()).tick(); }

    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            LeyServerContext.playerEnergy(player.serverLevel().getServer()).rememberDeath(event, player);
    }

    public static void clone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && event.getOriginal() instanceof ServerPlayer player)
            LeyServerContext.playerEnergy(player.serverLevel().getServer()).checkDeath(player, true);
    }

    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            LeyServerContext.playerEnergy(player.serverLevel().getServer()).logout(player);
    }

    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            LeyServerContext.playerEnergy(player.serverLevel().getServer()).changedDimension(player);
    }

    public static void reload(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) LeyServerContext.playerEnergy(event.getPlayerList().getServer()).reload();
    }
}
