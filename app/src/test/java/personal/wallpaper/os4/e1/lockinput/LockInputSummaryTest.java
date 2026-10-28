package personal.wallpaper.os4.e1.lockinput;
import org.junit.Test;
import static org.junit.Assert.*;
public final class LockInputSummaryTest {
    @Test public void onlyFiniteCodesAreAccepted(){for(int i=0;i<=17;i++)assertTrue(LockInputSummary.valid(i));assertFalse(LockInputSummary.valid(-1));assertFalse(LockInputSummary.valid(18));}
    @Test public void authReasonsAreOnlyOneCoarseClass(){assertEquals(5,LockInputSummary.fromReason("BOUNCER_SHOWING_OR_SOON"));assertEquals(5,LockInputSummary.fromReason("SIM_AUTH_REQUIRED"));}
    @Test public void emptyRoiIsNotPermissionFailure(){assertEquals(8,LockInputSummary.fromReason("POSITIVE_ROI_EMPTY_AFTER_EXCLUSIONS"));}
    @Test public void missingPropertyIsNotFalse(){assertEquals(10,LockInputSummary.fromReason("UNKNOWN_mShowing"));}
    @Test public void noReadyHostDoesNotInventRegistration(){assertEquals(2,LockInputSummary.fromReason("HOST_NOT_READY"));}
    @Test public void successfulCommandAndRoiAreDistinct(){assertEquals(3,LockInputSummary.fromReason("ALLOW_METADATA_OBSERVATION"));assertEquals(14,LockInputSummary.fromReason("COMMAND_ACCEPTED"));}
}
