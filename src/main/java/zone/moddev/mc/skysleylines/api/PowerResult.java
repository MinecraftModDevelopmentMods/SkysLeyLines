package zone.moddev.mc.skysleylines.api;

import java.util.Optional;

/** A successful receipt means the ledger committed; consumers apply their effect only after success. */
public record PowerResult(Status status, Optional<Receipt> receipt) {
    public enum Status { SUCCESS, NO_NEXUS, INVALID_AMOUNT, UNKNOWN_CURRENCY, INACTIVE_CURRENCY,
        INSUFFICIENT_RAW, INSUFFICIENT_CURRENCY, NON_INTEGRAL_OUTPUT, OVERFLOW, CANCELLED, REENTRANT, CHECK_FAILED }
    public record Receipt(long revision, NexusId nexus, PowerRequest request, AccountSnapshot before, AccountSnapshot after) {}
    public boolean succeeded() { return status == Status.SUCCESS; }
    public static PowerResult failed(Status status) {
        if (status == Status.SUCCESS) throw new IllegalArgumentException("Missing success receipt");
        return new PowerResult(status, Optional.empty());
    }
    public static PowerResult committed(Receipt receipt) { return new PowerResult(Status.SUCCESS, Optional.of(receipt)); }
}
