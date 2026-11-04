package zone.moddev.mc.skysleylines.api;

/** Closest point, distance, and unit heading of a nearby mathematical line segment. */
public record LineReading(double x, double z, double distance, double headingX, double headingZ) {}
