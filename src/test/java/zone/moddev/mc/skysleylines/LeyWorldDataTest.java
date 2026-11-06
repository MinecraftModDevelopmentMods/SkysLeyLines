package zone.moddev.mc.skysleylines;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysleylines.api.*;
import zone.moddev.mc.skysleylines.core.LeySaveLimits;
import zone.moddev.mc.skysleylines.core.NexusLedger;
import zone.moddev.mc.skysleylines.server.LeyWorldData;

class LeyWorldDataTest {
    @Test void capacityMultiplierPreservesConvertedFundsAndRechargeUsesTheNewBudget() {
        var tick = new AtomicLong(100);
        var data = new LeyWorldData(LeyProfile.DEFAULT, tick::get);
        var node = NexusLedgerTest.NODE;
        var currency = NexusLedgerTest.MANA;
        data.configureCurrencies(Map.of(currency, MagicAmount.of(10)));
        var oldLedger = data.ledger();
        int baseCapacity = 7000;
        assertTrue(oldLedger.transact(node, baseCapacity, 100,
                NexusLedgerTest.request(PowerRequest.Operation.CONVERT, currency, MagicAmount.of(80)), NexusLedger.NO_HOOKS).succeeded());
        var before = data.save(new CompoundTag(), null);
        assertThrows(IllegalStateException.class, () -> data.applyPoolSettings(0, 100, () -> {
            throw new IllegalStateException("test backup failure");
        }));
        assertEquals(before, data.save(new CompoundTag(), null));
        data.applyPoolSettings(0, 100, () -> assertEquals(7000, oldLedger.quote(node, baseCapacity, 100).capacity()));
        var scaled = data.save(new CompoundTag(), null);
        assertEquals(6, scaled.getInt("schema"));
        assertFalse(scaled.contains("pins"));
        assertFalse(scaled.contains("playerEnergy"));
        var loaded = LeyWorldData.load(scaled, tick::get);
        assertEquals(LeyProfile.DEFAULT, loaded.profile());
        loaded.configureCurrencies(Map.of(currency, MagicAmount.of(10)));
        var ledger = loaded.ledger();
        var overfilled = ledger.quote(node, baseCapacity, 1000);
        assertEquals(70, overfilled.capacity());
        assertEquals(800, overfilled.currency(currency));
        assertEquals(MagicAmount.ZERO, overfilled.raw());
        assertTrue(ledger.transact(node, baseCapacity, 1000,
                NexusLedgerTest.request(PowerRequest.Operation.RETURN_TO_RAW, currency, MagicAmount.of(50)), NexusLedger.NO_HOOKS).succeeded());
        assertEquals(MagicAmount.of(5), ledger.quote(node, baseCapacity, 2000).raw());
        assertTrue(ledger.transact(node, baseCapacity, 2000,
                NexusLedgerTest.request(PowerRequest.Operation.SPEND, currency, MagicAmount.of(450)), NexusLedger.NO_HOOKS).succeeded());
        assertEquals(MagicAmount.fraction(45, 2), ledger.quote(node, baseCapacity, 8000).raw());
        assertEquals(MagicAmount.of(40), ledger.quote(node, baseCapacity, 14000).raw());
        assertEquals(MagicAmount.of(70), ledger.quote(node, baseCapacity, 14000).total());
        var untouched = new NexusId("minecraft:overworld", 0, 0, 0, 1);
        assertEquals(MagicAmount.of(70), ledger.quote(untouched, baseCapacity, 14000).raw());
        tick.set(14000);
        loaded.applyPoolSettings(0, NexusLedger.CAPACITY_SCALE_ONE, () -> {});
        var reset = loaded.save(new CompoundTag(), null);
        assertEquals(6, reset.getInt("schema"));
        var restored = LeyWorldData.load(reset, tick::get).ledger();
        assertEquals(7000, restored.quote(node, baseCapacity, 14000).capacity());
        assertEquals(MagicAmount.of(70), restored.quote(node, baseCapacity, 14000).total());
    }

    @Test void compressedSaveAcceptsTheExactNbtLimitAndRejectsTheNextDigitBeforeBinding() throws Exception {
        var data = new LeyWorldData(LeyProfile.DEFAULT, () -> 0);
        var id = NexusLedgerTest.MANA;
        var boundary = new MagicAmount(BigInteger.TEN.pow(LeySaveLimits.MAX_STRING_BYTES - 1), BigInteger.ONE);
        data.configureCurrencies(Map.of(id, boundary));
        var encoded = new ByteArrayOutputStream();
        NbtIo.writeCompressed(data.save(new CompoundTag(), null), encoded);
        var decoded = NbtIo.readCompressed(new ByteArrayInputStream(encoded.toByteArray()), NbtAccounter.unlimitedHeap());
        assertEquals(boundary, LeyWorldData.load(decoded, () -> 0).ledger().rates().get(id));
        var revision = data.ledger().revision();
        var tooLarge = boundary.multiply(MagicAmount.of(10));
        var failure = assertThrows(ArithmeticException.class,
                () -> data.configureCurrencies(Map.of(id, tooLarge, NexusLedgerTest.AETHER, MagicAmount.ONE)));
        assertTrue(failure.getMessage().contains(id.toString()));
        assertEquals(Map.of(id, boundary), data.ledger().rates());
        assertEquals(revision, data.ledger().revision());
    }

    @Test void rechargeSettlesAtOldRateThenPersistsTheNewRateAndTheReset() {
        var tick = new AtomicLong(100);
        var profile = new LeyProfile(2048, 32, 64, 128, 10000, 200);
        var data = new LeyWorldData(profile, tick::get);
        var node = NexusLedgerTest.NODE;
        var currency = NexusLedgerTest.MANA;
        data.configureCurrencies(Map.of(currency, MagicAmount.of(10)));
        assertTrue(NexusLedgerTest.tx(data.ledger(), 100, PowerRequest.Operation.CONVERT, 20).succeeded());
        assertTrue(NexusLedgerTest.tx(data.ledger(), 100, PowerRequest.Operation.SPEND, 150).succeeded());
        var before = data.save(new CompoundTag(), null);
        assertEquals(2, before.getInt("schema"));
        tick.set(103);
        assertThrows(IllegalStateException.class, () -> data.applyRechargeOverride(80, () -> {
            throw new IllegalStateException("test backup failure");
        }));
        assertEquals(before, data.save(new CompoundTag(), null));
        var originalEntries = data.ledger().entries();
        data.applyRechargeOverride(80, () -> {
            assertEquals(200, data.ledger().rechargeTicks());
            assertEquals(originalEntries, data.ledger().entries());
        });
        var upgraded = data.save(new CompoundTag(), null);
        assertEquals(3, upgraded.getInt("schema"));
        var loaded = LeyWorldData.load(upgraded, tick::get);
        assertEquals(profile, loaded.profile());
        assertEquals(80, loaded.ledger().rechargeTicks());
        assertEquals(MagicAmount.fraction(3, 10), loaded.ledger().quote(node, 20, 103).raw());
        assertEquals(50, loaded.ledger().quote(node, 20, 103).currency(currency));
        tick.set(106);
        loaded.applyRechargeOverride(0, () -> {});
        var reset = loaded.save(new CompoundTag(), null);
        assertEquals(3, reset.getInt("schema"));
        var restored = LeyWorldData.load(reset, tick::get);
        assertEquals(200, restored.ledger().rechargeTicks());
        assertEquals(MagicAmount.fraction(21, 20), restored.ledger().quote(node, 20, 106).raw());
        assertEquals(MagicAmount.fraction(23, 20), restored.ledger().quote(node, 20, 107).raw());
    }

    @Test void currencyIdBoundaryRoundTripsAndAnOversizedIdCannotRegister() throws Exception {
        String path = "x".repeat(LeySaveLimits.MAX_STRING_BYTES - 2);
        var id = ResourceLocation.fromNamespaceAndPath("p", path);
        var data = new LeyWorldData(LeyProfile.DEFAULT, () -> 0);
        data.configureCurrencies(Map.of(id, MagicAmount.ONE));
        var encoded = new ByteArrayOutputStream();
        NbtIo.writeCompressed(data.save(new CompoundTag(), null), encoded);
        var decoded = NbtIo.readCompressed(new ByteArrayInputStream(encoded.toByteArray()), NbtAccounter.unlimitedHeap());
        assertEquals(Map.of(id, MagicAmount.ONE), LeyWorldData.load(decoded, () -> 0).ledger().rates());
        var oversized = ResourceLocation.fromNamespaceAndPath("p", path + "x");
        var registry = new ParadigmRegistry();
        var definition = new Paradigm<>(NexusLedgerTest.MANA, null,
                java.util.List.of(new Paradigm.Currency(oversized, () -> MagicAmount.ONE)));
        assertThrows(IllegalArgumentException.class, () -> registry.register(definition));
        assertTrue(registry.definitions().isEmpty());
    }

    @Test void unencodableProjectedRawCannotCommitThroughSpendingMigrationOrRecharge() {
        var ledger = new NexusLedger(40);
        var currency = NexusLedgerTest.MANA;
        ledger.bindCurrencies(Map.of(currency, MagicAmount.ONE));
        var denominator = BigInteger.TEN.pow(LeySaveLimits.MAX_STRING_BYTES).subtract(BigInteger.ONE);
        var raw = new MagicAmount(BigInteger.ONE, denominator);
        ledger.restore(NexusLedgerTest.NODE, new NexusLedger.Balance(20, raw, Map.of(currency, 1L), 0));
        var original = ledger.entries();
        assertEquals(PowerResult.Status.OVERFLOW, NexusLedgerTest.tx(ledger, 1, PowerRequest.Operation.SPEND, 1).status());
        assertThrows(ArithmeticException.class, () -> ledger.previewMigration(Map.of(currency, MagicAmount.of(2)), 1));
        assertThrows(ArithmeticException.class, () -> ledger.changeRechargeTicks(20, 1, () -> fail("Backup before validation")));
        assertEquals(original, ledger.entries());
        assertEquals(40, ledger.rechargeTicks());
    }
    @Test void profileAndDepletedReserveRoundTripWithoutDefaultSubstitution() {
        var profile=new LeyProfile(4096,48,96,128,20000,24000);
        var original=new LeyWorldData(profile,()->100);
        var id=new NexusId("minecraft:overworld",-1,0,0,1);
        original.configureCurrencies(java.util.Map.of(NexusLedgerTest.MANA, MagicAmount.of(10)));
        original.ledger().transact(id,10000,100,NexusLedgerTest.request(PowerRequest.Operation.CONVERT,NexusLedgerTest.MANA,MagicAmount.of(5000)),zone.moddev.mc.skysleylines.core.NexusLedger.NO_HOOKS);
        var loaded=LeyWorldData.load(original.save(new CompoundTag(),null),()->100);
        assertEquals(profile,loaded.profile());
        assertNotEquals(LeyProfile.DEFAULT,loaded.profile());
        assertEquals(original.ledger().entries(),loaded.ledger().entries());
    }
    @Test void queryDoesNotDirtyDataOrChangeEncodedState() {
        var original=new LeyWorldData(LeyProfile.DEFAULT,()->0);
        var id=new NexusId("minecraft:overworld",-1,0,0,1);
        var before=original.save(new CompoundTag(),null);
        original.ledger().quote(id,10000,500);
        assertFalse(original.isDirty());
        assertEquals(before,original.save(new CompoundTag(),null));
    }
    @Test void unknownVersionsAndMissingFieldsCannotBecomeFreshDefaults() {
        var tag=new LeyWorldData(LeyProfile.DEFAULT,()->0).save(new CompoundTag(),null);
        tag.putInt("schema",99);
        assertThrows(IllegalStateException.class,()->LeyWorldData.load(tag,()->0));
        tag.putInt("schema",1); tag.remove("profile");
        assertThrows(IllegalArgumentException.class,()->LeyWorldData.load(tag,()->0));
    }
    @Test void malformedListCannotSilentlyDiscardExistingBalances() {
        var tag=new LeyWorldData(LeyProfile.DEFAULT,()->0).save(new CompoundTag(),null);
        var invalid=new ListTag(); invalid.add(StringTag.valueOf("not a balance")); tag.put("balances",invalid);
        assertThrows(IllegalArgumentException.class,()->LeyWorldData.load(tag,()->0));
    }
    @Test void fullCurrencyAccountAndMissingModRetainBackingAcrossReload() {
        var data=new LeyWorldData(LeyProfile.DEFAULT,()->0);
        data.configureCurrencies(java.util.Map.of(NexusLedgerTest.MANA,MagicAmount.of(10)));
        data.ledger().transact(NexusLedgerTest.NODE,20,0,NexusLedgerTest.request(PowerRequest.Operation.CONVERT,NexusLedgerTest.MANA,MagicAmount.of(20)),zone.moddev.mc.skysleylines.core.NexusLedger.NO_HOOKS);
        var loaded=LeyWorldData.load(data.save(new CompoundTag(),null),()->100000);
        loaded.configureCurrencies(java.util.Map.of());
        assertEquals(200,loaded.ledger().quote(NexusLedgerTest.NODE,20,100000).currency(NexusLedgerTest.MANA));
        assertEquals(MagicAmount.ZERO,loaded.ledger().quote(NexusLedgerTest.NODE,20,100000).raw());
        assertEquals(MagicAmount.of(20),loaded.ledger().quote(NexusLedgerTest.NODE,20,100000).total());
    }
    @Test void legacyUpgradeKeepsExactFractionTimestampProfileAndIdentity() {
        var tag=new LeyWorldData(LeyProfile.DEFAULT,()->100).save(new CompoundTag(),null);
        tag.putInt("schema",1); tag.remove("rates"); tag.remove("revision");
        var row=new CompoundTag(); row.putString("dimension","minecraft:overworld");
        row.putInt("ax",-1); row.putInt("az",0); row.putInt("bx",0); row.putInt("bz",1);
        row.putInt("capacity",20); row.putInt("available",7); row.putLong("tick",100); row.putLong("remainder",125);
        var rows=new ListTag(); rows.add(row); tag.put("balances",rows);
        var loaded=LeyWorldData.load(tag,()->100);
        assertEquals(MagicAmount.of(7).add(MagicAmount.fraction(125,24000)),loaded.ledger().quote(NexusLedgerTest.NODE,20,100).raw());
        assertTrue(loaded.isDirty()); assertEquals(2,loaded.save(new CompoundTag(),null).getInt("schema"));
        row.putLong("remainder",24000);
        assertThrows(IllegalArgumentException.class,()->LeyWorldData.load(tag,()->100));
    }
    @Test void duplicateRatesInvalidAmountsAndOverCapacityCannotResetData() {
        var data=new LeyWorldData(LeyProfile.DEFAULT,()->0); data.configureCurrencies(java.util.Map.of(NexusLedgerTest.MANA,MagicAmount.ONE));
        var tag=data.save(new CompoundTag(),null); var rates=tag.getList("rates",Tag.TAG_COMPOUND);
        rates.add(rates.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class,()->LeyWorldData.load(tag,()->0));
        rates.remove(1); rates.getCompound(0).getCompound("rate").putString("denominator","0");
        assertThrows(IllegalArgumentException.class,()->LeyWorldData.load(tag,()->0));
    }
}
