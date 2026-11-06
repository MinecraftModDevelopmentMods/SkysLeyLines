package zone.moddev.mc.skysleylines.probe;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.server.level.ServerLevel;
import zone.moddev.mc.skysleylines.api.LeyFieldSampler;
import zone.moddev.mc.skysleylines.api.LeyLinesApi;

/** Loaded only when the optional mod is installed. */
final class OptionalSampler {
    private static final ConcurrentHashMap<ServerLevel, LeyFieldSampler> SAMPLERS = new ConcurrentHashMap<>();

    static void load(ServerLevel level) {
        SAMPLERS.put(level, LeyLinesApi.forLevel(level).field());
    }

    static LeyFieldSampler sampler(ServerLevel level) { return SAMPLERS.get(level); }

    static Object lookup(RandomState state) { return LeyLinesApi.worldgenSampler(state).orElse(null); }

    static List<BlockPos> sites(Object field, int x, int z) { return positions(query(field, x, z)); }

    static List<?> query(Object field, int x, int z) { return ((LeyFieldSampler) field).nexusesInChunk(x, z); }

    static List<BlockPos> positions(List<?> sites) {
        if (sites.isEmpty()) return List.of();
        return sites.stream().map(zone.moddev.mc.skysleylines.api.Nexus.class::cast)
                .map(nexus -> BlockPos.containing(nexus.x(), nexus.authoredY().orElse(80), nexus.z())).toList();
    }

    static double column(Object field, int x, int z) {
        var column = ((LeyFieldSampler) field).sampleColumn(x, z);
        return column.naturalStrengthAt(column.naturalSurfaceY());
    }

    static void unload(ServerLevel level) { SAMPLERS.remove(level); }

    static double sample(ServerLevel level, int x, int z) {
        var sampler = SAMPLERS.get(level);
        if (sampler == null) throw new IllegalStateException("Ley sampler was unavailable during world generation");
        var column = sampler.sampleColumn(x, z);
        return column.naturalStrengthAt(column.naturalSurfaceY());
    }
}
