package zone.moddev.mc.skysleylines.server;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import zone.moddev.mc.skysleylines.api.PlayerPowerService.Settings;
import zone.moddev.mc.skysleylines.core.LeySaveLimits;
import zone.moddev.mc.skysleylines.core.NexusLedger;
import static zone.moddev.mc.skysleylines.server.LeyWorldData.*;

/** The only persistent copy of pool balances and dropped energy. */
final class PlayerEnergyData {
    record StoredPool(Settings settings, long units) {}
    record Drop(UUID owner, ResourceLocation dimension, Vec3 position, long expires, boolean ownerOnly,
                Map<ResourceLocation, Long> balances) {
        Drop { balances = Map.copyOf(balances); }
    }

    final Map<UUID, Map<ResourceLocation, StoredPool>> players = new TreeMap<>();
    final NavigableMap<UUID, Drop> drops = new TreeMap<>();

    StoredPool pool(UUID player, ResourceLocation currency) {
        var pools = players.get(player);
        return pools == null ? null : pools.get(currency);
    }

    void put(UUID player, ResourceLocation currency, StoredPool pool) {
        players.computeIfAbsent(player, ignored -> new TreeMap<>()).put(currency, pool);
    }

    CompoundTag write() {
        var root = new CompoundTag();
        var wallets = new ListTag();
        players.forEach((id, pools) -> {
            var wallet = new CompoundTag();
            wallet.putUUID("player", id);
            var rows = new ListTag();
            pools.forEach((currency, pool) -> {
                var row = currencyTag(currency, pool.units());
                row.putBoolean("enabled", pool.settings().enabled());
                row.putLong("capacity", pool.settings().capacity());
                if (pool.settings().autoRate() != null) putAmount(row, "autoRate", pool.settings().autoRate());
                rows.add(row);
            });
            wallet.put("pools", rows);
            wallets.add(wallet);
        });
        root.put("players", wallets);
        var loot = new ListTag();
        drops.forEach((id, drop) -> {
            var row = new CompoundTag();
            row.putUUID("id", id);
            row.putUUID("owner", drop.owner());
            row.putString("dimension", drop.dimension().toString());
            row.putDouble("x", drop.position().x);
            row.putDouble("y", drop.position().y);
            row.putDouble("z", drop.position().z);
            row.putLong("expires", drop.expires());
            row.putBoolean("ownerOnly", drop.ownerOnly());
            var balances = new ListTag();
            new TreeMap<>(drop.balances()).forEach((currency, units) -> balances.add(currencyTag(currency, units)));
            row.put("balances", balances);
            loot.add(row);
        });
        root.put("drops", loot);
        return root;
    }

    void read(CompoundTag root, NexusLedger ledger) {
        for (Tag entry : compounds(root, "players")) {
            var row = (CompoundTag) entry;
            var pools = new TreeMap<ResourceLocation, StoredPool>();
            for (Tag value : compounds(row, "pools")) {
                var pool = (CompoundTag) value;
                var id = currency(pool, ledger);
                require(pool, "enabled", Tag.TAG_BYTE);
                require(pool, "capacity", Tag.TAG_LONG);
                var rate = pool.contains("autoRate") ? amount(pool, "autoRate") : null;
                if (rate != null) LeySaveLimits.checkAmount(rate, "Player recharge rate");
                var settings = new Settings(pool.getBoolean("enabled"), pool.getLong("capacity"), rate);
                if (pools.putIfAbsent(id, new StoredPool(settings, pool.getLong("units"))) != null)
                    throw new IllegalArgumentException("Duplicate pool " + id);
            }
            if (players.putIfAbsent(row.getUUID("player"), pools) != null) throw new IllegalArgumentException("Duplicate wallet");
        }
        for (Tag entry : compounds(root, "drops")) {
            var row = (CompoundTag) entry;
            require(row, "dimension", Tag.TAG_STRING);
            for (String key : new String[]{"x", "y", "z"}) {
                require(row, key, Tag.TAG_DOUBLE);
                if (!Double.isFinite(row.getDouble(key))) throw new IllegalArgumentException("Invalid energy drop position");
            }
            require(row, "expires", Tag.TAG_LONG);
            require(row, "ownerOnly", Tag.TAG_BYTE);
            var balances = new TreeMap<ResourceLocation, Long>();
            for (Tag value : compounds(row, "balances")) {
                var balance = (CompoundTag) value;
                var id = currency(balance, ledger);
                long units = balance.getLong("units");
                if (units == 0 || balances.putIfAbsent(id, units) != null) throw new IllegalArgumentException("Invalid drop balance");
            }
            long expires = row.getLong("expires");
            if (expires < 0 || balances.isEmpty()) throw new IllegalArgumentException("Invalid energy drop");
            var position = new Vec3(row.getDouble("x"), row.getDouble("y"), row.getDouble("z"));
            var drop = new Drop(row.getUUID("owner"), identifier(row.getString("dimension")), position,
                    expires, row.getBoolean("ownerOnly"), balances);
            if (drops.putIfAbsent(row.getUUID("id"), drop) != null) throw new IllegalArgumentException("Duplicate energy drop");
        }
    }

    private static ResourceLocation currency(CompoundTag row, NexusLedger ledger) {
        require(row, "id", Tag.TAG_STRING);
        require(row, "units", Tag.TAG_LONG);
        var id = identifier(row.getString("id"));
        if (ledger.savedRate(id) == null || row.getLong("units") < 0) throw new IllegalArgumentException("Invalid saved pool currency " + id);
        return id;
    }

    private static CompoundTag currencyTag(ResourceLocation currency, long units) {
        var row = new CompoundTag();
        row.putString("id", currency.toString());
        row.putLong("units", units);
        return row;
    }
}
