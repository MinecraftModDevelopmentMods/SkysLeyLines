package zone.moddev.mc.skysleylines.core;

import java.nio.charset.StandardCharsets;
import java.util.*;
import zone.moddev.mc.skysleylines.api.*;

/** Version-one bounded, order-independent geometry. No Minecraft or mutable server dependencies. */
public final class LeyNetwork implements LeyFieldSampler {
    @FunctionalInterface public interface NaturalHeight { OptionalInt at(int x, int z); }
    private record Cell(int x, int z) {}
    private record Segment(int cellX, int cellZ, double x, double z, double dx, double dz, double intensity,
                           double y0, double y1) {
        Segment(int cellX, int cellZ, double x, double z, double dx, double dz, double intensity) {
            this(cellX, cellZ, x, z, dx, dz, intensity, Double.NaN, Double.NaN);
        }

        double fraction(double px, double pz) {
            return Math.clamp(((px - x) * dx + (pz - z) * dz) / (dx * dx + dz * dz), 0, 1);
        }

        double height(double fraction) { return y0 + fraction * (y1 - y0); }
        double distance(double px, double pz) {
            double t = fraction(px, pz);
            return StrictMath.hypot(px - x - t * dx, pz - z - t * dz);
        }
        LineReading reading(double px, double pz) {
            double t = fraction(px, pz);
            double length = StrictMath.hypot(dx, dz);
            return new LineReading(x + t * dx, z + t * dz, distance(px, pz), dx / length, dz / length);
        }
    }
    private record Tile(List<Segment> lines, List<Nexus> nexuses, Map<Long, List<Nexus>> byChunk) {}
    private record OverlayTile(List<Nexus> nodes, List<Segment> lines) {
        static final OverlayTile EMPTY = new OverlayTile(List.of(), List.of());
    }
    private record Overlay(Map<Long, OverlayTile> tiles, Map<Long, List<Nexus>> byChunk) {
        static final Overlay EMPTY = new Overlay(Map.of(), Map.of());
    }
    private static final int OVERLAY_TILE = 256;
    public static final int CONNECTOR_REACH = 512;
    private final Overlay overlay;
    private final long salt;
    private final String dimension;
    private final LeyProfile profile;
    private final NaturalHeight height;
    private final boolean active;
    private final Map<Cell, Tile> tiles = lru(128);
    private final Map<Long, OptionalInt> heights = lru(8192);

    public LeyNetwork(long seed, String dimension, LeyProfile profile, NaturalHeight height, boolean active) {
        this(seed, dimension, profile, height, active, List.of());
    }

    public LeyNetwork(long seed, String dimension, LeyProfile profile, NaturalHeight height, boolean active, List<Nexus> pins) {
        this.salt = mix(seed ^ dimensionHash(dimension) ^ 0x534B59534C455931L);
        this.dimension = dimension;
        this.profile = Objects.requireNonNull(profile);
        this.height = Objects.requireNonNull(height);
        this.active = active;
        this.overlay = active && !pins.isEmpty() ? buildOverlay(pins) : Overlay.EMPTY;
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
        return new Tile(List.copyOf(lines), List.copyOf(nodes), indexChunks(nodes));
    }
    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }

    private static Map<Long, List<Nexus>> indexChunks(List<Nexus> nodes) {
        var chunks = new HashMap<Long, List<Nexus>>();
        for (var node : nodes) {
            int x = Math.floorDiv((int) Math.floor(node.x()), 16);
            int z = Math.floorDiv((int) Math.floor(node.z()), 16);
            chunks.computeIfAbsent(key(x, z), ignored -> new ArrayList<>()).add(node);
        }
        chunks.replaceAll((ignored, values) -> List.copyOf(values));
        return Map.copyOf(chunks);
    }

    @Override public List<Nexus> nexusesInChunk(int chunkX, int chunkZ) {
        if (!active || chunkX < -134217728 || chunkX > 134217727
                || chunkZ < -134217728 || chunkZ > 134217727) return List.of();
        int x = chunkX * 16, z = chunkZ * 16, spacing = profile.spacingBlocks();
        int tx = Math.floorDiv(x, spacing), tz = Math.floorDiv(z, spacing);
        long key = key(chunkX, chunkZ);
        List<Nexus> seeded = tile(tx, tz).byChunk.getOrDefault(key, List.of());
        // A tile's margin can already contain the entire chunk across either boundary.
        int margin = Math.max(profile.lineWidth(), profile.nexusRadius());
        if (margin < 15) {
            int endX = Math.floorDiv(x + 15, spacing), endZ = Math.floorDiv(z + 15, spacing);
            if (tx != endX || tz != endZ) seeded = mergeSeededChunk(key, tx, tz, endX, endZ, seeded);
        }
        if (overlay == Overlay.EMPTY) return seeded;
        var pinned = overlay.byChunk.get(key);
        if (pinned == null || seeded.isEmpty()) return pinned == null ? seeded : pinned;
        var merged = new ArrayList<Nexus>(seeded.size() + pinned.size());
        merged.addAll(seeded);
        merged.addAll(pinned);
        return List.copyOf(merged);
    }

    private List<Nexus> mergeSeededChunk(long key, int tx, int tz, int endX, int endZ, List<Nexus> seeded) {
        TreeMap<NexusId, Nexus> merged = null;
        for (int cx = tx; cx <= endX; cx++) {
            for (int cz = tz; cz <= endZ; cz++) {
                if (cx == tx && cz == tz) continue;
                var adjacent = tile(cx, cz).byChunk.get(key);
                if (adjacent == null) continue;
                if (seeded.isEmpty()) {
                    seeded = adjacent;
                    continue;
                }
                if (merged == null) {
                    merged = new TreeMap<>();
                    for (var node : seeded) merged.put(node.id(), node);
                }
                for (var node : adjacent) merged.put(node.id(), node);
            }
        }
        return merged == null ? seeded : List.copyOf(merged.values());
    }

    public record Connection(double x, int y, double z, double strength) {}
    private record Join(Segment line, LineReading point) {}

    public List<Nexus> overlappingNexuses(Nexus pin) {
        int radius = 2 * profile.nexusRadius();
        int x = (int) pin.x(), z = (int) pin.z();
        return java.util.stream.Stream.concat(searchTiles(x, z, radius).stream().flatMap(tile -> tile.nexuses.stream()).distinct(),
                        overlayNear(x, z, radius).nodes.stream())
                .filter(other -> other.distanceSquared(x, z) < (double) radius * radius)
                .sorted(Comparator.comparing(Nexus::id)).toList();
    }

    /** Computes joins against the seeded network alone, for preview and restart. */
    public List<Connection> connections(Nexus pin) {
        int x = (int) pin.x(), z = (int) pin.z();
        return searchTiles(x, z, CONNECTOR_REACH).stream().flatMap(tile -> tile.lines.stream()).distinct()
                .map(line -> new Join(line, line.reading(x, z)))
                .filter(join -> join.point.distance() >= 0.01 && join.point.distance() <= CONNECTOR_REACH)
                .sorted(Comparator.comparingDouble((Join join) -> join.point.distance())
                        .thenComparingInt(join -> join.line.cellX).thenComparingInt(join -> join.line.cellZ))
                .map(join -> {
                    var point = join.point;
                    var surface = naturalHeight((int) Math.floor(point.x()), (int) Math.floor(point.z()));
                    return surface.isEmpty() ? null : new Connection(point.x(), surface.getAsInt(), point.z(),
                            Math.min(pin.strength(), join.line.intensity));
                }).filter(Objects::nonNull).limit(2).toList();
    }

    private Overlay buildOverlay(List<Nexus> input) {
        var pins = input.stream().sorted(Comparator.comparing(Nexus::id)).toList();
        var tiles = new HashMap<Long, OverlayTile>();
        for (var pin : pins) {
            int radius = profile.nexusRadius();
            index(tiles, pin.x() - radius, pin.z() - radius, pin.x() + radius, pin.z() + radius, pin, null);
            int width = profile.lineWidth();
            for (var join : connections(pin)) {
                var line = new Segment(0, 0, pin.x(), pin.z(), join.x - pin.x(), join.z - pin.z(), join.strength,
                        pin.authoredY().orElseThrow(), join.y);
                index(tiles, Math.min(pin.x(), join.x) - width, Math.min(pin.z(), join.z) - width,
                        Math.max(pin.x(), join.x) + width, Math.max(pin.z(), join.z) + width, null, line);
            }
        }
        tiles.replaceAll((ignored, tile) -> new OverlayTile(List.copyOf(tile.nodes), List.copyOf(tile.lines)));
        return new Overlay(Map.copyOf(tiles), indexChunks(pins));
    }

    private static void index(Map<Long, OverlayTile> tiles, double minX, double minZ, double maxX, double maxZ,
                              Nexus node, Segment line) {
        for (int x = overlayCell(minX); x <= overlayCell(maxX); x++) {
            for (int z = overlayCell(minZ); z <= overlayCell(maxZ); z++) {
                var tile = tiles.computeIfAbsent(key(x, z), ignored -> new OverlayTile(new ArrayList<>(), new ArrayList<>()));
                if (node != null) tile.nodes.add(node); else tile.lines.add(line);
            }
        }
    }

    private static int overlayCell(double coordinate) { return (int) Math.floor(coordinate / OVERLAY_TILE); }

    private OverlayTile overlayAt(int x, int z) {
        return overlay.tiles.getOrDefault(key(Math.floorDiv(x, OVERLAY_TILE), Math.floorDiv(z, OVERLAY_TILE)), OverlayTile.EMPTY);
    }

    private List<LeyColumn.Influence> authoredAt(int x, int z) {
        if (overlay == Overlay.EMPTY) return List.of();
        var tile = overlayAt(x, z);
        if (tile == OverlayTile.EMPTY) return List.of();
        var influences = new ArrayList<LeyColumn.Influence>();
        for (var node : tile.nodes) {
            double distance = Math.sqrt(node.distanceSquared(x, z));
            int radius = profile.nexusRadius();
            if (distance < radius) influences.add(new LeyColumn.Influence(node.authoredY().orElseThrow(),
                    node.strength() * (1 - distance / radius), distance, radius, node));
        }
        for (var line : tile.lines) {
            double distance = line.distance(x, z);
            int width = profile.lineWidth();
            if (distance < width) influences.add(new LeyColumn.Influence(line.height(line.fraction(x, z)),
                    line.intensity * 0.5 * (1 - distance / width), distance, width, null));
        }
        return influences;
    }

    private OverlayTile overlayNear(int x, int z, int radius) {
        if (overlay == Overlay.EMPTY) return OverlayTile.EMPTY;
        var nodes = new LinkedHashSet<Nexus>();
        var lines = new LinkedHashSet<Segment>();
        for (int cx = overlayCell((double) x - radius); cx <= overlayCell((double) x + radius); cx++) {
            for (int cz = overlayCell((double) z - radius); cz <= overlayCell((double) z + radius); cz++) {
                var tile = overlay.tiles.get(key(cx, cz));
                if (tile != null) { nodes.addAll(tile.nodes); lines.addAll(tile.lines); }
            }
        }
        return new OverlayTile(List.copyOf(nodes), List.copyOf(lines));
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
                Optional.ofNullable(closest), distance, nexusDistance, authoredAt(x, z));
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
        return java.util.stream.Stream.concat(searchTiles(x, z, radius).stream().flatMap(t -> t.nexuses.stream()).distinct(),
                        overlayNear(x, z, radius).nodes.stream())
                .filter(n -> n.distanceSquared(x, z) <= (double) radius * radius)
                .min(Comparator.comparingDouble((Nexus n) -> n.distanceSquared(x, z)).thenComparing(Nexus::id));
    }
    @Override public Optional<LineReading> nearestLine(int x, int z, int radius) {
        if (!active) return Optional.empty();
        return java.util.stream.Stream.concat(searchTiles(x, z, radius).stream().flatMap(t -> t.lines.stream()).distinct(),
                        overlayNear(x, z, radius).lines.stream())
                .map(l -> l.reading(x, z)).filter(l -> l.distance() <= radius)
                .min(Comparator.comparingDouble(LineReading::distance).thenComparingDouble(LineReading::x).thenComparingDouble(LineReading::z));
    }

    /** Bounded planar geometry for an authorized viewer. Makes no terrain calls. */
    public VisionGeometry vision(int x, int z) {
        if (!active) return VisionGeometry.EMPTY;
        int radius = VisionGeometry.RADIUS;
        List<Tile> region = searchTiles(x, z, radius);
        var nearby = overlayNear(x, z, radius);
        var reaches = java.util.stream.Stream.concat(region.stream().flatMap(tile -> tile.lines.stream()).distinct(), nearby.lines.stream())
                .sorted(Comparator.comparingDouble((Segment line) -> line.distance(x, z))
                        .thenComparingInt(Segment::cellX).thenComparingInt(Segment::cellZ))
                .map(line -> clip(line, x, z, radius)).filter(Objects::nonNull)
                .limit(VisionGeometry.MAX_REACHES).toList();
        var crossings = java.util.stream.Stream.concat(region.stream().flatMap(tile -> tile.nexuses.stream()).distinct(), nearby.nodes.stream())
                .filter(node -> node.distanceSquared(x, z) <= radius * radius)
                .sorted(Comparator.comparingDouble((Nexus node) -> node.distanceSquared(x, z)).thenComparing(Nexus::id))
                .limit(VisionGeometry.MAX_CROSSINGS)
                .map(node -> new VisionGeometry.Crossing(node.x(), node.z(), visionStrength(node.strength()), node.authoredY().isPresent() ? node.authoredY().getAsInt() : Double.NaN)).toList();
        return new VisionGeometry(profile.lineWidth(), reaches, crossings);
    }

    private static float visionStrength(double strength) { return Math.max(Float.MIN_VALUE, (float) strength); }

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
                line.x + last * ux, line.z + last * uz, visionStrength(line.intensity), line.height(first / length), line.height(last / length));
    }
    public int cachedTileCount() { synchronized (tiles) { return tiles.size(); } }
    public int cachedHeightCount() { synchronized (heights) { return heights.size(); } }
}
