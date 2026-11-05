package zone.moddev.mc.skysleylines.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import zone.moddev.mc.skysleylines.server.VisionUpdates;

/** Server-side grants for the same ley vision used by the operator command. */
public final class LeyVision {
    private LeyVision() {}

    /**
     * Give a connected player vision for a positive number of server game ticks.
     * Call on the server thread. A namespaced source renews or replaces its own
     * grant. Grants from other sources remain independent.
     */
    public static void grant(ServerPlayer player, ResourceLocation source, int durationTicks) {
        VisionUpdates.grant(player, source, durationTicks);
    }

    /** Register a named style during mod setup, before the registry freezes at load completion. */
    public static void registerStyle(ResourceLocation id, LeyVisionStyle style) {
        VisionUpdates.registerStyle(id, style);
    }

    /**
     * Grant vision with a registered style ID. The latest active styled source takes precedence.
     * Renewing an active source keeps its activation order. Expiring or revoking it reveals the
     * next styled source, or the server default when an unstyled source is still active.
     */
    public static void grant(ServerPlayer player, ResourceLocation source, int durationTicks, ResourceLocation styleId) {
        VisionUpdates.grant(player, source, durationTicks, styleId);
    }

    /** Remove this source's grant on the server thread. */
    public static void revoke(ServerPlayer player, ResourceLocation source) {
        VisionUpdates.revoke(player, source);
    }
}
