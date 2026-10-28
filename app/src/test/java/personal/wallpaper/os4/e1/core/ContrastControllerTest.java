package personal.wallpaper.os4.e1.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static personal.wallpaper.os4.e1.core.ContrastController.*;

public class ContrastControllerTest {
    private static long baseline(ContrastController c) {
        long epoch = c.start(100, BASELINE);
        assertTrue(epoch > 0);
        return epoch;
    }

    private static long enabled(ContrastController c) {
        long epoch = baseline(c);
        assertTrue(c.enable(101));
        return epoch;
    }

    private static boolean permit(ContrastController c, long now, long epoch) {
        return c.permit(now, epoch, true, true, true, true, false, true);
    }

    private static void apply(ContrastController c, long now, long epoch) {
        assertTrue(permit(c, now, epoch));
        assertTrue(c.overrideApplied(now, epoch));
    }

    private static void confirmed(ContrastController c, long now, long epoch) {
        assertTrue(c.beginRecovery(now, "USER_RECOVERY"));
        assertTrue(c.observeRecovery(now, epoch, true, true, true));
        assertEquals(FINAL_CONFIRMED, c.finalizeRecovery(now, epoch));
    }

    @Test public void defaultIsDisarmedAndCannotModifyOrProveRestoration() {
        ContrastController c = new ContrastController();
        assertEquals(DISARMED, c.mode());
        assertEquals(0, c.epoch());
        assertFalse(c.needsRecovery());
        assertFalse(c.recoveryProved());
        assertFalse(c.enable(0));
        assertFalse(permit(c, 0, 0));
        assertFalse(c.overrideApplied(0, 0));
        assertFalse(c.observeRecovery(0, 0, true, true, true));
        assertFalse(c.beginRecovery(0, "USER_STOP"));
        assertEquals("STALE_EPOCH", c.finalizeRecovery(0, 0));
    }

    @Test public void startOnlyAcceptsExactBaselineAndNonnegativeTime() {
        ContrastController c = new ContrastController();
        for (String mode : new String[]{null, "", "baseline", ENABLE, RECOVERY, DISARMED,
                "BASELINE\n", "BASELINE private_uri"}) {
            assertEquals(0, c.start(10, mode));
            assertEquals(0, c.epoch());
            assertEquals(DISARMED, c.mode());
        }
        assertEquals(0, c.start(-1, BASELINE));
        assertEquals(1, c.start(0, BASELINE));
    }

    @Test public void baselineNeverOverridesAndEnableDoesNotCreateNewEpoch() {
        ContrastController c = new ContrastController();
        long epoch = baseline(c);
        assertFalse(permit(c, 100, epoch));
        assertFalse(c.overrideApplied(100, epoch));
        assertTrue(c.enable(101));
        assertEquals(epoch, c.epoch());
        assertEquals(ENABLE, c.mode());
        assertFalse(c.enable(102));
        assertEquals(0, c.appliedCount());
        apply(c, 102, epoch);
        assertEquals(1, c.appliedCount());
    }

    @Test public void duplicateStartPreservesEpochCountersAndDeadline() {
        ContrastController c = new ContrastController(10, 20);
        long epoch = enabled(c);
        apply(c, 102, epoch);
        assertEquals(0, c.start(108, BASELINE));
        assertEquals(epoch, c.epoch());
        assertEquals(1, c.appliedCount());
        assertFalse(permit(c, 110, epoch));
        assertEquals(RECOVERY, c.mode());
        assertTrue(c.status(110).contains("contrastReason=TIMEOUT"));
    }

    @Test public void allSixtyFourAuthorizationCombinationsFailClosedExceptExactOne() {
        for (int bits = 0; bits < 64; bits++) {
            ContrastController c = new ContrastController();
            long epoch = enabled(c);
            boolean allowed = c.permit(102, epoch, (bits & 1) != 0, (bits & 2) != 0,
                    (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0,
                    (bits & 32) != 0);
            assertEquals("CONDITION_BITS=" + bits, bits == 47, allowed);
            assertEquals(allowed ? 1 : 0, c.permitCount());
            assertEquals(0, c.appliedCount());
            assertEquals(allowed, c.overrideApplied(102, epoch));
            assertEquals(allowed ? 1 : 0, c.appliedCount());
        }
    }

    @Test public void rejectedUnrelatedOrShortCircuitGetterDoesNotSpendBudget() {
        ContrastController c = new ContrastController(100, 1);
        long epoch = enabled(c);
        for (int i = 0; i < 100; i++) {
            assertFalse(c.permit(102, epoch, false, false, false, false, true, false));
        }
        assertEquals(0, c.permitCount());
        apply(c, 103, epoch);
        assertEquals(1, c.appliedCount());
        assertEquals(RECOVERY, c.mode());
    }

    @Test public void permitIsReservationAndAppliedRequiresExactlyOneReservation() {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        assertFalse(c.overrideApplied(102, epoch));
        assertTrue(permit(c, 102, epoch));
        assertEquals(1, c.permitCount());
        assertEquals(0, c.appliedCount());
        assertTrue(c.status(102).contains("contrastPending=true"));
        assertTrue(c.overrideApplied(102, epoch));
        assertFalse(c.overrideApplied(102, epoch));
        assertEquals(1, c.appliedCount());
        assertTrue(c.status(102).contains("contrastPending=false"));
    }

    @Test public void unacknowledgedPermitCannotBeReusedForUnlimitedOverrides() {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        assertTrue(permit(c, 102, epoch));
        assertFalse(permit(c, 103, epoch));
        assertEquals(RECOVERY, c.mode());
        assertTrue(c.needsRecovery());
        assertFalse(c.overrideApplied(103, epoch));
        assertEquals(0, c.appliedCount());
        assertEquals(1, c.permitCount());
    }

    @Test public void hardDeadlineUsesWholeExperimentAndCannotBeExtendedByEnable() {
        ContrastController c = new ContrastController(10, 20);
        long epoch = baseline(c);
        assertTrue(c.enable(109));
        apply(c, 109, epoch);
        assertFalse(permit(c, 110, epoch));
        assertEquals(RECOVERY, c.mode());
        assertTrue(c.needsRecovery());
        assertFalse(c.recoveryProved());
        assertEquals(1, c.appliedCount());
    }

    @Test public void baselineAlsoExpiresExactlyAtTheDeadline() {
        ContrastController c = new ContrastController(10, 20);
        baseline(c);
        assertTrue(c.status(109).contains("contrastMode=BASELINE"));
        assertFalse(c.enable(110));
        assertEquals(RECOVERY, c.mode());
        assertTrue(c.needsRecovery());
        assertEquals(0, c.appliedCount());
    }

    @Test public void statusTimeoutRevokesAnOutstandingPermit() {
        ContrastController c = new ContrastController(10, 20);
        long epoch = enabled(c);
        assertTrue(permit(c, 109, epoch));
        assertTrue(c.status(110).contains("contrastPending=false"));
        assertFalse(c.overrideApplied(110, epoch));
        assertEquals(RECOVERY, c.mode());
    }

    @Test public void productionDeadlineIsFrozenAtOneHundredEightySeconds() {
        assertEquals(180_000, MAX_DURATION_MS);
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        apply(c, 180_099, epoch);
        assertFalse(permit(c, 180_100, epoch));
        assertTrue(c.needsRecovery());
    }

    @Test public void backwardTimestampCannotExtendElapsedTimeOrDefeatTimeout() {
        ContrastController c = new ContrastController(10, 20);
        long epoch = enabled(c);
        c.status(109);
        apply(c, 105, epoch);
        assertTrue(c.status(104).contains("contrastElapsedMs=9"));
        assertFalse(permit(c, 110, epoch));
        assertFalse(permit(c, 101, epoch));
        assertEquals(RECOVERY, c.mode());
    }

    @Test public void negativeTimeFailsClosedAndDoesNotLeakIntoStatus() {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        assertFalse(permit(c, -1, epoch));
        assertEquals(RECOVERY, c.mode());
        assertTrue(c.status(102).contains("contrastReason=ERROR"));
        assertTrue(c.needsRecovery());
    }

    @Test public void timestampsNearLongMaximumDoNotOverflowDeadlineArithmetic() {
        ContrastController c = new ContrastController(10, 20);
        long epoch = c.start(Long.MAX_VALUE - 10, BASELINE);
        assertTrue(c.enable(Long.MAX_VALUE - 9));
        apply(c, Long.MAX_VALUE - 1, epoch);
        assertFalse(permit(c, Long.MAX_VALUE, epoch));
        assertTrue(c.status(Long.MAX_VALUE).contains("contrastElapsedMs=10"));
    }

    @Test public void maximumSpanExpiresWithoutNegativeElapsedOverflow() {
        ContrastController c = new ContrastController();
        long epoch = c.start(0, BASELINE);
        assertTrue(c.enable(0));
        assertFalse(permit(c, Long.MAX_VALUE, epoch));
        assertTrue(c.status(Long.MAX_VALUE).contains("contrastElapsedMs=" + Long.MAX_VALUE));
    }

    @Test public void exactOverrideLimitAppliesThenImmediatelyRevokesFurtherPermission() {
        ContrastController c = new ContrastController(100, 3);
        long epoch = enabled(c);
        for (int i = 0; i < 3; i++) apply(c, 102 + i, epoch);
        assertEquals(3, c.appliedCount());
        assertEquals(3, c.permitCount());
        assertEquals(RECOVERY, c.mode());
        assertTrue(c.status(105).contains("contrastReason=LIMIT_OVERRIDES"));
        for (int i = 0; i < 50; i++) assertFalse(permit(c, 105, epoch));
        assertFalse(c.overrideApplied(105, epoch));
        assertEquals(3, c.appliedCount());
        assertTrue(c.needsRecovery());
    }

    @Test public void productionTwoThousandLimitIsActuallyExercised() {
        assertEquals(2_000, MAX_OVERRIDES);
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        for (int i = 0; i < 2_000; i++) apply(c, 102, epoch);
        assertEquals(2_000, c.appliedCount());
        assertEquals(RECOVERY, c.mode());
        assertFalse(permit(c, 102, epoch));
    }

    @Test public void stopRevokesPendingPermissionBeforeAnyRecoveryProof() {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        assertTrue(permit(c, 102, epoch));
        assertTrue(c.beginRecovery(103, "USER_STOP"));
        assertFalse(c.overrideApplied(103, epoch));
        assertFalse(permit(c, 103, epoch));
        assertFalse(c.recoveryProved());
        assertTrue(c.needsRecovery());
        assertTrue(c.status(103).contains("contrastPending=false"));
    }

    @Test public void everyRecognizedStoppingCauseRequiresRestorationProof() {
        for (String reason : new String[]{"USER_STOP", "USER_RECOVERY", "TIMEOUT",
                "LIMIT_OVERRIDES", "LIMIT_ROWS", "LIMIT_BYTES", "LIMIT_LINE", "ERROR",
                "AMBIGUOUS_HOME", "IDENTITY_CHANGED", "UNHOOK_REQUESTED"}) {
            ContrastController c = new ContrastController();
            long epoch = enabled(c);
            apply(c, 102, epoch);
            assertTrue(c.beginRecovery(103, reason));
            assertFalse(permit(c, 104, epoch));
            assertTrue(c.needsRecovery());
            assertTrue(c.status(104).contains("contrastReason=" + reason + " "));
        }
    }

    @Test public void arbitraryStopTextIsNotExportedAndBecomesError() {
        for (String reason : new String[]{null, "", "private/path\nURI=secret", "STOPPED"}) {
            ContrastController c = new ContrastController();
            enabled(c);
            c.beginRecovery(102, reason);
            String status = c.status(102);
            assertTrue(status.contains("contrastReason=ERROR"));
            assertFalse(status.contains("private"));
            assertFalse(status.contains("secret"));
            assertFalse(status.contains("\n"));
        }
    }

    @Test public void invalidStartDuringEnableRevokesExistingAuthorization() {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        assertTrue(permit(c, 102, epoch));
        assertEquals(0, c.start(103, ENABLE));
        assertEquals(epoch, c.epoch());
        assertFalse(c.overrideApplied(103, epoch));
        assertEquals(RECOVERY, c.mode());
        assertTrue(c.needsRecovery());
    }

    @Test public void allEightRecoveryConditionCombinationsRequireAllThreeFacts() {
        for (int bits = 0; bits < 8; bits++) {
            ContrastController c = new ContrastController();
            long epoch = enabled(c);
            apply(c, 102, epoch);
            c.beginRecovery(103, "USER_STOP");
            assertEquals("RECOVERY_BITS=" + bits, bits == 7,
                    c.observeRecovery(104, epoch, (bits & 1) != 0,
                            (bits & 2) != 0, (bits & 4) != 0));
            assertEquals(bits == 7, c.recoveryProved());
            assertEquals(bits != 7, c.needsRecovery());
        }
    }

    @Test public void recoveryCannotBeProvedWhileBaselineOrEnabled() {
        ContrastController c = new ContrastController();
        long epoch = baseline(c);
        assertFalse(c.observeRecovery(101, epoch, true, true, true));
        c.enable(102);
        assertFalse(c.observeRecovery(103, epoch, true, true, true));
        assertFalse(c.recoveryProved());
    }

    @Test public void oldPopulateTimeAndWrongWrapperCannotProveRecovery() {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        apply(c, 102, epoch);
        c.beginRecovery(110, "USER_STOP");
        assertFalse(c.observeRecovery(109, epoch, true, true, true));
        assertFalse(c.observeRecovery(111, epoch, false, true, true));
        assertTrue(c.needsRecovery());
        assertTrue(c.observeRecovery(112, epoch, true, true, true));
        assertTrue(c.recoveryProved());
        assertFalse(c.needsRecovery());
        assertEquals(RECOVERY, c.mode());
        assertFalse(permit(c, 112, epoch));
    }

    @Test public void fieldMutationCannotBeWashedAwayByLaterIntactObservation() {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        c.beginRecovery(102, "USER_STOP");
        assertFalse(c.observeRecovery(103, epoch, true, true, false));
        assertFalse(c.observeRecovery(104, epoch, true, true, true));
        assertTrue(c.needsRecovery());
        assertFalse(c.recoveryProved());
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(105, epoch));
    }

    @Test public void contradictoryLaterRecoveryEvidenceWithdrawsEarlierProof() {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        c.beginRecovery(102, "USER_STOP");
        assertTrue(c.observeRecovery(103, epoch, true, true, true));
        assertFalse(c.observeRecovery(104, epoch, true, false, true));
        assertTrue(c.needsRecovery());
        assertFalse(c.recoveryProved());
        assertTrue(c.observeRecovery(105, epoch, true, true, true));
        assertFalse(c.observeRecovery(106, epoch, true, true, false));
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(107, epoch));
    }

    @Test public void repeatedStopDoesNotResetRecoveryOrReplaceFirstReason() {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        c.beginRecovery(102, "IDENTITY_CHANGED");
        assertTrue(c.beginRecovery(110, "USER_STOP"));
        assertTrue(c.status(110).contains("contrastReason=IDENTITY_CHANGED"));
        assertTrue(c.observeRecovery(105, epoch, true, true, true));
        assertTrue(c.beginRecovery(111, "USER_STOP"));
        assertTrue(c.recoveryProved());
    }

    @Test public void newErrorOrIdentityChangeInvalidatesPreviouslyProvedRecovery() {
        for (String reason : new String[]{"ERROR", "IDENTITY_CHANGED", "AMBIGUOUS_HOME"}) {
            ContrastController c = new ContrastController();
            long epoch = enabled(c);
            c.beginRecovery(102, "USER_STOP");
            assertTrue(c.observeRecovery(103, epoch, true, true, true));
            assertTrue(c.beginRecovery(110, reason));
            assertFalse(c.recoveryProved());
            assertTrue(c.needsRecovery());
            assertFalse(c.observeRecovery(109, epoch, true, true, true));
            assertTrue(c.observeRecovery(111, epoch, true, true, true));
            assertEquals(FINAL_CONFIRMED, c.finalizeRecovery(112, epoch));
        }
    }

    @Test public void invalidClockDuringRecoveryWithdrawsPreviousProof() {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        c.beginRecovery(102, "USER_STOP");
        assertTrue(c.observeRecovery(103, epoch, true, true, true));
        c.status(-1);
        assertFalse(c.recoveryProved());
        assertTrue(c.needsRecovery());
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(104, epoch));
    }

    @Test public void timeoutDoesNotInventProofButAllowsNaturalRecoveryAfterDeadline() {
        ContrastController c = new ContrastController(10, 20);
        long epoch = enabled(c);
        apply(c, 102, epoch);
        c.status(110);
        assertTrue(c.needsRecovery());
        assertFalse(c.recoveryProved());
        assertTrue(c.observeRecovery(120, epoch, true, true, true));
        assertEquals(FINAL_CONFIRMED, c.finalizeRecovery(121, epoch));
    }

    @Test public void unconfirmedFinalizationDisarmsButPermanentlyPreservesObligation() {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        apply(c, 102, epoch);
        c.beginRecovery(103, "USER_STOP");
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(123, epoch));
        assertEquals(DISARMED, c.mode());
        assertTrue(c.needsRecovery());
        assertFalse(c.recoveryProved());
        assertEquals(0, c.start(124, BASELINE));
        assertFalse(c.enable(124));
        assertFalse(permit(c, 124, epoch));
        assertFalse(c.observeRecovery(124, epoch, true, true, true));
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(125, epoch));
        assertEquals(1, c.appliedCount());
    }

    @Test public void noOverrideIsStillNotAProofOfNaturalRecovery() {
        ContrastController c = new ContrastController();
        long epoch = baseline(c);
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(101, epoch));
        assertEquals(0, c.appliedCount());
        assertTrue(c.needsRecovery());
        assertEquals(0, c.start(102, BASELINE));
    }

    @Test public void directFinalizationFirstRevokesAnOutstandingPermission() {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        assertTrue(permit(c, 102, epoch));
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(103, epoch));
        assertFalse(c.overrideApplied(103, epoch));
        assertTrue(c.needsRecovery());
    }

    @Test public void confirmedFinalizationAllowsOnlyANewBaselineWithNewCounters() {
        ContrastController c = new ContrastController();
        long old = enabled(c);
        apply(c, 102, old);
        confirmed(c, 103, old);
        assertEquals(DISARMED, c.mode());
        assertFalse(c.needsRecovery());
        assertTrue(c.recoveryProved());
        assertFalse(c.observeRecovery(104, old, true, false, false));
        assertTrue(c.recoveryProved());
        long next = c.start(200, BASELINE);
        assertEquals(old + 1, next);
        assertEquals(0, c.appliedCount());
        assertEquals(0, c.permitCount());
        assertFalse(c.recoveryProved());
        assertEquals(BASELINE, c.mode());
    }

    @Test public void frozenFinalResultAndElapsedAreNotRewrittenByLaterStatusOrStop() {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        confirmed(c, 103, epoch);
        String finalStatus = c.status(103);
        assertFalse(c.beginRecovery(Long.MAX_VALUE, "ERROR"));
        assertEquals(FINAL_CONFIRMED, c.finalizeRecovery(-1, epoch));
        assertEquals(finalStatus, c.status(Long.MAX_VALUE));
        assertEquals(finalStatus, c.status(-1));
    }

    @Test public void oldFutureZeroAndNegativeEpochsCannotAffectNewExperiment() {
        ContrastController c = new ContrastController(100, 20);
        long old = enabled(c);
        confirmed(c, 103, old);
        long current = c.start(200, BASELINE);
        c.enable(201);
        String before = c.status(201);
        for (long stale : new long[]{old, 0, -1, current + 1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertFalse(permit(c, Long.MAX_VALUE, stale));
            assertFalse(c.overrideApplied(-1, stale));
            assertFalse(c.observeRecovery(Long.MAX_VALUE, stale, true, true, false));
            assertEquals("STALE_EPOCH", c.finalizeRecovery(Long.MAX_VALUE, stale));
            assertEquals(before, c.status(201));
        }
        apply(c, 202, current);
        assertEquals(1, c.appliedCount());
    }

    @Test public void staleAcknowledgementCannotConsumeNewPendingPermit() {
        ContrastController c = new ContrastController();
        long old = enabled(c);
        confirmed(c, 103, old);
        long next = c.start(200, BASELINE);
        c.enable(201);
        assertTrue(permit(c, 202, next));
        assertFalse(c.overrideApplied(202, old));
        assertTrue(c.overrideApplied(202, next));
        assertEquals(1, c.appliedCount());
    }

    @Test public void maximumEpochIsUsableButNeverWrapsOrReuses() {
        ContrastController c = new ContrastController(100, 20, Long.MAX_VALUE - 1);
        long epoch = c.start(100, BASELINE);
        assertEquals(Long.MAX_VALUE, epoch);
        c.enable(101);
        apply(c, 102, epoch);
        confirmed(c, 103, epoch);
        assertEquals(0, c.start(200, BASELINE));
        assertEquals(Long.MAX_VALUE, c.epoch());
        assertEquals(DISARMED, c.mode());
        assertFalse(permit(c, 201, Long.MIN_VALUE));
        assertTrue(c.status(201).contains("contrastReason=EPOCH_EXHAUSTED"));
    }

    @Test public void invalidConstructorLimitsAndNegativeInitialEpochAreRejected() {
        for (long[] limits : new long[][]{{0, 1}, {-1, 1}, {180_001, 1},
                {1, 0}, {1, -1}, {1, 2_001}, {Long.MAX_VALUE, Long.MAX_VALUE}}) {
            try {
                new ContrastController(limits[0], limits[1]);
                fail("INVALID_LIMIT_ACCEPTED");
            } catch (IllegalArgumentException expected) {
                assertEquals("INVALID_LIMIT", expected.getMessage());
            }
        }
        try {
            new ContrastController(1, 1, -1);
            fail("INVALID_EPOCH_ACCEPTED");
        } catch (IllegalArgumentException expected) {
            assertEquals("INVALID_EPOCH", expected.getMessage());
        }
    }

    @Test public void anotherThreadCannotAcknowledgeAReservedPermit() throws Exception {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        assertTrue(permit(c, 102, epoch));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertFalse(executor.submit(() -> c.overrideApplied(102, epoch)).get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertTrue(c.overrideApplied(102, epoch));
        assertEquals(1, c.appliedCount());
    }

    @Test public void pendingWorkerCannotApplyAfterStopWinsTheRace() throws Exception {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        CountDownLatch reserved = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> delayed = executor.submit(() -> {
                assertTrue(permit(c, 102, epoch));
                reserved.countDown();
                assertTrue(stopped.await(5, TimeUnit.SECONDS));
                return c.overrideApplied(103, epoch);
            });
            assertTrue(reserved.await(5, TimeUnit.SECONDS));
            c.beginRecovery(103, "USER_STOP");
            stopped.countDown();
            assertFalse(delayed.get(5, TimeUnit.SECONDS));
            assertEquals(0, c.appliedCount());
            assertTrue(c.needsRecovery());
        } finally {
            stopped.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test public void delayedOldWorkerCannotFinalizeOrModifyNewExperiment() throws Exception {
        ContrastController c = new ContrastController();
        long old = enabled(c);
        CountDownLatch delayedEntered = new CountDownLatch(1);
        CountDownLatch restarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> delayed = executor.submit(() -> {
                delayedEntered.countDown();
                try {
                    assertTrue(restarted.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                assertFalse(permit(c, Long.MAX_VALUE, old));
                assertFalse(c.overrideApplied(-1, old));
                assertEquals("STALE_EPOCH", c.finalizeRecovery(Long.MAX_VALUE, old));
            });
            assertTrue(delayedEntered.await(5, TimeUnit.SECONDS));
            confirmed(c, 103, old);
            long current = c.start(200, BASELINE);
            c.enable(201);
            restarted.countDown();
            delayed.get(5, TimeUnit.SECONDS);
            assertEquals(ENABLE, c.mode());
            apply(c, 202, current);
        } finally {
            restarted.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test public void concurrentAtomicOverridesCannotExceedProductionBudget() throws Exception {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        concurrently(12, worker -> {
            for (int i = 0; i < 300; i++) {
                synchronized (c) {
                    if (permit(c, 102, epoch)) assertTrue(c.overrideApplied(102, epoch));
                }
            }
        });
        assertEquals(2_000, c.appliedCount());
        assertEquals(2_000, c.permitCount());
        assertEquals(RECOVERY, c.mode());
        assertTrue(c.needsRecovery());
    }

    @Test public void concurrentStartHasExactlyOneWinnerWithoutDiscardingExperiment() throws Exception {
        ContrastController c = new ContrastController();
        List<Long> winners = java.util.Collections.synchronizedList(new ArrayList<>());
        concurrently(16, worker -> {
            long epoch = c.start(100, BASELINE);
            if (epoch != 0) winners.add(epoch);
        });
        assertEquals(1, winners.size());
        assertEquals(1, c.epoch());
        assertEquals(BASELINE, c.mode());
    }

    @Test public void concurrentStopFinalizationNeverClaimsUnobservedRecovery() throws Exception {
        ContrastController c = new ContrastController();
        long epoch = enabled(c);
        apply(c, 102, epoch);
        concurrently(12, worker -> {
            if (worker % 3 == 0) c.beginRecovery(103, "USER_STOP");
            else if (worker % 3 == 1) c.finalizeRecovery(103, epoch);
            else assertFalse(permit(c, 103, 0));
        });
        assertEquals(DISARMED, c.mode());
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(104, epoch));
        assertTrue(c.needsRecovery());
        assertFalse(c.recoveryProved());
        assertEquals(0, c.start(105, BASELINE));
    }

    @Test public void seededStressAcrossOneHundredExperimentsKeepsSafetyInvariants() {
        Random random = new Random(0x4E1);
        ContrastController c = new ContrastController(100, 25);
        long prior = 0;
        for (int experiment = 0; experiment < 100; experiment++) {
            long start = 1_000L * (experiment + 1);
            long epoch = c.start(start, BASELINE);
            assertEquals(prior + 1, epoch);
            assertTrue(c.enable(start + 1));
            for (int i = 2; i < 90; i++) {
                boolean qualified = random.nextBoolean();
                if (c.permit(start + i, epoch, qualified, true, true, true, false, true)) {
                    assertTrue(c.overrideApplied(start + i, epoch));
                }
                assertTrue(c.appliedCount() <= c.permitCount());
                assertTrue(c.permitCount() <= 25);
                assertFalse(c.observeRecovery(start + i, prior, true, true, true));
                if (RECOVERY.equals(c.mode())) assertFalse(permit(c, start + i, epoch));
            }
            assertTrue(c.beginRecovery(start + 90, "USER_STOP"));
            assertTrue(c.observeRecovery(start + 91, epoch, true, true, true));
            assertEquals(FINAL_CONFIRMED, c.finalizeRecovery(start + 92, epoch));
            assertFalse(c.needsRecovery());
            prior = epoch;
        }
    }

    private interface Work { void run(int worker); }

    private static void concurrently(int workers, Work work) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                final int worker = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                    work.run(worker);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
