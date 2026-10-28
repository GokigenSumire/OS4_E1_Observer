package personal.wallpaper.os4.e1;

import org.junit.Test;
import static org.junit.Assert.*;

public final class DashboardStateTest {
    private static final String READY = "dailyPolicy=PERMANENT dailyMode=DISARMED dailyNeedsRecovery=false dailyFinal=NONE recoveryStatus=NOT_VERIFIED";
    private static final String ACTIVE = "dailyPolicy=PERMANENT dailyMode=ENABLE dailyNeedsRecovery=false dailyFinal=NONE dailyDurationMs=0 dailyDeadline=-1 targetWallpaper=PROJECT_WALLPAPER recoveryStatus=PENDING";
    private DashboardState state(String daily, String hooks, String bootstrap, String coverage,
                                 String error, boolean available, boolean enabled) {
        return DashboardState.from(daily, hooks, bootstrap, coverage, error, available, enabled);
    }
    private DashboardState ready(boolean available, boolean enabled) {
        return state(READY, "UNHOOKED", "BOOTSTRAP_UNHOOKED", "", null, available, enabled);
    }
    private DashboardState active(boolean available, boolean enabled) {
        return state(ACTIVE, "HOOKED", "BOOTSTRAP_UNHOOKED", "POPULATE=HOOK_AVAILABLE", null, available, enabled);
    }

    @Test public void initialStoredOffAndActualDisarmedAreAligned() {
        DashboardState result = ready(true, false);
        assertEquals(DashboardState.Kind.READY, result.kind);
        assertTrue(result.canEnable);
        assertFalse(result.canStop);
        assertTrue(result.positive);
        assertFalse(result.needsApply);
    }
    @Test public void permanentActiveDoesNotRequireALeaseDeadline() {
        DashboardState result = active(true, true);
        assertEquals(DashboardState.Kind.ACTIVE, result.kind);
        assertTrue(result.positive);
        assertTrue(result.canStop);
        assertFalse(result.canEnable);
    }
    @Test public void permanentActiveWithoutAnyDurationFieldsIsStillActualActive() {
        assertEquals(DashboardState.Kind.ACTIVE,
                state(ACTIVE.replace(" dailyDurationMs=0 dailyDeadline=-1", ""), "HOOKED",
                        "BOOTSTRAP_UNHOOKED", "", null, true, true).kind);
    }
    @Test public void storedOnDoesNotInventCurrentlyActive() {
        DashboardState result = ready(true, true);
        assertEquals(DashboardState.Kind.READY, result.kind);
        assertFalse(result.positive);
        assertTrue(result.needsApply);
        assertTrue(result.desiredEnabled);
    }
    @Test public void storedOffDoesNotHideCurrentlyActive() {
        DashboardState result = active(true, false);
        assertEquals(DashboardState.Kind.ACTIVE, result.kind);
        assertFalse(result.positive);
        assertTrue(result.needsStop);
        assertFalse(result.desiredEnabled);
    }
    @Test public void unavailableSettingNeverLooksLikeConfirmedOff() {
        DashboardState result = ready(false, false);
        assertFalse(result.desiredAvailable);
        assertFalse(result.positive);
        assertFalse(result.needsApply);
    }
    @Test public void unavailableSettingDoesNotHideNeedForSafeStop() {
        DashboardState result = active(false, false);
        assertFalse(result.positive);
        assertTrue(result.needsStop);
        assertTrue(result.canStop);
    }
    @Test public void unverifiedWallpaperRemainsWaiting() {
        DashboardState result = state(ACTIVE.replace("PROJECT_WALLPAPER", "NOT_VERIFIED"),
                "HOOKED", "BOOTSTRAP_UNHOOKED", "", null, true, true);
        assertEquals(DashboardState.Kind.WAITING_TARGET, result.kind);
        assertFalse(result.positive);
    }
    @Test public void otherWallpaperNeverLooksEffective() {
        assertEquals(DashboardState.Kind.WAITING_TARGET,
                state(ACTIVE.replace("PROJECT_WALLPAPER", "OTHER_WALLPAPER"), "HOOKED",
                        "BOOTSTRAP_UNHOOKED", "", null, true, true).kind);
    }
    @Test public void oldTimedPolicyCannotMasqueradeAsPermanent() {
        DashboardState result = state(ACTIVE.replace("PERMANENT", "LEASE"), "HOOKED",
                "BOOTSTRAP_UNHOOKED", "", null, true, true);
        assertEquals(DashboardState.Kind.UNKNOWN, result.kind);
        assertFalse(result.positive);
        assertTrue(result.canStop);
    }
    @Test public void missingPolicyCannotEnable() {
        assertFalse(state(READY.replace("dailyPolicy=PERMANENT ", ""), "UNHOOKED",
                "BOOTSTRAP_UNHOOKED", "", null, true, false).canEnable);
    }
    @Test public void recoveryKeepsNewActivationBlocked() {
        DashboardState result = state("dailyPolicy=PERMANENT dailyMode=RECOVERY dailyNeedsRecovery=true dailyFinal=NONE recoveryStatus=RECOVERY_PENDING",
                "HOOKED", "BOOTSTRAP_UNHOOKED", "", null, true, true);
        assertEquals(DashboardState.Kind.RECOVERING, result.kind);
        assertFalse(result.canEnable);
        assertFalse(result.needsApply);
    }
    @Test public void unconfirmedFinalLocksEvenWhenStoredOn() {
        DashboardState result = state(READY.replace("dailyFinal=NONE", "dailyFinal=FINAL_UNCONFIRMED"),
                "UNHOOKED", "BOOTSTRAP_UNHOOKED", "", null, true, true);
        assertEquals(DashboardState.Kind.LOCKED, result.kind);
        assertFalse(result.canEnable);
    }
    @Test public void recoveryFailureLocksEvenWithFinalNone() {
        assertEquals(DashboardState.Kind.LOCKED, state(READY.replace("NOT_VERIFIED", "RECOVERY_UNCONFIRMED"),
                "UNHOOKED", "BOOTSTRAP_UNHOOKED", "", null, true, true).kind);
    }
    @Test public void unhookFailureDoesNotLookStopped() {
        assertEquals(DashboardState.Kind.LOCKED, state(READY, "UNHOOK_FAILED",
                "BOOTSTRAP_UNHOOKED", "", null, true, false).kind);
    }
    @Test public void bootstrapUnhookFailureLocks() {
        assertEquals(DashboardState.Kind.LOCKED, state(READY, "UNHOOKED",
                "BOOTSTRAP_UNHOOK_FAILED", "", null, true, false).kind);
    }
    @Test public void reflectionFailureIsVisibleAndSafeStopRemainsPossible() {
        DashboardState result = state(ACTIVE, "HOOKED", "BOOTSTRAP_UNHOOKED", "POPULATE=HOOK_MISSING",
                null, true, true);
        assertEquals(DashboardState.Kind.HOOK_FAILURE, result.kind);
        assertFalse(result.canEnable);
        assertTrue(result.canStop);
    }
    @Test public void availabilityHistoryIsNotCurrentHookedState() {
        assertEquals(DashboardState.Kind.READY, state(READY, "UNHOOKED",
                "BOOTSTRAP_UNHOOKED", "POPULATE=HOOK_AVAILABLE", null, true, false).kind);
        assertEquals(DashboardState.Kind.UNKNOWN, state(ACTIVE, "UNHOOKED",
                "BOOTSTRAP_UNHOOKED", "POPULATE=HOOK_AVAILABLE", null, true, true).kind);
    }
    @Test public void explicitControlErrorNeverLooksSuccessful() {
        DashboardState result = state(ACTIVE, "HOOKED", "BOOTSTRAP_UNHOOKED", "",
                "START_REJECTED_RECOVERY_OR_HOOKS", true, true);
        assertEquals(DashboardState.Kind.CONTROL_ERROR, result.kind);
        assertFalse(result.positive);
        assertTrue(result.canStop);
    }
    @Test public void missingReadinessFieldsNeverEnable() {
        assertFalse(state("dailyPolicy=PERMANENT dailyMode=DISARMED", "UNHOOKED",
                "BOOTSTRAP_UNHOOKED", "", null, true, false).canEnable);
    }
    @Test public void duplicateModeCannotChooseSafeLookingValue() {
        assertEquals(DashboardState.Kind.UNKNOWN, state(READY + " dailyMode=ENABLE", "UNHOOKED",
                "BOOTSTRAP_UNHOOKED", "", null, true, true).kind);
    }
    @Test public void duplicatePolicyCannotChoosePermanent() {
        assertEquals(DashboardState.Kind.UNKNOWN, state(READY + " dailyPolicy=LEASE", "UNHOOKED",
                "BOOTSTRAP_UNHOOKED", "", null, true, false).kind);
    }
    @Test public void malformedStatusFailsClosed() {
        assertEquals(DashboardState.Kind.UNKNOWN, state(READY + " broken", "UNHOOKED",
                "BOOTSTRAP_UNHOOKED", "", null, true, false).kind);
    }
    @Test public void nullInputsNeverInventConnectedOrActive() {
        DashboardState result = state(null, null, null, null, null, false, false);
        assertEquals(DashboardState.Kind.UNKNOWN, result.kind);
        assertFalse(result.canEnable);
        assertFalse(result.canStop);
        assertFalse(result.positive);
    }
    @Test public void confirmedRecoveryAllowsOnlyExplicitReactivation() {
        DashboardState result = state(READY.replace("dailyFinal=NONE", "dailyFinal=FINAL_CONFIRMED")
                        .replace("NOT_VERIFIED", "RECOVERY_CONFIRMED"), "UNHOOKED",
                "BOOTSTRAP_UNHOOKED", "", null, true, true);
        assertTrue(result.canEnable);
        assertTrue(result.needsApply);
        assertFalse(result.positive);
    }
    @Test public void storedTrueCannotWashAFieldRecoveryLock() {
        assertEquals(DashboardState.Kind.LOCKED, state(READY.replace("dailyNeedsRecovery=false", "dailyNeedsRecovery=true"),
                "UNHOOKED", "BOOTSTRAP_UNHOOKED", "", null, true, true).kind);
    }
    @Test public void enablingNeedsSuccessfulPersistenceAndConfirmedCurrentIntent() {
        assertEquals(DashboardState.Mutation.ENABLE, DashboardState.afterPersistence(true, true, true, true, 3));
    }
    @Test public void failedEnableCommitMustNeverSendEnable() {
        assertEquals(DashboardState.Mutation.REJECT, DashboardState.afterPersistence(true, false, true, true, 3));
    }
    @Test public void unreadableSettingAfterCommitMustNeverSendEnable() {
        assertEquals(DashboardState.Mutation.REJECT, DashboardState.afterPersistence(true, true, false, true, 3));
    }
    @Test public void newerSavedOffPreventsAnEnableCommand() {
        assertEquals(DashboardState.Mutation.REJECT, DashboardState.afterPersistence(true, true, true, false, 4));
    }
    @Test public void invalidRevisionMustNeverSendEnable() {
        assertEquals(DashboardState.Mutation.REJECT, DashboardState.afterPersistence(true, true, true, true, -1));
    }
    @Test public void failedOffCommitStillRequestsSafeStop() {
        assertEquals(DashboardState.Mutation.STOP, DashboardState.afterPersistence(false, false, true, true, 3));
    }
    @Test public void unreadableOffSettingStillRequestsSafeStop() {
        assertEquals(DashboardState.Mutation.STOP, DashboardState.afterPersistence(false, false, false, true, 0));
    }
    @Test public void failedOffCommitUsesEmergencyRevisionEvenWhenReadShowsAnOldRevision() {
        assertEquals(-1, DashboardState.commandRevision(false, false, true, 7));
    }
    @Test public void unreadableOffSettingUsesEmergencyRevision() {
        assertEquals(-1, DashboardState.commandRevision(false, true, false, 7));
    }
    @Test public void successfulOffKeepsTheCapturedRevision() {
        assertEquals(8, DashboardState.commandRevision(false, true, true, 8));
    }
    @Test public void enableNeverBorrowsANewerRevisionOrEmergencySentinel() {
        assertEquals(9, DashboardState.commandRevision(true, true, true, 9));
    }
}
