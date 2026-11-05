package zone.moddev.mc.skysleylines.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import zone.moddev.mc.skysleylines.SkysLeyLines;
import zone.moddev.mc.skysleylines.item.*;
import zone.moddev.mc.skysleylines.network.DowsingSignals;

@Mod(value = SkysLeyLines.MOD_ID, dist = Dist.CLIENT)
public final class DowsingClient {
    static final ModelResourceLocation ROD = ModelResourceLocation.standalone(ResourceLocation.parse("skysleylines:item/dowsing_rod"));
    static final ModelResourceLocation ICON = ModelResourceLocation.standalone(ResourceLocation.parse("skysleylines:item/dowsing_rods_inventory"));
    private static final Map<UUID, DowsingMotion> MOTIONS = new HashMap<>();
    private static final DowsingMotion STILL = new DowsingMotion();
    private static ClientLevel lastLevel;
    private static PlayerModel<AbstractClientPlayer> wideHands, slimHands;

    public DowsingClient(IEventBus bus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new ConfigurationScreen(mod, parent, LeyConfigSection::new));
        bus.addListener((ModelEvent.RegisterAdditional event) -> { event.register(ROD); event.register(ICON); });
        bus.addListener(DowsingClient::extensions);
        bus.addListener((EntityRenderersEvent.AddLayers event) -> {
            wideHands = slimHands = null; // Also invalidated on resource/model reload.
            for (var skin : event.getSkins()) {
                PlayerRenderer renderer = event.getSkin(skin);
                if (renderer != null) renderer.addLayer(new DowsingPlayerLayer(renderer));
            }
        });
        NeoForge.EVENT_BUS.addListener(DowsingClient::tick);
        NeoForge.EVENT_BUS.addListener(DowsingClient::hand);
    }

    private static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private DowsingItemRenderer renderer;
            @Override public DowsingItemRenderer getCustomRenderer() {
                if (renderer == null) renderer = new DowsingItemRenderer();
                return renderer;
            }
            @Override public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return DowsingHands.paired(entity) && !entity.isUsingItem() ? DowsingArmPose.DOWSING.getValue() : null;
            }
        }, SkysLeyLines.DOWSING_RODS.get());
    }

    private static void tick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level != lastLevel) { MOTIONS.clear(); DowsingSignals.clear(); lastLevel = level; }
        if (level == null) return;
        long now = level.getGameTime();
        DowsingSignals.prune(now);
        MOTIONS.keySet().removeIf(id -> level.getPlayerByUUID(id) == null || !DowsingHands.hasRods(level.getPlayerByUUID(id)));
        for (var player : level.players()) {
            if (DowsingHands.hasRods(player)) MOTIONS.computeIfAbsent(player.getUUID(), id -> new DowsingMotion())
                    .tick(DowsingSignals.read(player.getUUID(), level.dimension().location(), now));
        }
    }

    static float angle(AbstractClientPlayer player, boolean left, float facing, float partial) {
        return MOTIONS.getOrDefault(player.getUUID(), STILL).angle(left, facing, partial);
    }

    private static void hand(RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null || !DowsingHands.hasRods(player) || player.isSpectator() || player.isUsingItem()) return;
        boolean paired = DowsingHands.paired(player);
        if (!paired && !(event.getItemStack().getItem() instanceof DowsingRods)) return;
        event.setCanceled(true);
        if (paired && event.getHand() != DowsingHands.source(player)) return;
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0, -event.getEquipProgress() * 0.6, -Math.sin(event.getSwingProgress() * Math.PI) * 0.06);
        for (var arm : HumanoidArm.values()) {
            if (!paired && arm != (event.getHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite())) continue;
            boolean left = arm == HumanoidArm.LEFT;
            pose.pushPose();
            pose.translate(left ? -0.30 : 0.30, -0.30 + (left ? 0.012 : 0), -0.58);
            if (!player.isInvisible()) renderHand(player, left, pose, event.getMultiBufferSource(), event.getPackedLight());
            pose.mulPose(Axis.YP.rotationDegrees(-angle(player, left, player.getViewYRot(event.getPartialTick()), event.getPartialTick())));
            renderRod(pose, event.getMultiBufferSource(), event.getPackedLight());
            pose.popPose();
        }
        pose.popPose();
    }

    private static void renderHand(AbstractClientPlayer player, boolean left, PoseStack pose, MultiBufferSource buffers, int light) {
        var mc = Minecraft.getInstance();
        boolean slim = player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        if (slim ? slimHands == null : wideHands == null) {
            var model = new PlayerModel<AbstractClientPlayer>(mc.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
            if (slim) slimHands = model; else wideHands = model;
        }
        var model = slim ? slimHands : wideHands;
        var arm = left ? model.leftArm : model.rightArm;
        var sleeve = left ? model.leftSleeve : model.rightSleeve;
        arm.setPos(0, 0, 0); arm.setRotation(0, 0, 0);
        sleeve.copyFrom(arm);
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(-65));
        pose.translate(left ? -0.0625 : 0.0625, -0.625, 0);
        var texture = player.getSkin().texture();
        arm.render(pose, buffers.getBuffer(RenderType.entitySolid(texture)), light, OverlayTexture.NO_OVERLAY);
        if (player.isModelPartShown(left ? net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE : net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE)) {
            sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(texture)), light, OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
    }

    static void renderRod(PoseStack pose, MultiBufferSource buffers, int light) {
        var mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(0, 0.094, 0);
        mc.getItemRenderer().render(new ItemStack(SkysLeyLines.DOWSING_RODS.get()), net.minecraft.world.item.ItemDisplayContext.NONE,
                false, pose, buffers, light, OverlayTexture.NO_OVERLAY, mc.getModelManager().getModel(ROD));
        pose.popPose();
    }
}
