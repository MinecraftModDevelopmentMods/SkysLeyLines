package zone.moddev.mc.skysleylines.core;

import java.util.LinkedHashMap;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.skysleylines.api.LeyVisionStyle;

/** Insertion order records activation, and replacing an active grant preserves that order. */
public final class VisionGrants {
    private record Grant(long expiresAt, LeyVisionStyle style) {}
    private final LinkedHashMap<ResourceLocation, Grant> grants = new LinkedHashMap<>();

    public void grant(ResourceLocation source, long now, int ticks, LeyVisionStyle style) {
        Objects.requireNonNull(source);
        if (ticks <= 0) throw new IllegalArgumentException("Vision duration must be positive");
        Grant previous = grants.get(source);
        if (previous != null && previous.expiresAt() <= now) grants.remove(source);
        grants.put(source, new Grant(now + ticks, style));
    }

    public void revoke(ResourceLocation source) { grants.remove(Objects.requireNonNull(source)); }
    public void expire(long now) { grants.values().removeIf(grant -> grant.expiresAt() <= now); }
    public boolean isEmpty() { return grants.isEmpty(); }

    public LeyVisionStyle styleOr(LeyVisionStyle fallback) {
        for (Grant grant : grants.reversed().values()) {
            if (grant.style() != null) return grant.style();
        }
        return fallback;
    }
}
