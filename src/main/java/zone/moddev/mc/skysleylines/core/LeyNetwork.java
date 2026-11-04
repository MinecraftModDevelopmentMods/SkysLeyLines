package zone.moddev.mc.skysleylines.core;

import java.nio.charset.StandardCharsets;
import java.util.*;
import zone.moddev.mc.skysleylines.api.*;

/** Version-one bounded, order-independent geometry. No Minecraft or mutable server dependencies. */
public final class LeyNetwork implements LeyFieldSampler {
    @FunctionalInterface public interface NaturalHeight { OptionalInt at(int x, int z); }
    private record Cell(int x, int z) {}
    private record Segment(int cellX, int cellZ, double x, double z, double dx, double dz, double intensity) {
        double distance(double px, double pz) {
            double t = Math.clamp(((px - x) * dx + (pz - z) * dz) / (dx * dx + dz * dz), 0, 1);
            return StrictMath.hypot(px - x - t * dx, pz - z - t * dz);
        }
        LineReading reading(double px, double pz) {
            double t = Math.clamp(((px - x) * dx + (pz - z) * dz) / (dx * dx + dz * dz), 0, 1);
            double length = StrictMath.hypot(dx, dz);
            return new LineReading(x + t * dx, z + t * dz, distance(px, pz), dx / length, dz / length);
        }
    }
    private record Tile(List<Segment> lines, List<Nexus> nexuses) {}
    private final long salt;
    private final String dimension;
    private final LeyProfile profile;
    private final NaturalHeight height;
    private final boolean active;
    private final Map<Cell, Tile> tiles = lru(128);
    private final Map<Long, OptionalInt> heights = lru(8192);

    public LeyNetwork(long seed, String dimension, LeyProfile profile, NaturalHeight height, boolean active) {
        this.salt = mix(seed ^ dimensionHash(dimension) ^ 0x534B59534C455931L);
        this.dimension = dimension;
        this.profile = Objects.requireNonNull(profile);
        this.height = Objects.requireNonNull(height);
        this.active = active;
    }
    private static <K,V> Map<K,V> lru(int limit) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K,V> entry) { return size() > limit; }
        };
    }
    private static long dimensionHash(String dimension) {
        long result = 0xcbf29ce484222325L;
        for (byte value : dimension.getBytes(StandardCharsets.UTF_8)) result = (result ^ (value & 255)) * 0x100000001b3L;
        return result;
    }
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
    private double unit(int x, int z, long channel) {
        return (mix(salt ^ mix(x * 0x9e3779b97f4a7c15L) ^ mix(z * 0xd1b54a32d192ed03L) ^ channel) >>> 11) * 0x1.0p-53;
    }
    private Segment segment(int cx, int cz) {
        double spacing = profile.spacingBlocks();
        double x = (cx + 0.25 + unit(cx, cz, 1) * 0.5) * spacing;
        double z = (cz + 0.25 + unit(cx, cz, 2) * 0.5) * spacing;
        double angle = unit(cx, cz, 3) * StrictMath.PI * 2;
        double dx = StrictMath.cos(angle) * spacing * 3, dz = StrictMath.sin(angle) * spacing * 3;
        return new Segment(cx, cz, x - dx / 2, z - dz / 2, dx, dz, 0.5 + unit(cx, cz, 4) * 0.5);
    }
    private Tile tile(int cx, int cz) {
        Cell key = new Cell(cx, cz);
        synchronized (tiles) {
            Tile result = tiles.get(key);
            if (result == null) { result = compile(cx, cz); tiles.put(key, result); }
            return result;
        }
    }
    private Tile compile(int cx, int cz) {
        double spacing = profile.spacingBlocks(), margin = Math.max(profile.lineWidth(), profile.nexusRadius());
        double minX = cx * spacing - margin, minZ = cz * spacing - margin;
        double maxX = (cx + 1.0) * spacing + margin, maxZ = (cz + 1.0) * spacing + margin;
        List<Segment> lines = new ArrayList<>();
        List<Segment> candidates = new ArrayList<>();
        for (int x = cx - 3; x <= cx + 3; x++) for (int z = cz - 3; z <= cz + 3; z++) {
            Segment line = segment(x, z);
            candidates.add(line);
            if (Math.max(line.x, line.x + line.dx) >= minX && Math.min(line.x, line.x + line.dx) <= maxX
                    && Math.max(line.z, line.z + line.dz) >= minZ && Math.min(line.z, line.z + line.dz) <= maxZ) lines.add(line);
        }
        List<Nexus> nodes = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) for (int j = i + 1; j < lines.size(); j++) {
            Segment a = lines.get(i), b = lines.get(j);
            double cross = a.dx * b.dz - a.dz * b.dx;
            if (Math.abs(cross) < 1e-10) continue;
            double ox = b.x - a.x, oz = b.z - a.z;
            double t = (ox * b.dz - oz * b.dx) / cross, u = (ox * a.dz - oz * a.dx) / cross;
            if (t < 0 || t > 1 || u < 0 || u > 1) continue;
            double x = a.x + t * a.dx, z = a.z + t * a.dz;
            if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
            double strength = (a.intensity + b.intensity) / 2;
            for (Segment extra : candidates) if (extra != a && extra != b && extra.distance(x, z) < profile.lineWidth()) {
                strength = Math.min(1, strength + 0.1 * extra.intensity);
            }
            nodes.add(new Nexus(new NexusId(dimension, a.cellX, a.cellZ, b.cellX, b.cellZ), x, z, strength));
        }
        nodes.sort(Comparator.comparing(Nexus::id));
        return new Tile(List.copyOf(lines), List.copyOf(nodes));
    }
    private OptionalInt naturalHeight(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        synchronized (heights) {
            OptionalInt result = heights.get(key);
            if (result == null) { result = Objects.requireNonNull(height.at(x, z)); heights.put(key, result); }
            return result;
        }
    }
    @Override public LeyColumn sampleColumn(int x, int z) {
        if (!active) return LeyColumn.inactive();
        Tile tile = tile(Math.floorDiv(x, profile.spacingBlocks()), Math.floorDiv(z, profile.spacingBlocks()));
        double lineStrength = 0, distance = Double.POSITIVE_INFINITY;
        for (Segment line : tile.lines) {
            double d = line.distance(x, z);
            distance = Math.min(distance, d);
            lineStrength = Math.max(lineStrength, line.intensity * 0.5 * Math.max(0, 1 - d / profile.lineWidth()));
        }
        Nexus closest = null;
        double nexusDistance = Double.POSITIVE_INFINITY, nexusStrength = 0;
        for (Nexus node : tile.nexuses) {
            double d = StrictMath.sqrt(node.distanceSquared(x, z));
            if (d < profile.nexusRadius()) {
                nexusStrength = Math.max(nexusStrength, node.strength() * (1 - d / profile.nexusRadius()));
                if (d < nexusDistance || (d == nexusDistance && node.id().compareTo(closest.id()) < 0)) {
                    closest = node; nexusDistance = d;
                }
            }
        }
        // Empty columns have no terrain-following field, including void areas.
        OptionalInt surface = naturalHeight(x, z);
        if (surface.isEmpty()) return LeyColumn.inactive();
        return new LeyColumn(true, surface.getAsInt(), profile.verticalReach(), lineStrength, nexusStrength,
                Optional.ofNullable(closest), distance, nexusDistance);
    }
    private List<Tile> searchTiles(int x, int z, int radius) {
        if (radius <= 0 || radius > Math.min(4096, profile.spacingBlocks() * 2)) throw new IllegalArgumentException("Search radius out of bounds");
        List<Tile> result = new ArrayList<>();
        int spacing = profile.spacingBlocks();
        int minX = (int) Math.floorDiv((long) x - radius, spacing), maxX = (int) Math.floorDiv((long) x + radius, spacing);
        int minZ = (int) Math.floorDiv((long) z - radius, spacing), maxZ = (int) Math.floorDiv((long) z + radius, spacing);
        for (int cx = minX; cx <= maxX; cx++) for (int cz = minZ; cz <= maxZ; cz++) result.add(tile(cx, cz));
        return result;
    }
    @Override public Optional<Nexus> nearestNexus(int x, int z, int radius) {
        if (!active) return Optional.empty();
        return searchTiles(x, z, radius).stream().flatMap(t -> t.nexuses.stream()).distinct()
                .filter(n -> n.distanceSquared(x, z) <= (double) radius * radius)
                .min(Comparator.comparingDouble((Nexus n) -> n.distanceSquared(x, z)).thenComparing(Nexus::id));
    }
    @Override public Optional<LineReading> nearestLine(int x, int z, int radius) {
        if (!active) return Optional.empty();
        return searchTiles(x, z, radius).stream().flatMap(t -> t.lines.stream()).distinct()
                .map(l -> l.reading(x, z)).filter(l -> l.distance() <= radius)
                .min(Comparator.comparingDouble(LineReading::distance).thenComparingDouble(LineReading::x).thenComparingDouble(LineReading::z));
    }

    /** Bounded planar geometry for an authorized viewer. Makes no terrain calls. */
    public VisionGeometry vision(int x, int z) {
        if (!active) return VisionGeometry.EMPTY;
        int radius = VisionGeometry.RADIUS;
        List<Tile> region = searchTiles(x, z, radius);
        var reaches = region.stream().flatMap(tile -> tile.lines.stream()).distinct()
                .sorted(Comparator.comparingDouble((Segment line) -> line.distance(x, z))
                        .thenComparingInt(Segment::cellX).thenComparingInt(Segment::cellZ))
                .map(line -> clip(line, x, z, radius)).filter(Objects::nonNull)
                .limit(VisionGeometry.MAX_REACHES).toList();
        var crossings = region.stream().flatMap(tile -> tile.nexuses.stream()).distinct()
                .filter(node -> node.distanceSquared(x, z) <= radius * radius)
                .sorted(Comparator.comparingDouble((Nexus node) -> node.distanceSquared(x, z)).thenComparing(Nexus::id))
                .limit(VisionGeometry.MAX_CROSSINGS)
                .map(node -> new VisionGeometry.Crossing(node.x(), node.z(), (float) node.strength())).toList();
        return new VisionGeometry(profile.lineWidth(), reaches, crossings);
    }

    private static VisionGeometry.Reach clip(Segment line, double x, double z, double radius) {
        double length = Math.hypot(line.dx, line.dz);
        double ux = line.dx / length, uz = line.dz / length;
        double along = (x - line.x) * ux + (z - line.z) * uz;
        double across = (x - line.x) * uz - (z - line.z) * ux;
        if (Math.abs(across) >= radius) return null;
        double half = Math.sqrt(radius * radius - across * across);
        double first = Math.max(0, along - half), last = Math.min(length, along + half);
        if (last - first < 0.01) return null;
        return new VisionGeometry.Reach(line.x + first * ux, line.z + first * uz,
                line.x + last * ux, line.z + last * uz, (float) line.intensity);
    }
    public int cachedTileCount() { synchronized (tiles) { return tiles.size(); } }
    public int cachedHeightCount() { synchronized (heights) { return heights.size(); } }
}
