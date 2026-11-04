package zone.moddev.mc.skysleylines.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.skysleylines.item.DowsingReading;

/** Server-to-client only. Observer identity/dimension and a bounded qualitative local reading. */
public record DowsingPayload(UUID player, ResourceLocation dimension, DowsingReading reading) implements CustomPacketPayload {
    public static final Type<DowsingPayload> TYPE = new Type<>(ResourceLocation.parse("skysleylines:dowsing"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DowsingPayload> CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeUUID(value.player()); buffer.writeResourceLocation(value.dimension());
                buffer.writeByte(value.reading().mode()); buffer.writeFloat(value.reading().bearing());
            }, buffer -> new DowsingPayload(buffer.readUUID(), buffer.readResourceLocation(),
                    new DowsingReading(buffer.readUnsignedByte(), buffer.readFloat())));
    @Override public Type<DowsingPayload> type() { return TYPE; }
}
