package zone.moddev.mc.skysleylines.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.skysleylines.api.PlayerPowerService.CurrencyPolicy;

public final class PlayerEnergyDefaults {
    private static final Map<ResourceLocation, CurrencyPolicy> POLICIES = new HashMap<>();
    private static boolean frozen;

    private PlayerEnergyDefaults() {}

    public static synchronized void register(ResourceLocation currency, CurrencyPolicy policy) {
        if (frozen) throw new IllegalStateException("Player energy defaults are frozen");
        LeySaveLimits.checkCurrencyId(currency);
        if (POLICIES.putIfAbsent(currency, Objects.requireNonNull(policy)) != null)
            throw new IllegalArgumentException("Duplicate player energy defaults for " + currency);
    }

    public static synchronized void freeze() { frozen = true; }

    public static synchronized CurrencyPolicy forCurrency(ResourceLocation currency) {
        return POLICIES.getOrDefault(currency, CurrencyPolicy.DEFAULT);
    }
}
