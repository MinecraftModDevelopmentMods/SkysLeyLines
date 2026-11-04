package zone.moddev.mc.skysleylines.api;

import java.util.Objects;

/** Collision-free identity of an intersection: dimension and an ordered pair of source cells. */
public record NexusId(String dimension, int ax, int az, int bx, int bz) implements Comparable<NexusId> {
    public NexusId {
        Objects.requireNonNull(dimension);
        if (!dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("Invalid dimension");
        if (ax > bx || (ax == bx && az >= bz)) throw new IllegalArgumentException("Line identities must be distinct and ordered");
    }

    @Override public int compareTo(NexusId other) {
        int result = dimension.compareTo(other.dimension);
        if (result == 0) result = Integer.compare(ax, other.ax);
        if (result == 0) result = Integer.compare(az, other.az);
        if (result == 0) result = Integer.compare(bx, other.bx);
        if (result == 0) result = Integer.compare(bz, other.bz);
        return result;
    }
}
