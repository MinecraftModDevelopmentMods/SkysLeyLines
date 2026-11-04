package zone.moddev.mc.skysleylines.client;

import net.minecraft.client.model.HumanoidModel;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/** Isolated parameter holder: runtime enum extension can initialize before mod construction. */
public final class DowsingArmPose {
    public static final EnumProxy<HumanoidModel.ArmPose> DOWSING = new EnumProxy<>(HumanoidModel.ArmPose.class, true,
            (IArmPoseTransformer) (model, entity, arm) -> {
                model.leftArm.xRot = model.rightArm.xRot = -0.95f;
                model.leftArm.yRot = model.rightArm.yRot = 0;
                model.leftArm.zRot = model.rightArm.zRot = 0;
            });
    private DowsingArmPose() {}
}
