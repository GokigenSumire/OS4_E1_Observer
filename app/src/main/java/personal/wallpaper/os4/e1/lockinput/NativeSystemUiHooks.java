package personal.wallpaper.os4.e1.lockinput;

import android.view.MotionEvent;
import android.view.View;
import android.os.Looper;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.ArrayDeque;

/** Root与限定息屏listener以最高Hook优先级运行；原始触摸返回值保持系统所有。 */
final class NativeSystemUiHooks {
    interface Callback {
        void observed(String source);
        Object begin(Object injector,MotionEvent event,int state,boolean expanding,boolean qs,boolean dozing);
        void end(Object frame);
        boolean suppress(Object listener);
        void lifecycle(Object controller);
        default void bindingRejected(String reason){observed(reason);}
        default boolean internalCancelAllowed(Object frame,Object windowController,View root){return false;}
        default boolean nativeRouteSelected(){return true;}
    }
    private final Callback callback;
    private final ArrayList<XC_MethodHook.Unhook> handles=new ArrayList<>();
    private static final class RootFrame {
        final NativeRootBinding.Result binding;final Object frame;final int action;final Thread thread;
        RootFrame(NativeRootBinding.Result binding,Object frame,int action){this.binding=binding;this.frame=frame;this.action=action;thread=Thread.currentThread();}
    }
    private final IdentityHashMap<XC_MethodHook.MethodHookParam,RootFrame> frames=new IdentityHashMap<>(),cancellations=new IdentityHashMap<>();
    private final ThreadLocal<ArrayDeque<RootFrame>> rootFrames=ThreadLocal.withInitial(ArrayDeque::new),cancelFrames=ThreadLocal.withInitial(ArrayDeque::new);
    private final ThreadLocal<ArrayDeque<XC_MethodHook.MethodHookParam>> dangerousCalls=ThreadLocal.withInitial(ArrayDeque::new);
    private boolean rootReady,sleepReady,lifecycleReady,internalCancelReady,cancelCallersReady;
    interface SourceVerifier {boolean matches(Class<?> type)throws Exception;}
    private NativeSystemUiHooks(Callback callback){this.callback=callback;}
    static NativeSystemUiHooks install(ClassLoader loader,String source,Callback callback){
        return installWithContract(loader,type->RuntimeApkIdentity.loaderMatches(type.getClassLoader(),source),callback);
    }
    static NativeSystemUiHooks install(ClassLoader loader,RuntimeApkIdentity.Result source,Callback callback){
        return installWithContract(loader,type->source.stillSameFile()&&RuntimeApkIdentity.loaderMatches(type.getClassLoader(),source.sources),callback);
    }
    static NativeSystemUiHooks installWithContract(ClassLoader loader,SourceVerifier verifier,Callback callback){
        SystemUiCapabilityContract.Result result=SystemUiCapabilityContract.inspect(loader,verifier);
        callback.observed(result.reason);
        if(!result.supported)return new NativeSystemUiHooks(callback);
        return installVerified(loader,verifier,callback);
    }
    static NativeSystemUiHooks installVerified(ClassLoader loader,SourceVerifier verifier,Callback callback){
        NativeSystemUiHooks installed=new NativeSystemUiHooks(callback);
        try{
            Class<?> root=verified(loader,verifier,"com.android.systemui.shade.NotificationShadeWindowView");Method dispatch=declared(root,"dispatchTouchEvent",boolean.class,MotionEvent.class);
            installed.handles.add(XposedBridge.hookMethod(dispatch,new XC_MethodHook(Integer.MAX_VALUE){
                @Override protected void beforeHookedMethod(MethodHookParam param){
                    callback.observed("NATIVE_ENTRY_ROOT");MotionEvent event=touch(param,root);if(event==null)return;
                    int action=event.getActionMasked();callback.observed(raw("ROOT",action));
                    if(!callback.nativeRouteSelected())return;
                    RootFrame internal=installed.cancelFrames.get().peekLast();
                    if(action==MotionEvent.ACTION_CANCEL&&internal!=null&&internal.thread==Thread.currentThread()&&internal.binding.root==param.thisObject&&installed.rootFrames.get().peekLast()==internal){
                        NativeRootBinding.Result fresh=NativeRootBinding.read(param.thisObject);
                        boolean permit=false;try{permit=installed.dangerousCalls.get().isEmpty()&&fresh.allowed&&fresh.windowController==internal.binding.windowController&&fresh.panelController==internal.binding.panelController&&fresh.injector==internal.binding.injector&&NativeRootBinding.cancelSourceEligible(fresh)&&callback.internalCancelAllowed(internal.frame,fresh.windowController,fresh.root);}catch(Throwable ignored){callback.lifecycle(null);}
                        if(permit){callback.observed("NATIVE_INTERNAL_CANCEL_OBSERVED");return;}
                    }
                    NativeRootBinding.Result binding=NativeRootBinding.read(param.thisObject);
                    if(!binding.allowed){callback.bindingRejected(binding.reason);callback.lifecycle(null);return;}
                    Object owned;try{owned=callback.begin(binding.injector,event,binding.barState,binding.expanding,binding.qsExpanded,binding.dozing);}catch(Throwable ignored){callback.lifecycle(null);return;}
                    if(owned==null)return;RootFrame frame=new RootFrame(binding,owned,action);
                    synchronized(installed.frames){if(installed.frames.size()>=NativeGestureOwnership.MAX_DEPTH||installed.rootFrames.get().size()>=NativeGestureOwnership.MAX_DEPTH){callback.end(owned);callback.lifecycle(null);return;}installed.frames.put(param,frame);installed.rootFrames.get().addLast(frame);}
                }
                @Override protected void afterHookedMethod(MethodHookParam param){
                    RootFrame frame;synchronized(installed.frames){frame=installed.frames.remove(param);}if(frame==null)return;
                    if(param.hasThrowable())installed.revoke();
                    try{callback.end(frame.frame);}catch(Throwable ignored){installed.revoke();}
                    finally{ArrayDeque<RootFrame> stack=installed.rootFrames.get();if(stack.peekLast()==frame)stack.removeLast();else{stack.clear();installed.cancelFrames.get().clear();installed.revoke();}if(stack.isEmpty())installed.rootFrames.remove();}
                }
            }));installed.rootReady=true;callback.observed("NATIVE_HOOK_ROOT_READY");
        }catch(Throwable ignored){callback.observed("NATIVE_HOOK_ROOT_UNAVAILABLE");}
        try{
            Class<?> injector=verified(loader,verifier,"com.android.keyguard.injector.KeyguardPanelViewInjector");
            Method touch=injector.getDeclaredMethod("onTouchEvent",MotionEvent.class,int.class,float.class,float.class,boolean.class,boolean.class,boolean.class);
            if(touch.getReturnType()!=boolean.class||touch.getDeclaringClass()!=injector)throw new IllegalArgumentException();
            touch.setAccessible(true);
            installed.handles.add(XposedBridge.hookMethod(touch,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam param){
                    callback.observed("NATIVE_ENTRY_INJECTOR");
                    if(param.thisObject==null||param.thisObject.getClass()!=injector||param.args==null||param.args.length!=7
                            ||!(param.args[0] instanceof MotionEvent)||!(param.args[1] instanceof Integer)
                            ||!(param.args[4] instanceof Boolean)||!(param.args[5] instanceof Boolean)||!(param.args[6] instanceof Boolean))return;
                    callback.observed(raw("INJECTOR",((MotionEvent)param.args[0]).getActionMasked()));
                }
            }));
            callback.observed("NATIVE_HOOK_INJECTOR_READY");
        }catch(Throwable ignored){callback.observed("NATIVE_HOOK_INJECTOR_UNAVAILABLE");}
        try{
            Class<?> listener=verified(loader,verifier,"com.android.keyguard.injector.KeyguardPanelViewInjector$bindView$2");
            Method sleep=listener.getDeclaredMethod("onDoubleTap");
            if(sleep.getReturnType()!=boolean.class||sleep.getDeclaringClass()!=listener)throw new IllegalArgumentException();
            sleep.setAccessible(true);
            installed.handles.add(XposedBridge.hookMethod(sleep,new XC_MethodHook(Integer.MAX_VALUE){
                @Override protected void beforeHookedMethod(MethodHookParam param){
                    callback.observed("NATIVE_ENTRY_SLEEP_CALLBACK");
                    if(param.thisObject==null||param.thisObject.getClass()!=listener||param.args==null||param.args.length!=0)return;
                    boolean suppress=false;try{suppress=callback.suppress(param.thisObject);}catch(Throwable ignored){callback.lifecycle(null);}
                    // 唯一返回值接管点：只允许当前同步准入frame，非原生触摸分发、非PowerManager。
                    if(suppress)param.setResult(true);
                }
            }));
            installed.sleepReady=true;callback.observed("NATIVE_HOOK_SLEEP_READY");
        }catch(Throwable ignored){callback.observed("NATIVE_HOOK_SLEEP_UNAVAILABLE");}
        installed.observeMethod(loader,verifier,"com.android.systemui.shade.NotificationPanelView","dispatchTouchEvent",boolean.class,"NATIVE_ENTRY_PANEL","NATIVE_HOOK_PARENT_READY","NATIVE_HOOK_PARENT_UNAVAILABLE");
        installed.observeMethod(loader,verifier,"com.android.systemui.shade.NotificationPanelViewController$TouchHandler","onInterceptTouchEvent",boolean.class,"NATIVE_ENTRY_INTERCEPT","NATIVE_HOOK_PARENT_READY","NATIVE_HOOK_PARENT_UNAVAILABLE");
        installed.observeMethod(loader,verifier,"com.android.systemui.shade.NotificationPanelViewController$TouchHandler","onTouchEvent",boolean.class,"NATIVE_ENTRY_TOUCH_HANDLER","NATIVE_HOOK_PARENT_READY","NATIVE_HOOK_PARENT_UNAVAILABLE");
        installed.observeMethod(loader,verifier,"com.android.keyguard.DoubleTapHelper","onTouchEvent",void.class,"NATIVE_ENTRY_HELPER","NATIVE_HOOK_HELPER_READY","NATIVE_HOOK_HELPER_UNAVAILABLE");
        try{
            Class<?> controller=verified(loader,verifier,"com.android.keyguard.panel.KeyguardPanelViewController");
            Class<?> constraint=Class.forName("androidx.constraintlayout.widget.ConstraintLayout",false,loader);
            Method bind=controller.getDeclaredMethod("onKeyguardViewBind",constraint),detach=controller.getDeclaredMethod("onViewDetachedFromWindow",View.class);
            for(Method method:new Method[]{bind,detach}){
                if(method.getReturnType()!=void.class||method.getDeclaringClass()!=controller)throw new IllegalArgumentException();
                method.setAccessible(true);installed.handles.add(XposedBridge.hookMethod(method,new XC_MethodHook(){
                    @Override protected void beforeHookedMethod(MethodHookParam param){callback.lifecycle(param.thisObject);}
                }));
            }
            callback.observed("NATIVE_HOOK_LIFECYCLE_READY");
            installed.lifecycleReady=true;
        }catch(Throwable ignored){callback.observed("NATIVE_HOOK_LIFECYCLE_UNAVAILABLE");}
        try{
            Class<?> window=verified(loader,verifier,"com.android.systemui.shade.NotificationShadeWindowViewController");Method cancel=declared(window,"cancelCurrentTouch$1",void.class);
            installed.handles.add(XposedBridge.hookMethod(cancel,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam param){
                    if(Looper.myLooper()!=Looper.getMainLooper()||param.thisObject==null||param.thisObject.getClass()!=window||param.args==null||param.args.length!=0)return;
                    RootFrame frame=installed.rootFrames.get().peekLast();
                    if(frame==null||frame.action!=MotionEvent.ACTION_POINTER_DOWN||frame.thread!=Thread.currentThread()||frame.binding.windowController!=param.thisObject)return;
                    NativeRootBinding.Result fresh=NativeRootBinding.read(frame.binding.root);
                    if(!fresh.allowed||fresh.windowController!=param.thisObject||fresh.injector!=frame.binding.injector||fresh.panelController!=frame.binding.panelController)return;
                    if(!installed.dangerousCalls.get().isEmpty()||!NativeRootBinding.cancelSourceEligible(fresh)){callback.observed("NATIVE_INTERNAL_CANCEL_SOURCE_REJECTED");return;}
                    boolean allowed=false;try{allowed=callback.internalCancelAllowed(frame.frame,param.thisObject,frame.binding.root);}catch(Throwable ignored){callback.lifecycle(null);}
                    if(!allowed)return;
                    synchronized(installed.cancellations){if(installed.cancelFrames.get().size()>=NativeGestureOwnership.MAX_DEPTH){callback.lifecycle(null);return;}installed.cancellations.put(param,frame);installed.cancelFrames.get().addLast(frame);}
                    callback.observed("NATIVE_INTERNAL_CANCEL_SCOPE_ENTER");
                }
                @Override protected void afterHookedMethod(MethodHookParam param){
                    RootFrame frame;synchronized(installed.cancellations){frame=installed.cancellations.remove(param);}if(frame==null)return;
                    ArrayDeque<RootFrame> stack=installed.cancelFrames.get();if(stack.peekLast()==frame)stack.removeLast();else{stack.clear();callback.lifecycle(null);}if(stack.isEmpty())installed.cancelFrames.remove();callback.observed("NATIVE_INTERNAL_CANCEL_SCOPE_EXIT");
                }
            }));installed.internalCancelReady=true;callback.observed("NATIVE_HOOK_INTERNAL_CANCEL_READY");
        }catch(Throwable ignored){callback.observed("NATIVE_HOOK_INTERNAL_CANCEL_UNAVAILABLE");}
        int protectedCallers=0;
        if(installed.protectCancelCaller(loader,verifier,"com.android.systemui.shade.ShadeControllerImpl","cancelExpansionAndCollapseShade"))protectedCallers++;
        if(installed.protectCancelCaller(loader,verifier,"com.android.systemui.statusbar.phone.CentralSurfacesImpl$8","onFinishedGoingToSleep"))protectedCallers++;
        if(installed.protectCancelCaller(loader,verifier,"com.android.systemui.doze.DozeTriggers$$ExternalSyntheticLambda3","accept",Object.class))protectedCallers++;
        installed.cancelCallersReady=protectedCallers==3;callback.observed(installed.cancelCallersReady?"NATIVE_HOOK_CANCEL_CALLERS_READY":"NATIVE_HOOK_CANCEL_CALLERS_UNAVAILABLE");
        return installed;
    }
    boolean actionHooksReady(){return rootReady&&sleepReady&&lifecycleReady&&internalCancelReady&&cancelCallersReady;}
    private void revoke(){try{callback.lifecycle(null);}catch(Throwable ignored){ }}
    private boolean protectCancelCaller(ClassLoader loader,SourceVerifier verifier,String name,String methodName,Class<?>... args){
        try{Class<?> type=verified(loader,verifier,name);Method method=declared(type,methodName,void.class,args);
            handles.add(XposedBridge.hookMethod(method,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam param){
                    if(rootFrames.get().isEmpty())return;ArrayDeque<XC_MethodHook.MethodHookParam> calls=dangerousCalls.get();
                    if(calls.size()>=NativeGestureOwnership.MAX_DEPTH){callback.lifecycle(null);return;}calls.addLast(param);callback.observed("NATIVE_INTERNAL_CANCEL_DANGEROUS_SOURCE");callback.lifecycle(null);
                }
                @Override protected void afterHookedMethod(MethodHookParam param){ArrayDeque<XC_MethodHook.MethodHookParam> calls=dangerousCalls.get();if(!calls.contains(param))return;if(calls.peekLast()==param)calls.removeLast();else{calls.clear();callback.lifecycle(null);}if(calls.isEmpty())dangerousCalls.remove();}
            }));return true;
        }catch(Throwable ignored){return false;}
    }
    private void observeMethod(ClassLoader loader,SourceVerifier verifier,String type,String name,Class<?> expectedReturn,String entry,String success,String failure){
        try{
            Class<?> target=verified(loader,verifier,type);Method method=target.getDeclaredMethod(name,MotionEvent.class);method.setAccessible(true);
            if(method.getDeclaringClass()!=target||method.getReturnType()!=expectedReturn)throw new IllegalArgumentException();
            handles.add(XposedBridge.hookMethod(method,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam param){callback.observed(entry);MotionEvent event=touch(param,target);if(event!=null&&expectedReturn==boolean.class){String source=entry.equals("NATIVE_ENTRY_PANEL")?"PANEL":entry.equals("NATIVE_ENTRY_INTERCEPT")?"INTERCEPT":"TOUCH_HANDLER";callback.observed(raw(source,event.getActionMasked()));}}
                @Override protected void afterHookedMethod(MethodHookParam param){
                    if(expectedReturn==boolean.class&&!param.hasThrowable()&&param.getResult() instanceof Boolean){
                        String prefix=entry.equals("NATIVE_ENTRY_PANEL")?"NATIVE_PANEL_":entry.equals("NATIVE_ENTRY_INTERCEPT")?"NATIVE_INTERCEPT_":"NATIVE_TOUCH_";
                        callback.observed(prefix+((Boolean)param.getResult()?"CONSUMED":"NOT_CONSUMED"));
                    }
                }
            }));callback.observed(success);
        }catch(Throwable ignored){callback.observed(failure);}
    }
    private static MotionEvent touch(XC_MethodHook.MethodHookParam param,Class<?> expected){return param.thisObject!=null&&param.thisObject.getClass()==expected&&param.args!=null&&param.args.length==1&&param.args[0] instanceof MotionEvent?(MotionEvent)param.args[0]:null;}
    private static String raw(String source,int action){String suffix=switch(action){case 0->"DOWN";case 1->"UP";case 2->"MOVE";case 3->"CANCEL";case 5->"POINTER_DOWN";case 6->"POINTER_UP";default->"OTHER";};return "NATIVE_RAW_"+source+"_"+suffix;}
    private static Method declared(Class<?> type,String name,Class<?> result,Class<?>... args)throws Exception{Method method=type.getDeclaredMethod(name,args);if(method.getDeclaringClass()!=type||method.getReturnType()!=result)throw new IllegalArgumentException();method.setAccessible(true);return method;}
    private static Class<?> verified(ClassLoader loader,SourceVerifier verifier,String name)throws Exception{
        Class<?> type=Class.forName(name,false,loader);
        if(type.getClassLoader()!=loader||!verifier.matches(type))throw new SecurityException();
        return type;
    }
}
