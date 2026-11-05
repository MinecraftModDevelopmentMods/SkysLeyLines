package zone.moddev.mc.skysleylines.core;

import java.math.BigInteger;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.skysleylines.api.MagicAmount;

/** Checks the ASCII values written into NBT strings before the ledger accepts them. */
public final class LeySaveLimits {
    public static final int MAX_STRING_BYTES = 65_535;
    private static final int BOUNDARY_BITS = (int) Math.ceil(MAX_STRING_BYTES * (Math.log(10) / Math.log(2)));

    private LeySaveLimits() {}

    public static void checkCurrencyId(ResourceLocation id) {
        if (id.getNamespace().length() + 1L + id.getPath().length() > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Currency ID exceeds the NBT string limit: "
                    + id.getNamespace().substring(0, Math.min(32, id.getNamespace().length()))
                    + ":" + id.getPath().substring(0, Math.min(32, id.getPath().length())) + "…");
        }
    }

    public static void checkRate(ResourceLocation id, MagicAmount rate) {
        checkCurrencyId(id);
        if (Objects.requireNonNull(rate, "Null currency rate for " + id).isZero()) {
            throw new IllegalArgumentException("Currency rate must be positive: " + id);
        }
        checkAmount(rate, "Rate for " + id);
    }

    public static void checkAmount(MagicAmount value, String description) {
        if (!fits(value.numerator()) || !fits(value.denominator())) {
            throw new ArithmeticException(description + " exceeds the " + MAX_STRING_BYTES + "-byte NBT string limit");
        }
    }

    private static boolean fits(BigInteger component) {
        int bits = component.bitLength();
        return bits < BOUNDARY_BITS || (bits == BOUNDARY_BITS && component.toString().length() <= MAX_STRING_BYTES);
    }
}
