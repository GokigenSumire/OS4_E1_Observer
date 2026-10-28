package personal.wallpaper.os4.e1.lockinput;

import org.junit.Test;
import static org.junit.Assert.*;

/** 非作者定向对抗：只诊断登记不升级为生产身份或播放能力。 */
public final class LockDiagnosticPrivilegeIndependentTest {
    @Test public void diagnosticRoleHasOnlyReportAndQueryAcrossAllNearbyTransactionValues() {
        for (int code = -32; code <= 64; code++)
            assertEquals("tx=" + code, code == 11 || code == 12, LockDiagnosticRolePolicy.allows(3, code));
        assertFalse(LockDiagnosticRolePolicy.allows(3, Integer.MIN_VALUE));
        assertFalse(LockDiagnosticRolePolicy.allows(3, Integer.MAX_VALUE));
    }
    @Test public void moduleAndProducerCannotBorrowEachOthersDiagnosticOrPlaybackRights() {
        assertFalse(LockDiagnosticRolePolicy.allows(1, 3));
        assertFalse(LockDiagnosticRolePolicy.allows(1, 5));
        assertTrue(LockDiagnosticRolePolicy.allows(1, 11)); // 仅新增固定编辑结果，Broker仍逐stage/cause约束。
        assertFalse(LockDiagnosticRolePolicy.allows(1, 12));
        for (int code = 7; code <= 10; code++) {
            assertTrue(LockDiagnosticRolePolicy.allows(1, code));
            assertFalse(LockDiagnosticRolePolicy.allows(2, code));
        }
        assertFalse(LockDiagnosticRolePolicy.allows(2, 1));
        assertFalse(LockDiagnosticRolePolicy.allows(2, 4));
        assertFalse(LockDiagnosticRolePolicy.allows(2, 6));
        for (int role : new int[]{-1, 0, 4, Integer.MAX_VALUE})
            for (int code = 1; code <= 12; code++) assertFalse(LockDiagnosticRolePolicy.allows(role, code));
    }
    @Test public void uidOneThousandByItselfIsNeverExactSystemUi() {
        Object record = new Object();
        assertFalse(ProducerSessionGuard.isExactSystemUi(identity(91,1000,7,"android","android",record)));
        assertFalse(ProducerSessionGuard.isExactSystemUi(identity(91,1000,7,"com.android.systemui:secondary","com.android.systemui",record)));
        assertFalse(ProducerSessionGuard.isExactSystemUi(identity(91,10000,7,"com.android.systemui","com.android.systemui",record)));
        assertFalse(ProducerSessionGuard.isExactSystemUi(identity(91,1000,0,"com.android.systemui","com.android.systemui",record)));
        assertFalse(ProducerSessionGuard.isExactSystemUi(identity(91,1000,7,"com.android.systemui","com.android.systemui",null)));
    }
    @Test public void reusedPidDifferentStartOrRecordCannotReuseLowPrivilegeRegistration() {
        Object record = new Object();
        ProducerSessionGuard.Identity admitted = identity(91,1000,7,"com.android.systemui","com.android.systemui",record);
        ProducerSessionGuard guard = new ProducerSessionGuard(admitted);
        assertTrue(guard.accepts(91,1000,admitted));
        assertFalse(guard.accepts(92,1000,admitted));
        assertFalse(guard.accepts(91,10001,admitted));
        assertFalse(guard.accepts(91,1000,identity(91,1000,8,"com.android.systemui","com.android.systemui",record)));
        assertFalse(guard.accepts(91,1000,identity(91,1000,7,"com.android.systemui","com.android.systemui",new Object())));
        guard.revoke(); assertFalse(guard.accepts(91,1000,admitted));
    }
    @Test public void diagnosticClosedOrStoppedDoesNotAccumulateInputRows() {
        LockDiagnosticRing ring = new LockDiagnosticRing();
        assertFalse(ring.record("NONE",0,LockDiagnosticCause.INPUT,LockDiagnosticCause.code("HOST_NOT_READY"),1));
        assertEquals(0,ring.state(0).rows);
        LockDiagnosticRing.State started = ring.start(1);
        assertTrue(ring.record(started.token,2,LockDiagnosticCause.INPUT,LockDiagnosticCause.code("HOST_NOT_READY"),1));
        LockDiagnosticRing.State stopped = ring.stop(started.token,3);
        assertFalse(ring.record(started.token,4,LockDiagnosticCause.INPUT,LockDiagnosticCause.code("BOUNCER_SHOWING"),2));
        assertEquals(stopped.rows,ring.state(4).rows); assertEquals(stopped.revision,ring.state(4).revision);
        assertEquals(stopped.bytes,ring.state(4).bytes); assertFalse(ring.state(4).active);
    }
    @Test public void fixedSourceLoaderAndHookCausesCannotBeRelabelledAsInput() {
        int source = LockDiagnosticCause.code("SOURCE_SHA_MISMATCH");
        int loader = LockDiagnosticCause.code("DECLARING_LOADER_NOT_MATCHED");
        int hook = LockDiagnosticCause.code("HOOK_UNAVAILABLE");
        assertTrue(LockDiagnosticCause.validPair(LockDiagnosticCause.SOURCE,source));
        assertTrue(LockDiagnosticCause.validPair(LockDiagnosticCause.LOADER,loader));
        assertTrue(LockDiagnosticCause.validPair(LockDiagnosticCause.HOOK,hook));
        assertFalse(LockDiagnosticCause.validPair(LockDiagnosticCause.INPUT,source));
        assertFalse(LockDiagnosticCause.validPair(LockDiagnosticCause.INPUT,loader));
        assertFalse(LockDiagnosticCause.validPair(LockDiagnosticCause.INPUT,hook));
        assertFalse(LockDiagnosticCause.validPair(LockDiagnosticCause.SOURCE,LockDiagnosticCause.code("BOUNCER_SHOWING")));
        assertFalse(LockDiagnosticCause.validPair(99,source));
        assertFalse(LockDiagnosticCause.validPair(LockDiagnosticCause.INPUT,Integer.MAX_VALUE));
    }
    private static ProducerSessionGuard.Identity identity(int pid,int uid,long start,String process,String pkg,Object record) {
        return new ProducerSessionGuard.Identity(pid,uid,start,process,pkg,record);
    }
}
