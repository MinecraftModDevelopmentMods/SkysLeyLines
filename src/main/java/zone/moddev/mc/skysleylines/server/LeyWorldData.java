package zone.moddev.mc.skysleylines.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.math.BigInteger;
import java.util.function.LongSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import zone.moddev.mc.skysleylines.LeyConfig;
import zone.moddev.mc.skysleylines.api.*;
import zone.moddev.mc.skysleylines.core.NexusLedger;

/** World-wide settings and sparse balances live in the Overworld, independent of loaded nexus chunks. */
public final class LeyWorldData extends SavedData {
    public static final String NAME = "skysleylines";
    private static final int BASE_SCHEMA = 2;
    public static final int SCHEMA = 3;
    private final LeyProfile profile;
    private final NexusLedger ledger;
    private final LongSupplier clock;
    private Map<ResourceLocation, MagicAmount> configuredRates = Map.of();
    private Path storageDirectory;
    private boolean hasRechargeHistory;

    public LeyWorldData(LeyProfile profile, LongSupplier clock) {
        this(profile, clock, profile.rechargeTicks(), false);
    }
    private LeyWorldData(LeyProfile profile, LongSupplier clock, int effectiveRechargeTicks, boolean hasRechargeHistory) {
        this.profile = profile;
        this.ledger = new NexusLedger(effectiveRechargeTicks);
        this.clock = clock;
        this.hasRechargeHistory = hasRechargeHistory;
    }
    public LeyProfile profile() { return profile; }
    public NexusLedger ledger() { return ledger; }

    public static LeyWorldData get(MinecraftServer server) {
        LongSupplier clock = () -> server.overworld().getGameTime();
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(NAME + ".dat");
        int override = LeyConfig.rechargeOverride();
        var configured = LeyLinesApi.paradigms().configuredRates();
        LeyWorldData data;
        boolean existing = !Files.notExists(path);
        // Publish only after validation, so SavedData cannot replace a failed load with defaults.
        if (existing) {
            try {
                CompoundTag file = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                if (!file.contains("data", Tag.TAG_COMPOUND)) throw new IllegalStateException("Missing ley data root");
                CompoundTag saved = file.getCompound("data");
                data = load(saved, clock);
                if (saved.getInt("schema") == 1) {
                    copyBackup(path, "schema1");
                }
            } catch (IOException | RuntimeException failure) {
                throw new IllegalStateException("Cannot safely read " + path + "; existing ley data has not been replaced", failure);
            }
        } else {
            data = new LeyWorldData(LeyConfig.defaults(), clock);
            data.setDirty();
        }
        data.storageDirectory = path.getParent();
        data.applyRechargeOverride(override, () -> {
            if (existing) {
                try {
                    copyBackup(path, "before-recharge");
                } catch (IOException failure) {
                    throw new IllegalStateException("Recharge backup failed; ley data has not been replaced", failure);
                }
            }
        });
        data.configureCurrencies(configured);
        server.overworld().getDataStorage().set(NAME, data);
        return data;
    }

    private static void copyBackup(Path path, String reason) throws IOException {
        Files.copy(path, path.resolveSibling(NAME + "-" + reason + "-" + UUID.randomUUID() + ".dat.bak"));
    }

    public void applyRechargeOverride(int override, Runnable backup) {
        int target = override == 0 ? profile.rechargeTicks() : override;
        if (ledger.changeRechargeTicks(target, Math.max(0, clock.getAsLong()), backup)) {
            hasRechargeHistory = true;
            setDirty();
        }
    }

    public void configureCurrencies(Map<ResourceLocation, MagicAmount> rates) {
        if (ledger.bindCurrencies(rates)) setDirty();
        configuredRates = Map.copyOf(rates);
        rates.forEach((id, rate) -> {
            if (!rate.equals(ledger.rates().get(id))) LogUtils.getLogger().warn("Ley currency {} retains saved rate {}; configured {} requires explicit migration", id, ledger.rates().get(id), rate);
        });
    }
    public RateMigration previewMigration() { return ledger.previewMigration(configuredRates, Math.max(0, clock.getAsLong())); }
    public boolean applyMigration(RateMigration preview) {
        if (!preview.newRates().equals(previewMigration().newRates())) return false;
        return ledger.applyMigration(preview, Math.max(0, clock.getAsLong()), new NexusLedger.MigrationHooks() {
            @Override public boolean before(RateMigration plan) {
                return !NeoForge.EVENT_BUS.post(new RateMigrationEvent.Pre(plan)).isCanceled();
            }
            @Override public void backup() {
                if (storageDirectory == null) throw new IllegalStateException("No world backup directory");
                try {
                    Files.createDirectories(storageDirectory);
                    Path backup = Files.createTempFile(storageDirectory, NAME + "-before-rates-", ".dat.bak");
                    CompoundTag root = new CompoundTag(); root.put("data", encode(new CompoundTag()));
                    NbtIo.writeCompressed(root, backup);
                } catch (IOException failure) { throw new IllegalStateException("Rate migration backup failed; ledger unchanged", failure); }
            }
            @Override public void after(RateMigration plan) {
                setDirty();
                NeoForge.EVENT_BUS.post(new RateMigrationEvent.Post(plan));
            }
            @Override public void failure(RuntimeException failure) { LogUtils.getLogger().error("Ley rate migration observer failed after commit", failure); }
        });
    }

    public static LeyWorldData load(CompoundTag tag, LongSupplier clock) {
        require(tag, "schema", Tag.TAG_INT);
        require(tag, "layoutVersion", Tag.TAG_INT);
        int schema = tag.getInt("schema");
        if (schema < 1 || schema > SCHEMA || tag.getInt("layoutVersion") != LeyProfile.LAYOUT_VERSION) {
            throw new IllegalStateException("Unsupported ley save or layout version; refusing to reset the network");
        }
        require(tag, "dimension", Tag.TAG_STRING);
        if (!tag.getString("dimension").equals("minecraft:overworld")) throw new IllegalStateException("Unsupported ley dimension profile");
        require(tag, "profile", Tag.TAG_COMPOUND);
        CompoundTag p = tag.getCompound("profile");
        for (String key : new String[]{"spacing", "lineWidth", "nexusRadius", "verticalReach", "capacity", "rechargeTicks"}) require(p, key, Tag.TAG_INT);
        LeyProfile profile = new LeyProfile(p.getInt("spacing"), p.getInt("lineWidth"), p.getInt("nexusRadius"),
                p.getInt("verticalReach"), p.getInt("capacity"), p.getInt("rechargeTicks"));
        int effectiveRechargeTicks = profile.rechargeTicks();
        if (schema == SCHEMA) {
            require(tag, "effectiveRechargeTicks", Tag.TAG_INT);
            effectiveRechargeTicks = tag.getInt("effectiveRechargeTicks");
        }
        LeyWorldData data = new LeyWorldData(profile, clock, effectiveRechargeTicks, schema == SCHEMA);
        if (schema >= BASE_SCHEMA) {
            require(tag, "revision", Tag.TAG_LONG);
            data.ledger.restoreRevision(tag.getLong("revision"));
            for (Tag rateTag : compounds(tag, "rates")) {
                CompoundTag rate = (CompoundTag) rateTag;
                require(rate, "id", Tag.TAG_STRING);
                data.ledger.restoreRate(identifier(rate.getString("id")), amount(rate, "rate"));
            }
        }
        ListTag rows = compounds(tag, "balances");
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            require(row, "dimension", Tag.TAG_STRING);
            for (String key : new String[]{"ax", "az", "bx", "bz", "capacity"}) require(row, key, Tag.TAG_INT);
            require(row, "tick", Tag.TAG_LONG);
            NexusId id = new NexusId(row.getString("dimension"), row.getInt("ax"), row.getInt("az"), row.getInt("bx"), row.getInt("bz"));
            if (!id.dimension().equals("minecraft:overworld")) throw new IllegalArgumentException("Inactive dimension balance");
            MagicAmount raw;
            var currencies = new TreeMap<ResourceLocation, Long>();
            if (schema == 1) {
                require(row, "available", Tag.TAG_INT); require(row, "remainder", Tag.TAG_LONG);
                int available = row.getInt("available"); long remainder = row.getLong("remainder");
                if (available < 0 || available > row.getInt("capacity") || remainder < 0 || remainder >= data.profile.rechargeTicks()
                        || (available == row.getInt("capacity") && remainder != 0)) throw new IllegalArgumentException("Invalid legacy balance");
                raw = MagicAmount.of(available).add(MagicAmount.fraction(remainder, data.profile.rechargeTicks()));
            } else {
                raw = amount(row, "raw");
                for (Tag currencyTag : compounds(row, "currencies")) {
                    CompoundTag currency = (CompoundTag) currencyTag;
                    require(currency, "id", Tag.TAG_STRING); require(currency, "units", Tag.TAG_LONG);
                    ResourceLocation key = identifier(currency.getString("id"));
                    long units = currency.getLong("units");
                    if (units <= 0 || !data.ledger.rates().containsKey(key) || currencies.putIfAbsent(key, units) != null) throw new IllegalArgumentException("Invalid/duplicate currency balance");
                }
            }
            data.ledger.restore(id, new NexusLedger.Balance(row.getInt("capacity"), raw, currencies, row.getLong("tick")));
        }
        if (schema == 1) data.setDirty();
        return data;
    }
    private static ListTag compounds(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_LIST);
        ListTag list = (ListTag) tag.get(key);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid " + key + " list");
        return list;
    }
    private static ResourceLocation identifier(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (!value.contains(":") || id == null) throw new IllegalArgumentException("Invalid currency identifier");
        return id;
    }
    private static MagicAmount amount(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_COMPOUND);
        CompoundTag value = tag.getCompound(key);
        require(value, "numerator", Tag.TAG_STRING); require(value, "denominator", Tag.TAG_STRING);
        String n = value.getString("numerator"), d = value.getString("denominator");
        if (!n.matches("0|[1-9][0-9]*") || !d.matches("[1-9][0-9]*")) throw new IllegalArgumentException("Invalid exact amount");
        return new MagicAmount(new BigInteger(n), new BigInteger(d));
    }
    private static void putAmount(CompoundTag tag, String key, MagicAmount amount) {
        CompoundTag value = new CompoundTag();
        value.putString("numerator", amount.numerator().toString()); value.putString("denominator", amount.denominator().toString());
        tag.put(key, value);
    }
    private static void require(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw new IllegalArgumentException("Missing/invalid ley field: " + key);
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ledger.pruneRecovered(Math.max(0, clock.getAsLong()));
        return encode(tag);
    }
    private CompoundTag encode(CompoundTag tag) {
        tag.putInt("schema", hasRechargeHistory ? SCHEMA : BASE_SCHEMA);
        if (hasRechargeHistory) tag.putInt("effectiveRechargeTicks", ledger.rechargeTicks());
        tag.putInt("layoutVersion", LeyProfile.LAYOUT_VERSION);
        tag.putString("dimension", "minecraft:overworld");
        CompoundTag p = new CompoundTag();
        p.putInt("spacing", profile.spacingBlocks()); p.putInt("lineWidth", profile.lineWidth());
        p.putInt("nexusRadius", profile.nexusRadius()); p.putInt("verticalReach", profile.verticalReach());
        p.putInt("capacity", profile.fullStrengthCapacity()); p.putInt("rechargeTicks", profile.rechargeTicks());
        tag.put("profile", p);
        tag.putLong("revision", ledger.revision());
        ListTag rates = new ListTag();
        ledger.rates().forEach((id, rate) -> {
            CompoundTag row = new CompoundTag(); row.putString("id", id.toString()); putAmount(row, "rate", rate); rates.add(row);
        });
        tag.put("rates", rates);
        ListTag rows = new ListTag();
        for (Map.Entry<NexusId, NexusLedger.Balance> entry : ledger.entries().entrySet()) {
            NexusId id = entry.getKey(); NexusLedger.Balance balance = entry.getValue();
            CompoundTag row = new CompoundTag();
            row.putString("dimension", id.dimension()); row.putInt("ax", id.ax()); row.putInt("az", id.az());
            row.putInt("bx", id.bx()); row.putInt("bz", id.bz()); row.putInt("capacity", balance.capacity());
            putAmount(row, "raw", balance.raw()); row.putLong("tick", balance.tick());
            ListTag currencies = new ListTag();
            balance.currencies().forEach((currency, units) -> {
                CompoundTag value = new CompoundTag(); value.putString("id", currency.toString()); value.putLong("units", units); currencies.add(value);
            });
            row.put("currencies", currencies);
            rows.add(row);
        }
        tag.put("balances", rows);
        return tag;
    }
}
