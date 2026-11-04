package zone.moddev.mc.skysleylines.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import zone.moddev.mc.skysleylines.item.DowsingHands;
import zone.moddev.mc.skysleylines.item.DowsingRods;

final class DowsingPlayerLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    DowsingPlayerLayer(PlayerRenderer parent) { super(parent); }
    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                 float limbSwing, float limbAmount, float partial, float age, float headYaw, float headPitch) {
        if (!DowsingHands.hasRods(player) || player.isSpectator()) return;
        boolean paired = DowsingHands.paired(player) && !player.isUsingItem();
        for (var arm : HumanoidArm.values()) {
            boolean left = arm == HumanoidArm.LEFT;
            InteractionHand hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            if (!paired && !(player.getItemInHand(hand).getItem() instanceof DowsingRods)) continue;
            var part = left ? getParentModel().leftArm : getParentModel().rightArm;
            pose.pushPose();
            getParentModel().translateToHand(arm, pose);
            pose.translate(left ? -0.0625 : 0.0625, 0.65, 0);
            pose.mulPose(Axis.XP.rotation(-part.xRot));
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            float body = net.minecraft.util.Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
            pose.mulPose(Axis.YP.rotationDegrees(-DowsingClient.angle(player, left, body, partial)));
            DowsingClient.renderRod(pose, buffers, light);
            pose.popPose();
        }
    }
}
