package zone.moddev.mc.skysleylines;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysleylines.api.*;
import zone.moddev.mc.skysleylines.server.LeyWorldData;

class LeyWorldDataTest {
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
