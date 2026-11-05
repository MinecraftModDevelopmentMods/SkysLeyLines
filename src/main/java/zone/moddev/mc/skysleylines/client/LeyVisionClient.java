package zone.moddev.mc.skysleylines.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import zone.moddev.mc.skysleylines.SkysLeyLines;
import zone.moddev.mc.skysleylines.core.VisionGeometry;
import zone.moddev.mc.skysleylines.network.VisionSignals;

@EventBusSubscriber(modid = SkysLeyLines.MOD_ID, value = Dist.CLIENT)
public final class LeyVisionClient {
    private static final int STEP = 8;
    private static VisionGeometry patch = VisionGeometry.EMPTY;
    private static List<Span> spans = List.of();
    private static List<Plume> plumes = List.of();
    private static long nextSample;

    record Span(Vec3 start, Vec3 end, double along, double nx, double nz, double width,
                float strength, double startFade, double endFade, AABB bounds) {}
    record Plume(Vec3 centre, float strength, AABB bounds) {}

    private LeyVisionClient() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        VisionGeometry current = VisionSignals.read(level);
        if (level == null || (current.reaches().isEmpty() && current.crossings().isEmpty())) {
            clear();
            return;
        }
        long tick = level.getGameTime();
        if (current == patch && tick < nextSample) return;
        patch = current;
        nextSample = tick + 10;
        var newSpans = new ArrayList<Span>();
        double width = Math.clamp(current.lineHalfWidth() * 0.72, 0.8, 32);
        for (var reach : current.reaches()) sampleReach(level, reach, width, newSpans);
        spans = newSpans;
        var newPlumes = new ArrayList<Plume>();
        for (var crossing : current.crossings()) {
            Vec3 centre = surface(level, crossing.x(), crossing.z());
            if (centre != null) {
                AABB bounds = new AABB(centre.add(-22, -1, -22), centre.add(22, 62, 22));
                newPlumes.add(new Plume(centre, crossing.strength(), bounds));
            }
        }
        plumes = newPlumes;
    }

    private static void sampleReach(ClientLevel level, VisionGeometry.Reach reach, double width, List<Span> result) {
        double dx = reach.x1() - reach.x0(), dz = reach.z1() - reach.z0();
        double length = Math.hypot(dx, dz);
        double ux = dx / length, uz = dz / length;
        double offset = reach.x0() * ux + reach.z0() * uz;
        double at = 0;
        Vec3 from = surface(level, reach.x0(), reach.z0());
        // A world-aligned sampling grid keeps the filaments in place when the server patch moves.
        double next = (Math.floor(offset / STEP) + 1) * STEP - offset;
        while (at < length) {
            double end = Math.min(length, next);
            Vec3 to = surface(level, reach.x0() + end * ux, reach.z0() + end * uz);
            if (from != null && to != null && end - at > 0.001) {
                double startFade = Math.clamp(Math.min(at, length - at) / 12, 0, 1);
                double endFade = Math.clamp(Math.min(end, length - end) / 12, 0, 1);
                AABB bounds = new AABB(from, to).inflate(width * 1.5 + 2, 22, width * 1.5 + 2);
                result.add(new Span(from, to, offset + at, -uz, ux, width, reach.strength(), startFade, endFade, bounds));
            }
            from = to;
            at = end;
            next += STEP;
        }
    }

    private static Vec3 surface(ClientLevel level, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        var chunk = level.getChunkSource().getChunk(SectionPos.blockToSectionCoord(bx),
                SectionPos.blockToSectionCoord(bz), ChunkStatus.FULL, false);
        if (chunk == null) return null;
        // WORLD_SURFACE is sent to clients and puts the river above water and terrain.
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
        return y < level.getMinBuildHeight() ? null : new Vec3(x, y + 1.05, z);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (spans.isEmpty() && plumes.isEmpty())) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || VisionSignals.read(level) != patch) return;
        double time = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        LeyVisionRenderer.draw(event, spans, plumes, time, VisionSignals.style());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        VisionSignals.clear();
        clear();
    }

    private static void clear() {
        patch = VisionGeometry.EMPTY;
        spans = List.of();
        plumes = List.of();
        LeyVisionRenderer.clear();
    }
}
