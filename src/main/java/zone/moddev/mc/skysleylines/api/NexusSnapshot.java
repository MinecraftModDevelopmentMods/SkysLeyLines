package zone.moddev.mc.skysleylines.api;

/** Server-authoritative reserve quotation. A quote does not reserve energy for a later call. */
public record NexusSnapshot(Nexus nexus, AccountSnapshot account, int rechargeTicks, double localStrength) {
    public int capacity() { return account.capacity(); }
}
