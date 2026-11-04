package zone.moddev.mc.skysleylines.api;

import net.minecraft.server.level.ServerLevel;
import zone.moddev.mc.skysleylines.server.LeyServerContext;

/** Entry point for consumer mods. Acquire on the server thread; the returned sampler may be used by worldgen workers. */
public final class LeyLinesApi {
    public static final int API_VERSION = 2;
    private static final ParadigmRegistry PARADIGMS = new ParadigmRegistry();
    private LeyLinesApi() {}

    public record Context(LeyProfile profile, LeyFieldSampler field, NexusPowerService power) {}

    public static ParadigmRegistry paradigms() { return PARADIGMS; }

    public static Context forLevel(ServerLevel level) { return LeyServerContext.forLevel(level); }
}
