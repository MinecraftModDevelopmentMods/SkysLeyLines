package zone.moddev.mc.skysleylines.server;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import com.mojang.logging.LogUtils;
import zone.moddev.mc.skysleylines.api.*;
import zone.moddev.mc.skysleylines.core.LeyNetwork;
import zone.moddev.mc.skysleylines.core.NexusLedger;

/** Binds immutable sampling inputs and server-thread reserve access without worldgen callbacks. */
public final class LeyServerContext {
    private static final Map<ServerLevel, LeyLinesApi.Context> CONTEXTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, LeyWorldData> WORLDS = new IdentityHashMap<>();
    private LeyServerContext() {}
    private static void serverThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Ley context/reserve operations require the server thread");
    }
    public static LeyLinesApi.Context forLevel(ServerLevel level) {
        MinecraftServer server = level.getServer();
        serverThread(server);
        return CONTEXTS.computeIfAbsent(level, key -> create(key, WORLDS.computeIfAbsent(server, LeyWorldData::get)));
    }
    private static LeyLinesApi.Context create(ServerLevel level, LeyWorldData data) {
        MinecraftServer server = level.getServer();
        String dimension = level.dimension().location().toString();
        var generator = level.getChunkSource().getGenerator();
        var randomState = level.getChunkSource().randomState();
        var heights = LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight());
        LeyNetwork field = new LeyNetwork(level.getSeed(), dimension, data.profile(), (x, z) -> {
            int free = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, randomState);
            return free <= heights.getMinBuildHeight() ? OptionalInt.empty() : OptionalInt.of(free - 1);
        }, dimension.equals("minecraft:overworld"));
        NexusPowerService power = new NexusPowerService() {
            @Override public Optional<NexusSnapshot> quoteAt(int x, int y, int z) {
                serverThread(server);
                LeySample sample = field.sample(x, y, z);
                if (!sample.canDrawPower()) return Optional.empty();
                Nexus nexus = sample.nexus().orElseThrow();
                int capacity = nexus.capacity(data.profile());
                var balance = data.ledger().quote(nexus.id(), capacity, server.overworld().getGameTime());
                return Optional.of(new NexusSnapshot(nexus, balance, data.profile().rechargeTicks(), sample.naturalStrength()));
            }
            @Override public Map<ResourceLocation, MagicAmount> rates() { serverThread(server); return data.ledger().rates(); }
            @Override public PowerResult transact(PowerRequest request) {
                serverThread(server);
                Optional<NexusSnapshot> quote = quoteAt(request.x(), request.y(), request.z());
                if (quote.isEmpty()) return PowerResult.failed(PowerResult.Status.NO_NEXUS);
                NexusSnapshot node = quote.get();
                return data.ledger().transact(node.nexus().id(), node.capacity(), server.overworld().getGameTime(), request, new NexusLedger.Hooks() {
                    @Override public boolean before(PowerResult.Receipt proposed) {
                        return !NeoForge.EVENT_BUS.post(new NexusTransactionEvent.Pre(level, proposed)).isCanceled();
                    }
                    @Override public void after(PowerResult.Receipt receipt) {
                        data.setDirty();
                        NeoForge.EVENT_BUS.post(new NexusTransactionEvent.Post(level, receipt));
                    }
                    @Override public void failure(RuntimeException failure) { LogUtils.getLogger().error("Ley transaction listener failed; consult receipt for commit status", failure); }
                });
            }
        };
        return new LeyLinesApi.Context(data.profile(), field, power);
    }
    public static void clear(MinecraftServer server) {
        LeyCommands.clear(server);
        VisionUpdates.clear(server);
        CONTEXTS.keySet().removeIf(level -> level.getServer() == server);
        WORLDS.remove(server);
    }
    static LeyWorldData world(MinecraftServer server) {
        serverThread(server);
        forLevel(server.overworld());
        return WORLDS.get(server);
    }
}
