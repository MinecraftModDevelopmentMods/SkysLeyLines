package zone.moddev.mc.skysleylines;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ResourceTest {
    private static final Path LANG = Path.of("src/main/resources/assets/skysleylines/lang");
    private static final Pattern ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?s");
    @Test void allJsonParsesAndRodsAreTheOnlyGameplayRegistration() throws Exception {
        try(var files=Files.walk(Path.of("src/main/resources"))) {
            for(Path path:files.filter(p->p.toString().endsWith(".json")||p.toString().endsWith(".mcmeta")).toList()) {
                assertTrue(JsonParser.parseString(Files.readString(path)).isJsonObject(),path.toString());
            }
        }
        var recipe=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/skysleylines/recipe/dowsing_rods.json"))).getAsJsonObject();
        assertEquals("skysleylines:dowsing_rods",recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(4,recipe.getAsJsonArray("ingredients").size());
        assertFalse(Files.exists(Path.of("src/main/resources/data/skysleylines/worldgen")));
    }
    @Test void allLiteralTranslationKeysAndDirectionsExist() throws Exception {
        var lang=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/skysleylines/lang/en_us.json"))).getAsJsonObject();
        for(String key:List.of("inactive","quiet","nexus","nexus_direction","line_direction","line_heading")) assertTrue(lang.has("message.skysleylines."+key));
        for(String key:List.of("faint","weak","moderate","strong")) assertTrue(lang.has("strength.skysleylines."+key));
        for(String key:List.of("empty","low","recovering","full")) assertTrue(lang.has("reserve.skysleylines."+key));
        for(String key:List.of("north","north_east","east","south_east","south","south_west","west","north_west")) assertTrue(lang.has("direction.skysleylines."+key));
        assertTrue(lang.has("command.skysleylines.vision.on"));
        assertTrue(lang.has("command.skysleylines.vision.off"));
    }

    @Test void baseMetalsLocaleSetHasMatchingTranslationKeysAndArguments() throws Exception {
        var locales = Set.of("de_de", "en_au", "en_ca", "en_en", "en_gb", "en_pt", "en_us", "es_es", "es_mx",
                "fr_ca", "fr_fr", "it_it", "ja_jp", "nl_nl", "pt_br", "ru_ru", "zh_cn", "zh_tw");
        var english = readLanguage(LANG.resolve("en_us.json"));
        try (var paths = Files.list(LANG)) {
            var files = paths.filter(path -> path.toString().endsWith(".json")).toList();
            assertEquals(locales, Set.copyOf(files.stream()
                    .map(path -> path.getFileName().toString().replace(".json", "")).toList()));
            for (var path : files) {
                var translations = readLanguage(path);
                assertEquals(english.keySet(), translations.keySet(), path.toString());
                for (var key : english.keySet()) {
                    var label = path.getFileName() + " " + key;
                    var value = translations.get(key).getAsString();
                    assertFalse(value.isBlank(), label);
                    assertFalse(ARGUMENT.matcher(value).replaceAll("").contains("%"), label);
                    assertEquals(arguments(english.get(key).getAsString()), arguments(value), label);
                }
            }
        }
    }

    private static JsonObject readLanguage(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static List<Integer> arguments(String template) {
        var matcher = ARGUMENT.matcher(template);
        var positions = new ArrayList<Integer>();
        int next = 1;
        while (matcher.find()) positions.add(matcher.group(1) == null ? next++ : Integer.parseInt(matcher.group(1)));
        return positions.stream().sorted().toList();
    }
}
