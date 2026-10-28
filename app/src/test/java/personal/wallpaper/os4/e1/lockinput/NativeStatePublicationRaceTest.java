package personal.wallpaper.os4.e1.lockinput;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Looper;
import android.view.MotionEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

/** 真Observer/Android主队列及受控Binder响应先后；不执行设备或伪造完整APK身份。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE,qualifiers="w1200dp-h2400dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public final class NativeStatePublicationRaceTest {
    private NativeObserverIntegrationTest fixture;
    private Object observer,session,channel;
    private Object commandApproval;
    @Before public void before()throws Exception{fixture=new NativeObserverIntegrationTest();fixture.before();observer=get(fixture,"observer");session=get(fixture,"session");channel=get(fixture,"channel");}
    @After public void after()throws Throwable{fixture.after();}
    private static Object get(Object object,String name)throws Exception{Field field=LockInputBroker.field(object.getClass(),name);return field.get(object);}
    private static void set(Object object,String name,Object value)throws Exception{LockInputBroker.field(object.getClass(),name).set(object,value);}
    private Object invoke(String name,Class<?>[] arguments,Object...values)throws Exception{Method method=LockSystemUiObserver.class.getDeclaredMethod(name,arguments);method.setAccessible(true);return method.invoke(observer,values);}
    @Test public void oldQueuedTicketChangeCannotEraseNewReadySequence()throws Exception{
        set(session,"ticket","new-lock-ticket");invoke("pollOnce",new Class<?>[0]);
        Method single=NativeObserverIntegrationTest.class.getDeclaredMethod("single",int.class,long.class,float.class,float.class);single.setAccessible(true);
        long now=((Number)Class.forName("android.os.SystemClock").getMethod("uptimeMillis").invoke(null)).longValue();
        single.invoke(fixture,MotionEvent.ACTION_DOWN,now,700f,1500f);
        assertEquals("new-lock-ticket",get(observer,"gestureTicket"));
        Shadow.<ShadowLooper>extract(Looper.getMainLooper()).idle();
        assertEquals("旧轮询清理不得撤销后来实时准入的手势","new-lock-ticket",get(observer,"gestureTicket"));
    }
    @Test public void slowOlderWorkerResponseCannotOverwriteNewerMainState()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
        Binder reordered=new Binder(){int calls;
            @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){data.enforceInterface(LockInputBroker.SESSION);if(code==5){data.readInt();reply.writeNoException();reply.writeInt(0);return true;}assertEquals(2,code);int call; synchronized(this){call=++calls;}if(call==1){entered.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException problem){throw new AssertionError(problem);}}reply.writeNoException();reply.writeInt(call==1?0:1);reply.writeString(call==1?"":"new-lock-ticket");reply.writeString(call==1?"BUSY_EDIT":"READY_NATIVE_INPUT");return true;}};
        Constructor<?> constructor=channel.getClass().getDeclaredConstructors()[0];constructor.setAccessible(true);
        Object reorderedChannel=constructor.newInstance(reordered,100,"race-server",1L,"race-actor");set(observer,"channel",reorderedChannel);
        Thread worker=new Thread(()->{try{invoke("pollOnce",new Class<?>[0]);}catch(Throwable problem){failure.set(problem);}});
        worker.start();assertTrue(entered.await(5,TimeUnit.SECONDS));
        Object newest=invoke("nativeState",new Class<?>[]{channel.getClass()},reorderedChannel);assertNotNull(newest);assertEquals("new-lock-ticket",get(newest,"ticket"));
        release.countDown();worker.join(5000);assertFalse(worker.isAlive());assertNull(failure.get());
        Object current=get(observer,"state");assertNotNull(current);assertTrue((Boolean)get(current,"ready"));assertEquals("new-lock-ticket",get(current,"ticket"));
    }
    @Test public void foregroundStatusCannotBePreemptedByLaterBackgroundPoll()throws Exception{
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Binder overlapping=new Binder(){int calls;
            @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){data.enforceInterface(LockInputBroker.SESSION);if(code==5){data.readInt();reply.writeNoException();reply.writeInt(0);return true;}int call;synchronized(this){call=++calls;}if(call==1){Thread poll=new Thread(()->{try{invoke("pollOnce",new Class<?>[0]);}catch(Throwable problem){failure.set(problem);}});poll.start();try{poll.join(5000);assertFalse(poll.isAlive());}catch(InterruptedException problem){throw new AssertionError(problem);}}reply.writeNoException();reply.writeInt(call==1?1:0);reply.writeString(call==1?"foreground-ticket":"");reply.writeString(call==1?"READY_NATIVE_INPUT":"BUSY_EDIT");return true;}};
        Constructor<?> constructor=channel.getClass().getDeclaredConstructors()[0];constructor.setAccessible(true);Object next=constructor.newInstance(overlapping,100,"new-server",1L,"new-actor");set(observer,"channel",next);
        Object current=invoke("nativeState",new Class<?>[]{channel.getClass()},next);assertNull(failure.get());assertNotNull("后台轮询不得抢占前台鲜状态请求",current);assertEquals("foreground-ticket",get(current,"ticket"));
    }
    @Test public void olderPollExceptionCannotInvalidateLaterFreshChannelState()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
        Binder reordered=new Binder(){int calls;@Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){data.enforceInterface(LockInputBroker.SESSION);if(code==5){data.readInt();reply.writeNoException();reply.writeInt(0);return true;}int call;synchronized(this){call=++calls;}if(call==1){entered.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException problem){throw new AssertionError(problem);}throw new IllegalStateException("OLD_RESPONSE_FAILED");}reply.writeNoException();reply.writeInt(1);reply.writeString("fresh-ticket");reply.writeString("READY_NATIVE_INPUT");return true;}};
        Constructor<?> constructor=channel.getClass().getDeclaredConstructors()[0];constructor.setAccessible(true);Object next=constructor.newInstance(reordered,100,"new-server",1L,"new-actor");set(observer,"channel",next);
        Thread worker=new Thread(()->{try{invoke("pollOnce",new Class<?>[0]);}catch(Throwable problem){failure.set(problem);}});worker.start();assertTrue(entered.await(5,TimeUnit.SECONDS));assertNotNull(invoke("nativeState",new Class<?>[]{channel.getClass()},next));release.countDown();worker.join(5000);assertFalse(worker.isAlive());assertNull(failure.get());assertSame(next,get(observer,"channel"));assertEquals("fresh-ticket",get(get(observer,"state"),"ticket"));
    }
    @Test public void nonMainRevocationCleanupCannotClearLaterMainSequence()throws Exception{
        Thread revoke=new Thread(()->{try{invoke("revokeNative",new Class<?>[0]);}catch(Exception problem){throw new AssertionError(problem);}});revoke.start();revoke.join(5000);assertFalse(revoke.isAlive());
        Method single=NativeObserverIntegrationTest.class.getDeclaredMethod("single",int.class,long.class,float.class,float.class);single.setAccessible(true);long now=((Number)Class.forName("android.os.SystemClock").getMethod("uptimeMillis").invoke(null)).longValue();
        single.invoke(fixture,MotionEvent.ACTION_DOWN,now,700f,1500f);assertNotNull(get(observer,"gestureTicket"));Shadow.<ShadowLooper>extract(Looper.getMainLooper()).idle();assertNotNull("旧后台清理不能清除新主线程手势",get(observer,"gestureTicket"));
    }
    @Test public void olderUnauthorizedReplyCannotInvalidateNewerReadyObservation()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
        Binder reordered=new Binder(){int calls;@Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){data.enforceInterface(LockInputBroker.SESSION);if(code==5){data.readInt();reply.writeNoException();reply.writeInt(0);return true;}int call;synchronized(this){call=++calls;}if(call==1){entered.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException problem){throw new AssertionError(problem);}}reply.writeNoException();reply.writeInt(call==1?0:1);reply.writeString(call==1?"":"fresh-ticket");reply.writeString(call==1?"UNAUTHORIZED_OR_NOT_READY":"READY_NATIVE_INPUT");return true;}};
        Constructor<?> constructor=channel.getClass().getDeclaredConstructors()[0];constructor.setAccessible(true);Object next=constructor.newInstance(reordered,100,"new-server",1L,"new-actor");set(observer,"channel",next);
        Thread worker=new Thread(()->{try{invoke("pollOnce",new Class<?>[0]);}catch(Throwable problem){failure.set(problem);}});worker.start();assertTrue(entered.await(5,TimeUnit.SECONDS));assertNotNull(invoke("nativeState",new Class<?>[]{channel.getClass()},next));release.countDown();worker.join(5000);assertNull(failure.get());assertSame(next,get(observer,"channel"));assertEquals("fresh-ticket",get(get(observer,"state"),"ticket"));
    }
    private NativeGestureOwnership.Binding commandBinding()throws Exception{
        Object injector=get(fixture,"injector");NativeBusinessSnapshot snapshot=NativeBusinessSnapshot.read(injector,1,false,false,false);assertTrue(snapshot.reason,snapshot.allowed);
        Object current=get(observer,"state");Method bind=LockSystemUiObserver.class.getDeclaredMethod("nativeBinding",NativeBusinessSnapshot.class,channel.getClass(),current.getClass());bind.setAccessible(true);
        NativeGestureOwnership.Binding binding=(NativeGestureOwnership.Binding)bind.invoke(observer,snapshot,channel,current);((NativeGestureOwnership)get(observer,"nativeOwner")).bind(binding);
        Class<?> approvalType=Class.forName(LockSystemUiObserver.class.getName()+"$NativeInvocation");Constructor<?> approvalConstructor=approvalType.getDeclaredConstructors()[0];approvalConstructor.setAccessible(true);
        commandApproval=approvalConstructor.newInstance(null,binding,snapshot,1,false,false,false,new int[]{4},new float[]{700},new float[]{1500},null);return binding;
    }
    private void command(NativeGestureOwnership.Binding binding,long issued)throws Exception{
        long generation=((NativeGestureOwnership)get(observer,"nativeOwner")).currentGeneration();Class<?> approvalType=Class.forName(LockSystemUiObserver.class.getName()+"$NativeInvocation");Method send=LockSystemUiObserver.class.getDeclaredMethod("sendNativeCommandAttempt",int.class,long.class,long.class,long.class,String.class,NativeGestureOwnership.Binding.class,channel.getClass(),approvalType,int.class);send.setAccessible(true);
        send.invoke(observer,1,1L,issued,generation,binding.ticket,binding,channel,commandApproval,0);
    }
    @Test public void identifiedCommandDefersWhileForegroundInFlightThenSendsExactlyOnce()throws Exception{
        NativeGestureOwnership.Binding binding=commandBinding();MonotonicObservation<?> observations=(MonotonicObservation<?>)get(observer,"stateObservations");long foreground=observations.beginRequest(true);
        long issued=android.os.SystemClock.elapsedRealtime();AtomicReference<Throwable> failure=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);
        Thread worker=new Thread(()->{try{command(binding,issued);}catch(Throwable problem){failure.set(problem);}finally{done.countDown();}});worker.start();assertTrue(done.await(5,TimeUnit.SECONDS));assertNull(failure.get());assertTrue(((java.util.List<?>)get(session,"commands")).isEmpty());
        observations.finishRequest(foreground);Shadow.<ShadowLooper>extract(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(25));assertEquals(java.util.List.of(1),get(session,"commands"));
    }
    @Test public void deferredCommandDoesNotSurviveCloseOrTicketChangeOrExpiry()throws Exception{
        for(int change=0;change<3;change++){
            NativeGestureOwnership.Binding binding=commandBinding();MonotonicObservation<?> observations=(MonotonicObservation<?>)get(observer,"stateObservations");long foreground=observations.beginRequest(true);long issued=android.os.SystemClock.elapsedRealtime();
            Thread worker=new Thread(()->{try{command(binding,issued);}catch(Exception problem){throw new AssertionError(problem);}});worker.start();worker.join(5000);assertFalse(worker.isAlive());
            observations.finishRequest(foreground);
            if(change==0)((NativeGestureOwnership)get(observer,"nativeOwner")).revoke();
            else if(change==1)set(session,"ticket","changed-ticket");
            else org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(1100));
            Shadow.<ShadowLooper>extract(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(25));assertTrue(((java.util.List<?>)get(session,"commands")).isEmpty());
            set(session,"ticket",binding.ticket);
        }
    }
    @Test public void foregroundBusyRetryIsStrictlyBounded()throws Exception{
        NativeGestureOwnership.Binding binding=commandBinding();MonotonicObservation<?> observations=(MonotonicObservation<?>)get(observer,"stateObservations");long foreground=observations.beginRequest(true);long issued=android.os.SystemClock.elapsedRealtime();
        android.os.HandlerThread thread=new android.os.HandlerThread("bounded-command-test");thread.start();android.os.Handler worker=new android.os.Handler(thread.getLooper());set(observer,"worker",worker);AtomicReference<Throwable> failure=new AtomicReference<>();
        try{
            worker.post(()->{try{command(binding,issued);}catch(Throwable problem){failure.set(problem);}});
            ShadowLooper background=Shadow.extract(thread.getLooper()),main=Shadow.extract(Looper.getMainLooper());background.idle();
            for(int tick=0;tick<4;tick++){main.idleFor(java.time.Duration.ofMillis(20));background.idle();}assertNull(failure.get());
            NativeDiagnosticCounters counts=(NativeDiagnosticCounters)get(observer,"nativeDiagnostics");Field totalsField=NativeDiagnosticCounters.class.getDeclaredField("totals");totalsField.setAccessible(true);long[] totals=(long[])totalsField.get(counts);
            assertEquals(3,totals[NativeInputDetail.code("NATIVE_COMMAND_DEFERRED")-NativeInputDetail.BASE]);
            assertEquals(1,totals[NativeInputDetail.code("NATIVE_COMMAND_RETRY_EXHAUSTED")-NativeInputDetail.BASE]);
            assertTrue(((java.util.List<?>)get(session,"commands")).isEmpty());
        }finally{observations.finishRequest(foreground);worker.removeCallbacksAndMessages(null);thread.quitSafely();}
    }
    @Test public void busyRetryRechecksBouncerBeforeSendingSameTicket()throws Exception{
        NativeGestureOwnership.Binding binding=commandBinding();MonotonicObservation<?> observations=(MonotonicObservation<?>)get(observer,"stateObservations");long foreground=observations.beginRequest(true);long issued=android.os.SystemClock.elapsedRealtime();
        Thread worker=new Thread(()->{try{command(binding,issued);}catch(Exception problem){throw new AssertionError(problem);}});worker.start();worker.join(5000);observations.finishRequest(foreground);
        Object business=get(fixture,"fixture");set(get(business,"monitor"),"mPrimaryBouncerIsOrWillBeShowing",true);
        Shadow.<ShadowLooper>extract(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(25));assertTrue(((java.util.List<?>)get(session,"commands")).isEmpty());
    }
    @Test public void busyRetryRechecksNewNotificationLayoutBeforeSendingSameTicket()throws Exception{
        NativeGestureOwnership.Binding binding=commandBinding();MonotonicObservation<?> observations=(MonotonicObservation<?>)get(observer,"stateObservations");long foreground=observations.beginRequest(true);long issued=android.os.SystemClock.elapsedRealtime();
        Thread worker=new Thread(()->{try{command(binding,issued);}catch(Exception problem){throw new AssertionError(problem);}});worker.start();worker.join(5000);observations.finishRequest(foreground);
        Object business=get(fixture,"fixture");android.view.ViewGroup stack=(android.view.ViewGroup)get(business,"stack");stack.requestLayout();set(stack,"mChildrenUpdateRequested",true);
        Shadow.<ShadowLooper>extract(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(25));assertTrue(((java.util.List<?>)get(session,"commands")).isEmpty());
    }
}
