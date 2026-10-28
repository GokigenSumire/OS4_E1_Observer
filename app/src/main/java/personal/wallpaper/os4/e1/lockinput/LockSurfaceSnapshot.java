package personal.wallpaper.os4.e1.lockinput;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Point;
import android.os.PowerManager;
import android.view.View;
import android.view.Surface;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** 同一个自然root回调内的正向背景区域。只读已核属性，不调用OEM触摸/认证动作。 */
final class LockSurfaceSnapshot {
    final boolean allowed;
    final String reason;
    final Rect background;
    final List<Rect> exclusions;
    final int layoutKey;
    final int layoutDetailCode;
    final boolean forceLocal;
    final boolean wallpaperPriority;
    private final int[] roiDetailCodes;
    private final BackgroundRegion region;
    private LockSurfaceSnapshot(boolean allowed,String reason,Rect background,List<Rect> exclusions,int key){
        this(allowed,reason,background,exclusions,key,0);
    }
    private LockSurfaceSnapshot(boolean allowed,String reason,Rect background,List<Rect> exclusions,int key,int detail){
        this(allowed,reason,background,exclusions,key,detail,false);
    }
    private LockSurfaceSnapshot(boolean allowed,String reason,Rect background,List<Rect> exclusions,int key,int detail,boolean forceLocal){
        this(allowed,reason,background,exclusions,key,detail,forceLocal,new int[0]);
    }
    private LockSurfaceSnapshot(boolean allowed,String reason,Rect background,List<Rect> exclusions,int key,int detail,boolean forceLocal,int[] roiDetails){
        this(allowed,reason,background,exclusions,key,detail,forceLocal,roiDetails,false);
    }
    private LockSurfaceSnapshot(boolean allowed,String reason,Rect background,List<Rect> exclusions,int key,int detail,boolean forceLocal,int[] roiDetails,boolean wallpaperPriority){
        this.allowed=allowed;this.reason=reason;this.background=background;this.exclusions=exclusions;this.layoutKey=key;
        this.layoutDetailCode=detail;
        this.forceLocal=forceLocal;
        this.wallpaperPriority=wallpaperPriority;
        this.roiDetailCodes=roiDetails.clone();
        ArrayList<BackgroundRegion.Box> holes=new ArrayList<>();for(Rect rect:exclusions)holes.add(new BackgroundRegion.Box(rect.left,rect.top,rect.right,rect.bottom));
        region=background==null?null:new BackgroundRegion(new BackgroundRegion.Box(background.left,background.top,background.right,background.bottom),holes);
    }
    static LockSurfaceSnapshot denied(String reason){return new LockSurfaceSnapshot(false,reason,null,List.of(),0);}
    int[] roiDetails(){return roiDetailCodes.clone();}
    private static LockSurfaceSnapshot denied(String reason,LayoutCapture capture){return new LockSurfaceSnapshot(false,reason,null,List.of(),0,capture==null?0:capture.code);}
    boolean contains(float x,float y){
        return allowed&&region!=null&&region.contains(x,y);
    }
    boolean sameLayout(LockSurfaceSnapshot other){return other!=null&&allowed&&other.allowed&&forceLocal==other.forceLocal&&wallpaperPriority==other.wallpaperPriority&&background.equals(other.background)&&exclusions.equals(other.exclusions);}
    static LockSurfaceSnapshot read(View root){return read(root,false);}
    static LockSurfaceSnapshot read(View root,boolean captureLayoutDetails){
        return read(root,captureLayoutDetails,false);
    }
    static LockSurfaceSnapshot read(View root,boolean captureLayoutDetails,boolean forceLocal){
        return read(root,captureLayoutDetails,forceLocal,false);
    }
    static LockSurfaceSnapshot read(View root,boolean captureLayoutDetails,boolean forceLocal,boolean wallpaperPriority){
        try {
            if(root==null||!root.isAttachedToWindow()||root.getWindowVisibility()!=View.VISIBLE||!root.isShown()
                    ||root.getDisplay()==null||root.getDisplay().getDisplayId()!=0)return denied("ROOT_NOT_ATTACHED_PRIMARY");
            if(root.getDisplay().getRotation()!=Surface.ROTATION_0)return denied("ROTATION_NOT_PINNED");
            PowerManager power=root.getContext().getSystemService(PowerManager.class);
            if(power==null||!power.isInteractive())return denied("NOT_INTERACTIVE");
            Object interaction=get(root,"mInteractionEventHandler"),controller=get(interaction,"this$0");
            if(get(controller,"mView")!=root)return denied("ROOT_CONTROLLER_MISMATCH");
            Object central=get(controller,"mService"),keyguard=get(central,"mKeyguardStateController");
            // 所有认证/安全布尔先读，任何未知或活动状态都在读取MotionEvent坐标前拒绝。
            if(!bool(keyguard,"mShowing"))return denied("KEYGUARD_NOT_SHOWING");
            if(bool(keyguard,"mPrimaryBouncerShowing")||bool(central,"mBouncerShowing"))return denied("BOUNCER_SHOWING");
            Object primary=get(controller,"mPrimaryBouncerInteractor");
            if(flowBool(get(primary,"isShowing"),"PRIMARY_SHOWING")||flowBool(get(primary,"isShowingSoon"),"PRIMARY_SOON"))return denied("BOUNCER_SHOWING_OR_SOON");
            Object alternate=get(controller,"mAlternateBouncerInteractor");
            if((Boolean)method(alternate,"isVisibleState").invoke(alternate))return denied("ALTERNATE_BOUNCER_VISIBLE");
            if(bool(keyguard,"mOccluded")||bool(keyguard,"mKeyguardGoingAway")||bool(keyguard,"mKeyguardFadingAway")
                    ||bool(keyguard,"mMiKeyguardFadingAway")||bool(keyguard,"mLaunchTransitionFadingAway")
                    ||bool(keyguard,"mFlingingToDismissKeyguard")||bool(keyguard,"mKeyguardNegativeScreen"))return denied("KEYGUARD_TRANSITION_OR_OCCLUDED");
            Object manager=get(central,"mStatusBarKeyguardViewManager");
            if(bool(central,"mDozing")||bool(manager,"mDozing")||flowBool(get(controller,"mAodInterceptingTouches"),"AOD"))return denied("DOZING_OR_AOD");
            if(bool(manager,"mRemoteInputActive")||bool(manager,"mSecureLockDeviceBiometricAuthVisible"))return denied("REMOTE_INPUT_OR_AUTH_VISIBLE");
            if(bool(central,"mLaunchEmergencyActionOnFinishedGoingToSleep")||bool(central,"mLaunchEmergencyActionWhenFinishedWaking"))return denied("EMERGENCY_TRANSITION");
            Object monitor=get(central,"mKeyguardUpdateMonitor");
            if((Boolean)method(monitor,"isSimPinSecure").invoke(monitor))return denied("SIM_AUTH_REQUIRED");
            Object controlCenter=get(controller,"mControlCenter");
            if(controlCenter==null||!"com.miui.systemui.controlcenter.ControlCenterImpl".equals(controlCenter.getClass().getName())
                    ||controlCenter.getClass().getClassLoader()!=root.getClass().getClassLoader())return denied("CONTROL_CENTER_LOADER_NOT_VERIFIED");
            if(flowBool(get(controlCenter,"expandedStateInternal"),"CONTROL_CENTER"))return denied("CONTROL_CENTER_EXPANDED");
            if(bool(controller,"mHandlingExternalTouch")||bool(controller,"mExternalTouchIntercepted")
                    ||bool(controller,"mIsOcclusionTransitionRunning")||bool(controller,"mExpandAnimationRunning")
                    ||bool(controller,"mIsTrackingBarGesture"))return denied("EXTERNAL_OR_PANEL_TRANSITION");
            View positive=find(root,"keyguard_root_view");
            if(positive==null||!"com.android.keyguard.widget.HyperOSKeyguardRootView".equals(positive.getClass().getName())
                    ||!positive.isShown()||!positive.isAttachedToWindow())return denied("POSITIVE_BACKGROUND_NOT_PRESENT");
            Rect area=new Rect();if(!positive.getGlobalVisibleRect(area)||area.width()<1||area.height()<1)return denied("BACKGROUND_EMPTY");
            WindowInsets insets=root.getRootWindowInsets();if(insets==null)return denied("INSETS_UNKNOWN");
            var safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.systemGestures()|WindowInsets.Type.tappableElement());
            int edge=Math.max(1,(int)(48*root.getResources().getDisplayMetrics().density));
            Rect window=new Rect();if(!root.getGlobalVisibleRect(window))return denied("WINDOW_RECT_UNKNOWN");
            if(!area.intersect(window.left+Math.max(edge,safe.left),window.top+Math.max(edge,safe.top),
                    window.right-Math.max(edge,safe.right),window.bottom-Math.max(edge,safe.bottom)))return denied("SAFE_BACKGROUND_EMPTY");
            if(area.isEmpty())return denied("SAFE_BACKGROUND_EMPTY");
            if(forceLocal){
                Point displaySize=new Point();root.getDisplay().getRealSize(displaySize);
                if(displaySize.x<=0||displaySize.y<=0||!ForceLocalRegion.constrain(area,displaySize.y))return denied("SAFE_BACKGROUND_EMPTY");
            }
            ArrayList<Rect> excluded=RoiExclusionTrace.create(captureLayoutDetails,root,area,forceLocal,node->layoutNode(root,node));
            int[] budget={0};
            LayoutCapture capture=captureLayoutDetails?new LayoutCapture():null;
            if(wallpaperPriority){
                if(!forceLocal)return priorityDenied("PRIORITY_MODE_INCONSISTENT",captureLayoutDetails);
                PriorityNotificationRegions.Result notifications=PriorityNotificationRegions.read(root);
                if(!notifications.allowed)return priorityDenied(notifications.reason,captureLayoutDetails);
                for(Rect protectedArea:notifications.areas())excluded.add(protectedArea);
            }else{
            if(!foreground(root,positive,positive,excluded,budget,capture,positive,area,forceLocal))return denied("BACKGROUND_CHILD_LAYOUT_UNKNOWN",capture);
            // 已核布局中的跨背景前景：不以透明大容器本身的全屏矩形冒充空白区域。
            // 所有自然根的其他child都必须被扣除；只有同一已核APK中明示的纯背景层可跳过。
            if(!(root instanceof ViewGroup)||((ViewGroup)root).getChildCount()>128)return denied("ROOT_CHILDREN_UNKNOWN");
            ViewGroup children=(ViewGroup)root;
            for(int i=0;i<children.getChildCount();i++){
                View child=children.getChildAt(i);if(child==positive||pureBackgroundLayer(root,child)||IdleDecoration.mayIgnore(root,child))continue;
                if(!foreground(root,child,positive,excluded,budget,capture,child,area,forceLocal))return denied("FOREGROUND_LAYOUT_UNKNOWN",capture);
            }
            }
            if(!fingerprintRegions(monitor,excluded,window))return denied("FINGERPRINT_REGION_UNKNOWN");
            int key=area.hashCode();for(Rect rect:excluded)key=31*key+rect.hashCode();
            LockSurfaceSnapshot result=new LockSurfaceSnapshot(true,"ALLOW_BACKGROUND",area,List.copyOf(excluded),key,0,forceLocal,captureLayoutDetails&&wallpaperPriority?new int[]{RoiDiagnosticDetail.MODE_PRIORITY}:new int[0],wallpaperPriority);
            if(result.region.hasSpace())return result;
            if(wallpaperPriority)return priorityDenied("PRIORITY_NOTIFICATION_AREA_COVERED",captureLayoutDetails);
            int[] roiDetails=captureLayoutDetails?RoiExclusionTrace.emptyDetails(excluded,forceLocal):new int[0];
            return new LockSurfaceSnapshot(false,"POSITIVE_ROI_EMPTY_AFTER_EXCLUSIONS",null,List.of(),0,0,forceLocal,roiDetails);
        }catch(MissingProperty missing){return denied("UNKNOWN_"+missing.key);}
        catch(Throwable ignored){return denied("REQUIRED_PROPERTY_UNKNOWN");}
    }
    private static LockSurfaceSnapshot priorityDenied(String reason,boolean capture){
        return new LockSurfaceSnapshot(false,reason,null,List.of(),0,0,true,capture?new int[]{RoiDiagnosticDetail.MODE_PRIORITY}:new int[0],true);
    }
    private static boolean foreground(View root,View view,View positive,List<Rect> excluded,int[] budget){
        return foreground(root,view,positive,excluded,budget,null,view);
    }
    private static boolean foreground(View root,View view,View positive,List<Rect> excluded,int[] budget,LayoutCapture capture,View top){
        return foreground(root,view,positive,excluded,budget,capture,top,null);
    }
    private static boolean foreground(View root,View view,View positive,List<Rect> excluded,int[] budget,LayoutCapture capture,View top,Rect safeRoi){
        return foreground(root,view,positive,excluded,budget,capture,top,safeRoi,false);
    }
    private static boolean foreground(View root,View view,View positive,List<Rect> excluded,int[] budget,LayoutCapture capture,View top,Rect safeRoi,boolean forceLocal){
        if(++budget[0]>512){capture(capture,LayoutDiagnosticDetail.NODE_LIMIT,root,top,view);return false;}
        if(!view.isShown()||view.getAlpha()<=0)return true;
        Animation animation=view.getAnimation();
        if(animation!=null){capture(capture,capture==null?LayoutDiagnosticDetail.UNKNOWN:animationWhy(animation),root,top,view);return false;}
        if(view.isLayoutRequested()){capture(capture,LayoutDiagnosticDetail.LAYOUT_REQ,root,top,view);return false;}
        RoiExclusionTrace.context(excluded,top,view);
        if(forceLocal){
            Rect status=ForceLocalRegion.statusExclusion(root,view);
            if(status!=null){RoiExclusionTrace.reason(excluded,RoiDiagnosticDetail.FORCE_STATUS);excluded.add(status);return true;}
        }
        StatusBarIsolation.Result isolation=StatusBarIsolation.tryIsolate(root,view,safeRoi,512-budget[0]);
        if(isolation!=null){RoiExclusionTrace.reason(excluded,RoiDiagnosticDetail.HARD_STATUS);budget[0]+=isolation.additionalNodes;excluded.add(isolation.fullHitbox());return true;}
        if(IdleDecoration.mayIgnore(root,view))return true;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            if(group.getChildCount()>128){capture(capture,LayoutDiagnosticDetail.CHILD_LIMIT,root,top,view);return false;}
            // 透明组织容器逐叶扣除；本身有前景/点击处理者则整个区域扣除。
            if(view!=positive&&(view.isClickable()||view.isLongClickable()||view.hasOnClickListeners()
                    ||view.getForeground()!=null||view.getBackground()!=null)){RoiExclusionTrace.container(excluded,view);add(view,excluded);}
            for(int i=0;i<group.getChildCount();i++)if(!foreground(root,group.getChildAt(i),positive,excluded,budget,capture,top,safeRoi,forceLocal))return false;
            // 无child的未知自绘ViewGroup不能按透明理解。
            if(group.getChildCount()==0&&view!=positive&&!view.willNotDraw()){RoiExclusionTrace.reason(excluded,RoiDiagnosticDetail.EMPTY_DRAW);add(view,excluded);}
        }else add(view,excluded);
        return true;
    }
    /** 首拒绝只记录固定枚举。任何诊断读取失败不改变原false结果。 */
    private static final class LayoutCapture {int code;}
    private static void capture(LayoutCapture capture,int why,View root,View top,View node){
        if(capture==null||capture.code!=0)return;
        int topKind=LayoutDiagnosticDetail.OTHER,nodeKind=LayoutDiagnosticDetail.OTHER;
        try{if(top!=null&&top.getParent()==root)topKind=layoutNode(root,top);}catch(Throwable ignored){ }
        try{nodeKind=layoutNode(root,node);}catch(Throwable ignored){ }
        capture.code=LayoutDiagnosticDetail.code(why,topKind,nodeKind);
    }
    private static int animationWhy(Animation animation){
        try{
            if(animation.hasEnded())return animation.getFillAfter()?LayoutDiagnosticDetail.ANIM_ENDED_FILL:LayoutDiagnosticDetail.ANIM_ENDED;
            return animation.hasStarted()?LayoutDiagnosticDetail.ANIM_ACTIVE:LayoutDiagnosticDetail.ANIM_NEW;
        }catch(Throwable ignored){return LayoutDiagnosticDetail.UNKNOWN;}
    }
    /** 资源、精确类/加载器及实际对象三者都匹配才命名；非白名单永远OTHER。 */
    private static int layoutNode(View root,View node)throws Exception{
        if(root==null||node==null)return LayoutDiagnosticDetail.OTHER;
        String[] ids={"keyguard_root_view","shared_notification_container","miui_keyguard_face_unlock_view_keygurd_container",
                "miui_keyguard_face_unlock_view_keygurd","scrim_in_front","keyguard_message_area_container","keyguard_message_area",
                "keyguard_bouncer_container","notification_panel","control_center_container","shade_header_container","header_clip",
                "header_foreground","footer_clip","aod_root_view","auth_ripple","notification_stack_scroller","shade_background",
                "brightness_mirror_container","keyguard_left_view","keyguard_status_bar","low_light_animation_container","light_reveal_scrim"};
        String[] classes={"com.android.keyguard.widget.HyperOSKeyguardRootView",
                "com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainer","android.widget.FrameLayout",
                "com.miui.keyguard.biometrics.faceunlock.MiuiKeyguardFaceUnlockView","com.android.systemui.scrim.ScrimView","android.widget.LinearLayout",
                "com.android.keyguard.AuthKeyguardMessageArea","android.widget.FrameLayout","com.android.systemui.shade.NotificationPanelView",
                "com.miui.systemui.controlcenter.container.ControlCenterContainer","com.miui.systemui.shade.header.ShadeHeaderContainer",
                "com.miui.systemui.shade.ShadeBackgroundView","com.miui.systemui.shade.ShadeBackgroundView","com.miui.systemui.shade.ShadeBackgroundView",
                "com.android.keyguard.widget.AodView","com.android.systemui.biometrics.AuthRippleView",
                "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout","com.miui.systemui.shade.ShadeBackgroundView",
                "android.widget.FrameLayout","com.android.keyguard.widget.MiuiKeyguardMoveLeftViewContainer",
                "com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView","android.widget.FrameLayout","com.android.systemui.statusbar.LightRevealScrim"};
        for(int i=0;i<ids.length;i++){
            int id=root.getResources().getIdentifier(ids[i],"id","com.android.systemui");
            if(id==0||node.getId()!=id||!classes[i].equals(node.getClass().getName()))continue;
            boolean loader="android.widget.FrameLayout".equals(classes[i])?node.getClass()==FrameLayout.class:
                    "android.widget.LinearLayout".equals(classes[i])?node.getClass()==LinearLayout.class:
                    node.getClass().getClassLoader()==root.getClass().getClassLoader();
            if(!loader)continue;
            if(root.findViewById(id)==node)return i+1;
            // shade_background在根与通知容器中重复；仅injector双对象绑定可命名嵌套副本。
            if(i+1==LayoutDiagnosticDetail.SHADE&&nestedShadeObject(root,node))return LayoutDiagnosticDetail.SHADE;
        }
        return LayoutDiagnosticDetail.OTHER;
    }
    private static boolean nestedShadeObject(View root,View node)throws Exception{
        if(!(node.getParent() instanceof View))return false;
        View parent=(View)node.getParent();int id=root.getResources().getIdentifier("shared_notification_container","id","com.android.systemui");
        if(id==0||parent!=root.findViewById(id)||parent.getParent()!=root
                ||!"com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainer".equals(parent.getClass().getName())
                ||parent.getClass().getClassLoader()!=root.getClass().getClassLoader())return false;
        Object injector=get(parent,"injector");
        return injector!=null&&"com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainerInjector".equals(injector.getClass().getName())
                &&injector.getClass().getClassLoader()==root.getClass().getClassLoader()
                &&get(injector,"container")==parent&&get(injector,"shadeBackground")==node;
    }
    private static void add(View view,List<Rect> output){Rect rect=new Rect();if(view.getGlobalVisibleRect(rect)&&!rect.isEmpty())output.add(rect);}
    private static boolean pureBackgroundLayer(View root,View child){
        if(child.isClickable()||child.isLongClickable()||child.hasOnClickListeners())return false;
        if(child.getClass().getClassLoader()!=root.getClass().getClassLoader())return false;
        String name=child.getClass().getName();
        if("com.android.systemui.scrim.ScrimView".equals(name))return child==find(root,"scrim_behind")||child==find(root,"scrim_notifications");
        if("com.miui.systemui.shade.ShadeBackgroundView".equals(name))return child==find(root,"shade_background");
        if("com.miui.systemui.shade.BlurProviderView".equals(name))return child==find(root,"blur_provider");
        return false;
    }
    static boolean nativeFingerprintRegions(Object monitor,List<Rect> excluded,Rect window)throws Exception{
        return fingerprintRegions(monitor,excluded,window);
    }
    private static boolean fingerprintRegions(Object monitor,List<Rect> excluded,Rect window)throws Exception{
        Object value=get(monitor,"mFingerprintSensorProperties");
        if(!(value instanceof List<?>))return false;
        List<?> properties=(List<?>)value;if(properties.size()>8)return false;
        // 初始化前空列表不证明设备不存在屏下传感器。
        if(properties.isEmpty())return false;
        for(Object sensor:properties){
            int type=((Number)get(sensor,"sensorType")).intValue();
            if(type!=2&&type!=3)continue;
            Object location=method(sensor,"getLocation").invoke(sensor);
            int x=((Number)get(location,"sensorLocationX")).intValue(),y=((Number)get(location,"sensorLocationY")).intValue();
            int radius=((Number)get(location,"sensorRadius")).intValue();
            if(!window.contains(x,y)||radius<=0||radius>4096)return false;
            RoiExclusionTrace.fingerprint(excluded);
            int safeRadius=radius*2;excluded.add(new Rect(x-safeRadius,y-safeRadius,x+safeRadius,y+safeRadius));
        }
        return true;
    }
    private static View find(View root,String id){int value=root.getResources().getIdentifier(id,"id","com.android.systemui");return value==0?null:root.findViewById(value);}
    private static Object get(Object object,String name)throws Exception{
        if(object==null)throw new MissingProperty(name);
        try{return LockInputBroker.field(object.getClass(),name).get(object);}catch(Throwable ignored){throw new MissingProperty(name);}
    }
    private static boolean bool(Object object,String name)throws Exception{Object value=get(object,name);if(!(value instanceof Boolean))throw new MissingProperty(name);return (Boolean)value;}
    private static boolean flowBool(Object flow,String owner)throws Exception{
        try{Object value=method(flow,"getValue").invoke(flow);if(!(value instanceof Boolean))throw new MissingProperty("FLOW_"+owner);return (Boolean)value;}
        catch(Throwable ignored){throw new MissingProperty("FLOW_"+owner);}
    }
    private static Method method(Object object,String name)throws Exception{
        if(object==null)throw new MissingProperty("METHOD_"+name);
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Method method=type.getDeclaredMethod(name);method.setAccessible(true);return method;}catch(NoSuchMethodException ignored){ }
        throw new MissingProperty("METHOD_"+name);
    }
    private static final class MissingProperty extends Exception{final String key;MissingProperty(String key){this.key=key;}}
}
