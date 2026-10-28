package personal.wallpaper.os4.e1.lockinput;

import android.content.pm.ApplicationInfo;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

/** 实际Handler/Parcel发送路径；不执行真实跨进程或OEM输入。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class RoiDiagnosticObserverTest {
    private LockSystemUiObserver observer;
    private Object channel,active;
    private Class<?> stateType;
    private Method report;
    private final List<Integer> delivered=new ArrayList<>();
    private int rejectRemaining,attempts;
    private final int[] details={RoiDiagnosticDetail.MODE_FORCE,RoiDiagnosticDetail.SINGLE_COVER,RoiDiagnosticDetail.code(true,true,2,2,0)};
    @Before public void before()throws Exception{
        Constructor<?> constructor=LockSystemUiObserver.class.getDeclaredConstructor(Class.class,ApplicationInfo.class);constructor.setAccessible(true);observer=(LockSystemUiObserver)constructor.newInstance(null,new ApplicationInfo());
        Field worker=LockSystemUiObserver.class.getDeclaredField("worker");worker.setAccessible(true);Handler old=(Handler)worker.get(observer);old.getLooper().quitSafely();worker.set(observer,new Handler(Looper.getMainLooper()));
        IBinder binder=new Binder(){@Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){assertEquals(11,code);data.enforceInterface(LockInputBroker.SESSION);assertEquals("session-one",data.readString());assertEquals(0,data.readInt());int cause=data.readInt();assertEquals(1,data.readLong());assertEquals(0,data.dataAvail());attempts++;reply.writeNoException();if(rejectRemaining>0){rejectRemaining--;reply.writeInt(2);}else{delivered.add(cause);reply.writeInt(0);}return true;}};
        Class<?> channelType=Class.forName(LockSystemUiObserver.class.getName()+"$Channel");Constructor<?> channelConstructor=channelType.getDeclaredConstructor(IBinder.class,int.class,String.class,long.class,String.class);channelConstructor.setAccessible(true);channel=channelConstructor.newInstance(binder,4321,"server",1L,"actor");
        stateType=Class.forName(LockSystemUiObserver.class.getName()+"$DiagnosticState");active=state(true,"session-one");set("diagnosticChannel",channel);set("diagnosticState",active);
        report=LockSystemUiObserver.class.getDeclaredMethod("reportRoiDetails",LockSurfaceSnapshot.class,stateType);report.setAccessible(true);
    }
    private void set(String name,Object value)throws Exception{Field field=LockSystemUiObserver.class.getDeclaredField(name);field.setAccessible(true);field.set(observer,value);}
    private Object state(boolean on,String token)throws Exception{Constructor<?> constructor=stateType.getDeclaredConstructors()[0];constructor.setAccessible(true);return constructor.newInstance(channel,on,token);}
    private LockSurfaceSnapshot snapshot()throws Exception{return snapshot(details);}
    private LockSurfaceSnapshot snapshot(int[] codes)throws Exception{Constructor<?> constructor=LockSurfaceSnapshot.class.getDeclaredConstructor(boolean.class,String.class,Rect.class,List.class,int.class,int.class,boolean.class,int[].class);constructor.setAccessible(true);return (LockSurfaceSnapshot)constructor.newInstance(false,"POSITIVE_ROI_EMPTY_AFTER_EXCLUSIONS",null,List.of(),0,0,true,codes);}
    @Test public void realWorkerBatchSendsEveryFixedCauseOnceAndDeduplicates()throws Exception{
        report.invoke(observer,snapshot(),active);report.invoke(observer,snapshot(),active);assertTrue(delivered.isEmpty());ShadowLooper.idleMainLooper();assertEquals(List.of(details[0],details[1],details[2],RoiDiagnosticDetail.BATCH_COMPLETE),delivered);report.invoke(observer,snapshot(),active);ShadowLooper.idleMainLooper();assertEquals(4,delivered.size());
    }
    @Test public void stoppingBeforeWorkerRunsDropsEntireOldBatch()throws Exception{report.invoke(observer,snapshot(),active);set("diagnosticState",state(false,"session-one"));ShadowLooper.idleMainLooper();assertTrue(delivered.isEmpty());}
    @Test public void replacingDiagnosticSessionCannotSendOldDetails()throws Exception{report.invoke(observer,snapshot(),active);set("diagnosticState",state(true,"session-two"));ShadowLooper.idleMainLooper();assertTrue(delivered.isEmpty());}
    @Test public void inactiveAndLegacySnapshotsDoNotQueueExtraIpc()throws Exception{set("diagnosticState",state(false,"session-one"));report.invoke(observer,snapshot(),active);set("diagnosticState",active);report.invoke(observer,LockSurfaceSnapshot.denied("OLD"),active);ShadowLooper.idleMainLooper();assertTrue(delivered.isEmpty());}
    @Test public void largeBatchUsesTwentyFourPerWindowAndEndsWithCompleteMarker()throws Exception{
        int[] codes=new int[51];for(int i=0;i<codes.length;i++)codes[i]=RoiDiagnosticDetail.code(true,false,3,i/24,i%24);
        report.invoke(observer,snapshot(codes),active);ShadowLooper.idleMainLooper();assertEquals(24,delivered.size());
        ShadowLooper.idleMainLooper(1200,TimeUnit.MILLISECONDS);assertEquals(48,delivered.size());ShadowLooper.idleMainLooper(1200,TimeUnit.MILLISECONDS);assertEquals(52,delivered.size());assertEquals(Integer.valueOf(RoiDiagnosticDetail.BATCH_COMPLETE),delivered.get(51));
    }
    @Test public void stopBetweenChunksCancelsRemainingRowsAndCompletion()throws Exception{
        int[] codes=new int[51];for(int i=0;i<codes.length;i++)codes[i]=RoiDiagnosticDetail.code(true,false,3,i/24,i%24);
        report.invoke(observer,snapshot(codes),active);ShadowLooper.idleMainLooper();assertEquals(24,delivered.size());set("diagnosticState",state(false,"session-one"));ShadowLooper.idleMainLooper(1200,TimeUnit.MILLISECONDS);assertEquals(24,delivered.size());assertFalse(delivered.contains(RoiDiagnosticDetail.BATCH_COMPLETE));
    }
    @Test public void rejectedRowRetriesBeforeAdvancingAndOnlyThenConfirmsCompletion()throws Exception{
        rejectRemaining=1;report.invoke(observer,snapshot(),active);ShadowLooper.idleMainLooper();assertTrue(delivered.isEmpty());assertEquals(1,attempts);ShadowLooper.idleMainLooper(1200,TimeUnit.MILLISECONDS);assertEquals(List.of(details[0],details[1],details[2],RoiDiagnosticDetail.BATCH_COMPLETE),delivered);assertEquals(5,attempts);
    }
    @Test public void permanentRejectionStopsAfterThreeAttemptsWithoutFalseCompletion()throws Exception{
        rejectRemaining=100;report.invoke(observer,snapshot(),active);ShadowLooper.idleMainLooper();ShadowLooper.idleMainLooper(1200,TimeUnit.MILLISECONDS);ShadowLooper.idleMainLooper(1200,TimeUnit.MILLISECONDS);assertEquals(3,attempts);assertTrue(delivered.isEmpty());ShadowLooper.idleMainLooper(10000,TimeUnit.MILLISECONDS);assertEquals(3,attempts);
    }
}
