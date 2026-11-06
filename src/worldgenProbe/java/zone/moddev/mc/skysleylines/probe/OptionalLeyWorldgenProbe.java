package zone.moddev.mc.skysleylines.probe;

import java.util.concurrent.atomic.AtomicBoolean;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(OptionalLeyWorldgenProbe.MOD_ID)
public final class OptionalLeyWorldgenProbe {
    public static final String MOD_ID = "leyworldgenprobe";
    private static final AtomicBoolean STARTUP_WORKER = new AtomicBoolean();
    private static final AtomicBoolean EXPLORATION_WORKER = new AtomicBoolean();
    private static volatile boolean starting = true;
    private static boolean present;

    public OptionalLeyWorldgenProbe(IEventBus bus) {
        var features = DeferredRegister.create(Registries.FEATURE, MOD_ID);
        features.register("sample", SampleFeature::new);
        features.register(bus);
        present = ModList.get().isLoaded("skysleylines");
        StructureWorldgenProbe.register(bus, present);
        if (present) {
            NeoForge.EVENT_BUS.addListener(OptionalLeyWorldgenProbe::load);
            NeoForge.EVENT_BUS.addListener(OptionalLeyWorldgenProbe::unload);
        }
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> starting = false);
    }

    private static void load(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(Level.OVERWORLD)) {
            OptionalSampler.load(level);
        }
    }

    private static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) OptionalSampler.unload(level);
    }

    private static final class SampleFeature extends Feature<NoneFeatureConfiguration> {
        private SampleFeature() { super(NoneFeatureConfiguration.CODEC); }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
            var level = context.level().getLevel();
            var pos = context.origin();
            double strength = present ? OptionalSampler.sample(level, pos.getX(), pos.getZ()) : -1;
            var receipt = starting ? STARTUP_WORKER : EXPLORATION_WORKER;
            if (!level.getServer().isSameThread() && receipt.compareAndSet(false, true)) {
                LogUtils.getLogger().info("LEY_WORLDGEN_PROBE phase={} present={} worker={} pos={} strength={}",
                        starting ? "spawn" : "exploration", present, Thread.currentThread().getName(), pos, strength);
            }
            return false;
        }
    }
}
