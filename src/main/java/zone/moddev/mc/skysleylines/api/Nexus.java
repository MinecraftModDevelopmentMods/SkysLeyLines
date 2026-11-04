package zone.moddev.mc.skysleylines.api;

/** Seed-derived planar nexus. Its terrain height is resolved separately. */
public record Nexus(NexusId id, double x, double z, double strength) {
    public Nexus {
        if (id == null || !Double.isFinite(x) || !Double.isFinite(z)
                || !Double.isFinite(strength) || strength <= 0 || strength > 1) {
            throw new IllegalArgumentException("Invalid nexus");
        }
    }
    public double distanceSquared(double px, double pz) {
        double dx = x - px, dz = z - pz;
        return dx * dx + dz * dz;
    }
    public int capacity(LeyProfile profile) {
        return Math.max(1, (int) StrictMath.round(profile.fullStrengthCapacity() * strength));
    }
}
