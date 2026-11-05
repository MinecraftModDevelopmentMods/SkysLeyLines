package zone.moddev.mc.skysleylines.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.Test;

class LeyCommandTextTest {
    @Test void commandHasAnEnglishFallbackAndRendersWithTheClientLocale() {
        var message = LeyCommandText.command("recover.success", 9_007_199_254_740_993L, "pack:aether", "3/2", "node");
        assertEquals("Recovered 9007199254740993 pack:aether as 3/2 raw at nexus node", message.getString());

        var originalLanguage = Language.getInstance();
        var location = new PackLocationInfo("test", Component.literal("test"), PackSource.DEFAULT, Optional.empty());
        var pack = new PathPackResources(location, Path.of("src/main/resources"));
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack))) {
            Language.inject(ClientLanguage.loadFrom(resources, List.of("en_us", "ja_jp"), false));
            assertEquals("node のネクサスでpack:aetherを9007199254740993単位回収し、未変換魔力3/2に戻しました。", message.getString());
            assertEquals("ダウジングロッド", Component.translatable("item.skysleylines.dowsing_rods").getString());
        } finally {
            Language.inject(originalLanguage);
        }
    }
}
