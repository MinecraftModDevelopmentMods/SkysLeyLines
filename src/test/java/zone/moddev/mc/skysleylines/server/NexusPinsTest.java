package zone.moddev.mc.skysleylines.server;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysleylines.api.*;
import zone.moddev.mc.skysleylines.core.LeyNetwork;
import zone.moddev.mc.skysleylines.core.NexusLedger;
import static org.junit.jupiter.api.Assertions.*;
import static zone.moddev.mc.skysleylines.core.NexusLedger.RecoveryStatus.*;

class NexusPinsTest {
    private static final String DIM = NexusPins.DIMENSION;
    private static final ResourceLocation PIN = ResourceLocation.parse("test:deep");
    private static final ResourceLocation CURRENCY = ResourceLocation.parse("test:aether");
    private static final PowerRequest.Consumer CONSUMER = new PowerRequest.Consumer(ResourceLocation.parse("test:recovery"));

    @Test void chunkQueryUsesHalfOpenBoundsAndNeverSamplesTerrain() {
        var profile = new LeyProfile(257, 32, 64, 128, 10000, 24000);
        var field = new LeyNetwork(44, DIM, profile, (x, z) -> fail("Chunk query touched height"), true);
        int found = 0;
        for (int x = -18; x <= 18; x++) for (int z = -18; z <= 18; z++) {
            var sites = field.nexusesInChunk(x, z);
            for (var site : sites) {
                assertEquals(x, Math.floorDiv((int) Math.floor(site.x()), 16));
                assertEquals(z, Math.floorDiv((int) Math.floor(site.z()), 16));
                assertEquals(site, field.nearestNexus((int) Math.round(site.x()), (int) Math.round(site.z()), 1).orElseThrow());
            }
            assertEquals(sites.size(), sites.stream().map(Nexus::id).distinct().count());
            assertEquals(sites, sites.stream().sorted(java.util.Comparator.comparing(Nexus::id)).toList());
            found += sites.size();
        }
        assertTrue(found > 0);
    }

    @Test void deepPinsShareChunkAndUseTheirOwnHeight() {
        var one = pin(PIN, -16, -40, -1, NexusPins.State.ACTIVE);
        var two = pin(ResourceLocation.parse("test:other"), -1, 40, -16, NexusPins.State.ACTIVE);
        var baseline = new LeyNetwork(44, DIM, LeyProfile.DEFAULT, (x, z) -> OptionalInt.of(100), true);
        var field = new LeyNetwork(44, DIM, LeyProfile.DEFAULT, (x, z) -> OptionalInt.of(100), true, List.of(two.nexus(), one.nexus()));
        var chunk = field.nexusesInChunk(-1, -1);
        assertTrue(chunk.containsAll(List.of(one.nexus(), two.nexus())));
        assertThrows(UnsupportedOperationException.class, () -> chunk.add(one.nexus()));
        var deep = field.sample(-16, -40, -1);
        assertEquals(one.nexus(), deep.nexus().orElseThrow());
        assertEquals(0.85, deep.nexusStrength(), 1e-12);
        assertTrue(deep.canDrawPower());
        assertEquals(deep.naturalStrength(), field.sampleColumn(-16, -1).naturalStrengthAt(-40), 1e-12);
        assertEquals(baseline.sample(3000, 100, -3000), field.sample(3000, 100, -3000));
        assertTrue(field.vision(-16, -1).crossings().stream().anyMatch(site -> site.y() == -40));
    }

    @Test void straddlingChunkUsesItsMarginOrFindsTheSiteInTheAdjacentTile() {
        long seed = -4965128775892001975L;
        var wide = new LeyNetwork(seed, DIM, new LeyProfile(257, 32, 64, 128, 10000, 24000),
                (x, z) -> fail("Chunk query touched height"), true);
        var narrow = new LeyNetwork(seed, DIM, new LeyProfile(257, 1, 1, 128, 10000, 24000),
                (x, z) -> fail("Chunk query touched height"), true);

        // The chunk starts at z=256, and the crossing is beyond the narrow margin of tile zero.
        var wideSites = wide.nexusesInChunk(-38, 16);
        assertEquals(1, wide.cachedTileCount());
        assertTrue(wideSites.contains(wide.nearestNexus(-601, 261, 2).orElseThrow()));
        var narrowSites = narrow.nexusesInChunk(-38, 16);
        assertTrue(narrowSites.contains(narrow.nearestNexus(-601, 261, 2).orElseThrow()));
    }

    @Test void aPinCanRemainIsolatedWhenNoSeededLineIsWithinReach() {
        var profile = new LeyProfile(65536, 32, 64, 128, 10000, 24000);
        var pin = pin(PIN, 0, -40, 0, NexusPins.State.ACTIVE).nexus();
        var field = new LeyNetwork(44, DIM, profile, (x, z) -> OptionalInt.of(100), true, List.of(pin));
        assertTrue(field.connections(pin).isEmpty());
        assertEquals(pin, field.sample(0, -40, 0).nexus().orElseThrow());
        assertTrue(field.vision(0, 0).reaches().isEmpty());
    }

    @Test void backupFailureDoesNotStageOrUpgradeAndSchemaFiveRetainsRetiredFunds() {
        var data = new LeyWorldData(LeyProfile.DEFAULT, () -> 100);
        data.configureCurrencies(Map.of(CURRENCY, MagicAmount.of(3)));
        var pin = pin(PIN, 0, 20, 0, NexusPins.State.STAGED_ADD);
        var before = data.save(new CompoundTag(), null);
        assertThrows(IllegalStateException.class, () -> data.stagePin(pin));
        assertEquals(before, data.save(new CompoundTag(), null));
        assertTrue(data.activePins().isEmpty());

        var staged = new NexusPins();
        staged.put(pin);
        before.putInt("schema", 5);
        before.putInt("effectiveRechargeTicks", data.profile().rechargeTicks());
        before.put("playerEnergy", new PlayerEnergyData().write());
        before.put("pins", staged.write());
        var loaded = LeyWorldData.load(before, () -> 100);
        assertTrue(loaded.activePins().isEmpty());
        assertTrue(loaded.pins.activateStaged());
        assertEquals(List.of(pin.nexus()), loaded.activePins());
        var account = pin.nexus().id();
        var capacity = pin.nexus().capacity(loaded.profile());
        loaded.ledger().restore(account, new NexusLedger.Balance(capacity, MagicAmount.ONE, Map.of(CURRENCY, 2L), 0));
        loaded.stagePin(pin.state(NexusPins.State.STAGED_REMOVE));
        assertEquals(List.of(pin.nexus()), loaded.activePins());
        assertTrue(loaded.pins.activateStaged());
        var saved = loaded.save(new CompoundTag(), null);
        var restored = LeyWorldData.load(saved, () -> 100);
        assertTrue(restored.activePins().isEmpty());
        assertEquals(2, restored.ledger().entries().get(account).currencies().get(CURRENCY));
        assertThrows(IllegalArgumentException.class, () -> restored.pins.checkAdd(pin));
        restored.configureCurrencies(Map.of(CURRENCY, MagicAmount.of(4)));
        assertThrows(IllegalStateException.class, restored::previewMigration);
        assertEquals("test:deep", saved.getList("balances", Tag.TAG_COMPOUND).getCompound(0).getString("pin"));
        restored.applyPoolSettings(0, 100, () -> {});
        var scaled = LeyWorldData.load(restored.save(new CompoundTag(), null), () -> 100);
        assertTrue(scaled.activePins().isEmpty());
        assertEquals(NexusPins.State.RETIRED, scaled.pins.get(PIN).state());
        assertEquals(2, scaled.ledger().quote(account, capacity, 100).currency(CURRENCY));
        assertEquals(85, scaled.ledger().quote(account, capacity, 100).capacity());
    }

    @Test void recoveryMovesInactiveWholeUnitsExactlyAndCommitsBothOrNeither() {
        var ledger = new NexusLedger(24000);
        var pin = NexusId.pinned(DIM, PIN);
        var dest = new NexusId(DIM, 0, 0, 0, 1);
        ledger.restoreRate(CURRENCY, MagicAmount.of(3));
        ledger.restore(pin, new NexusLedger.Balance(10, MagicAmount.of(9), Map.of(CURRENCY, 3L), 0));
        ledger.restore(dest, new NexusLedger.Balance(10, MagicAmount.fraction(28, 3), Map.of(), 0));
        var before = ledger.entries();
        var posts = new AtomicInteger();
        class Hooks implements NexusLedger.RecoveryHooks {
            boolean accept;
            @Override public boolean before(NexusRecoveryEvent.Receipt receipt) {
                assertEquals(2, receipt.destination().after().currency(CURRENCY));
                assertEquals(MagicAmount.of(10), receipt.destination().after().total());
                assertEquals(REENTRANT, move(ledger, pin, dest, 2, this));
                return accept;
            }
            @Override public void after(NexusRecoveryEvent.Receipt receipt) { posts.incrementAndGet(); }
            @Override public void failure(RuntimeException failure) { fail(failure); }
        }
        var hooks = new Hooks();
        assertEquals(CAPACITY, move(ledger, pin, dest, 3, hooks));
        assertEquals(CANCELLED, move(ledger, pin, dest, 2, hooks));
        assertEquals(before, ledger.entries());
        hooks.accept = true;
        assertEquals(SUCCESS, move(ledger, pin, dest, 2, hooks));
        assertEquals(1, posts.get());
        assertEquals(1, ledger.entries().get(pin).currencies().get(CURRENCY));
        assertEquals(2, ledger.entries().get(dest).currencies().get(CURRENCY));
    }

    private static NexusLedger.RecoveryStatus move(NexusLedger ledger, NexusId pin, NexusId dest,
                                                     long units, NexusLedger.RecoveryHooks hooks) {
        return ledger.recover(pin, BlockPos.ZERO, dest, BlockPos.ZERO, 10, 0, CURRENCY, units, CONSUMER, hooks);
    }

    private static NexusPins.Pin pin(ResourceLocation id, int x, int y, int z, NexusPins.State state) {
        return new NexusPins.Pin(id, new BlockPos(x, y, z), 0.85, state);
    }
}
