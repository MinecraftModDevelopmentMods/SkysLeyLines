package zone.moddev.mc.skysleylines;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysleylines.api.*;
import zone.moddev.mc.skysleylines.core.LeyNetwork;

class LeyNetworkTest {
    private static final long SEED = -4965128775892001975L;
    private static LeyNetwork network(long seed) {
        return new LeyNetwork(seed, "minecraft:overworld", LeyProfile.DEFAULT, (x,z) -> OptionalInt.of(70), true);
    }
    @Test void layoutVersionOneHasAFixedGoldenSite() {
        Nexus node=network(SEED).nearestNexus(0,0,4096).orElseThrow();
        assertEquals(new NexusId("minecraft:overworld",0,-1,1,0),node.id());
        assertEquals(1165.6918876521872,node.x(),1e-10);
        assertEquals(-743.3212163142174,node.z(),1e-10);
        assertEquals(0.95568635102886,node.strength(),1e-14);
    }
    @Test void intersectionIsStrongAndVerticalBandMatchesContract() {
        var field = network(SEED);
        var node = field.nearestNexus(0, 0, 4096).orElseThrow();
        int x = (int) Math.round(node.x()), z = (int) Math.round(node.z());
        var column = field.sampleColumn(x, z);
        double peak = column.naturalStrengthAt(70);
        assertTrue(peak > 0.45);
        assertEquals(peak / 2, column.naturalStrengthAt(134), 1e-12);
        assertEquals(peak / 2, column.naturalStrengthAt(6), 1e-12);
        assertEquals(0, column.naturalStrengthAt(198));
        assertEquals(0, column.naturalStrengthAt(-58));
        assertTrue(column.atHeight(70).canDrawPower());
        assertFalse(column.atHeight(198).canDrawPower());
        assertEquals(1 - peak, column.atHeight(70).dimensionalResistance());
    }
    @Test void traversalAndCacheEvictionCannotMoveSites() {
        var first = network(SEED); var second = network(SEED);
        List<int[]> points = new ArrayList<>();
        for (int i = -20; i <= 20; i++) points.add(new int[]{i * 2048 - 1, i * -517});
        List<Optional<Nexus>> expected = points.stream().map(p -> first.nearestNexus(p[0], p[1], 512)).toList();
        Collections.reverse(points);
        List<Optional<Nexus>> actual = new ArrayList<>(points.stream().map(p -> second.nearestNexus(p[0], p[1], 512)).toList());
        Collections.reverse(actual);
        assertEquals(expected, actual);
        for (int i = 0; i < 200; i++) first.sampleColumn(i * 4096, -i * 4096);
        assertTrue(first.cachedTileCount() <= 128);
        assertEquals(expected.get(20), first.nearestNexus(-1, 0, 512));
    }
    @Test void sameIntersectionHasSameIdentityAndStrengthAcrossSearchTiles() {
        var field = network(SEED);
        for (int boundary = -4; boundary <= 4; boundary++) for (int z = -4096; z <= 4096; z += 127) {
            int x = boundary * 2048;
            var near = field.nearestNexus(x, z, 128);
            if (near.isPresent()) assertEquals(near, field.nearestNexus(x, z, 4096));
        }
    }
    @Test void differentSeedsAndDimensionsGiveDifferentLayouts() {
        var a = network(SEED); var b = network(SEED + 1);
        var c = new LeyNetwork(SEED, "example:dimension", LeyProfile.DEFAULT, (x,z) -> OptionalInt.of(70), true);
        assertNotEquals(a.nearestNexus(0,0,4096), b.nearestNexus(0,0,4096));
        assertNotEquals(a.nearestNexus(0,0,4096), c.nearestNexus(0,0,4096));
    }
    @Test void disabledAndVoidQueriesDoNotFabricateTerrainFields() {
        AtomicInteger calls = new AtomicInteger();
        var disabled = new LeyNetwork(SEED, "minecraft:the_nether", LeyProfile.DEFAULT, (x,z) -> { calls.incrementAndGet(); return OptionalInt.of(70); }, false);
        assertFalse(disabled.sample(0,70,0).active());
        assertTrue(disabled.nearestNexus(0,0,512).isEmpty());
        assertEquals(0, calls.get());
        var empty = new LeyNetwork(SEED, "minecraft:overworld", LeyProfile.DEFAULT, (x,z) -> OptionalInt.empty(), true);
        assertFalse(empty.sample(0,70,0).active());
        assertFalse(empty.sample(0,70,0).canDrawPower());
    }
    @Test void samplerReusesNaturalHeightAndColumnHotLoopHasNoCallbacks() {
        AtomicInteger calls = new AtomicInteger();
        var field = new LeyNetwork(SEED, "minecraft:overworld", LeyProfile.DEFAULT, (x,z) -> { calls.incrementAndGet(); return OptionalInt.of(70); }, true);
        var column = field.sampleColumn(-2048,-1);
        for (int y=-64; y<320; y++) column.naturalStrengthAt(y);
        field.sampleColumn(-2048,-1);
        assertEquals(1, calls.get());
        for (int x=0; x<8300; x++) field.sampleColumn(x,0);
        assertTrue(field.cachedHeightCount() <= 8192);
    }
    @Test void parallelReadQueriesMatchSerialResults() throws Exception {
        var field = network(SEED);
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Callable<LeySample>> tasks = new ArrayList<>();
            for (int i=0; i<100; i++) { int x=i*71-3500; tasks.add(() -> field.sample(x,70,-x)); }
            var actual=workers.invokeAll(tasks);
            for (int i=0;i<100;i++) { int x=i*71-3500; assertEquals(field.sample(x,70,-x),actual.get(i).get()); }
        } finally { workers.shutdownNow(); }
    }
    @Test void searchLimitsAndProfileValidationAreExplicit() {
        assertThrows(IllegalArgumentException.class, () -> network(SEED).nearestNexus(0,0,4097));
        assertThrows(IllegalArgumentException.class, () -> network(SEED).nearestLine(0,0,0));
        assertThrows(IllegalArgumentException.class, () -> new LeyProfile(0,32,64,128,10000,24000));
        assertThrows(IllegalArgumentException.class, () -> new NexusId("minecraft:overworld",0,0,0,0));
    }
    @Test void spacingScalesTheSameSeededNetwork() {
        var large=network(SEED);
        var small=new LeyNetwork(SEED,"minecraft:overworld",new LeyProfile(1024,32,64,128,10000,24000),(x,z)->OptionalInt.of(70),true);
        var a=large.nearestNexus(0,0,4096).orElseThrow();
        var b=small.nearestNexus(0,0,2048).orElseThrow();
        assertEquals(a.id(),b.id());
        assertEquals(a.x()/2,b.x(),1e-10);
        assertEquals(a.z()/2,b.z(),1e-10);
    }
}
