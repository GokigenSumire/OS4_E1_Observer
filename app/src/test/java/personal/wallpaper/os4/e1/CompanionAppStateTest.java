package personal.wallpaper.os4.e1;

import org.junit.Test;
import static org.junit.Assert.*;

public final class CompanionAppStateTest {
    private static final String FORMAL = "personal.wallpaper.os3";
    private static final String DEBUG = "personal.wallpaper.os3.debug";

    @Test public void missingAppNeverHasVersionOrOpenPermission() {
        CompanionAppState state = CompanionAppState.notInstalled();
        assertEquals(CompanionAppState.Availability.NOT_INSTALLED, state.availability);
        assertNull(state.versionName);
        assertEquals(-1, state.versionCode);
        assertFalse(state.canOpen);
    }
    @Test public void restrictedQueryNeverLooksInstalled() {
        CompanionAppState state = CompanionAppState.unavailable();
        assertEquals(CompanionAppState.Availability.UNAVAILABLE, state.availability);
        assertFalse(state.canOpen);
    }
    @Test public void installedFormalAppShowsActualInstalledVersion() {
        CompanionAppState state = CompanionAppState.installed(FORMAL, FORMAL, "2.3.4", 71, true, FORMAL);
        assertEquals(CompanionAppState.Availability.INSTALLED, state.availability);
        assertEquals("2.3.4", state.versionName);
        assertEquals(71, state.versionCode);
        assertTrue(state.canOpen);
    }
    @Test public void installedWithoutLauncherKeepsVersionButCannotOpen() {
        CompanionAppState state = CompanionAppState.installed(FORMAL, FORMAL, "1.0.0", 61, true, null);
        assertEquals(CompanionAppState.Availability.INSTALLED, state.availability);
        assertEquals("1.0.0", state.versionName);
        assertEquals(61, state.versionCode);
        assertFalse(state.canOpen);
    }
    @Test public void disabledAppKeepsVersionButCannotOpen() {
        CompanionAppState state = CompanionAppState.installed(FORMAL, FORMAL, "1.0.0", 61, false, FORMAL);
        assertEquals(CompanionAppState.Availability.INSTALLED, state.availability);
        assertFalse(state.canOpen);
    }
    @Test public void formalEntryCannotOpenDebugComponent() {
        assertFalse(CompanionAppState.installed(FORMAL, FORMAL, "1", 61, true, DEBUG).canOpen);
    }
    @Test public void debugInstallationCannotSatisfyFormalQuery() {
        assertEquals(CompanionAppState.Availability.UNAVAILABLE,
                CompanionAppState.installed(FORMAL, DEBUG, "1", 61, true, DEBUG).availability);
    }
    @Test public void debugBuildOnlyOpensItsOwnExpectedCompanion() {
        assertTrue(CompanionAppState.installed(DEBUG, DEBUG, "1-debug", 61, true, DEBUG).canOpen);
        assertFalse(CompanionAppState.installed(DEBUG, DEBUG, "1-debug", 61, true, FORMAL).canOpen);
    }
    @Test public void missingOrBlankVersionIsExplicitlyUnknown() {
        assertNull(CompanionAppState.installed(FORMAL, FORMAL, null, 61, true, FORMAL).versionName);
        assertNull(CompanionAppState.installed(FORMAL, FORMAL, "   ", 61, true, FORMAL).versionName);
    }
    @Test public void invalidVersionCodeIsNotReplacedByBuildVersion() {
        assertEquals(-1, CompanionAppState.installed(FORMAL, FORMAL, "1.0.0", -5, true, FORMAL).versionCode);
    }
    @Test public void unrelatedOrMalformedExpectedPackageIsRejected() {
        for (String expected : new String[]{null, "", "other.package", FORMAL + " ", FORMAL + ".prod"}) {
            CompanionAppState state = CompanionAppState.installed(expected, expected, "1", 1, true, expected);
            assertEquals(CompanionAppState.Availability.UNAVAILABLE, state.availability);
            assertFalse(state.canOpen);
        }
    }
    @Test public void absentActualPackageCannotLookInstalled() {
        assertEquals(CompanionAppState.Availability.UNAVAILABLE,
                CompanionAppState.installed(FORMAL, null, "1", 61, true, FORMAL).availability);
    }
}
