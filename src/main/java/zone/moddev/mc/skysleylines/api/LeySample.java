package zone.moddev.mc.skysleylines.api;

import java.util.Optional;

/** Read-only environmental sample. Natural strength does not depend on remaining reserves. */
public record LeySample(boolean active, double naturalStrength, double lineStrength, double nexusStrength,
                        double verticalFactor, Optional<Nexus> nexus, double distanceToLine, double distanceToNexus) {
    public double dimensionalResistance() { return 1.0 - naturalStrength; }
    public boolean canDrawPower() { return active && nexus.isPresent() && nexusStrength > 0 && verticalFactor > 0; }
}
