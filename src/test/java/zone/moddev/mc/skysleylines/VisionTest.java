package zone.moddev.mc.skysleylines;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import com.electronwill.nightconfig.core.CommentedConfig;
import java.util.OptionalInt;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysleylines.api.LeyProfile;
import zone.moddev.mc.skysleylines.api.LeyVisionStyle;
import zone.moddev.mc.skysleylines.core.VisionGrants;
import zone.moddev.mc.skysleylines.core.VisionStyles;
import zone.moddev.mc.skysleylines.core.LeyNetwork;
import zone.moddev.mc.skysleylines.core.VisionGeometry;
import zone.moddev.mc.skysleylines.network.VisionPayload;

class VisionTest {
    private static LeyNetwork field(LeyProfile profile, boolean active) {
        return new LeyNetwork(-4965128775892001975L, "minecraft:overworld", profile, (x, z) -> {
            fail("Planar vision must not query terrain");
            return OptionalInt.empty();
        }, active);
    }

    @Test void visionShowsTheRealCrossingAndClipsItsLinesWithoutReadingTerrain() {
        var network = field(LeyProfile.DEFAULT, true);
        var nexus = network.nearestNexus(0, 0, 4096).orElseThrow();
        int x = 1166, z = -743;
        var patch = network.vision(x, z);
        assertTrue(patch.crossings().contains(new VisionGeometry.Crossing(nexus.x(), nexus.z(), (float) nexus.strength())));
        assertTrue(patch.reaches().stream().filter(reach -> {
            double dx = reach.x1() - reach.x0(), dz = reach.z1() - reach.z0();
            return Math.abs((nexus.x() - reach.x0()) * dz - (nexus.z() - reach.z0()) * dx) / Math.hypot(dx, dz) < 1e-8;
        }).count() >= 2);
        for (var reach : patch.reaches()) {
            assertTrue(Math.hypot(reach.x0() - x, reach.z0() - z) <= VisionGeometry.RADIUS + 1e-7);
            assertTrue(Math.hypot(reach.x1() - x, reach.z1() - z) <= VisionGeometry.RADIUS + 1e-7);
        }
        assertEquals(patch.reaches().size(), patch.reaches().stream().distinct().count());
        assertEquals(patch, field(LeyProfile.DEFAULT, true).vision(x, z));
        assertEquals(VisionGeometry.EMPTY, field(LeyProfile.DEFAULT, false).vision(x, z));
    }

    @Test void serverConfigCorrectsMissingAndInvalidStylesToRiver() {
        var config = CommentedConfig.inMemory();
        LeyConfig.SERVER_SPEC.correct(config);
        assertEquals("river", (String) config.get("defaultVisionStyle"));
        config.set("defaultVisionStyle", "glow");
        LeyConfig.SERVER_SPEC.correct(config);
        assertEquals("glow", (String) config.get("defaultVisionStyle"));
        config.set("defaultVisionStyle", "bad");
        LeyConfig.SERVER_SPEC.correct(config);
        assertEquals("river", (String) config.get("defaultVisionStyle"));
    }

    @Test void registrationValidatesCustomStylesAndKeepsCoreIdsReserved() {
        var styles = new VisionStyles();
        var id = ResourceLocation.parse("probe:goggles");
        var custom = new LeyVisionStyle(LeyVisionStyle.Pattern.CRACKLE, 0xffa011, 0x2299ff, 0.7f, 0.6f);
        styles.register(id, custom);
        assertSame(custom, styles.require(id));
        assertThrows(IllegalArgumentException.class, () -> styles.register(id, custom));
        assertThrows(IllegalArgumentException.class, () -> styles.register(ResourceLocation.parse("skysleylines:river"), custom));
        assertThrows(IllegalArgumentException.class, () -> styles.require(ResourceLocation.parse("probe:missing")));
        assertThrows(IllegalArgumentException.class, () -> new LeyVisionStyle(custom.pattern(), -1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new LeyVisionStyle(custom.pattern(), 0, 0, Float.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> new LeyVisionStyle(custom.pattern(), 0, 0, 1, 2));
        styles.freeze();
        assertThrows(IllegalStateException.class, () -> styles.register(ResourceLocation.parse("probe:potion"), custom));
    }

    @Test void newestStyledSourceWinsUntilExpiryEvenWhenOlderSourcesRenew() {
        var styles = new VisionStyles();
        var crackle = styles.require(ResourceLocation.parse("skysleylines:crackle"));
        var glow = styles.require(ResourceLocation.parse("skysleylines:glow"));
        var goggles = ResourceLocation.parse("probe:goggles");
        var potion = ResourceLocation.parse("probe:potion");
        var unstyled = ResourceLocation.parse("probe:unstyled");
        var grants = new VisionGrants();
        grants.grant(goggles, 10, 10, crackle);
        grants.grant(potion, 12, 5, glow);
        grants.grant(goggles, 14, 25, crackle);
        grants.grant(unstyled, 16, 100, null);
        assertSame(glow, grants.styleOr(VisionStyles.RIVER));
        grants.expire(17);
        assertSame(crackle, grants.styleOr(VisionStyles.RIVER));
        grants.revoke(goggles);
        assertSame(VisionStyles.RIVER, grants.styleOr(VisionStyles.RIVER));
        grants.expire(116);
        assertTrue(grants.isEmpty());
    }

    @Test void grantingAgainAfterExpiryCountsAsANewActivationEvenBeforeTheTickPrunesIt() {
        var styles = new VisionStyles();
        var crackle = styles.require(ResourceLocation.parse("skysleylines:crackle"));
        var glow = styles.require(ResourceLocation.parse("skysleylines:glow"));
        var first = ResourceLocation.parse("probe:first");
        var second = ResourceLocation.parse("probe:second");
        var grants = new VisionGrants();
        grants.grant(first, 0, 10, crackle);
        grants.grant(second, 1, 99, glow);
        grants.grant(first, 10, 20, crackle);
        assertSame(crackle, grants.styleOr(VisionStyles.RIVER));
    }

    @Test void visionPayloadRoundTripsAndRejectsOversizedGeometryBeforeReadingIt() {
        var dimension = ResourceLocation.parse("minecraft:overworld");
        var style = new LeyVisionStyle(LeyVisionStyle.Pattern.SPARKLES, 0xffbb88, 0x8877ff, 0.65f, 0.8f);
        var payload = new VisionPayload(dimension, field(LeyProfile.DEFAULT, true).vision(1166, -743), style);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            VisionPayload.CODEC.encode(buffer, payload);
            assertEquals(payload, VisionPayload.CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
            buffer.clear();
            buffer.writeResourceLocation(dimension);
            buffer.writeEnum(style.pattern());
            buffer.writeInt(style.lineRgb());
            buffer.writeInt(style.nexusRgb());
            buffer.writeFloat(style.opacity());
            buffer.writeFloat(style.scale());
            buffer.writeByte(32);
            buffer.writeByte(VisionGeometry.MAX_REACHES + 1);
            assertThrows(IllegalArgumentException.class, () -> VisionPayload.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
