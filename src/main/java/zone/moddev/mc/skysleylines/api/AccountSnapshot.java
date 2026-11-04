package zone.moddev.mc.skysleylines.api;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Exact projected balances at one game tick; no reservation or mutation. Zero currencies may be omitted. */
public record AccountSnapshot(int capacity, MagicAmount raw, Map<ResourceLocation, Long> currencies,
                              MagicAmount total, long tick) {
    public AccountSnapshot { currencies = Map.copyOf(currencies); }
    public long currency(ResourceLocation id) { return currencies.getOrDefault(id, 0L); }
}
