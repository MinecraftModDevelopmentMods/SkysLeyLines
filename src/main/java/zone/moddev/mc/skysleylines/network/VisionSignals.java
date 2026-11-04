package zone.moddev.mc.skysleylines.network;

import net.minecraft.world.level.Level;
import zone.moddev.mc.skysleylines.core.VisionGeometry;

/** Client main-thread mailbox. Level identity prevents reuse on dimension changes or reconnects. */
public final class VisionSignals {
    private static Level level;
    private static VisionGeometry geometry = VisionGeometry.EMPTY;

    private VisionSignals() {}

    public static void receive(Level currentLevel, VisionPayload payload) {
        if (currentLevel.isClientSide() && currentLevel.dimension().location().equals(payload.dimension())) {
            level = currentLevel;
            geometry = payload.geometry();
        }
    }

    public static VisionGeometry read(Level currentLevel) {
        if (level != currentLevel) clear();
        return geometry;
    }

    public static void clear() {
        level = null;
        geometry = VisionGeometry.EMPTY;
    }
}
