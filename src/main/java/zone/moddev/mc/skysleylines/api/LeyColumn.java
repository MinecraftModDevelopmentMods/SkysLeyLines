package zone.moddev.mc.skysleylines.api;

import java.util.Optional;

/** Reuse a column in generation loops. The primitive strength method allocates nothing and reads no terrain. */
public record LeyColumn(boolean active, int naturalSurfaceY, int verticalReach, double lineStrength,
                        double nexusStrength, Optional<Nexus> nexus, double distanceToLine, double distanceToNexus) {
    public double verticalFactorAt(int y) {
        return active ? Math.max(0, 1.0 - Math.abs((double) y - naturalSurfaceY) / verticalReach) : 0;
    }
    public double naturalStrengthAt(int y) { return Math.max(lineStrength, nexusStrength) * verticalFactorAt(y); }
    public LeySample atHeight(int y) {
        double vertical = verticalFactorAt(y);
        return new LeySample(active, naturalStrengthAt(y), lineStrength * vertical, nexusStrength * vertical,
                vertical, nexus, distanceToLine, distanceToNexus);
    }
    public static LeyColumn inactive() {
        return new LeyColumn(false, 0, 1, 0, 0, Optional.empty(), Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }
}
