package zone.moddev.mc.skysleylines.network;

import net.minecraft.world.level.Level;
import zone.moddev.mc.skysleylines.api.LeyVisionStyle;
import zone.moddev.mc.skysleylines.core.VisionGeometry;
import zone.moddev.mc.skysleylines.core.VisionStyles;

/** Client main-thread mailbox. Level identity prevents reuse on dimension changes or reconnects. */
public final class VisionSignals {
    private static Level level;
    private static VisionGeometry geometry = VisionGeometry.EMPTY;
    private static LeyVisionStyle style = VisionStyles.RIVER;

    private VisionSignals() {}

    public static void receive(Level currentLevel, VisionPayload payload) {
        if (currentLevel.isClientSide() && currentLevel.dimension().location().equals(payload.dimension())) {
            if (level != currentLevel || !geometry.equals(payload.geometry())) geometry = payload.geometry();
            level = currentLevel;
            style = payload.style();
        }
    }

    public static VisionGeometry read(Level currentLevel) {
        if (level != currentLevel) clear();
        return geometry;
    }

    public static LeyVisionStyle style() { return style; }

    public static void clear() {
        level = null;
        geometry = VisionGeometry.EMPTY;
        style = VisionStyles.RIVER;
    }
}
