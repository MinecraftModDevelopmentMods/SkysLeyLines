package zone.moddev.mc.skysleylines;

import java.math.BigDecimal;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import zone.moddev.mc.skysleylines.api.LeyProfile;
import zone.moddev.mc.skysleylines.core.NexusLedger;

/** Installation defaults copied once when each world first initializes its ley profile. */
public final class LeyConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue SPACING = BUILDER.comment(
            "NEW WORLDS ONLY. Existing worlds keep their saved layout and reserve rules.",
            "Source-cell spacing in blocks; typical nexus distances are of the same order.")
            .translation("config.skysleylines.spacingBlocks")
            .defineInRange("spacingBlocks", 2048, 256, 65536);
    public static final ModConfigSpec.IntValue LINE_WIDTH = BUILDER.translation("config.skysleylines.lineHalfWidthBlocks")
            .defineInRange("lineHalfWidthBlocks", 32, 1, 128);
    public static final ModConfigSpec.IntValue NEXUS_RADIUS = BUILDER.translation("config.skysleylines.nexusRadiusBlocks")
            .defineInRange("nexusRadiusBlocks", 64, 1, 128);
    public static final ModConfigSpec.IntValue VERTICAL_REACH = BUILDER.translation("config.skysleylines.verticalReachBlocks")
            .defineInRange("verticalReachBlocks", 128, 1, 2048);
    public static final ModConfigSpec.IntValue CAPACITY = BUILDER.translation("config.skysleylines.fullStrengthCapacity")
            .defineInRange("fullStrengthCapacity", 10000, 1, 1000000);
    public static final ModConfigSpec.IntValue RECOVERY = BUILDER.translation("config.skysleylines.rechargeTicks")
            .defineInRange("rechargeTicks", 24000, 1, 1728000);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private static final ModConfigSpec.Builder SERVER = new ModConfigSpec.Builder();
    public static final String RECHARGE_OVERRIDE_KEY = "rechargeTicksOverride";
    public static final String CAPACITY_MULTIPLIER_KEY = "nexusCapacityMultiplier";
    public static final String VISION_STYLE_KEY = "defaultVisionStyle";
    public static final List<String> VISION_STYLES = List.of("river", "crackle", "sparkles", "glow");
    private static final ModConfigSpec.ConfigValue<Object> RECHARGE_OVERRIDE = SERVER
            .comment("Full nexus recharge time in ticks, from 1 to 1728000. Zero uses the saved world profile.",
                    "Applied when the world starts. Elapsed recharge is settled at the previous rate.")
            .worldRestart()
            .translation("config.skysleylines.rechargeTicksOverride")
            .define(RECHARGE_OVERRIDE_KEY, (Object) 0, value -> value != null);
    private static final ModConfigSpec.ConfigValue<Object> CAPACITY_MULTIPLIER = SERVER
            .comment("Multiplies the base raw capacity of natural and authored nexuses on world start.",
                    "0.01 gives 1% of the original capacity; 1.0 uses the original capacity.",
                    "Use 0.0001 to 100 with at most four decimal places. Existing converted units are preserved.")
            .worldRestart()
            .translation("config.skysleylines.nexusCapacityMultiplier")
            .define(CAPACITY_MULTIPLIER_KEY, (Object) 0.01, value -> value != null);
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_VISION_STYLE = SERVER
            .comment("Ley Vision style for operator vision and unstyled grants. Applies to existing worlds too.",
                    "Allowed values: river, crackle, sparkles, glow")
            .translation("config.skysleylines.defaultVisionStyle")
            .define(VISION_STYLE_KEY, "river", value -> value instanceof String style && VISION_STYLES.contains(style));
    public static final ModConfigSpec SERVER_SPEC = SERVER.build();

    private LeyConfig() {}
    public static LeyProfile defaults() {
        return new LeyProfile(SPACING.get(), LINE_WIDTH.get(), NEXUS_RADIUS.get(), VERTICAL_REACH.get(), CAPACITY.get(), RECOVERY.get());
    }

    public static int rechargeOverride() {
        // Let the loader keep invalid supplied values so startup can reject them without resetting the ledger.
        if (RECHARGE_OVERRIDE.get() instanceof Integer ticks && validRechargeOverride(ticks)) return ticks;
        throw new IllegalArgumentException("rechargeTicksOverride must be an integer from 0 through 1728000");
    }

    public static boolean validRechargeOverride(int ticks) {
        return ticks >= 0 && ticks <= 1_728_000;
    }

    public static double configuredCapacityMultiplier() {
        return CAPACITY_MULTIPLIER.getRaw() instanceof Number value ? value.doubleValue() : Double.NaN;
    }

    public static int capacityScale() {
        double multiplier = configuredCapacityMultiplier();
        if (validCapacityMultiplier(multiplier)) return capacityScale(multiplier);
        throw new IllegalArgumentException("nexusCapacityMultiplier must be a number from 0.0001 through 100 with at most four decimal places");
    }

    public static boolean validCapacityMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier < 0.0001 || multiplier > 100) return false;
        try {
            capacityScale(multiplier);
            return true;
        } catch (ArithmeticException invalidPrecision) {
            return false;
        }
    }

    private static int capacityScale(double multiplier) {
        return BigDecimal.valueOf(multiplier).multiply(BigDecimal.valueOf(NexusLedger.CAPACITY_SCALE_ONE)).intValueExact();
    }
}
