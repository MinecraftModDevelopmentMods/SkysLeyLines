package zone.moddev.mc.skysleylines.probe;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.*;
import net.minecraft.world.level.levelgen.structure.placement.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Test-only real placement, starts, and pieces, with identical pieces in measured and control runs. */
final class StructureWorldgenProbe {
    private static final String MOD = OptionalLeyWorldgenProbe.MOD_ID;
    private static final String MODE = System.getProperty("ley.structure.mode", "both");
    private static final boolean BENCH = Boolean.getBoolean("ley.structure.bench");
    private static final boolean END_TO_END_ONLY = Boolean.getBoolean("ley.structure.endToEndOnly");
    private static final boolean TELEMETRY = Boolean.getBoolean("ley.structure.telemetry");
    private static final boolean BASELINE = Boolean.getBoolean("ley.structure.baseline");
    private static final Path ORACLE = Path.of(System.getProperty("ley.structure.oracle", "sites.tsv"));
    private static final ConcurrentSkipListSet<String> STARTS = new ConcurrentSkipListSet<>();
    private static final ConcurrentLinkedQueue<String> MEASUREMENTS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger PLACEMENTS = new AtomicInteger();
    private static final int[][] EXPLORATION = {{72, -47}, {74, -47}, {72, -45}, {74, -45},
            {76, -47}, {76, -45}, {72, -49}, {76, -49}};
    private static final com.sun.management.ThreadMXBean ALLOCATION =
            (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    private static volatile Map<Long, List<BlockPos>> oracle = Map.of();
    private static volatile Object sampler;
    private static volatile RandomState state;
    private static volatile boolean exploration;
    private static boolean present;
    private static Supplier<StructureType<?>> structure;
    private static Supplier<StructurePlacementType<?>> placement;
    private static Supplier<StructurePieceType> piece;

    private StructureWorldgenProbe() {}

    static void register(IEventBus bus, boolean available) {
        present = available;
        var structures = DeferredRegister.create(Registries.STRUCTURE_TYPE, MOD);
        structure = structures.register("nexus", () -> (StructureType<NexusStructure>) () -> NexusStructure.CODEC);
        structures.register(bus);
        var placements = DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, MOD);
        placement = placements.register("nexus", () -> (StructurePlacementType<NexusPlacement>) () -> NexusPlacement.CODEC);
        placements.register(bus);
        var pieces = DeferredRegister.create(Registries.STRUCTURE_PIECE, MOD);
        piece = pieces.register("nexus", () -> (StructurePieceType) (context, tag) -> new Marker(tag));
        pieces.register(bus);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, StructureWorldgenProbe::load);
        NeoForge.EVENT_BUS.addListener(StructureWorldgenProbe::started);
        NeoForge.EVENT_BUS.addListener(StructureWorldgenProbe::stopping);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, StructureWorldgenProbe::stopped);
    }

    private static void load(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)) return;
        state = level.getChunkSource().randomState();
        if (present) sampler = OptionalSampler.sampler(level);
        if (!present) return;
        try {
            if (MODE.equals("export")) {
                var rows = new ArrayList<String>();
                for (int x = -40; x <= 100; x++) for (int z = -80; z <= 40; z++) {
                    if (!inArea(x, z)) continue;
                    for (var site : OptionalSampler.sites(sampler, x, z))
                        rows.add(site.getX() + "\t" + site.getY() + "\t" + site.getZ());
                }
                Files.write(ORACLE, rows);
            }
            if (MODE.equals("control") || BENCH) {
                var sites = new HashMap<Long, List<BlockPos>>();
                for (String row : Files.readAllLines(ORACLE)) {
                    String[] fields = row.split("\t");
                    var pos = new BlockPos(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]), Integer.parseInt(fields[2]));
                    sites.computeIfAbsent(ChunkPos.asLong(pos), ignored -> new ArrayList<>()).add(pos);
                }
                sites.replaceAll((ignored, list) -> List.copyOf(list));
                oracle = Map.copyOf(sites);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot prepare structure benchmark oracle", failure);
        }
    }

    private static boolean inArea(int x, int z) {
        return (Math.abs(x) <= 40 && Math.abs(z) <= 40) || (x >= 50 && x <= 100 && z >= -80 && z <= -20);
    }

    private static void started(ServerStartedEvent event) {
        LogUtils.getLogger().info("LEY_STRUCTURE_SPAWN placements={} starts={}", PLACEMENTS.get(), STARTS.size());
        if (!BENCH) { exploration = true; return; }
        exploration = true;
        var level = event.getServer().overworld();
        var telemetry = TELEMETRY ? new ExplorationTelemetry(MODE) : null;
        long begin = System.nanoTime();
        for (var chunk : EXPLORATION) {
            if (telemetry != null) telemetry.begin(chunk[0], chunk[1]);
            long start = System.nanoTime();
            level.getChunk(chunk[0], chunk[1]);
            long elapsed = System.nanoTime() - start;
            if (telemetry != null) telemetry.end(elapsed);
            MEASUREMENTS.add("chunk\t" + elapsed + "\t0\t0");
        }
        LogUtils.getLogger().info("LEY_STRUCTURE_BENCH_DONE mode={} placements={} starts={} elapsedNs={}",
                MODE, PLACEMENTS.get(), STARTS.size(), System.nanoTime() - begin);
        if (telemetry != null) telemetry.write();
    }

    private static void stopping(ServerStoppingEvent event) {
        try {
            Files.write(Path.of("structure-starts.tsv"), STARTS);
            Files.write(Path.of("structure-measurements.tsv"), MEASUREMENTS);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot write structure benchmark receipts", failure);
        }
    }

    private static void stopped(ServerStoppedEvent event) {
        if (present && !BASELINE && OptionalSampler.lookup(state) != null)
            throw new IllegalStateException("Structure sampler outlived its server");
        LogUtils.getLogger().info("LEY_STRUCTURE_UNBOUND present={} baseline={}", present, BASELINE);
    }

    private static long bytes(long thread) { return ALLOCATION.getThreadAllocatedBytes(thread); }

    private static void measured(String label, long nanos, long bytes, long blocked) {
        MEASUREMENTS.add(label + "\t" + nanos + "\t" + bytes + "\t" + blocked);
    }

    private static List<BlockPos> sites(RandomState random, int x, int z, boolean measure) {
        if (!inArea(x, z)) return List.of();
        if (!present) return x == 0 && z == 0 || x == 72 && z == -47
                ? List.of(new BlockPos(x * 16 + 8, 80, z * 16 + 8)) : List.of();
        if (random != state) return List.of();
        var expected = oracle.getOrDefault(ChunkPos.asLong(x, z), List.of());
        if (END_TO_END_ONLY && measure) {
            PLACEMENTS.incrementAndGet();
            measure = false;
        }
        if (!measure) {
            if (MODE.equals("control")) return expected;
            var field = OptionalSampler.lookup(random);
            if (field == null) throw new IllegalStateException("Missing structure sampler");
            return OptionalSampler.sites(field, x, z);
        }
        PLACEMENTS.incrementAndGet();
        long thread = Thread.currentThread().threadId();
        var mx = java.lang.management.ManagementFactory.getThreadMXBean();
        boolean sampleColumns = BENCH && Math.floorMod(x + z, 4) == 0;
        long blocked = sampleColumns ? mx.getThreadInfo(thread).getBlockedCount() : 0;
        long b0 = bytes(thread), t0 = System.nanoTime();
        var field = MODE.equals("control") ? sampler : OptionalSampler.lookup(random);
        long t1 = System.nanoTime(), b1 = bytes(thread);
        if (field == null) throw new IllegalStateException("Structure worker did not see its sampler");
        var queried = MODE.equals("control") ? expected : OptionalSampler.query(field, x, z);
        long t2 = System.nanoTime(), b2 = bytes(thread);
        long queryBlocked = sampleColumns ? mx.getThreadInfo(thread).getBlockedCount() - blocked : 0;
        var actual = MODE.equals("control") ? expected : OptionalSampler.positions(queried);
        if (BENCH && !expected.equals(actual)) throw new IllegalStateException("Structure sites differ at " + x + ", " + z);
        measured("lookup", t1 - t0, b1 - b0, 0);
        measured(actual.isEmpty() ? "chunk-empty" : "chunk-occupied", t2 - t1, b2 - b1, 0);
        measured("combined", t2 - t0, b2 - b0, queryBlocked);
        if (sampleColumns) {
            blocked = mx.getThreadInfo(thread).getBlockedCount();
            long b3 = bytes(thread), t3 = System.nanoTime();
            double cold = OptionalSampler.column(field, x * 16 + 7, z * 16 + 7);
            long t4 = System.nanoTime(), b4 = bytes(thread);
            double warm = OptionalSampler.column(field, x * 16 + 7, z * 16 + 7);
            long t5 = System.nanoTime(), b5 = bytes(thread);
            if (cold != warm) throw new IllegalStateException("Column cache changed field result");
            measured("column-cold", t4 - t3, b4 - b3, mx.getThreadInfo(thread).getBlockedCount() - blocked);
            measured("column-warm", t5 - t4, b5 - b4, 0);
        }
        return actual;
    }

    private static final class NexusPlacement extends StructurePlacement {
        static final MapCodec<NexusPlacement> CODEC = MapCodec.unit(new NexusPlacement());
        NexusPlacement() { super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1, 0, Optional.empty()); }
        @Override protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int x, int z) {
            return !sites(state.randomState(), x, z, true).isEmpty();
        }
        @Override public StructurePlacementType<?> type() { return placement.get(); }
    }

    private static final class NexusStructure extends Structure {
        static final MapCodec<NexusStructure> CODEC = simpleCodec(NexusStructure::new);
        NexusStructure(StructureSettings settings) { super(settings); }
        @Override public StructureType<?> type() { return structure.get(); }
        @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
            var chunk = context.chunkPos();
            var sites = sites(context.randomState(), chunk.x, chunk.z, false);
            if (sites.isEmpty()) return Optional.empty();
            for (var site : sites) {
                if (!chunk.equals(new ChunkPos(site))) throw new IllegalStateException("Structure site owned by wrong chunk");
                STARTS.add((exploration ? "exploration" : "spawn") + "\t" + site.toShortString());
            }
            return Optional.of(new GenerationStub(sites.getFirst(), builder -> sites.forEach(site -> builder.addPiece(new Marker(site)))));
        }
    }

    private static final class Marker extends StructurePiece {
        Marker(BlockPos pos) { super(piece.get(), 0, BoundingBox.fromCorners(pos, pos)); }
        Marker(CompoundTag tag) { super(piece.get(), tag); }
        @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {}
        @Override public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                                          RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos origin) {
            var pos = getBoundingBox().getCenter();
            if (box.isInside(pos)) level.setBlock(pos, Blocks.AMETHYST_BLOCK.defaultBlockState(), 2);
        }
    }
}
