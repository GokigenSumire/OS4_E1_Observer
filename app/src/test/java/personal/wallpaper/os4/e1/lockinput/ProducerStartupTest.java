package personal.wallpaper.os4.e1.lockinput;

import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Constructor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** 执行生产启动排队与异常隔离；不声称已在手机完成跨进程登记。 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34,manifest=Config.NONE)
public final class ProducerStartupTest {
    private LockSystemUiObserver observer;
    private Handler handler;
    @Before public void before()throws Exception{
        Constructor<LockSystemUiObserver> ctor=LockSystemUiObserver.class.getDeclaredConstructor(Class.class,ApplicationInfo.class);ctor.setAccessible(true);
        observer=ctor.newInstance(null,new ApplicationInfo());
        ((Handler)field("worker")).getLooper().quitSafely();handler=new Handler(Looper.getMainLooper());
        LockInputBroker.field(observer.getClass(),"worker").set(observer,handler);
    }
    @After public void after(){handler.removeCallbacksAndMessages(null);}
    private Object field(String name)throws Exception{return LockInputBroker.field(observer.getClass(),name).get(observer);}
    @Test public void diagnosticConnectionIsAttemptedBeforeSourceOrContractWork()throws Exception{
        final boolean[] ran={false};observer.startInitialization(()->{
            try{assertEquals(Boolean.TRUE,field("diagnosticAttempted"));assertEquals(Boolean.TRUE,field("capabilityUnsupported"));}catch(Exception error){throw new AssertionError(error);}
            ran[0]=true;
        });
        ShadowLooper.shadowMainLooper().idle();assertTrue(ran[0]);
        assertEquals(LockDiagnosticCause.code("PRODUCER_INITIALIZATION_FINISHED"),field("bootstrapDiagnostic"));
        assertNull(field("channel"));
    }
    @Test public void initializationExceptionRemainsExportableAndDoesNotEscapeWorker()throws Exception{
        observer.initializeSafely(()->{throw new LinkageError("TEST_LINKAGE_FAILURE_NOT_EXPORTED");});
        assertEquals(LockDiagnosticCause.code("PRODUCER_INITIALIZATION_FAILED"),field("bootstrapDiagnostic"));
        assertEquals(LockDiagnosticCause.code("HOOK_UNAVAILABLE"),field("hookDiagnostic"));
        assertEquals(Boolean.TRUE,field("capabilityUnsupported"));assertNull(field("identity"));assertNull(field("channel"));
        final boolean[] alive={false};handler.post(()->alive[0]=true);ShadowLooper.shadowMainLooper().idle();assertTrue(alive[0]);
    }
    @Test public void bootstrapReasonsCannotBecomeInputOrSourceAuthority(){
        for(String name:new String[]{"PRODUCER_INITIALIZATION_QUEUED","PRODUCER_INITIALIZATION_STARTED","PRODUCER_INITIALIZATION_FINISHED","PRODUCER_INITIALIZATION_FAILED","PRODUCER_SOURCE_REJECTED","PRODUCER_DIAGNOSTIC_NOT_CONTACTED","PRODUCER_DIAGNOSTIC_CONNECTED","PRODUCER_SOURCE_CHECK_PENDING","PRODUCER_SOURCE_CHECK_REJECTED"}){
            int code=LockDiagnosticCause.code(name);assertNotEquals(0,code);assertTrue(LockDiagnosticCause.validPair(LockDiagnosticCause.BOOTSTRAP,code));
            assertFalse(LockDiagnosticCause.validPair(LockDiagnosticCause.INPUT,code));assertFalse(LockDiagnosticCause.validPair(LockDiagnosticCause.SOURCE,code));
        }
        assertTrue(LockDiagnosticCause.validPair(LockDiagnosticCause.SOURCE,LockDiagnosticCause.code("SOURCE_VERIFY_STARTED")));
        assertFalse(LockDiagnosticCause.validPair(LockDiagnosticCause.INPUT,LockDiagnosticCause.code("SOURCE_VERIFY_STARTED")));
    }
    @Test public void brokerKeepsBootstrapObservationBoundedAndCollectsOnlyDuringUserWindow()throws Exception{
        Constructor<LockInputBroker> ctor=LockInputBroker.class.getDeclaredConstructor();ctor.setAccessible(true);LockInputBroker broker=ctor.newInstance();
        Handler brokerWorker=(Handler)LockInputBroker.field(broker.getClass(),"worker").get(broker);brokerWorker.getLooper().quitSafely();
        assertEquals(LockDiagnosticCause.code("PRODUCER_DIAGNOSTIC_NOT_CONTACTED"),LockInputBroker.field(broker.getClass(),"bootstrapCause").get(broker));
        LockDiagnosticRing ring=(LockDiagnosticRing)LockInputBroker.field(broker.getClass(),"diagnostic").get(broker);
        java.lang.reflect.Method report=broker.getClass().getDeclaredMethod("bootstrapStatus",String.class);report.setAccessible(true);
        report.invoke(broker,"PRODUCER_DIAGNOSTIC_CONNECTED");assertEquals(0,ring.state(0).rows);
        String token=ring.start(android.os.SystemClock.elapsedRealtime()).token;report.invoke(broker,"PRODUCER_SOURCE_CHECK_PENDING");
        LockDiagnosticRing.State snapshot=ring.stop(token,android.os.SystemClock.elapsedRealtime());assertEquals(1,snapshot.rows);
        assertTrue(ring.page(token,snapshot.revision,0,android.os.SystemClock.elapsedRealtime()).get(0).contains("stage=BOOTSTRAP cause=PRODUCER_SOURCE_CHECK_PENDING"));
        report.invoke(broker,"PRODUCER_SOURCE_CHECK_REJECTED");assertEquals(1,ring.state(android.os.SystemClock.elapsedRealtime()).rows);
    }
}
