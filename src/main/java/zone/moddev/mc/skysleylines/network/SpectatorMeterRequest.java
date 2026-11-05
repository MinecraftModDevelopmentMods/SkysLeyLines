package zone.moddev.mc.skysleylines.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

/** Spectator mode suppresses vanilla use-in-air. The server samples only the player's own position. */
public record SpectatorMeterRequest(InteractionHand hand) implements CustomPacketPayload {
    public static final Type<SpectatorMeterRequest> TYPE =
            new Type<>(ResourceLocation.parse("skysleylines:spectator_meter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpectatorMeterRequest> CODEC =
            StreamCodec.of((buf, value) -> buf.writeEnum(value.hand),
                    buf -> new SpectatorMeterRequest(buf.readEnum(InteractionHand.class)));

    @Override public Type<SpectatorMeterRequest> type() { return TYPE; }
}
