package personal.wallpaper.os4.e1.lockinput;

import org.junit.Test;
import static org.junit.Assert.*;

public final class MonotonicObservationTest {
    @Test public void noObservationIsNotAuthority(){assertNull(new MonotonicObservation<String>().snapshot());}
    @Test public void pendingNewRequestMakesPriorObservationUnavailable(){MonotonicObservation<String> store=new MonotonicObservation<>();long first=store.beginRequest();assertTrue(store.publish(first,"old"));assertEquals("old",store.snapshot());store.beginRequest();assertNull(store.snapshot());}
    @Test public void slowerOlderResponseCannotOverwriteFresh(){MonotonicObservation<String> store=new MonotonicObservation<>();long older=store.beginRequest(),fresh=store.beginRequest();assertTrue(store.publish(fresh,"fresh"));assertFalse(store.publish(older,"old"));assertEquals("fresh",store.snapshot());}
    @Test public void olderResponseCannotGrantWhileNewerRequestUnfinished(){MonotonicObservation<String> store=new MonotonicObservation<>();long older=store.beginRequest();store.beginRequest();assertFalse(store.publish(older,"old"));assertNull(store.snapshot());}
    @Test public void invalidationRejectsEveryInFlightResponse(){MonotonicObservation<String> store=new MonotonicObservation<>();long pending=store.beginRequest();store.invalidate();assertFalse(store.publish(pending,"old"));assertNull(store.snapshot());}
    @Test public void laterValidObservationMayRecoverAfterInvalidation(){MonotonicObservation<String> store=new MonotonicObservation<>();store.invalidate();long next=store.beginRequest();assertTrue(store.publish(next,"ready"));assertEquals("ready",store.snapshot());}
    @Test public void zeroNegativeAndFuturePublicationRejected(){MonotonicObservation<String> store=new MonotonicObservation<>();long next=store.beginRequest();assertFalse(store.publish(0,"zero"));assertFalse(store.publish(-1,"negative"));assertFalse(store.publish(next+1,"future"));assertNull(store.snapshot());}
    @Test public void foregroundRequestCannotBePreemptedByBackgroundPoll(){MonotonicObservation<String> store=new MonotonicObservation<>();long foreground=store.beginRequest(true);assertEquals(-1,store.beginRequest());assertTrue(store.publish(foreground,"fresh"));store.finishRequest(foreground);assertEquals("fresh",store.snapshot());assertTrue(store.beginRequest()>foreground);}
    @Test public void foregroundSupersedesOldBackgroundWithoutOldFinishReleasingLease(){MonotonicObservation<String> store=new MonotonicObservation<>();long older=store.beginRequest();long foreground=store.beginRequest(true);store.finishRequest(older);assertFalse(store.publish(older,"old"));assertEquals(-1,store.beginRequest());store.finishRequest(foreground);assertTrue(store.beginRequest()>foreground);}
    @Test public void disconnectCancelsForegroundLeaseAndEveryOldResponse(){MonotonicObservation<String> store=new MonotonicObservation<>();long foreground=store.beginRequest(true);store.invalidate();assertFalse(store.publish(foreground,"old"));assertTrue(store.beginRequest()>foreground);}
}
