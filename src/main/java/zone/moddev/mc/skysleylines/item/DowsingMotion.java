package zone.moddev.mc.skysleylines.item;

/** Tick-based interpolation shared by first/third person; independent of frame rate. */
public final class DowsingMotion {
    private float previousBearing, bearing, previousCrossing, crossing, previousSignal, signal;

    public void tick(DowsingReading reading) {
        previousBearing = bearing;
        previousCrossing = crossing;
        previousSignal = signal;
        if (reading.mode() == DowsingReading.LINE) {
            if (signal < 0.01f) previousBearing = bearing = reading.bearing();
            bearing += wrap(reading.bearing() - bearing) * 0.35f;
        }
        crossing += ((reading.mode() == DowsingReading.NEXUS ? 1 : 0) - crossing) * 0.3f;
        signal += ((reading.mode() == DowsingReading.LINE ? 1 : 0) - signal) * 0.3f;
    }

    /** Positive angles follow Minecraft yaw; the left rod swings inward at a nexus. */
    public float angle(boolean left, float facing, float partialTick) {
        float cross = lerp(previousCrossing, crossing, partialTick);
        float yaw = previousBearing + wrap(bearing - previousBearing) * partialTick;
        float turn = Math.max(-85, Math.min(85, wrap(yaw - facing))) * lerp(previousSignal, signal, partialTick);
        return turn * (1 - cross) + (left ? 50 : -50) * cross;
    }

    public static float wrap(float degrees) {
        float result = degrees % 360;
        return result >= 180 ? result - 360 : result < -180 ? result + 360 : result;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
