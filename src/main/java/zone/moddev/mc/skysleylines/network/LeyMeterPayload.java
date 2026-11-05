package zone.moddev.mc.skysleylines.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A single reading sent to the operator who used the meter. Ledger amounts are exact text. */
public record LeyMeterPayload(ResourceLocation dimension, BlockPos position, boolean active,
                              double naturalStrength, double lineStrength, double nexusStrength,
                              double verticalFactor, double dimensionalResistance, Reserve reserve)
        implements CustomPacketPayload {
    public static final int MAX_CURRENCIES = 512;
    public static final int MAX_TEXT = 256;
    public static final Type<LeyMeterPayload> TYPE = new Type<>(ResourceLocation.parse("skysleylines:ley_meter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LeyMeterPayload> CODEC =
            StreamCodec.of(LeyMeterPayload::write, LeyMeterPayload::read);

    public record Currency(String id, long balance, String rate, boolean installed) {}

    public record Reserve(String id, double strength, String raw, String total, int capacity, int rechargeTicks,
                          List<Currency> currencies, int omitted) {
        public Reserve {
            currencies = List.copyOf(currencies);
            if (currencies.size() > MAX_CURRENCIES || omitted < 0) throw new IllegalArgumentException("Invalid currency count");
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, LeyMeterPayload value) {
        buf.writeResourceLocation(value.dimension);
        buf.writeBlockPos(value.position);
        buf.writeBoolean(value.active);
        buf.writeDouble(value.naturalStrength);
        buf.writeDouble(value.lineStrength);
        buf.writeDouble(value.nexusStrength);
        buf.writeDouble(value.verticalFactor);
        buf.writeDouble(value.dimensionalResistance);
        Reserve reserve = value.reserve;
        buf.writeBoolean(reserve != null);
        if (reserve == null) return;
        buf.writeUtf(reserve.id, MAX_TEXT);
        buf.writeDouble(reserve.strength);
        buf.writeUtf(reserve.raw, MAX_TEXT);
        buf.writeUtf(reserve.total, MAX_TEXT);
        buf.writeVarInt(reserve.capacity);
        buf.writeVarInt(reserve.rechargeTicks);
        buf.writeVarInt(reserve.currencies.size());
        for (Currency currency : reserve.currencies) {
            buf.writeUtf(currency.id, MAX_TEXT);
            buf.writeVarLong(currency.balance);
            buf.writeUtf(currency.rate, MAX_TEXT);
            buf.writeBoolean(currency.installed);
        }
        buf.writeVarInt(reserve.omitted);
    }

    private static LeyMeterPayload read(RegistryFriendlyByteBuf buf) {
        return new LeyMeterPayload(buf.readResourceLocation(), buf.readBlockPos(), buf.readBoolean(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readBoolean() ? readReserve(buf) : null);
    }

    private static Reserve readReserve(RegistryFriendlyByteBuf buf) {
        String id = buf.readUtf(MAX_TEXT);
        double strength = buf.readDouble();
        String raw = buf.readUtf(MAX_TEXT), total = buf.readUtf(MAX_TEXT);
        int capacity = buf.readVarInt(), recharge = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_CURRENCIES) throw new IllegalArgumentException("Invalid currency count");
        var currencies = new ArrayList<Currency>(count);
        for (int i = 0; i < count; i++) {
            currencies.add(new Currency(buf.readUtf(MAX_TEXT), buf.readVarLong(), buf.readUtf(MAX_TEXT), buf.readBoolean()));
        }
        return new Reserve(id, strength, raw, total, capacity, recharge, currencies, buf.readVarInt());
    }

    @Override public Type<LeyMeterPayload> type() { return TYPE; }
}
