package personal.wallpaper.os4.e1.relay;

import org.junit.Test;
import static org.junit.Assert.*;

public final class DurableSwitchGateTest {
    @Test public void initialReadUsesStoredValue() {
        DurableSwitchGate gate = new DurableSwitchGate();
        assertTrue(gate.confirmed()); assertTrue(gate.allows(true)); assertFalse(gate.allows(false));
    }
    @Test public void pendingWriteNeverAuthorizesInMemoryTrue() {
        DurableSwitchGate gate = new DurableSwitchGate(); gate.beginWrite();
        assertFalse(gate.confirmed()); assertFalse(gate.allows(true)); assertFalse(gate.allows(false));
    }
    @Test public void failedCommitWithUpdatedMemoryTrueRemainsDisabled() {
        DurableSwitchGate gate = new DurableSwitchGate(); gate.beginWrite(); gate.finishedWrite(false);
        assertFalse(gate.confirmed()); assertFalse(gate.allows(true));
    }
    @Test public void confirmedWriteRestoresExactStoredChoice() {
        DurableSwitchGate gate = new DurableSwitchGate(); gate.beginWrite(); gate.finishedWrite(true);
        assertTrue(gate.confirmed()); assertTrue(gate.allows(true)); assertFalse(gate.allows(false));
    }
    @Test public void successfulRetryAfterFailureCanEnable() {
        DurableSwitchGate gate = new DurableSwitchGate(); gate.beginWrite(); gate.finishedWrite(false);
        gate.beginWrite(); gate.finishedWrite(true);
        assertTrue(gate.allows(true));
    }
}
