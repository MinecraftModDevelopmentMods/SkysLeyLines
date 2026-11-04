package zone.moddev.mc.skysleylines.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;
import net.minecraft.world.level.Level;
import zone.moddev.mc.skysleylines.api.*;

/** Ordinary server-side item interaction; only translated local hints are sent to the player. */
public final class DowsingRods extends Item {
    public DowsingRods(Properties properties) { super(properties); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skysleylines.dowsing_hands").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skysleylines.dowsing_motion").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skysleylines.dowsing_reading").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server && !player.getCooldowns().isOnCooldown(this)) {
            var context = LeyLinesApi.forLevel(server);
            var pos = player.blockPosition();
            LeySample sample = context.field().sample(pos.getX(), pos.getY(), pos.getZ());
            Component feedback;
            if (!sample.active()) feedback = Component.translatable("message.skysleylines.inactive");
            else if (sample.canDrawPower()) {
                var node = context.power().quoteAt(pos.getX(), pos.getY(), pos.getZ()).orElseThrow();
                double fraction = node.account().total().doubleValue() / node.capacity();
                String reserve = fraction == 0 ? "empty" : fraction < 0.25 ? "low" : fraction < 0.75 ? "recovering" : "full";
                feedback = Component.translatable("message.skysleylines.nexus", strength(sample.naturalStrength()),
                        Component.translatable("reserve.skysleylines." + reserve));
            } else {
                var nexus = context.field().nearestNexus(pos.getX(), pos.getZ(), 512);
                var line = context.field().nearestLine(pos.getX(), pos.getZ(), 256);
                if (nexus.isPresent()) {
                    Nexus n = nexus.get();
                    feedback = Component.translatable("message.skysleylines.nexus_direction", direction(n.x() - player.getX(), n.z() - player.getZ()), strength(sample.naturalStrength()));
                } else if (line.isPresent()) {
                    LineReading l = line.get();
                    feedback = l.distance() < context.profile().lineWidth()
                            ? Component.translatable("message.skysleylines.line_heading", direction(l.headingX(), l.headingZ()), direction(-l.headingX(), -l.headingZ()), strength(sample.naturalStrength()))
                            : Component.translatable("message.skysleylines.line_direction", direction(l.x() - player.getX(), l.z() - player.getZ()), strength(sample.naturalStrength()));
                } else feedback = Component.translatable("message.skysleylines.quiet");
            }
            player.displayClientMessage(feedback, true);
            player.getCooldowns().addCooldown(this, 20);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    public static Component strength(double value) {
        return Component.translatable("strength.skysleylines." + (value < 0.05 ? "faint" : value < 0.3 ? "weak" : value < 0.65 ? "moderate" : "strong"));
    }
    public static Component direction(double dx, double dz) {
        int index = Math.floorMod((int) StrictMath.round(StrictMath.atan2(dx, -dz) * 4 / StrictMath.PI), 8);
        return Component.translatable("direction.skysleylines." + new String[]{"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"}[index]);
    }
}
