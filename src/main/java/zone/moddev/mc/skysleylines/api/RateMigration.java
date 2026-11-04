package zone.moddev.mc.skysleylines.api;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Immutable preview bound to the ledger revision. Apply re-evaluates normal recharge at the current tick. */
public record RateMigration(long revision, Map<ResourceLocation, MagicAmount> oldRates,
                            Map<ResourceLocation, MagicAmount> newRates, Map<NexusId, AccountSnapshot> accounts) {
    public RateMigration { oldRates = Map.copyOf(oldRates); newRates = Map.copyOf(newRates); accounts = Map.copyOf(accounts); }
}
