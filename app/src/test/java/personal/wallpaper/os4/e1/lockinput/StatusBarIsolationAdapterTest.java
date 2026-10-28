package personal.wallpaper.os4.e1.lockinput;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.FrameLayout;
import com.android.systemui.shade.NotificationShadeWindowView;
import com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** 真Android宿主的限定围栏与生产递归；小同名夹具不冒OEM或目标机验收。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE,qualifiers="w1200dp-h2400dp-mdpi")
public final class StatusBarIsolationAdapterTest {
    private static final int STATUS=0x7f071401;
    private Activity activity;
    private ActivityController<Activity> controller;
    private Context context;
    private NotificationShadeWindowView root;
    private MiuiKeyguardStatusBarView status;
    private Rect roi;
    private View pending;
    private Method oldWalk,newWalk;
    @Before public void setUp()throws Exception{
        controller=Robolectric.buildActivity(Activity.class).setup();activity=controller.get();Resources original=activity.getResources();
        Resources resources=new Resources(original.getAssets(),original.getDisplayMetrics(),original.getConfiguration()){
            @Override public int getIdentifier(String name,String type,String pkg){
                if("keyguard_status_bar".equals(name)&&"id".equals(type)&&"com.android.systemui".equals(pkg))return STATUS;
                return super.getIdentifier(name,type,pkg);
            }
        };
        context=new ContextWrapper(activity){@Override public Resources getResources(){return resources;}};
        root=new NotificationShadeWindowView(context);root.setClipChildren(false);root.setClipToPadding(false);activity.setContentView(root);
        status=new MiuiKeyguardStatusBarView(context);status.setId(STATUS);root.addView(status,new FrameLayout.LayoutParams(1000,80));
        pending=new PendingView(context);status.addView(pending,new FrameLayout.LayoutParams(0,0));controller.visible();makeWindowVisible();layout();
        Rect rootBox=physical(root);roi=new Rect(rootBox.left+48,rootBox.top+100,rootBox.right-48,rootBox.bottom-48);
        Class<?> capture=Class.forName(LockSurfaceSnapshot.class.getName()+"$LayoutCapture");
        newWalk=LockSurfaceSnapshot.class.getDeclaredMethod("foreground",View.class,View.class,View.class,List.class,int[].class,capture,View.class,Rect.class);newWalk.setAccessible(true);
        oldWalk=LockSurfaceSnapshot.class.getDeclaredMethod("foreground",View.class,View.class,View.class,List.class,int[].class);oldWalk.setAccessible(true);
        assertTrue(root.isLaidOut());assertTrue(status.isLaidOut());assertFalse(root.isLayoutRequested());assertFalse(status.isLayoutRequested());assertTrue(pending.isLayoutRequested());
        assertTrue("真实Activity.visible附着根",root.isAttachedToWindow());assertTrue("真实Activity.visible附着STATUS",status.isAttachedToWindow());
        assertEquals("根窗口真实可见",View.VISIBLE,root.getWindowVisibility());assertEquals("STATUS窗口真实可见",View.VISIBLE,status.getWindowVisibility());
        Rect visible=new Rect();assertTrue(root.getGlobalVisibleRect(visible));assertEquals("宿主根必须完整可见，不能让小Activity窗口裁剪测试前提",physical(root),visible);
        assertEquals("夹具final布局设置了真实显式clipBounds",new Rect(0,0,1000,80),status.getClipBounds());
    }
    /** 复现已证稳定父/待布局后代，而不把强制清标志当修复。 */
    private static final class PendingView extends View {
        PendingView(Context context){super(context);}
        @Override public boolean isLayoutRequested(){return true;}
    }
    private void layout(){root.forceLayout();root.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY));root.layout(0,0,1000,2000);}
    private void makeWindowVisible()throws Exception{
        // 测试宿主通过真实ViewRoot生命周期分发窗口可见性，不改mAttachInfo或伪造getter。
        Method accessor=View.class.getDeclaredMethod("getViewRootImpl");accessor.setAccessible(true);
        Object viewRoot=accessor.invoke(activity.getWindow().getDecorView());assertNotNull(viewRoot);
        Method visible=viewRoot.getClass().getDeclaredMethod("dispatchAppVisibility",boolean.class);visible.setAccessible(true);visible.invoke(viewRoot,true);
        ShadowLooper.idleMainLooper();controller.windowFocusChanged(true);ShadowLooper.idleMainLooper();
    }
    private static Rect physical(View view){int[] xy=new int[2];view.getLocationOnScreen(xy);return new Rect(xy[0],xy[1],xy[0]+view.getWidth(),xy[1]+view.getHeight());}
    private StatusBarIsolation.Result isolate(){return StatusBarIsolation.tryIsolate(root,status,roi,511);}
    private void assertFallback(){assertNull(isolate());}
    private void add(View child){status.addView(child,new FrameLayout.LayoutParams(1,1));layout();}
    private static final class Walk {
        final boolean allowed;final int budget;final List<Rect> exclusions;
        Walk(boolean allowed,int budget,List<Rect> exclusions){this.allowed=allowed;this.budget=budget;this.exclusions=exclusions;}
    }
    private Walk walk(boolean modern,int initial,Rect safe)throws Exception{
        int[] budget={initial};List<Rect> exclusions=new ArrayList<>();
        boolean allowed=modern?(Boolean)newWalk.invoke(null,root,status,root,exclusions,budget,null,status,safe)
                :(Boolean)oldWalk.invoke(null,root,status,root,exclusions,budget);
        return new Walk(allowed,budget[0],exclusions);
    }
    @Test public void exactHardClippedStatusIsConservativelyExcludedAndPendingChildNotCleared(){
        StatusBarIsolation.Result result=isolate();assertNotNull(result);assertEquals(physical(status),result.fullHitbox());assertEquals(1,result.additionalNodes);assertTrue(pending.isLayoutRequested());
    }
    @Test public void resultDoesNotExposeMutableHitbox(){StatusBarIsolation.Result result=isolate();assertNotNull(result);Rect copy=result.fullHitbox();copy.setEmpty();assertEquals(physical(status),result.fullHitbox());}
    @Test public void safeRoiIsNeverMutated(){Rect original=new Rect(roi);assertNotNull(isolate());assertEquals(original,roi);}
    @Test public void productionIsolationCommitsOnlyFullHitboxAndActualScanBudget()throws Exception{
        Walk previous=walk(false,0,null);assertFalse(previous.allowed);assertEquals(2,previous.budget);
        Walk fixed=walk(true,0,roi);assertTrue(fixed.allowed);assertEquals(2,fixed.budget);assertEquals(List.of(physical(status)),fixed.exclusions);
    }
    @Test public void oldSignatureAndNullSafeRoiKeepOriginalRejection()throws Exception{
        Walk previous=walk(false,0,null),modern=walk(true,0,null);assertFalse(modern.allowed);assertEquals(previous.allowed,modern.allowed);assertEquals(previous.budget,modern.budget);assertEquals(previous.exclusions,modern.exclusions);
    }
    @Test public void failedSpeculationDoesNotDoubleCountOrAddExclusion()throws Exception{
        status.setClipBounds(null);Walk previous=walk(false,0,null),modern=walk(true,0,roi);
        assertFalse(modern.allowed);assertEquals(previous.budget,modern.budget);assertEquals(previous.exclusions,modern.exclusions);
    }
    @Test public void ownLayoutRequestStillRejectsBeforeIsolation()throws Exception{
        status.requestLayout();Walk modern=walk(true,0,roi);assertFalse(modern.allowed);assertEquals(1,modern.budget);assertTrue(modern.exclusions.isEmpty());
    }
    @Test public void ownAnimationStillRejectsBeforeIsolation()throws Exception{
        status.setAnimation(new AlphaAnimation(0,1));Walk modern=walk(true,0,roi);assertFalse(modern.allowed);assertEquals(1,modern.budget);assertTrue(modern.exclusions.isEmpty());
    }
    @Test public void wrongResourceIdentityFallsBack(){status.setId(STATUS+1);assertFallback();}
    @Test public void sameIdDifferentClassFallsBack(){FrameLayout other=new FrameLayout(context);other.setId(STATUS);root.removeView(status);root.addView(other,new FrameLayout.LayoutParams(1000,80));layout();assertNull(StatusBarIsolation.tryIsolate(root,other,roi,511));}
    @Test public void duplicateResourceObjectCannotStealIdentity(){MiuiKeyguardStatusBarView second=new MiuiKeyguardStatusBarView(context);second.setId(STATUS);root.addView(second,new FrameLayout.LayoutParams(1000,80));layout();assertNull(StatusBarIsolation.tryIsolate(root,second,roi,511));}
    @Test public void onlyDirectRootChildCanBeIsolated(){root.removeView(status);FrameLayout wrapper=new FrameLayout(context);root.addView(wrapper,new FrameLayout.LayoutParams(1000,80));wrapper.addView(status,new FrameLayout.LayoutParams(1000,80));layout();assertFallback();}
    @Test public void missingClipFallsBack(){status.setClipBounds(null);assertFallback();}
    @Test public void emptyClipFallsBack(){status.setClipBounds(new Rect());assertFallback();}
    @Test public void clipOutsideViewportFallsBack(){for(Rect clip:new Rect[]{new Rect(-1,0,1000,80),new Rect(0,-1,1000,80),new Rect(0,0,1001,80),new Rect(0,0,1000,81)}){status.setClipBounds(clip);assertFallback();}}
    @Test public void partialClipStillExcludesFullPhysicalTouchHitbox(){status.setClipBounds(new Rect(20,10,300,50));StatusBarIsolation.Result result=isolate();assertNotNull(result);assertEquals(physical(status),result.fullHitbox());assertEquals(1000,result.fullHitbox().width());assertEquals(80,result.fullHitbox().height());}
    @Test public void roiIntersectionAlwaysFallsBack(){Rect box=physical(status);assertNull(StatusBarIsolation.tryIsolate(root,status,new Rect(box.left,box.top+79,box.right,box.bottom+20),511));}
    @Test public void touchingRoiBoundaryIsDisjoint(){Rect box=physical(status);assertNotNull(StatusBarIsolation.tryIsolate(root,status,new Rect(box.left+48,box.bottom,box.right-48,box.bottom+100),511));}
    @Test public void invalidOrUnknownRoiFallsBack(){assertNull(StatusBarIsolation.tryIsolate(root,status,null,511));assertNull(StatusBarIsolation.tryIsolate(root,status,new Rect(),511));Rect box=physical(root);assertNull(StatusBarIsolation.tryIsolate(root,status,new Rect(box.left-1,box.top,box.right,box.bottom),511));}
    @Test public void zeroSizedParentFallsBack(){status.layout(0,0,0,80);assertFallback();}
    @Test public void parentOutsideRootWindowFallsBack(){status.layout(-1,0,999,80);assertFallback();}
    @Test public void rootPendingLayoutFallsBack(){root.requestLayout();assertFallback();}
    @Test public void rootAndStatusTransformsOrScrollFallBack(){
        for(View view:new View[]{root,status}){
            view.setTranslationX(1);assertFallback();view.setTranslationX(0);
            view.setTranslationY(1);assertFallback();view.setTranslationY(0);
            view.setTranslationZ(1);assertFallback();view.setTranslationZ(0);
            view.setRotation(1);assertFallback();view.setRotation(0);
            view.setScaleX(1.1f);assertFallback();view.setScaleX(1);
            view.scrollTo(0,1);assertFallback();view.scrollTo(0,0);
        }
        assertNotNull(isolate());
    }
    @Test public void rootOrStatusTouchDelegateFallsBack(){for(View view:new View[]{root,status}){view.setTouchDelegate(new TouchDelegate(new Rect(0,0,1000,2000),pending));assertFallback();view.setTouchDelegate(null);}assertNotNull(isolate());}
    @Test public void rootAndDescendantTransientStateFallsBack(){root.setHasTransientState(true);assertFallback();root.setHasTransientState(false);pending.setHasTransientState(true);assertFallback();pending.setHasTransientState(false);assertNotNull(isolate());}
    @Test public void anyExistingOverlayFallsBackWithoutCreatingOneDuringProbe(){assertNotNull(isolate());status.getOverlay();assertFallback();}
    @Test public void rootOverlayAlsoFallsBack(){root.getOverlay();assertFallback();}
    @Test public void anyLayoutTransitionFallsBack(){status.setLayoutTransition(new LayoutTransition());assertFallback();}
    @Test public void suppressedLayoutFallsBack(){status.suppressLayout(true);assertFallback();}
    @Test public void surfaceViewEvenHiddenFallsBack(){SurfaceView surface=new SurfaceView(context);surface.setVisibility(View.GONE);add(surface);assertFallback();}
    @Test public void textureViewEvenHiddenFallsBack(){TextureView texture=new TextureView(context);texture.setVisibility(View.GONE);add(texture);assertFallback();}
    @Test public void hiddenEscapeDescendantsAreNotPruned(){FrameLayout hidden=new FrameLayout(context);hidden.setVisibility(View.GONE);View target=new View(context);target.setTouchDelegate(new TouchDelegate(new Rect(0,0,10,10),pending));hidden.addView(target);add(hidden);assertFallback();}
    @Test public void descendantAnimationOrTransformOrOverlayFallsBack(){
        View child=new View(context);add(child);child.setAnimation(new AlphaAnimation(0,1));assertFallback();child.clearAnimation();
        child.setTranslationY(200);assertFallback();child.setTranslationY(0);child.getOverlay();assertFallback();
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void animationMatrixOrTransitionAlphaFallsBack(){
        Matrix matrix=new Matrix();matrix.setTranslate(0,1);status.setAnimationMatrix(matrix);
        assertNotNull("真实RenderNode必须已保存动画矩阵",status.getAnimationMatrix());assertFalse("矩阵必须实际非identity才测试拒绝",status.getAnimationMatrix().isIdentity());assertFallback();
        status.setAnimationMatrix(null);assertNull(status.getAnimationMatrix());status.setTransitionAlpha(.5f);assertEquals(.5f,status.getTransitionAlpha(),0f);assertFallback();
        status.setTransitionAlpha(1f);assertEquals(1f,status.getTransitionAlpha(),0f);assertNotNull(isolate());
    }
    @Test public void childCount128SucceedsAnd129FallsBack(){for(int i=1;i<128;i++)status.addView(new View(context),new FrameLayout.LayoutParams(0,0));layout();assertNotNull(isolate());status.addView(new View(context),new FrameLayout.LayoutParams(0,0));layout();assertFallback();}
    @Test public void localBudgetExactAndOneBelowDoNotMutateSharedInputs(){
        assertNotNull(StatusBarIsolation.tryIsolate(root,status,roi,1));assertNull(StatusBarIsolation.tryIsolate(root,status,roi,0));assertNull(StatusBarIsolation.tryIsolate(root,status,roi,-1));assertNull(StatusBarIsolation.tryIsolate(root,status,roi,513));assertNotNull(isolate());
    }
    @Test public void completeSubtreeBudgetIncludesHiddenNodes(){FrameLayout hidden=new FrameLayout(context);hidden.setVisibility(View.GONE);hidden.addView(new View(context));add(hidden);assertNull(StatusBarIsolation.tryIsolate(root,status,roi,2));StatusBarIsolation.Result result=StatusBarIsolation.tryIsolate(root,status,roi,3);assertNotNull(result);assertEquals(3,result.additionalNodes);}
    @Test public void global512BoundaryIncludesCommittedIsolationScan()throws Exception{
        Walk exact=walk(true,510,roi);assertTrue(exact.allowed);assertEquals(512,exact.budget);
        Walk over=walk(true,511,roi);assertFalse(over.allowed);assertEquals(513,over.budget);assertTrue(over.exclusions.isEmpty());
    }
    @Test public void differentDeclaredLoaderCannotGainStatusIsolation()throws Exception{
        String name=MiuiKeyguardStatusBarView.class.getName();byte[] bytes;try(InputStream stream=MiuiKeyguardStatusBarView.class.getResourceAsStream("/"+name.replace('.','/')+".class")){assertNotNull(stream);bytes=stream.readAllBytes();}final byte[] definition=bytes;
        ClassLoader loader=new ClassLoader(status.getClass().getClassLoader()){
            @Override protected Class<?> loadClass(String value,boolean resolve)throws ClassNotFoundException{
                if(!name.equals(value))return super.loadClass(value,resolve);Class<?> type=findLoadedClass(value);if(type==null)type=defineClass(value,definition,0,definition.length);if(resolve)resolveClass(type);return type;
            }
        };
        View other=(View)loader.loadClass(name).getConstructor(Context.class).newInstance(context);other.setId(STATUS);root.removeView(status);root.addView(other,new FrameLayout.LayoutParams(1000,80));layout();assertNotSame(root.getClass().getClassLoader(),other.getClass().getClassLoader());assertNull(StatusBarIsolation.tryIsolate(root,other,roi,511));
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void actualParentDrawClipsOversizedChildDespiteClipChildrenFalse(){
        status.removeView(pending);View paint=new View(context);paint.setBackground(new ColorDrawable(Color.RED));status.addView(paint,new FrameLayout.LayoutParams(1000,250));layout();
        root.setBackgroundColor(Color.BLUE);status.setClipBounds(new Rect(0,0,1000,80));assertFalse(status.getClipChildren());assertFalse(root.getClipChildren());
        Bitmap bitmap=Bitmap.createBitmap(1000,2000,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);root.draw(canvas);
        assertEquals(Color.RED,bitmap.getPixel(500,40));assertEquals(Color.BLUE,bitmap.getPixel(500,150));
    }
    @Test public void actualTouchHitboxRemainsFullParentWhenVisibleClipIsSmaller(){
        status.removeView(pending);status.setClickable(true);layout();status.setClipBounds(new Rect(0,0,1000,20));
        MotionEvent event=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,500,60,0);
        try{assertTrue(root.dispatchTouchEvent(event));}finally{event.recycle();}
        StatusBarIsolation.Result result=isolate();assertNotNull(result);assertEquals(physical(status),result.fullHitbox());
    }
    @Test public void elevationOnRootParentOrDescendantFallsBack(){
        for(View view:new View[]{root,status,pending}){view.setElevation(1f);assertFallback();view.setElevation(0f);}
        assertNotNull(isolate());
    }
    @Test public void androidRejectsNonFiniteElevationBeforeItCanBecomeRuntimeState(){
        try{status.setElevation(Float.NaN);fail("Android必须拒绝NaN elevation");}catch(IllegalArgumentException expected){assertEquals(0f,status.getElevation(),0f);}
    }
    @Test public void actualTransitionRemovalCreatesIndependentDisappearingQueueAndFallsBack()throws Exception{
        View child=new View(context);add(child);status.startViewTransition(child);status.removeView(child);
        layout();assertFalse(root.isLayoutRequested());assertFalse(status.isLayoutRequested());
        Object queue=groupField("mDisappearingChildren").get(status);
        assertTrue("实际startViewTransition/removeView须先产生非空消失绘制队列",queue instanceof List<?>);
        assertTrue(((List<?>)queue).contains(child));assertEquals(-1,status.indexOfChild(child));assertFallback();
    }
    @Test public void artificialTransientQueueFaultCannotHideEscapingSurfaceFromChildCount()throws Exception{
        // 人工故障注入：并非声称本机OEM自然出现这份队列。
        SurfaceView surface=new SurfaceView(context);ArrayList<View> queue=new ArrayList<>();queue.add(surface);groupField("mTransientViews").set(status,queue);
        assertEquals(-1,status.indexOfChild(surface));assertEquals(1,((List<?>)groupField("mTransientViews").get(status)).size());assertFallback();
    }
    @Test public void everyIndependentDrawQueueRejectsNonEmptyButAcceptsNullAndEmpty()throws Exception{
        for(String name:new String[]{"mTransientViews","mDisappearingChildren","mTransitioningViews","mVisibilityChangingChildren"}){
            Field field=groupField(name);Object previous=field.get(status);
            try{
                field.set(status,null);assertNotNull(name+" null",isolate());
                field.set(status,new ArrayList<View>());assertNotNull(name+" empty",isolate());
                ArrayList<View> queue=new ArrayList<>();queue.add(new View(context));field.set(status,queue);assertFallback();
            }finally{field.set(status,previous);}
        }
    }
    @Test public void independentDrawQueueOnRootOrDescendantAlsoFallsBack()throws Exception{
        FrameLayout child=new FrameLayout(context);add(child);
        for(ViewGroup group:new ViewGroup[]{root,child}){
            Field field=groupField("mTransitioningViews");Object previous=field.get(group);
            try{ArrayList<View> queue=new ArrayList<>();queue.add(new View(context));field.set(group,queue);assertFallback();}finally{field.set(group,previous);}
        }
        assertNotNull(isolate());
    }
    private static Field groupField(String name)throws Exception{Field field=ViewGroup.class.getDeclaredField(name);field.setAccessible(true);return field;}
    @Test public void diagnosticBaselineReportsExactFailedIsolationPrecondition()throws Exception{
        Method safe=StatusBarIsolation.class.getDeclaredMethod("safeNode",View.class);safe.setAccessible(true);
        Method stable=StatusBarIsolation.class.getDeclaredMethod("stableFrame",View.class);stable.setAccessible(true);
        for(View view:new View[]{root,status,pending}){
            String values="class="+view.getClass().getName()+" alpha="+view.getAlpha()+" transitionAlpha="+view.getTransitionAlpha()
                    +" elevation="+view.getElevation()+" scale="+view.getScaleX()+","+view.getScaleY()+" transform="+view.getMatrix()
                    +" animMatrix="+view.getAnimationMatrix()+" overlay="+LockInputBroker.field(View.class,"mOverlay").get(view)
                    +" transient="+view.hasTransientState()+" scroll="+view.getScrollX()+","+view.getScrollY();
            if(view instanceof ViewGroup){
                ViewGroup group=(ViewGroup)view;values+=" layoutTransition="+group.getLayoutTransition()+" suppressed="+group.isLayoutSuppressed();
                for(String name:new String[]{"mTransientViews","mDisappearingChildren","mTransitioningViews","mVisibilityChangingChildren"})values+=" "+name+"="+groupField(name).get(group);
            }
            System.out.println("STATUS74_BASELINE "+values);
            try{assertTrue(values,(Boolean)safe.invoke(null,view));}catch(java.lang.reflect.InvocationTargetException failure){throw new AssertionError(values,failure.getCause());}
        }
        for(View view:new View[]{root,status}){
            String frame="class="+view.getClass().getName()+" attached="+view.isAttachedToWindow()+" shown="+view.isShown()+" laidOut="+view.isLaidOut()+" layoutReq="+view.isLayoutRequested()+" windowVisibility="+view.getWindowVisibility()+" width="+view.getWidth()+" height="+view.getHeight();
            System.out.println("STATUS74_STABLE_FRAME "+frame);assertEquals(frame,Boolean.TRUE,stable.invoke(null,view));
        }
        assertEquals(root.getClass().getClassLoader(),status.getClass().getClassLoader());assertSame(status,root.findViewById(STATUS));assertSame(root,status.getParent());
        Rect rootBox=physical(root),box=physical(status),clip=status.getClipBounds();
        String geometry="root="+rootBox+" status="+box+" roi="+roi+" clip="+clip+" statusLocal="+status.getLeft()+","+status.getTop();System.out.println("STATUS74_GEOMETRY "+geometry);
        assertNotNull(geometry,clip);assertTrue(geometry,new Rect(0,0,status.getWidth(),status.getHeight()).contains(clip));assertTrue(geometry,rootBox.contains(box));assertTrue(geometry,rootBox.contains(roi));assertFalse(geometry,Rect.intersects(box,roi));
        assertEquals(geometry,(long)rootBox.left+status.getLeft(),box.left);assertEquals(geometry,(long)rootBox.top+status.getTop(),box.top);
        assertNotNull(geometry,isolate());
    }
    private static final class LocatedView extends View {
        private final int x,y;
        LocatedView(Context context,int x,int y){super(context);this.x=x;this.y=y;}
        @Override public void getLocationOnScreen(int[] out){out[0]=x;out[1]=y;}
    }
    private Rect physicalProduction(int x,int y,int width,int height)throws Exception{
        View view=new LocatedView(context,x,y);view.layout(0,0,width,height);assertEquals(width,view.getWidth());assertEquals(height,view.getHeight());
        int[] xy=new int[2];view.getLocationOnScreen(xy);assertEquals(x,xy[0]);assertEquals(y,xy[1]);
        Method method=StatusBarIsolation.class.getDeclaredMethod("physicalHitbox",View.class);method.setAccessible(true);return (Rect)method.invoke(null,view);
    }
    @Test public void physicalHitboxRejectsRightOrBottomIntegerOverflow()throws Exception{
        assertNull(physicalProduction(Integer.MAX_VALUE-5,0,6,1));assertNull(physicalProduction(0,Integer.MAX_VALUE-5,1,6));
    }
    @Test public void physicalHitboxPreservesExactIntegerEdgesWithoutOverflow()throws Exception{
        assertEquals(new Rect(Integer.MAX_VALUE-5,Integer.MAX_VALUE-7,Integer.MAX_VALUE,Integer.MAX_VALUE),physicalProduction(Integer.MAX_VALUE-5,Integer.MAX_VALUE-7,5,7));
        assertEquals(new Rect(Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE+5,Integer.MIN_VALUE+7),physicalProduction(Integer.MIN_VALUE,Integer.MIN_VALUE,5,7));
    }
    @Test public void physicalHitboxRejectsEmptyOrNegativeRealFrames()throws Exception{
        assertNull(physicalProduction(0,0,0,5));assertNull(physicalProduction(0,0,5,0));assertNull(physicalProduction(0,0,-1,5));assertNull(physicalProduction(0,0,5,-1));
    }
}
