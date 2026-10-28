package personal.wallpaper.os4.e1.lockinput;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** 一个已核业务接口族；只做结构选择，不用反射成功替代每帧语义／安全准入。 */
final class SystemUiCapabilityContract {
    static final String FAMILY="MIUI_SHADE_KEYGUARD_V1";
    interface Resolver { Class<?> resolve(String name)throws Exception; }
    static final class Result {
        final boolean supported;final String reason;
        Result(boolean supported,String reason){this.supported=supported;this.reason=reason;}
    }
    // 参数／返回值和合成类属于同一个业务族；不搜索相似方法，不按设备或哈希选择。
    static final String[][] METHODS={
        {"com.android.systemui.shade.NotificationShadeWindowView","dispatchTouchEvent","boolean","android.view.MotionEvent"},
        {"com.android.keyguard.injector.KeyguardPanelViewInjector","onTouchEvent","boolean","android.view.MotionEvent","int","float","float","boolean","boolean","boolean"},
        {"com.android.keyguard.injector.KeyguardPanelViewInjector","getNotificationPanelViewController","com.android.systemui.shade.NotificationPanelViewController"},
        {"com.android.keyguard.injector.KeyguardPanelViewInjector","getKeyguardPanelViewController","com.android.keyguard.panel.KeyguardPanelViewController"},
        {"com.android.keyguard.injector.KeyguardPanelViewInjector","isShowEditLoading","boolean"},
        {"com.android.keyguard.injector.KeyguardPanelViewInjector$bindView$2","onDoubleTap","boolean"},
        {"com.android.keyguard.DoubleTapHelper","onTouchEvent","void","android.view.MotionEvent"},
        {"com.android.systemui.shade.NotificationPanelViewControllerInjector","getPanelViewController","com.android.systemui.shade.NotificationPanelViewController"},
        {"com.android.systemui.shade.NotificationPanelViewController","isExpandingOrCollapsing","boolean"},
        {"com.android.systemui.shade.NotificationPanelViewController","isTracking","boolean"},
        {"com.android.systemui.shade.NotificationShadeWindowViewController","cancelCurrentTouch$1","void"},
        {"com.android.keyguard.panel.KeyguardPanelViewController","onKeyguardViewBind","void","androidx.constraintlayout.widget.ConstraintLayout"},
        {"com.android.keyguard.panel.KeyguardPanelViewController","onViewDetachedFromWindow","void","android.view.View"},
        {"com.android.systemui.shade.ShadeControllerImpl","cancelExpansionAndCollapseShade","void"},
        {"com.android.systemui.statusbar.phone.CentralSurfacesImpl$8","onFinishedGoingToSleep","void"},
        {"com.android.systemui.doze.DozeTriggers$$ExternalSyntheticLambda3","accept","void","java.lang.Object"}
    };
    static final String[][] FIELDS={
        {"com.android.systemui.shade.NotificationShadeWindowView","mInteractionEventHandler","reference"},
        {"com.android.systemui.shade.NotificationShadeWindowViewController$1","this$0","reference"},
        {"com.android.systemui.shade.NotificationShadeWindowViewController","mView","reference"},
        {"com.android.systemui.shade.NotificationShadeWindowViewController","mNPVCInjector","reference"},
        {"com.android.systemui.shade.NotificationShadeWindowViewController","mPrimaryBouncerInteractor","reference"},
        {"com.android.systemui.shade.NotificationShadeWindowViewController","mAodInterceptingTouches","reference"},
        {"com.android.systemui.shade.NotificationShadeWindowViewController","mHandlingExternalTouch","boolean"},
        {"com.android.systemui.shade.NotificationShadeWindowViewController","mExternalTouchIntercepted","boolean"},
        {"com.android.systemui.shade.NotificationShadeWindowViewController","mIsOcclusionTransitionRunning","boolean"},
        {"com.android.systemui.shade.NotificationShadeWindowViewController","mExpandAnimationRunning","boolean"},
        {"com.android.systemui.shade.NotificationShadeWindowViewController","mIsTrackingBarGesture","boolean"},
        {"com.android.systemui.shade.NotificationPanelViewController","mBarState","int"},
        {"com.android.systemui.shade.NotificationPanelViewController","mDozing","boolean"},
        {"com.android.systemui.shade.NotificationPanelViewController","mKeyguardPanelViewInjector","reference"},
        {"com.android.systemui.shade.NotificationPanelViewController","mNotificationStackScrollLayoutController","reference"},
        {"com.android.systemui.shade.NotificationPanelViewController","mKeyguardStateController","reference"},
        {"com.android.systemui.shade.NotificationPanelViewController","mStatusBarKeyguardViewManager","reference"},
        {"com.android.systemui.shade.NotificationPanelViewController","mAlternateBouncerInteractor","reference"},
        {"com.android.keyguard.panel.KeyguardPanelViewController","keyguardShowing","boolean"},
        {"com.android.keyguard.panel.KeyguardPanelViewController","keyguardBouncerShowing","boolean"},
        {"com.android.keyguard.panel.KeyguardPanelViewController","keyguardUpdateMonitor","reference"},
        {"com.android.keyguard.injector.KeyguardPanelViewInjector","keyguardEditorHelper$delegate","reference"},
        {"com.android.keyguard.injector.KeyguardPanelViewInjector","controlCenterImpl","reference"},
        {"com.android.keyguard.injector.KeyguardPanelViewInjector","doubleTapHelper","reference"},
        {"com.android.keyguard.DoubleTapHelper","mDoubleTapListener","reference"},
        {"com.android.keyguard.DoubleTapHelper","mView","reference"},
        {"com.android.keyguard.injector.KeyguardPanelViewInjector$bindView$2","$tmp0","reference"}
    };
    private SystemUiCapabilityContract(){}
    static Result inspect(ClassLoader loader,NativeSystemUiHooks.SourceVerifier source){
        return inspect(name->{Class<?> type=Class.forName(name,false,loader);if(type.getClassLoader()!=loader||!source.matches(type))throw new SecurityException();return type;},loader);
    }
    static Result inspect(Resolver resolver,ClassLoader loader){
        String reason="NATIVE_CAPABILITY_METHOD_MISSING";
        try{
            for(String[] spec:METHODS){
                Class<?> owner=resolver.resolve(spec[0]);Class<?>[] args=new Class<?>[spec.length-3];
                for(int i=0;i<args.length;i++)args[i]=type(spec[i+3],loader);
                Method method=owner.getDeclaredMethod(spec[1],args);
                if(method.getReturnType()!=type(spec[2],loader)||Modifier.isStatic(method.getModifiers())||Modifier.isAbstract(method.getModifiers())||Modifier.isNative(method.getModifiers()))return new Result(false,"NATIVE_CAPABILITY_SIGNATURE_REJECTED");
            }
            Class<?> common=resolver.resolve("com.miui.systemui.util.CommonUtil");Method theme=common.getDeclaredMethod("isDefaultLockScreenTheme");
            if(theme.getReturnType()!=boolean.class||!Modifier.isStatic(theme.getModifiers())||Modifier.isNative(theme.getModifiers()))return new Result(false,"NATIVE_CAPABILITY_SIGNATURE_REJECTED");
            reason="NATIVE_CAPABILITY_FIELD_REJECTED";
            for(String[] spec:FIELDS){Field field=LockInputBroker.field(resolver.resolve(spec[0]),spec[1]);if(Modifier.isStatic(field.getModifiers()))return new Result(false,reason);
                if(spec[2].equals("reference")){if(field.getType().isPrimitive())return new Result(false,reason);}
                else if(field.getType()!=type(spec[2],loader))return new Result(false,reason);
            }
            return new Result(true,"NATIVE_HOOK_CONTRACT_V1_READY");
        }catch(SecurityException refused){return new Result(false,"NATIVE_CAPABILITY_SOURCE_REJECTED");}
        catch(Throwable unavailable){return new Result(false,reason);}
    }
    private static Class<?> type(String name,ClassLoader loader)throws ClassNotFoundException{
        return switch(name){case "void"->void.class;case "boolean"->boolean.class;case "int"->int.class;case "float"->float.class;default->Class.forName(name,false,loader);};
    }
}
