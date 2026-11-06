package zone.moddev.mc.skysleylines.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import zone.moddev.mc.skysleylines.api.MagicAmount;
import zone.moddev.mc.skysleylines.api.PlayerPowerService.DeathMode;
import zone.moddev.mc.skysleylines.api.PlayerPowerService.Settings;
import zone.moddev.mc.skysleylines.core.PlayerEnergyDefaults;

/** Applies sparse overlays in data pack, instance, world order. */
final class PlayerEnergyPolicy {
    private static final String DIRECTORY = "player_energy";
    private static final String FILE = "skysleylines-player-energy.json";
    private static final Set<String> KEYS = Set.of("enabled", "capacity", "autoRate", "instant",
            "transferRateLimit", "conversionRateLimit", "sameDimension", "maxDistance", "death",
            "honorKeepInventory", "ownerOnly", "dropLifetime");

    record Rule(Settings settings, boolean instant, MagicAmount transferLimit, MagicAmount conversionLimit,
                boolean sameDimension, int maxDistance, DeathMode death, boolean honorKeepInventory,
                boolean ownerOnly, int lifetime) {}

    private record Layer(JsonObject defaults, Map<ResourceLocation, JsonObject> currencies) {}
    private final List<Layer> layers;

    private PlayerEnergyPolicy(List<Layer> layers) { this.layers = List.copyOf(layers); }

    static PlayerEnergyPolicy read(MinecraftServer server) throws IOException {
        Map<ResourceLocation, JsonObject> data = new TreeMap<>();
        var resources = server.getResourceManager().listResources(DIRECTORY, id -> id.getPath().endsWith(".json"));
        for (var entry : new TreeMap<>(resources).entrySet()) {
            var id = entry.getKey();
            var currency = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                    id.getPath().substring(DIRECTORY.length() + 1, id.getPath().length() - 5));
            try (var reader = entry.getValue().openAsReader()) {
                data.put(currency, checked(parse(reader)));
            } catch (IOException | RuntimeException failure) {
                throw new IOException("Invalid player energy data pack rule " + id, failure);
            }
        }
        return new PlayerEnergyPolicy(List.of(new Layer(new JsonObject(), data),
                readLayer(FMLPaths.CONFIGDIR.get().resolve(FILE)),
                readLayer(server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(FILE))));
    }

    boolean sameRules(PlayerEnergyPolicy other) { return layers.equals(other.layers); }

    Rule rule(ResourceLocation currency, Settings requested) {
        var values = new JsonObject();
        for (Layer layer : layers) {
            overlay(values, layer.defaults());
            overlay(values, layer.currencies().get(currency));
        }
        var defaults = PlayerEnergyDefaults.forCurrency(currency);
        return new Rule(new Settings(bool(values, "enabled", requested.enabled()),
                number(values, "capacity", requested.capacity()),
                rate(values, "autoRate", requested.autoRate())), bool(values, "instant", defaults.instant()),
                rate(values, "transferRateLimit", defaults.transferRateLimit()), rate(values, "conversionRateLimit", defaults.conversionRateLimit()),
                bool(values, "sameDimension", defaults.sameDimension()), (int) number(values, "maxDistance", defaults.maxDistance()),
                values.has("death") ? DeathMode.valueOf(values.get("death").getAsString()) : defaults.death(),
                bool(values, "honorKeepInventory", defaults.honorKeepInventory()), bool(values, "ownerOnly", defaults.ownerOnly()),
                (int) number(values, "dropLifetime", defaults.dropLifetime()));
    }

    private static void overlay(JsonObject target, JsonObject source) {
        if (source != null) source.entrySet().forEach(entry -> target.add(entry.getKey(), entry.getValue()));
    }

    private static Layer readLayer(Path path) throws IOException {
        if (Files.notExists(path)) return new Layer(new JsonObject(), Map.of());
        try (var reader = Files.newBufferedReader(path)) {
            var root = parse(reader);
            if (!Set.of("defaults", "currencies").containsAll(root.keySet())) throw new IllegalArgumentException("Unknown policy section");
            var defaults = root.has("defaults") ? checked(root.getAsJsonObject("defaults")) : new JsonObject();
            var rules = new TreeMap<ResourceLocation, JsonObject>();
            if (root.has("currencies")) root.getAsJsonObject("currencies").entrySet().forEach(entry ->
                    rules.put(LeyWorldData.identifier(entry.getKey()), checked(entry.getValue().getAsJsonObject())));
            return new Layer(defaults, rules);
        } catch (IOException | RuntimeException failure) {
            throw new IOException("Invalid player energy policy " + path, failure);
        }
    }

    private static JsonObject parse(Reader reader) { return JsonParser.parseReader(reader).getAsJsonObject(); }

    private static JsonObject checked(JsonObject rule) {
        for (var entry : rule.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            if (!KEYS.contains(key)) throw new IllegalArgumentException("Unknown player energy setting " + key);
            switch (key) {
                case "enabled", "instant", "sameDimension", "honorKeepInventory", "ownerOnly" -> {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                        throw new IllegalArgumentException("Expected boolean for " + key);
                }
                case "capacity" -> integer(value);
                case "maxDistance" -> bounded(value, 0, 60_000_000);
                case "dropLifetime" -> bounded(value, 1, 1_728_000);
                case "death" -> DeathMode.valueOf(string(value));
                default -> {
                    if (value.isJsonNull() && !key.equals("autoRate")) throw new IllegalArgumentException("Expected rate for " + key);
                    if (!value.isJsonNull()) exactRate(string(value));
                }
            }
        }
        return rule;
    }

    private static boolean bool(JsonObject values, String key, boolean fallback) {
        return values.has(key) ? values.get(key).getAsBoolean() : fallback;
    }

    private static long number(JsonObject values, String key, long fallback) {
        return values.has(key) ? integer(values.get(key)) : fallback;
    }

    private static MagicAmount rate(JsonObject values, String key, MagicAmount fallback) {
        if (!values.has(key)) return fallback;
        return values.get(key).isJsonNull() ? null : exactRate(string(values.get(key)));
    }

    private static String string(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected a string");
        return value.getAsString();
    }

    private static long integer(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsString().matches("0|[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("Expected a nonnegative whole number");
        return Long.parseLong(value.getAsString());
    }

    private static void bounded(JsonElement value, int min, int max) {
        long number = integer(value);
        if (number < min || number > max) throw new IllegalArgumentException("Expected " + min + " through " + max);
    }

    static MagicAmount exactRate(String text) {
        if (text.length() > 256 || !text.matches("[1-9][0-9]*(/[1-9][0-9]*)?"))
            throw new IllegalArgumentException("Expected positive exact rate");
        var parts = text.split("/");
        return new MagicAmount(new BigInteger(parts[0]), parts.length == 1 ? BigInteger.ONE : new BigInteger(parts[1]));
    }
}
