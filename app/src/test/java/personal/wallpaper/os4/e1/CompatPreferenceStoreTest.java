package personal.wallpaper.os4.e1;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompatPreferenceStoreTest {
    private static final class Memory implements CompatPreferenceStore.Storage {
        boolean enabled, fail, throwRead, throwWrite;
        long revision;
        int writes;
        public boolean enabled() { if (throwRead) throw new IllegalStateException(); return enabled; }
        public long revision() { if (throwRead) throw new IllegalStateException(); return revision; }
        public boolean commit(boolean desired, long next) {
            writes++;
            if (throwWrite) throw new IllegalStateException();
            if (fail) return false;
            enabled=desired; revision=next; return true;
        }
    }
    @Test public void newInstallationDefaultsOff() { Memory m=new Memory();assertFalse(CompatPreferenceStore.read(m).enabled);assertTrue(CompatPreferenceStore.read(m).available);assertEquals(0,CompatPreferenceStore.read(m).revision); }
    @Test public void readNeverWrites() { Memory m=new Memory();CompatPreferenceStore.read(m);CompatPreferenceStore.read(m);assertEquals(0,m.writes); }
    @Test public void onIsSavedWithNewRevision() { Memory m=new Memory();assertTrue(CompatPreferenceStore.setEnabled(m,true));assertTrue(m.enabled);assertEquals(1,m.revision); }
    @Test public void offIsSavedWithNewRevision() { Memory m=new Memory();m.enabled=true;m.revision=4;assertTrue(CompatPreferenceStore.setEnabled(m,false));assertFalse(m.enabled);assertEquals(5,m.revision); }
    @Test public void repeatUserEnableAdvancesRevision() { Memory m=new Memory();assertTrue(CompatPreferenceStore.setEnabled(m,true));assertTrue(CompatPreferenceStore.setEnabled(m,true));assertEquals(2,m.revision); }
    @Test public void failedCommitIsNotReportedSuccessful() { Memory m=new Memory();m.fail=true;assertFalse(CompatPreferenceStore.setEnabled(m,true));assertFalse(m.enabled);assertEquals(0,m.revision); }
    @Test public void failedOffDoesNotClaimPersistedOff() { Memory m=new Memory();m.enabled=true;m.revision=2;m.fail=true;assertFalse(CompatPreferenceStore.setEnabled(m,false));assertTrue(CompatPreferenceStore.read(m).enabled);assertEquals(2,m.revision); }
    @Test public void corruptRevisionIsUnavailableNotOff() { Memory m=new Memory();m.revision=-1;assertFalse(CompatPreferenceStore.read(m).available);assertEquals(-1,CompatPreferenceStore.read(m).revision); }
    @Test public void readFailureIsUnavailable() { Memory m=new Memory();m.throwRead=true;assertFalse(CompatPreferenceStore.read(m).available); }
    @Test public void unreadablePreferenceCannotEnable() { Memory m=new Memory();m.throwRead=true;assertFalse(CompatPreferenceStore.setEnabled(m,true));assertEquals(0,m.writes); }
    @Test public void maximumRevisionDoesNotWrap() { Memory m=new Memory();m.revision=Long.MAX_VALUE;assertFalse(CompatPreferenceStore.setEnabled(m,true));assertEquals(0,m.writes); }
    @Test public void nearMaximumAdvancesExactlyOnce() { Memory m=new Memory();m.revision=Long.MAX_VALUE-1;assertTrue(CompatPreferenceStore.setEnabled(m,true));assertEquals(Long.MAX_VALUE,m.revision);assertFalse(CompatPreferenceStore.setEnabled(m,false)); }
    @Test public void writeExceptionIsFailure() { Memory m=new Memory();m.throwWrite=true;assertFalse(CompatPreferenceStore.setEnabled(m,true)); }
    @Test public void failedCommitWithChangedMemoryStaysUnconfirmedForNewActivity() {
        CompatPreferenceStore.Storage androidLike=new CompatPreferenceStore.Storage(){
            boolean memory=true;long revision=3;
            public boolean enabled(){return memory;}
            public long revision(){return revision;}
            public boolean commit(boolean enabled,long next){memory=enabled;revision=next;return false;}
        };
        CompatPreferenceStore.Session process=new CompatPreferenceStore.Session();
        assertFalse(process.setEnabled(androidLike,false));
        assertFalse(process.read(androidLike).available);
        assertTrue(process.read(androidLike).writeUnconfirmed);
        // 模拟新Activity再次read同一进程Session，而不是重新创建确认状态。
        assertFalse(process.read(androidLike).available);
        assertFalse(process.read(androidLike).enabled);
    }
    @Test public void successfulRetryClearsProcessUnconfirmedFlag() {Memory m=new Memory();CompatPreferenceStore.Session p=new CompatPreferenceStore.Session();m.fail=true;assertFalse(p.setEnabled(m,false));assertTrue(p.read(m).writeUnconfirmed);m.fail=false;assertTrue(p.setEnabled(m,false));assertTrue(p.read(m).available);assertFalse(p.read(m).writeUnconfirmed);}
    @Test public void readAloneCannotClearUnconfirmedFlag() {Memory m=new Memory();CompatPreferenceStore.Session p=new CompatPreferenceStore.Session();p.failedWrite();assertFalse(p.read(m).available);assertFalse(p.read(m).available);assertTrue(p.read(m).writeUnconfirmed);}
    @Test public void newProcessCanReadActualDiskWithoutOldUnconfirmedCache() {Memory disk=new Memory();disk.enabled=true;CompatPreferenceStore.Session old=new CompatPreferenceStore.Session();old.failedWrite();assertFalse(old.read(disk).available);CompatPreferenceStore.Session fresh=new CompatPreferenceStore.Session();assertTrue(fresh.read(disk).available);assertTrue(fresh.read(disk).enabled);}
    @Test public void writeFailureBeforeStorageCreationIsAlsoSticky() {CompatPreferenceStore.Session p=new CompatPreferenceStore.Session();p.failedWrite();assertFalse(p.unavailable().available);assertTrue(p.unavailable().writeUnconfirmed);assertFalse(p.read(new Memory()).available);}
}
