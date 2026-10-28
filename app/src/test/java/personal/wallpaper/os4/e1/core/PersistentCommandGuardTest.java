package personal.wallpaper.os4.e1.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class PersistentCommandGuardTest {
    @Test public void coldOnAcceptsSavedRevision() { assertTrue(new PersistentCommandGuard().acceptOn(1)); }
    @Test public void invalidOnRejected() { assertFalse(new PersistentCommandGuard().acceptOn(-1)); }
    @Test public void lateBootOnCannotOverrideNewOff() { PersistentCommandGuard g=new PersistentCommandGuard();assertTrue(g.acceptOff(2));assertFalse(g.acceptOn(1)); }
    @Test public void sameRevisionOnCannotUndoOff() { PersistentCommandGuard g=new PersistentCommandGuard();assertTrue(g.acceptOff(2));assertFalse(g.acceptOn(2)); }
    @Test public void explicitNewUserOnAfterOffAllowed() { PersistentCommandGuard g=new PersistentCommandGuard();assertTrue(g.acceptOff(2));assertTrue(g.acceptOn(3)); }
    @Test public void failedPersistenceOffCanRevokeAtSameRevision() { PersistentCommandGuard g=new PersistentCommandGuard();assertTrue(g.acceptOn(3));assertTrue(g.acceptOff(3));assertFalse(g.acceptOn(3)); }
    @Test public void staleOffCannotOverrideNewIntent() { PersistentCommandGuard g=new PersistentCommandGuard();assertTrue(g.acceptOn(3));assertFalse(g.acceptOff(2)); }
    @Test public void offDoesNotNeedExistingOn() { PersistentCommandGuard g=new PersistentCommandGuard();assertTrue(g.acceptOff(0));assertFalse(g.acceptOn(0)); }
    @Test public void emergencyBlocksCurrentAndOlderOn() { PersistentCommandGuard g=new PersistentCommandGuard();assertTrue(g.acceptOn(9));g.emergencyStop();assertFalse(g.acceptOn(9));assertFalse(g.acceptOn(8));assertTrue(g.acceptOn(10)); }
    @Test public void repeatedOffIsIdempotentBarrier() { PersistentCommandGuard g=new PersistentCommandGuard();assertTrue(g.acceptOff(2));assertTrue(g.acceptOff(2));assertFalse(g.acceptOn(2)); }
    @Test public void acceptedRevisionIsExact() { PersistentCommandGuard g=new PersistentCommandGuard();g.acceptOn(Long.MAX_VALUE);assertEquals(Long.MAX_VALUE,g.revision());assertTrue(g.acceptOff(Long.MAX_VALUE));assertFalse(g.acceptOn(Long.MAX_VALUE)); }
    @Test public void staleRejectionDoesNotLowerRevision() { PersistentCommandGuard g=new PersistentCommandGuard();g.acceptOff(7);assertFalse(g.acceptOn(6));assertEquals(7,g.revision()); }
}
