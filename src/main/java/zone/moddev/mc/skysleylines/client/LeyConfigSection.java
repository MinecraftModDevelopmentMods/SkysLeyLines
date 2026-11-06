package zone.moddev.mc.skysleylines.client;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;
import net.neoforged.neoforge.common.ModConfigSpec.ValueSpec;
import zone.moddev.mc.skysleylines.LeyConfig;

/** Adds the Ley Lines controls that need more than a generic text or number box. */
final class LeyConfigSection extends ConfigurationSectionScreen {
    private EditBox rechargeField;
    private EditBox capacityField;

    LeyConfigSection(Screen parent, ModConfig.Type type, ModConfig config, Component title) {
        super(parent, type, config, title);
    }

    @Override
    protected Element createStringValue(String key, Predicate<String> validator, Supplier<String> source, Consumer<String> target) {
        if (!LeyConfig.VISION_STYLE_KEY.equals(key)) return super.createStringValue(key, validator, source, target);
        Consumer<String> apply = value -> {
            target.accept(value);
            onChanged(key);
        };
        var picker = new OptionInstance<>(getTranslationKey(key), getTooltip(key, null),
                (caption, style) -> Component.translatable("config.skysleylines.style." + style),
                new Custom<>(LeyConfig.VISION_STYLES), source.get(),
                style -> undoManager.add(apply, style, apply, source.get()));
        return new Element(getTranslationComponent(key), getTooltipComponent(key, null), picker);
    }

    @Override
    protected Element createIntegerValue(String key, ValueSpec spec, Supplier<Integer> source, Consumer<Integer> target) {
        if (!LeyConfig.RECHARGE_OVERRIDE_KEY.equals(key)) return super.createIntegerValue(key, spec, source, target);
        var element = createNumberBox(key, spec, source, target, LeyConfig::validRechargeOverride, Integer::parseInt, 0);
        rechargeField = (EditBox) element.widget();
        return element;
    }

    @Override
    protected Element createDoubleValue(String key, ValueSpec spec, Supplier<Double> source, Consumer<Double> target) {
        if (!LeyConfig.CAPACITY_MULTIPLIER_KEY.equals(key)) return super.createDoubleValue(key, spec, source, target);
        var element = createNumberBox(key, spec, LeyConfig::configuredCapacityMultiplier, target,
                LeyConfig::validCapacityMultiplier, Double::parseDouble, 0.0);
        capacityField = (EditBox) element.widget();
        return element;
    }

    @Override
    public void onClose() {
        if (rechargeField != null && !hasValidRechargeInput()) {
            setFocused(rechargeField);
            return;
        }
        if (capacityField != null && !hasValidCapacityInput()) {
            setFocused(capacityField);
            return;
        }
        super.onClose();
    }

    private boolean hasValidRechargeInput() {
        try {
            return LeyConfig.validRechargeOverride(Integer.parseInt(rechargeField.getValue()));
        } catch (NumberFormatException invalidInput) {
            return false;
        }
    }

    private boolean hasValidCapacityInput() {
        try {
            return LeyConfig.validCapacityMultiplier(Double.parseDouble(capacityField.getValue()));
        } catch (NumberFormatException invalidInput) {
            return false;
        }
    }
}
