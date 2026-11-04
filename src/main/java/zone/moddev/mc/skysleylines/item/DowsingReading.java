package zone.moddev.mc.skysleylines.item;

import zone.moddev.mc.skysleylines.api.LeyFieldSampler;

/** A local visual hint, with no seed, nexus identity, coordinates, or account data. */
public record DowsingReading(int mode, float bearing) {
    public static final int QUIET = 0, LINE = 1, NEXUS = 2;
    public static final DowsingReading STILL = new DowsingReading(QUIET, 0);

    public DowsingReading {
        if (mode < QUIET || mode > NEXUS || !Float.isFinite(bearing) || bearing < -180 || bearing > 180) {
            throw new IllegalArgumentException("Invalid dowsing reading");
        }
    }

    public static DowsingReading sample(LeyFieldSampler field, int x, int y, int z, float facing) {
        var local = field.sample(x, y, z);
        if (!local.active() || local.verticalFactor() <= 0) return STILL;
        if (local.canDrawPower()) return new DowsingReading(NEXUS, 0);
        var nearby = field.nearestLine(x, z, 256);
        if (nearby.isEmpty()) return STILL;
        var line = nearby.get();
        double dx = line.x() - x, dz = line.z() - z;
        // On the line, follow its axis rather than flipping across the closest point.
        if (line.distance() < 2) {
            dx = line.headingX(); dz = line.headingZ();
            float forward = bearing(dx, dz);
            if (Math.abs(DowsingMotion.wrap(forward - facing)) > 90) { dx = -dx; dz = -dz; }
        }
        return new DowsingReading(LINE, bearing(dx, dz));
    }

    private static float bearing(double dx, double dz) {
        // Minecraft yaw: south 0, west +90, east -90.
        return (float) (StrictMath.round(StrictMath.toDegrees(StrictMath.atan2(-dx, dz)) / 5) * 5);
    }
}
