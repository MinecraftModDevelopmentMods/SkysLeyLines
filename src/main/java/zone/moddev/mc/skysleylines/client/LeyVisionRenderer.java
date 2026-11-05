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
import zone.moddev.mc.skysleylines.api.LeyVisionStyle;
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

    static void draw(RenderLevelStageEvent event, List<Span> spans, List<Plume> plumes, double time, LeyVisionStyle style) {
        if (storage == null) storage = new ByteBufferBuilder(65_536);
        Vec3 camera = event.getCamera().getPosition();
        var mesh = new BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        var paint = new Paint(mesh, event.getPoseStack().last().pose(), camera, style);
        for (Span span : spans) {
            double a = distanceFade(span.start(), camera) * span.startFade() * span.strength();
            double b = distanceFade(span.end(), camera) * span.endFade() * span.strength();
            if (Math.max(a, b) <= 0.01 || !event.getFrustum().isVisible(span.bounds())) continue;
            switch (style.pattern()) {
                case RIVER -> river(paint, span, time, a, b);
                case CRACKLE -> crackle(paint, span, time, a, b);
                case SPARKLES -> sparkle(paint, span.start(), time, slot(span), a);
                case GLOW -> glow(paint, span, a, b);
            }
        }
        for (Plume plume : plumes) {
            double alpha = distanceFade(plume.centre(), camera) * plume.strength();
            if (alpha <= 0.01 || !event.getFrustum().isVisible(plume.bounds())) continue;
            Vec3 centre = plume.centre();
            switch (style.pattern()) {
                case RIVER -> nexus(paint, centre, time, alpha);
                case CRACKLE -> halo(paint, centre, 2.1, alpha * (42 + 13 * Math.sin(time * 0.09)));
                case SPARKLES -> crossingSparkles(paint, centre, time, alpha);
                case GLOW -> halo(paint, centre, 4.8, alpha * 65);
            }
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

    private static long slot(Span span) {
        return Math.round(span.along() / 8);
    }

    private static void crackle(Paint paint, Span span, double time, double a, double b) {
        double scale = paint.style().scale();
        double nx = span.nx(), nz = span.nz();
        long slot = slot(span);
        double wave = time * 0.095 + slot * 0.83;
        Vec3 from = offset(span.start(), nx, nz, Math.sin(wave) * 0.20 * scale, 0.23 * scale);
        Vec3 to = offset(span.end(), nx, nz, Math.sin(wave + 0.83) * 0.20 * scale, 0.23 * scale);
        int colour = paint.style().lineRgb();
        double pulse = 0.65 + 0.35 * Math.sin(wave);
        paint.filament(from, to, nx, nz, 0.052 * scale, colour, a * 113 * pulse, b * 113 * pulse);
        long flash = (long) (time / 11);
        if (noise(slot, flash) > 0.86) {
            Vec3 root = from.lerp(to, 0.33);
            double side = noise(slot + 1, flash) > 0.5 ? 1 : -1;
            Vec3 elbow = offset(from.lerp(to, 0.50), nx, nz, side * 0.34 * scale, 0.46 * scale);
            Vec3 tip = offset(from.lerp(to, 0.68), nx, nz, side * 0.72 * scale, 0.33 * scale);
            double alpha = Math.min(a, b) * 126;
            paint.filament(root, elbow, nx, nz, 0.041 * scale, colour, alpha, alpha);
            paint.filament(elbow, tip, nx, nz, 0.030 * scale, colour, alpha, 0);
        }
    }

    private static void sparkle(Paint paint, Vec3 base, double time, long seed, double alpha) {
        double phase = noise(seed, 13) * Math.TAU;
        double brightness = 0.5 + 0.5 * Math.sin(time * 0.072 + phase);
        double size = (0.07 + 0.08 * brightness) * paint.style().scale();
        Vec3 centre = base.add(0, (0.28 + 0.10 * Math.sin(time * 0.04 + phase)) * paint.style().scale(), 0);
        mote(paint, centre, size, paint.style().lineRgb(), alpha * (68 + 135 * brightness));
    }

    private static void crossingSparkles(Paint paint, Vec3 centre, double time, double alpha) {
        long seed = (long) Math.floor(centre.x) * 31 + (long) Math.floor(centre.z);
        double scale = paint.style().scale();
        for (int i = 0; i < 14; i++) {
            double angle = noise(seed, i) * Math.TAU;
            double radius = (0.30 + noise(seed + 7, i) * 2.5) * scale;
            double brightness = 0.5 + 0.5 * Math.sin(time * 0.065 + i * 2.1);
            Vec3 point = centre.add(radius * Math.cos(angle), (0.20 + 0.33 * brightness) * scale,
                    radius * Math.sin(angle));
            mote(paint, point, (0.08 + 0.09 * brightness) * scale, paint.style().nexusRgb(),
                    alpha * (76 + 112 * brightness));
        }
    }

    private static void mote(Paint paint, Vec3 centre, double size, int colour, double alpha) {
        double angle = Math.atan2(paint.camera().z - centre.z, paint.camera().x - centre.x);
        double nx = Math.sin(angle), nz = -Math.cos(angle);
        paint.quad(centre.add(0, -size, 0), offset(centre, nx, nz, size, 0),
                centre.add(0, size, 0), offset(centre, nx, nz, -size, 0),
                colour, colour, alpha * 0.45, alpha, alpha * 0.55, alpha);
    }

    private static void glow(Paint paint, Span span, double a, double b) {
        double width = Math.clamp(span.width() * 0.32, 0.65, 4.0) * paint.style().scale();
        Vec3 from = span.start().add(0, 0.14, 0), to = span.end().add(0, 0.14, 0);
        int colour = paint.style().lineRgb();
        paint.band(from, to, span.nx(), span.nz(), -width, 0, colour, colour, 0, 70, a, b);
        paint.band(from, to, span.nx(), span.nz(), 0, width, colour, colour, 70, 0, a, b);
    }

    private static void halo(Paint paint, Vec3 centre, double radius, double alpha) {
        Vec3 base = centre.add(0, 0.18, 0);
        double size = radius * paint.style().scale();
        int colour = paint.style().nexusRgb();
        Vec3 edge = base.add(size, 0, 0);
        for (int step = 1; step <= 20; step++) {
            double angle = step * Math.TAU / 20;
            Vec3 next = base.add(size * Math.cos(angle), 0, size * Math.sin(angle));
            paint.quad(base, edge, next, base, colour, colour, alpha, 0, 0, alpha);
            edge = next;
        }
    }

    private static void river(Paint paint, Span span, double time, double a, double b) {
        double length = Math.hypot(span.end().x - span.start().x, span.end().z - span.start().z);
        double u = span.along(), v = u + length;
        double scale = paint.style().scale();
        double nx = span.nx(), nz = span.nz(), w = span.width() * scale;
        Vec3 from = wave(span.start().add(0, 1.15, 0), nx, nz, w, u, time);
        Vec3 to = wave(span.end().add(0, 1.15, 0), nx, nz, w, v, time);
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
        centre = centre.add(0, 1.15, 0);
        double scale = paint.style().scale();
        Vec3 base = centre.add(0, 0.4 * scale, 0);
        Vec3 middle = centre.add(scale * 1.1 * Math.sin(time * 0.05), 13 * scale, scale * 1.1 * Math.cos(time * 0.05));
        Vec3 tip = middle.add(scale * 1.8 * Math.cos(time * 0.07), scale * (24 + 2 * Math.sin(time * 0.14)), scale * 1.8 * Math.sin(time * 0.07));
        for (int fin = 0; fin < 3; fin++) {
            double angle = time * 0.027 + fin * Math.PI / 3;
            double nx = Math.cos(angle), nz = Math.sin(angle);
            paint.band(base, middle, nx, nz, -5.4 * scale, 0, VIOLET, LAVENDER, 0, 121, alpha, alpha);
            paint.band(base, middle, nx, nz, 0, 5.4 * scale, LAVENDER, VIOLET, 121, 0, alpha, alpha);
            paint.quad(offset(middle, nx, nz, -5.4 * scale, 0), middle, tip, tip, VIOLET, LAVENDER, 0, 121 * alpha, 0, 0);
            paint.quad(middle, offset(middle, nx, nz, 5.4 * scale, 0), tip, tip, LAVENDER, VIOLET, 121 * alpha, 0, 0, 0);
        }
        for (int ring = 0; ring < 2; ring++) {
            double radius = scale * (8 + 5 * ring + Math.sin(time * 0.08) * 0.7);
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

    private record Paint(BufferBuilder vertices, Matrix4f pose, Vec3 camera, LeyVisionStyle style) {
        private int riverColour(int colour) {
            if (colour == CYAN) return style.lineRgb();
            if (colour == VIOLET) return style.nexusRgb();
            if (colour == DEEP_BLUE && style.lineRgb() != CYAN) return mix(style.lineRgb(), 0x000020, 0.45);
            if (colour == WHITE_BLUE && style.lineRgb() != CYAN) return mix(style.lineRgb(), 0xffffff, 0.72);
            if (colour == LAVENDER && style.nexusRgb() != VIOLET) return mix(style.nexusRgb(), 0xffffff, 0.58);
            return colour;
        }

        private static int mix(int first, int second, double fraction) {
            int red = channel(first >> 16, second >> 16, fraction);
            int green = channel(first >> 8, second >> 8, fraction);
            return red << 16 | green << 8 | channel(first, second, fraction);
        }

        private static int channel(int first, int second, double fraction) {
            return (int) ((first & 255) * (1 - fraction) + (second & 255) * fraction);
        }
        void point(Vec3 p, int colour, double alpha) {
            if (style.pattern() == LeyVisionStyle.Pattern.RIVER) colour = riverColour(colour);
            vertices.addVertex(pose, (float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z))
                    .setColor(colour >> 16 & 255, colour >> 8 & 255, colour & 255, (int) (alpha * style.opacity()));
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
