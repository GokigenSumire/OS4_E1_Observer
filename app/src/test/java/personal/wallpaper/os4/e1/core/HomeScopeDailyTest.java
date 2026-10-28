package personal.wallpaper.os4.e1.core;

import org.junit.Test;

import static org.junit.Assert.*;

/** 日常租约的独立命令、自然恢复期限与当前正式壁纸资格边界。 */
public class HomeScopeDailyTest {
    private static final long DURATION = DailyController.MAX_DURATION_MS;

    @Test public void dailyCommandCanAddressInitialZeroEpochButRequiresExactEquality() {
        assertTrue(HomeScope.dailyCommandAllowed(0, 0));
        assertTrue(HomeScope.dailyCommandAllowed(1, 1));
        assertTrue(HomeScope.dailyCommandAllowed(59, 59));
        assertTrue(HomeScope.dailyCommandAllowed(Long.MAX_VALUE, Long.MAX_VALUE));
        assertFalse(HomeScope.dailyCommandAllowed(-1, -1));
        assertFalse(HomeScope.dailyCommandAllowed(Long.MIN_VALUE, Long.MIN_VALUE));
    }

    @Test public void staleFutureNegativeAndUnsetExpectedTicketsCannotControlActiveDaily() {
        for (long expected : new long[]{Long.MIN_VALUE, -1, 0, 1, 58, 60, Long.MAX_VALUE}) {
            assertFalse(HomeScope.dailyCommandAllowed(expected, 59));
        }
        assertFalse(HomeScope.dailyCommandAllowed(59, 0));
        assertFalse(HomeScope.dailyCommandAllowed(0, 1));
        assertFalse(HomeScope.dailyCommandAllowed(1, -1));
    }

    @Test public void lateDailyEnableFromOldSessionCannotControlTheNextOne() {
        for (long epoch = 0; epoch < 10_000; epoch++) {
            assertFalse(HomeScope.dailyCommandAllowed(epoch, epoch + 1));
            assertTrue(HomeScope.dailyCommandAllowed(epoch + 1, epoch + 1));
        }
    }

    @Test public void recoveryAtStartHasOnlyTwentySeconds() {
        assertTrue(HomeScope.dailyRecoveryWindow(1_000, 1_000, 1_000, DURATION));
        assertTrue(HomeScope.dailyRecoveryWindow(20_999, 1_000, 1_000, DURATION));
        assertFalse(HomeScope.dailyRecoveryWindow(21_000, 1_000, 1_000, DURATION));
        assertFalse(HomeScope.dailyRecoveryWindow(21_001, 1_000, 1_000, DURATION));
    }

    @Test public void dailyRecoveryStillWorksLongAfterTheOldTwoHundredSecondLimit() {
        long stop = 3_600_000;
        assertFalse(HomeScope.recoveryWindow(stop, 0, stop));
        assertTrue(HomeScope.dailyRecoveryWindow(stop, 0, stop, DURATION));
        assertTrue(HomeScope.dailyRecoveryWindow(stop + 19_999, 0, stop, DURATION));
        assertFalse(HomeScope.dailyRecoveryWindow(stop + 20_000, 0, stop, DURATION));
    }

    @Test public void timeoutRecoveryEndsAtTwelveHoursPlusTwentySeconds() {
        long start = 1_000;
        long deadline = start + DURATION;
        assertTrue(HomeScope.dailyRecoveryWindow(deadline, start, deadline, DURATION));
        assertTrue(HomeScope.dailyRecoveryWindow(deadline + 19_999, start, deadline, DURATION));
        assertFalse(HomeScope.dailyRecoveryWindow(deadline + 20_000, start, deadline, DURATION));
    }

    @Test public void delayedWatchdogCannotGrantAnotherTwentySecondsBeyondTotalDeadline() {
        long start = 1_000;
        long delayed = start + DURATION + 19_000;
        assertTrue(HomeScope.dailyRecoveryWindow(delayed, start, delayed, DURATION));
        assertTrue(HomeScope.dailyRecoveryWindow(start + DURATION + 19_999,
                start, delayed, DURATION));
        assertFalse(HomeScope.dailyRecoveryWindow(start + DURATION + 20_000,
                start, delayed, DURATION));
        assertFalse(HomeScope.dailyRecoveryWindow(delayed + 20_000, start, delayed, DURATION));
    }

    @Test public void localGraceAndWholeSessionDeadlineIndependentlyApply() {
        assertTrue(HomeScope.dailyRecoveryWindow(100_999, 1_000, 81_000, DURATION));
        assertFalse(HomeScope.dailyRecoveryWindow(101_000, 1_000, 81_000, DURATION));
        long stop = DURATION + 10_000;
        assertTrue(HomeScope.dailyRecoveryWindow(DURATION + 19_999, 0, stop, DURATION));
        assertFalse(HomeScope.dailyRecoveryWindow(DURATION + 20_000, 0, stop, DURATION));
    }

    @Test public void testDurationCanOnlyTightenTheTwelveHourBound() {
        assertTrue(HomeScope.dailyRecoveryWindow(1, 0, 1, 1));
        assertTrue(HomeScope.dailyRecoveryWindow(20_000, 0, 1, 1));
        assertFalse(HomeScope.dailyRecoveryWindow(20_001, 0, 1, 1));
        for (long duration : new long[]{0, -1, DURATION + 1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertFalse(HomeScope.dailyRecoveryWindow(0, 0, 0, duration));
        }
    }

    @Test public void unsetBackwardNegativeAndPreSessionTimesRejectRecoveryProof() {
        assertFalse(HomeScope.dailyRecoveryWindow(1, -1, 0, DURATION));
        assertFalse(HomeScope.dailyRecoveryWindow(1, 0, -1, DURATION));
        assertFalse(HomeScope.dailyRecoveryWindow(1_000, 1_000, 999, DURATION));
        assertFalse(HomeScope.dailyRecoveryWindow(1_099, 1_000, 1_100, DURATION));
        assertFalse(HomeScope.dailyRecoveryWindow(-1, 0, 0, DURATION));
        assertFalse(HomeScope.dailyRecoveryWindow(Long.MIN_VALUE, 0, 0, DURATION));
    }

    @Test public void zeroMonotonicStartIsValid() {
        assertTrue(HomeScope.dailyRecoveryWindow(0, 0, 0, DURATION));
        assertTrue(HomeScope.dailyRecoveryWindow(19_999, 0, 0, DURATION));
        assertFalse(HomeScope.dailyRecoveryWindow(20_000, 0, 0, DURATION));
    }

    @Test public void maximumTimestampDoesNotWrapIntoEitherRecoveryWindow() {
        assertFalse(HomeScope.dailyRecoveryWindow(Long.MAX_VALUE, 0, 0, DURATION));
        assertFalse(HomeScope.dailyRecoveryWindow(Long.MAX_VALUE, 0, Long.MAX_VALUE, DURATION));
        assertTrue(HomeScope.dailyRecoveryWindow(Long.MAX_VALUE, Long.MAX_VALUE - 19_999,
                Long.MAX_VALUE - 19_999, DURATION));
        assertFalse(HomeScope.dailyRecoveryWindow(Long.MAX_VALUE, Long.MAX_VALUE - 20_000,
                Long.MAX_VALUE - 20_000, DURATION));
    }

    @Test public void wallpaperMustHaveExactOwnerTypeDisplayAndBothLiveTokenFacts() {
        for (int bits = 0; bits < 32; bits++) {
            Object owner = (bits & 1) != 0 ? "personal.wallpaper.os3.debug" : "com.miui.home";
            Object type = (bits & 2) != 0 ? 2013 : 1;
            Object display = (bits & 4) != 0 ? 0 : 1;
            boolean allowed = HomeScope.targetWallpaperAllowed(owner, type, display,
                    (bits & 8) != 0, (bits & 16) != 0);
            assertEquals("WALLPAPER_BITS=" + bits, bits == 31, allowed);
        }
    }

    @Test public void unknownAndLookalikeWallpaperOwnerFailClosed() {
        for (Object owner : new Object[]{null, "", "personal.wallpaper.os3",
                "personal.wallpaper.os4.e1.debug", "personal.wallpaper.os3.debug ",
                "Personal.wallpaper.os3.debug", "personal.wallpaper.os3.debug\n"}) {
            assertFalse(HomeScope.targetWallpaperAllowed(owner, 2013, 0, true, true));
        }
    }

    @Test public void wallpaperTypeAndDisplayRequireExactIntegerObjects() {
        for (Object type : new Object[]{null, "2013", 2013L, 2013.0, 1, 2011, -1}) {
            assertFalse(HomeScope.targetWallpaperAllowed("personal.wallpaper.os3.debug",
                    type, 0, true, true));
        }
        for (Object display : new Object[]{null, "0", 0L, 0.0, 1, -1}) {
            assertFalse(HomeScope.targetWallpaperAllowed("personal.wallpaper.os3.debug",
                    2013, display, true, true));
        }
    }

    @Test public void selectedButInvisibleOrVisibleButNotSelectedWallpaperCannotAuthorize() {
        assertFalse(HomeScope.targetWallpaperAllowed("personal.wallpaper.os3.debug",
                2013, 0, true, false));
        assertFalse(HomeScope.targetWallpaperAllowed("personal.wallpaper.os3.debug",
                2013, 0, false, true));
        assertFalse(HomeScope.targetWallpaperAllowed("personal.wallpaper.os3.debug",
                2013, 0, false, false));
    }
}
