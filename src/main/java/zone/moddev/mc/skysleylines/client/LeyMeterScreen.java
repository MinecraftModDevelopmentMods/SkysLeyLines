package zone.moddev.mc.skysleylines.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import zone.moddev.mc.skysleylines.SkysLeyLines;
import zone.moddev.mc.skysleylines.network.LeyMeterPayload;
import zone.moddev.mc.skysleylines.network.LeyMeterSignals;
import zone.moddev.mc.skysleylines.network.SpectatorMeterRequest;

@EventBusSubscriber(modid = SkysLeyLines.MOD_ID, value = Dist.CLIENT)
public final class LeyMeterScreen extends Screen {
    private static final int LINE_HEIGHT = 12;
    private static final int TEXT = 0xe9f1f5;
    private static final int ACCENT = 0x78eadb;
    private final Player viewer;
    private final LeyMeterPayload reading;
    private final List<Line> lines = new ArrayList<>();
    private int left, panelWidth, top, bottom, scroll;

    private record Line(FormattedCharSequence text, int colour) {}

    private LeyMeterScreen(Player viewer, LeyMeterPayload reading) {
        super(Component.translatable("item.skysleylines.ley_meter"));
        this.viewer = viewer;
        this.reading = reading;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        var reading = LeyMeterSignals.take(client.player);
        if (reading != null) client.setScreen(new LeyMeterScreen(client.player, reading));
        else if (client.screen instanceof LeyMeterScreen screen && (screen.viewer != client.player
                || client.level == null || !client.level.dimension().location().equals(screen.reading.dimension()))) {
            client.setScreen(null);
        }
    }

    @SubscribeEvent
    public static void spectatorUse(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft client = Minecraft.getInstance();
        var player = client.player;
        if (!event.isUseItem() || player == null || !player.isSpectator()
                || !player.getItemInHand(event.getHand()).is(SkysLeyLines.LEY_METER.get())
                || (client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        PacketDistributor.sendToServer(new SpectatorMeterRequest(event.getHand()));
    }

    @Override protected void init() {
        panelWidth = Math.min(340, width - 16);
        left = (width - panelWidth) / 2;
        top = Math.max(36, (height - 252) / 2);
        bottom = Math.min(height - 32, top + 236);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - 45, bottom + 6, 90, 20).build());
        lines.clear();
        var pos = reading.position();
        add("location", ACCENT, reading.dimension().toString(), pos.getX(), pos.getY(), pos.getZ());
        if (!reading.active()) add("inactive", ACCENT);
        add("field", TEXT, decimal(reading.naturalStrength()), decimal(reading.lineStrength()), decimal(reading.nexusStrength()));
        add("vertical", TEXT, decimal(reading.verticalFactor()), decimal(reading.dimensionalResistance()));
        add(null, TEXT);
        var reserve = reading.reserve();
        if (reserve == null) {
            if (reading.active()) add("no_nexus", ACCENT);
        } else {
            add("nexus", ACCENT, decimal(reserve.strength()));
            wrap(Component.literal(reserve.id()), TEXT);
            add("raw", TEXT, reserve.raw());
            add("charge", TEXT, reserve.total(), reserve.capacity());
            add("recharge", TEXT, reserve.rechargeTicks());
            add(null, TEXT);
            add("currencies", ACCENT);
            if (reserve.currencies().isEmpty()) add("no_currencies", TEXT);
            for (var currency : reserve.currencies()) {
                wrap(Component.literal(currency.id()), currency.installed() ? TEXT : 0xe4bf89);
                add(currency.installed() ? "currency" : "currency_inactive", TEXT, currency.balance(), currency.rate());
            }
            if (reserve.omitted() > 0) add("more", ACCENT, reserve.omitted());
        }
        scroll = Math.clamp(scroll, 0, maxScroll());
    }

    private void add(String key, int colour, Object... args) {
        wrap(key == null ? Component.literal(" ") : Component.translatable("meter.skysleylines." + key, args), colour);
    }

    private void wrap(Component text, int colour) {
        for (var part : font.split(text, panelWidth - 24)) lines.add(new Line(part, colour));
    }

    private static String decimal(double value) { return String.format(Locale.ROOT, "%.4f", value); }
    private int maxScroll() { return Math.max(0, lines.size() * LINE_HEIGHT - (bottom - top - 10)); }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll = Math.clamp(scroll - (int) (vertical * LINE_HEIGHT * 3), 0, maxScroll());
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        int distance = switch (key) {
            case InputConstants.KEY_UP -> -LINE_HEIGHT;
            case InputConstants.KEY_DOWN -> LINE_HEIGHT;
            case InputConstants.KEY_PAGEUP -> top - bottom;
            case InputConstants.KEY_PAGEDOWN -> bottom - top;
            default -> 0;
        };
        if (distance == 0) return super.keyPressed(key, scan, modifiers);
        scroll = Math.clamp(scroll + distance, 0, maxScroll());
        return true;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left, top - 23, left + panelWidth, bottom, 0xf016232c);
        graphics.fill(left, top - 1, left + panelWidth, top, 0xff3d847f);
        graphics.drawCenteredString(font, title, width / 2, top - 16, ACCENT);
        graphics.enableScissor(left + 6, top + 4, left + panelWidth - 8, bottom - 4);
        int first = scroll / LINE_HEIGHT;
        int y = top + 5 - scroll % LINE_HEIGHT;
        for (int index = first; index < lines.size() && y < bottom; index++, y += LINE_HEIGHT) {
            var line = lines.get(index);
            graphics.drawString(font, line.text(), left + 10, y, line.colour(), false);
        }
        graphics.disableScissor();
        if (maxScroll() > 0) {
            int range = Math.max(1, bottom - top - 22);
            int thumb = top + 5 + range * scroll / maxScroll();
            graphics.fill(left + panelWidth - 6, thumb, left + panelWidth - 4, thumb + 12, 0xff78eadb);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
