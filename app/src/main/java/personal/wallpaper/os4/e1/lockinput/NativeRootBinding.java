package personal.wallpaper.os4.e1.lockinput;

import android.os.Looper;
import android.view.View;
import java.lang.reflect.Method;

/** 前置shade只绑定当前OEM业务图；完整安全准入仍由NativeBusinessSnapshot执行。 */
final class NativeRootBinding {
    static final String[] REASONS={"NATIVE_ROOT_THREAD","NATIVE_ROOT_IDENTITY","NATIVE_ROOT_WINDOW_BINDING","NATIVE_ROOT_PANEL_BINDING","NATIVE_ROOT_INJECTOR_BINDING","NATIVE_ROOT_STATE_UNKNOWN","NATIVE_ROOT_BOUND"};
    static final class Result {
        final boolean allowed;final String reason;final View root;final Object windowController,panelController,injector;
        final int barState;final boolean expanding,qsExpanded,dozing;
        private Result(boolean allowed,String reason,View root,Object window,Object panel,Object injector,int state,boolean expanding,boolean qs,boolean dozing){this.allowed=allowed;this.reason=reason;this.root=root;windowController=window;panelController=panel;this.injector=injector;barState=state;this.expanding=expanding;qsExpanded=qs;this.dozing=dozing;}
    }
    private NativeRootBinding(){ }
    private static Result denied(String reason){return new Result(false,reason,null,null,null,null,0,false,false,false);}
    static Result read(Object source){
        if(Looper.myLooper()!=Looper.getMainLooper())return denied("NATIVE_ROOT_THREAD");
        String stage="NATIVE_ROOT_IDENTITY";
        try{
            if(!(source instanceof View)||!source.getClass().getName().equals("com.android.systemui.shade.NotificationShadeWindowView"))return denied(stage);
            View root=(View)source;ClassLoader loader=source.getClass().getClassLoader();stage="NATIVE_ROOT_WINDOW_BINDING";
            Object interaction=field(source,"mInteractionEventHandler");if(!exact(interaction,"com.android.systemui.shade.NotificationShadeWindowViewController$1",loader))return denied(stage);
            Object window=field(interaction,"this$0");if(!exact(window,"com.android.systemui.shade.NotificationShadeWindowViewController",loader)||field(window,"mView")!=source)return denied(stage);
            stage="NATIVE_ROOT_PANEL_BINDING";Object panelInjector=field(window,"mNPVCInjector");
            if(!exact(panelInjector,"com.android.systemui.shade.NotificationPanelViewControllerInjector",loader))return denied(stage);
            Object panel=call(panelInjector,"getPanelViewController");if(!exact(panel,"com.android.systemui.shade.NotificationPanelViewController",loader))return denied(stage);
            Object panelView=field(panel,"mView"),handler=field(panel,"mTouchHandler");
            if(!exact(panelView,"com.android.systemui.shade.NotificationPanelView",loader)||!(panelView instanceof View)||field(panelInjector,"panelView")!=panelView
                    ||!exact(handler,"com.android.systemui.shade.NotificationPanelViewController$TouchHandler",loader)||field(handler,"this$0")!=panel
                    ||field(panelView,"mTouchHandler")!=handler||!descends(root,(View)panelView))return denied(stage);
            stage="NATIVE_ROOT_INJECTOR_BINDING";Object injector=field(panel,"mKeyguardPanelViewInjector");
            if(!exact(injector,"com.android.keyguard.injector.KeyguardPanelViewInjector",loader)||call(injector,"getNotificationPanelViewController")!=panel||field(injector,"notificationShadeWindowView")!=root)return denied(stage);
            stage="NATIVE_ROOT_STATE_UNKNOWN";Object state=field(panel,"mBarState");if(!(state instanceof Integer))return denied(stage);
            boolean expanding=booleanCall(panel,"isExpandingOrCollapsing"),dozing=bool(panel,"mDozing");Object qs=field(injector,"quickSettingsControllerImpl");
            return new Result(true,"NATIVE_ROOT_BOUND",root,window,panel,injector,(Integer)state,expanding,booleanCall(qs,"getExpanded"),dozing);
        }catch(Throwable ignored){return denied(stage);}
    }
    private static boolean descends(View root,View child){for(int depth=0;depth<32;depth++){if(child==root)return true;if(!(child.getParent() instanceof View))return false;child=(View)child.getParent();}return false;}
    /** 仅确认已核根POINTER_DOWN默认主题分支的当前业务前提，不给普通动作放宽安全。 */
    static boolean cancelSourceEligible(Result captured){
        if(captured==null||!captured.allowed||Looper.myLooper()!=Looper.getMainLooper())return false;
        try{
            Result fresh=read(captured.root);
            if(!fresh.allowed||fresh.windowController!=captured.windowController||fresh.panelController!=captured.panelController||fresh.injector!=captured.injector
                    ||fresh.barState!=1||fresh.expanding||fresh.qsExpanded||fresh.dozing)return false;
            Object window=fresh.windowController,panel=fresh.panelController,central=field(panel,"mCentralSurfaces");
            if(field(window,"mService")!=central||!bool(central,"mDeviceInteractive")||bool(central,"mBouncerShowing")
                    ||bool(central,"mDozing")||booleanCall(central,"shouldIgnoreTouch")||booleanCall(panel,"isTracking")
                    ||!bool(window,"mTouchActive")||bool(window,"mTouchCancelled")||bool(window,"mExpandAnimationRunning"))return false;
            Object status=field(window,"mStatusBarStateController");Object state=call(status,"getCurrentOrUpcomingState");
            if(!(state instanceof Integer)||(Integer)state!=1||booleanCall(status,"isDozing"))return false;
            Object mirror=field(window,"mBrightnessMirror");if(mirror!=null&&(!(mirror instanceof View)||((View)mirror).getVisibility()==View.VISIBLE))return false;
            Class<?> config=Class.forName("com.miui.systemui.util.CommonUtil",false,fresh.root.getClass().getClassLoader());
            if(config.getClassLoader()!=fresh.root.getClass().getClassLoader())return false;
            Method theme=config.getDeclaredMethod("isDefaultLockScreenTheme");
            return theme.getReturnType()==boolean.class&&java.lang.reflect.Modifier.isStatic(theme.getModifiers())&&Boolean.TRUE.equals(theme.invoke(null));
        }catch(Throwable ignored){return false;}
    }
    private static boolean exact(Object value,String name,ClassLoader loader){return value!=null&&name.equals(value.getClass().getName())&&value.getClass().getClassLoader()==loader;}
    private static Object field(Object value,String name)throws Exception{if(value==null)throw new NoSuchFieldException();return LockInputBroker.field(value.getClass(),name).get(value);}
    private static Object call(Object value,String name)throws Exception{if(value==null)throw new NoSuchMethodException();for(Class<?> type=value.getClass();type!=null;type=type.getSuperclass())try{Method method=type.getDeclaredMethod(name);method.setAccessible(true);return method.invoke(value);}catch(NoSuchMethodException ignored){ }throw new NoSuchMethodException();}
    private static boolean bool(Object value,String name)throws Exception{Object result=field(value,name);if(!(result instanceof Boolean))throw new IllegalStateException();return (Boolean)result;}
    private static boolean booleanCall(Object value,String name)throws Exception{Object result=call(value,name);if(!(result instanceof Boolean))throw new IllegalStateException();return (Boolean)result;}
}
