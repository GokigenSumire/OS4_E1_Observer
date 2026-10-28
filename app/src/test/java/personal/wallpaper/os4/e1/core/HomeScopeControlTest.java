package personal.wallpaper.os4.e1.core;

import org.junit.Test;

import static org.junit.Assert.*;

/** 请求票据、候选身份和恢复观察硬边界的独立示例。 */
public class HomeScopeControlTest {
    @Test public void currentPositiveCommandTicketIsAccepted() {
        assertTrue(HomeScope.commandAllowed(1, 1));
        assertTrue(HomeScope.commandAllowed(57, 57));
        assertTrue(HomeScope.commandAllowed(Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test public void staleFutureUnsetAndNegativeCommandTicketsAreRejected() {
        for (long ticket : new long[]{0, -1, 1, 56, 58, Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertFalse(HomeScope.commandAllowed(ticket, 57));
        }
        assertFalse(HomeScope.commandAllowed(0, 0));
        assertFalse(HomeScope.commandAllowed(-1, -1));
        assertFalse(HomeScope.commandAllowed(1, 0));
    }

    @Test public void delayedEnableFromPriorExperimentCannotAuthorizeNextExperiment() {
        for (long experiment = 1; experiment < 10_000; experiment++) {
            assertFalse(HomeScope.commandAllowed(experiment, experiment + 1));
            assertTrue(HomeScope.commandAllowed(experiment + 1, experiment + 1));
        }
    }

    @Test public void responseMustIdentifyExactlyThisCandidate() {
        assertTrue(HomeScope.responseAllowed("60/1.0.0", 60, "1.0.0"));
        for (String response : new String[]{null, "", "56/0.0.56-e1-debug",
                "57/0.0.57-e1-debug", "58/0.0.58-e1-debug", "59/0.0.59-e1-debug",
                "59/1.0.0", "60/0.0.59-e1-debug", "60/1.0.0-debug", "060/1.0.0", "+60/1.0.0",
                "60/1.0.0 ", " 60/1.0.0", "60/1.0.0\n", "60/1.0.0\r\n", "60/1.0.0-secret",
                "60 /1.0.0", "60/ 1.0.0", "module=60/1.0.0"}) {
            assertFalse(HomeScope.responseAllowed(response, 60, "1.0.0"));
        }
    }

    @Test public void expectedVersionDynamicallyControlsDebugAndReleaseIdentity() {
        for (int code : new int[]{1, 60, 61, 123_456, Integer.MAX_VALUE}) {
            for (String name : new String[]{"0.0.60-e1-debug", "1.0.0", "Version_A-9"}) {
                String module = code + "/" + name;
                assertTrue(HomeScope.responseAllowed(module, code, name));
                assertFalse(HomeScope.responseAllowed(module, code, name + "-other"));
                assertFalse(HomeScope.responseAllowed(module, code == 1 ? 2 : code - 1, name));
            }
        }
        assertFalse(HomeScope.responseAllowed("60/1.0.0", 60, "1.0.0-debug"));
        assertFalse(HomeScope.responseAllowed("60/1.0.0-debug", 60, "1.0.0"));
        assertFalse(HomeScope.responseAllowed("60/Release_A-9", 60, "release_A-9"));
    }

    @Test public void nonPositiveExpectedVersionCodesAreRejectedEvenWithMatchingText() {
        for (int code : new int[]{0, -1, -60, Integer.MIN_VALUE}) {
            assertFalse(HomeScope.responseAllowed(code + "/1.0.0", code, "1.0.0"));
            assertFalse(HomeScope.responseAllowed("60/1.0.0", code, "1.0.0"));
        }
    }

    @Test public void nullAndEmptyExpectedNamesAreRejectedEvenWithMatchingText() {
        assertFalse(HomeScope.responseAllowed("60/null", 60, null));
        assertFalse(HomeScope.responseAllowed("60/", 60, ""));
        assertFalse(HomeScope.responseAllowed("60/1.0.0", 60, null));
        assertFalse(HomeScope.responseAllowed("60/1.0.0", 60, ""));
        assertFalse(HomeScope.responseAllowed(null, 60, "1.0.0"));
        assertFalse(HomeScope.responseAllowed(null, 0, null));
    }

    @Test public void safeExpectedNameAcceptsEveryAllowedAsciiCharacter() {
        String allowed = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz._-";
        for (int index = 0; index < allowed.length(); index++) {
            String name = String.valueOf(allowed.charAt(index));
            assertTrue(HomeScope.responseAllowed("60/" + name, 60, name));
        }
    }

    @Test public void unsafeAsciiExpectedNamesAreRejectedEvenWithMatchingText() {
        String allowed = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz._-";
        for (char value = 0; value < 128; value++) {
            if (allowed.indexOf(value) >= 0) continue;
            String name = "1.0" + value + "0";
            assertFalse("ASCII " + (int) value,
                    HomeScope.responseAllowed("60/" + name, 60, name));
        }
    }

    @Test public void unicodeExpectedNamesAreRejectedEvenWithMatchingText() {
        for (String name : new String[]{"版本1", "\u0080", "\u00e9", "\uff11.0.0",
                "1.0.0\u00a0", "1.0.0\u200b", "1.0.0\ud83d\ude00", "\ud800"}) {
            assertFalse(HomeScope.responseAllowed("60/" + name, 60, name));
        }
    }

    @Test public void expectedNameLengthAcceptsOneAndSixtyFourButRejectsSixtyFive() {
        String sixtyFour = new String(new char[64]).replace('\0', 'a');
        assertTrue(HomeScope.responseAllowed("60/a", 60, "a"));
        assertTrue(HomeScope.responseAllowed("60/" + sixtyFour, 60, sixtyFour));
        assertFalse(HomeScope.responseAllowed("60/" + sixtyFour + "a", 60, sixtyFour + "a"));
        assertFalse(HomeScope.responseAllowed("60/" + sixtyFour, 60, sixtyFour + "a"));
    }

    @Test public void expectedAndResponseNamesCannotBeTrimmedIntoMatching() {
        for (String name : new String[]{" 1.0.0", "1.0.0 ", "\t1.0.0", "1.0.0\n", "1.0.0\r\n"}) {
            assertFalse(HomeScope.responseAllowed("60/" + name, 60, name));
            assertFalse(HomeScope.responseAllowed("60/1.0.0", 60, name));
            assertFalse(HomeScope.responseAllowed("60/" + name, 60, "1.0.0"));
        }
    }

    @Test public void recoveryAtExperimentStartCanObserveUntilItsTwentySecondBoundary() {
        assertTrue(HomeScope.recoveryWindow(1_000, 1_000, 1_000));
        assertTrue(HomeScope.recoveryWindow(20_999, 1_000, 1_000));
        assertFalse(HomeScope.recoveryWindow(21_000, 1_000, 1_000));
        assertFalse(HomeScope.recoveryWindow(21_001, 1_000, 1_000));
    }

    @Test public void timeoutRecoveryEndsAtTotalTwoHundredSeconds() {
        assertTrue(HomeScope.recoveryWindow(181_000, 1_000, 181_000));
        assertTrue(HomeScope.recoveryWindow(200_999, 1_000, 181_000));
        assertFalse(HomeScope.recoveryWindow(201_000, 1_000, 181_000));
        assertFalse(HomeScope.recoveryWindow(201_001, 1_000, 181_000));
    }

    @Test public void delayedWatchdogCannotGrantAnotherTwentySecondsAfterTotalDeadline() {
        assertTrue(HomeScope.recoveryWindow(200_999, 1_000, 200_000));
        assertFalse(HomeScope.recoveryWindow(201_000, 1_000, 200_000));
        assertFalse(HomeScope.recoveryWindow(220_000, 1_000, 200_000));
    }

    @Test public void invalidUnsetBackwardAndPreExperimentTimesRejectRecoveryEvidence() {
        assertFalse(HomeScope.recoveryWindow(1, -1, 0));
        assertFalse(HomeScope.recoveryWindow(1, 0, -1));
        assertFalse(HomeScope.recoveryWindow(1_000, 1_000, 999));
        assertFalse(HomeScope.recoveryWindow(1_099, 1_000, 1_100));
        assertFalse(HomeScope.recoveryWindow(-1, 0, 0));
        assertFalse(HomeScope.recoveryWindow(Long.MIN_VALUE, 0, 0));
    }

    @Test public void zeroIsAValidMonotonicExperimentStart() {
        assertTrue(HomeScope.recoveryWindow(0, 0, 0));
        assertTrue(HomeScope.recoveryWindow(19_999, 0, 0));
        assertFalse(HomeScope.recoveryWindow(20_000, 0, 0));
    }

    @Test public void longMaximumElapsedSpanCannotWrapIntoAllowedWindow() {
        assertFalse(HomeScope.recoveryWindow(Long.MAX_VALUE, 0, 0));
        assertFalse(HomeScope.recoveryWindow(Long.MAX_VALUE, 0, Long.MAX_VALUE));
        assertTrue(HomeScope.recoveryWindow(Long.MAX_VALUE, Long.MAX_VALUE - 19_999,
                Long.MAX_VALUE - 19_999));
        assertFalse(HomeScope.recoveryWindow(Long.MAX_VALUE, Long.MAX_VALUE - 20_000,
                Long.MAX_VALUE - 20_000));
    }

    @Test public void graceAndTotalDeadlineEachIndependentlyLimitRecovery() {
        assertFalse(HomeScope.recoveryWindow(101_000, 1_000, 81_000));
        assertTrue(HomeScope.recoveryWindow(100_999, 1_000, 81_000));
        assertFalse(HomeScope.recoveryWindow(201_000, 1_000, 191_000));
        assertTrue(HomeScope.recoveryWindow(200_999, 1_000, 191_000));
    }
}
