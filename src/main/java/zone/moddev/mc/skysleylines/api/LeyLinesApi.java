package zone.moddev.mc.skysleylines.api;

import java.util.Map;
import java.util.Optional;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import zone.moddev.mc.skysleylines.server.LeyServerContext;

/** Entry point for consumer mods. Acquire on the server thread; the returned sampler may be used by worldgen workers. */
public final class LeyLinesApi {
    public static final int API_VERSION = 2;
    private static final ParadigmRegistry PARADIGMS = new ParadigmRegistry();
    private LeyLinesApi() {}

    public record Context(LeyProfile profile, LeyFieldSampler field, NexusPowerService power) {}

    public static ParadigmRegistry paradigms() { return PARADIGMS; }

    public static Context forLevel(ServerLevel level) { return LeyServerContext.forLevel(level); }

    /** Worker-safe lookup using the exact RandomState supplied by a structure callback. */
    public static Optional<LeyFieldSampler> worldgenSampler(RandomState state) { return LeyServerContext.worldgenSampler(state); }

    /** Register currency-level defaults during mod setup. Duplicate definitions are rejected. */
    public static void registerPlayerEnergyDefaults(ResourceLocation currency, PlayerPowerService.CurrencyPolicy defaults) {
        zone.moddev.mc.skysleylines.core.PlayerEnergyDefaults.register(currency, defaults);
    }

    /** Converted player pools, transfers, and scheduled conversions for the current server. */
    public static PlayerPowerService playerPower(MinecraftServer server) { return LeyServerContext.playerEnergy(server); }

    /** Saved currencies, including inactive entries. Read on the server thread. */
    public static Map<ResourceLocation, CurrencyInfo> currencyInfo(ServerLevel level) {
        return LeyServerContext.currencyInfo(level);
    }
}
