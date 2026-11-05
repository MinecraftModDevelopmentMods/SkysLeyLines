package zone.moddev.mc.skysleylines;

import zone.moddev.mc.skysleylines.integration.kubejs.PackCurrencies;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import zone.moddev.mc.skysleylines.api.LeyLinesApi;
import zone.moddev.mc.skysleylines.item.DowsingRods;
import zone.moddev.mc.skysleylines.item.LeyMeter;
import zone.moddev.mc.skysleylines.network.LeyMeterPayload;
import zone.moddev.mc.skysleylines.network.LeyMeterSignals;
import zone.moddev.mc.skysleylines.network.SpectatorMeterRequest;
import zone.moddev.mc.skysleylines.server.LeyCommands;
import zone.moddev.mc.skysleylines.server.LeyServerContext;
import zone.moddev.mc.skysleylines.server.DowsingUpdates;
import zone.moddev.mc.skysleylines.network.DowsingPayload;
import zone.moddev.mc.skysleylines.network.DowsingSignals;
import zone.moddev.mc.skysleylines.network.VisionPayload;
import zone.moddev.mc.skysleylines.network.VisionSignals;
import zone.moddev.mc.skysleylines.server.VisionUpdates;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(SkysLeyLines.MOD_ID)
public final class SkysLeyLines {
    public static final String MOD_ID = "skysleylines";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<DowsingRods> DOWSING_RODS = ITEMS.register("dowsing_rods",
            () -> new DowsingRods(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<LeyMeter> LEY_METER = ITEMS.register("ley_meter",
            () -> new LeyMeter(new Item.Properties().stacksTo(1)));

    public SkysLeyLines(IEventBus bus, ModContainer container) {
        ITEMS.register(bus);
        container.registerConfig(ModConfig.Type.COMMON, LeyConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, LeyConfig.SERVER_SPEC);
        bus.addListener(SkysLeyLines::creativeItems);
        bus.addListener((FMLLoadCompleteEvent event) -> event.enqueueWork(() -> {
            PackCurrencies.completeRegistration();
            LeyLinesApi.paradigms().freeze();
            VisionUpdates.freezeStyles();
        }));
        NeoForge.EVENT_BUS.addListener(LeyCommands::register);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LeyMeter::onBlockUse);
        NeoForge.EVENT_BUS.addListener(SkysLeyLines::serverStarting);
        NeoForge.EVENT_BUS.addListener(SkysLeyLines::serverStopped);
        NeoForge.EVENT_BUS.addListener(DowsingUpdates::tick);
        NeoForge.EVENT_BUS.addListener(VisionUpdates::tick);
        NeoForge.EVENT_BUS.addListener(VisionUpdates::logout);
        NeoForge.EVENT_BUS.addListener(VisionUpdates::respawn);
        bus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("2").playToClient(
                DowsingPayload.TYPE, DowsingPayload.CODEC,
                (payload, context) -> DowsingSignals.receive(payload, context.player().level().getGameTime()))
                .playToClient(VisionPayload.TYPE, VisionPayload.CODEC,
                        (payload, context) -> VisionSignals.receive(context.player().level(), payload))
                .playToClient(LeyMeterPayload.TYPE, LeyMeterPayload.CODEC,
                        (payload, context) -> LeyMeterSignals.receive(context.player(), payload))
                .playToServer(SpectatorMeterRequest.TYPE, SpectatorMeterRequest.CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) LeyMeter.spectatorUse(player, payload.hand());
                }));
    }
    private static void creativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(DOWSING_RODS);
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS && event.hasPermissions()) event.accept(LEY_METER);
    }
    private static void serverStarting(ServerStartingEvent event) { LeyLinesApi.forLevel(event.getServer().overworld()); }
    private static void serverStopped(ServerStoppedEvent event) { LeyServerContext.clear(event.getServer()); }
}
