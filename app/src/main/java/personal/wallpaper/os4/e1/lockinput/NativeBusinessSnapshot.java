package personal.wallpaper.os4.e1.lockinput;

import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Matrix;
import android.os.PowerManager;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowInsets;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 原生锁屏业务入口的只读局部快照；不将主题分支或原生双击当成安全授权。 */
final class NativeBusinessSnapshot {
    static final String[] REASONS={
        "NATIVE_ALLOWED","NATIVE_BINDING_UNKNOWN","NATIVE_BINDING_CLASS","NATIVE_BINDING_CONTROLLER",
        "NATIVE_BINDING_ROOT","NATIVE_BINDING_HELPER","NATIVE_BINDING_NOTIFICATION","NATIVE_BINDING_CHANGED",
        "NATIVE_STATE_UNKNOWN","NATIVE_NOT_KEYGUARD","NATIVE_NOT_INTERACTIVE","NATIVE_AUTHENTICATION",
        "NATIVE_SIM_AUTH","NATIVE_TRANSITION","NATIVE_DOZING_AOD","NATIVE_REMOTE_AUTH","NATIVE_EDITOR",
        "NATIVE_QS","NATIVE_CONTROL_CENTER","NATIVE_SHORTCUT_MOVING","NATIVE_REGION_UNKNOWN",
        "NATIVE_PRIMARY_WINDOW","NATIVE_ROOT_GEOMETRY","NATIVE_INSETS_UNKNOWN","NATIVE_SAFE_REGION_EMPTY",
        "NATIVE_NOTIFICATION_LAYOUT","NATIVE_NOTIFICATION_TRANSITION","NATIVE_NOTIFICATION_CHILD_UNKNOWN","NATIVE_NOTIFICATION_UNACCOUNTED",
        "NATIVE_MEDIA_BINDING","NATIVE_MEDIA_UNACCOUNTED","NATIVE_SHORTCUT_BINDING","NATIVE_PROTECTED_LAYOUT",
        "NATIVE_PRIORITY_LAYOUT_UNKNOWN","NATIVE_PRIORITY_PROTECTED_REGION",
        "NATIVE_FINGERPRINT_UNKNOWN","NATIVE_POINT_NONFINITE","NATIVE_EDGE_TOP_REGION",
        "NATIVE_NOTIFICATION_REGION","NATIVE_MEDIA_REGION","NATIVE_SHORTCUT_REGION","NATIVE_FINGERPRINT_REGION",
        "NATIVE_LOCAL_LAYOUT_CHANGED","NATIVE_HIT_TEST_UNKNOWN","NATIVE_HIT_TEST_REGION"
    };
    private static final String INJECTOR="com.android.keyguard.injector.KeyguardPanelViewInjector";
    private static final String CONTROLLER="com.android.keyguard.panel.KeyguardPanelViewController";
    private static final String HELPER="com.android.keyguard.DoubleTapHelper";
    private static final String LISTENER="com.android.keyguard.injector.KeyguardPanelViewInjector$bindView$2";
    private static final String PANEL="com.android.systemui.shade.NotificationPanelViewController";
    private static final String STACK="com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout";
    private static final String STACK_CONTROLLER="com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController";
    private static final String EXPANDABLE="com.android.systemui.statusbar.notification.row.ExpandableView";

    static final class Binding {
        final Object injector,controller,helper,listener,notificationController,stackController,mediaController;
        final View root,shadeRoot;
        final ViewGroup stack;
        private Binding(Object injector,Object controller,View root,View shadeRoot,Object helper,Object listener,
                        Object notificationController,Object stackController,ViewGroup stack,Object mediaController){
            this.injector=injector;this.controller=controller;this.root=root;this.shadeRoot=shadeRoot;
            this.helper=helper;this.listener=listener;this.notificationController=notificationController;
            this.stackController=stackController;this.stack=stack;
            this.mediaController=mediaController;
        }
        boolean same(Binding other){return other!=null&&injector==other.injector&&controller==other.controller
                &&root==other.root&&shadeRoot==other.shadeRoot&&helper==other.helper&&listener==other.listener
                &&notificationController==other.notificationController&&stackController==other.stackController&&stack==other.stack
                &&mediaController==other.mediaController;}
    }
    static final class PointResult {
        final boolean allowed;final String reason;
        private PointResult(boolean allowed,String reason){this.allowed=allowed;this.reason=reason;}
    }
    private static final class ProtectedArea {
        final Rect area;final String reason;
        ProtectedArea(Rect area,String reason){this.area=new Rect(area);this.reason=reason;}
        boolean same(ProtectedArea other){return area.equals(other.area)&&reason.equals(other.reason);}
    }
    /** 仅保存已知局部对象及其祖先的实际几何，不枚举任意根树或记录坐标轨迹。 */
    private static final class Geometry {
        final View view;final ViewParent parent;final Rect rect,clip;
        final int visibility,width,height,scrollX,scrollY,children;
        final boolean shown,attached,layoutRequested;final float alpha,x,y;
        final float[] actualMatrix=new float[9];
        Geometry(View view){
            this.view=view;parent=view.getParent();visibility=view.getVisibility();shown=view.isShown();attached=view.isAttachedToWindow();layoutRequested=view.isLayoutRequested();
            width=view.getWidth();height=view.getHeight();scrollX=view.getScrollX();scrollY=view.getScrollY();
            children=view instanceof ViewGroup?((ViewGroup)view).getChildCount():-1;
            alpha=view.getAlpha();x=view.getTranslationX();y=view.getTranslationY();
            view.getMatrix().getValues(actualMatrix);
            clip=view.getClipBounds()==null?null:new Rect(view.getClipBounds());rect=physical(view);
        }
        boolean same(Geometry other){return other!=null&&view==other.view&&parent==other.parent&&visibility==other.visibility
                &&shown==other.shown&&attached==other.attached&&layoutRequested==other.layoutRequested&&width==other.width&&height==other.height&&scrollX==other.scrollX
                &&scrollY==other.scrollY&&children==other.children&&alpha==other.alpha&&x==other.x&&y==other.y
                &&java.util.Arrays.equals(actualMatrix,other.actualMatrix)
                &&java.util.Objects.equals(clip,other.clip)&&rect.equals(other.rect);}
    }
    final boolean allowed;
    final String reason;
    final String diagnosticScope;
    final String diagnosticCause;
    final Binding binding;
    final View root;
    final boolean wallpaperPriority;
    private final Rect safe;
    private final List<ProtectedArea> protectedAreas;
    private final List<Geometry> geometries;
    private final Method hitMethod;
    private NativeBusinessSnapshot(boolean allowed,String reason,Binding binding,Rect safe,List<ProtectedArea> areas,List<Geometry> geometry,Method method,boolean wallpaperPriority){
        this(allowed,reason,binding,safe,areas,geometry,method,wallpaperPriority,null,null);
    }
    private NativeBusinessSnapshot(boolean allowed,String reason,Binding binding,Rect safe,List<ProtectedArea> areas,List<Geometry> geometry,Method method,boolean wallpaperPriority,String scope,String cause){
        this.allowed=allowed;this.reason=reason;this.binding=binding;root=binding==null?null:binding.root;
        diagnosticScope=scope;diagnosticCause=cause;
        this.wallpaperPriority=wallpaperPriority;
        this.safe=safe==null?null:new Rect(safe);protectedAreas=List.copyOf(areas);geometries=List.copyOf(geometry);hitMethod=method;
    }
    private static NativeBusinessSnapshot denied(String reason,Binding binding){return new NativeBusinessSnapshot(false,reason,binding,null,List.of(),List.of(),null,false);}
    static NativeBusinessSnapshot read(Object injector,int barState,boolean expanding,boolean qsExpanded,boolean dozing){
        return read(injector,barState,expanding,qsExpanded,dozing,false);
    }
    /**
     * 原生入口始终使用当前业务绑定图。显式模式用于序列隔离，不把遗留根适配器
     * 的全根稳定性或通知集合搜索加作原生准入，也不绕过任何局部安全保护。
     */
    static NativeBusinessSnapshot read(Object injector,int barState,boolean expanding,boolean qsExpanded,boolean dozing,boolean wallpaperPriority){
        Binding binding=null;String stage="NATIVE_BINDING_UNKNOWN",scope=null;
        try{
            require(injector!=null&&INJECTOR.equals(injector.getClass().getName()),"NATIVE_BINDING_CLASS");
            ClassLoader loader=injector.getClass().getClassLoader();
            Object controller=call(injector,"getKeyguardPanelViewController");
            require(exact(controller,CONTROLLER,loader)&&field(controller,"keyguardPanelViewInjector")==injector,"NATIVE_BINDING_CONTROLLER");
            Object rootObject=field(injector,"keyguardRootView"),shadeObject=field(injector,"notificationShadeWindowView");
            require(rootObject instanceof ViewGroup&&shadeObject instanceof ViewGroup
                    &&exact(rootObject,"com.android.keyguard.widget.HyperOSKeyguardRootView",loader)
                    &&exact(shadeObject,"com.android.systemui.shade.NotificationShadeWindowView",loader)
                    &&field(controller,"keyguardRootView")==rootObject&&field(controller,"notificationShadeWindowView")==shadeObject,"NATIVE_BINDING_ROOT");
            View root=(View)rootObject,shade=(View)shadeObject;
            Object helper=field(injector,"doubleTapHelper");require(exact(helper,HELPER,loader),"NATIVE_BINDING_HELPER");
            Object listener=field(helper,"mDoubleTapListener");
            require(exact(listener,LISTENER,loader)&&field(helper,"mView")==root
                    &&field(listener,"$tmp0")==injector,"NATIVE_BINDING_HELPER");
            Object panel=call(injector,"getNotificationPanelViewController");
            Object stackController=field(panel,"mNotificationStackScrollLayoutController"),stackObject=field(stackController,"mView");
            require(exact(panel,PANEL,loader)&&field(panel,"mKeyguardPanelViewInjector")==injector
                    &&exact(stackController,STACK_CONTROLLER,loader)&&exact(stackObject,STACK,loader)&&stackObject instanceof ViewGroup
                    &&field(stackObject,"mController")==stackController
                    &&field(controller,"notificationStackScrollLayoutController")==stackController
                    &&field(controller,"notificationStackScrollLayout")==stackObject,"NATIVE_BINDING_NOTIFICATION");
            binding=new Binding(injector,controller,root,shade,helper,listener,panel,stackController,(ViewGroup)stackObject,wallpaperPriority?null:field(panel,"mKeyguardMediaController"));
            if(wallpaperPriority)return prioritySnapshot(binding);
            stage="NATIVE_STATE_UNKNOWN";
            require(barState==1&&bool(controller,"keyguardShowing"),"NATIVE_NOT_KEYGUARD");
            Object monitor=field(injector,"keyguardUpdateMonitor");
            require(field(controller,"keyguardUpdateMonitor")==monitor,"NATIVE_BINDING_CONTROLLER");
            require(bool(monitor,"mDeviceInteractive"),"NATIVE_NOT_INTERACTIVE");
            Object keyguard=field(panel,"mKeyguardStateController");
            require(field(controller,"keyguardStateController")==keyguard&&bool(keyguard,"mShowing"),"NATIVE_NOT_KEYGUARD");
            require(!bool(monitor,"mPrimaryBouncerIsOrWillBeShowing")&&!bool(controller,"keyguardBouncerShowing")
                    &&!bool(keyguard,"mPrimaryBouncerShowing")&&!booleanCall(field(panel,"mAlternateBouncerInteractor"),"isVisibleState"),"NATIVE_AUTHENTICATION");
            require(!booleanCall(monitor,"isSimPinSecure"),"NATIVE_SIM_AUTH");
            for(String name:new String[]{"mOccluded","mKeyguardGoingAway","mKeyguardFadingAway","mMiKeyguardFadingAway",
                    "mLaunchTransitionFadingAway","mFlingingToDismissKeyguard","mKeyguardNegativeScreen"})require(!bool(keyguard,name),"NATIVE_TRANSITION");
            require(!expanding&&!booleanCall(panel,"isExpandingOrCollapsing"),"NATIVE_TRANSITION");
            Object wake=field(controller,"wakeupAnimState");
            require(wake instanceof Enum<?> &&("IDLE".equals(((Enum<?>)wake).name())||"FINISHED".equals(((Enum<?>)wake).name())),"NATIVE_TRANSITION");
            for(String name:new String[]{"unlockAlphaAndScaleAnimator","unlockBlurAnimator","unlockSmartScaleAnimator","unlockSmartTransAnimator","bouncerFractionAnimator"}){
                Object animator=field(controller,name);require(animator==null||animator instanceof android.animation.Animator&&!((android.animation.Animator)animator).isStarted(),"NATIVE_TRANSITION");
            }
            Object manager=field(panel,"mStatusBarKeyguardViewManager");
            require(field(controller,"statusBarKeyguardViewManager")==manager,"NATIVE_BINDING_CONTROLLER");
            require(!dozing&&!bool(panel,"mDozing")&&!bool(manager,"mDozing"),"NATIVE_DOZING_AOD");
            require(!bool(manager,"mRemoteInputActive")&&!bool(manager,"mSecureLockDeviceBiometricAuthVisible"),"NATIVE_REMOTE_AUTH");
            Object editor=call(field(injector,"keyguardEditorHelper$delegate"),"getValue");
            Object editorState=field(editor,"mState");
            require(editorState instanceof Enum<?> &&"IDEL".equals(((Enum<?>)editorState).name())&&!booleanCall(injector,"isShowEditLoading"),"NATIVE_EDITOR");
            require(!qsExpanded&&!booleanCall(field(injector,"quickSettingsControllerImpl"),"getExpanded"),"NATIVE_QS");
            Object control=field(injector,"controlCenterImpl");
            require(exact(control,"com.miui.systemui.controlcenter.ControlCenterImpl",loader),"NATIVE_BINDING_CLASS");
            require(!flowBool(field(control,"expandedStateInternal")),"NATIVE_CONTROL_CENTER");
            Object move=field(injector,"keyguardMoveHelper");
            require(move!=null&&!bool(move,"mIsShortcutMoving")&&!bool(move,"mSwipingInProgress")
                    &&!booleanCall(move,"isInLeftView")&&!booleanCall(move,"isInMagazineAnimation")
                    &&field(move,"mSwipeFolme")==null&&field(move,"mResetFolme")==null,"NATIVE_SHORTCUT_MOVING");
            Object interaction=field(shade,"mInteractionEventHandler"),window=field(interaction,"this$0");
            require(field(window,"mView")==shade,"NATIVE_BINDING_ROOT");
            require(!flowBool(field(window,"mAodInterceptingTouches")),"NATIVE_DOZING_AOD");
            Object primary=field(window,"mPrimaryBouncerInteractor");
            require(!flowBool(field(primary,"isShowing"))&&!flowBool(field(primary,"isShowingSoon")),"NATIVE_AUTHENTICATION");
            for(String name:new String[]{"mHandlingExternalTouch","mExternalTouchIntercepted","mIsOcclusionTransitionRunning","mExpandAnimationRunning","mIsTrackingBarGesture"})require(!bool(window,name),"NATIVE_TRANSITION");
            Object central=field(panel,"mCentralSurfaces");
            require(field(central,"mKeyguardStateController")==keyguard&&field(central,"mStatusBarKeyguardViewManager")==manager
                    &&field(central,"mKeyguardUpdateMonitor")==monitor,"NATIVE_BINDING_CONTROLLER");
            require(!bool(central,"mBouncerShowing"),"NATIVE_AUTHENTICATION");
            require(!bool(central,"mDozing"),"NATIVE_DOZING_AOD");
            require(!bool(central,"mLaunchEmergencyActionOnFinishedGoingToSleep")&&!bool(central,"mLaunchEmergencyActionWhenFinishedWaking"),"NATIVE_TRANSITION");
            stage="NATIVE_REGION_UNKNOWN";
            require(root.isAttachedToWindow()&&shade.isAttachedToWindow()&&root.isShown()&&shade.isShown()
                    &&root.getWindowVisibility()==View.VISIBLE&&shade.getWindowVisibility()==View.VISIBLE
                    &&shade.getDisplay()!=null&&shade.getDisplay().getDisplayId()==0&&shade.getDisplay().getRotation()==Surface.ROTATION_0,"NATIVE_PRIMARY_WINDOW");
            PowerManager power=shade.getContext().getSystemService(PowerManager.class);
            require(power!=null&&power.isInteractive(),"NATIVE_NOT_INTERACTIVE");
            ArrayList<Geometry> geometry=new ArrayList<>();
            Rect area=new Rect(),windowRect=new Rect();
            require(shade.getGlobalVisibleRect(windowRect)&&!windowRect.isEmpty(),"NATIVE_ROOT_GEOMETRY");
            scope="NATIVE_LAYOUT_ROOT";captureAncestors(root,shade,geometry,false);
            scope="NATIVE_LAYOUT_STACK";captureAncestors(binding.stack,shade,geometry,true);
            require(root.getGlobalVisibleRect(area)&&!area.isEmpty()&&area.intersect(windowRect),"NATIVE_ROOT_GEOMETRY");
            WindowInsets insets=shade.getRootWindowInsets();require(insets!=null,"NATIVE_INSETS_UNKNOWN");
            var inset=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.systemGestures()|WindowInsets.Type.tappableElement());
            int edge=Math.max(1,(int)(48*shade.getResources().getDisplayMetrics().density));
            require(area.intersect(windowRect.left+Math.max(edge,inset.left),windowRect.top+Math.max(edge,inset.top),
                    windowRect.right-Math.max(edge,inset.right),windowRect.bottom-Math.max(edge,inset.bottom)),"NATIVE_SAFE_REGION_EMPTY");
            Point displaySize=new Point();shade.getDisplay().getRealSize(displaySize);
            require(displaySize.x>0&&displaySize.y>0&&ForceLocalRegion.constrain(area,displaySize.y),"NATIVE_SAFE_REGION_EMPTY");
            ArrayList<ProtectedArea> areas=new ArrayList<>();
            scope="NATIVE_LAYOUT_NOTIFICATIONS";
            notificationAreas(binding,geometry,areas,loader);
            scope="NATIVE_LAYOUT_MEDIA";
            mediaAreas(binding,geometry,areas,loader);
            scope="NATIVE_LAYOUT_SHORTCUTS";
            shortcutAreas(binding,geometry,areas);
            ArrayList<Rect> fingerprint=new ArrayList<>();
            require(LockSurfaceSnapshot.nativeFingerprintRegions(monitor,fingerprint,windowRect),"NATIVE_FINGERPRINT_UNKNOWN");
            for(Rect rect:fingerprint)areas.add(new ProtectedArea(rect,"NATIVE_FINGERPRINT_REGION"));
            Method hit=method(binding.stack,"getChildAtPosition",float.class,float.class,boolean.class,boolean.class,boolean.class);
            return new NativeBusinessSnapshot(true,"NATIVE_ALLOWED",binding,area,areas,geometry,hit,false);
        }catch(Rejected rejected){return new NativeBusinessSnapshot(false,rejected.reason,binding,null,List.of(),List.of(),null,false,scope,rejected.detail);}
        catch(Throwable ignored){return new NativeBusinessSnapshot(false,stage,binding,null,List.of(),List.of(),null,false,scope,"NATIVE_LAYOUT_PROPERTY_UNKNOWN");}
    }
    boolean contains(float screenX,float screenY){return point(screenX,screenY).allowed;}
    /** 用户明确选择最高优先级：只确认当前实际锁屏窗口，不把控件/认证布局状态作为识别条件。 */
    private static NativeBusinessSnapshot prioritySnapshot(Binding binding){
        try{
            Object keyguard=field(binding.notificationController,"mKeyguardStateController");
            require(bool(keyguard,"mShowing")&&field(binding.controller,"keyguardStateController")==keyguard,"NATIVE_NOT_KEYGUARD");
            View window=binding.shadeRoot;
            require(window.isAttachedToWindow()&&window.isShown()&&window.getWindowVisibility()==View.VISIBLE&&binding.root.isAttachedToWindow()
                    &&window.getDisplay()!=null&&window.getDisplay().getDisplayId()==0,"NATIVE_PRIMARY_WINDOW");
            Rect area=new Rect();require(window.getGlobalVisibleRect(area)&&!area.isEmpty(),"NATIVE_ROOT_GEOMETRY");
            return new NativeBusinessSnapshot(true,"NATIVE_ALLOWED",binding,area,List.of(),List.of(),null,true);
        }catch(Rejected rejected){return denied(rejected.reason,binding);}
        catch(Throwable ignored){return denied("NATIVE_STATE_UNKNOWN",binding);}
    }
    PointResult point(float screenX,float screenY){
        if(!allowed)return new PointResult(false,reason);
        if(!Float.isFinite(screenX)||!Float.isFinite(screenY))return new PointResult(false,"NATIVE_POINT_NONFINITE");
        try{
            if(!stillBound())return new PointResult(false,"NATIVE_BINDING_CHANGED");
            for(Geometry geometry:geometries)if(!geometry.same(new Geometry(geometry.view))
                    ||(geometry.shown&&!transformKnown(geometry.view)))return new PointResult(false,"NATIVE_LOCAL_LAYOUT_CHANGED");
            if(screenX<safe.left||screenY<safe.top||screenX>=safe.right||screenY>=safe.bottom)return new PointResult(false,"NATIVE_EDGE_TOP_REGION");
            for(ProtectedArea protectedArea:protectedAreas)if(screenX>=protectedArea.area.left&&screenY>=protectedArea.area.top
                    &&screenX<protectedArea.area.right&&screenY<protectedArea.area.bottom)return new PointResult(false,protectedArea.reason);
            if(wallpaperPriority)return new PointResult(true,"NATIVE_ALLOWED");
            int[] xy=new int[2];binding.stack.getLocationOnScreen(xy);
            Object hit=hitMethod.invoke(binding.stack,screenX-xy[0],screenY-xy[1],true,false,true);
            if(hit!=null)return new PointResult(false,hit instanceof View?"NATIVE_HIT_TEST_REGION":"NATIVE_HIT_TEST_UNKNOWN");
            return new PointResult(true,"NATIVE_ALLOWED");
        }catch(Throwable ignored){return new PointResult(false,"NATIVE_HIT_TEST_UNKNOWN");}
    }
    boolean sameLayout(NativeBusinessSnapshot other){
        if(other==null||!allowed||!other.allowed||wallpaperPriority!=other.wallpaperPriority||!binding.same(other.binding)||!safe.equals(other.safe)
                ||protectedAreas.size()!=other.protectedAreas.size()||geometries.size()!=other.geometries.size())return false;
        for(int i=0;i<protectedAreas.size();i++)if(!protectedAreas.get(i).same(other.protectedAreas.get(i)))return false;
        for(int i=0;i<geometries.size();i++)if(!geometries.get(i).same(other.geometries.get(i)))return false;
        return true;
    }
    private boolean stillBound()throws Exception{
        return call(binding.injector,"getKeyguardPanelViewController")==binding.controller
                &&field(binding.controller,"keyguardPanelViewInjector")==binding.injector
                &&field(binding.injector,"keyguardRootView")==binding.root&&field(binding.controller,"keyguardRootView")==binding.root
                &&field(binding.injector,"notificationShadeWindowView")==binding.shadeRoot&&field(binding.controller,"notificationShadeWindowView")==binding.shadeRoot
                &&field(binding.injector,"doubleTapHelper")==binding.helper
                &&field(binding.helper,"mView")==binding.root&&field(binding.helper,"mDoubleTapListener")==binding.listener
                &&field(binding.listener,"$tmp0")==binding.injector&&call(binding.injector,"getNotificationPanelViewController")==binding.notificationController
                &&field(binding.notificationController,"mKeyguardPanelViewInjector")==binding.injector
                &&field(binding.notificationController,"mNotificationStackScrollLayoutController")==binding.stackController
                &&field(binding.controller,"notificationStackScrollLayoutController")==binding.stackController
                &&field(binding.controller,"notificationStackScrollLayout")==binding.stack
                &&field(binding.stackController,"mView")==binding.stack&&field(binding.stack,"mController")==binding.stackController
                &&(wallpaperPriority||field(binding.notificationController,"mKeyguardMediaController")==binding.mediaController);
    }
    private static void notificationAreas(Binding binding,List<Geometry> geometry,List<ProtectedArea> areas,ClassLoader loader)throws Exception{
        ViewGroup stack=binding.stack;
        require(!bool(stack,"mAnimationRunning")&&!bool(stack,"mNeedsAnimation")&&!bool(stack,"mIsExpansionChanging")
                &&!bool(stack,"mChildrenUpdateRequested")&&field(binding.stackController,"mLongPressedView")==null,"NATIVE_NOTIFICATION_TRANSITION");
        Object ambient=field(stack,"mAmbientState");
        for(String name:new String[]{"mIsSwipingUp","mIsFlinging","mIsClosing","mDozing"})require(!bool(ambient,name),"NATIVE_NOTIFICATION_TRANSITION");
        for(String name:new String[]{"mChildrenToAddAnimated","mChildrenToRemoveAnimated","mSwipedOutViews","mChildrenChangingPositions","mAnimationEvents"}){
            Object queue=field(stack,name);require(queue instanceof Collection<?> &&((Collection<?>)queue).isEmpty(),"NATIVE_NOTIFICATION_TRANSITION");
        }
        require(stack.getChildCount()<=128,"NATIVE_NOTIFICATION_LAYOUT");
        Class<?> expandable=Class.forName(EXPANDABLE,false,loader);
        Class<?> row=Class.forName("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow",false,loader);
        boolean notificationAccounted=false;
        for(int i=0;i<stack.getChildCount();i++){
            View child=stack.getChildAt(i);require(child!=null&&expandable.isInstance(child),"NATIVE_NOTIFICATION_CHILD_UNKNOWN");
            captureLocal(child,binding.shadeRoot,geometry,areas,"NATIVE_NOTIFICATION_REGION");
            if(row.isInstance(child)&&child.isShown()&&child.getAlpha()>0)notificationAccounted=true;
        }
        require(!bool(binding.controller,"hasNotification")||notificationAccounted,"NATIVE_NOTIFICATION_UNACCOUNTED");
    }
    private static void mediaAreas(Binding binding,List<Geometry> geometry,List<ProtectedArea> areas,ClassLoader loader)throws Exception{
        Object media=field(binding.notificationController,"mKeyguardMediaController");
        require(media==binding.mediaController,"NATIVE_MEDIA_BINDING");
        require(exact(media,"com.android.systemui.media.controls.ui.controller.KeyguardMediaController",loader),"NATIVE_MEDIA_BINDING");
        Object single=field(media,"singlePaneContainer"),split=field(media,"splitShadeContainer");
        boolean visible=flowBool(field(media,"visible$delegate"));boolean accounted=false;
        for(Object value:new Object[]{single,split}){
            if(value==null)continue;require(value instanceof ViewGroup,"NATIVE_MEDIA_BINDING");
            View view=(View)value;
            require(view.isAttachedToWindow()&&belongsTo(binding.shadeRoot,view),"NATIVE_MEDIA_BINDING");
            if(view.isShown()&&view.getVisibility()==View.VISIBLE&&emptyMediaPlaceholder(view)){
                require(view.isAttachedToWindow()&&view.isLaidOut()&&!view.isLayoutRequested()
                        &&transformKnown(view)&&noIndependentDrawQueues(view),"NATIVE_PROTECTED_LAYOUT");
                require(view.getParent() instanceof View,"NATIVE_PROTECTED_LAYOUT");
                captureAncestors((View)view.getParent(),binding.shadeRoot,geometry,false);
                geometry.add(new Geometry(view));continue;
            }
            captureLocal(view,binding.shadeRoot,geometry,areas,"NATIVE_MEDIA_REGION");
            if(view.isShown()&&view.getAlpha()>0)accounted=true;
        }
        require(!visible||accounted,"NATIVE_MEDIA_UNACCOUNTED");
    }
    /** OEM保留的零尺寸空容器不能代表活跃媒体；保留其几何以撤销同序列内的内容变化。 */
    static boolean emptyMediaPlaceholder(View view)throws Exception{
        if(!(view instanceof ViewGroup)||((ViewGroup)view).getChildCount()!=0
                ||(view.getWidth()!=0&&view.getHeight()!=0)||view.getBackground()!=null||view.getForeground()!=null
                ||view.isClickable()||view.isLongClickable()||view.hasOnClickListeners()||view.getTouchDelegate()!=null)return false;
        Object listeners=field(view,"mListenerInfo");return listeners==null||field(listeners,"mOnTouchListener")==null;
    }
    private static boolean belongsTo(View root,View view){for(int depth=0;depth<32;depth++){if(view==root)return true;if(!(view.getParent() instanceof View))return false;view=(View)view.getParent();}return false;}
    private static void shortcutAreas(Binding binding,List<Geometry> geometry,List<ProtectedArea> areas)throws Exception{
        Object bottom=call(field(binding.injector,"keyguardBottomAreaInjector$delegate"),"getValue");
        require(bottom!=null&&field(bottom,"mKeyguardPanelViewInjector")==binding.injector,"NATIVE_SHORTCUT_BINDING");
        Object whole=field(bottom,"mKeyguardBottomAreaView"),custom=field(bottom,"mCustomLockscreenButton"),indication=field(bottom,"mIndicationArea");
        ClassLoader loader=binding.injector.getClass().getClassLoader();
        require(whole instanceof ViewGroup&&exact(whole,"com.android.systemui.statusbar.phone.KeyguardBottomAreaView",loader)
                &&indication instanceof View,"NATIVE_SHORTCUT_BINDING");
        ViewGroup parent=(ViewGroup)whole;
        Object shortcuts=field(bottom,"miuiShortcutController");
        require(exact(shortcuts,"com.android.keyguard.shortcut.MiuiShortcutController",loader)
                &&call(shortcuts,"getKeyguardPanelViewInjector")==binding.injector,"NATIVE_SHORTCUT_BINDING");
        Object container=field(shortcuts,"shortcutContainerView");
        require(container instanceof android.widget.FrameLayout&&((View)container).getParent()==parent
                &&((View)indication).getParent()==parent&&(custom==null||custom instanceof View&&((View)custom).getParent()==parent)
                &&parent.getChildCount()<=32,"NATIVE_SHORTCUT_BINDING");
        // OEM整个bottom父是fill_parent，不能把这个纯组织父再次扣成全屏。
        captureAncestors(parent,binding.shadeRoot,geometry,false);
        int overlayId=parent.getResources().getIdentifier("overlay_container","id","com.android.systemui");
        for(int i=0;i<parent.getChildCount();i++){
            View child=parent.getChildAt(i);require(child!=null,"NATIVE_SHORTCUT_BINDING");
            if(child==container&&child instanceof ViewGroup&&((ViewGroup)child).getChildCount()==0
                    &&(child.getWidth()==0||child.getHeight()==0)){
                require(transformKnown(child)&&child.isAttachedToWindow()&&child.isLaidOut()&&!child.isLayoutRequested()
                        &&noIndependentDrawQueues(child),"NATIVE_PROTECTED_LAYOUT");
                geometry.add(new Geometry(child));continue;
            }
            // 已核XML的overlay_container是无子项、无触摸处理的FrameLayout；出现内容就按局部前景保护。
            if(overlayId!=0&&child.getId()==overlayId&&child.getClass()==android.widget.FrameLayout.class
                    &&((ViewGroup)child).getChildCount()==0&&child.getBackground()==null&&child.getForeground()==null
                    &&!child.isClickable()&&!child.isLongClickable()&&!child.hasOnClickListeners()){
                Object listeners=field(child,"mListenerInfo");
                require(listeners==null||field(listeners,"mOnTouchListener")==null,"NATIVE_PROTECTED_LAYOUT");
                captureAncestors(child,binding.shadeRoot,geometry,false);continue;
            }
            captureLocal(child,binding.shadeRoot,geometry,areas,"NATIVE_SHORTCUT_REGION");
        }
    }
    private static void captureLocal(View view,View shade,List<Geometry> geometry,List<ProtectedArea> areas,String why)throws Exception{
        // 隐藏对象也采样身份/可见性，防止同序列内突然出现后仍使用旧空白。
        if(!view.isShown()||view.getVisibility()!=View.VISIBLE){geometry.add(new Geometry(view));return;}
        captureAncestors(view,shade,geometry,true);
        Rect rect=physical(view);require(!rect.isEmpty(),"NATIVE_PROTECTED_LAYOUT");
        Rect visible=new Rect();if(view.getGlobalVisibleRect(visible)&&!visible.isEmpty())rect.union(visible);
        areas.add(new ProtectedArea(rect,why));
    }
    private static void captureAncestors(View leaf,View shade,List<Geometry> geometry,boolean local)throws Exception{
        View cursor=leaf;
        for(int depth=0;depth<32;depth++){
            requireLayout(transformKnown(cursor),"NATIVE_LAYOUT_TRANSFORM");
            requireLayout(cursor.isAttachedToWindow(),"NATIVE_LAYOUT_DETACHED");
            requireLayout(cursor.isShown()&&cursor.getWindowVisibility()==View.VISIBLE,"NATIVE_LAYOUT_HIDDEN");
            requireLayout(cursor.isLaidOut()&&cursor.getWidth()>0&&cursor.getHeight()>0,"NATIVE_LAYOUT_EMPTY");
            if(depth==0&&local)require(!cursor.isLayoutRequested(),"NATIVE_NOTIFICATION_LAYOUT");
            require(noIndependentDrawQueues(cursor),"NATIVE_PROTECTED_LAYOUT");
            boolean known=false;for(Geometry previous:geometry)if(previous.view==cursor){known=true;break;}
            if(!known)geometry.add(new Geometry(cursor));
            if(cursor==shade)return;
            require(cursor.getParent() instanceof View,"NATIVE_PROTECTED_LAYOUT");cursor=(View)cursor.getParent();
        }
        throw new Rejected("NATIVE_PROTECTED_LAYOUT");
    }
    private static boolean transformKnown(View view){
        if(!(view.getAnimation()==null&&view.getTouchDelegate()==null&&view.getAnimationMatrix()==null
                &&Float.isFinite(view.getAlpha())&&view.getAlpha()>0&&Float.isFinite(view.getTranslationX())&&Float.isFinite(view.getTranslationY())
                &&view.getScaleX()==1f&&view.getScaleY()==1f&&view.getRotation()==0f&&view.getRotationX()==0f&&view.getRotationY()==0f
                &&view.getTranslationZ()==0f&&view.getScrollX()==0&&view.getScrollY()==0))return false;
        // PropertiesFrameLayout会先返回requested值；实际RenderNode矩阵必须独立证明只含平移。
        Matrix actual=view.getMatrix();if(actual==null)return false;float[] values=new float[9];actual.getValues(values);
        for(float value:values)if(!Float.isFinite(value))return false;
        if(values[Matrix.MSCALE_X]!=1f||values[Matrix.MSCALE_Y]!=1f||values[Matrix.MSKEW_X]!=0f||values[Matrix.MSKEW_Y]!=0f
                ||values[Matrix.MPERSP_0]!=0f||values[Matrix.MPERSP_1]!=0f||values[Matrix.MPERSP_2]!=1f)return false;
        for(Class<?> type=view.getClass();type!=null;type=type.getSuperclass()){
            if(!"com.miui.systemui.widget.PropertiesFrameLayout".equals(type.getName()))continue;
            if(type.getClassLoader()!=view.getClass().getClassLoader())return false;
            try{
                for(String name:new String[]{"getSuperScaleX","getSuperScaleY","getSuperRotationX","getSuperRotationY","getSuperRotationZ","getSuperTranslationZ"}){
                    Method getter=type.getDeclaredMethod(name);
                    if(getter.getReturnType()!=float.class||!java.lang.reflect.Modifier.isFinal(getter.getModifiers()))return false;
                    getter.setAccessible(true);Object result=getter.invoke(view);if(!(result instanceof Number))return false;
                    float value=((Number)result).floatValue();if(!Float.isFinite(value)||value!=(name.startsWith("getSuperScale")?1f:0f))return false;
                }
            }catch(Throwable ignored){return false;}
            break;
        }
        return true;
    }
    private static boolean noIndependentDrawQueues(View view)throws Exception{
        if(field(view,"mOverlay")!=null)return false;
        if(!(view instanceof ViewGroup))return true;
        ViewGroup group=(ViewGroup)view;if(group.getLayoutTransition()!=null||group.isLayoutSuppressed())return false;
        for(String name:new String[]{"mTransientViews","mDisappearingChildren","mTransitioningViews","mVisibilityChangingChildren"}){
            Object queue=field(group,name);if(queue!=null&&(!(queue instanceof Collection<?>)||!((Collection<?>)queue).isEmpty()))return false;
        }
        return true;
    }
    private static Rect physical(View view){
        int[] xy=new int[2];view.getLocationOnScreen(xy);
        // 祖先均已核为纯平移。getLocationOnScreen返回整数，向外1px覆盖实际亚像素边缘。
        long left=(long)xy[0]-1,top=(long)xy[1]-1,right=(long)xy[0]+view.getWidth()+1,bottom=(long)xy[1]+view.getHeight()+1;
        if(left<Integer.MIN_VALUE||top<Integer.MIN_VALUE||right>Integer.MAX_VALUE||bottom>Integer.MAX_VALUE)throw new IllegalStateException();
        return new Rect((int)left,(int)top,(int)right,(int)bottom);
    }
    private static boolean exact(Object value,String name,ClassLoader loader){return value!=null&&name.equals(value.getClass().getName())&&value.getClass().getClassLoader()==loader;}
    private static Object field(Object value,String name)throws Exception{if(value==null)throw new NoSuchFieldException();return LockInputBroker.field(value.getClass(),name).get(value);}
    private static boolean bool(Object value,String name)throws Exception{Object result=field(value,name);if(!(result instanceof Boolean))throw new IllegalStateException();return (Boolean)result;}
    private static boolean flowBool(Object flow)throws Exception{Object result=call(flow,"getValue");if(!(result instanceof Boolean))throw new IllegalStateException();return (Boolean)result;}
    private static boolean booleanCall(Object value,String name)throws Exception{Object result=call(value,name);if(!(result instanceof Boolean))throw new IllegalStateException();return (Boolean)result;}
    private static Object call(Object value,String name)throws Exception{return method(value,name).invoke(value);}
    private static Method method(Object value,String name,Class<?>... args)throws Exception{
        if(value==null)throw new NoSuchMethodException();
        for(Class<?> type=value.getClass();type!=null;type=type.getSuperclass())try{Method method=type.getDeclaredMethod(name,args);method.setAccessible(true);return method;}catch(NoSuchMethodException ignored){ }
        throw new NoSuchMethodException();
    }
    private static void require(boolean condition,String reason)throws Rejected{if(!condition)throw new Rejected(reason);}
    private static void requireLayout(boolean condition,String detail)throws Rejected{if(!condition)throw new Rejected("NATIVE_PROTECTED_LAYOUT",detail);}
    private static final class Rejected extends Exception {final String reason,detail;Rejected(String reason){this(reason,null);}Rejected(String reason,String detail){this.reason=reason;this.detail=detail;}}
}
