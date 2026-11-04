package zone.moddev.mc.skysleylines.api;

import java.util.Optional;

/** Thread-safe, read-only field queries; never loads chunks, mutates reserves, or exposes the world seed. */
public interface LeyFieldSampler {
    LeyColumn sampleColumn(int x, int z);
    default LeySample sample(int x, int y, int z) { return sampleColumn(x, z).atHeight(y); }
    /** Radius must be positive and at most min(4096, 2 * saved spacing). */
    Optional<Nexus> nearestNexus(int x, int z, int radius);
    /** Radius must be positive and at most min(4096, 2 * saved spacing). */
    Optional<LineReading> nearestLine(int x, int z, int radius);
}
