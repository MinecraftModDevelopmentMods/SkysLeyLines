package zone.moddev.mc.skysleylines.network;

import java.util.ArrayList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.skysleylines.api.LeyVisionStyle;
import zone.moddev.mc.skysleylines.core.VisionGeometry;

/** Clientbound only. An empty patch clears any previous view. */
public record VisionPayload(ResourceLocation dimension, VisionGeometry geometry, LeyVisionStyle style) implements CustomPacketPayload {
    public static final Type<VisionPayload> TYPE = new Type<>(ResourceLocation.parse("skysleylines:vision"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VisionPayload> CODEC =
            StreamCodec.of(VisionPayload::write, VisionPayload::read);

    private static void write(RegistryFriendlyByteBuf buffer, VisionPayload payload) {
        var geometry = payload.geometry;
        buffer.writeResourceLocation(payload.dimension);
        buffer.writeEnum(payload.style.pattern());
        buffer.writeInt(payload.style.lineRgb());
        buffer.writeInt(payload.style.nexusRgb());
        buffer.writeFloat(payload.style.opacity());
        buffer.writeFloat(payload.style.scale());
        buffer.writeByte(geometry.lineHalfWidth());
        buffer.writeByte(geometry.reaches().size());
        for (var reach : geometry.reaches()) {
            buffer.writeDouble(reach.x0());
            buffer.writeDouble(reach.z0());
            buffer.writeDouble(reach.x1());
            buffer.writeDouble(reach.z1());
            buffer.writeFloat(reach.strength());
            buffer.writeDouble(reach.y0());
            buffer.writeDouble(reach.y1());
        }
        buffer.writeByte(geometry.crossings().size());
        for (var crossing : geometry.crossings()) {
            buffer.writeDouble(crossing.x());
            buffer.writeDouble(crossing.z());
            buffer.writeFloat(crossing.strength());
            buffer.writeDouble(crossing.y());
        }
    }

    private static VisionPayload read(RegistryFriendlyByteBuf buffer) {
        var dimension = buffer.readResourceLocation();
        var style = new LeyVisionStyle(buffer.readEnum(LeyVisionStyle.Pattern.class), buffer.readInt(),
                buffer.readInt(), buffer.readFloat(), buffer.readFloat());
        int width = buffer.readUnsignedByte();
        int reachCount = buffer.readUnsignedByte();
        if (reachCount > VisionGeometry.MAX_REACHES) throw new IllegalArgumentException("Too many ley reaches");
        var reaches = new ArrayList<VisionGeometry.Reach>(reachCount);
        for (int i = 0; i < reachCount; i++) {
            reaches.add(new VisionGeometry.Reach(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readFloat(), buffer.readDouble(), buffer.readDouble()));
        }
        int crossingCount = buffer.readUnsignedByte();
        if (crossingCount > VisionGeometry.MAX_CROSSINGS) throw new IllegalArgumentException("Too many ley crossings");
        var crossings = new ArrayList<VisionGeometry.Crossing>(crossingCount);
        for (int i = 0; i < crossingCount; i++) {
            crossings.add(new VisionGeometry.Crossing(buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readDouble()));
        }
        return new VisionPayload(dimension, new VisionGeometry(width, reaches, crossings), style);
    }

    @Override public Type<VisionPayload> type() { return TYPE; }
}
