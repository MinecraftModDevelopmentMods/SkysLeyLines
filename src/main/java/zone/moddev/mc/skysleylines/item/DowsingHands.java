package zone.moddev.mc.skysleylines.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/** One paired item can occupy either inventory hand; other held tools keep their own hand. */
public final class DowsingHands {
    private DowsingHands() {}
    public static boolean hasRods(LivingEntity entity) {
        return entity.getMainHandItem().getItem() instanceof DowsingRods || entity.getOffhandItem().getItem() instanceof DowsingRods;
    }
    public static boolean paired(LivingEntity entity) {
        if (!hasRods(entity)) return false;
        return (entity.getMainHandItem().isEmpty() || entity.getMainHandItem().getItem() instanceof DowsingRods)
                && (entity.getOffhandItem().isEmpty() || entity.getOffhandItem().getItem() instanceof DowsingRods);
    }
    public static InteractionHand source(LivingEntity entity) {
        return entity.getMainHandItem().getItem() instanceof DowsingRods ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }
}
