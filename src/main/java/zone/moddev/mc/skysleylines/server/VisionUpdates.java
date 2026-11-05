package zone.moddev.mc.skysleylines.server;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import zone.moddev.mc.skysleylines.LeyConfig;
import zone.moddev.mc.skysleylines.api.LeyVisionStyle;
import zone.moddev.mc.skysleylines.core.LeyNetwork;
import zone.moddev.mc.skysleylines.core.VisionGrants;
import zone.moddev.mc.skysleylines.core.VisionStyles;
import zone.moddev.mc.skysleylines.core.VisionGeometry;
import zone.moddev.mc.skysleylines.network.VisionPayload;

/** One session per authorized player. World data and the nexus ledger are untouched. */
public final class VisionUpdates {
    private static final VisionStyles STYLES = new VisionStyles();
    private static final int MOVE_THRESHOLD = 48;
    private static final int UPDATE_INTERVAL = 10;
    private static final Map<ServerPlayer, Session> SESSIONS = new IdentityHashMap<>();

    private static final class Session {
        final VisionGrants grants = new VisionGrants();
        LeyVisionStyle lastStyle;
        VisionGeometry geometry;
        boolean operator;
        boolean clearOnRemoval;
        ServerLevel lastLevel;
        int lastX, lastZ;
        long lastSent;
    }

    private VisionUpdates() {}

    private static MinecraftServer serverThread(ServerPlayer player) {
        MinecraftServer server = player.serverLevel().getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Ley vision requires the server thread");
        return server;
    }

    public static void registerStyle(ResourceLocation id, LeyVisionStyle style) { STYLES.register(id, style); }
    public static void freezeStyles() { STYLES.freeze(); }

    public static void grant(ServerPlayer player, ResourceLocation source, int ticks) {
        grantResolved(player, source, ticks, null);
    }

    public static void grant(ServerPlayer player, ResourceLocation source, int ticks, ResourceLocation styleId) {
        grantResolved(player, source, ticks, STYLES.require(styleId));
    }

    private static void grantResolved(ServerPlayer player, ResourceLocation source, int ticks, LeyVisionStyle style) {
        Objects.requireNonNull(source);
        if (ticks <= 0) throw new IllegalArgumentException("Vision duration must be positive");
        long now = serverThread(player).overworld().getGameTime();
        SESSIONS.computeIfAbsent(player, ignored -> new Session()).grants.grant(source, now, ticks, style);
    }

    public static void revoke(ServerPlayer player, ResourceLocation source) {
        serverThread(player);
        Objects.requireNonNull(source);
        Session session = SESSIONS.get(player);
        if (session != null) session.grants.revoke(source);
    }

    static boolean operatorEnabled(ServerPlayer player) {
        Session session = SESSIONS.get(player);
        return session != null && session.operator;
    }

    static void operator(ServerPlayer player, boolean enabled) {
        if (enabled) SESSIONS.computeIfAbsent(player, ignored -> new Session()).operator = true;
        else if (SESSIONS.containsKey(player)) SESSIONS.get(player).operator = false;
    }

    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (SESSIONS.isEmpty()) return;
        var fallback = STYLES.require(ResourceLocation.fromNamespaceAndPath("skysleylines", LeyConfig.DEFAULT_VISION_STYLE.get()));
        var entries = SESSIONS.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            ServerPlayer player = entry.getKey();
            ServerLevel level = player.serverLevel();
            if (level.getServer() != server) continue;
            if (server.getPlayerList().getPlayer(player.getUUID()) != player) {
                entries.remove();
                continue;
            }
            Session session = entry.getValue();
            session.grants.expire(now);
            if (session.operator && !player.hasPermissions(2)) session.operator = false;
            if (!session.operator && session.grants.isEmpty()) {
                if (session.clearOnRemoval) send(player, level, VisionGeometry.EMPTY, VisionStyles.RIVER);
                entries.remove();
                continue;
            }
            int x = player.getBlockX(), z = player.getBlockZ();
            boolean moved = Math.hypot(x - (double) session.lastX, z - (double) session.lastZ) >= MOVE_THRESHOLD;
            if (session.lastLevel != level || (moved && now - session.lastSent >= UPDATE_INTERVAL)) {
                var field = (LeyNetwork) LeyServerContext.forLevel(level).field();
                session.geometry = field.vision(x, z);
                session.lastStyle = null;
                session.lastLevel = level;
                session.lastX = x;
                session.lastZ = z;
                session.lastSent = now;
            }
            var style = session.grants.styleOr(fallback);
            if (!style.equals(session.lastStyle)) {
                send(player, level, session.geometry, style);
                session.lastStyle = style;
                session.clearOnRemoval = true;
            }
        }
    }

    private static void send(ServerPlayer player, ServerLevel level, VisionGeometry geometry, LeyVisionStyle style) {
        PacketDistributor.sendToPlayer(player, new VisionPayload(level.dimension().location(), geometry, style));
    }

    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SESSIONS.remove(player);
    }

    public static void respawn(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer player && SESSIONS.remove(player) != null
                && event.getEntity() instanceof ServerPlayer replacement) {
            // Clear after vanilla sends the respawn packet. A child can grant again before the next tick.
            SESSIONS.computeIfAbsent(replacement, ignored -> new Session()).clearOnRemoval = true;
        }
    }

    static void clear(MinecraftServer server) {
        SESSIONS.keySet().removeIf(player -> player.serverLevel().getServer() == server);
    }
}
