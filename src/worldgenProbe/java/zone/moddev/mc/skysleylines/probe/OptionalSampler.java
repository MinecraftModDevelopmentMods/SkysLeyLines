package zone.moddev.mc.skysleylines.probe;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import zone.moddev.mc.skysleylines.api.LeyFieldSampler;
import zone.moddev.mc.skysleylines.api.LeyLinesApi;

/** Loaded only when the optional mod is installed. */
final class OptionalSampler {
    private static final ConcurrentHashMap<ServerLevel, LeyFieldSampler> SAMPLERS = new ConcurrentHashMap<>();

    static void load(ServerLevel level) {
        SAMPLERS.put(level, LeyLinesApi.forLevel(level).field());
    }

    static void unload(ServerLevel level) { SAMPLERS.remove(level); }

    static double sample(ServerLevel level, int x, int z) {
        var sampler = SAMPLERS.get(level);
        if (sampler == null) throw new IllegalStateException("Ley sampler was unavailable during world generation");
        var column = sampler.sampleColumn(x, z);
        return column.naturalStrengthAt(column.naturalSurfaceY());
    }
}
