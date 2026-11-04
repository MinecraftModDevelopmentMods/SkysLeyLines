package zone.moddev.mc.skysleylines;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysleylines.api.*;
import zone.moddev.mc.skysleylines.core.LeyNetwork;
import zone.moddev.mc.skysleylines.item.*;
import zone.moddev.mc.skysleylines.network.*;

class DowsingTest {
    @Test void realNexusCrossesOnlyInsideItsHorizontalAndVerticalField() {
        var field = new LeyNetwork(-4965128775892001975L, "minecraft:overworld", LeyProfile.DEFAULT, (x,z) -> OptionalInt.of(70), true);
        var nexus = field.nearestNexus(0, 0, 4096).orElseThrow();
        int x = (int) Math.round(nexus.x()), z = (int) Math.round(nexus.z());
        assertEquals(DowsingReading.NEXUS, DowsingReading.sample(field, x, 70, z, 0).mode());
        assertEquals(DowsingReading.QUIET, DowsingReading.sample(field, x, 198, z, 0).mode());
        assertNotEquals(DowsingReading.NEXUS, DowsingReading.sample(field, x + 100, 70, z, 0).mode());
    }
    @Test void linesPointTowardsNearestPointAndAlignOnTheLineWithoutFlipping() {
        var field = field(new LineReading(10, 0, 10, 0, 1));
        assertEquals(new DowsingReading(DowsingReading.LINE, -90), DowsingReading.sample(field, 0, 70, 0, 0));
        var onLine = field(new LineReading(0, 0, 0, 0, 1));
        assertEquals(0, DowsingReading.sample(onLine, 0, 70, 0, 0).bearing());
        assertEquals(-180, DowsingMotion.wrap(DowsingReading.sample(onLine, 0, 70, 0, 180).bearing()));
    }
    @Test void emptySearchAndInactiveDimensionsStayQuiet() {
        assertEquals(DowsingReading.STILL, DowsingReading.sample(field(null), 0, 70, 0, 0));
        var inactive = new LeyNetwork(1, "minecraft:the_nether", LeyProfile.DEFAULT, (x,z) -> OptionalInt.of(70), false);
        assertEquals(DowsingReading.STILL, DowsingReading.sample(inactive, 0, 70, 0, 0));
    }
    @Test void turningUsesShortestArcAndCrossingIsSmoothThenUncrosses() {
        var motion = new DowsingMotion();
        for (int i=0;i<30;i++) motion.tick(new DowsingReading(DowsingReading.LINE, 175));
        assertEquals(0, motion.angle(true,175,1), 0.01);
        motion.tick(new DowsingReading(DowsingReading.LINE,-175));
        assertTrue(motion.angle(true,175,1) > 0 && motion.angle(true,175,1) < 10);
        float before = motion.angle(true,175,0);
        motion.tick(new DowsingReading(DowsingReading.NEXUS,0));
        assertTrue(motion.angle(true,175,1) < 50);
        for(int i=0;i<40;i++) motion.tick(new DowsingReading(DowsingReading.NEXUS,0));
        assertEquals(50,motion.angle(true,0,1),0.01);
        assertEquals(-50,motion.angle(false,0,1),0.01);
        for(int i=0;i<40;i++) motion.tick(DowsingReading.STILL);
        assertEquals(0,motion.angle(true,0,1),0.01);
        assertTrue(Float.isFinite(before));
    }
    @Test void staleOrDifferentDimensionPacketsNeverKeepCrossedRods() {
        var id=UUID.randomUUID(); var overworld=ResourceLocation.parse("minecraft:overworld");
        DowsingSignals.clear();
        var crossed=new DowsingReading(DowsingReading.NEXUS,0);
        DowsingSignals.receive(new DowsingPayload(id,overworld,crossed),100);
        assertEquals(crossed,DowsingSignals.read(id,overworld,120));
        assertEquals(DowsingReading.STILL,DowsingSignals.read(id,overworld,121));
        assertEquals(DowsingReading.STILL,DowsingSignals.read(id,ResourceLocation.parse("minecraft:the_nether"),105));
        assertEquals(DowsingReading.STILL,DowsingSignals.read(id,overworld,99));
        DowsingSignals.clear();
        assertEquals(DowsingReading.STILL,DowsingSignals.read(id,overworld,105));
    }
    @Test void malformedNetworkHintsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new DowsingReading(3,0));
        assertThrows(IllegalArgumentException.class,()->new DowsingReading(1,Float.NaN));
        assertThrows(IllegalArgumentException.class,()->new DowsingReading(1,181));
    }
    private static LeyFieldSampler field(LineReading line) {
        return new LeyFieldSampler() {
            public LeyColumn sampleColumn(int x,int z) { return new LeyColumn(true,70,128,0,0,Optional.empty(),100,1000); }
            public Optional<Nexus> nearestNexus(int x,int z,int radius) { return Optional.empty(); }
            public Optional<LineReading> nearestLine(int x,int z,int radius) { assertEquals(256,radius); return Optional.ofNullable(line); }
        };
    }
}
