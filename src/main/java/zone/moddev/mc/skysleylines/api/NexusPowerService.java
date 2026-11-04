package zone.moddev.mc.skysleylines.api;

import java.util.Optional;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Bound to one server level. All methods require its server thread. The caller owns its machine/player position. */
public interface NexusPowerService {
    /** Simulation only; does not create a saved balance, claim power, or dirty world data. */
    Optional<NexusSnapshot> quoteAt(int x, int y, int z);
    /** Saved world rates, including inactive currencies retained for recovery. */
    Map<ResourceLocation, MagicAmount> rates();
    /** All-or-nothing against the nearest eligible nexus, with no fallback or splitting. */
    PowerResult transact(PowerRequest request);
    default PowerResult convertAt(int x, int y, int z, ResourceLocation currency, MagicAmount raw, PowerRequest.Consumer consumer) {
        return transact(new PowerRequest(x, y, z, PowerRequest.Operation.CONVERT, currency, raw, consumer));
    }
    default PowerResult spendAt(int x, int y, int z, ResourceLocation currency, long units, PowerRequest.Consumer consumer) {
        if (units <= 0) return PowerResult.failed(PowerResult.Status.INVALID_AMOUNT);
        return transact(new PowerRequest(x, y, z, PowerRequest.Operation.SPEND, currency, MagicAmount.of(units), consumer));
    }
    default PowerResult returnToRawAt(int x, int y, int z, ResourceLocation currency, long units, PowerRequest.Consumer consumer) {
        if (units <= 0) return PowerResult.failed(PowerResult.Status.INVALID_AMOUNT);
        return transact(new PowerRequest(x, y, z, PowerRequest.Operation.RETURN_TO_RAW, currency, MagicAmount.of(units), consumer));
    }
}
