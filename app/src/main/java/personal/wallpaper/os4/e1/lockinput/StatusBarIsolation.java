package personal.wallpaper.os4.e1.lockinput;

import android.animation.LayoutTransition;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import java.util.List;

/** 仅已核直属状态栏的硬裁剪围栏；排除完整触摸父区而不是把状态栏当背景。 */
final class StatusBarIsolation {
    private static final int MAX_NODES=512,MAX_CHILDREN=128;
    static final class Result {
        private final Rect hitbox;
        final int additionalNodes;
        Result(Rect hitbox,int additionalNodes){this.hitbox=new Rect(hitbox);this.additionalNodes=additionalNodes;}
        Rect fullHitbox(){return new Rect(hitbox);}
    }
    private StatusBarIsolation(){ }
    /** 局部失败不写调用者预算/排除区；status自身已在foreground计为一个节点。 */
    static Result tryIsolate(View root,View status,Rect safeRoi,int remainingNodes){
        try{
            if(safeRoi==null||safeRoi.isEmpty()||remainingNodes<0||remainingNodes>MAX_NODES||!(root instanceof ViewGroup)||!(status instanceof ViewGroup))return null;
            if(!"com.android.systemui.shade.NotificationShadeWindowView".equals(root.getClass().getName())
                    ||!"com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView".equals(status.getClass().getName())
                    ||status.getClass().getClassLoader()!=root.getClass().getClassLoader()||status.getParent()!=root)return null;
            int id=root.getResources().getIdentifier("keyguard_status_bar","id","com.android.systemui");
            if(id==0||status.getId()!=id||root.findViewById(id)!=status)return null;
            if(!stableFrame(root)||!stableFrame(status)||!safeNode(root)||!safeNode(status))return null;
            Rect clip=status.getClipBounds();
            if(clip==null||clip.isEmpty()||clip.left<0||clip.top<0||clip.right>status.getWidth()||clip.bottom>status.getHeight())return null;
            Rect rootBox=physicalHitbox(root),box=physicalHitbox(status);
            if(rootBox==null||box==null||!rootBox.contains(box)||!rootBox.contains(safeRoi)||Rect.intersects(box,safeRoi))return null;
            // 只接受可由当前无变换直接父坐标精确解释的屏幕映射。
            if((long)rootBox.left+status.getLeft()!=box.left||(long)rootBox.top+status.getTop()!=box.top)return null;
            Rect visibleRoot=new Rect();
            if(!root.getGlobalVisibleRect(visibleRoot)||!visibleRoot.equals(rootBox))return null;
            int[] visited={0};
            if(!scanChildren((ViewGroup)status,visited,remainingNodes))return null;
            return new Result(box,visited[0]);
        }catch(Throwable ignored){return null;}
    }
    private static boolean stableFrame(View view){
        return view.isAttachedToWindow()&&view.isShown()&&view.isLaidOut()&&!view.isLayoutRequested()
                &&view.getWindowVisibility()==View.VISIBLE&&view.getWidth()>0&&view.getHeight()>0;
    }
    private static Rect physicalHitbox(View view){
        int[] location=new int[2];view.getLocationOnScreen(location);
        long right=(long)location[0]+view.getWidth(),bottom=(long)location[1]+view.getHeight();
        if(view.getWidth()<=0||view.getHeight()<=0||right>Integer.MAX_VALUE||right<Integer.MIN_VALUE||bottom>Integer.MAX_VALUE||bottom<Integer.MIN_VALUE)return null;
        return new Rect(location[0],location[1],(int)right,(int)bottom);
    }
    private static boolean safeNode(View view)throws Exception{
        if(view instanceof SurfaceView||view instanceof TextureView||view.getAnimation()!=null||view.getTouchDelegate()!=null
                ||view.hasTransientState()||view.getScrollX()!=0||view.getScrollY()!=0||view.getElevation()!=0f)return false;
        float alpha=view.getAlpha(),transitionAlpha=view.getTransitionAlpha();
        if(!Float.isFinite(alpha)||alpha<0f||alpha>1f||!Float.isFinite(transitionAlpha)||transitionAlpha!=1f)return false;
        if(view.getTranslationX()!=0f||view.getTranslationY()!=0f||view.getTranslationZ()!=0f
                ||view.getRotation()!=0f||view.getRotationX()!=0f||view.getRotationY()!=0f||view.getScaleX()!=1f||view.getScaleY()!=1f)return false;
        Matrix matrix=view.getMatrix(),animationMatrix=view.getAnimationMatrix();
        if(matrix==null||!matrix.isIdentity()||(animationMatrix!=null&&!animationMatrix.isIdentity()))return false;
        // getOverlay()会懒创建对象；仅只读已有View基类字段，未知即拒绝本特例。
        if(LockInputBroker.field(View.class,"mOverlay").get(view)!=null)return false;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            LayoutTransition transition=group.getLayoutTransition();
            if(transition!=null||group.isLayoutSuppressed())return false;
            // getChildCount不包含这些仍可能绘制/过渡的独立队列；不改写或尝试跳过其中对象。
            for(String name:new String[]{"mTransientViews","mDisappearingChildren","mTransitioningViews","mVisibilityChangingChildren"}){
                Object value=LockInputBroker.field(ViewGroup.class,name).get(group);
                if(value!=null&&(!(value instanceof List<?>)||!((List<?>)value).isEmpty()))return false;
            }
        }
        return true;
    }
    private static boolean scanChildren(ViewGroup group,int[] visited,int remaining)throws Exception{
        int size=group.getChildCount();if(size>MAX_CHILDREN)return false;
        // 隐藏后代也扫描：不以isShown跳过Surface/overlay/触摸委托等潜在逃逸。
        for(int i=0;i<size;i++){
            if(++visited[0]>remaining||visited[0]>MAX_NODES)return false;
            View child=group.getChildAt(i);if(child==null||!safeNode(child))return false;
            if(child instanceof ViewGroup&&!scanChildren((ViewGroup)child,visited,remaining))return false;
        }
        return true;
    }
}
