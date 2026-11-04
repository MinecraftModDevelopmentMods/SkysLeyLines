package zone.moddev.mc.skysleylines;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.skysleylines.api.*;

class ParadigmRegistryTest {
    @Test void typedExtensionAndLateRateResolutionWithoutHotPathCallbacks() {
        var r = new ParadigmRegistry(); var rate = new AtomicInteger(10); var calls = new AtomicInteger();
        var config = new Object();
        var paradigm = new Paradigm<>(ResourceLocation.parse("tech:technomancy"), config,
                List.of(new Paradigm.Currency(NexusLedgerTest.MANA, () -> { calls.incrementAndGet(); return MagicAmount.of(rate.get()); })));
        assertSame(config, r.register(paradigm).configuration()); assertEquals(0, calls.get());
        rate.set(1000); r.freeze();
        assertEquals(MagicAmount.of(1000), r.configuredRates().get(NexusLedgerTest.MANA)); assertEquals(1, calls.get());
        assertThrows(IllegalStateException.class, () -> r.register(paradigm));
    }
    @Test void duplicateAndInvalidDefinitionsAreRejectedWithoutPartialRegistration() {
        var r = new ParadigmRegistry(); var id = ResourceLocation.parse("tech:magic");
        var c = new Paradigm.Currency(NexusLedgerTest.MANA, () -> MagicAmount.ONE);
        assertThrows(IllegalArgumentException.class, () -> r.register(new Paradigm<>(id, null, List.of(c, c))));
        assertTrue(r.definitions().isEmpty()); r.register(new Paradigm<>(id, null, List.of(c)));
        assertThrows(IllegalArgumentException.class, () -> r.register(new Paradigm<>(id, null, List.of())));
        assertThrows(IllegalArgumentException.class, () -> r.register(new Paradigm<>(ResourceLocation.parse("other:magic"), null, List.of(c))));
        var invalid = new ParadigmRegistry(); invalid.register(new Paradigm<>(id, null, List.of(new Paradigm.Currency(NexusLedgerTest.MANA, () -> MagicAmount.ZERO)))); invalid.freeze();
        assertThrows(IllegalArgumentException.class, invalid::configuredRates);
    }
}
