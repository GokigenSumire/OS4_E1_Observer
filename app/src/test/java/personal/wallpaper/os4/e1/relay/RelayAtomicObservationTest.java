package personal.wallpaper.os4.e1.relay;

import android.os.Binder;
import android.os.Parcel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowProcess;
import personal.wallpaper.os4.e1.NativeSigningFixtures;
import personal.wallpaper.protocol.lock.LockProtocol;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
public final class RelayAtomicObservationTest {
    private LockRelayService service;
    @Before public void before()throws Exception{service=Robolectric.buildService(LockRelayService.class).get();ShadowProcess.setPid(51001);NativeSigningFixtures.host(service,32001);assertTrue(LockCallerAuth.host(service,32001,51001));assertTrue(LockRelayService.setDesiredEnabled(service,true));set("hostSession","owner");}
    private void set(String name,Object value)throws Exception{Field field=LockRelayService.class.getDeclaredField(name);field.setAccessible(true);field.set(service,value);}
    private Object get(Object owner,String name)throws Exception{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private void refresh()throws Exception{Method method=LockRelayService.class.getDeclaredMethod("refreshNativeHostState");method.setAccessible(true);method.invoke(service);}
    @Test public void slowerOlderNotReadyCannotOverwriteLatestReadyTuple()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
        Binder host=new Binder(){int calls;@Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){assertEquals(LockProtocol.STATE,code);data.enforceInterface(LockProtocol.HOST);assertEquals("owner",data.readString());int call;synchronized(this){call=++calls;}if(call==1){entered.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException problem){throw new AssertionError(problem);}}reply.writeNoException();reply.writeInt(call==1?0:1);reply.writeString(call==1?"":"latest-ticket");reply.writeString(call==1?"BUSY_EDIT":"READY");return true;}};
        set("hostBinder",host);Thread older=new Thread(()->{try{Method periodic=LockRelayService.class.getDeclaredMethod("refreshHost");periodic.setAccessible(true);periodic.invoke(service);}catch(Throwable problem){failure.set(problem);}});older.start();assertTrue(entered.await(5,TimeUnit.SECONDS));Thread newer=new Thread(()->{try{refresh();}catch(Throwable problem){failure.set(problem);}});newer.start();release.countDown();older.join(5000);newer.join(5000);assertFalse(older.isAlive());assertFalse(newer.isAlive());assertNull(failure.get());
        Object observation=get(service,"hostObservation");assertTrue((Boolean)get(observation,"ready"));assertEquals("latest-ticket",get(observation,"ticket"));assertEquals("READY",get(observation,"reason"));
    }
    @Test public void newerRequestInFlightCannotGrantPreviousTuple()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
        Binder host=new Binder(){@Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){entered.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException problem){throw new AssertionError(problem);}reply.writeNoException();reply.writeInt(1);reply.writeString("ticket");reply.writeString("READY");return true;}};
        set("hostBinder",host);Thread pending=new Thread(()->{try{refresh();}catch(Throwable problem){failure.set(problem);}});pending.start();assertTrue(entered.await(5,TimeUnit.SECONDS));assertFalse((Boolean)get(get(service,"hostObservation"),"ready"));release.countDown();pending.join(5000);assertNull(failure.get());
    }
    @Test public void replacedHostCannotPublishOldOwnerObservation()throws Exception{
        Binder host=new Binder(){@Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){try{set("hostSession","new-owner");}catch(Exception problem){throw new AssertionError(problem);}reply.writeNoException();reply.writeInt(1);reply.writeString("old-ticket");reply.writeString("READY");return true;}};
        set("hostBinder",host);refresh();assertFalse((Boolean)get(get(service,"hostObservation"),"ready"));
    }
    @Test public void periodicWorkerCannotPreemptInteractiveHostObservation()throws Exception{
        AtomicReference<Throwable> failure=new AtomicReference<>();CountDownLatch interactiveEntered=new CountDownLatch(1),release=new CountDownLatch(1),periodicEntered=new CountDownLatch(1);Binder host=new Binder(){int calls;
            @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){int call;synchronized(this){call=++calls;}if(call==1){interactiveEntered.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException problem){throw new AssertionError(problem);}}else periodicEntered.countDown();reply.writeNoException();reply.writeInt(1);reply.writeString(call==1?"interactive-ticket":"periodic-ticket");reply.writeString("READY");return true;}};
        set("hostBinder",host);Thread interactive=new Thread(()->{try{refresh();}catch(Throwable problem){failure.set(problem);}});interactive.start();assertTrue(interactiveEntered.await(5,TimeUnit.SECONDS));Thread periodic=new Thread(()->{try{Method method=LockRelayService.class.getDeclaredMethod("refreshHost");method.setAccessible(true);method.invoke(service);}catch(Throwable problem){failure.set(problem);}});periodic.start();assertFalse(periodicEntered.await(100,TimeUnit.MILLISECONDS));release.countDown();interactive.join(5000);periodic.join(5000);assertNull(failure.get());assertFalse(interactive.isAlive());assertFalse(periodic.isAlive());Object current=get(service,"hostObservation");assertTrue((Boolean)get(current,"ready"));assertEquals("periodic-ticket",get(current,"ticket"));
    }
    @Test public void twoNativeRequestsSerializeThroughWholeReplyWithoutMixingPendingTuple()throws Exception{
        assertTrue(LockRelayService.setNativeInputEnabled(service,true));set("appliedForceRevision",LockRelayService.nativeInputState(service).revision);
        set("serverPid",4321);set("serverEpoch","server-epoch");set("serverSession",new Binder());
        CountDownLatch firstEntered=new CountDownLatch(1),releaseFirst=new CountDownLatch(1),secondEntered=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();java.util.List<String> answers=java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        Binder host=new Binder(){int calls;@Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){int call;synchronized(this){call=++calls;}if(call==1){firstEntered.countDown();try{assertTrue(releaseFirst.await(5,TimeUnit.SECONDS));}catch(InterruptedException problem){throw new AssertionError(problem);}}else secondEntered.countDown();reply.writeNoException();reply.writeInt(1);reply.writeString("ticket-"+call);reply.writeString("READY");return true;}};
        set("hostBinder",host);Runnable query=()->{org.robolectric.shadows.ShadowBinder.setCallingUid(1000);org.robolectric.shadows.ShadowBinder.setCallingPid(4321);Parcel input=Parcel.obtain(),output=Parcel.obtain();try{input.writeInterfaceToken(LockProtocol.RELAY);input.setDataPosition(0);assertTrue(service.onBind(new android.content.Intent()).transact(LockProtocol.RELAY_STATE,input,output,0));output.setDataPosition(0);output.readException();assertEquals(1,output.readInt());answers.add(output.readString());assertEquals("READY_NATIVE_INPUT",output.readString());assertEquals(0,output.dataAvail());}catch(Throwable problem){failure.set(problem);}finally{input.recycle();output.recycle();}};
        Thread first=new Thread(query),second=new Thread(query);first.start();assertTrue(firstEntered.await(5,TimeUnit.SECONDS));second.start();assertFalse(secondEntered.await(100,TimeUnit.MILLISECONDS));releaseFirst.countDown();first.join(5000);second.join(5000);assertFalse(first.isAlive());assertFalse(second.isAlive());assertNull(failure.get());assertEquals(java.util.List.of("ticket-1","ticket-2"),answers);
    }
    @Test public void eventWaitsForPeriodicQueryThenUsesFreshReadyRatherThanPendingTuple()throws Exception{
        assertTrue(LockRelayService.setNativeInputEnabled(service,true));set("appliedForceRevision",LockRelayService.nativeInputState(service).revision);set("serverPid",4321);set("serverEpoch","server-epoch");set("serverSession",new Binder());
        CountDownLatch periodicEntered=new CountDownLatch(1),release=new CountDownLatch(1),eventFinished=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();AtomicReference<Integer> result=new AtomicReference<>();
        Binder host=new Binder(){int calls;@Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){data.enforceInterface(LockProtocol.HOST);if(code==LockProtocol.STATE){assertEquals("owner",data.readString());int call;synchronized(this){call=++calls;}if(call==1){periodicEntered.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException problem){throw new AssertionError(problem);}}reply.writeNoException();reply.writeInt(1);reply.writeString("same-ticket");reply.writeString("READY");return true;}if(code==LockProtocol.COMMAND){reply.writeNoException();reply.writeString("ACCEPTED");return true;}return false;}};
        set("hostBinder",host);Thread periodic=new Thread(()->{try{Method method=LockRelayService.class.getDeclaredMethod("refreshHost");method.setAccessible(true);method.invoke(service);}catch(Throwable problem){failure.set(problem);}});periodic.start();assertTrue(periodicEntered.await(5,TimeUnit.SECONDS));
        Thread event=new Thread(()->{org.robolectric.shadows.ShadowBinder.setCallingUid(1000);org.robolectric.shadows.ShadowBinder.setCallingPid(4321);Parcel data=Parcel.obtain(),reply=Parcel.obtain();try{data.writeInterfaceToken(LockProtocol.RELAY);data.writeInt(LockProtocol.NEXT);data.writeLong(1);data.writeLong(android.os.SystemClock.elapsedRealtime());data.writeLong(1);data.writeString("actor");data.writeString("same-ticket");data.setDataPosition(0);assertTrue(service.onBind(new android.content.Intent()).transact(LockProtocol.RELAY_EVENT,data,reply,0));reply.setDataPosition(0);reply.readException();result.set(reply.readInt());assertEquals(0,reply.dataAvail());}catch(Throwable problem){failure.set(problem);}finally{data.recycle();reply.recycle();eventFinished.countDown();}});event.start();assertFalse(eventFinished.await(100,TimeUnit.MILLISECONDS));release.countDown();assertTrue(eventFinished.await(5,TimeUnit.SECONDS));periodic.join(5000);assertNull(failure.get());assertEquals(Integer.valueOf(RelayAdmission.ACCEPTED),result.get());
    }
}
