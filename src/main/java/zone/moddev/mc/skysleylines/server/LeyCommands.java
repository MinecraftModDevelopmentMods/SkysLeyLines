package zone.moddev.mc.skysleylines.server;

import java.util.*;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import zone.moddev.mc.skysleylines.api.*;

/** Operator diagnostics read the field and reserves without loading chunks or consuming power. */
public final class LeyCommands {
    private static final Map<MinecraftServer, Map<String, RateMigration>> PREVIEWS = new IdentityHashMap<>();
    private LeyCommands() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skysleylines").requires(s -> s.hasPermission(2))
                .then(Commands.literal("sample").executes(c -> sample(c.getSource(), BlockPos.containing(c.getSource().getPosition())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> sample(c.getSource(), BlockPosArgument.getBlockPos(c, "pos")))))
                .then(Commands.literal("nearest").executes(c -> nearest(c.getSource())))
                .then(Commands.literal("locate")
                        .then(Commands.literal("nexus").executes(c -> nearest(c.getSource()))))
                .then(Commands.literal("account").executes(c -> account(c.getSource())))
                .then(Commands.literal("vision").executes(c -> vision(c.getSource(), null))
                        .then(Commands.literal("on").executes(c -> vision(c.getSource(), true)))
                        .then(Commands.literal("off").executes(c -> vision(c.getSource(), false))))
                .then(Commands.literal("rates").executes(c -> rates(c.getSource()))
                        .then(Commands.literal("preview").requires(s -> s.hasPermission(4)).executes(c -> preview(c.getSource())))
                        .then(Commands.literal("apply").requires(s -> s.hasPermission(4))
                                .then(Commands.argument("revision", LongArgumentType.longArg(0)).executes(c -> apply(c.getSource(), LongArgumentType.getLong(c, "revision"))))))
                .then(Commands.literal("profile").executes(c -> {
                    var p = LeyLinesApi.forLevel(c.getSource().getLevel()).profile();
                    c.getSource().sendSuccess(() -> Component.literal("Saved ley layout v1, Overworld only: " + p), false);
                    return 1;
                })));
    }
    private static int vision(CommandSourceStack source, Boolean enabled) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        boolean show = enabled != null ? enabled : !VisionUpdates.operatorEnabled(player);
        VisionUpdates.operator(player, show);
        source.sendSuccess(() -> Component.translatable(show ? "command.skysleylines.vision.on" : "command.skysleylines.vision.off"), false);
        return 1;
    }
    private static int sample(CommandSourceStack source, BlockPos pos) {
        var context = LeyLinesApi.forLevel(source.getLevel());
        var value = context.field().sample(pos.getX(), pos.getY(), pos.getZ());
        var reserve = context.power().quoteAt(pos.getX(), pos.getY(), pos.getZ());
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Ley %s: active=%s strength=%.4f line=%.4f nexus=%.4f vertical=%.4f resistance=%.4f reserve=%s",
                pos.toShortString(), value.active(), value.naturalStrength(), value.lineStrength(), value.nexusStrength(),
                value.verticalFactor(), value.dimensionalResistance(), reserve.map(n -> n.account().total() + "/" + n.capacity()).orElse("unavailable"))), false);
        return (int) Math.round(value.naturalStrength() * 1000);
    }
    private static int nearest(CommandSourceStack source) {
        if (!source.getLevel().dimension().equals(Level.OVERWORLD)) {
            message(source, "Ley nexuses can currently be located in the Overworld.");
            return 0;
        }
        var context = LeyLinesApi.forLevel(source.getLevel());
        BlockPos pos = BlockPos.containing(source.getPosition());
        int radius = Math.min(4096, context.profile().spacingBlocks() * 2);
        var found = context.field().nearestNexus(pos.getX(), pos.getZ(), radius);
        if (found.isEmpty()) {
            message(source, "No nexus within " + radius + " blocks of this position.");
            return 0;
        }
        Nexus node = found.get();
        int x = (int) Math.floor(node.x()), z = (int) Math.floor(node.z());
        LeyColumn column = context.field().sampleColumn(x, z);
        String location = column.active()
                ? String.format(Locale.ROOT, "%.2f %d %.2f", node.x(), column.naturalSurfaceY(), node.z())
                : String.format(Locale.ROOT, "%.2f %.2f", node.x(), node.z());
        String height = column.active() ? " (natural ground Y)" : " (X Z, no natural ground)";
        long distance = Math.round(Math.hypot(node.x() - source.getPosition().x, node.z() - source.getPosition().z));
        var coordinates = Component.literal(location).withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, location))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy nexus coordinates"))));
        source.sendSuccess(() -> Component.literal("Nearest nexus at ").append(coordinates)
                .append(height + String.format(Locale.ROOT, " · %d blocks away horizontally · strength %.4f · ID %s",
                        distance, node.strength(), node.id())), false);
        return 1;
    }
    public static void clear(MinecraftServer server) { PREVIEWS.remove(server); }
    private static void message(CommandSourceStack source, String text) { source.sendSuccess(() -> Component.literal(text), false); }
    private static int account(CommandSourceStack source) {
        var p = BlockPos.containing(source.getPosition());
        var power = LeyLinesApi.forLevel(source.getLevel()).power();
        var node = power.quoteAt(p.getX(), p.getY(), p.getZ());
        if (node.isEmpty()) { message(source, "No eligible nexus at this position"); return 0; }
        var a = node.get().account();
        message(source, "Nexus " + node.get().nexus().id() + ": raw=" + a.raw() + ", total=" + a.total() + "/" + a.capacity() + ", full recovery=" + node.get().rechargeTicks() + " ticks");
        power.rates().forEach((id, rate) -> message(source, id + "=" + a.currency(id) + " units; units/raw=" + rate));
        return 1;
    }
    private static int rates(CommandSourceStack source) {
        var ledger = LeyServerContext.world(source.getServer()).ledger();
        ledger.rates().forEach((id, rate) -> message(source, id + ": saved units/raw=" + rate + ", installed=" + ledger.activeCurrencies().contains(id)));
        if (ledger.rates().isEmpty()) message(source, "No currencies registered");
        return 1;
    }
    private static int preview(CommandSourceStack source) {
        try {
            var plan = LeyServerContext.world(source.getServer()).previewMigration();
            PREVIEWS.computeIfAbsent(source.getServer(), s -> new HashMap<>()).put(source.getTextName(), plan);
            message(source, "Rate migration preview revision " + plan.revision() + "; accounts=" + plan.accounts().size());
            plan.newRates().forEach((id, rate) -> message(source, id + ": " + plan.oldRates().get(id) + " -> " + rate + " units/raw"));
            plan.accounts().entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(10).forEach(e -> message(source, e.getKey() + ": raw=" + e.getValue().raw() + ", currencies=" + e.getValue().currencies() + ", total=" + e.getValue().total()));
            message(source, "Whole currency units are re-denominated; exact indivisible value returns to raw. Apply with /skysleylines rates apply " + plan.revision() + ". Recharge continues normally until apply.");
            return 1;
        } catch (RuntimeException failure) { source.sendFailure(Component.literal("Migration preview rejected: " + failure.getMessage())); return 0; }
    }
    private static int apply(CommandSourceStack source, long revision) {
        var pending = PREVIEWS.getOrDefault(source.getServer(), Map.of());
        var plan = pending.get(source.getTextName());
        if (plan == null || plan.revision() != revision) { source.sendFailure(Component.literal("Create a matching migration preview first")); return 0; }
        try {
            boolean committed = LeyServerContext.world(source.getServer()).applyMigration(plan);
            PREVIEWS.get(source.getServer()).remove(source.getTextName());
            message(source, committed ? "Rate migration committed; previous ledger backed up in world/data" : "Migration cancelled or preview stale; create a new preview");
            return committed ? 1 : 0;
        } catch (RuntimeException failure) { source.sendFailure(Component.literal("Migration rejected; no ledger changes: " + failure.getMessage())); return 0; }
    }
}
