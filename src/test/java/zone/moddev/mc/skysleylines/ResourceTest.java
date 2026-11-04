package zone.moddev.mc.skysleylines;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ResourceTest {
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
        for (String locale : List.of("en_us", "en_gb")) {
            var strings = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/skysleylines/lang/" + locale + ".json"))).getAsJsonObject();
            assertTrue(strings.has("command.skysleylines.vision.on"));
            assertTrue(strings.has("command.skysleylines.vision.off"));
        }
    }
}
