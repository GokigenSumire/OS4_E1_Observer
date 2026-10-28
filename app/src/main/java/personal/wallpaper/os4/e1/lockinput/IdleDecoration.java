package personal.wallpaper.os4.e1.lockinput;

import android.animation.Animator;
import android.graphics.drawable.Drawable;
import android.view.View;

/** 仅已核同APK的两个装饰实例可证明空绘制；不是通用透明View白名单。 */
final class IdleDecoration {
    static boolean mayIgnore(View root,View child){
        if(root==null||child==null||child.getClass().getClassLoader()!=root.getClass().getClassLoader()
                ||!baseIdle(child))return false;
        try{
            String name=child.getClass().getName();
            if("com.android.systemui.scrim.ScrimView".equals(name)&&exactId(root,child,"scrim_in_front"))return idleScrim(child);
            if("com.android.systemui.biometrics.AuthRippleView".equals(name)&&exactId(root,child,"auth_ripple"))return idleRipple(child);
            if("com.miui.systemui.shade.ShadeBackgroundView".equals(name))return idleNestedShade(root,child);
        }catch(Throwable ignored){return false;}
        return false;
    }
    private static boolean baseIdle(View view){
        return !view.isClickable()&&!view.isLongClickable()&&!view.hasOnClickListeners()
                &&view.getBackground()==null&&view.getForeground()==null&&view.getAnimation()==null&&!view.isLayoutRequested();
    }
    private static boolean idleNestedShade(View root,View child)throws Exception{
        int shadeId=root.getResources().getIdentifier("shade_background","id","com.android.systemui");
        int containerId=root.getResources().getIdentifier("shared_notification_container","id","com.android.systemui");
        if(shadeId==0||containerId==0||child.getId()!=shadeId||!(child.getParent() instanceof View))return false;
        View parent=(View)child.getParent();
        if(parent!=root.findViewById(containerId)||parent.getParent()!=root||!baseIdle(parent)
                ||!"com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainer".equals(parent.getClass().getName())
                ||parent.getClass().getClassLoader()!=root.getClass().getClassLoader())return false;
        Object injector=objectField(parent,"injector");
        if(injector==null||!"com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainerInjector".equals(injector.getClass().getName())
                ||injector.getClass().getClassLoader()!=root.getClass().getClassLoader()
                ||objectField(injector,"container")!=parent||objectField(injector,"shadeBackground")!=child)return false;
        Object helper=objectField(injector,"helper");
        if(helper==null)return true;
        if(!"com.miui.systemui.shade.header.ShadeHeaderClipController".equals(helper.getClass().getName())
                ||helper.getClass().getClassLoader()!=root.getClass().getClassLoader())return false;
        Object flow=objectField(helper,"clipRatio");
        if(flow==null)return false;
        java.lang.reflect.Method value=flow.getClass().getMethod("getValue");value.setAccessible(true);
        Object ratio=value.invoke(flow);
        if(!(ratio instanceof Number))return false;
        float number=((Number)ratio).floatValue();return Float.isFinite(number)&&number==0f;
    }
    private static boolean exactId(View root,View child,String id){
        int identifier=root.getResources().getIdentifier(id,"id","com.android.systemui");
        return identifier!=0&&root.findViewById(identifier)==child&&child.getParent()==root;
    }
    private static boolean idleScrim(View child)throws Exception{
        Object alpha=field(child,"mViewAlpha"),drawable=field(child,"mDrawable");
        return alpha instanceof Float&&Float.isFinite((Float)alpha)&&(Float)alpha==0f
                &&drawable instanceof Drawable&&((Drawable)drawable).getAlpha()==0;
    }
    private static boolean idleRipple(View child)throws Exception{
        if(!Boolean.FALSE.equals(field(child,"drawDwell"))||!Boolean.FALSE.equals(field(child,"drawRipple")))return false;
        for(String name:new String[]{"dwellPulseOutAnimator","fadeDwellAnimator","retractDwellAnimator","unlockedRippleAnimator"}){
            Object value=field(child,name);
            if(value!=null&&(!(value instanceof Animator)||((Animator)value).isStarted()||((Animator)value).isRunning()))return false;
        }
        return true;
    }
    private static Object objectField(Object value,String name)throws Exception{return LockInputBroker.field(value.getClass(),name).get(value);}
    private static Object field(View value,String name)throws Exception{return objectField(value,name);}
}
