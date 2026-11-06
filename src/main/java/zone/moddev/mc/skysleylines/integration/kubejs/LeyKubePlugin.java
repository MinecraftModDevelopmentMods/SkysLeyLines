package zone.moddev.mc.skysleylines.integration.kubejs;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import net.neoforged.neoforge.common.NeoForge;
import dev.latvian.mods.kubejs.script.BindingRegistry;

/** Loaded by KubeJS when present. The ordinary mod entry point does not link to this class. */
public final class LeyKubePlugin implements KubeJSPlugin {
    @Override
    public void init() {
        NeoForge.EVENT_BUS.addListener(LeyKubeEvents::changed);
        NeoForge.EVENT_BUS.addListener(LeyKubeEvents::finished);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(LeyKubeEvents.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        switch (bindings.type()) {
            case STARTUP -> bindings.add("LeyLines", new PackCurrencies());
            case SERVER -> bindings.add("LeyLines", new LeyServerScripts());
            default -> { }
        }
    }
}
