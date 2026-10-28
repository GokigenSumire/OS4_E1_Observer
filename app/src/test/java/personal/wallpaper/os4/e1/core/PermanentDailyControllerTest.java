package personal.wallpaper.os4.e1.core;

import org.junit.Test;
import static org.junit.Assert.*;

/** 永久运行仍由外部明确启用；不覆盖持久桥、开机广播或Android设备行为。 */
public class PermanentDailyControllerTest {
    private static long enable(DailyController c, long now) {
        long epoch = c.start(now, DailyController.BASELINE);
        assertTrue(epoch > 0);
        assertTrue(c.enable(now));
        return epoch;
    }
    private static boolean permit(DailyController c, long now, long epoch) {
        return c.permit(now, epoch, true, true, true, true, false, true);
    }
    private static void apply(DailyController c, long now, long epoch) {
        assertTrue(permit(c, now, epoch));
        assertTrue(c.overrideApplied(now, epoch));
    }

    @Test public void newPermanentInstanceHasNoInheritedIntentOrAutomaticEnable() {
        DailyController c = new DailyController();
        assertTrue(c.isPermanent());
        assertEquals(DailyController.NO_DEADLINE, c.deadline());
        assertEquals(DailyController.DISARMED, c.mode());
        assertFalse(c.enable(0));
        assertFalse(permit(c, 0, 0));
        assertTrue(c.status(0).contains("dailyPolicy=PERMANENT "));
    }
    @Test public void permanentBaselineDoesNotAgeOutBeforeAnExplicitEnable() {
        DailyController c = new DailyController();
        long epoch = c.start(0, DailyController.BASELINE);
        assertTrue(c.enable(365L * 24 * 3_600_000));
        apply(c, 365L * 24 * 3_600_000, epoch);
        assertEquals(DailyController.ENABLE, c.mode());
        assertEquals(DailyController.NO_DEADLINE, c.deadline());
    }
    @Test public void queriesAtHugeElapsedTimesCannotExpireOrRenewPermanentPermission() {
        DailyController c = new DailyController();
        long epoch = enable(c, 0);
        c.status(Long.MAX_VALUE - 1);
        apply(c, Long.MAX_VALUE, epoch);
        assertEquals(DailyController.ENABLE, c.mode());
        assertEquals(Long.MAX_VALUE, c.activeDurationMillis());
        assertEquals(epoch, c.epoch());
        assertEquals(0, c.durationLimitMillis());
        assertEquals(DailyController.NO_DEADLINE, c.deadline());
    }
    @Test public void startingNearMaximumHasNoArtificialSaturatedDeadline() {
        DailyController c = new DailyController();
        long epoch = enable(c, Long.MAX_VALUE - 3);
        apply(c, Long.MAX_VALUE, epoch);
        assertEquals(3, c.activeDurationMillis());
        assertEquals(DailyController.NO_DEADLINE, c.deadline());
        assertFalse(c.status(Long.MAX_VALUE).contains("TIMEOUT"));
    }
    @Test public void longRunningPermanentRateLimitStillRevokesAtTwoThousandAndOne() {
        DailyController c = new DailyController();
        long now = 100L * 24 * 3_600_000;
        long epoch = enable(c, 0);
        for (int i = 0; i < 2000; i++) apply(c, now, epoch);
        assertFalse(permit(c, now, epoch));
        assertEquals(DailyController.RECOVERY, c.mode());
        assertFalse(permit(c, now + 60_000, epoch));
        assertFalse(c.enable(now + 60_000));
        assertTrue(c.status(now + 60_000).contains("dailyReason=LIMIT_RATE "));
    }
    @Test public void permanentRateWindowCanExpireWithoutRenewingTheSession() {
        DailyController c = new DailyController();
        long epoch = enable(c, 0);
        for (int i = 0; i < 2000; i++) apply(c, Long.MAX_VALUE - 60_000, epoch);
        apply(c, Long.MAX_VALUE, epoch);
        assertEquals(2001, c.appliedCount());
        assertEquals(epoch, c.epoch());
        assertEquals(DailyController.ENABLE, c.mode());
    }
    @Test public void manualStopAfterLongRuntimeFreezesActualActiveDuration() {
        DailyController c = new DailyController();
        long epoch = enable(c, 1);
        assertTrue(c.beginRecovery(Long.MAX_VALUE - 1, "USER_STOP"));
        assertFalse(permit(c, Long.MAX_VALUE, epoch));
        assertEquals(Long.MAX_VALUE - 2, c.activeDurationMillis(Long.MAX_VALUE));
        assertEquals(DailyController.FINAL_UNCONFIRMED, c.finalizeRecovery(Long.MAX_VALUE, epoch));
        assertEquals(0, c.start(Long.MAX_VALUE, DailyController.BASELINE));
        assertFalse(c.enable(Long.MAX_VALUE));
    }
    @Test public void negativeClockStillRevokesPermanentPermission() {
        DailyController c = new DailyController();
        long epoch = enable(c, 0);
        assertFalse(permit(c, Long.MIN_VALUE, epoch));
        assertEquals(DailyController.RECOVERY, c.mode());
        assertFalse(c.status(10).contains("dailyActiveMs=-"));
    }
    @Test public void timedConstructorStillHasItsRealDeadlineAndNoPermanentMarker() {
        DailyController c = new DailyController(100, 2, 60_000);
        long epoch = enable(c, 5);
        assertFalse(c.isPermanent());
        assertEquals(105, c.deadline());
        assertTrue(c.status(5).contains("dailyPolicy=TIMED "));
        assertFalse(permit(c, 105, epoch));
        assertEquals(DailyController.RECOVERY, c.mode());
    }
    @Test public void oldEpochCannotAdvanceOrStopTheNextPermanentSession() {
        DailyController c = new DailyController();
        long old = enable(c, 0);
        c.beginRecovery(1, "USER_STOP");
        assertTrue(c.observeRecovery(1, old, true, true, true));
        assertEquals(DailyController.FINAL_CONFIRMED, c.finalizeRecovery(1, old));
        long current = enable(c, 2);
        assertFalse(permit(c, Long.MAX_VALUE, old));
        apply(c, 3, current);
        assertEquals(1, c.activeDurationMillis());
        assertEquals(DailyController.ENABLE, c.mode());
    }
}
