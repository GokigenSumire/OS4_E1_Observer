package personal.wallpaper.os4.e1.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class PersistentControlEpochTest {
    @Test public void coldQueryCanAuthorizeFirstOn() {assertTrue(new PersistentControlEpoch().acceptsOn(0));}
    @Test public void negativeTicketRejected() {assertFalse(new PersistentControlEpoch().acceptsOn(-1));}
    @Test public void unknownRevisionOffInvalidatesPendingBootOn() {
        PersistentControlEpoch tickets=new PersistentControlEpoch();PersistentCommandGuard desired=new PersistentCommandGuard();
        long oldBootTicket=tickets.current();desired.emergencyStop();tickets.revoke();
        assertFalse(tickets.acceptsOn(oldBootTicket));
        // revision本身无法识别尚未到达的未来值，必须同时拒绝旧控制票据。
        assertTrue(desired.acceptOn(1));
    }
    @Test public void normalOffAlsoInvalidatesPendingOn() {PersistentControlEpoch e=new PersistentControlEpoch();long old=e.current();e.revoke();assertFalse(e.acceptsOn(old));}
    @Test public void repeatedOffNeverReusesTicket() {PersistentControlEpoch e=new PersistentControlEpoch();assertEquals(1,e.revoke());assertEquals(2,e.revoke());}
    @Test public void freshQueryAfterOffCanAuthorizeExplicitNewOn() {PersistentControlEpoch e=new PersistentControlEpoch();e.revoke();assertTrue(e.acceptsOn(e.current()));assertEquals(2,e.advance());}
    @Test public void startInvalidatesOtherPendingStart() {PersistentControlEpoch e=new PersistentControlEpoch();e.advance();assertFalse(e.acceptsOn(0));}
    @Test public void overflowDoesNotWrapOrAuthorizeOn() {PersistentControlEpoch e=new PersistentControlEpoch(Long.MAX_VALUE);assertFalse(e.acceptsOn(Long.MAX_VALUE));assertEquals(Long.MAX_VALUE,e.revoke());assertFalse(e.acceptsOn(Long.MAX_VALUE));assertEquals(-1,e.advance());}
    @Test public void lastRepresentableAdvanceIsNotNewStartTicket() {PersistentControlEpoch e=new PersistentControlEpoch(Long.MAX_VALUE-1);assertTrue(e.acceptsOn(Long.MAX_VALUE-1));assertEquals(Long.MAX_VALUE,e.advance());assertFalse(e.acceptsOn(Long.MAX_VALUE));}
    @Test public void revocationAtLimitStillLeavesWatchdogGenerationStable() {PersistentControlEpoch e=new PersistentControlEpoch(Long.MAX_VALUE);assertEquals(Long.MAX_VALUE,e.revoke());assertEquals(Long.MAX_VALUE,e.current());}
    @Test(expected=IllegalArgumentException.class) public void negativeInitialRejected() {new PersistentControlEpoch(-1);}
}
