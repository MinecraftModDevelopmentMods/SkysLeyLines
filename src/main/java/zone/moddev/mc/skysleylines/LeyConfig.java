package zone.moddev.mc.skysleylines;

import net.neoforged.neoforge.common.ModConfigSpec;
import zone.moddev.mc.skysleylines.api.LeyProfile;

/** Installation defaults copied once when each world first initializes its ley profile. */
public final class LeyConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue SPACING = BUILDER.comment(
            "NEW WORLDS ONLY. Existing worlds keep their saved layout and reserve rules.",
            "Source-cell spacing in blocks; typical nexus distances are of the same order.")
            .defineInRange("spacingBlocks", 2048, 256, 65536);
    public static final ModConfigSpec.IntValue LINE_WIDTH = BUILDER.defineInRange("lineHalfWidthBlocks", 32, 1, 128);
    public static final ModConfigSpec.IntValue NEXUS_RADIUS = BUILDER.defineInRange("nexusRadiusBlocks", 64, 1, 128);
    public static final ModConfigSpec.IntValue VERTICAL_REACH = BUILDER.defineInRange("verticalReachBlocks", 128, 1, 2048);
    public static final ModConfigSpec.IntValue CAPACITY = BUILDER.defineInRange("fullStrengthCapacity", 10000, 1, 1000000);
    public static final ModConfigSpec.IntValue RECOVERY = BUILDER.defineInRange("rechargeTicks", 24000, 1, 1728000);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private LeyConfig() {}
    public static LeyProfile defaults() {
        return new LeyProfile(SPACING.get(), LINE_WIDTH.get(), NEXUS_RADIUS.get(), VERTICAL_REACH.get(), CAPACITY.get(), RECOVERY.get());
    }
}
