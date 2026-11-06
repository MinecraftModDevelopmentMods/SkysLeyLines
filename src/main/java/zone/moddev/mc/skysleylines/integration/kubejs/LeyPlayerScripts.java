package zone.moddev.mc.skysleylines.integration.kubejs;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import zone.moddev.mc.skysleylines.api.*;
import zone.moddev.mc.skysleylines.api.PlayerPowerService.*;
import static zone.moddev.mc.skysleylines.integration.kubejs.LeyServerScripts.exactAmount;
import static zone.moddev.mc.skysleylines.integration.kubejs.LeyServerScripts.namespaced;

public class LeyPlayerScripts {
    public Map<String, Object> inspectPlayer(ServerPlayer player) {
        return inspectPlayer(player.serverLevel().getServer(), player.getUUID().toString());
    }

    public Map<String, Object> inspectPlayer(MinecraftServer server, String uuid) {
        var snapshot = LeyLinesApi.playerPower(server).inspect(UUID.fromString(uuid));
        var pools = new TreeMap<String, Object>();
        snapshot.pools().forEach((id, pool) -> pools.put(id.toString(), pool(pool)));
        return Map.of("player", snapshot.player().toString(), "pools", Collections.unmodifiableMap(pools));
    }

    public Map<String, Object> configurePool(ServerPlayer player, String currency, boolean enabled, Object capacity,
                                              Object autoRate, String source) {
        return result(power(player).configure(player.getUUID(), namespaced(currency),
                new Settings(enabled, whole(capacity), autoRate == null ? null : exactAmount(autoRate)), consumer(source, player)));
    }

    public Map<String, Object> setPoolBalance(ServerPlayer player, String currency, Object units, String source) {
        return result(power(player).setBalance(player.getUUID(), namespaced(currency), whole(units), consumer(source, player)));
    }

    public Map<String, Object> adjustPool(ServerPlayer player, String currency, Object delta, String source) {
        if (!(delta instanceof CharSequence value) || !value.toString().matches("0|-?[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("Expected signed whole-unit string");
        return result(power(player).adjust(player.getUUID(), namespaced(currency),
                Long.parseLong(value.toString()), consumer(source, player)));
    }

    public Map<String, Object> spendPool(ServerPlayer player, String currency, Object units, String source) {
        return result(power(player).spend(player.getUUID(), namespaced(currency), whole(units), consumer(source, player)));
    }

    public Map<String, Object> withdrawToPlayer(ServerPlayer player, String currency, Object units, String schedule, String source) {
        return submission(power(player).withdraw(player, namespaced(currency), whole(units), timing(schedule), consumer(source, player)));
    }

    public Map<String, Object> depositFromPlayer(ServerPlayer player, String currency, Object units, String schedule, String source) {
        return submission(power(player).deposit(player, namespaced(currency), whole(units), timing(schedule), consumer(source, player)));
    }

    public Map<String, Object> transferPlayers(ServerPlayer from, ServerPlayer to, String currency, Object units, String schedule, String source) {
        return submission(power(from).transfer(from, to, namespaced(currency), whole(units), timing(schedule), consumer(source, from)));
    }

    public Map<String, Object> scheduleConversion(ServerLevel level, int x, int y, int z, String currency,
                                                  Object raw, String schedule, String source, ServerPlayer actor) {
        if (actor != null && actor.serverLevel() != level) throw new IllegalArgumentException("The actor must be in the supplied level");
        return submission(LeyLinesApi.playerPower(level.getServer()).convert(level, new BlockPos(x, y, z), namespaced(currency),
                exactAmount(raw), timing(schedule), consumer(source, actor)));
    }

    public Map<String, Object> scheduleConversion(ServerLevel level, int x, int y, int z, String currency,
                                                  Object raw, String schedule, String source) {
        return scheduleConversion(level, x, y, z, currency, raw, schedule, source, null);
    }

    public Map<String, Object> energyJob(MinecraftServer server, String id) {
        return LeyLinesApi.playerPower(server).job(UUID.fromString(id)).map(LeyPlayerScripts::job).orElse(null);
    }

    public boolean cancelEnergyJob(MinecraftServer server, String id) {
        return LeyLinesApi.playerPower(server).cancel(UUID.fromString(id));
    }

    private static PlayerPowerService power(ServerPlayer player) { return LeyLinesApi.playerPower(player.serverLevel().getServer()); }
    private static long whole(Object input) { return exactAmount(input).wholeLongExact(); }
    private static PowerRequest.Consumer consumer(String id, ServerPlayer player) {
        return new PowerRequest.Consumer(namespaced(id), Optional.ofNullable(player).map(ServerPlayer::getUUID));
    }
    private static Timing timing(String text) {
        if (text.equals("instant")) return Timing.instant();
        if (text.startsWith("duration:")) return Timing.duration(Integer.parseInt(text.substring(9)));
        if (text.startsWith("rate:")) return Timing.rate(exactAmount(text.substring(5)));
        throw new IllegalArgumentException("Expected 'instant', 'duration:20', or 'rate:3/2'");
    }

    private static Map<String, Object> result(Result result) {
        return reply(result.status(), Optional.empty(), result.receipt());
    }
    private static Map<String, Object> submission(Submission submission) {
        return reply(submission.status(), submission.jobId(), submission.receipt());
    }
    private static Map<String, Object> reply(Status status, Optional<UUID> id, Optional<Receipt> receipt) {
        var response = new LinkedHashMap<String, Object>();
        response.put("succeeded", status == Status.SUCCESS);
        response.put("status", status.name());
        id.ifPresent(value -> response.put("jobId", value.toString()));
        receipt.ifPresent(value -> response.put("receipt", receipt(value)));
        return Collections.unmodifiableMap(response);
    }

    static Map<String, Object> pool(Pool pool) {
        var result = new LinkedHashMap<String, Object>();
        result.put("units", Long.toString(pool.balance()));
        result.put("capacity", Long.toString(pool.settings().capacity()));
        result.put("enabled", pool.settings().enabled());
        result.put("active", pool.active());
        if (pool.settings().autoRate() != null) result.put("autoRate", pool.settings().autoRate().toString());
        return Collections.unmodifiableMap(result);
    }

    static Map<String, Object> receipt(Receipt receipt) {
        var result = new LinkedHashMap<String, Object>();
        result.put("operation", receipt.operation().name());
        result.put("currency", receipt.currency().toString());
        result.put("amount", receipt.amount().toString());
        result.put("source", receipt.consumer().id().toString());
        receipt.consumer().actor().ifPresent(id -> result.put("actor", id.toString()));
        receipt.nexus().ifPresent(id -> result.put("nexus", id.toString()));
        receipt.drop().ifPresent(id -> result.put("drop", id.toString()));
        receipt.anchor().ifPresent(anchor -> result.put("anchor", Map.of("dimension", anchor.dimension().location().toString(),
                "x", anchor.pos().getX(), "y", anchor.pos().getY(), "z", anchor.pos().getZ())));
        receipt.nexusBefore().ifPresent(account -> result.put("nexusBefore", account(account)));
        receipt.nexusAfter().ifPresent(account -> result.put("nexusAfter", account(account)));
        var changes = new TreeMap<String, Object>();
        receipt.changes().forEach((id, change) ->
                changes.put(id.toString(), Map.of("before", pool(change.before()), "after", pool(change.after()))));
        result.put("changes", Collections.unmodifiableMap(changes));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Object> account(AccountSnapshot account) {
        var currencies = new TreeMap<String, String>();
        account.currencies().forEach((id, units) -> currencies.put(id.toString(), Long.toString(units)));
        return Map.of("raw", account.raw().toString(), "total", account.total().toString(), "capacity", account.capacity(),
                "currencies", Collections.unmodifiableMap(currencies));
    }

    static Map<String, Object> job(Job job) {
        return Map.of("id", job.id().toString(), "operation", job.operation().name(), "currency", job.currency().toString(),
                "total", job.total().toString(), "completed", job.completed().toString(),
                "status", job.status().name(), "source", job.consumer().id().toString());
    }
}
