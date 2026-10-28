package personal.wallpaper.os4.e1.core;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static personal.wallpaper.os4.e1.core.DailyController.*;

public class DailyControllerTest {
    private static long baseline(DailyController controller) {
        long epoch = controller.start(100, BASELINE);
        assertTrue(epoch > 0);
        return epoch;
    }

    private static long enabled(DailyController controller) {
        long epoch = baseline(controller);
        assertTrue(controller.enable(101));
        return epoch;
    }

    private static boolean permit(DailyController controller, long now, long epoch) {
        return controller.permit(now, epoch, true, true, true, true, false, true);
    }

    private static void apply(DailyController controller, long now, long epoch) {
        assertTrue(permit(controller, now, epoch));
        assertTrue(controller.overrideApplied(now, epoch));
    }

    private static void confirmed(DailyController controller, long now, long epoch) {
        assertTrue(controller.beginRecovery(now, "USER_RECOVERY"));
        assertTrue(controller.observeRecovery(now, epoch, true, true, true));
        assertEquals(FINAL_CONFIRMED, controller.finalizeRecovery(now, epoch));
    }

    @Test public void freshProcessStartsOffWithNoPermitOrProof() {
        DailyController c = new DailyController();
        assertEquals(DISARMED, c.mode());
        assertEquals(0, c.epoch());
        assertEquals(NO_DEADLINE, c.deadline());
        assertEquals(0, c.activeDurationMillis(0));
        assertFalse(c.needsRecovery());
        assertFalse(c.recoveryProved());
        assertFalse(c.enable(0));
        assertFalse(permit(c, 0, 0));
        assertFalse(c.overrideApplied(0, 0));
        assertFalse(c.observeRecovery(0, 0, true, true, true));
        assertFalse(c.beginRecovery(0, "USER_STOP"));
        assertEquals("STALE_EPOCH", c.finalizeRecovery(0, 0));
    }

    @Test public void newInstanceDoesNotInheritEnabledStateOrRecoveryLock() {
        DailyController first = new DailyController();
        long epoch = enabled(first);
        apply(first, 102, epoch);
        assertEquals(FINAL_UNCONFIRMED, first.finalizeRecovery(103, epoch));
        DailyController replacement = new DailyController();
        assertEquals(DISARMED, replacement.mode());
        assertFalse(replacement.needsRecovery());
        assertEquals(0, replacement.epoch());
        assertFalse(permit(replacement, 104, epoch));
    }

    @Test public void exactBaselineAndNonnegativeStartAreRequired() {
        DailyController c = new DailyController();
        for (String mode : new String[]{null, "", "baseline", ENABLE, RECOVERY,
                DISARMED, "BASELINE\n", "BASELINE secret"}) {
            assertEquals(0, c.start(10, mode));
            assertEquals(DISARMED, c.mode());
            assertEquals(0, c.epoch());
        }
        assertEquals(0, c.start(-1, BASELINE));
        assertEquals(1, c.start(0, BASELINE));
    }

    @Test public void productionPolicyIsExplicitlyPermanentAndKeepsTwoThousandPerMinute() {
        DailyController c = new DailyController();
        assertTrue(c instanceof SessionPolicy);
        assertEquals(43_200_000L, MAX_DURATION_MS);
        assertEquals(2_000, MAX_RATE_OVERRIDES);
        assertEquals(60_000, RATE_WINDOW_MS);
        assertTrue(c.isPermanent());
        assertEquals(0, c.durationLimitMillis());
        baseline(c);
        assertEquals(NO_DEADLINE, c.deadline());
        String status = c.status(100);
        assertTrue(status.contains("dailyPolicy=PERMANENT "));
        assertTrue(status.contains("dailyDurationMs=0 dailyDeadline=-1 "));
        assertTrue(status.contains("dailyRateLimit=2000 "));
        assertTrue(status.contains("dailyRateWindowMs=60000 "));
    }

    @Test public void baselineDoesNotOverrideAndEnableDoesNotResetEpochOrDeadline() {
        DailyController c = new DailyController();
        long epoch = baseline(c);
        long deadline = c.deadline();
        assertFalse(permit(c, 100, epoch));
        assertFalse(c.overrideApplied(100, epoch));
        assertTrue(c.enable(101));
        assertFalse(c.enable(102));
        assertEquals(epoch, c.epoch());
        assertEquals(deadline, c.deadline());
        apply(c, 103, epoch);
        assertEquals(1, c.appliedCount());
    }

    @Test public void duplicateStartPreservesCountersAndHardDeadline() {
        DailyController c = new DailyController(10, 2, RATE_WINDOW_MS);
        long epoch = enabled(c);
        apply(c, 102, epoch);
        assertEquals(0, c.start(108, BASELINE));
        assertEquals(epoch, c.epoch());
        assertEquals(110, c.deadline());
        assertEquals(1, c.appliedCount());
        assertFalse(permit(c, 110, epoch));
        assertTrue(c.status(110).contains("dailyReason=TIMEOUT "));
    }

    @Test public void allAuthorizationCombinationsRequireExactlyTheSixFacts() {
        for (int bits = 0; bits < 64; bits++) {
            DailyController c = new DailyController();
            long epoch = enabled(c);
            boolean allowed = c.permit(102, epoch, (bits & 1) != 0, (bits & 2) != 0,
                    (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0, (bits & 32) != 0);
            assertEquals("CONDITION_BITS=" + bits, bits == 47, allowed);
            assertEquals(allowed ? 1 : 0, c.permitCount());
            assertEquals(allowed, c.overrideApplied(102, epoch));
            assertEquals(allowed ? 1 : 0, c.appliedCount());
        }
    }

    @Test public void unrelatedAndShortCircuitCallsDoNotConsumeRateBudget() {
        DailyController c = new DailyController(MAX_DURATION_MS, 1, RATE_WINDOW_MS);
        long epoch = enabled(c);
        for (int i = 0; i < 3_000; i++) {
            assertFalse(c.permit(102, epoch, false, false, false, false, true, false));
        }
        apply(c, 102, epoch);
        assertEquals(1, c.appliedCount());
        assertEquals(ENABLE, c.mode());
    }

    @Test public void oneReservationCanOnlyBeAcknowledgedOnce() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        assertFalse(c.overrideApplied(102, epoch));
        assertTrue(permit(c, 102, epoch));
        assertEquals(1, c.permitCount());
        assertEquals(0, c.appliedCount());
        assertTrue(c.status(102).contains("dailyPending=true "));
        assertTrue(c.overrideApplied(102, epoch));
        assertFalse(c.overrideApplied(102, epoch));
        assertEquals(1, c.appliedCount());
    }

    @Test public void missingAcknowledgementRevokesInsteadOfIssuingAnotherPermit() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        assertTrue(permit(c, 102, epoch));
        assertFalse(permit(c, 103, epoch));
        assertEquals(RECOVERY, c.mode());
        assertTrue(c.needsRecovery());
        assertFalse(c.overrideApplied(103, epoch));
        assertTrue(c.status(103).contains("dailyReason=ERROR "));
        assertEquals(1, c.permitCount());
        assertEquals(0, c.appliedCount());
    }

    @Test public void exactRateLimitIsAllowedAndTheNextRequestImmediatelyRevokes() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        for (int i = 0; i < MAX_RATE_OVERRIDES; i++) apply(c, 102, epoch);
        assertEquals(ENABLE, c.mode());
        assertEquals(2_000, c.appliedCount());
        assertFalse(permit(c, 102, epoch));
        assertEquals(RECOVERY, c.mode());
        assertTrue(c.status(102).contains("dailyReason=LIMIT_RATE "));
        assertTrue(c.needsRecovery());
        assertEquals(2_000, c.permitCount());
    }

    @Test public void windowBoundaryExpiresOnlyAtExactlySixtySeconds() {
        DailyController c = new DailyController(MAX_DURATION_MS, 1, RATE_WINDOW_MS);
        long epoch = enabled(c);
        apply(c, 102, epoch);
        c.status(60_101);
        apply(c, 60_102, epoch);
        assertEquals(2, c.appliedCount());
        assertEquals(ENABLE, c.mode());

        DailyController early = new DailyController(MAX_DURATION_MS, 1, RATE_WINDOW_MS);
        long earlyEpoch = enabled(early);
        apply(early, 102, earlyEpoch);
        assertFalse(permit(early, 60_101, earlyEpoch));
        assertEquals(RECOVERY, early.mode());
    }

    @Test public void slidingWindowDoesNotPermitDoubleBurstAcrossFixedMinuteBoundary() {
        DailyController c = new DailyController(MAX_DURATION_MS, 2, RATE_WINDOW_MS);
        long epoch = enabled(c);
        apply(c, 60_099, epoch);
        apply(c, 60_100, epoch);
        assertFalse(permit(c, 60_101, epoch));
        assertEquals(RECOVERY, c.mode());
        assertEquals(2, c.appliedCount());
    }

    @Test public void slidingRingExpiresIndividualEventsAndWrapsWithoutLosingRecentOnes() {
        DailyController c = new DailyController(MAX_DURATION_MS, 3, RATE_WINDOW_MS);
        long epoch = enabled(c);
        apply(c, 102, epoch);
        apply(c, 20_102, epoch);
        apply(c, 40_102, epoch);
        apply(c, 60_102, epoch);
        apply(c, 80_102, epoch);
        apply(c, 100_102, epoch);
        assertEquals(6, c.appliedCount());
        assertFalse(permit(c, 100_103, epoch));
        assertEquals(RECOVERY, c.mode());
    }

    @Test public void cumulativeOverridesCanExceedTheOldTwoThousandSessionCap() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        for (int minute = 0; minute < 4; minute++) {
            long now = 102 + minute * RATE_WINDOW_MS;
            for (int i = 0; i < MAX_RATE_OVERRIDES; i++) apply(c, now, epoch);
            assertEquals(ENABLE, c.mode());
        }
        assertEquals(8_000, c.appliedCount());
        assertEquals(8_000, c.permitCount());
    }

    @Test public void backwardClockCannotExpireReservationsOrExtendDeadline() {
        DailyController c = new DailyController(MAX_DURATION_MS, 2, RATE_WINDOW_MS);
        long epoch = enabled(c);
        apply(c, 20_000, epoch);
        c.status(70_000);
        apply(c, 21_000, epoch);
        assertFalse(permit(c, 79_999, epoch));
        assertEquals(2, c.appliedCount());
        assertEquals(RECOVERY, c.mode());
    }

    @Test public void hardDeadlineCountsBaselineAndIsNotRenewedByLateEnable() {
        DailyController c = new DailyController(10, 2, RATE_WINDOW_MS);
        long epoch = baseline(c);
        assertTrue(c.enable(109));
        apply(c, 109, epoch);
        assertFalse(permit(c, 110, epoch));
        assertEquals(RECOVERY, c.mode());
        assertEquals(10, c.activeDurationMillis());
        assertFalse(c.enable(111));
    }

    @Test public void baselineAloneExpiresAtTheSameFixedDeadline() {
        DailyController c = new DailyController(10, 2, RATE_WINDOW_MS);
        baseline(c);
        assertFalse(c.enable(110));
        assertEquals(RECOVERY, c.mode());
        assertEquals(0, c.appliedCount());
        assertTrue(c.needsRecovery());
    }

    @Test public void productionSessionKeepsRunningAtAndBeyondTheOldTwelveHourBoundary() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        apply(c, 180_101, epoch);
        apply(c, 43_200_099, epoch);
        apply(c, 43_200_100, epoch);
        apply(c, 46_800_100, epoch);
        assertEquals(ENABLE, c.mode());
        assertEquals(46_800_000, c.activeDurationMillis());
        assertFalse(c.status(46_800_100).contains("dailyReason=TIMEOUT "));
        assertEquals(epoch, c.epoch());
        assertEquals(NO_DEADLINE, c.deadline());
    }

    @Test public void statusAndDurationChecksRevokePendingPermitAtDeadline() {
        for (boolean durationQuery : new boolean[]{false, true}) {
            DailyController c = new DailyController(10, 2, RATE_WINDOW_MS);
            long epoch = enabled(c);
            assertTrue(permit(c, 109, epoch));
            if (durationQuery) assertEquals(10, c.activeDurationMillis(110));
            else c.status(110);
            assertFalse(c.overrideApplied(110, epoch));
            assertEquals(RECOVERY, c.mode());
            assertEquals(0, c.appliedCount());
        }
    }

    @Test public void hugeClockJumpExpiresWithoutOverflowAndCapsActiveDuration() {
        DailyController c = new DailyController(MAX_DURATION_MS, MAX_RATE_OVERRIDES, RATE_WINDOW_MS);
        long epoch = enabled(c);
        assertFalse(permit(c, Long.MAX_VALUE, epoch));
        assertEquals(MAX_DURATION_MS, c.activeDurationMillis());
        assertTrue(c.status(Long.MAX_VALUE).contains("dailyElapsedMs=" + (Long.MAX_VALUE - 100)));
    }

    @Test public void overflowingDeadlineSaturatesAndFailsClosedAtLongMaximum() {
        DailyController c = new DailyController(10, 2, RATE_WINDOW_MS);
        long epoch = c.start(Long.MAX_VALUE - 5, BASELINE);
        assertEquals(Long.MAX_VALUE, c.deadline());
        assertTrue(c.enable(Long.MAX_VALUE - 4));
        apply(c, Long.MAX_VALUE - 1, epoch);
        assertFalse(permit(c, Long.MAX_VALUE, epoch));
        assertEquals(RECOVERY, c.mode());
        assertEquals(5, c.activeDurationMillis());
    }

    @Test public void negativeClockFailsClosedAndNeverAppearsAsElapsedTime() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        assertFalse(permit(c, -1, epoch));
        assertEquals(RECOVERY, c.mode());
        assertTrue(c.status(102).contains("dailyReason=ERROR "));
        assertFalse(c.status(102).contains("dailyElapsedMs=-"));
    }

    @Test public void countersSaturateAndDoNotDisableAValidRateWindow() throws Exception {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        setCounter(c, "permits", Long.MAX_VALUE - 1);
        setCounter(c, "applied", Long.MAX_VALUE - 1);
        apply(c, 102, epoch);
        apply(c, 103, epoch);
        assertEquals(Long.MAX_VALUE, c.permitCount());
        assertEquals(Long.MAX_VALUE, c.appliedCount());
        assertEquals(ENABLE, c.mode());
        c.beginRecovery(104, "USER_STOP");
        setCounter(c, "recoveryObservations", Long.MAX_VALUE);
        assertTrue(c.observeRecovery(105, epoch, true, true, true));
        assertTrue(c.status(105).contains("dailyRecoveryObservations=" + Long.MAX_VALUE));
    }

    @Test public void userStopRevokesPendingBeforeAnyNaturalRecoveryProof() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        assertTrue(permit(c, 102, epoch));
        assertTrue(c.beginRecovery(103, "USER_STOP"));
        assertFalse(c.overrideApplied(103, epoch));
        assertFalse(permit(c, 103, epoch));
        assertFalse(c.recoveryProved());
        assertTrue(c.needsRecovery());
        assertTrue(c.status(103).contains("dailyPending=false "));
    }

    @Test public void arbitraryReasonTextIsReplacedWithControlledError() {
        for (String reason : new String[]{null, "", "STOPPED", "private/path\nsecret"}) {
            DailyController c = new DailyController();
            enabled(c);
            c.beginRecovery(102, reason);
            String status = c.status(102);
            assertTrue(status.contains("dailyReason=ERROR "));
            assertFalse(status.contains("private"));
            assertFalse(status.contains("secret"));
            assertFalse(status.contains("\n"));
        }
    }

    @Test public void recognizedRecoveryReasonsArePreservedWithoutRenewing() {
        for (String reason : new String[]{"USER_STOP", "USER_RECOVERY", "TIMEOUT", "LIMIT_RATE",
                "LIMIT_OVERRIDES", "LIMIT_ROWS", "LIMIT_BYTES", "LIMIT_LINE", "ERROR",
                "AMBIGUOUS_HOME", "IDENTITY_CHANGED", "UNHOOK_REQUESTED"}) {
            DailyController c = new DailyController();
            long epoch = enabled(c);
            apply(c, 102, epoch);
            assertTrue(c.beginRecovery(103, reason));
            assertFalse(permit(c, 104, epoch));
            assertTrue(c.status(104).contains("dailyReason=" + reason + " "));
        }
    }

    @Test public void invalidStartWhileActiveRevokesWithoutChangingEpoch() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        assertTrue(permit(c, 102, epoch));
        assertEquals(0, c.start(103, ENABLE));
        assertEquals(epoch, c.epoch());
        assertEquals(RECOVERY, c.mode());
        assertFalse(c.overrideApplied(103, epoch));
    }

    @Test public void everyRecoveryConditionIsRequiredAndObservationIsNotEnable() {
        for (int bits = 0; bits < 8; bits++) {
            DailyController c = new DailyController();
            long epoch = enabled(c);
            assertFalse(c.observeRecovery(101, epoch, true, true, true));
            c.beginRecovery(102, "USER_STOP");
            assertEquals(bits == 7, c.observeRecovery(103, epoch,
                    (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0));
            assertEquals(bits == 7, c.recoveryProved());
            assertEquals(bits != 7, c.needsRecovery());
            assertFalse(c.enable(104));
            assertFalse(permit(c, 104, epoch));
        }
    }

    @Test public void baselineCannotProveRecoveryAndOldOrWrongWrapperIsRejected() {
        DailyController c = new DailyController();
        long epoch = baseline(c);
        assertFalse(c.observeRecovery(101, epoch, true, true, true));
        c.enable(102);
        c.beginRecovery(110, "USER_STOP");
        assertFalse(c.observeRecovery(109, epoch, true, true, true));
        assertFalse(c.observeRecovery(111, epoch, false, true, true));
        assertTrue(c.observeRecovery(112, epoch, true, true, true));
    }

    @Test public void originalFieldViolationIsStickyEvenAfterNormalObservations() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        c.beginRecovery(102, "USER_STOP");
        assertFalse(c.observeRecovery(103, epoch, true, true, false));
        assertFalse(c.observeRecovery(104, epoch, true, true, true));
        assertTrue(c.needsRecovery());
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(105, epoch));
        assertEquals(0, c.start(106, BASELINE));
    }

    @Test public void directFieldViolationWithdrawsProofAndStaysStickyWithoutFakeObservation() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        apply(c, 102, epoch);
        c.beginRecovery(103, "USER_STOP");
        assertTrue(c.observeRecovery(104, epoch, true, true, true));
        assertTrue(c.noteFieldViolation(105, epoch));
        assertFalse(c.recoveryProved());
        assertFalse(c.observeRecovery(106, epoch, true, true, true));
        assertTrue(c.status(106).contains("dailyFieldViolation=true "));
        assertTrue(c.status(106).contains("dailyRecoveryObservations=2 "));
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(107, epoch));
        assertEquals(0, c.start(108, BASELINE));
    }

    @Test public void fieldViolationDuringEnableRevokesPendingWithoutInventingProofOrObservation() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        assertTrue(permit(c, 102, epoch));
        assertTrue(c.noteFieldViolation(103, epoch));
        assertEquals(RECOVERY, c.mode());
        assertFalse(c.overrideApplied(104, epoch));
        assertFalse(c.recoveryProved());
        assertTrue(c.needsRecovery());
        assertEquals(0, c.appliedCount());
        assertTrue(c.status(104).contains("dailyRecoveryObservations=0 "));
        assertTrue(c.status(104).contains("dailyReason=ERROR "));
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(105, epoch));
    }

    @Test public void fieldViolationDuringBaselineStillRequiresUnconfirmedFinalization() {
        DailyController c = new DailyController();
        long epoch = baseline(c);
        assertTrue(c.noteFieldViolation(101, epoch));
        assertEquals(RECOVERY, c.mode());
        assertFalse(c.enable(102));
        assertFalse(c.observeRecovery(103, epoch, true, true, true));
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(104, epoch));
        assertEquals(0, c.permitCount());
    }

    @Test public void staleFieldViolationCannotTickOrTaintTheCurrentSession() {
        DailyController c = new DailyController(100, 2, RATE_WINDOW_MS);
        long old = enabled(c);
        confirmed(c, 103, old);
        long current = c.start(200, BASELINE);
        c.enable(201);
        String before = c.status(201);
        for (long stale : new long[]{old, 0, -1, current + 1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertFalse(c.noteFieldViolation(Long.MAX_VALUE, stale));
            assertFalse(c.noteFieldViolation(-1, stale));
            assertEquals(before, c.status(201));
        }
        apply(c, 202, current);
    }

    @Test public void noSessionOrFrozenFinalResultCannotBeChangedByFieldViolationCallback() {
        DailyController c = new DailyController();
        assertFalse(c.noteFieldViolation(0, 0));
        assertFalse(c.noteFieldViolation(0, 1));
        long epoch = enabled(c);
        confirmed(c, 103, epoch);
        String before = c.status(103);
        assertFalse(c.noteFieldViolation(Long.MAX_VALUE, epoch));
        assertFalse(c.noteFieldViolation(-1, epoch));
        assertEquals(before, c.status(103));
        assertFalse(c.needsRecovery());
    }

    @Test public void contradictoryDupEvidenceRevokesProofUntilFreshNaturalEvidence() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        c.beginRecovery(102, "USER_STOP");
        assertTrue(c.observeRecovery(103, epoch, true, true, true));
        assertFalse(c.observeRecovery(104, epoch, true, false, true));
        assertFalse(c.recoveryProved());
        assertTrue(c.observeRecovery(105, epoch, true, true, true));
        assertEquals(FINAL_CONFIRMED, c.finalizeRecovery(106, epoch));
    }

    @Test public void repeatedStopPreservesFirstReasonAndRecoveryStart() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        c.beginRecovery(102, "IDENTITY_CHANGED");
        c.beginRecovery(110, "USER_STOP");
        assertTrue(c.status(110).contains("dailyReason=IDENTITY_CHANGED "));
        assertTrue(c.observeRecovery(105, epoch, true, true, true));
        c.beginRecovery(111, "USER_STOP");
        assertTrue(c.recoveryProved());
    }

    @Test public void errorsAndIdentityChangesInvalidateProofAndRejectOlderWrappers() {
        for (String reason : new String[]{"ERROR", "IDENTITY_CHANGED", "AMBIGUOUS_HOME"}) {
            DailyController c = new DailyController();
            long epoch = enabled(c);
            c.beginRecovery(102, "USER_STOP");
            assertTrue(c.observeRecovery(103, epoch, true, true, true));
            c.beginRecovery(110, reason);
            assertFalse(c.recoveryProved());
            assertFalse(c.observeRecovery(109, epoch, true, true, true));
            assertTrue(c.observeRecovery(111, epoch, true, true, true));
            assertEquals(FINAL_CONFIRMED, c.finalizeRecovery(112, epoch));
        }
    }

    @Test public void invalidClockDuringRecoveryRevokesExistingProof() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        c.beginRecovery(102, "USER_STOP");
        assertTrue(c.observeRecovery(103, epoch, true, true, true));
        c.status(-1);
        assertFalse(c.recoveryProved());
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(104, epoch));
    }

    @Test public void activeDurationFreezesAtFirstRevocationAndCannotGrowDuringRecovery() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        assertEquals(10, c.activeDurationMillis(110));
        c.beginRecovery(120, "USER_STOP");
        assertEquals(20, c.activeDurationMillis(150));
        c.beginRecovery(160, "ERROR");
        assertEquals(20, c.activeDurationMillis(170));
        assertTrue(c.observeRecovery(171, epoch, true, true, true));
        c.finalizeRecovery(172, epoch);
        assertEquals(20, c.activeDurationMillis(Long.MAX_VALUE));
    }

    @Test public void timeoutAllowsRecoveryButNeverInventsNaturalProof() {
        DailyController c = new DailyController(10, 2, RATE_WINDOW_MS);
        long epoch = enabled(c);
        c.status(110);
        assertTrue(c.needsRecovery());
        assertFalse(c.recoveryProved());
        assertTrue(c.observeRecovery(120, epoch, true, true, true));
        assertEquals(FINAL_CONFIRMED, c.finalizeRecovery(121, epoch));
    }

    @Test public void unconfirmedFinalizationLocksRestartEvenWithoutActualOverrides() {
        DailyController c = new DailyController();
        long epoch = baseline(c);
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(101, epoch));
        assertEquals(DISARMED, c.mode());
        assertEquals(0, c.appliedCount());
        assertTrue(c.needsRecovery());
        assertEquals(0, c.start(102, BASELINE));
        assertFalse(c.enable(102));
        assertFalse(c.observeRecovery(103, epoch, true, true, true));
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(104, epoch));
    }

    @Test public void directFinalizationRevokesPendingAndDoesNotClaimRestoration() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        assertTrue(permit(c, 102, epoch));
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(103, epoch));
        assertFalse(c.overrideApplied(104, epoch));
        assertTrue(c.needsRecovery());
    }

    @Test public void confirmedRestartCreatesNewEpochAndResetsCountersAndRateWindow() {
        DailyController c = new DailyController(MAX_DURATION_MS, 1, RATE_WINDOW_MS);
        long old = enabled(c);
        apply(c, 102, old);
        confirmed(c, 103, old);
        long current = c.start(104, BASELINE);
        assertEquals(old + 1, current);
        assertEquals(0, c.permitCount());
        assertEquals(0, c.appliedCount());
        assertFalse(c.recoveryProved());
        assertTrue(c.enable(105));
        apply(c, 106, current);
    }

    @Test public void finalizedStateAndTimeRemainFrozenDespiteFurtherQueriesOrOldProof() {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        confirmed(c, 103, epoch);
        String status = c.status(103);
        assertFalse(c.beginRecovery(Long.MAX_VALUE, "ERROR"));
        assertFalse(c.observeRecovery(104, epoch, true, false, false));
        assertEquals(FINAL_CONFIRMED, c.finalizeRecovery(-1, epoch));
        assertEquals(status, c.status(Long.MAX_VALUE));
        assertEquals(status, c.status(-1));
    }

    @Test public void staleFutureZeroAndNegativeTicketsCannotTickNewSession() {
        DailyController c = new DailyController(100, 2, RATE_WINDOW_MS);
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
    }

    @Test public void staleAcknowledgementCannotConsumeCurrentPendingPermit() {
        DailyController c = new DailyController();
        long old = enabled(c);
        confirmed(c, 103, old);
        long current = c.start(200, BASELINE);
        c.enable(201);
        assertTrue(permit(c, 202, current));
        assertFalse(c.overrideApplied(Long.MAX_VALUE, old));
        assertTrue(c.overrideApplied(202, current));
    }

    @Test public void maximumEpochCanBeUsedOnceAndNeverWraps() {
        DailyController c = new DailyController(100, 2, RATE_WINDOW_MS, Long.MAX_VALUE - 1);
        long epoch = c.start(100, BASELINE);
        assertEquals(Long.MAX_VALUE, epoch);
        c.enable(101);
        apply(c, 102, epoch);
        confirmed(c, 103, epoch);
        assertEquals(0, c.start(200, BASELINE));
        assertEquals(Long.MAX_VALUE, c.epoch());
        assertTrue(c.status(200).contains("dailyReason=EPOCH_EXHAUSTED "));
    }

    @Test public void testLimitsCannotRelaxAnyProductionSafetyBound() {
        for (long[] values : new long[][]{{0, 1, 60_000}, {-1, 1, 60_000},
                {43_200_001, 1, 60_000}, {1, 0, 60_000}, {1, -1, 60_000},
                {1, 2_001, 60_000}, {1, 1, 59_999}, {1, 1, -1}}) {
            try {
                new DailyController(values[0], values[1], values[2]);
                fail("INVALID_LIMIT_ACCEPTED");
            } catch (IllegalArgumentException expected) {
                assertEquals("INVALID_LIMIT", expected.getMessage());
            }
        }
        assertEquals(1, new DailyController(1, 1, Long.MAX_VALUE).durationLimitMillis());
        try {
            new DailyController(1, 1, RATE_WINDOW_MS, -1);
            fail("INVALID_EPOCH_ACCEPTED");
        } catch (IllegalArgumentException expected) {
            assertEquals("INVALID_EPOCH", expected.getMessage());
        }
    }

    @Test public void wrongThreadCannotAcknowledgeTheReservedPermit() throws Exception {
        DailyController c = new DailyController();
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
    }

    @Test public void stopWinningRacePreventsDelayedWorkerAcknowledgement() throws Exception {
        DailyController c = new DailyController();
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
        } finally {
            stopped.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test public void oldDelayedCallbacksCannotFinalizeOrExpireRestartedSession() throws Exception {
        DailyController c = new DailyController();
        long old = enabled(c);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch restarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> delayed = executor.submit(() -> {
                entered.countDown();
                try { assertTrue(restarted.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                assertFalse(permit(c, Long.MAX_VALUE, old));
                assertFalse(c.overrideApplied(-1, old));
                assertFalse(c.observeRecovery(Long.MAX_VALUE, old, true, true, false));
                assertEquals("STALE_EPOCH", c.finalizeRecovery(Long.MAX_VALUE, old));
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
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

    @Test public void concurrentAtomicOverridesCannotExceedSlidingRateLimit() throws Exception {
        DailyController c = new DailyController();
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
    }

    @Test public void concurrentStartHasExactlyOneWinner() throws Exception {
        DailyController c = new DailyController();
        List<Long> winners = Collections.synchronizedList(new ArrayList<>());
        concurrently(16, worker -> {
            long epoch = c.start(100, BASELINE);
            if (epoch != 0) winners.add(epoch);
        });
        assertEquals(1, winners.size());
        assertEquals(1, c.epoch());
        assertEquals(BASELINE, c.mode());
    }

    @Test public void concurrentStopAndFinalizationCannotInventProof() throws Exception {
        DailyController c = new DailyController();
        long epoch = enabled(c);
        apply(c, 102, epoch);
        concurrently(12, worker -> {
            if (worker % 3 == 0) c.beginRecovery(103, "USER_STOP");
            else if (worker % 3 == 1) c.finalizeRecovery(103, epoch);
            else assertFalse(permit(c, 103, 0));
        });
        assertEquals(DISARMED, c.mode());
        assertTrue(c.needsRecovery());
        assertEquals(FINAL_UNCONFIRMED, c.finalizeRecovery(104, epoch));
        assertEquals(0, c.start(105, BASELINE));
    }

    @Test public void seededLifecycleStressKeepsRateEpochAndRecoveryInvariants() {
        Random random = new Random(0xD4117);
        DailyController c = new DailyController(MAX_DURATION_MS, 5, RATE_WINDOW_MS);
        long prior = 0;
        for (int session = 0; session < 100; session++) {
            long start = 200_000L * (session + 1);
            long epoch = c.start(start, BASELINE);
            assertEquals(prior + 1, epoch);
            assertTrue(c.enable(start + 1));
            for (int i = 2; i < 90; i++) {
                if (c.permit(start + i, epoch, random.nextBoolean(), true, true, true, false, true)) {
                    assertTrue(c.overrideApplied(start + i, epoch));
                }
                assertTrue(c.appliedCount() <= c.permitCount());
                assertTrue(c.permitCount() <= 5);
                assertFalse(c.observeRecovery(Long.MAX_VALUE, prior, true, true, false));
                if (RECOVERY.equals(c.mode())) assertFalse(permit(c, start + i, epoch));
            }
            confirmed(c, start + 100, epoch);
            assertFalse(c.needsRecovery());
            prior = epoch;
        }
    }

    private static void setCounter(DailyController controller, String name, long value)
            throws Exception {
        Field field = DailyController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(controller, value);
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
                    try { assertTrue(start.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException failure) { throw new AssertionError(failure); }
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
