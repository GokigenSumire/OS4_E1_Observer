package personal.wallpaper.os4.e1.core;
import org.junit.Test;
import static org.junit.Assert.*;
public final class HomeForwardingEvidenceTest {
    @Test public void emptyEvidenceIsNotProofOfMissingNativeInput(){assertEquals("INSUFFICIENT_EVIDENCE",new HomeForwardingEvidence().status());}
    @Test public void onlyCurrentAdmittedGetterCanReportCondition(){var evidence=new HomeForwardingEvidence();evidence.observe(true,true);assertEquals("NATIVE_FORWARDING_ALLOWED",evidence.status());evidence.observe(false,true);assertEquals("FORWARDING_MISSING",evidence.status());evidence.observe(false,false);assertEquals("INSUFFICIENT_EVIDENCE",evidence.status());}
    @Test public void unknownDoesNotBecomeFalse(){var evidence=new HomeForwardingEvidence();evidence.observe(null,true);assertEquals("INSUFFICIENT_EVIDENCE",evidence.status());evidence.observe("false",true);assertEquals("INSUFFICIENT_EVIDENCE",evidence.status());}
    @Test public void unavailableAndNewSessionAreDifferent(){var evidence=new HomeForwardingEvidence();evidence.unavailable();assertEquals("INTERFACE_UNAVAILABLE",evidence.status());evidence.reset();assertEquals("INSUFFICIENT_EVIDENCE",evidence.status());}
}
