package zone.moddev.mc.skysleylines.core;

import java.util.List;

/** A bounded patch of the planar field. Coordinates and strength suffice to draw it. */
public record VisionGeometry(int lineHalfWidth, List<Reach> reaches, List<Crossing> crossings) {
    public static final int RADIUS = 384;
    public static final int MAX_REACHES = 24;
    public static final int MAX_CROSSINGS = 32;
    public static final VisionGeometry EMPTY = new VisionGeometry(1, List.of(), List.of());

    public VisionGeometry {
        reaches = List.copyOf(reaches);
        crossings = List.copyOf(crossings);
        if (lineHalfWidth < 1 || lineHalfWidth > 128 || reaches.size() > MAX_REACHES || crossings.size() > MAX_CROSSINGS) {
            throw new IllegalArgumentException("Invalid ley vision geometry");
        }
    }

    public record Reach(double x0, double z0, double x1, double z1, float strength) {
        public Reach {
            checkPoint(x0, z0, strength);
            checkPoint(x1, z1, strength);
            double length = Math.hypot(x1 - x0, z1 - z0);
            if (length < 0.01 || length > 2 * RADIUS + 0.001) {
                throw new IllegalArgumentException("Invalid ley vision reach length");
            }
        }
    }

    public record Crossing(double x, double z, float strength) {
        public Crossing { checkPoint(x, z, strength); }
    }

    private static void checkPoint(double x, double z, float strength) {
        // Include clipped ends just outside Minecraft's 30-million-block coordinate limit.
        if (!(Math.abs(x) <= 30_000_000 + RADIUS && Math.abs(z) <= 30_000_000 + RADIUS
                && strength > 0 && strength <= 1)) {
            throw new IllegalArgumentException("Invalid ley vision point");
        }
    }
}
