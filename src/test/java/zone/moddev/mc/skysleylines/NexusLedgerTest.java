package zone.moddev.mc.skysleylines;

import static org.junit.jupiter.api.Assertions.*;
import static zone.moddev.mc.skysleylines.api.PowerRequest.Operation.*;
import static zone.moddev.mc.skysleylines.api.PowerResult.Status.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.math.BigInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysleylines.api.*;
import zone.moddev.mc.skysleylines.core.NexusLedger;

class NexusLedgerTest {
    static final NexusId NODE = new NexusId("minecraft:overworld", -1, 0, 0, 1);
    static final ResourceLocation MANA = ResourceLocation.parse("mage:mana"), AETHER = ResourceLocation.parse("tech:aether");
    static final PowerRequest.Consumer CONSUMER = new PowerRequest.Consumer(ResourceLocation.parse("tech:plant"));
    static NexusLedger ledger() {
        var l = new NexusLedger(200); l.bindCurrencies(Map.of(MANA, MagicAmount.of(10), AETHER, MagicAmount.of(1000))); return l;
    }
    static PowerRequest request(PowerRequest.Operation op, ResourceLocation id, MagicAmount amount) {
        return new PowerRequest(1, 2, 3, op, id, amount, CONSUMER);
    }
    static PowerResult tx(NexusLedger l, long tick, PowerRequest.Operation op, ResourceLocation id, MagicAmount amount) {
        return l.transact(NODE, 20, tick, request(op, id, amount), NexusLedger.NO_HOOKS);
    }
    static PowerResult tx(NexusLedger l, long tick, PowerRequest.Operation op, long amount) { return tx(l, tick, op, MANA, MagicAmount.of(amount)); }
    static NexusLedger.MigrationHooks migrationHooks(boolean accept, Runnable backup) {
        return new NexusLedger.MigrationHooks() {
            public boolean before(RateMigration p) { return accept; }
            public void backup() { backup.run(); }
            public void after(RateMigration p) {}
            public void failure(RuntimeException failure) { fail(failure); }
        };
    }
    @Test void capacityTwentyExampleAndReturnPreserveSharedBudget() {
        var l = ledger();
        assertTrue(tx(l, 0, CONVERT, 10).succeeded());
        var a = l.quote(NODE, 20, 0);
        assertEquals(MagicAmount.of(10), a.raw()); assertEquals(100, a.currency(MANA)); assertEquals(MagicAmount.of(20), a.total());
        assertEquals(a.raw(), l.quote(NODE, 20, 2000).raw());
        assertTrue(tx(l, 2000, SPEND, 10).succeeded());
        assertEquals(MagicAmount.of(19), l.quote(NODE, 20, 2000).total());
        assertEquals(MagicAmount.of(11), l.quote(NODE, 20, 2010).raw());
        assertTrue(tx(l, 2010, RETURN_TO_RAW, 50).succeeded());
        a = l.quote(NODE, 20, 2010);
        assertEquals(MagicAmount.of(16), a.raw()); assertEquals(40, a.currency(MANA)); assertEquals(MagicAmount.of(20), a.total());
    }
    @Test void distinctParadigmsCompeteForRawAndConsumersCannotDoubleSpend() {
        var l = ledger(); assertTrue(tx(l, 0, CONVERT, 10).succeeded());
        assertTrue(tx(l, 0, CONVERT, AETHER, MagicAmount.of(10)).succeeded());
        assertEquals(INSUFFICIENT_RAW, tx(l, 0, CONVERT, 1).status());
        assertTrue(tx(l, 0, SPEND, 60).succeeded());
        var state = l.entries(); long rev = l.revision();
        assertEquals(INSUFFICIENT_CURRENCY, tx(l, 0, SPEND, 60).status());
        assertEquals(state, l.entries()); assertEquals(rev, l.revision());
        assertTrue(tx(l, 0, SPEND, 40).succeeded()); assertEquals(10000, l.quote(NODE, 20, 0).currency(AETHER));
    }
    @Test void untouchedQuotesArePureAndCurrenciesStartAtZero() {
        var l = ledger(); long rev = l.revision();
        assertEquals(MagicAmount.of(20), l.quote(NODE, 20, 1000).raw());
        assertEquals(0, l.quote(NODE, 20, 1000).currency(MANA)); assertTrue(l.entries().isEmpty()); assertEquals(rev, l.revision());
    }
    @Test void spendingAfterLongFullPeriodCannotRechargeRetroactively() {
        var l = ledger(); tx(l, 0, CONVERT, 20);
        tx(l, 100000, SPEND, 10);
        assertEquals(MagicAmount.ZERO, l.quote(NODE, 20, 100000).raw());
        assertEquals(MagicAmount.fraction(1, 10), l.quote(NODE, 20, 100001).raw());
        assertEquals(MagicAmount.ONE, l.quote(NODE, 20, Long.MAX_VALUE).raw());
    }
    @Test void fractionalRatiosAndRepeatedReturnsHaveNoRoundingLoss() {
        var l = new NexusLedger(200); l.bindCurrencies(Map.of(MANA, MagicAmount.fraction(3, 7)));
        assertEquals(NON_INTEGRAL_OUTPUT, tx(l, 0, CONVERT, 1).status());
        for (int i = 0; i < 100; i++) {
            assertTrue(tx(l, 0, CONVERT, MANA, MagicAmount.fraction(7, 3)).succeeded());
            assertTrue(tx(l, 0, RETURN_TO_RAW, 1).succeeded());
        }
        assertEquals(MagicAmount.of(20), l.quote(NODE, 20, 0).raw());
    }
    @Test void invalidUnknownAndOverflowRequestsDoNotMutate() {
        var l = ledger(); var before = l.entries();
        assertEquals(INVALID_AMOUNT, tx(l, 0, CONVERT, 0).status());
        assertEquals(INVALID_AMOUNT, tx(l, 0, SPEND, MANA, MagicAmount.fraction(1, 2)).status());
        assertEquals(UNKNOWN_CURRENCY, tx(l, 0, CONVERT, ResourceLocation.parse("absent:coin"), MagicAmount.ONE).status());
        assertEquals(before, l.entries());
        var huge = new NexusLedger(200); huge.bindCurrencies(Map.of(MANA, MagicAmount.of(Long.MAX_VALUE)));
        assertTrue(tx(huge, 0, CONVERT, 1).succeeded()); before = huge.entries();
        assertEquals(OVERFLOW, tx(huge, 0, CONVERT, 1).status()); assertEquals(before, huge.entries());
        assertThrows(IllegalArgumentException.class, () -> MagicAmount.of(-1));
    }
    @Test void clockRewindAndFractionalRechargeSurviveRestore() {
        var l = ledger(); tx(l, 100, CONVERT, 20); tx(l, 100, SPEND, 200);
        assertEquals(MagicAmount.ZERO, l.quote(NODE, 20, 0).raw());
        assertEquals(MagicAmount.fraction(1, 10), l.quote(NODE, 20, 101).raw());
        var restored = ledger(); l.entries().forEach(restored::restore);
        assertEquals(l.quote(NODE, 20, 107), restored.quote(NODE, 20, 107));
        assertEquals(MagicAmount.of(20), restored.quote(NODE, 20, Long.MAX_VALUE).raw());
    }
    @Test void fullConvertedBalancesCannotBePruned() {
        var l = ledger(); tx(l, 0, CONVERT, 20); l.pruneRecovered(10000);
        assertEquals(200, l.quote(NODE, 20, 10000).currency(MANA)); assertEquals(1, l.entries().size());
        tx(l, 10000, RETURN_TO_RAW, 200); l.pruneRecovered(10000); assertTrue(l.entries().isEmpty());
    }
    @Test void cancelledChecksAndThrowingChecksLeaveNoTrace() {
        var l = ledger(); long rev = l.revision();
        var r = request(CONVERT, MANA, MagicAmount.ONE);
        assertEquals(CANCELLED, l.transact(NODE, 20, 0, r, new NexusLedger.Hooks() {
            public boolean before(PowerResult.Receipt p) { return false; }
            public void after(PowerResult.Receipt p) { fail("Cancelled transaction notified"); }
        }).status());
        assertEquals(CHECK_FAILED, l.transact(NODE, 20, 0, r, new NexusLedger.Hooks() {
            public boolean before(PowerResult.Receipt p) { throw new IllegalStateException("intentional pre-check failure"); }
        }).status());
        assertEquals(rev, l.revision()); assertTrue(l.entries().isEmpty());
    }
    @Test void callbacksRejectCrossNodeReentrancyAndPostFailureStillReturnsSuccess() {
        var l = ledger(); AtomicInteger posts = new AtomicInteger(), errors = new AtomicInteger();
        var r = request(CONVERT, MANA, MagicAmount.ONE);
        var result = l.transact(NODE, 20, 0, r, new NexusLedger.Hooks() {
            public boolean before(PowerResult.Receipt p) {
                assertEquals(REENTRANT, l.transact(new NexusId("minecraft:overworld", 0, 0, 1, 0), 20, 0, r, NexusLedger.NO_HOOKS).status());
                assertEquals(MagicAmount.of(20), l.quote(NODE, 20, 0).total()); return true;
            }
            public void after(PowerResult.Receipt p) { posts.incrementAndGet(); throw new IllegalStateException("intentional post failure"); }
            public void failure(RuntimeException failure) { errors.incrementAndGet(); }
        });
        assertTrue(result.succeeded()); assertEquals(1, posts.get()); assertEquals(1, errors.get()); assertEquals(10, l.quote(NODE, 20, 0).currency(MANA));
    }
    @Test void absentModCannotConvertOrSpendButItsBackingCanReturnToRaw() {
        var l = ledger(); tx(l, 0, CONVERT, 10); l.bindCurrencies(Map.of());
        assertEquals(MagicAmount.of(20), l.quote(NODE, 20, 1000).total());
        assertEquals(INACTIVE_CURRENCY, tx(l, 0, CONVERT, 1).status()); assertEquals(INACTIVE_CURRENCY, tx(l, 0, SPEND, 1).status());
        long beforeReturn = l.revision();
        var recovery = request(RETURN_TO_RAW, MANA, MagicAmount.of(100));
        assertEquals(CANCELLED, l.transact(NODE, 20, 0, recovery, new NexusLedger.Hooks() {
            @Override public boolean before(PowerResult.Receipt proposed) { return false; }
            @Override public void after(PowerResult.Receipt receipt) { fail("Cancelled recovery was reported as committed"); }
        }).status());
        assertEquals(beforeReturn, l.revision());
        assertEquals(100, l.quote(NODE, 20, 0).currency(MANA));
        assertTrue(tx(l, 0, RETURN_TO_RAW, 100).succeeded()); assertEquals(MagicAmount.of(20), l.quote(NODE, 20, 0).raw());
        l.bindCurrencies(Map.of(MANA, MagicAmount.of(500))); assertEquals(MagicAmount.of(10), l.rates().get(MANA));
    }
    @Test void migrationConservesValueAndReturnsIndivisibleValueToRaw() {
        var l = ledger(); tx(l, 0, CONVERT, MANA, MagicAmount.fraction(1, 10));
        var p = l.previewMigration(Map.of(MANA, MagicAmount.of(3)), 0); AtomicInteger backups = new AtomicInteger();
        assertTrue(l.applyMigration(p, 0, migrationHooks(true, backups::incrementAndGet)));
        assertEquals(1, backups.get()); assertEquals(MagicAmount.of(3), l.rates().get(MANA));
        assertEquals(MagicAmount.of(20), l.quote(NODE, 20, 0).raw()); assertEquals(0, l.quote(NODE, 20, 0).currency(MANA));
    }
    @Test void migrationReevaluatesElapsedRechargeUsingOldRatesBeforeCommit() {
        var l = ledger(); tx(l, 0, CONVERT, 20); tx(l, 0, SPEND, 100);
        var plan = l.previewMigration(Map.of(MANA, MagicAmount.of(20)), 0);
        assertTrue(l.applyMigration(plan, 50, migrationHooks(true, () -> {})));
        assertEquals(MagicAmount.of(15), l.quote(NODE, 20, 50).total()); assertEquals(200, l.quote(NODE, 20, 50).currency(MANA));
    }
    @Test void staleCancelledFailedBackupAndOverflowMigrationAreAtomic() {
        var l = ledger(); tx(l, 0, CONVERT, 10);
        var plan = l.previewMigration(Map.of(MANA, MagicAmount.of(20)), 0);
        tx(l, 0, SPEND, 1); assertFalse(l.applyMigration(plan, 0, migrationHooks(true, () -> fail("Stale backup"))));
        plan = l.previewMigration(Map.of(MANA, MagicAmount.of(20)), 0);
        var before = l.entries(); var rates = l.rates(); long revision = l.revision();
        assertFalse(l.applyMigration(plan, 0, migrationHooks(false, () -> fail("Cancelled backup"))));
        final var current = plan;
        assertThrows(IllegalStateException.class, () -> l.applyMigration(current, 0, migrationHooks(true, () -> { throw new IllegalStateException("backup unavailable"); })));
        assertThrows(ArithmeticException.class, () -> l.previewMigration(Map.of(MANA, new MagicAmount(BigInteger.TEN.pow(50), BigInteger.ONE)), 0));
        assertEquals(before, l.entries()); assertEquals(rates, l.rates()); assertEquals(revision, l.revision());
    }
    @Test void corruptRestoredAccountsAreRejected() {
        var l = ledger();
        assertThrows(IllegalArgumentException.class, () -> l.restore(NODE, new NexusLedger.Balance(20, MagicAmount.of(20), Map.of(MANA, 1L), 0)));
        assertThrows(IllegalArgumentException.class, () -> l.restore(NODE, new NexusLedger.Balance(20, MagicAmount.ZERO, Map.of(ResourceLocation.parse("unknown:coin"), 1L), 0)));
        l.restore(NODE, new NexusLedger.Balance(20, MagicAmount.ZERO, Map.of(), 0));
        assertThrows(IllegalStateException.class, () -> l.quote(NODE, 19, 0));
        assertThrows(IllegalArgumentException.class, () -> l.restore(NODE, new NexusLedger.Balance(20, MagicAmount.ZERO, Map.of(), 0)));
    }
}
