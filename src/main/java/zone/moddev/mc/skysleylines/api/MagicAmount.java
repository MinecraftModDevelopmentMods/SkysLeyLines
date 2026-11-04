package zone.moddev.mc.skysleylines.api;

import java.math.BigInteger;
import java.util.Objects;

/** Exact nonnegative rational value. Never use floating point for ledger arithmetic. */
public record MagicAmount(BigInteger numerator, BigInteger denominator) implements Comparable<MagicAmount> {
    public static final MagicAmount ZERO = of(0);
    public static final MagicAmount ONE = of(1);
    public MagicAmount {
        Objects.requireNonNull(numerator); Objects.requireNonNull(denominator);
        if (numerator.signum() < 0 || denominator.signum() <= 0) throw new IllegalArgumentException("Invalid magic amount");
        BigInteger gcd = numerator.gcd(denominator);
        numerator = numerator.divide(gcd); denominator = denominator.divide(gcd);
    }
    public static MagicAmount of(long value) { return fraction(value, 1); }
    public static MagicAmount fraction(long numerator, long denominator) {
        return new MagicAmount(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }
    public MagicAmount add(MagicAmount b) { return new MagicAmount(numerator.multiply(b.denominator).add(b.numerator.multiply(denominator)), denominator.multiply(b.denominator)); }
    public MagicAmount subtract(MagicAmount b) { return new MagicAmount(numerator.multiply(b.denominator).subtract(b.numerator.multiply(denominator)), denominator.multiply(b.denominator)); }
    public MagicAmount multiply(MagicAmount b) { return new MagicAmount(numerator.multiply(b.numerator), denominator.multiply(b.denominator)); }
    public MagicAmount divide(MagicAmount b) { return new MagicAmount(numerator.multiply(b.denominator), denominator.multiply(b.numerator)); }
    public boolean isZero() { return numerator.signum() == 0; }
    public boolean isWhole() { return denominator.equals(BigInteger.ONE); }
    public long wholeLongExact() { if (!isWhole()) throw new ArithmeticException("Fractional currency units"); return numerator.longValueExact(); }
    public long floorLongExact() { return numerator.divide(denominator).longValueExact(); }
    /** Display only. */
    public double doubleValue() { return new java.math.BigDecimal(numerator).divide(new java.math.BigDecimal(denominator), java.math.MathContext.DECIMAL64).doubleValue(); }
    public MagicAmount min(MagicAmount b) { return compareTo(b) <= 0 ? this : b; }
    @Override public int compareTo(MagicAmount b) { return numerator.multiply(b.denominator).compareTo(b.numerator.multiply(denominator)); }
    @Override public String toString() { return isWhole() ? numerator.toString() : numerator + "/" + denominator; }
}
