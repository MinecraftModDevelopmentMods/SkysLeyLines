package zone.moddev.mc.skysleylines.network;

import net.minecraft.world.entity.player.Player;

/** Main-thread handoff that keeps the payload handler independent of client screen classes. */
public final class LeyMeterSignals {
    private record Reading(Player player, LeyMeterPayload payload) {}
    private static Reading pending;

    private LeyMeterSignals() {}

    public static void receive(Player player, LeyMeterPayload payload) {
        if (player.level().isClientSide() && player.level().dimension().location().equals(payload.dimension()))
            pending = new Reading(player, payload);
    }

    public static LeyMeterPayload take(Player player) {
        Reading reading = pending;
        pending = null;
        return reading != null && reading.player == player
                && player.level().dimension().location().equals(reading.payload.dimension()) ? reading.payload : null;
    }
}
