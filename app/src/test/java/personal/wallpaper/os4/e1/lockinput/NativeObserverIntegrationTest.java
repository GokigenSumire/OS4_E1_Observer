package personal.wallpaper.os4.e1.lockinput;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.SystemClock;
import android.system.Os;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import com.android.keyguard.injector.KeyguardPanelViewInjector;
import com.android.keyguard.injector.KeyguardPanelViewInjector$bindView$2;
import de.robv.android.xposed.XC_MethodHook;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import personal.wallpaper.protocol.lock.LockProtocol;
import static org.junit.Assert.*;

/** 真实Android窗口＋局部OEM字段结构＋本地鉴别会话替身；不证明设备ART或跨进程认证结果。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE,qualifiers="w1200dp-h2400dp-mdpi")
public final class NativeObserverIntegrationTest {
    private NativeBusinessSnapshotTest fixture;
    private KeyguardPanelViewInjector injector;
    private View root;
    private Context context;
    private LockSystemUiObserver observer;
    private NativeSystemUiHooks hooks;
    private Object channel;
    private Session session;
    private File identityFile;
    private final List<XC_MethodHook.MethodHookParam> open=new ArrayList<>();
    private static final String TOKEN="native-integration-78",TICKET="LOCK2:00000000-0000-0000-0000-000000000094:00000000-0000-0000-0000-000000000001";
    private static final class Session extends Binder {
        boolean ready=true,throwState;String ticket=TICKET,reason="READY_NATIVE_INPUT";
        final List<Integer> commands=new ArrayList<>();final List<String> tickets=new ArrayList<>();int statusCalls;
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){
            data.enforceInterface(LockInputBroker.SESSION);
            if(code==2){statusCalls++;if(throwState)throw new IllegalStateException("channel lost");reply.writeNoException();reply.writeInt(ready?1:0);reply.writeString(ticket);reply.writeString(reason);return true;}
            if(code==3){int action=data.readInt();data.readLong();data.readLong();data.readLong();data.readString();String supplied=data.readString();assertEquals(0,data.dataAvail());commands.add(action);tickets.add(supplied);reply.writeNoException();reply.writeInt(0);return true;}
            if(code==5){data.readInt();reply.writeNoException();reply.writeInt(0);return true;}
            return false;
        }
    }
    @Before public void before()throws Exception{
        NativeRootBindingTest.Fixture rootFixture=new NativeRootBindingTest.Fixture();rootFixture.before();fixture=rootFixture.guard;injector=(KeyguardPanelViewInjector)get(fixture,"injector");root=(View)get(fixture,"root");context=root.getContext();
        Constructor<LockSystemUiObserver> constructor=LockSystemUiObserver.class.getDeclaredConstructor(Class.class,ApplicationInfo.class);constructor.setAccessible(true);
        observer=constructor.newInstance(null,new ApplicationInfo());Handler old=(Handler)get(observer,"worker");old.getLooper().quitSafely();set(observer,"worker",new Handler(Looper.getMainLooper()));
        // 只建stillSameFile所需的真实临时文件stat，不伪造生产完整APK/source-loader验证通过。
        identityFile=File.createTempFile("native-identity-78-",".tmp",context.getCacheDir());
        set(observer,"identity",new RuntimeApkIdentity.Result(true,true,"TEST_LOCAL_STAT_ONLY",identityFile.getCanonicalPath(),Os.stat(identityFile.getCanonicalPath())));
        session=new Session();Class<?> channelType=Class.forName(LockSystemUiObserver.class.getName()+"$Channel");
        Constructor<?> channelConstructor=channelType.getDeclaredConstructor(android.os.IBinder.class,int.class,String.class,long.class,String.class);channelConstructor.setAccessible(true);
        channel=channelConstructor.newInstance(session,100,"server-test-78",1L,"actor-test-78");set(observer,"channel",channel);
        Class<?> hostType=Class.forName(LockSystemUiObserver.class.getName()+"$HostState");Constructor<?> host=hostType.getDeclaredConstructor(channelType,boolean.class,String.class,long.class,boolean.class,boolean.class,boolean.class);host.setAccessible(true);
        set(observer,"state",host.newInstance(channel,true,TICKET,SystemClock.elapsedRealtime(),false,false,true));set(observer,"nativeSelected",true);
        ((NativeDiagnosticCounters)get(observer,"nativeDiagnostics")).session(TOKEN,true);
        hooks=NativeSystemUiHooks.installVerified(injector.getClass().getClassLoader(),type->true,new NativeSystemUiHooks.Callback(){
            @Override public void observed(String source){invokeUnchecked("nativeObserved",new Class<?>[]{String.class},source);}
            @Override public Object begin(Object target,MotionEvent event,int state,boolean expanding,boolean qs,boolean dozing){return invokeUnchecked("beginNative",new Class<?>[]{Object.class,MotionEvent.class,int.class,boolean.class,boolean.class,boolean.class},target,event,state,expanding,qs,dozing);}
            @Override public void end(Object frame){invokeUnchecked("endNative",new Class<?>[]{Object.class},frame);}
            @Override public boolean suppress(Object listener){return (Boolean)invokeUnchecked("suppressNativeSleep",new Class<?>[]{Object.class},listener);}
            @Override public void lifecycle(Object controller){invokeUnchecked("revokeNative",new Class<?>[0]);}
            @Override public void bindingRejected(String reason){invokeUnchecked("nativeRejected",new Class<?>[]{String.class},reason);}
            @Override public boolean internalCancelAllowed(Object frame,Object window,View shade){return (Boolean)invokeUnchecked("nativeInternalCancelAllowed",new Class<?>[]{Object.class,Object.class,View.class},frame,window,shade);}
            @Override public boolean nativeRouteSelected(){try{return (Boolean)get(observer,"nativeSelected");}catch(Exception error){throw new IllegalStateException(error);}}
        });set(observer,"nativeHooks",hooks);
        NativeBusinessSnapshot sanity=NativeBusinessSnapshot.read(injector,1,false,false,false);assertTrue(sanity.reason,sanity.allowed);
    }
    @After public void after()throws Throwable{
        while(!open.isEmpty())close(open.get(open.size()-1));
        if(observer!=null){((Handler)get(observer,"main")).removeCallbacksAndMessages(null);((Handler)get(observer,"worker")).removeCallbacksAndMessages(null);}
        if(hooks!=null){@SuppressWarnings("unchecked")List<XC_MethodHook.Unhook> handles=(List<XC_MethodHook.Unhook>)get(hooks,"handles");for(XC_MethodHook.Unhook handle:handles)handle.unhook();}
        if(identityFile!=null)assertTrue(identityFile.delete());
    }
    private static Object get(Object object,String name)throws Exception{Field field=LockInputBroker.field(object.getClass(),name);return field.get(object);}
    private static void set(Object object,String name,Object value)throws Exception{LockInputBroker.field(object.getClass(),name).set(object,value);}
    private Object invoke(String name,Class<?>[] types,Object... args)throws Exception{Method method=LockSystemUiObserver.class.getDeclaredMethod(name,types);method.setAccessible(true);try{return method.invoke(observer,args);}catch(java.lang.reflect.InvocationTargetException error){throw new IllegalStateException(error.getCause());}}
    private Object invokeUnchecked(String name,Class<?>[] types,Object... args){try{return invoke(name,types,args);}catch(Exception error){throw new IllegalStateException(error);}}
    private long diagnostic(String fixed)throws Exception{long[] totals=(long[])get(get(observer,"nativeDiagnostics"),"totals");return totals[NativeInputDetail.code(fixed)-NativeInputDetail.BASE];}
    private String diagnosticSummary()throws Exception{long[] totals=(long[])get(get(observer,"nativeDiagnostics"),"totals");StringBuilder text=new StringBuilder();for(int i=0;i<totals.length;i++)if(totals[i]>0)text.append(NativeInputDetail.label(NativeInputDetail.BASE+i)).append('=').append(totals[i]).append(' ');return text.toString();}
    private MotionEvent event(int action,long time,int[] ids,float[] xs,float[] ys,int index){
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[ids.length];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[ids.length];
        for(int i=0;i<ids.length;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=ids[i];properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=xs[i];coords[i].y=ys[i];coords[i].pressure=1;coords[i].size=1;}
        int encoded=action|((action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_POINTER_UP)?index<<MotionEvent.ACTION_POINTER_INDEX_SHIFT:0);
        return MotionEvent.obtain(Math.max(0,time-20),time,encoded,ids.length,properties,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
    }
    private XC_MethodHook.MethodHookParam begin(MotionEvent event)throws Throwable{
        Method method=NativeSystemUiHooksTest.rootMethod();XC_MethodHook.MethodHookParam param=NativeSystemUiHooksTest.parameter(method,get(fixture,"shade"),event);param.setResult(false);
        NativeSystemUiHooksTest.callbackFor(hooks,method).callBefore(param);open.add(param);return param;
    }
    private void close(XC_MethodHook.MethodHookParam param)throws Throwable{NativeSystemUiHooksTest.callbackFor(hooks,NativeSystemUiHooksTest.rootMethod()).callAfter(param);open.remove(param);if(param.args[0] instanceof MotionEvent)((MotionEvent)param.args[0]).recycle();}
    private void feed(int action,long time,int[] ids,float[] xs,float[] ys,int index)throws Throwable{close(begin(event(action,time,ids,xs,ys,index)));}
    private void single(int action,long time,float x,float y)throws Throwable{feed(action,time,new int[]{4},new float[]{x},new float[]{y},0);}
    private void tap(long time)throws Throwable{single(MotionEvent.ACTION_DOWN,time,700,1500);single(MotionEvent.ACTION_UP,time+30,700,1500);}
    private void idle(long milliseconds){ShadowLooper.idleMainLooper(milliseconds,java.util.concurrent.TimeUnit.MILLISECONDS);}
    private static long clock(){try{return ((Number)Class.forName("android.os.SystemClock").getMethod("uptimeMillis").invoke(null)).longValue();}catch(Exception error){throw new IllegalStateException(error);}}
    private boolean suppress()throws Throwable{
        Method method=KeyguardPanelViewInjector$bindView$2.class.getDeclaredMethod("onDoubleTap");Object listener=get(fixture,"listener");
        XC_MethodHook.MethodHookParam param=NativeSystemUiHooksTest.parameter(method,listener);param.setResult(false);NativeSystemUiHooksTest.callbackFor(hooks,method).callBefore(param);return Boolean.TRUE.equals(param.getResult());
    }
    private void twoTap(long time,boolean swap)throws Throwable{
        feed(MotionEvent.ACTION_DOWN,time,new int[]{8},new float[]{700},new float[]{1500},0);
        feed(MotionEvent.ACTION_POINTER_DOWN,time+5,new int[]{8,12},new float[]{700,800},new float[]{1500,1500},1);
        if(swap){feed(MotionEvent.ACTION_POINTER_UP,time+10,new int[]{12,8},new float[]{800,700},new float[]{1500,1500},0);feed(MotionEvent.ACTION_UP,time+15,new int[]{8},new float[]{700},new float[]{1500},0);}
        else {feed(MotionEvent.ACTION_POINTER_UP,time+10,new int[]{8,12},new float[]{700,800},new float[]{1500,1500},0);feed(MotionEvent.ACTION_UP,time+15,new int[]{12},new float[]{800},new float[]{1500},0);}
    }
    @Test public void doubleWaitsForThirdWindowThenSendsOneNext()throws Throwable{long now=clock();tap(now);tap(now+80);assertTrue(session.commands.isEmpty());idle(500);assertEquals(diagnosticSummary(),List.of(LockProtocol.NEXT),session.commands);assertEquals(List.of(TICKET),session.tickets);}
    private void enablePriority()throws Exception{session.reason="READY_NATIVE_INPUT+READY_WALLPAPER_PRIORITY";invoke("nativeState",new Class<?>[]{channel.getClass()},channel);idle(0);}
    @Test public void delayedDoubleEmissionDoesNotEatNextDoubleFirstDown()throws Throwable{enablePriority();long now=clock();tap(now);tap(now+80);tap(now+450);tap(now+530);idle(1000);assertEquals(diagnosticSummary(),List.of(LockProtocol.NEXT,LockProtocol.NEXT),session.commands);}
    @Test public void delayedDoubleEmissionDoesNotEatFollowingTripleFirstDown()throws Throwable{enablePriority();long now=clock();tap(now);tap(now+80);tap(now+450);tap(now+530);tap(now+610);idle(1100);assertEquals(diagnosticSummary(),List.of(LockProtocol.NEXT,LockProtocol.PREVIOUS),session.commands);}
    @Test public void delayedDoubleEmissionDoesNotEatFollowingTwoFingerFirstDown()throws Throwable{enablePriority();long now=clock();tap(now);tap(now+80);twoTap(now+450,false);twoTap(now+550,true);idle(1100);assertEquals(diagnosticSummary(),List.of(LockProtocol.NEXT,LockProtocol.EDIT),session.commands);}
    @Test public void defaultNativeDelayedDoubleAlsoKeepsNextFirstDown()throws Throwable{long now=clock();tap(now);tap(now+80);tap(now+450);tap(now+530);idle(1000);assertEquals(diagnosticSummary(),List.of(LockProtocol.NEXT,LockProtocol.NEXT),session.commands);assertTrue(diagnostic("NATIVE_EXPIRED_DOUBLE_NEXT_DOWN_PRESERVED")>0);}
    @Test public void expiredDoubleCannotContinueAcrossNewScene()throws Throwable{enablePriority();long now=clock();tap(now);tap(now+80);session.ticket="LOCK2:00000000-0000-0000-0000-000000000097:00000000-0000-0000-0000-000000000002";invoke("pollOnce",new Class<?>[0]);tap(now+450);tap(now+530);idle(1000);assertEquals(diagnosticSummary(),List.of(LockProtocol.NEXT),session.commands);assertEquals(List.of(session.ticket),session.tickets);}
    @Test public void priorityBusyStateDoesNotErasePendingEditCapability()throws Throwable{enablePriority();long now=clock();twoTap(now,false);twoTap(now+100,true);idle(200);assertNotNull(get(observer,"pendingEditRoot"));long sequence=(Long)get(observer,"pendingEditSequence");session.ready=false;session.reason="INPUT_ONLY_NATIVE_PRIORITY_BUSY_EDIT";invoke("pollOnce",new Class<?>[0]);idle(0);assertNotNull("同场景忙碌不能吞掉认证入口",get(observer,"pendingEditRoot"));assertEquals(sequence,get(observer,"pendingEditSequence"));}
    @Test public void priorityRecognizesWhileHostBusyThenDispatchesWhenReady()throws Throwable{enablePriority();session.ready=false;session.reason="INPUT_ONLY_NATIVE_PRIORITY_BUSY_EDIT";long now=clock();tap(now);tap(now+80);idle(500);assertEquals(diagnosticSummary(),1,diagnostic("NATIVE_RECOGNIZED_NEXT"));assertTrue(session.commands.isEmpty());session.ready=true;session.reason="READY_NATIVE_INPUT+READY_WALLPAPER_PRIORITY";idle(250);assertEquals(diagnosticSummary(),List.of(LockProtocol.NEXT),session.commands);}
    @Test public void priorityImageTicketChangeBetweenTapsKeepsGestureAndUsesFreshDispatchTicket()throws Throwable{enablePriority();long now=clock();tap(now);session.ticket="LOCK2:00000000-0000-0000-0000-000000000094:00000000-0000-0000-0000-000000000002";invoke("pollOnce",new Class<?>[0]);idle(0);tap(now+80);idle(600);assertEquals(diagnosticSummary(),List.of(LockProtocol.NEXT),session.commands);assertEquals(List.of(session.ticket),session.tickets);}
    @Test public void cachedPriorityTouchesDoNotPerformPerEventStateIpc()throws Throwable{enablePriority();int calls=session.statusCalls;tap(clock());assertEquals(calls,session.statusCalls);assertTrue(diagnostic("NATIVE_PRIORITY_CACHE_HIT")>=2);}
    @Test public void busyPriorityBurstKeepsNextPreviousEditInOrder()throws Throwable{enablePriority();session.ready=false;session.reason="INPUT_ONLY_NATIVE_PRIORITY_BUSY_EDIT";long now=clock();tap(now);tap(now+80);idle(500);now=clock();tap(now);tap(now+80);tap(now+160);idle(500);now=clock();twoTap(now,false);twoTap(now+100,true);idle(500);assertTrue(session.commands.isEmpty());session.ready=true;session.reason="READY_NATIVE_INPUT+READY_WALLPAPER_PRIORITY";idle(1000);assertEquals(diagnosticSummary(),List.of(LockProtocol.NEXT,LockProtocol.PREVIOUS,LockProtocol.EDIT),session.commands);}
    @Test public void newSceneCannotReceiveQueuedPriorityAction()throws Throwable{enablePriority();session.ready=false;session.reason="INPUT_ONLY_NATIVE_PRIORITY_BUSY_EDIT";long now=clock();tap(now);tap(now+80);idle(500);session.ticket="LOCK2:00000000-0000-0000-0000-000000000095:00000000-0000-0000-0000-000000000001";invoke("pollOnce",new Class<?>[0]);session.ready=true;session.reason="READY_NATIVE_INPUT+READY_WALLPAPER_PRIORITY";idle(500);assertTrue(session.commands.isEmpty());assertTrue(diagnostic("NATIVE_PRIORITY_QUEUE_SCENE_REVOKED")>0);}
    @Test public void pendingEditSurvivesOrdinaryTouchCancellation()throws Throwable{enablePriority();long now=clock();twoTap(now,false);twoTap(now+100,true);idle(200);long seq=(Long)get(observer,"pendingEditSequence");assertNotNull(get(observer,"pendingEditRoot"));single(MotionEvent.ACTION_CANCEL,clock(),700,1500);assertNotNull(get(observer,"pendingEditRoot"));assertEquals(seq,get(observer,"pendingEditSequence"));}
    @Test public void priorityDoubleAtTopEdgeSendsExactlyOneNextWithoutLegacyLayout()throws Throwable{enablePriority();long now=clock();single(0,now,5,20);single(1,now+25,5,20);single(0,now+80,5,20);single(1,now+105,5,20);idle(500);assertEquals(diagnosticSummary(),List.of(LockProtocol.NEXT),session.commands);assertEquals(0,diagnostic("NATIVE_PRIORITY_LAYOUT_UNKNOWN"));}
    @Test public void priorityTripleSurvivesUnrelatedThemePendingLayout()throws Throwable{enablePriority();root.setScaleX(.8f);root.requestLayout();long now=clock();tap(now);tap(now+80);tap(now+160);idle(600);assertEquals(diagnosticSummary(),List.of(LockProtocol.PREVIOUS),session.commands);assertEquals(0,diagnostic("NATIVE_RECOGNIZED_NEXT"));}
    @Test public void priorityTwoFingerDoubleStillOnlyRequestsAuthenticatedEdit()throws Throwable{enablePriority();long now=clock();twoTap(now,false);twoTap(now+100,true);idle(600);assertEquals(diagnosticSummary(),List.of(LockProtocol.EDIT),session.commands);assertEquals(List.of(TICKET),session.tickets);}
    @Test public void disablingPriorityBeforeDelayedDecisionCancelsOldSequence()throws Throwable{enablePriority();long now=clock();tap(now);tap(now+80);session.reason="READY_NATIVE_INPUT";idle(600);assertTrue(session.commands.isEmpty());}
    @Test public void inputFirstRecognizesEvenWhileAuthenticationUiIsShowing()throws Throwable{enablePriority();set(get(fixture,"monitor"),"mPrimaryBouncerIsOrWillBeShowing",true);long now=clock();tap(now);tap(now+80);idle(600);assertEquals(List.of(LockProtocol.NEXT),session.commands);assertEquals(0,diagnostic("NATIVE_AUTHENTICATION"));}
    @Test public void unsupportedCapabilityCannotBecomeLegacyByChangingRoute()throws Exception{set(observer,"nativeSelected",false);set(observer,"capabilityUnsupported",true);MotionEvent event=event(0,clock(),new int[]{4},new float[]{700},new float[]{1500},0);try{invoke("observe",new Class<?>[]{View.class,MotionEvent.class},root,event);assertNull(get(observer,"gestureTicket"));assertTrue(session.commands.isEmpty());assertEquals(1,diagnostic("NATIVE_CAPABILITY_SIGNATURE_REJECTED"));}finally{event.recycle();}}
    @Test public void tripleNeverSendsNextBeforePrevious()throws Throwable{long now=clock();tap(now);tap(now+80);assertTrue(session.commands.isEmpty());tap(now+160);assertTrue(session.commands.isEmpty());idle(600);assertEquals(List.of(LockProtocol.PREVIOUS),session.commands);assertEquals(0,diagnostic("NATIVE_RECOGNIZED_NEXT"));}
    @Test public void slightlySlowerTripleInsideIntervalIsStillOnlyPrevious()throws Throwable{long now=clock();tap(now);tap(now+180);tap(now+360);idle(800);assertEquals(List.of(LockProtocol.PREVIOUS),session.commands);}
    @Test public void twoFingerTapsWithReorderedPointerArraySendEdit()throws Throwable{long now=clock();twoTap(now,false);twoTap(now+80,true);idle(200);assertEquals(List.of(LockProtocol.EDIT),session.commands);assertNotNull(get(observer,"pendingEditRoot"));}
    @Test public void duplicateNaturalEventCannotBecomeTwoTaps()throws Throwable{long now=clock();single(0,now,700,1500);MotionEvent up=event(1,now+20,new int[]{4},new float[]{700},new float[]{1500},0);XC_MethodHook.MethodHookParam param=begin(up);close(param);feed(1,now+20,new int[]{4},new float[]{700},new float[]{1500},0);idle(500);assertTrue(session.commands.isEmpty());assertTrue(diagnostic("NATIVE_DUPLICATE_EVENT")>0);}
    @Test public void synchronousFrameMaySuppressButAfterFrameCannot()throws Throwable{long now=clock();tap(now);single(0,now+80,700,1500);XC_MethodHook.MethodHookParam up=begin(event(1,now+110,new int[]{4},new float[]{700},new float[]{1500},0));assertTrue(suppress());assertTrue(session.commands.isEmpty());close(up);assertFalse(suppress());idle(500);assertEquals(List.of(LockProtocol.NEXT),session.commands);}
    @Test public void firstAllowedTapCannotClaimOriginalHelpersEarlierTap()throws Throwable{long now=clock();single(0,now,700,1500);XC_MethodHook.MethodHookParam first=begin(event(1,now+20,new int[]{4},new float[]{700},new float[]{1500},0));assertFalse(suppress());close(first);idle(500);assertTrue(session.commands.isEmpty());}
    @Test public void protectedTapThenFirstBlankTapKeepsOriginalSleepPassthrough()throws Throwable{long now=clock();single(0,now,5,1500);single(1,now+20,5,1500);single(0,now+80,700,1500);XC_MethodHook.MethodHookParam first=begin(event(1,now+100,new int[]{4},new float[]{700},new float[]{1500},0));assertFalse(suppress());close(first);idle(500);assertTrue(session.commands.isEmpty());}
    @Test public void disabledEarlierTapThenFirstEnabledTapDoesNotTakeOverOriginalSleep()throws Throwable{long now=clock();session.ready=false;tap(now);session.ready=true;single(0,now+80,700,1500);XC_MethodHook.MethodHookParam first=begin(event(1,now+100,new int[]{4},new float[]{700},new float[]{1500},0));assertFalse(suppress());close(first);idle(500);assertTrue(session.commands.isEmpty());}
    @Test public void claimedDoubleHoldsNativeSleepUntilThirdOnlyPrevious()throws Throwable{long now=clock();tap(now);single(0,now+80,700,1500);XC_MethodHook.MethodHookParam second=begin(event(1,now+110,new int[]{4},new float[]{700},new float[]{1500},0));assertTrue(suppress());assertTrue(session.commands.isEmpty());close(second);tap(now+160);idle(600);assertEquals(List.of(LockProtocol.PREVIOUS),session.commands);assertEquals(0,diagnostic("NATIVE_RECOGNIZED_NEXT"));}
    @Test public void duplicatedClaimedSecondUpKeepsArbitrationButSendsOnlyOneNext()throws Throwable{long now=clock();tap(now);single(0,now+80,700,1500);XC_MethodHook.MethodHookParam second=begin(event(1,now+110,new int[]{4},new float[]{700},new float[]{1500},0));assertTrue(suppress());close(second);XC_MethodHook.MethodHookParam duplicate=begin(event(1,now+110,new int[]{4},new float[]{700},new float[]{1500},0));assertTrue(suppress());close(duplicate);idle(500);assertEquals(List.of(LockProtocol.NEXT),session.commands);assertTrue(diagnostic("NATIVE_DUPLICATE_EVENT")>0);}
    @Test public void recognizedEditKeepsOnlyCurrentFrameAndDefersCommandUntilAfter()throws Throwable{
        long now=clock();twoTap(now,false);feed(0,now+80,new int[]{8},new float[]{700},new float[]{1500},0);feed(5,now+85,new int[]{8,12},new float[]{700,800},new float[]{1500,1500},1);feed(6,now+90,new int[]{12,8},new float[]{800,700},new float[]{1500,1500},0);
        XC_MethodHook.MethodHookParam up=begin(event(1,now+95,new int[]{8},new float[]{700},new float[]{1500},0));assertTrue(session.commands.isEmpty());assertTrue(suppress());close(up);assertFalse(suppress());idle(200);assertEquals(List.of(LockProtocol.EDIT),session.commands);
    }
    @Test public void rejectedNestedRegionRevokesOuterArbitration()throws Throwable{long now=clock();tap(now);single(0,now+80,700,1500);XC_MethodHook.MethodHookParam outer=begin(event(1,now+110,new int[]{4},new float[]{700},new float[]{1500},0));assertTrue(suppress());XC_MethodHook.MethodHookParam rejected=begin(event(1,now+111,new int[]{4},new float[]{5},new float[]{1500},0));assertFalse(suppress());close(rejected);assertFalse(suppress());close(outer);idle(500);assertTrue(session.commands.isEmpty());}
    @Test public void cancelNestedEventRevokesOuterArbitration()throws Throwable{long now=clock();tap(now);single(0,now+80,700,1500);XC_MethodHook.MethodHookParam outer=begin(event(1,now+110,new int[]{4},new float[]{700},new float[]{1500},0));assertTrue(suppress());XC_MethodHook.MethodHookParam cancelled=begin(event(3,now+111,new int[]{4},new float[]{700},new float[]{1500},0));assertFalse(suppress());close(cancelled);close(outer);idle(500);assertTrue(session.commands.isEmpty());}
    @Test public void authenticationRejectsAfterEntryCounterButBeforeRegionHit()throws Throwable{Object monitor=get(fixture,"monitor");set(monitor,"mPrimaryBouncerIsOrWillBeShowing",true);single(0,clock(),700,1500);assertTrue(diagnostic("NATIVE_ENTRY_ROOT")>0);assertTrue(diagnostic("NATIVE_AUTHENTICATION")>0);assertEquals(0,((com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout)get(fixture,"stack")).hitTests);}
    @Test public void protectedAreaRejectStillProvesNativeEntryReached()throws Throwable{single(0,clock(),5,1500);assertTrue(diagnostic("NATIVE_ENTRY_ROOT")>0);assertTrue(diagnostic("NATIVE_EDGE_TOP_REGION")>0);assertTrue(session.commands.isEmpty());}
    @Test public void absentSourceIdentityCannotActionOrSuppress()throws Throwable{set(observer,"identity",new RuntimeApkIdentity.Result(false,false,"TEST_NOT_VERIFIED",null,null));single(0,clock(),700,1500);assertFalse(suppress());assertTrue(session.commands.isEmpty());assertTrue(diagnostic("NATIVE_SESSION_INVALID")>0);}
    @Test public void missingSleepHookPreventsRecognitionRatherThanRaceNativePower()throws Throwable{NativeSystemUiHooks noSleep=NativeSystemUiHooks.installVerified(injector.getClass().getClassLoader(),type->!type.getName().contains("bindView$2"),new NativeSystemUiHooksTest.Recorder());set(observer,"nativeHooks",noSleep);tap(clock());idle(500);assertTrue(session.commands.isEmpty());assertTrue(diagnostic("NATIVE_HOOK_SLEEP_UNAVAILABLE")>0);}
    @Test public void lateDoubleDecisionAfterDisableDoesNotSend()throws Throwable{long now=clock();tap(now);tap(now+80);session.ready=false;idle(600);assertTrue(session.commands.isEmpty());}
    @Test public void lateDoubleDecisionAfterTargetTicketChangedDoesNotSend()throws Throwable{long now=clock();tap(now);tap(now+80);session.ticket="LOCK-next-target";idle(600);assertTrue(session.commands.isEmpty());}
    @Test public void lateDoubleDecisionAfterHostReplacementDoesNotSend()throws Throwable{long now=clock();tap(now);tap(now+80);injector.doubleTapHelper=new com.android.keyguard.DoubleTapHelper();idle(600);assertTrue(session.commands.isEmpty());}
    @Test public void queuedCommandRevocationDoesNotSendAfterRecognizedTriple()throws Throwable{long now=clock();tap(now);tap(now+80);tap(now+160);invoke("revokeNative",new Class<?>[0]);idle(600);assertTrue(session.commands.isEmpty());}
    @Test public void moveAndLongHoldCancelWithoutSleepTakeover()throws Throwable{long now=clock();single(0,now,700,1500);single(2,now+20,750,1500);single(1,now+30,750,1500);idle(500);assertTrue(session.commands.isEmpty());assertFalse(suppress());single(0,now+700,700,1500);single(1,now+1700,700,1500);idle(1800);assertTrue(session.commands.isEmpty());}
    @Test public void nonMainSleepCallbackNeverReadsSessionOrView()throws Exception{int before=session.statusCalls;AtomicBoolean result=new AtomicBoolean(true);Thread thread=new Thread(()->result.set((Boolean)invokeUnchecked("suppressNativeSleep",new Class<?>[]{Object.class},new KeyguardPanelViewInjector$bindView$2())));thread.start();thread.join(3000);assertFalse(thread.isAlive());assertFalse(result.get());assertEquals(before,session.statusCalls);}
    @Test public void modeRevokeClearsPendingEditAndPreventsLateAuthEntry()throws Throwable{
        long now=clock();twoTap(now,false);twoTap(now+80,true);idle(200);assertNotNull(get(observer,"pendingEditRoot"));long sequence=(Long)get(observer,"pendingEditSequence");
        invoke("revokeNative",new Class<?>[0]);assertNull(get(observer,"pendingEditRoot"));assertEquals(-1L,get(observer,"pendingEditSequence"));
        PendingIntent intent=PendingIntent.getActivity(context,78,new Intent(context,Activity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        invoke("launchEdit",new Class<?>[]{channel.getClass(),long.class,PendingIntent.class},channel,sequence,intent);assertEquals(0,diagnostic("NATIVE_AUTH_ENTRY_SENT"));
    }
    @Test public void legacyRootRemainsObserveOnlyEvenWhenHostNotReady()throws Throwable{session.ready=false;MotionEvent event=event(0,clock(),new int[]{4},new float[]{700},new float[]{1500},0);try{invoke("observe",new Class<?>[]{View.class,MotionEvent.class},get(fixture,"shade"),event);}finally{event.recycle();}assertTrue(diagnostic("NATIVE_LEGACY_OBSERVE_ONLY")>0);assertTrue(session.commands.isEmpty());}
    @Test public void nativeReadDoesNotModifyLockDomainTicketInCommand()throws Throwable{long now=clock();tap(now);tap(now+80);tap(now+160);idle(600);assertEquals(List.of(LockProtocol.PREVIOUS),session.commands);assertEquals(List.of(TICKET),session.tickets);}
    @Test public void originalRootDispatchExceptionRevokesAlreadyRecognizedQueuedCommand()throws Throwable{long now=clock();tap(now);tap(now+80);single(0,now+160,700,1500);XC_MethodHook.MethodHookParam finalUp=begin(event(1,now+190,new int[]{4},new float[]{700},new float[]{1500},0));Throwable original=new IllegalStateException("OEM-root");finalUp.setThrowable(original);close(finalUp);assertSame(original,finalUp.getThrowable());idle(600);assertTrue(session.commands.isEmpty());}
    private void defaultThemeInternalCancelInsidePointer(XC_MethodHook.MethodHookParam pointer)throws Throwable{
        Object shade=get(fixture,"shade"),interaction=get(shade,"mInteractionEventHandler"),window=get(interaction,"this$0");Method method=window.getClass().getDeclaredMethod("cancelCurrentTouch$1");
        XC_MethodHook.MethodHookParam scope=NativeSystemUiHooksTest.parameter(method,window);XC_MethodHook hook=NativeSystemUiHooksTest.callbackFor(hooks,method);hook.callBefore(scope);
        XC_MethodHook.MethodHookParam cancel=begin(event(3,clock(),new int[]{0},new float[]{0},new float[]{0},0));close(cancel);hook.callAfter(scope);close(pointer);
    }
    private void rootTwoTapWithOemInternalCancel(long time)throws Throwable{
        feed(0,time,new int[]{8},new float[]{700},new float[]{1500},0);
        XC_MethodHook.MethodHookParam pointer=begin(event(5,time+5,new int[]{8,12},new float[]{700,800},new float[]{1500,1500},1));defaultThemeInternalCancelInsidePointer(pointer);
        feed(6,time+10,new int[]{12,8},new float[]{800,700},new float[]{1500,1500},0);feed(1,time+15,new int[]{8},new float[]{700},new float[]{1500},0);
    }
    @Test public void rootObserverExactDefaultInternalCancelThenTwoTapStillSendsEdit()throws Throwable{long now=clock();rootTwoTapWithOemInternalCancel(now);rootTwoTapWithOemInternalCancel(now+80);idle(300);assertEquals(diagnosticSummary(),List.of(LockProtocol.EDIT),session.commands);assertEquals(2,diagnostic("NATIVE_INTERNAL_CANCEL_OBSERVED"));}
    @Test public void customThemeDoesNotNeedAnyInjectorCallbackToReceiveTwoFingers()throws Throwable{com.miui.systemui.util.CommonUtil.defaultTheme=false;long now=clock();twoTap(now,false);twoTap(now+80,true);idle(300);assertEquals(diagnosticSummary(),List.of(LockProtocol.EDIT),session.commands);assertEquals(0,diagnostic("NATIVE_ENTRY_INJECTOR"));assertTrue(diagnostic("NATIVE_RAW_ROOT_POINTER_UP")>0);}
    @Test public void ordinaryNaturalCancelStillRevokesRootTwoFingerCandidate()throws Throwable{long now=clock();twoTap(now,false);single(3,now+40,700,1500);twoTap(now+80,true);idle(500);assertTrue(session.commands.isEmpty());assertEquals(0,diagnostic("NATIVE_INTERNAL_CANCEL_OBSERVED"));}
    private void dangerousCancellationRevokesTwoFingerRootSequence(Class<?> type,String name,Class<?>... args)throws Throwable{
        long now=clock();twoTap(now,false);feed(0,now+80,new int[]{8},new float[]{700},new float[]{1500},0);XC_MethodHook.MethodHookParam pointer=begin(event(5,now+85,new int[]{8,12},new float[]{700,800},new float[]{1500,1500},1));
        Method method=type.getDeclaredMethod(name,args);XC_MethodHook hook=NativeSystemUiHooksTest.callbackFor(hooks,method);XC_MethodHook.MethodHookParam danger=NativeSystemUiHooksTest.parameter(method,type.getConstructor().newInstance(),new Object[args.length]);hook.callBefore(danger);defaultThemeInternalCancelInsidePointer(pointer);hook.callAfter(danger);
        feed(6,now+90,new int[]{12,8},new float[]{800,700},new float[]{1500,1500},0);feed(1,now+95,new int[]{8},new float[]{700},new float[]{1500},0);idle(500);
        assertTrue(session.commands.isEmpty());assertEquals(0,diagnostic("NATIVE_INTERNAL_CANCEL_OBSERVED"));assertTrue(diagnostic("NATIVE_INTERNAL_CANCEL_DANGEROUS_SOURCE")>0);
    }
    @Test public void realObserverCollapseCallerCannotBorrowDefaultPointerException()throws Throwable{dangerousCancellationRevokesTwoFingerRootSequence(com.android.systemui.shade.ShadeControllerImpl.class,"cancelExpansionAndCollapseShade");}
    @Test public void realObserverSleepCallerCannotBorrowDefaultPointerException()throws Throwable{dangerousCancellationRevokesTwoFingerRootSequence(com.android.systemui.statusbar.phone.CentralSurfacesImpl$8.class,"onFinishedGoingToSleep");}
    @Test public void realObserverDozeCallerCannotBorrowDefaultPointerException()throws Throwable{dangerousCancellationRevokesTwoFingerRootSequence(com.android.systemui.doze.DozeTriggers$$ExternalSyntheticLambda3.class,"accept",Object.class);}
}
