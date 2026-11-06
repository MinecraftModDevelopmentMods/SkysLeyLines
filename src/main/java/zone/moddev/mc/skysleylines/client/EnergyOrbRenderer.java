package zone.moddev.mc.skysleylines.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import zone.moddev.mc.skysleylines.server.EnergyOrb;

final class EnergyOrbRenderer extends EntityRenderer<EnergyOrb> {
    private final ItemRenderer items;
    private final ItemStack appearance = new ItemStack(Items.ECHO_SHARD);

    EnergyOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        items = context.getItemRenderer();
    }

    @Override public void render(EnergyOrb orb, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = orb.tickCount + partialTick;
        pose.pushPose();
        pose.translate(0, 0.35 + 0.08 * Math.sin(age * 0.12), 0);
        pose.mulPose(Axis.YP.rotationDegrees(age * 4));
        pose.scale(0.65F, 0.65F, 0.65F);
        items.renderStatic(appearance, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                pose, buffers, orb.level(), orb.getId());
        pose.popPose();
        super.render(orb, yaw, partialTick, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(EnergyOrb orb) { return TextureAtlas.LOCATION_BLOCKS; }
}
