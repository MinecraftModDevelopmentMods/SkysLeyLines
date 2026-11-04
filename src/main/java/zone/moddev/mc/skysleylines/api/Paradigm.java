package zone.moddev.mc.skysleylines.api;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

/** Child-owned configuration is an optional typed extension; its storage belongs to the child. */
public record Paradigm<T>(ResourceLocation id, T configuration, List<Currency> currencies) {
    public Paradigm { Objects.requireNonNull(id); currencies = List.copyOf(currencies); }
    /** Supplier is evaluated once per server start, after server configuration has loaded. */
    public record Currency(ResourceLocation id, Supplier<MagicAmount> unitsPerRaw) {
        public Currency { Objects.requireNonNull(id); Objects.requireNonNull(unitsPerRaw); }
    }
}
