package zone.moddev.mc.skysleylines.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.skysleylines.item.DowsingReading;

/** Logical-client mailbox. No client-only classes are linked from common payload registration. */
public final class DowsingSignals {
    private record Signal(ResourceLocation dimension, DowsingReading reading, long tick) {}
    private static final Map<UUID, Signal> SIGNALS = new HashMap<>();
    private DowsingSignals() {}
    public static void receive(DowsingPayload payload, long tick) {
        SIGNALS.put(payload.player(), new Signal(payload.dimension(), payload.reading(), tick));
    }
    public static DowsingReading read(UUID player, ResourceLocation dimension, long tick) {
        var signal = SIGNALS.get(player);
        return signal != null && signal.dimension().equals(dimension) && tick >= signal.tick() && tick - signal.tick() <= 20
                ? signal.reading() : DowsingReading.STILL;
    }
    public static void prune(long tick) { SIGNALS.values().removeIf(s -> tick < s.tick() || tick - s.tick() > 20); }
    public static void clear() { SIGNALS.clear(); }
}
