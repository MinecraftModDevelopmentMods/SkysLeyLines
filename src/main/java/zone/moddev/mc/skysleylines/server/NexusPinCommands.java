package zone.moddev.mc.skysleylines.server;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import zone.moddev.mc.skysleylines.api.*;
import zone.moddev.mc.skysleylines.core.LeyNetwork;
import zone.moddev.mc.skysleylines.core.NexusLedger;
import static zone.moddev.mc.skysleylines.server.NexusPins.State.*;

final class NexusPinCommands {
    private static final ResourceLocation RECOVERY = ResourceLocation.parse("skysleylines:pin_recovery");
    private NexusPinCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("nexus").then(Commands.literal("pin").requires(source -> source.hasPermission(4))
                .then(place("preview", false)).then(place("add", true))
                .then(Commands.literal("list").executes(NexusPinCommands::list))
                .then(Commands.literal("remove").then(Commands.argument("id", ResourceLocationArgument.id())
                        .executes(NexusPinCommands::remove)))
                .then(Commands.literal("recover").then(Commands.argument("id", ResourceLocationArgument.id())
                        .then(Commands.argument("currency", ResourceLocationArgument.id())
                                .then(Commands.argument("units", LongArgumentType.longArg(1))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(NexusPinCommands::recover)))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> place(String verb, boolean commit) {
        return Commands.literal(verb).then(Commands.argument("id", ResourceLocationArgument.id())
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("strength", DoubleArgumentType.doubleArg(Double.MIN_VALUE, 1))
                                .executes(context -> place(context, commit)))));
    }

    private static ResourceLocation id(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        boolean explicit = context.getNodes().stream().anyMatch(node -> node.getNode().getName().equals("id")
                && node.getRange().get(context.getInput()).contains(":"));
        if (!explicit) throw new IllegalArgumentException("Use a namespaced pin ID such as pack:dragon_roost");
        return ResourceLocationArgument.getId(context, "id");
    }

    private static int place(CommandContext<CommandSourceStack> context, boolean commit) throws CommandSyntaxException {
        var source = context.getSource();
        var level = source.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) return fail(source, "Nexus pins belong to the Overworld");
        try {
            var pos = BlockPosArgument.getBlockPos(context, "pos");
            var pin = new NexusPins.Pin(id(context), pos, DoubleArgumentType.getDouble(context, "strength"), STAGED_ADD);
            var data = LeyServerContext.world(source.getServer());
            data.pins.checkAdd(pin);
            var field = (LeyNetwork) LeyLinesApi.forLevel(level).field();
            if (level.isOutsideBuildHeight(pos) || !field.sampleColumn(pos.getX(), pos.getZ()).active())
                return fail(source, "Choose a position within build bounds over natural terrain");
            var links = field.connections(pin.nexus());
            var overlaps = new java.util.TreeSet<NexusId>();
            field.overlappingNexuses(pin.nexus()).forEach(nexus -> overlaps.add(nexus.id()));
            double radius = 2.0 * data.profile().nexusRadius();
            for (var other : data.pins.list()) {
                if (other.state() == STAGED_ADD && other.nexus().distanceSquared(pos.getX(), pos.getZ()) < radius * radius)
                    overlaps.add(other.nexus().id());
            }
            if (commit) data.stagePin(pin);
            tell(source, (commit ? "Staged for next restart: " : "Preview: ") + pin.id()
                    + " at " + pos.toShortString() + ", strength " + pin.strength()
                    + ", capacity " + data.ledger().scaledCapacity(pin.nexus().capacity(data.profile())));
            if (!overlaps.isEmpty()) tell(source, "Overlapping fields: " + overlaps);
            if (links.isEmpty()) tell(source, "Isolated pin. No seeded line joins within " + LeyNetwork.CONNECTOR_REACH + " blocks.");
            for (var link : links) tell(source, String.format(Locale.ROOT, "Join at %.2f %d %.2f", link.x(), link.y(), link.z()));
            return 1;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return fail(source, failure.getMessage());
        }
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        var data = LeyServerContext.world(source.getServer());
        var pins = data.pins.list();
        if (pins.isEmpty()) tell(source, "No nexus pins");
        var balances = data.ledger().entries();
        for (var pin : pins) {
            var balance = balances.get(pin.nexus().id());
            tell(source, pin.id() + " " + pin.state() + " at " + pin.position().toShortString()
                    + (balance == null || balance.currencies().isEmpty() ? "" : " holds " + balance.currencies()));
        }
        return pins.size();
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        try {
            var data = LeyServerContext.world(source.getServer());
            var pin = data.pins.get(id(context));
            if (pin == null) return fail(source, "Unknown nexus pin");
            if (pin.state() == RETIRED || pin.state() == STAGED_REMOVE) return fail(source, "Pin is already retired or staged for removal");
            boolean pending = pin.state() == STAGED_ADD;
            data.stagePin(pin.state(pending ? RETIRED : STAGED_REMOVE));
            tell(source, pending ? "Cancelled staged pin " + pin.id() : "Staged retirement for next restart: " + pin.id());
            return 1;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return fail(source, failure.getMessage());
        }
    }

    private static int recover(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var level = source.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) return fail(source, "Recovery requires an Overworld destination");
        try {
            var data = LeyServerContext.world(source.getServer());
            var pin = data.pins.get(id(context));
            if (pin == null || pin.state() != RETIRED) return fail(source, "Recover from a retired pin");
            var pos = BlockPosArgument.getBlockPos(context, "pos");
            var destination = LeyLinesApi.forLevel(level).power().quoteAt(pos.getX(), pos.getY(), pos.getZ()).orElse(null);
            if (destination == null) return fail(source, "No active nexus at the destination");
            var currency = ResourceLocationArgument.getId(context, "currency");
            long units = LongArgumentType.getLong(context, "units");
            var consumer = new PowerRequest.Consumer(RECOVERY, Optional.ofNullable(source.getEntity()).map(entity -> entity.getUUID()));
            var status = data.ledger().recover(pin.nexus().id(), pin.position(), destination.nexus().id(), pos,
                    destination.nexus().capacity(data.profile()), level.getGameTime(), currency, units, consumer, new NexusLedger.RecoveryHooks() {
                        @Override public boolean before(NexusRecoveryEvent.Receipt receipt) {
                            return !NeoForge.EVENT_BUS.post(new NexusRecoveryEvent.Pre(level, receipt)).isCanceled();
                        }
                        @Override public void after(NexusRecoveryEvent.Receipt receipt) {
                            data.setDirty();
                            NeoForge.EVENT_BUS.post(new NexusRecoveryEvent.Post(level, receipt));
                        }
                        @Override public void failure(RuntimeException failure) {
                            LogUtils.getLogger().error("Pin recovery listener failed", failure);
                        }
                    });
            if (status != NexusLedger.RecoveryStatus.SUCCESS) return fail(source, "Pin recovery failed: " + status);
            tell(source, "Recovered " + units + " " + currency + " from " + pin.id() + " to " + destination.nexus().id());
            return 1;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return fail(source, failure.getMessage());
        }
    }

    private static void tell(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
