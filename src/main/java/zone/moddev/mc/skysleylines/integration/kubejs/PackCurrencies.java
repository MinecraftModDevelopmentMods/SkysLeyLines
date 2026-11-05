package zone.moddev.mc.skysleylines.integration.kubejs;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.skysleylines.api.LeyLinesApi;
import zone.moddev.mc.skysleylines.api.Paradigm;
import zone.moddev.mc.skysleylines.core.LeySaveLimits;

/** Collects startup script definitions before the normal paradigm catalogue freezes. */
public final class PackCurrencies {
    private static final Map<ResourceLocation, Paradigm.Currency> PENDING = new TreeMap<>();
    private static boolean closed;

    public void registerCurrency(String id, Object unitsPerRaw) {
        synchronized (PENDING) {
            if (closed) throw new IllegalStateException("Restart Minecraft to change Ley Lines currency definitions");
            var key = LeyServerScripts.namespaced(id);
            if (!key.getNamespace().equals("kubejs")) {
                throw new IllegalArgumentException("Pack currency IDs must use the kubejs namespace: " + key);
            }
            var rate = LeyServerScripts.exactAmount(unitsPerRaw);
            LeySaveLimits.checkRate(key, rate);
            if (PENDING.putIfAbsent(key, new Paradigm.Currency(key, () -> rate)) != null) {
                throw new IllegalArgumentException("Duplicate pack currency: " + key);
            }
        }
    }

    public static void completeRegistration() {
        synchronized (PENDING) {
            if (!PENDING.isEmpty()) {
                LeyLinesApi.paradigms().register(new Paradigm<>(ResourceLocation.parse("skysleylines:kubejs_pack"),
                        null, List.copyOf(PENDING.values())));
                PENDING.clear();
            }
            closed = true;
        }
    }
}
