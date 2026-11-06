package zone.moddev.mc.skysleylines.probe;

import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/** Process counters and JFR correlation for the eight end-to-end chunk requests. */
final class ExplorationTelemetry {
    private static final String HEADER = "index\tmode\tchunkX\tchunkZ\tstartEpochMs\twallNs\tprocessCpuNs"
            + "\tserverCpuNs\tgcCollections\tgcMs\tjitMs";

    @Name("skysleylines.ExplorationChunk")
    @Label("Ley Lines exploration chunk")
    @StackTrace(false)
    private static final class ChunkEvent extends Event {
        int index;
        String mode;
        int chunkX;
        int chunkZ;
    }

    private record Counters(long processCpuNs, long serverCpuNs, long gcCollections, long gcMs, long jitMs) {}

    private final String mode;
    private final OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    private final List<java.lang.management.GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
    private final java.lang.management.ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private final java.lang.management.CompilationMXBean compiler = ManagementFactory.getCompilationMXBean();
    private final List<String> rows = new ArrayList<>(9);
    private ChunkEvent event;
    private Counters before;
    private long startEpochMs;

    ExplorationTelemetry(String mode) {
        this.mode = mode;
        rows.add(HEADER);
    }

    void begin(int x, int z) {
        before = counters();
        event = new ChunkEvent();
        event.index = rows.size();
        event.mode = mode;
        event.chunkX = x;
        event.chunkZ = z;
        startEpochMs = System.currentTimeMillis();
        event.begin();
    }

    void end(long elapsedNs) {
        event.end();
        Counters after = counters();
        rows.add(event.index + "\t" + mode + "\t" + event.chunkX + "\t" + event.chunkZ + "\t" + startEpochMs
                + "\t" + elapsedNs + "\t" + delta(before.processCpuNs(), after.processCpuNs())
                + "\t" + delta(before.serverCpuNs(), after.serverCpuNs())
                + "\t" + delta(before.gcCollections(), after.gcCollections()) + "\t" + delta(before.gcMs(), after.gcMs())
                + "\t" + delta(before.jitMs(), after.jitMs()));
        event.commit();
    }

    void write() {
        try {
            Files.write(Path.of("structure-telemetry.tsv"), rows);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot write structure telemetry", failure);
        }
    }

    private Counters counters() {
        long collections = 0;
        long gcMs = 0;
        for (var collector : collectors) {
            collections += Math.max(0, collector.getCollectionCount());
            gcMs += Math.max(0, collector.getCollectionTime());
        }
        long jitMs = compiler != null && compiler.isCompilationTimeMonitoringSupported()
                ? compiler.getTotalCompilationTime() : -1;
        return new Counters(os != null ? os.getProcessCpuTime() : -1, threads.getCurrentThreadCpuTime(),
                collections, gcMs, jitMs);
    }

    private static long delta(long before, long after) {
        return before < 0 || after < 0 ? -1 : after - before;
    }
}
