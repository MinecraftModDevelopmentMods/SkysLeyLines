package zone.moddev.mc.skysleylines;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysleylines.network.LeyMeterPayload;

class LeyMeterTest {
    @Test void meterPayloadPreservesAnExactSnapshotAndInactiveCurrencies() {
        var reserve = new LeyMeterPayload.Reserve("node", 0.87, "42/7", "7103/11", 8700, 24000, List.of(
                new LeyMeterPayload.Currency("probe:aether", 9_007_199_254_740_993L, "3/4", true),
                new LeyMeterPayload.Currency("probe:electric", 0, "10000", false)), 0);
        var reading = new LeyMeterPayload(ResourceLocation.parse("minecraft:overworld"),
                new BlockPos(1166, -22, -743), true, 0.8, 0.4, 0.8, 0.9, 0.2, reserve);
        assertRoundTrip(reading);
    }

    @Test void meterPayloadDistinguishesInactiveDimensionFromAnActiveFieldWithoutAnAccount() {
        assertRoundTrip(new LeyMeterPayload(ResourceLocation.parse("minecraft:the_nether"), BlockPos.ZERO,
                false, 0, 0, 0, 0, 1, null));
        assertRoundTrip(new LeyMeterPayload(ResourceLocation.parse("minecraft:overworld"), BlockPos.ZERO,
                true, 0.4, 0.4, 0, 1, 0.6, null));
    }

    private static void assertRoundTrip(LeyMeterPayload reading) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            LeyMeterPayload.CODEC.encode(buffer, reading);
            assertEquals(reading, LeyMeterPayload.CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }
}
