package personal.wallpaper.os4.e1.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class PermanentRecoveryWindowTest {
    @Test public void permanentRecoveryIsIndependentOfMonthsOfRuntime() {
        long stop = 365L * 24 * 3_600_000;
        assertTrue(HomeScope.permanentRecoveryWindow(stop, 0, stop));
        assertTrue(HomeScope.permanentRecoveryWindow(stop + 19_999, 0, stop));
        assertFalse(HomeScope.permanentRecoveryWindow(stop + 20_000, 0, stop));
    }
    @Test public void negativeUnsetFutureAndPreSessionTimesNeverProveRecovery() {
        for (long[] times : new long[][]{{-1, 0, 0}, {1, -1, 0}, {1, 0, -1},
                {10, 1, 11}, {10, 2, 1}, {Long.MIN_VALUE, 0, 0}}) {
            assertFalse(HomeScope.permanentRecoveryWindow(times[0], times[1], times[2]));
        }
    }
    @Test public void zeroStartIsAValidRecoveryOrigin() {
        assertTrue(HomeScope.permanentRecoveryWindow(0, 0, 0));
        assertTrue(HomeScope.permanentRecoveryWindow(19_999, 0, 0));
        assertFalse(HomeScope.permanentRecoveryWindow(20_000, 0, 0));
    }
    @Test public void maximumTimestampUsesSubtractionWithoutOverflowingDeadlineAddition() {
        assertTrue(HomeScope.permanentRecoveryWindow(Long.MAX_VALUE, 0, Long.MAX_VALUE - 19_999));
        assertFalse(HomeScope.permanentRecoveryWindow(Long.MAX_VALUE, 0, Long.MAX_VALUE - 20_000));
        assertTrue(HomeScope.permanentRecoveryWindow(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
        assertFalse(HomeScope.permanentRecoveryWindow(Long.MAX_VALUE, 0, 0));
    }
    @Test public void timedRecoveryKeepsItsWholeSessionCutoff() {
        long duration = DailyController.MAX_DURATION_MS;
        long late = duration + 20_000;
        assertFalse(HomeScope.dailyRecoveryWindow(late, 0, late, duration));
        assertTrue(HomeScope.permanentRecoveryWindow(late, 0, late));
    }
}
