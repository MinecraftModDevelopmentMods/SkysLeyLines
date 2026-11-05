package zone.moddev.mc.skysleylines.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.skysleylines.api.LeyVisionStyle;

public final class VisionStyles {
    public static final LeyVisionStyle RIVER = new LeyVisionStyle(LeyVisionStyle.Pattern.RIVER, 0x4fe7ff, 0xa16aff, 1, 1);
    private final Map<ResourceLocation, LeyVisionStyle> styles = new HashMap<>();
    private boolean frozen;

    public VisionStyles() {
        for (var pattern : LeyVisionStyle.Pattern.values()) {
            var style = pattern == LeyVisionStyle.Pattern.RIVER ? RIVER
                    : new LeyVisionStyle(pattern, RIVER.lineRgb(), RIVER.nexusRgb(), 1, 1);
            register(ResourceLocation.fromNamespaceAndPath("skysleylines", pattern.name().toLowerCase(Locale.ROOT)), style);
        }
    }

    public synchronized void register(ResourceLocation id, LeyVisionStyle style) {
        if (frozen) throw new IllegalStateException("Ley vision styles are frozen");
        Objects.requireNonNull(id);
        Objects.requireNonNull(style);
        if (styles.putIfAbsent(id, style) != null) throw new IllegalArgumentException("Duplicate ley vision style " + id);
    }

    public synchronized LeyVisionStyle require(ResourceLocation id) {
        var style = styles.get(Objects.requireNonNull(id));
        if (style == null) throw new IllegalArgumentException("Unknown ley vision style " + id);
        return style;
    }

    public synchronized void freeze() { frozen = true; }
}
