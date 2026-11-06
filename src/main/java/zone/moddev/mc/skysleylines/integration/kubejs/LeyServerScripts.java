package zone.moddev.mc.skysleylines.integration.kubejs;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import zone.moddev.mc.skysleylines.api.AccountSnapshot;
import zone.moddev.mc.skysleylines.api.CurrencyInfo;
import zone.moddev.mc.skysleylines.api.LeyLinesApi;
import zone.moddev.mc.skysleylines.api.LeyVision;
import zone.moddev.mc.skysleylines.api.MagicAmount;
import zone.moddev.mc.skysleylines.api.PowerRequest;
import zone.moddev.mc.skysleylines.core.LeySaveLimits;

/** Server script binding. Uses exact strings for quantities that JavaScript numbers cannot preserve. */
public final class LeyServerScripts extends LeyPlayerScripts {
    private static final Pattern EXACT_AMOUNT = Pattern.compile("(0|[1-9][0-9]*)(?:/([1-9][0-9]*))?");

    static ResourceLocation namespaced(String id) {
        var parsed = ResourceLocation.tryParse(id);
        if (parsed == null || !id.contains(":") || parsed.getPath().isEmpty()) {
            throw new IllegalArgumentException("Expected a namespaced ID: " + id);
        }
        return parsed;
    }

    static MagicAmount exactAmount(Object input) {
        int limit = LeySaveLimits.MAX_STRING_BYTES;
        if (input instanceof CharSequence string && string.length() <= 2 * limit + 1) {
            var match = EXACT_AMOUNT.matcher(string);
            if (match.matches()) {
                var numerator = match.group(1);
                var denominator = match.group(2);
                if (numerator.length() <= limit && (denominator == null || denominator.length() <= limit)) {
                    return new MagicAmount(new BigInteger(numerator), denominator == null ? BigInteger.ONE : new BigInteger(denominator));
                }
            }
        }
        throw new IllegalArgumentException("Expected a nonnegative exact amount string such as '1000' or '3/2'");
    }

    public Map<String, Object> inspectAt(ServerLevel level, int x, int y, int z) {
        var context = LeyLinesApi.forLevel(level);
        var field = context.field().sample(x, y, z);
        var inspection = new LinkedHashMap<String, Object>();
        inspection.put("dimension", level.dimension().location().toString());
        inspection.put("x", x);
        inspection.put("y", y);
        inspection.put("z", z);
        inspection.put("active", field.active());
        inspection.put("naturalStrength", field.naturalStrength());
        inspection.put("lineStrength", field.lineStrength());
        inspection.put("nexusStrength", field.nexusStrength());
        inspection.put("verticalFactor", field.verticalFactor());
        inspection.put("dimensionalResistance", field.dimensionalResistance());
        inspection.put("account", context.power().quoteAt(x, y, z).map(quote -> {
            var nexus = quote.nexus();
            var account = new LinkedHashMap<String, Object>();
            account.put("nexus", nexus.id().toString());
            account.put("x", nexus.x());
            account.put("z", nexus.z());
            account.put("strength", nexus.strength());
            account.put("rechargeTicks", quote.rechargeTicks());
            account.put("balance", snapshot(quote.account(), LeyLinesApi.currencyInfo(level)));
            nexus.id().pinId().ifPresent(id -> account.put("pinId", id.toString()));
            nexus.authoredY().ifPresent(yAnchor -> account.put("authoredY", yAnchor));
            return Collections.unmodifiableMap(account);
        }).orElse(null));
        return Collections.unmodifiableMap(inspection);
    }

    public Map<String, Object> convertAt(ServerLevel level, int x, int y, int z, String currency,
                                         Object raw, String consumer, ServerPlayer actor) {
        return transact(level, x, y, z, currency, raw, consumer, actor, PowerRequest.Operation.CONVERT);
    }
    public Map<String, Object> convertAt(ServerLevel level, int x, int y, int z, String currency, Object raw, String consumer) {
        return convertAt(level, x, y, z, currency, raw, consumer, null);
    }

    public Map<String, Object> spendAt(ServerLevel level, int x, int y, int z, String currency,
                                       Object units, String consumer, ServerPlayer actor) {
        return transact(level, x, y, z, currency, units, consumer, actor, PowerRequest.Operation.SPEND);
    }
    public Map<String, Object> spendAt(ServerLevel level, int x, int y, int z, String currency, Object units, String consumer) {
        return spendAt(level, x, y, z, currency, units, consumer, null);
    }

    public Map<String, Object> returnToRawAt(ServerLevel level, int x, int y, int z, String currency,
                                             Object units, String consumer, ServerPlayer actor) {
        return transact(level, x, y, z, currency, units, consumer, actor, PowerRequest.Operation.RETURN_TO_RAW);
    }
    public Map<String, Object> returnToRawAt(ServerLevel level, int x, int y, int z, String currency, Object units, String consumer) {
        return returnToRawAt(level, x, y, z, currency, units, consumer, null);
    }

    private static Map<String, Object> transact(ServerLevel level, int x, int y, int z, String currency,
                                                Object value, String consumer, ServerPlayer actor,
                                                PowerRequest.Operation operation) {
        var power = LeyLinesApi.forLevel(level).power();
        if (actor != null && actor.serverLevel() != level) {
            throw new IllegalArgumentException("The transaction actor must be in the supplied level");
        }
        var request = new PowerRequest(x, y, z, operation, namespaced(currency), exactAmount(value),
                new PowerRequest.Consumer(namespaced(consumer), Optional.ofNullable(actor).map(ServerPlayer::getUUID)));
        var result = power.transact(request);
        if (!result.succeeded()) return Map.of("succeeded", false, "status", result.status().name());
        var receipt = result.receipt().orElseThrow();
        var currencies = LeyLinesApi.currencyInfo(level);
        return Map.of("succeeded", true, "status", result.status().name(), "nexus", receipt.nexus().toString(),
                "revision", Long.toString(receipt.revision()),
                "before", snapshot(receipt.before(), currencies), "after", snapshot(receipt.after(), currencies));
    }

    private static Map<String, Object> snapshot(AccountSnapshot snapshot, Map<ResourceLocation, CurrencyInfo> currencies) {
        var rows = new LinkedHashMap<String, Map<String, Object>>();
        currencies.forEach((id, info) -> rows.put(id.toString(), Map.of("units", Long.toString(snapshot.currency(id)),
                "unitsPerRaw", info.unitsPerRaw().toString(), "active", info.active())));
        return Map.of("raw", snapshot.raw().toString(), "total", snapshot.total().toString(),
                "capacity", snapshot.capacity(), "tick", Long.toString(snapshot.tick()),
                "currencies", Collections.unmodifiableMap(rows));
    }

    public void grantVision(ServerPlayer player, String source, int durationTicks) {
        LeyVision.grant(player, namespaced(source), durationTicks);
    }

    public void grantVision(ServerPlayer player, String source, int durationTicks, String style) {
        LeyVision.grant(player, namespaced(source), durationTicks, namespaced(style));
    }

    public void revokeVision(ServerPlayer player, String source) {
        LeyVision.revoke(player, namespaced(source));
    }
}
