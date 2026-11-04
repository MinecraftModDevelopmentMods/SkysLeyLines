package zone.moddev.mc.skysleylines.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Amount is raw for CONVERT and whole currency units for SPEND/RETURN_TO_RAW. Supply the actual position. */
public record PowerRequest(int x, int y, int z, Operation operation, ResourceLocation currency,
                           MagicAmount amount, Consumer consumer) {
    public enum Operation { CONVERT, SPEND, RETURN_TO_RAW }
    public record Consumer(ResourceLocation id, Optional<UUID> actor) {
        public Consumer { Objects.requireNonNull(id); Objects.requireNonNull(actor); }
        public Consumer(ResourceLocation id) { this(id, Optional.empty()); }
    }
    public PowerRequest {
        Objects.requireNonNull(operation); Objects.requireNonNull(currency); Objects.requireNonNull(amount); Objects.requireNonNull(consumer);
    }
}
