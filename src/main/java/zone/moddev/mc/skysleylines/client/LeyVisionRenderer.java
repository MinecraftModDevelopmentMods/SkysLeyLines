package zone.moddev.mc.skysleylines.client;

import java.util.List;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import zone.moddev.mc.skysleylines.client.LeyVisionClient.Plume;
import zone.moddev.mc.skysleylines.client.LeyVisionClient.Span;

/** Feathered ribbons and electric arcs with terrain depth testing and a single translucent batch. */
final class LeyVisionRenderer {
    private static final int DEEP_BLUE = 0x456BFF;
    private static final int CYAN = 0x4FE7FF;
    private static final int WHITE_BLUE = 0xD5FAFF;
    private static final int VIOLET = 0xA16AFF;
    private static final int LAVENDER = 0xE5C9FF;
    private static final RenderType ENERGY = RenderType.create("skysleylines_vision", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65_536, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    private static ByteBufferBuilder storage;

    private LeyVisionRenderer() {}

    static void draw(RenderLevelStageEvent event, List<Span> spans, List<Plume> plumes, double time) {
        if (storage == null) storage = new ByteBufferBuilder(65_536);
        Vec3 camera = event.getCamera().getPosition();
        var mesh = new BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        var paint = new Paint(mesh, event.getPoseStack().last().pose(), camera);
        for (Span span : spans) {
            double a = distanceFade(span.start(), camera) * span.startFade() * span.strength();
            double b = distanceFade(span.end(), camera) * span.endFade() * span.strength();
            if (Math.max(a, b) > 0.01 && event.getFrustum().isVisible(span.bounds())) river(paint, span, time, a, b);
        }
        for (Plume plume : plumes) {
            double alpha = distanceFade(plume.centre(), camera) * plume.strength();
            if (alpha > 0.01 && event.getFrustum().isVisible(plume.bounds())) nexus(paint, plume.centre(), time, alpha);
        }
        var data = mesh.build();
        if (data != null) ENERGY.draw(data);
    }

    static void clear() {
        if (storage != null) {
            storage.close();
            storage = null;
        }
    }

    private static double distanceFade(Vec3 point, Vec3 camera) {
        return Math.clamp((320 - Math.hypot(point.x - camera.x, point.z - camera.z)) / 48, 0, 1);
    }

    private static void river(Paint paint, Span span, double time, double a, double b) {
        double length = Math.hypot(span.end().x - span.start().x, span.end().z - span.start().z);
        double u = span.along(), v = u + length;
        double nx = span.nx(), nz = span.nz(), w = span.width();
        Vec3 from = wave(span.start(), nx, nz, w, u, time);
        Vec3 to = wave(span.end(), nx, nz, w, v, time);
        paint.band(from, to, nx, nz, -w, -w * 0.55, DEEP_BLUE, DEEP_BLUE, 0, 104, a, b);
        paint.band(from, to, nx, nz, -w * 0.55, 0, DEEP_BLUE, CYAN, 104, 140, a, b);
        paint.band(from, to, nx, nz, 0, w * 0.55, CYAN, DEEP_BLUE, 140, 104, a, b);
        paint.band(from, to, nx, nz, w * 0.55, w, DEEP_BLUE, DEEP_BLUE, 104, 0, a, b);

        // The flowing folds lean up out of the ribbon and fade into the air.
        Vec3 topA = from.add(0, 4.5 + 2.6 * Math.sin(u * 0.13 - time * 0.11), 0);
        Vec3 topB = to.add(0, 4.5 + 2.6 * Math.sin(v * 0.13 - time * 0.11), 0);
        paint.quad(from, to, topB, topA, CYAN, VIOLET, 94 * a, 94 * b, 0, 0);
        for (int filament = 0; filament < 3; filament++) {
            double lane = (filament - 1) * 0.34;
            double phase = filament * 1.7;
            double frequency = 0.075 + filament * 0.021;
            Vec3 first = offset(from, nx, nz, w * (lane + Math.sin(u * frequency - time * 0.16 + phase) * 0.16), 1.2 + filament);
            Vec3 last = offset(to, nx, nz, w * (lane + Math.sin(v * frequency - time * 0.16 + phase) * 0.16), 1.2 + filament);
            int colour = filament == 1 ? WHITE_BLUE : CYAN;
            paint.filament(first, last, nx, nz, 0.11 + w * 0.010, colour,
                    169 * a * pulse(u, time, phase), 169 * b * pulse(v, time, phase));
        }
        long slot = (long) Math.floor(u / 8 + 0.001);
        long flash = (long) Math.floor(time / 4);
        if (noise(slot, flash) > 0.76) {
            double side = noise(slot + 17, flash) > 0.5 ? 1 : -1;
            Vec3 root = from.lerp(to, 0.24).add(0, 1.2, 0);
            Vec3 kink = offset(from.lerp(to, 0.43), nx, nz, w * side * 0.32, 3.6 + w * 0.23);
            Vec3 branch = offset(from.lerp(to, 0.55), nx, nz, w * side * 0.69, 2.5 + w * 0.14);
            Vec3 tip = offset(to, nx, nz, w * side * 0.88, 5 + w * 0.27);
            double brightness = 204 * Math.min(a, b);
            paint.filament(root, kink, nx, nz, 0.23, WHITE_BLUE, brightness, brightness);
            paint.filament(kink, branch, nx, nz, 0.18, WHITE_BLUE, brightness, brightness * 0.76);
            paint.filament(branch, tip, nx, nz, 0.12, CYAN, brightness * 0.76, 0);
        }
    }

    private static double pulse(double along, double time, double phase) {
        return 0.60 + 0.40 * Math.sin(along * 0.17 - time * 0.36 + phase);
    }

    private static Vec3 wave(Vec3 point, double nx, double nz, double width, double along, double time) {
        return offset(point, nx, nz, width * 0.08 * Math.sin(along * 0.10 - time * 0.09),
                0.85 + 0.6 * Math.sin(along * 0.14 - time * 0.14));
    }

    private static Vec3 offset(Vec3 point, double nx, double nz, double across, double up) {
        return point.add(nx * across, up, nz * across);
    }

    private static void nexus(Paint paint, Vec3 centre, double time, double alpha) {
        // A violet updraft stays legible from the ground and from a survey flight.
        Vec3 base = centre.add(0, 0.4, 0);
        Vec3 middle = centre.add(1.1 * Math.sin(time * 0.05), 13, 1.1 * Math.cos(time * 0.05));
        Vec3 tip = middle.add(1.8 * Math.cos(time * 0.07), 24 + 2 * Math.sin(time * 0.14), 1.8 * Math.sin(time * 0.07));
        for (int fin = 0; fin < 3; fin++) {
            double angle = time * 0.027 + fin * Math.PI / 3;
            double nx = Math.cos(angle), nz = Math.sin(angle);
            paint.band(base, middle, nx, nz, -5.4, 0, VIOLET, LAVENDER, 0, 121, alpha, alpha);
            paint.band(base, middle, nx, nz, 0, 5.4, LAVENDER, VIOLET, 121, 0, alpha, alpha);
            paint.quad(offset(middle, nx, nz, -5.4, 0), middle, tip, tip, VIOLET, LAVENDER, 0, 121 * alpha, 0, 0);
            paint.quad(middle, offset(middle, nx, nz, 5.4, 0), tip, tip, LAVENDER, VIOLET, 121 * alpha, 0, 0, 0);
        }
        for (int ring = 0; ring < 2; ring++) {
            double radius = 8 + 5 * ring + Math.sin(time * 0.08) * 0.7;
            double rotation = time * (ring == 0 ? 0.026 : -0.018);
            for (int step = 0; step < 32; step++) {
                double angle = rotation + step * Math.TAU / 32;
                double end = angle + Math.TAU / 32 * (ring == 0 ? 1 : 0.68);
                Vec3 a = orbit(centre, angle, radius, ring), b = orbit(centre, end, radius, ring);
                paint.filament(a, b, Math.cos(angle), Math.sin(angle), ring == 0 ? 0.46 : 0.27,
                        ring == 0 ? LAVENDER : CYAN, 204 * alpha, 176 * alpha);
            }
        }
        for (int petal = 0; petal < 3; petal++) {
            double angle = time * 0.035 + petal * Math.TAU / 3;
            Vec3 a = orbit(centre, angle, 6, 0);
            for (int step = 1; step <= 6; step++) {
                double fraction = step / 6.0;
                double turn = angle + fraction * 2.5;
                Vec3 b = orbit(centre, turn, 6 * (1 - fraction) + 0.6, 0).add(0, fraction * 32, 0);
                double opacity = (1 - fraction * 0.85) * alpha;
                paint.filament(a, b, Math.cos(turn), Math.sin(turn), 0.30, LAVENDER, 202 * opacity, 177 * opacity);
                a = b;
            }
        }
    }

    private static Vec3 orbit(Vec3 centre, double angle, double radius, int ring) {
        return centre.add(radius * Math.cos(angle), 0.35 + ring * (2.4 + 1.8 * Math.sin(angle)), radius * Math.sin(angle));
    }

    private static double noise(long slot, long flash) {
        long value = slot * 0x9e3779b97f4a7c15L ^ flash * 0xd1b54a32d192ed03L;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        return ((value ^ (value >>> 27)) >>> 11) * 0x1.0p-53;
    }

    private record Paint(BufferBuilder vertices, Matrix4f pose, Vec3 camera) {
        void point(Vec3 p, int colour, double alpha) {
            vertices.addVertex(pose, (float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z))
                    .setColor(colour >> 16 & 255, colour >> 8 & 255, colour & 255, (int) alpha);
        }

        void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int first, int second, double aa, double ab, double ac, double ad) {
            point(a, first, aa);
            point(b, first, ab);
            point(c, second, ac);
            point(d, second, ad);
        }

        void band(Vec3 a, Vec3 b, double nx, double nz, double left, double right,
                  int first, int second, int alphaLeft, int alphaRight, double fadeA, double fadeB) {
            quad(offset(a, nx, nz, left, 0), offset(b, nx, nz, left, 0),
                    offset(b, nx, nz, right, 0), offset(a, nx, nz, right, 0),
                    first, second, alphaLeft * fadeA, alphaLeft * fadeB, alphaRight * fadeB, alphaRight * fadeA);
        }

        void filament(Vec3 a, Vec3 b, double nx, double nz, double radius, int colour, double aa, double ab) {
            quad(offset(a, nx, nz, -radius, 0), offset(b, nx, nz, -radius, 0),
                    offset(b, nx, nz, radius, 0), offset(a, nx, nz, radius, 0), colour, colour, aa, ab, ab, aa);
            quad(a.add(0, -radius, 0), b.add(0, -radius, 0), b.add(0, radius, 0), a.add(0, radius, 0),
                    colour, colour, aa, ab, ab, aa);
        }
    }
}
