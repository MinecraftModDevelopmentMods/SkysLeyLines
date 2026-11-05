package zone.moddev.mc.skysleylines.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

final class LeyCommandText {
    private static final Map<String, String> ENGLISH = loadEnglish();

    private LeyCommandText() {}

    static Component command(String name, Object... arguments) {
        String key = "command.skysleylines." + name;
        // Keep exact amounts and identifiers as strings through chat serialization.
        for (int index = 0; index < arguments.length; index++) {
            if (!(arguments[index] instanceof Component)) arguments[index] = String.valueOf(arguments[index]);
        }
        return Component.translatableWithFallback(key, ENGLISH.get(key), arguments);
    }

    private static Map<String, String> loadEnglish() {
        var path = "/assets/skysleylines/lang/en_us.json";
        try (var stream = Objects.requireNonNull(LeyCommandText.class.getResourceAsStream(path), path)) {
            var translations = new HashMap<String, String>();
            Language.loadFromJson(stream, translations::put);
            return Map.copyOf(translations);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read Ley Lines command translations", failure);
        }
    }
}
