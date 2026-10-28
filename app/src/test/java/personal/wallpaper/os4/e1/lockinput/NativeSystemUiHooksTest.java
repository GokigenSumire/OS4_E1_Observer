package personal.wallpaper.os4.e1.lockinput;

import android.view.MotionEvent;
import android.view.View;
import com.android.keyguard.DoubleTapHelper;
import com.android.keyguard.injector.KeyguardPanelViewInjector;
import com.android.keyguard.injector.KeyguardPanelViewInjector$bindView$2;
import com.android.keyguard.panel.KeyguardPanelViewController;
import com.android.systemui.shade.NotificationPanelView;
import com.android.systemui.shade.NotificationPanelViewController;
import de.robv.android.xposed.XC_MethodHook;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** 模拟Xposed成对callback验证接线；明确不证明ART实际Hook、OEM方法可达或手机安全。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE,qualifiers="w1200dp-h2400dp-mdpi")
public final class NativeSystemUiHooksTest {
    private NativeSystemUiHooks hooks;
    private Recorder callback;
    private NativeRootBindingTest.Fixture fixture;
    static final class Recorder implements NativeSystemUiHooks.Callback {
        final List<String> reports=new ArrayList<>();int begin,end,lifecycle;boolean suppress,throwBegin,throwEnd,throwSuppress,allowInternal,selected=true;
        final Object frame=new Object();Object lastInjector,lastListener;MotionEvent synchronousEvent;
        @Override public void observed(String source){reports.add(source);}
        @Override public Object begin(Object injector,MotionEvent event,int state,boolean expanding,boolean qs,boolean dozing){
            begin++;lastInjector=injector;synchronousEvent=event;if(throwBegin)throw new IllegalStateException();return frame;
        }
        @Override public void end(Object value){assertSame(frame,value);end++;if(throwEnd)throw new IllegalStateException();}
        @Override public boolean suppress(Object listener){lastListener=listener;if(throwSuppress)throw new IllegalStateException();return suppress;}
        @Override public void lifecycle(Object controller){lifecycle++;}
        @Override public boolean internalCancelAllowed(Object frame,Object window,View root){return allowInternal;}
        @Override public boolean nativeRouteSelected(){return selected;}
    }
    @Before public void before()throws Exception{fixture=new NativeRootBindingTest.Fixture();fixture.before();callback=new Recorder();hooks=NativeSystemUiHooks.installVerified(KeyguardPanelViewInjector.class.getClassLoader(),type->true,callback);}
    @After @SuppressWarnings("unchecked") public void after()throws Exception{Field field=NativeSystemUiHooks.class.getDeclaredField("handles");field.setAccessible(true);for(XC_MethodHook.Unhook handle:(List<XC_MethodHook.Unhook>)field.get(hooks))handle.unhook();}
    static Method injectorMethod()throws Exception{return KeyguardPanelViewInjector.class.getDeclaredMethod("onTouchEvent",MotionEvent.class,int.class,float.class,float.class,boolean.class,boolean.class,boolean.class);}
    static Method rootMethod()throws Exception{return com.android.systemui.shade.NotificationShadeWindowView.class.getDeclaredMethod("dispatchTouchEvent",MotionEvent.class);}
    @Test public void rootAndScopedSleepHooksUseMaximumPriority()throws Exception{assertEquals(Integer.MAX_VALUE,callbackFor(hooks,rootMethod()).priority);assertEquals(Integer.MAX_VALUE,callbackFor(hooks,KeyguardPanelViewInjector$bindView$2.class.getDeclaredMethod("onDoubleTap")).priority);}
    @SuppressWarnings("unchecked") static XC_MethodHook callbackFor(NativeSystemUiHooks hooks,Method method)throws Exception{
        Field field=NativeSystemUiHooks.class.getDeclaredField("handles");field.setAccessible(true);
        for(XC_MethodHook.Unhook handle:(List<XC_MethodHook.Unhook>)field.get(hooks))if(handle.getHookedMethod().equals(method))return handle.getCallback();
        return null;
    }
    static XC_MethodHook.MethodHookParam parameter(Method method,Object receiver,Object... args){XC_MethodHook.MethodHookParam param=new XC_MethodHook.MethodHookParam();param.method=method;param.thisObject=receiver;param.args=args;return param;}
    @SuppressWarnings("unchecked") private int openFrames()throws Exception{Field field=NativeSystemUiHooks.class.getDeclaredField("frames");field.setAccessible(true);return ((java.util.Map<?,?>)field.get(hooks)).size();}
    @Test public void exactOemSignaturesInstallTogether()throws Exception{
        assertTrue(hooks.actionHooksReady());assertNotNull(callbackFor(hooks,injectorMethod()));
        assertNotNull(callbackFor(hooks,KeyguardPanelViewInjector$bindView$2.class.getDeclaredMethod("onDoubleTap")));
        assertTrue(callback.reports.contains("NATIVE_HOOK_LIFECYCLE_READY"));assertTrue(callback.reports.contains("NATIVE_HOOK_HELPER_READY"));
    }
    @Test public void rejectedSourceNeverInstallsActionHook(){Recorder rejected=new Recorder();NativeSystemUiHooks denied=NativeSystemUiHooks.installVerified(KeyguardPanelViewInjector.class.getClassLoader(),type->false,rejected);assertFalse(denied.actionHooksReady());assertEquals(0,rejected.begin);assertTrue(rejected.reports.contains("NATIVE_HOOK_INJECTOR_UNAVAILABLE"));}
    @Test public void onlySleepSourceRejectedDisablesAllActions(){NativeSystemUiHooks denied=NativeSystemUiHooks.installVerified(KeyguardPanelViewInjector.class.getClassLoader(),type->!type.getName().contains("bindView$2"),new Recorder());assertFalse(denied.actionHooksReady());}
    @Test public void verifierExceptionFailsClosed(){NativeSystemUiHooks denied=NativeSystemUiHooks.installVerified(KeyguardPanelViewInjector.class.getClassLoader(),type->{throw new SecurityException();},new Recorder());assertFalse(denied.actionHooksReady());}
    @Test public void parentLoaderDelegationIsNotClaimedAsExactDeclaringLoader(){ClassLoader child=new ClassLoader(KeyguardPanelViewInjector.class.getClassLoader()){};assertFalse(NativeSystemUiHooks.installVerified(child,type->true,new Recorder()).actionHooksReady());}
    @Test public void rootBeforeAndAfterPreserveOriginalFalseResult()throws Throwable{
        MotionEvent event=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,700,1500,0);Method method=rootMethod();XC_MethodHook hook=callbackFor(hooks,method);
        XC_MethodHook.MethodHookParam param=parameter(method,fixture.root,event);param.setResult(false);
        hook.callBefore(param);assertEquals(false,param.getResult());assertEquals(1,callback.begin);assertSame(event,callback.synchronousEvent);assertEquals(1,openFrames());
        hook.callAfter(param);assertEquals(false,param.getResult());assertEquals(1,callback.end);assertEquals(0,openFrames());event.recycle();
    }
    @Test public void exceptionalOriginalMethodStillClearsSynchronousFrame()throws Throwable{
        MotionEvent event=MotionEvent.obtain(0,0,0,700,1500,0);Method method=rootMethod();XC_MethodHook hook=callbackFor(hooks,method);
        XC_MethodHook.MethodHookParam param=parameter(method,fixture.root,event);hook.callBefore(param);
        Throwable original=new IllegalArgumentException("original");param.setThrowable(original);hook.callAfter(param);assertSame(original,param.getThrowable());assertEquals(0,openFrames());event.recycle();
    }
    @Test public void malformedReceiverIsObservedButCannotBegin()throws Throwable{Method method=injectorMethod();XC_MethodHook.MethodHookParam param=parameter(method,new Object(),null,1,0f,0f,false,false,false);callbackFor(hooks,method).callBefore(param);assertEquals(0,callback.begin);assertTrue(callback.reports.contains("NATIVE_ENTRY_INJECTOR"));}
    @Test public void beginFailureRevokesWithoutChangingHostResult()throws Throwable{
        callback.throwBegin=true;MotionEvent event=MotionEvent.obtain(0,0,0,700,1500,0);Method method=rootMethod();XC_MethodHook.MethodHookParam param=parameter(method,fixture.root,event);param.setResult(false);callbackFor(hooks,method).callBefore(param);assertEquals(1,callback.lifecycle);assertEquals(false,param.getResult());assertEquals(0,openFrames());event.recycle();
    }
    @Test public void endFailureRevokesAfterRemovingFrame()throws Throwable{
        callback.throwEnd=true;MotionEvent event=MotionEvent.obtain(0,0,0,700,1500,0);Method method=rootMethod();XC_MethodHook.MethodHookParam param=parameter(method,fixture.root,event);XC_MethodHook hook=callbackFor(hooks,method);hook.callBefore(param);hook.callAfter(param);assertEquals(1,callback.lifecycle);assertEquals(0,openFrames());event.recycle();
    }
    @Test public void exactSleepListenerIsTheOnlyReturnOverride()throws Throwable{
        callback.suppress=true;Method method=KeyguardPanelViewInjector$bindView$2.class.getDeclaredMethod("onDoubleTap");Object listener=new KeyguardPanelViewInjector$bindView$2();XC_MethodHook.MethodHookParam param=parameter(method,listener);param.setResult(false);callbackFor(hooks,method).callBefore(param);assertEquals(true,param.getResult());assertSame(listener,callback.lastListener);
    }
    @Test public void ordinarySleepPassthroughPreservesOriginalResult()throws Throwable{Method method=KeyguardPanelViewInjector$bindView$2.class.getDeclaredMethod("onDoubleTap");XC_MethodHook.MethodHookParam param=parameter(method,new KeyguardPanelViewInjector$bindView$2());param.setResult(false);callbackFor(hooks,method).callBefore(param);assertEquals(false,param.getResult());}
    @Test public void sleepArbitrationFailurePassesThroughAndRevokes()throws Throwable{callback.throwSuppress=true;Method method=KeyguardPanelViewInjector$bindView$2.class.getDeclaredMethod("onDoubleTap");XC_MethodHook.MethodHookParam param=parameter(method,new KeyguardPanelViewInjector$bindView$2());param.setResult(false);callbackFor(hooks,method).callBefore(param);assertEquals(false,param.getResult());assertEquals(1,callback.lifecycle);}
    @Test public void parentObserversPreserveConsumedAndNotConsumedResults()throws Throwable{
        Method method=NotificationPanelViewController.TouchHandler.class.getDeclaredMethod("onTouchEvent",MotionEvent.class);XC_MethodHook hook=callbackFor(hooks,method);MotionEvent event=MotionEvent.obtain(0,0,0,700,1500,0);XC_MethodHook.MethodHookParam param=parameter(method,new NotificationPanelViewController.TouchHandler(),event);
        param.setResult(true);hook.callBefore(param);hook.callAfter(param);assertEquals(true,param.getResult());assertTrue(callback.reports.contains("NATIVE_TOUCH_CONSUMED"));
        param.setResult(false);hook.callAfter(param);assertEquals(false,param.getResult());assertTrue(callback.reports.contains("NATIVE_TOUCH_NOT_CONSUMED"));assertEquals(0,callback.begin);event.recycle();
    }
    @Test public void parentOriginalExceptionDoesNotCreateBooleanOutcome()throws Throwable{Method method=NotificationPanelView.class.getDeclaredMethod("dispatchTouchEvent",MotionEvent.class);XC_MethodHook.MethodHookParam param=parameter(method,null,(Object)null);Throwable original=new RuntimeException();param.setThrowable(original);callbackFor(hooks,method).callAfter(param);assertSame(original,param.getThrowable());assertFalse(callback.reports.contains("NATIVE_PANEL_CONSUMED"));}
    @Test public void helperVoidObservationCannotSuppressOrRecognize()throws Throwable{Method method=DoubleTapHelper.class.getDeclaredMethod("onTouchEvent",MotionEvent.class);assertEquals(void.class,method.getReturnType());XC_MethodHook.MethodHookParam param=parameter(method,new DoubleTapHelper(),(Object)null);callbackFor(hooks,method).callBefore(param);callbackFor(hooks,method).callAfter(param);assertTrue(callback.reports.contains("NATIVE_ENTRY_HELPER"));assertNull(param.getResult());assertEquals(0,callback.begin);}
    @Test public void controllerBindAndDetachBothRevoke()throws Throwable{for(Method method:new Method[]{KeyguardPanelViewController.class.getDeclaredMethod("onKeyguardViewBind",androidx.constraintlayout.widget.ConstraintLayout.class),KeyguardPanelViewController.class.getDeclaredMethod("onViewDetachedFromWindow",View.class)})callbackFor(hooks,method).callBefore(parameter(method,new KeyguardPanelViewController(),(Object)null));assertEquals(2,callback.lifecycle);}
    @Test public void injectorIsObservationOnlyAndCannotBecomeSecondOwner()throws Throwable{MotionEvent event=MotionEvent.obtain(0,0,5,700,1500,0);Method method=injectorMethod();XC_MethodHook hook=callbackFor(hooks,method);XC_MethodHook.MethodHookParam param=parameter(method,fixture.injector,event,1,0f,0f,false,false,false);param.setResult(false);hook.callBefore(param);hook.callAfter(param);assertEquals(0,callback.begin);assertEquals(0,callback.end);assertEquals(false,param.getResult());assertTrue(callback.reports.contains("NATIVE_RAW_INJECTOR_POINTER_DOWN"));event.recycle();}
    @Test public void rootSourceAndCancelSourceAreRequiredNotInjectorObservationSource(){NativeSystemUiHooks noInjector=NativeSystemUiHooks.installVerified(KeyguardPanelViewInjector.class.getClassLoader(),type->!type.getName().equals(KeyguardPanelViewInjector.class.getName()),new Recorder());assertTrue(noInjector.actionHooksReady());NativeSystemUiHooks noCancel=NativeSystemUiHooks.installVerified(KeyguardPanelViewInjector.class.getClassLoader(),type->!type.getName().equals("com.android.systemui.shade.NotificationShadeWindowViewController"),new Recorder());assertFalse(noCancel.actionHooksReady());}
    private Method cancelMethod()throws Exception{return com.android.systemui.shade.NotificationShadeWindowViewController.class.getDeclaredMethod("cancelCurrentTouch$1");}
    private XC_MethodHook.MethodHookParam rootBefore(int action)throws Throwable{MotionEvent event=MotionEvent.obtain(0,0,action,700,1500,0);XC_MethodHook.MethodHookParam param=parameter(rootMethod(),fixture.root,event);param.setResult(false);callbackFor(hooks,rootMethod()).callBefore(param);return param;}
    private void rootAfter(XC_MethodHook.MethodHookParam param)throws Throwable{callbackFor(hooks,rootMethod()).callAfter(param);((MotionEvent)param.args[0]).recycle();}
    @Test public void exactPointerInternalCancelIsObservedWithoutSecondBegin()throws Throwable{
        callback.allowInternal=true;XC_MethodHook.MethodHookParam pointer=rootBefore(5);assertEquals(1,callback.begin);XC_MethodHook.MethodHookParam cancel=parameter(cancelMethod(),fixture.window);callbackFor(hooks,cancelMethod()).callBefore(cancel);
        XC_MethodHook.MethodHookParam nested=rootBefore(3);assertEquals(1,callback.begin);assertEquals(false,nested.getResult());assertTrue(callback.reports.contains("NATIVE_INTERNAL_CANCEL_OBSERVED"));rootAfter(nested);callbackFor(hooks,cancelMethod()).callAfter(cancel);rootAfter(pointer);assertEquals(1,callback.end);assertEquals(0,openFrames());
    }
    @Test public void ordinaryCancelRemainsNaturalOwnerInput()throws Throwable{callback.allowInternal=true;XC_MethodHook.MethodHookParam pointer=rootBefore(5);XC_MethodHook.MethodHookParam cancel=rootBefore(3);assertEquals(2,callback.begin);assertFalse(callback.reports.contains("NATIVE_INTERNAL_CANCEL_OBSERVED"));rootAfter(cancel);rootAfter(pointer);}
    @Test public void failedCallbackAuthorityCannotSkipNestedCancel()throws Throwable{callback.allowInternal=false;XC_MethodHook.MethodHookParam pointer=rootBefore(5);XC_MethodHook.MethodHookParam scope=parameter(cancelMethod(),fixture.window);callbackFor(hooks,cancelMethod()).callBefore(scope);XC_MethodHook.MethodHookParam nested=rootBefore(3);assertEquals(2,callback.begin);assertFalse(callback.reports.contains("NATIVE_INTERNAL_CANCEL_OBSERVED"));rootAfter(nested);callbackFor(hooks,cancelMethod()).callAfter(scope);rootAfter(pointer);}
    @Test public void nonPointerOwnerCannotGrantInternalCancelScope()throws Throwable{callback.allowInternal=true;XC_MethodHook.MethodHookParam down=rootBefore(0);XC_MethodHook.MethodHookParam scope=parameter(cancelMethod(),fixture.window);callbackFor(hooks,cancelMethod()).callBefore(scope);XC_MethodHook.MethodHookParam nested=rootBefore(3);assertEquals(2,callback.begin);assertFalse(callback.reports.contains("NATIVE_INTERNAL_CANCEL_OBSERVED"));rootAfter(nested);callbackFor(hooks,cancelMethod()).callAfter(scope);rootAfter(down);}
    @Test public void foreignControllerCannotGrantInternalCancelScope()throws Throwable{callback.allowInternal=true;XC_MethodHook.MethodHookParam pointer=rootBefore(5);XC_MethodHook.MethodHookParam scope=parameter(cancelMethod(),new com.android.systemui.shade.NotificationShadeWindowViewController());callbackFor(hooks,cancelMethod()).callBefore(scope);XC_MethodHook.MethodHookParam nested=rootBefore(3);assertEquals(2,callback.begin);assertFalse(callback.reports.contains("NATIVE_INTERNAL_CANCEL_OBSERVED"));rootAfter(nested);callbackFor(hooks,cancelMethod()).callAfter(scope);rootAfter(pointer);}
    @Test public void revokedAuthorityDuringInternalMethodCannotHideCancel()throws Throwable{callback.allowInternal=true;XC_MethodHook.MethodHookParam pointer=rootBefore(5);XC_MethodHook.MethodHookParam scope=parameter(cancelMethod(),fixture.window);callbackFor(hooks,cancelMethod()).callBefore(scope);callback.allowInternal=false;XC_MethodHook.MethodHookParam nested=rootBefore(3);assertEquals(2,callback.begin);assertFalse(callback.reports.contains("NATIVE_INTERNAL_CANCEL_OBSERVED"));rootAfter(nested);callbackFor(hooks,cancelMethod()).callAfter(scope);rootAfter(pointer);}
    @Test public void internalScopeAfterExceptionCannotAffectLaterNaturalCancel()throws Throwable{callback.allowInternal=true;XC_MethodHook.MethodHookParam pointer=rootBefore(5);XC_MethodHook.MethodHookParam scope=parameter(cancelMethod(),fixture.window);callbackFor(hooks,cancelMethod()).callBefore(scope);scope.setThrowable(new IllegalStateException("OEM-original"));callbackFor(hooks,cancelMethod()).callAfter(scope);rootAfter(pointer);XC_MethodHook.MethodHookParam natural=rootBefore(3);assertEquals(2,callback.begin);assertFalse(callback.reports.contains("NATIVE_INTERNAL_CANCEL_OBSERVED"));rootAfter(natural);}
    @Test public void allParentRawActionsStayObservational()throws Throwable{Method method=NotificationPanelViewController.TouchHandler.class.getDeclaredMethod("onTouchEvent",MotionEvent.class);for(int action:new int[]{0,1,2,3,5,6}){MotionEvent event=MotionEvent.obtain(0,0,action,700,1500,0);XC_MethodHook.MethodHookParam param=parameter(method,fixture.panel.mTouchHandler,event);param.setResult(true);callbackFor(hooks,method).callBefore(param);callbackFor(hooks,method).callAfter(param);assertEquals(true,param.getResult());event.recycle();}assertEquals(0,callback.begin);assertTrue(callback.reports.contains("NATIVE_RAW_TOUCH_HANDLER_POINTER_DOWN"));assertTrue(callback.reports.contains("NATIVE_RAW_TOUCH_HANDLER_POINTER_UP"));}
    @Test public void mirrorAndNonDefaultThemeNeverGrantInternalCancelException()throws Throwable{
        callback.allowInternal=true;fixture.window.mBrightnessMirror=new android.widget.FrameLayout(fixture.root.getContext());XC_MethodHook.MethodHookParam pointer=rootBefore(5);XC_MethodHook.MethodHookParam scope=parameter(cancelMethod(),fixture.window);callbackFor(hooks,cancelMethod()).callBefore(scope);XC_MethodHook.MethodHookParam nested=rootBefore(3);assertEquals(2,callback.begin);assertTrue(callback.reports.contains("NATIVE_INTERNAL_CANCEL_SOURCE_REJECTED"));assertFalse(callback.reports.contains("NATIVE_INTERNAL_CANCEL_OBSERVED"));rootAfter(nested);callbackFor(hooks,cancelMethod()).callAfter(scope);rootAfter(pointer);
    }
    private void dangerousCallerCannotBorrowPointerRoot(Class<?> type,String name,Class<?>... args)throws Throwable{
        callback.allowInternal=true;XC_MethodHook.MethodHookParam pointer=rootBefore(5);Method method=type.getDeclaredMethod(name,args);Object receiver=type.getConstructor().newInstance();Object[] values=new Object[args.length];XC_MethodHook.MethodHookParam danger=parameter(method,receiver,values);callbackFor(hooks,method).callBefore(danger);
        XC_MethodHook.MethodHookParam scope=parameter(cancelMethod(),fixture.window);callbackFor(hooks,cancelMethod()).callBefore(scope);XC_MethodHook.MethodHookParam nested=rootBefore(3);assertEquals(2,callback.begin);assertTrue(callback.lifecycle>0);assertTrue(callback.reports.contains("NATIVE_INTERNAL_CANCEL_DANGEROUS_SOURCE"));assertFalse(callback.reports.contains("NATIVE_INTERNAL_CANCEL_OBSERVED"));rootAfter(nested);callbackFor(hooks,cancelMethod()).callAfter(scope);callbackFor(hooks,method).callAfter(danger);rootAfter(pointer);
    }
    @Test public void collapseShadeCancellationNeverReceivesDefaultThemeException()throws Throwable{dangerousCallerCannotBorrowPointerRoot(com.android.systemui.shade.ShadeControllerImpl.class,"cancelExpansionAndCollapseShade");}
    @Test public void sleepLifecycleCancellationNeverReceivesDefaultThemeException()throws Throwable{dangerousCallerCannotBorrowPointerRoot(com.android.systemui.statusbar.phone.CentralSurfacesImpl$8.class,"onFinishedGoingToSleep");}
    @Test public void dozeProximityCancellationNeverReceivesDefaultThemeException()throws Throwable{dangerousCallerCannotBorrowPointerRoot(com.android.systemui.doze.DozeTriggers$$ExternalSyntheticLambda3.class,"accept",Object.class);}
    @Test public void missingDangerousCallerHookDisablesActionRatherThanGuessingSource(){NativeSystemUiHooks missing=NativeSystemUiHooks.installVerified(KeyguardPanelViewInjector.class.getClassLoader(),type->!type.getName().equals("com.android.systemui.shade.ShadeControllerImpl"),new Recorder());assertFalse(missing.actionHooksReady());}
    @Test public void legacyRouteBadBindingIsObservedWithoutRevokingLegacyState()throws Throwable{callback.selected=false;fixture.root.mInteractionEventHandler=null;XC_MethodHook.MethodHookParam event=rootBefore(0);rootAfter(event);assertEquals(0,callback.begin);assertEquals(0,callback.lifecycle);assertTrue(callback.reports.contains("NATIVE_RAW_ROOT_DOWN"));assertFalse(callback.reports.contains("NATIVE_ROOT_WINDOW_BINDING"));assertEquals(false,event.getResult());}
}
