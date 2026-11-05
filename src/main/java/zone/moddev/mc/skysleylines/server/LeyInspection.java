package zone.moddev.mc.skysleylines.server;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import zone.moddev.mc.skysleylines.api.LeyLinesApi;
import zone.moddev.mc.skysleylines.api.NexusSnapshot;
import zone.moddev.mc.skysleylines.network.LeyMeterPayload;

/** Server-side formatting of the same sample and quote used by the operator commands. */
public final class LeyInspection {
    private LeyInspection() {}

    public static LeyMeterPayload snapshot(ServerLevel level, BlockPos pos) {
        var ley = LeyLinesApi.forLevel(level);
        var field = ley.field().sample(pos.getX(), pos.getY(), pos.getZ());
        var reserve = ley.power().quoteAt(pos.getX(), pos.getY(), pos.getZ()).orElse(null);
        return new LeyMeterPayload(level.dimension().location(), pos.immutable(), field.active(), field.naturalStrength(),
                field.lineStrength(), field.nexusStrength(), field.verticalFactor(), field.dimensionalResistance(),
                reserve == null ? null : reserve(level, reserve));
    }

    private static LeyMeterPayload.Reserve reserve(ServerLevel level, NexusSnapshot quote) {
        var account = quote.account();
        var rates = LeyLinesApi.currencyInfo(level);
        var currencies = new ArrayList<LeyMeterPayload.Currency>();
        for (var entry : rates.entrySet()) {
            if (currencies.size() >= LeyMeterPayload.MAX_CURRENCIES) break;
            var id = entry.getKey();
            currencies.add(new LeyMeterPayload.Currency(boundedText(id), account.currency(id),
                    boundedText(entry.getValue().unitsPerRaw()), entry.getValue().active()));
        }
        return new LeyMeterPayload.Reserve(boundedText(quote.nexus().id()), quote.nexus().strength(),
                boundedText(account.raw()), boundedText(account.total()), quote.capacity(), quote.rechargeTicks(),
                currencies, rates.size() - currencies.size());
    }

    private static String boundedText(Object value) {
        String text = value.toString();
        int limit = LeyMeterPayload.MAX_TEXT;
        return text.length() <= limit ? text : text.substring(0, limit - 1) + "…";
    }
}
