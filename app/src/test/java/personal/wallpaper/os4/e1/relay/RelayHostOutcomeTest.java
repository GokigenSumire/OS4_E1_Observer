package personal.wallpaper.os4.e1.relay;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBinder;
import org.robolectric.shadows.ShadowProcess;
import personal.wallpaper.os4.e1.NativeSigningFixtures;
import personal.wallpaper.os4.e1.lockinput.NativeInputDetail;
import personal.wallpaper.protocol.lock.LockProtocol;
import static org.junit.Assert.*;

/** 正式HOST证书与原有CALLBACK_RESULT实际Binder封包；ACCEPTED不冒COMMITTED或EGL呈现。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
public final class RelayHostOutcomeTest {
    private LockRelayService service;private IBinder callback;private Server server;
    private void set(String name,Object value)throws Exception{Field field=LockRelayService.class.getDeclaredField(name);field.setAccessible(true);field.set(service,value);}
    private Object get(String name)throws Exception{Field field=LockRelayService.class.getDeclaredField(name);field.setAccessible(true);return field.get(service);}
    @Before public void before()throws Exception{service=Robolectric.buildService(LockRelayService.class).get();ShadowProcess.setPid(51001);NativeSigningFixtures.host(service,32001);ShadowBinder.setCallingUid(32001);ShadowBinder.setCallingPid(52001);server=new Server();set("serverSession",server);set("hostSession","host-owner");callback=(IBinder)get("hostCallback");}
    @After public void after()throws Exception{set("serverSession",null);set("hostSession",null);set("armed",false);service.onDestroy();ShadowBinder.setCallingUid(Process.myUid());ShadowBinder.setCallingPid(Process.myPid());}
    private Parcel result(String session,String id,String outcome)throws Exception{Parcel data=Parcel.obtain(),reply=Parcel.obtain();try{data.writeInterfaceToken(LockProtocol.CALLBACK);data.writeString(session);data.writeString(id);data.writeString(outcome);data.writeTypedObject(null,0);data.setDataPosition(0);assertTrue(callback.transact(LockProtocol.CALLBACK_RESULT,data,reply,0));reply.setDataPosition(0);return reply;}finally{data.recycle();}}
    private void prepare(String id)throws Exception{Class<?> type=Class.forName(LockRelayService.class.getName()+"$Pending");Constructor<?> constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);set("pending",constructor.newInstance(id,"actor",1L,1L,Long.MAX_VALUE,0L));}
    @Test public void realCommittedCallbackProducesFixedCommittedOnlyInActiveWindow()throws Exception{prepare("request");Parcel reply=result("host-owner","request","COMMITTED");try{reply.readException();}finally{reply.recycle();}assertEquals(List.of(NativeInputDetail.code("NATIVE_HOST_COMMITTED")),server.outcomes);}
    @Test public void everyFiniteHostFailureIsMappedWithoutReturningStatusText()throws Exception{int checked=0;for(String outcome:new String[]{"FAILED","STALE_TICKET","CANCELLED","EXPIRED","NO_ENGINE","TARGET_NOT_LOCK","BUSY_EDIT","UNKNOWN_STATE"}){if(checked>0){after();before();}prepare("request-"+outcome);Parcel reply=result("host-owner","request-"+outcome,outcome);try{reply.readException();}finally{reply.recycle();}assertEquals(List.of(NativeInputDetail.commandResult(outcome)),server.outcomes);checked++;}assertEquals(8,checked);}
    @Test public void acceptedOrUnknownCallbackNeverBecomesCommitted()throws Exception{prepare("request");Parcel reply=result("host-owner","request","ACCEPTED");try{reply.readException();}finally{reply.recycle();}assertEquals(List.of(NativeInputDetail.code("NATIVE_HOST_CALLBACK_INVALID")),server.outcomes);}
    @Test public void callbackOutsideActiveWindowIsNotReportedIntoFutureCapture()throws Exception{server.active=0;prepare("request");Parcel reply=result("host-owner","request","COMMITTED");try{reply.readException();}finally{reply.recycle();}server.active=1;assertTrue(server.outcomes.isEmpty());}
    @Test public void wrongOwnerOrRequestIsRejectedBeforeAnyOutcome()throws Exception{prepare("request");for(String[] identity:new String[][]{{"old-owner","request"},{"host-owner","other-request"}}){Parcel reply=result(identity[0],identity[1],"COMMITTED");try{reply.readException();fail();}catch(SecurityException expected){}finally{reply.recycle();}}assertTrue(server.outcomes.isEmpty());}
    @Test public void wrongUidCannotForgeCommittedResult()throws Exception{prepare("request");ShadowBinder.setCallingUid(32002);Parcel reply=result("host-owner","request","COMMITTED");try{reply.readException();fail();}catch(SecurityException expected){}finally{reply.recycle();}assertTrue(server.outcomes.isEmpty());}
    @Test public void authenticatedProcessPidCannotBeSwappedMidSession()throws Exception{set("hostPid",52000);prepare("request");Parcel reply=result("host-owner","request","COMMITTED");try{reply.readException();fail();}catch(SecurityException expected){}finally{reply.recycle();}assertTrue(server.outcomes.isEmpty());}
    @Test public void duplicateCompletedRequestCannotEmitSecondCommittedResult()throws Exception{prepare("request");Parcel first=result("host-owner","request","COMMITTED");try{first.readException();}finally{first.recycle();}Parcel repeated=result("host-owner","request","COMMITTED");try{repeated.readException();fail();}catch(SecurityException expected){}finally{repeated.recycle();}assertEquals(1,server.outcomes.size());}
    private static final class Server extends Binder{
        final List<Long> counts=new ArrayList<>();
        int active=1;final List<Integer> outcomes=new ArrayList<>();
        @Override protected boolean onTransact(int transaction,Parcel data,Parcel reply,int flags){data.enforceInterface(LockProtocol.SESSION);if(transaction==DiagnosticClient.STATE){reply.writeNoException();reply.writeString("capture-owner");reply.writeLong(1);reply.writeInt(active);reply.writeInt(active==1?1:2);reply.writeInt(0);reply.writeInt(0);return true;}if(transaction==11){assertEquals("capture-owner",data.readString());assertEquals(0,data.readInt());int code=data.readInt();assertTrue(NativeInputDetail.isRelayResult(code));assertFalse(NativeInputDetail.isEditResult(code));outcomes.add(code);long total=data.readLong();assertTrue(total>0);counts.add(total);assertEquals(0,data.dataAvail());reply.writeNoException();reply.writeInt(0);return true;}if(transaction==1){data.readInt();reply.writeNoException();reply.writeInt(0);return true;}return false;}
    }
    @Test public void distinctCompletedRequestsProduceIncreasingCommittedTotals()throws Exception{
        prepare("first");Parcel first=result("host-owner","first","COMMITTED");try{first.readException();}finally{first.recycle();}
        prepare("second");Parcel second=result("host-owner","second","COMMITTED");try{second.readException();}finally{second.recycle();}
        assertEquals(List.of(1L,2L),server.counts);
        assertEquals(List.of(NativeInputDetail.commandResult("COMMITTED"),NativeInputDetail.commandResult("COMMITTED")),server.outcomes);
    }
}
