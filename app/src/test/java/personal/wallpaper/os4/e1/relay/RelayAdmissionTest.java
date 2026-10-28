package personal.wallpaper.os4.e1.relay;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RelayAdmissionTest {
    private int admit(RelayAdmission a, boolean enabled, boolean ready, int action, long seq, long issued, long epoch, String session, String ticket, long now) {
        return a.admit(enabled, ready, action, seq, issued, epoch, session, ticket, now);
    }
    @Test public void acceptsOneValidEvent() {
        assertEquals(0, admit(new RelayAdmission(), true, true, 1, 1, 100, 1, "producer", "ticket", 101));
    }
    @Test public void disabledAndNoEngineNeverAccept() {
        assertEquals(1, admit(new RelayAdmission(), false, true, 1, 1, 100, 1, "p", "t", 101));
        assertEquals(1, admit(new RelayAdmission(), true, false, 1, 1, 100, 1, "p", "t", 101));
    }
    @Test public void concurrentEventIsBusyRatherThanQueued() {
        RelayAdmission a = new RelayAdmission(); a.admit(true, true, 1, 1, 100, 1, "p", "t", 101);
        assertEquals(3, a.admit(true, true, 1, 2, 100, 1, "p", "t", 101));
    }
    @Test public void completedEventAllowsNextButNotReplay() {
        RelayAdmission a = new RelayAdmission(); a.admit(true, true, 1, 1, 100, 1, "p", "t", 101); a.complete();
        assertEquals(2, a.admit(true, true, 1, 1, 100, 1, "p", "t", 101));
        assertEquals(0, a.admit(true, true, 1, 2, 100, 1, "p", "t", 101));
    }
    @Test public void invalidProducerEpochAndTokensReject() {
        assertEquals(2, admit(new RelayAdmission(), true, true, 1, 1, 100, 0, "p", "t", 101));
        assertEquals(2, admit(new RelayAdmission(), true, true, 1, 1, 100, 1, "p/x", "t", 101));
        assertEquals(2, admit(new RelayAdmission(), true, true, 1, 1, 100, 1, "p", "content://private", 101));
    }
    @Test public void invalidSequenceAndActionReject() {
        for (long seq : new long[]{-1, 0, Long.MAX_VALUE})
            assertEquals(2, admit(new RelayAdmission(), true, true, 1, seq, 100, 1, "p", "t", 101));
        assertEquals(2, admit(new RelayAdmission(), true, true, 4, 1, 100, 1, "p", "t", 101));
    }
    @Test public void elapsedTtlIsSeparateForEdit() {
        assertEquals(2, admit(new RelayAdmission(), true, true, 1, 1, 100, 1, "p", "t", 1101));
        assertEquals(0, admit(new RelayAdmission(), true, true, 3, 1, 100, 1, "p", "t", 5100));
        assertEquals(2, admit(new RelayAdmission(), true, true, 3, 1, 100, 1, "p", "t", 5101));
    }
    @Test public void futureAndNegativeTimesReject() {
        assertEquals(2, admit(new RelayAdmission(), true, true, 1, 1, -1, 1, "p", "t", 101));
        assertEquals(2, admit(new RelayAdmission(), true, true, 1, 1, 102, 1, "p", "t", 101));
    }
    @Test public void differentProducerEpochStartsIndependentSequence() {
        RelayAdmission a = new RelayAdmission(); a.admit(true, true, 1, 2, 100, 1, "p", "t", 101); a.complete();
        assertEquals(0, a.admit(true, true, 1, 1, 100, 2, "p", "t", 101));
    }
    @Test public void sessionsBoundedAndClearReleasesBusy() {
        RelayAdmission a = new RelayAdmission();
        for (int i = 1; i < 20; i++) { assertEquals(0, a.admit(true, true, 1, 1, 100, i, "p", "t", 101)); a.complete(); }
        assertEquals(8, a.size()); a.clear(); assertEquals(0, a.size());
        assertEquals(0, a.admit(true, true, 1, 1, 100, 1, "p", "t", 101));
    }
}
