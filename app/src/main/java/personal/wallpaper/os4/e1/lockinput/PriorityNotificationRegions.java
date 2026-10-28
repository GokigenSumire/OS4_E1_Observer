package personal.wallpaper.os4.e1.lockinput;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.SurfaceView;
import android.view.TextureView;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 壁纸优先不信任主题空白；通知必须独立核定，再扣完整卡片而非整屏绘制容器。 */
final class PriorityNotificationRegions {
    static final class Result {
        final boolean allowed;final String reason;private final List<Rect> areas;
        private Result(boolean allowed,String reason,List<Rect> areas){this.allowed=allowed;this.reason=reason;ArrayList<Rect> copy=new ArrayList<>();for(Rect area:areas)copy.add(new Rect(area));this.areas=List.copyOf(copy);}
        List<Rect> areas(){ArrayList<Rect> copy=new ArrayList<>();for(Rect area:areas)copy.add(new Rect(area));return copy;}
    }
    private static final String STACK="com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout";
    private static final String CONTROLLER="com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController";
    private static final String COLLECTION="com.android.systemui.statusbar.notification.collection.NotifCollection";
    private static final String ROW="com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    private PriorityNotificationRegions(){ }
    private static Result denied(String reason){return new Result(false,reason,List.of());}
    static Result read(View root){
        try{
            if(root==null||!"com.android.systemui.shade.NotificationShadeWindowView".equals(root.getClass().getName())||!stable(root))return denied("PRIORITY_ROOT_UNSTABLE");
            int id=root.getResources().getIdentifier("notification_stack_scroller","id","com.android.systemui");
            View target=id==0?null:root.findViewById(id);
            if(!(target instanceof ViewGroup)||!STACK.equals(target.getClass().getName())||target.getClass().getClassLoader()!=root.getClass().getClassLoader())return denied("PRIORITY_NOTIFICATION_IDENTITY");
            ViewGroup stack=(ViewGroup)target;
            if(!descendsFrom(root,stack)||countIdentity(root,id,stack,new int[]{0})!=1)return denied("PRIORITY_NOTIFICATION_IDENTITY");
            Object controller=field(stack,"mController");
            if(!exact(root,controller,CONTROLLER)||field(controller,"mView")!=stack)return denied("PRIORITY_NOTIFICATION_IDENTITY");
            Object collection=field(controller,"mNotifCollection");
            if(!exact(root,collection,COLLECTION))return denied("PRIORITY_NOTIFICATION_COLLECTION");
            Object entries=field(collection,"mNotificationSet");
            if(!(entries instanceof Map))return denied("PRIORITY_NOTIFICATION_COLLECTION");
            int notifications=((Map<?,?>)entries).size();
            if(notifications<0||notifications>512)return denied("PRIORITY_NOTIFICATION_COLLECTION");
            if(bool(stack,"mAnimationRunning")||bool(stack,"mNeedsAnimation")||bool(stack,"mIsExpansionChanging"))return denied("PRIORITY_NOTIFICATION_TRANSITION");
            for(String name:new String[]{"mChildrenToAddAnimated","mChildrenToRemoveAnimated","mSwipedOutViews"}){
                Object pending=field(stack,name);if(!(pending instanceof Collection<?>)||!((Collection<?>)pending).isEmpty())return denied("PRIORITY_NOTIFICATION_TRANSITION");
            }
            if(!noIndependentDrawQueues(stack))return denied("PRIORITY_NOTIFICATION_TRANSITION");
            ArrayList<Rect> areas=new ArrayList<>();int rows=0;
            if(stack.isShown()&&stack.getAlpha()>0){
                if(!stable(stack)||stack.getChildCount()>128)return denied("PRIORITY_NOTIFICATION_LAYOUT");
                for(int i=0;i<stack.getChildCount();i++){
                    View item=stack.getChildAt(i);
                    if(item==null)return denied("PRIORITY_NOTIFICATION_LAYOUT");
                    if(!item.isShown()||item.getAlpha()<=0)continue;
                    if(!stable(item)||item instanceof SurfaceView||item instanceof TextureView||!noIndependentDrawQueues(item))return denied("PRIORITY_NOTIFICATION_LAYOUT");
                    Rect rect=physicalAndVisible(item);if(rect==null)return denied("PRIORITY_NOTIFICATION_GEOMETRY");
                    areas.add(rect);if(knownRow(root,item))rows++;
                }
            }
            // 集合有通知而无法对应至少一个真实可见卡片，绝不能按“空列表”放行。
            if(notifications>0&&rows==0)return denied("PRIORITY_NOTIFICATION_UNACCOUNTED");
            String[] mediaIds={"media_carousel_scroller","tiny_media_session_container"};
            String[] mediaClasses={"com.android.systemui.media.controls.ui.view.MediaScrollView","androidx.constraintlayout.widget.ConstraintLayout"};
            for(int i=0;i<mediaIds.length;i++){
                int mediaId=root.getResources().getIdentifier(mediaIds[i],"id","com.android.systemui");
                if(mediaId==0)return denied("PRIORITY_MEDIA_IDENTITY");
                View media=root.findViewById(mediaId);if(media==null||!media.isShown()||media.getAlpha()<=0)continue;
                if(!mediaClasses[i].equals(media.getClass().getName())||media.getClass().getClassLoader()!=root.getClass().getClassLoader()||!descendsFrom(root,media)||!stable(media)||!noIndependentDrawQueues(media))return denied("PRIORITY_MEDIA_IDENTITY");
                Rect rect=physicalAndVisible(media);if(rect==null)return denied("PRIORITY_MEDIA_GEOMETRY");areas.add(rect);
            }
            // 系统快捷按钮/编辑按钮/紧急入口不是主题空白，按精确已核资源保留完整命中区。
            String[] controlIds={"keyguard_shortcut_container","custom_lockscreen_button","emergency_call_button","miui_tiny_keyguard_camera_view"};
            String[] controlClasses={"android.widget.FrameLayout","com.android.keyguard.view.EffectivelyVisibleFlowLayout","com.android.keyguard.EmergencyButton","com.android.keyguard.widget.TinyCameraView"};
            for(int i=0;i<controlIds.length;i++){
                int controlId=root.getResources().getIdentifier(controlIds[i],"id","com.android.systemui");
                if(controlId==0)return denied("PRIORITY_CONTROL_IDENTITY");
                View control=root.findViewById(controlId);if(control==null||!control.isShown()||control.getAlpha()<=0)continue;
                boolean identity=i==0?control.getClass()==FrameLayout.class:controlClasses[i].equals(control.getClass().getName())&&control.getClass().getClassLoader()==root.getClass().getClassLoader();
                if(!identity||!descendsFrom(root,control))return denied("PRIORITY_CONTROL_IDENTITY");
                if(i==0&&((FrameLayout)control).getChildCount()==0&&(control.getWidth()==0||control.getHeight()==0))continue;
                if(!stable(control)||!noIndependentDrawQueues(control))return denied("PRIORITY_CONTROL_LAYOUT");
                Rect rect=physicalAndVisible(control);if(rect==null)return denied("PRIORITY_CONTROL_GEOMETRY");areas.add(rect);
            }
            return new Result(true,"PRIORITY_NOTIFICATIONS_VERIFIED",areas);
        }catch(Throwable ignored){return denied("PRIORITY_NOTIFICATION_PROPERTIES_UNKNOWN");}
    }
    private static int countIdentity(View view,int id,View stack,int[] budget){
        if(++budget[0]>512)return 2;
        int found=view.getId()==id?1:0;
        // 通知卡片本身已整块保护；其内部重复资源不是另一个根级通知列表。
        if(view==stack)return found;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;if(group.getChildCount()>128)return 2;
            for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(child==null)return 2;found+=countIdentity(child,id,stack,budget);if(found>1)return 2;}}
        return found;
    }
    private static boolean exact(View root,Object object,String name){return object!=null&&name.equals(object.getClass().getName())&&object.getClass().getClassLoader()==root.getClass().getClassLoader();}
    private static boolean descendsFrom(View root,View node){
        View cursor=node;for(int depth=0;depth<32;depth++){if(cursor==root)return true;if(!(cursor.getParent() instanceof View))return false;cursor=(View)cursor.getParent();}return false;
    }
    private static boolean knownRow(View root,View row){
        for(Class<?> type=row.getClass();type!=null;type=type.getSuperclass())if(ROW.equals(type.getName()))return type.getClassLoader()==root.getClass().getClassLoader();return false;
    }
    private static boolean stable(View view){
        return view.isAttachedToWindow()&&view.isShown()&&view.getWindowVisibility()==View.VISIBLE&&view.isLaidOut()&&!view.isLayoutRequested()
                &&view.getWidth()>0&&view.getHeight()>0&&view.getAnimation()==null&&view.getTouchDelegate()==null
                &&Float.isFinite(view.getAlpha())&&view.getAlpha()>0&&Float.isFinite(view.getTranslationX())&&Float.isFinite(view.getTranslationY())
                &&view.getScaleX()==1f&&view.getScaleY()==1f&&view.getRotation()==0f&&view.getRotationX()==0f&&view.getRotationY()==0f
                &&view.getTranslationZ()==0f&&view.getAnimationMatrix()==null;
    }
    private static boolean noIndependentDrawQueues(View view)throws Exception{
        if(!(view instanceof ViewGroup))return true;
        ViewGroup group=(ViewGroup)view;if(group.getLayoutTransition()!=null||group.isLayoutSuppressed())return false;
        for(String name:new String[]{"mTransientViews","mDisappearingChildren","mTransitioningViews","mVisibilityChangingChildren"}){
            Object queue=LockInputBroker.field(ViewGroup.class,name).get(group);
            if(queue!=null&&(!(queue instanceof Collection<?>)||!((Collection<?>)queue).isEmpty()))return false;
        }
        return LockInputBroker.field(View.class,"mOverlay").get(view)==null;
    }
    private static Rect physicalAndVisible(View view){
        int[] location=new int[2];view.getLocationOnScreen(location);
        long right=(long)location[0]+view.getWidth(),bottom=(long)location[1]+view.getHeight();
        if(right>Integer.MAX_VALUE||bottom>Integer.MAX_VALUE||right<Integer.MIN_VALUE||bottom<Integer.MIN_VALUE)return null;
        Rect full=new Rect(location[0],location[1],(int)right,(int)bottom),visible=new Rect();
        if(full.isEmpty())return null;
        // 屏外/被裁剪卡片仍保留完整物理区；可见矩形只能扩充，不能缩小命中保护。
        if(view.getGlobalVisibleRect(visible)&&!visible.isEmpty())full.union(visible);
        return full;
    }
    private static Object field(Object value,String name)throws Exception{return LockInputBroker.field(value.getClass(),name).get(value);}
    private static boolean bool(Object value,String name)throws Exception{Object item=field(value,name);if(!(item instanceof Boolean))throw new IllegalStateException();return (Boolean)item;}
}
