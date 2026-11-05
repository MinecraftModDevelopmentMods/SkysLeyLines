package zone.moddev.mc.skysleylines.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import zone.moddev.mc.skysleylines.server.LeyInspection;

public final class LeyMeter extends Item {
    public LeyMeter(Properties properties) { super(properties); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.skysleylines.ley_meter").withStyle(ChatFormatting.GRAY));
    }

    // Inspect before vanilla opens a block menu or checks adventure-mode placement permissions.
    public static void onBlockUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof LeyMeter meter) || event.getUseItem() == TriState.FALSE) return;
        if (event.getEntity() instanceof ServerPlayer player) meter.inspect(player, event.getPos());
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        event.setCanceled(true);
    }

    public static void spectatorUse(ServerPlayer player, InteractionHand hand) {
        if (player.isSpectator() && player.getItemInHand(hand).getItem() instanceof LeyMeter meter)
            meter.inspect(player, player.blockPosition());
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) inspect(server, player.blockPosition());
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    private void inspect(ServerPlayer player, BlockPos pos) {
        if (player.getCooldowns().isOnCooldown(this)) return;
        player.getCooldowns().addCooldown(this, 10);
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("meter.skysleylines.denied"), true);
            return;
        }
        PacketDistributor.sendToPlayer(player, LeyInspection.snapshot(player.serverLevel(), pos));
    }
}
