package zone.moddev.mc.skysleylines;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.OptionalInt;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysleylines.api.LeyProfile;
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

    @Test void visionPayloadRoundTripsAndRejectsOversizedGeometryBeforeReadingIt() {
        var dimension = ResourceLocation.parse("minecraft:overworld");
        var payload = new VisionPayload(dimension, field(LeyProfile.DEFAULT, true).vision(1166, -743));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            VisionPayload.CODEC.encode(buffer, payload);
            assertEquals(payload, VisionPayload.CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
            buffer.clear();
            buffer.writeResourceLocation(dimension);
            buffer.writeByte(32);
            buffer.writeByte(VisionGeometry.MAX_REACHES + 1);
            assertThrows(IllegalArgumentException.class, () -> VisionPayload.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
