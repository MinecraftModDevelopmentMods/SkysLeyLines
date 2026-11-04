package zone.moddev.mc.skysleylines.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Hand geometry is rendered by the paired-hand event/player layer, never as a floating pair. */
final class DowsingItemRenderer extends BlockEntityWithoutLevelRenderer {
    DowsingItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) return;
        var mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        mc.getItemRenderer().render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, mc.getModelManager().getModel(DowsingClient.ICON));
        pose.popPose();
    }
}
