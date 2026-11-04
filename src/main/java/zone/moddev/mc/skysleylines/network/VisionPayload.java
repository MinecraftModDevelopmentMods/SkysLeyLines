package zone.moddev.mc.skysleylines.network;

import java.util.ArrayList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.skysleylines.core.VisionGeometry;

/** Clientbound only. An empty patch clears any previous view. */
public record VisionPayload(ResourceLocation dimension, VisionGeometry geometry) implements CustomPacketPayload {
    public static final Type<VisionPayload> TYPE = new Type<>(ResourceLocation.parse("skysleylines:vision"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VisionPayload> CODEC =
            StreamCodec.of(VisionPayload::write, VisionPayload::read);

    private static void write(RegistryFriendlyByteBuf buffer, VisionPayload payload) {
        var geometry = payload.geometry;
        buffer.writeResourceLocation(payload.dimension);
        buffer.writeByte(geometry.lineHalfWidth());
        buffer.writeByte(geometry.reaches().size());
        for (var reach : geometry.reaches()) {
            buffer.writeDouble(reach.x0());
            buffer.writeDouble(reach.z0());
            buffer.writeDouble(reach.x1());
            buffer.writeDouble(reach.z1());
            buffer.writeFloat(reach.strength());
        }
        buffer.writeByte(geometry.crossings().size());
        for (var crossing : geometry.crossings()) {
            buffer.writeDouble(crossing.x());
            buffer.writeDouble(crossing.z());
            buffer.writeFloat(crossing.strength());
        }
    }

    private static VisionPayload read(RegistryFriendlyByteBuf buffer) {
        var dimension = buffer.readResourceLocation();
        int width = buffer.readUnsignedByte();
        int reachCount = buffer.readUnsignedByte();
        if (reachCount > VisionGeometry.MAX_REACHES) throw new IllegalArgumentException("Too many ley reaches");
        var reaches = new ArrayList<VisionGeometry.Reach>(reachCount);
        for (int i = 0; i < reachCount; i++) {
            reaches.add(new VisionGeometry.Reach(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readFloat()));
        }
        int crossingCount = buffer.readUnsignedByte();
        if (crossingCount > VisionGeometry.MAX_CROSSINGS) throw new IllegalArgumentException("Too many ley crossings");
        var crossings = new ArrayList<VisionGeometry.Crossing>(crossingCount);
        for (int i = 0; i < crossingCount; i++) {
            crossings.add(new VisionGeometry.Crossing(buffer.readDouble(), buffer.readDouble(), buffer.readFloat()));
        }
        return new VisionPayload(dimension, new VisionGeometry(width, reaches, crossings));
    }

    @Override public Type<VisionPayload> type() { return TYPE; }
}
