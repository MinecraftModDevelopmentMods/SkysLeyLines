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

    public record Reach(double x0, double z0, double x1, double z1, float strength, double y0, double y1) {
        public Reach(double x0, double z0, double x1, double z1, float strength) {
            this(x0, z0, x1, z1, strength, Double.NaN, Double.NaN);
        }

        public double heightAt(double fraction) { return y0 + fraction * (y1 - y0); }

        public Reach {
            checkPoint(x0, z0, strength);
            checkPoint(x1, z1, strength);
            if (Double.isNaN(y0) != Double.isNaN(y1)) throw new IllegalArgumentException("Incomplete ley reach elevation");
            checkElevation(y0);
            checkElevation(y1);
            double length = Math.hypot(x1 - x0, z1 - z0);
            if (length < 0.01 || length > 2 * RADIUS + 0.001) {
                throw new IllegalArgumentException("Invalid ley vision reach length");
            }
        }
    }

    public record Crossing(double x, double z, float strength, double y) {
        public Crossing(double x, double z, float strength) { this(x, z, strength, Double.NaN); }

        public Crossing {
            checkPoint(x, z, strength);
            checkElevation(y);
        }
    }

    private static void checkElevation(double y) {
        if (!Double.isNaN(y) && !(Math.abs(y) <= 4096)) throw new IllegalArgumentException("Invalid ley elevation");
    }

    private static void checkPoint(double x, double z, float strength) {
        // Include clipped ends just outside Minecraft's 30-million-block coordinate limit.
        if (!(Math.abs(x) <= 30_000_000 + RADIUS && Math.abs(z) <= 30_000_000 + RADIUS
                && strength > 0 && strength <= 1)) {
            throw new IllegalArgumentException("Invalid ley vision point");
        }
    }
}
