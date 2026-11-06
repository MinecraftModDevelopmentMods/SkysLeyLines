package zone.moddev.mc.skysleylines.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysleylines.api.*;
import zone.moddev.mc.skysleylines.api.PlayerPowerService.Settings;
import zone.moddev.mc.skysleylines.core.NexusLedger;
import static org.junit.jupiter.api.Assertions.*;

class PlayerEnergyTest {
    private static final ResourceLocation AETHER = ResourceLocation.parse("test:aether");
    private static final ResourceLocation MANA = ResourceLocation.parse("test:mana");
    private static final NexusId NODE = new NexusId("minecraft:overworld", 0, 0, 1, 1);
    private static final long EXACT = 9_007_199_254_740_993L;

    @Test void fractionalRatesAccumulateOnlyCompletedUnits() {
        var total = MagicAmount.of(7);
        var speed = MagicAmount.fraction(3, 2);
        assertEquals(MagicAmount.ONE, EnergyJobs.earned(total, speed, 1, MagicAmount.ONE));
        assertEquals(MagicAmount.of(3), EnergyJobs.earned(total, speed, 2, MagicAmount.ONE));
        assertEquals(MagicAmount.of(4), EnergyJobs.earned(total, speed, 3, MagicAmount.ONE));
        assertEquals(total, EnergyJobs.earned(total, speed, 5, MagicAmount.ONE));

        var rate = MagicAmount.fraction(3, 2);
        var raw = MagicAmount.of(2);
        var rawPerTick = MagicAmount.fraction(1, 2);
        assertEquals(MagicAmount.ZERO, EnergyJobs.earned(raw, rawPerTick, 1, rate));
        assertEquals(MagicAmount.fraction(2, 3), EnergyJobs.earned(raw, rawPerTick, 2, rate));
        assertEquals(MagicAmount.fraction(4, 3), EnergyJobs.earned(raw, rawPerTick, 3, rate));
        assertEquals(raw, EnergyJobs.earned(raw, rawPerTick, 4, rate));
    }

    @Test void compressedSchemaFourRetainsExactDormantAndDroppedBalancesAndRecharge() throws Exception {
        var data = new LeyWorldData(LeyProfile.DEFAULT, () -> 0);
        data.configureCurrencies(Map.of(AETHER, MagicAmount.of(1000), MANA, MagicAmount.ONE));
        data.applyRechargeOverride(200, () -> {});
        var energy = new PlayerEnergyData();
        var player = UUID.randomUUID();
        var drop = UUID.randomUUID();
        energy.put(player, AETHER, new PlayerEnergyData.StoredPool(new Settings(false, 4, MagicAmount.fraction(1, 3)), EXACT));
        energy.put(player, MANA, new PlayerEnergyData.StoredPool(new Settings(true, 9, null), 0));
        energy.drops.put(drop, new PlayerEnergyData.Drop(player, ResourceLocation.parse("minecraft:the_nether"),
                new Vec3(1.25, 74, -0.5), 6000, true, Map.of(AETHER, 100L, MANA, 1L)));
        var tag = data.save(new CompoundTag(), null);
        assertEquals(3, tag.getInt("schema"));
        tag.putInt("schema", 4);
        tag.put("playerEnergy", energy.write());
        var compressed = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, compressed);
        var restored = LeyWorldData.load(NbtIo.readCompressed(new ByteArrayInputStream(compressed.toByteArray()),
                NbtAccounter.unlimitedHeap()), () -> 0);
        restored.configureCurrencies(Map.of(MANA, MagicAmount.ONE));
        assertEquals(200, restored.ledger().rechargeTicks());
        assertFalse(restored.ledger().activeCurrencies().contains(AETHER));
        assertEquals(energy.players, restored.playerEnergy.players);
        assertEquals(energy.drops, restored.playerEnergy.drops);
        assertEquals(tag, restored.save(new CompoundTag(), null));
    }

    @Test void absentBackupCannotUpgradeTheSaveAndMalformedPlayerDataFailsClosed() {
        var data = new LeyWorldData(LeyProfile.DEFAULT, () -> 0);
        var tag = data.save(new CompoundTag(), null);
        assertThrows(IllegalStateException.class, data::preparePlayerSave);
        assertEquals(tag, data.save(new CompoundTag(), null));
        tag.putInt("schema", 4);
        tag.putInt("effectiveRechargeTicks", data.ledger().rechargeTicks());
        assertThrows(IllegalArgumentException.class, () -> LeyWorldData.load(tag, () -> 0));
    }

    @Test void nodeDepositIsPreparedUnderTheSharedGuardAndRespectsExactCapacity() {
        var ledger = new NexusLedger(24000);
        var rate = MagicAmount.fraction(3, 2);
        ledger.bindCurrencies(Map.of(AETHER, rate));
        ledger.restore(NODE, new NexusLedger.Balance(10, MagicAmount.of(8), Map.of(AETHER, 1L), 0));
        long revision = ledger.revision();
        assertTrue(ledger.exclusive(false, () -> {
            assertEquals(PowerResult.Status.REENTRANT, ledger.transact(NODE, 10, 0,
                    new PowerRequest(0, 0, 0, PowerRequest.Operation.SPEND, AETHER, MagicAmount.ONE,
                            new PowerRequest.Consumer(ResourceLocation.parse("test:guard"))), NexusLedger.NO_HOOKS).status());
            assertThrows(IllegalArgumentException.class, () -> ledger.prepareDeposit(NODE, 10, 0, AETHER, 3));
            var commit = ledger.prepareDeposit(NODE, 10, 0, AETHER, 2);
            assertEquals(1, ledger.quote(NODE, 10, 0).currency(AETHER));
            assertEquals(revision, ledger.revision());
            commit.run();
            return true;
        }));
        assertEquals(3, ledger.quote(NODE, 10, 0).currency(AETHER));
        assertEquals(MagicAmount.of(10), ledger.quote(NODE, 10, 0).total());
        assertEquals(revision + 1, ledger.revision());
    }
}
