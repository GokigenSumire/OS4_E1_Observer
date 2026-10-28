package personal.wallpaper.os4.e1.lockinput;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ProducerSessionGuardTest {
    @Test public void dedicatedSystemUiUidUsesTheSameExactGenerationContract(){
        var value=id(71,10225,3,"com.android.systemui","com.android.systemui",record);
        assertFalse(ProducerSessionGuard.isExactSystemUi(value));
        assertTrue(ProducerSessionGuard.isExactSystemUi(value,10225));
        assertFalse(ProducerSessionGuard.isExactSystemUi(value,10226));
        var guard=new ProducerSessionGuard(value,10225);assertTrue(guard.accepts(71,10225,value));
        assertFalse(guard.accepts(71,1000,value));assertFalse(guard.accepts(72,10225,value));
        assertFalse(guard.accepts(71,10225,id(71,10225,4,"com.android.systemui","com.android.systemui",record)));
        assertFalse(guard.accepts(71,10225,id(71,10225,3,"com.android.systemui","com.android.systemui",new Object())));
    }
    private final Object record = new Object();
    private ProducerSessionGuard.Identity id(int pid, int uid, long seq, String name, String pkg, Object rec) {
        return new ProducerSessionGuard.Identity(pid,uid,seq,name,pkg,rec);
    }
    private ProducerSessionGuard.Identity valid() { return id(71,1000,3,"com.android.systemui","com.android.systemui",record); }
    @Test public void acceptsExactKernelCallerAndGeneration() { assertTrue(new ProducerSessionGuard(valid()).accepts(71,1000,valid())); }
    @Test public void rejectsSharedUidDifferentPid() { assertFalse(new ProducerSessionGuard(valid()).accepts(72,1000,valid())); }
    @Test public void rejectsDifferentUid() { assertFalse(new ProducerSessionGuard(valid()).accepts(71,1001,valid())); }
    @Test public void rejectsRecycledPidNewRecord() { assertFalse(new ProducerSessionGuard(valid()).accepts(71,1000,id(71,1000,3,"com.android.systemui","com.android.systemui",new Object()))); }
    @Test public void rejectsRecycledPidNewSequence() { assertFalse(new ProducerSessionGuard(valid()).accepts(71,1000,id(71,1000,4,"com.android.systemui","com.android.systemui",record))); }
    @Test public void rejectsOtherProcessOfSystemUi() { assertFalse(ProducerSessionGuard.isExactSystemUi(id(71,1000,3,"com.android.systemui:aux","com.android.systemui",record))); }
    @Test public void rejectsOtherPackageOfUid1000() { assertFalse(ProducerSessionGuard.isExactSystemUi(id(71,1000,3,"com.android.systemui","android",record))); }
    @Test public void rejectsNoRecord() { assertFalse(ProducerSessionGuard.isExactSystemUi(id(71,1000,3,"com.android.systemui","com.android.systemui",null))); }
    @Test public void rejectsMissingGeneration() { assertFalse(ProducerSessionGuard.isExactSystemUi(id(71,1000,0,"com.android.systemui","com.android.systemui",record))); }
    @Test public void rejectsZeroPid() { assertFalse(ProducerSessionGuard.isExactSystemUi(id(0,1000,3,"com.android.systemui","com.android.systemui",record))); }
    @Test public void deathRevokesPermanently() { ProducerSessionGuard guard=new ProducerSessionGuard(valid()); guard.revoke(); assertFalse(guard.accepts(71,1000,valid())); }
    @Test public void rejectsMissingCurrentRecord() { assertFalse(new ProducerSessionGuard(valid()).accepts(71,1000,null)); }
    @Test(expected=IllegalArgumentException.class) public void registrationRejectsSharedUidWithoutProcess() { new ProducerSessionGuard(id(71,1000,3,"android","android",record)); }
}
