package zone.moddev.mc.skysleylines.api;

import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Loading-time catalogue. Register in common setup's enqueueWork; frozen at load complete. */
public final class ParadigmRegistry {
    private final Map<ResourceLocation, Paradigm<?>> paradigms = new TreeMap<>();
    private final Set<ResourceLocation> currencies = new TreeSet<>();
    private boolean frozen;
    public synchronized <T> Paradigm<T> register(Paradigm<T> paradigm) {
        if (frozen) throw new IllegalStateException("Paradigm registration has closed");
        if (paradigms.containsKey(paradigm.id())) throw new IllegalArgumentException("Duplicate paradigm: " + paradigm.id());
        Set<ResourceLocation> added = new HashSet<>();
        for (var currency : paradigm.currencies()) {
            if (currencies.contains(currency.id()) || !added.add(currency.id())) throw new IllegalArgumentException("Duplicate currency: " + currency.id());
        }
        paradigms.put(paradigm.id(), paradigm); currencies.addAll(added);
        return paradigm;
    }
    public synchronized void freeze() { frozen = true; }
    public synchronized Map<ResourceLocation, Paradigm<?>> definitions() { return Collections.unmodifiableMap(new TreeMap<>(paradigms)); }
    public synchronized Map<ResourceLocation, MagicAmount> configuredRates() {
        if (!frozen) throw new IllegalStateException("Paradigm catalogue is not frozen");
        Map<ResourceLocation, MagicAmount> rates = new TreeMap<>();
        for (var paradigm : paradigms.values()) for (var currency : paradigm.currencies()) {
            var rate = Objects.requireNonNull(currency.unitsPerRaw().get(), "Null currency rate");
            if (rate.isZero()) throw new IllegalArgumentException("Currency rate must be positive: " + currency.id());
            rates.put(currency.id(), rate);
        }
        return Collections.unmodifiableMap(rates);
    }
}
