package zone.moddev.mc.skysleylines.api;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Stable identity of a seeded intersection or a named pin. */
public record NexusId(String dimension, int ax, int az, int bx, int bz,
                      Optional<ResourceLocation> pinId) implements Comparable<NexusId> {
    public NexusId(String dimension, int ax, int az, int bx, int bz) {
        this(dimension, ax, az, bx, bz, Optional.empty());
    }

    public static NexusId pinned(String dimension, ResourceLocation name) {
        return new NexusId(dimension, 0, 0, 0, 0, Optional.of(name));
    }

    public NexusId {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(pinId);
        if (!dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("Invalid dimension");
        if (pinId.isPresent()) {
            if (ax != 0 || az != 0 || bx != 0 || bz != 0) throw new IllegalArgumentException("Pin has source cells");
        } else if (ax > bx || (ax == bx && az >= bz)) {
            throw new IllegalArgumentException("Line identities must be distinct and ordered");
        }
    }

    @Override public int compareTo(NexusId other) {
        int result = dimension.compareTo(other.dimension);
        if (result != 0) return result;
        if (pinId.isPresent()) return other.pinId.isPresent() ? pinId.get().compareTo(other.pinId.get()) : 1;
        if (other.pinId.isPresent()) return -1;
        result = Integer.compare(ax, other.ax);
        if (result == 0) result = Integer.compare(az, other.az);
        if (result == 0) result = Integer.compare(bx, other.bx);
        if (result == 0) result = Integer.compare(bz, other.bz);
        return result;
    }

    @Override public int hashCode() {
        if (pinId.isPresent()) return Objects.hash(dimension, pinId);
        int hash = dimension.hashCode();
        hash = 31 * hash + ax;
        hash = 31 * hash + az;
        hash = 31 * hash + bx;
        return 31 * hash + bz;
    }

    @Override public String toString() {
        if (pinId.isPresent()) return "NexusId[dimension=" + dimension + ", pin=" + pinId.get() + "]";
        return "NexusId[dimension=" + dimension + ", ax=" + ax + ", az=" + az + ", bx=" + bx + ", bz=" + bz + "]";
    }
}
