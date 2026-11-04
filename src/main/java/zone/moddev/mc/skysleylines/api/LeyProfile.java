package zone.moddev.mc.skysleylines.api;

/** Immutable, world-persisted settings. Changing installation defaults never edits an existing profile. */
public record LeyProfile(int spacingBlocks, int lineWidth, int nexusRadius, int verticalReach,
                         int fullStrengthCapacity, int rechargeTicks) {
    public static final int LAYOUT_VERSION = 1;
    public static final LeyProfile DEFAULT = new LeyProfile(2048, 32, 64, 128, 10_000, 24_000);

    public LeyProfile {
        if (spacingBlocks < 256 || spacingBlocks > 65_536 || lineWidth < 1 || lineWidth > 128
                || nexusRadius < 1 || nexusRadius > 128 || verticalReach < 1 || verticalReach > 2048
                || fullStrengthCapacity < 1 || fullStrengthCapacity > 1_000_000
                || rechargeTicks < 1 || rechargeTicks > 1_728_000) {
            throw new IllegalArgumentException("Invalid ley profile");
        }
    }
}
