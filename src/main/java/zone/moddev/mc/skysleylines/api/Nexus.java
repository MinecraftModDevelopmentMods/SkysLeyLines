package zone.moddev.mc.skysleylines.api;

/** A planar intersection or a named nexus with an authored elevation. */
public record Nexus(NexusId id, double x, double z, double strength, java.util.OptionalInt authoredY) {
    public Nexus(NexusId id, double x, double z, double strength) {
        this(id, x, z, strength, java.util.OptionalInt.empty());
    }

    public Nexus {
        if (id == null || authoredY == null || id.pinId().isPresent() != authoredY.isPresent() || !Double.isFinite(x) || !Double.isFinite(z)
                || !Double.isFinite(strength) || strength <= 0 || strength > 1) {
            throw new IllegalArgumentException("Invalid nexus");
        }
    }
    @Override public int hashCode() {
        int hash = id.hashCode();
        hash = 31 * hash + Double.hashCode(x);
        hash = 31 * hash + Double.hashCode(z);
        hash = 31 * hash + Double.hashCode(strength);
        return authoredY.isPresent() ? 31 * hash + authoredY.hashCode() : hash;
    }

    public double distanceSquared(double px, double pz) {
        double dx = x - px, dz = z - pz;
        return dx * dx + dz * dz;
    }
    public int capacity(LeyProfile profile) {
        return Math.max(1, (int) StrictMath.round(profile.fullStrengthCapacity() * strength));
    }
}
