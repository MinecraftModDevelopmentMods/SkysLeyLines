package zone.moddev.mc.skysleylines.server;

import java.util.*;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import zone.moddev.mc.skysleylines.api.*;
import static zone.moddev.mc.skysleylines.server.LeyCommandText.command;

/** Operator diagnostics read the field and reserves without loading chunks or consuming power. */
public final class LeyCommands {
    private static final ResourceLocation RECOVERY_CONSUMER = ResourceLocation.parse("skysleylines:operator_recovery");
    private static final Map<MinecraftServer, Map<String, RateMigration>> PREVIEWS = new IdentityHashMap<>();
    private LeyCommands() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skysleylines").requires(s -> s.hasPermission(2))
                .then(Commands.literal("sample").executes(c -> sample(c.getSource(), BlockPos.containing(c.getSource().getPosition())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> sample(c.getSource(), BlockPosArgument.getBlockPos(c, "pos")))))
                .then(Commands.literal("nearest").executes(c -> nearest(c.getSource())))
                .then(Commands.literal("locate")
                        .then(Commands.literal("nexus").executes(c -> nearest(c.getSource()))))
                .then(Commands.literal("account").executes(c -> account(c.getSource(), BlockPos.containing(c.getSource().getPosition())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> account(c.getSource(), BlockPosArgument.getBlockPos(c, "pos"))))
                        .then(Commands.literal("recover").requires(s -> s.hasPermission(4))
                                .then(Commands.argument("currency", ResourceLocationArgument.id())
                                        .then(Commands.argument("units", LongArgumentType.longArg(1))
                                                .executes(c -> recover(c.getSource(), ResourceLocationArgument.getId(c, "currency"),
                                                        LongArgumentType.getLong(c, "units"), BlockPos.containing(c.getSource().getPosition())))
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                        .executes(c -> recover(c.getSource(), ResourceLocationArgument.getId(c, "currency"),
                                                                LongArgumentType.getLong(c, "units"), BlockPosArgument.getBlockPos(c, "pos"))))))))
                .then(Commands.literal("vision").executes(c -> vision(c.getSource(), null))
                        .then(Commands.literal("on").executes(c -> vision(c.getSource(), true)))
                        .then(Commands.literal("off").executes(c -> vision(c.getSource(), false))))
                .then(Commands.literal("rates").executes(c -> rates(c.getSource()))
                        .then(Commands.literal("preview").requires(s -> s.hasPermission(4)).executes(c -> preview(c.getSource())))
                        .then(Commands.literal("apply").requires(s -> s.hasPermission(4))
                                .then(Commands.argument("revision", LongArgumentType.longArg(0)).executes(c -> apply(c.getSource(), LongArgumentType.getLong(c, "revision"))))))
                .then(Commands.literal("profile").executes(c -> {
                    var p = LeyLinesApi.forLevel(c.getSource().getLevel()).profile();
                    int effective = LeyServerContext.world(c.getSource().getServer()).ledger().rechargeTicks();
                    message(c.getSource(), "profile", LeyProfile.LAYOUT_VERSION, p, effective);
                    return 1;
                })));
    }
    private static int vision(CommandSourceStack source, Boolean enabled) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        boolean show = enabled != null ? enabled : !VisionUpdates.operatorEnabled(player);
        VisionUpdates.operator(player, show);
        message(source, show ? "vision.on" : "vision.off");
        return 1;
    }
    private static int sample(CommandSourceStack source, BlockPos pos) {
        var context = LeyLinesApi.forLevel(source.getLevel());
        var value = context.field().sample(pos.getX(), pos.getY(), pos.getZ());
        var reserve = context.power().quoteAt(pos.getX(), pos.getY(), pos.getZ());
        var charge = reserve.<Component>map(n -> Component.literal(n.account().total() + "/" + n.capacity()))
                .orElseGet(() -> command("sample.unavailable"));
        message(source, "sample", pos.toShortString(), value.active(), decimal(value.naturalStrength()),
                decimal(value.lineStrength()), decimal(value.nexusStrength()), decimal(value.verticalFactor()),
                decimal(value.dimensionalResistance()), charge);
        return (int) Math.round(value.naturalStrength() * 1000);
    }
    private static int nearest(CommandSourceStack source) {
        if (!source.getLevel().dimension().equals(Level.OVERWORLD)) {
            message(source, "locate.overworld");
            return 0;
        }
        var context = LeyLinesApi.forLevel(source.getLevel());
        BlockPos pos = BlockPos.containing(source.getPosition());
        int radius = Math.min(4096, context.profile().spacingBlocks() * 2);
        var found = context.field().nearestNexus(pos.getX(), pos.getZ(), radius);
        if (found.isEmpty()) {
            message(source, "locate.none", radius);
            return 0;
        }
        Nexus node = found.get();
        int x = (int) Math.floor(node.x()), z = (int) Math.floor(node.z());
        LeyColumn column = context.field().sampleColumn(x, z);
        String location = column.active()
                ? String.format(Locale.ROOT, "%.2f %d %.2f", node.x(), column.naturalSurfaceY(), node.z())
                : String.format(Locale.ROOT, "%.2f %.2f", node.x(), node.z());
        var height = command(column.active() ? "locate.ground" : "locate.no_ground");
        long distance = Math.round(Math.hypot(node.x() - source.getPosition().x, node.z() - source.getPosition().z));
        var coordinates = Component.literal(location).withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, location))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, command("locate.copy"))));
        message(source, "locate.found", coordinates, height, distance, decimal(node.strength()), node.id());
        return 1;
    }
    public static void clear(MinecraftServer server) { PREVIEWS.remove(server); }
    private static String decimal(double value) { return String.format(Locale.ROOT, "%.4f", value); }
    private static void message(CommandSourceStack source, String key, Object... arguments) {
        source.sendSuccess(() -> command(key, arguments), false);
    }
    private static int account(CommandSourceStack source, BlockPos p) {
        var power = LeyLinesApi.forLevel(source.getLevel()).power();
        var node = power.quoteAt(p.getX(), p.getY(), p.getZ());
        if (node.isEmpty()) { message(source, "account.none"); return 0; }
        var a = node.get().account();
        message(source, "account", node.get().nexus().id(), a.raw(), a.total(), a.capacity(), node.get().rechargeTicks());
        LeyLinesApi.currencyInfo(source.getLevel()).forEach((id, info) -> message(source,
                info.active() ? "account.currency" : "account.currency_inactive", id, a.currency(id), info.unitsPerRaw()));
        return 1;
    }
    private static int recover(CommandSourceStack source, ResourceLocation currency, long units, BlockPos pos) {
        if (!source.hasPermission(4)) return 0;
        var level = source.getLevel();
        var info = LeyLinesApi.currencyInfo(level).get(currency);
        if (info == null || info.active()) {
            source.sendFailure(command("recover.inactive_required", currency));
            return 0;
        }
        var actor = Optional.ofNullable(source.getPlayer()).map(player -> player.getUUID());
        var power = LeyLinesApi.forLevel(level).power();
        var result = power.returnToRawAt(pos.getX(), pos.getY(), pos.getZ(), currency, units,
                new PowerRequest.Consumer(RECOVERY_CONSUMER, actor));
        if (!result.succeeded()) {
            source.sendFailure(command("recover.failed", result.status()));
            return 0;
        }
        var receipt = result.receipt().orElseThrow();
        var raw = receipt.after().raw().subtract(receipt.before().raw());
        source.sendSuccess(() -> command("recover.success", units, currency, raw, receipt.nexus()), true);
        return 1;
    }
    private static int rates(CommandSourceStack source) {
        var currencies = LeyLinesApi.currencyInfo(source.getLevel());
        currencies.forEach((id, info) -> message(source, "rates.entry", id, info.unitsPerRaw(), info.active()));
        if (currencies.isEmpty()) message(source, "rates.none");
        return 1;
    }
    private static int preview(CommandSourceStack source) {
        try {
            var plan = LeyServerContext.world(source.getServer()).previewMigration();
            PREVIEWS.computeIfAbsent(source.getServer(), s -> new HashMap<>()).put(source.getTextName(), plan);
            message(source, "rates.preview", plan.revision(), plan.accounts().size());
            plan.newRates().forEach((id, rate) -> message(source, "rates.change", id, plan.oldRates().get(id), rate));
            plan.accounts().entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(10).forEach(e ->
                    message(source, "rates.account", e.getKey(), e.getValue().raw(), e.getValue().currencies(), e.getValue().total()));
            message(source, "rates.instructions", plan.revision());
            return 1;
        } catch (RuntimeException failure) { source.sendFailure(command("rates.preview_failed", failure.getMessage())); return 0; }
    }
    private static int apply(CommandSourceStack source, long revision) {
        var pending = PREVIEWS.getOrDefault(source.getServer(), Map.of());
        var plan = pending.get(source.getTextName());
        if (plan == null || plan.revision() != revision) { source.sendFailure(command("rates.missing_preview")); return 0; }
        try {
            boolean committed = LeyServerContext.world(source.getServer()).applyMigration(plan);
            PREVIEWS.get(source.getServer()).remove(source.getTextName());
            message(source, committed ? "rates.applied" : "rates.stale");
            return committed ? 1 : 0;
        } catch (RuntimeException failure) { source.sendFailure(command("rates.failed", failure.getMessage())); return 0; }
    }
}
