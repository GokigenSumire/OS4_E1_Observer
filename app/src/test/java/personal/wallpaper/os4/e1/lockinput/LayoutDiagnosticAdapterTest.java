package personal.wallpaper.os4.e1.lockinput;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.FrameLayout;
import com.android.systemui.shade.NotificationShadeWindowView;
import com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainer;
import com.miui.systemui.shade.ShadeBackgroundView;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** 真实Android宿主的生产递归与固定诊断；不执行OEM或冒手机布局结论。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
public final class LayoutDiagnosticAdapterTest {
    private Activity activity;
    private Context context;
    private NotificationShadeWindowView root;
    private SharedNotificationContainer top;
    private Method detailed,legacy,denied;
    private Constructor<?> captureConstructor;
    private Field captureCode;
    private final Map<String,Integer> ids=new LinkedHashMap<>();
    @Before public void setUp()throws Exception{
        int next=0x7f061100;
        for(String name:new String[]{"shared_notification_container","shade_background","scrim_in_front",
                "miui_keyguard_face_unlock_view_keygurd_container","notification_stack_scroller"})ids.put(name,next++);
        activity=Robolectric.buildActivity(Activity.class).setup().get();Resources original=activity.getResources();
        Resources resources=new Resources(original.getAssets(),original.getDisplayMetrics(),original.getConfiguration()){
            @Override public int getIdentifier(String name,String type,String pkg){
                if("id".equals(type)&&"com.android.systemui".equals(pkg)&&ids.containsKey(name))return ids.get(name);
                return super.getIdentifier(name,type,pkg);
            }
        };
        context=new ContextWrapper(activity){@Override public Resources getResources(){return resources;}};
        root=new NotificationShadeWindowView(context);activity.setContentView(root);
        top=new SharedNotificationContainer(context);top.setId(id("shared_notification_container"));root.addView(top,new FrameLayout.LayoutParams(-1,-1));
        Class<?> capture=Class.forName(LockSurfaceSnapshot.class.getName()+"$LayoutCapture");
        captureConstructor=capture.getDeclaredConstructor();captureConstructor.setAccessible(true);
        captureCode=capture.getDeclaredField("code");captureCode.setAccessible(true);
        detailed=LockSurfaceSnapshot.class.getDeclaredMethod("foreground",View.class,View.class,View.class,List.class,int[].class,capture,View.class);detailed.setAccessible(true);
        legacy=LockSurfaceSnapshot.class.getDeclaredMethod("foreground",View.class,View.class,View.class,List.class,int[].class);legacy.setAccessible(true);
        denied=LockSurfaceSnapshot.class.getDeclaredMethod("denied",String.class,capture);denied.setAccessible(true);
        layout();
    }
    private int id(String name){return ids.get(name);}
    private void layout(){
        root.forceLayout();root.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY));root.layout(0,0,1000,2000);
    }
    private View leaf(){View view=new View(context);top.addView(view,new FrameLayout.LayoutParams(200,100));layout();return view;}
    private static final class Outcome {
        final boolean allowed;final int code,budget;final List<Rect> exclusions;
        Outcome(boolean allowed,int code,int budget,List<Rect> exclusions){this.allowed=allowed;this.code=code;this.budget=budget;this.exclusions=exclusions;}
    }
    private Outcome walk(View view,int initial)throws Exception{return walk(root,top,view,initial);}
    private Outcome walk(View rootValue,View topValue,View view,int initial)throws Exception{
        Object capture=captureConstructor.newInstance();ArrayList<Rect> excluded=new ArrayList<>();int[] budget={initial};
        boolean allowed=(Boolean)detailed.invoke(null,rootValue,view,rootValue,excluded,budget,capture,topValue);
        return new Outcome(allowed,captureCode.getInt(capture),budget[0],excluded);
    }
    private void assertWhy(Outcome result,int why){assertFalse(result.allowed);assertTrue(LayoutDiagnosticDetail.valid(result.code));assertEquals(why,LayoutDiagnosticDetail.why(result.code));}
    private void assertLegacySame(View view,int initial)throws Exception{
        Outcome result=walk(view,initial);ArrayList<Rect> exclusions=new ArrayList<>();int[] budget={initial};
        assertEquals(result.allowed,(Boolean)legacy.invoke(null,root,view,root,exclusions,budget));
        assertEquals(result.budget,budget[0]);assertEquals(result.exclusions,exclusions);
    }
    @Test public void stableTreeHasNoDetailAndSameExclusionsAsLegacy()throws Exception{
        View view=leaf();Outcome result=walk(top,0);assertTrue(result.allowed);assertEquals(0,result.code);assertEquals(2,result.budget);assertEquals(1,result.exclusions.size());assertLegacySame(top,0);
    }
    @Test public void budget512IsAllowedAnd513RejectedBeforeVisibility()throws Exception{
        View view=leaf();Outcome exact=walk(view,511);assertTrue(exact.allowed);assertEquals(512,exact.budget);
        view.setVisibility(View.GONE);Outcome over=walk(view,512);assertWhy(over,LayoutDiagnosticDetail.NODE_LIMIT);assertEquals(513,over.budget);assertLegacySame(view,512);
    }
    @Test public void budgetIsSharedWithPreviouslyTraversedBackground()throws Exception{
        View view=leaf();Object capture=captureConstructor.newInstance();ArrayList<Rect> excluded=new ArrayList<>();int[] budget={511};
        assertTrue((Boolean)detailed.invoke(null,root,view,root,excluded,budget,capture,top));
        assertFalse((Boolean)detailed.invoke(null,root,top,root,excluded,budget,capture,top));
        assertEquals(513,budget[0]);assertEquals(LayoutDiagnosticDetail.NODE_LIMIT,LayoutDiagnosticDetail.why(captureCode.getInt(capture)));
    }
    @Test public void group128ChildrenPassAnd129Reject()throws Exception{
        for(int i=0;i<128;i++)top.addView(new View(context),new FrameLayout.LayoutParams(1,1));layout();
        assertTrue(walk(top,0).allowed);top.addView(new View(context),new FrameLayout.LayoutParams(1,1));layout();
        Outcome result=walk(top,0);assertWhy(result,LayoutDiagnosticDetail.CHILD_LIMIT);assertEquals(LayoutDiagnosticDetail.SHARED,LayoutDiagnosticDetail.top(result.code));assertEquals(LayoutDiagnosticDetail.SHARED,LayoutDiagnosticDetail.node(result.code));assertLegacySame(top,0);
    }
    @Test public void goneAncestorDoesNotVisitHiddenPendingChild()throws Exception{assertPruned(View.GONE,1f);}
    @Test public void invisibleAncestorDoesNotVisitHiddenPendingChild()throws Exception{assertPruned(View.INVISIBLE,1f);}
    @Test public void zeroAlphaAncestorDoesNotVisitPendingChild()throws Exception{assertPruned(View.VISIBLE,0f);}
    private void assertPruned(int visibility,float alpha)throws Exception{
        View child=leaf();child.requestLayout();top.setVisibility(visibility);top.setAlpha(alpha);
        Outcome result=walk(top,0);assertTrue(result.allowed);assertEquals(0,result.code);assertEquals(1,result.budget);assertLegacySame(top,0);
    }
    @Test public void pendingLayoutRejectsAndCompletedLayoutRecovers()throws Exception{
        View view=leaf();view.requestLayout();assertTrue(view.isLayoutRequested());
        assertWhy(walk(view,0),LayoutDiagnosticDetail.LAYOUT_REQ);assertLegacySame(view,0);
        layout();assertFalse(view.isLayoutRequested());Outcome result=walk(view,0);assertTrue(result.allowed);assertEquals(0,result.code);
    }
    @Test public void unstartedAnimationStillRejects()throws Exception{
        View view=leaf();AlphaAnimation animation=new AlphaAnimation(0,1);view.setAnimation(animation);assertFalse(animation.hasStarted());
        assertWhy(walk(view,0),LayoutDiagnosticDetail.ANIM_NEW);assertLegacySame(view,0);
    }
    @Test public void activeAnimationStillRejects()throws Exception{
        View view=leaf();AlphaAnimation animation=new AlphaAnimation(0,1);animation.setDuration(100);animation.setStartTime(0);animation.getTransformation(1,new Transformation());
        assertTrue(animation.hasStarted());assertFalse(animation.hasEnded());view.setAnimation(animation);
        // setAnimation会reset Animation；再次用纯测试时钟推进，不改生产对象。
        animation.setStartTime(0);animation.getTransformation(1,new Transformation());
        assertWhy(walk(view,0),LayoutDiagnosticDetail.ANIM_ACTIVE);assertLegacySame(view,0);
    }
    @Test public void endedAnimationReferenceStillRejects()throws Exception{assertEnded(false,LayoutDiagnosticDetail.ANIM_ENDED);}
    @Test public void endedFillAfterAnimationReferenceStillRejects()throws Exception{assertEnded(true,LayoutDiagnosticDetail.ANIM_ENDED_FILL);}
    private void assertEnded(boolean fillAfter,int expected)throws Exception{
        View view=leaf();AlphaAnimation animation=new AlphaAnimation(0,1);animation.setDuration(1);animation.setFillAfter(fillAfter);view.setAnimation(animation);
        animation.setStartTime(0);animation.getTransformation(0,new Transformation());animation.getTransformation(2,new Transformation());assertTrue(animation.hasEnded());assertSame(animation,view.getAnimation());
        assertWhy(walk(view,0),expected);assertLegacySame(view,0);
    }
    @Test public void animationDetailAccessorFailureCannotTurnRejectionIntoAllow()throws Exception{
        View view=leaf();view.setAnimation(new Animation(){@Override public boolean hasEnded(){throw new IllegalStateException("private unexpected text");}});
        Outcome result=walk(view,0);assertWhy(result,LayoutDiagnosticDetail.UNKNOWN);assertFalse(LayoutDiagnosticDetail.label(result.code).contains("private"));assertLegacySame(view,0);
    }
    @Test public void animationPrecedesLayoutRequestAsBefore()throws Exception{
        View view=leaf();view.setAnimation(new AlphaAnimation(0,1));view.requestLayout();assertTrue(view.isLayoutRequested());
        assertWhy(walk(view,0),LayoutDiagnosticDetail.ANIM_NEW);assertLegacySame(view,0);
    }
    @Test public void firstNestedFailureKeepsTopAndDoesNotScanLaterChildren()throws Exception{
        FrameLayout nested=new FrameLayout(context);View child=new View(context);nested.addView(child,new FrameLayout.LayoutParams(100,100));top.addView(nested,new FrameLayout.LayoutParams(300,300));View later=leaf();layout();child.requestLayout();later.setAnimation(new AlphaAnimation(0,1));
        layout();child.setAnimation(new AlphaAnimation(0,1));
        Outcome result=walk(top,0);assertWhy(result,LayoutDiagnosticDetail.ANIM_NEW);assertEquals(LayoutDiagnosticDetail.SHARED,LayoutDiagnosticDetail.top(result.code));assertEquals(LayoutDiagnosticDetail.OTHER,LayoutDiagnosticDetail.node(result.code));assertEquals(3,result.budget);
        assertLegacySame(top,0);
    }
    @Test public void knownNodeUsesActualResourceClassAndObject()throws Exception{
        ShadeBackgroundView shade=new ShadeBackgroundView(context);shade.setId(id("shade_background"));top.addView(shade,new FrameLayout.LayoutParams(-1,-1));top.injector.shadeBackground=shade;layout();shade.requestLayout();
        Outcome result=walk(shade,0);assertWhy(result,LayoutDiagnosticDetail.LAYOUT_REQ);assertEquals(LayoutDiagnosticDetail.SHARED,LayoutDiagnosticDetail.top(result.code));assertEquals(LayoutDiagnosticDetail.SHADE,LayoutDiagnosticDetail.node(result.code));
    }
    @Test public void wrongClassWithKnownIdIsAlwaysOther()throws Exception{
        View view=leaf();view.setId(id("shade_background"));view.requestLayout();Outcome result=walk(view,0);
        assertWhy(result,LayoutDiagnosticDetail.LAYOUT_REQ);assertEquals(LayoutDiagnosticDetail.OTHER,LayoutDiagnosticDetail.node(result.code));
    }
    @Test public void duplicateKnownIdCannotNameDifferentObject()throws Exception{
        SharedNotificationContainer duplicate=new SharedNotificationContainer(context);duplicate.setId(top.getId());root.addView(duplicate,new FrameLayout.LayoutParams(-1,-1));layout();duplicate.requestLayout();
        Outcome result=walk(root,duplicate,duplicate,0);assertWhy(result,LayoutDiagnosticDetail.LAYOUT_REQ);assertEquals(LayoutDiagnosticDetail.OTHER,LayoutDiagnosticDetail.top(result.code));assertEquals(LayoutDiagnosticDetail.OTHER,LayoutDiagnosticDetail.node(result.code));
    }
    @Test public void nestedDuplicateShadeNeedsInjectorDoubleObjectBinding()throws Exception{
        ShadeBackgroundView direct=new ShadeBackgroundView(context);direct.setId(id("shade_background"));root.addView(direct,0,new FrameLayout.LayoutParams(-1,-1));
        ShadeBackgroundView nested=new ShadeBackgroundView(context);nested.setId(id("shade_background"));top.addView(nested,new FrameLayout.LayoutParams(-1,-1));top.injector.shadeBackground=nested;layout();nested.requestLayout();
        Outcome correct=walk(nested,0);assertEquals(LayoutDiagnosticDetail.SHADE,LayoutDiagnosticDetail.node(correct.code));
        top.injector.shadeBackground=direct;assertEquals(LayoutDiagnosticDetail.OTHER,LayoutDiagnosticDetail.node(walk(nested,0).code));
        top.injector.shadeBackground=nested;top.injector.container=new SharedNotificationContainer(context);assertEquals(LayoutDiagnosticDetail.OTHER,LayoutDiagnosticDetail.node(walk(nested,0).code));
    }
    @Test public void differentDeclaringLoaderIsAlwaysOther()throws Exception{
        String className=NotificationShadeWindowView.class.getName();String resource="/"+className.replace('.','/')+".class";byte[] bytes;
        try(InputStream stream=NotificationShadeWindowView.class.getResourceAsStream(resource)){assertNotNull(stream);bytes=stream.readAllBytes();}
        final byte[] definition=bytes;
        ClassLoader isolated=new ClassLoader(NotificationShadeWindowView.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!name.equals(className))return super.loadClass(name,resolve);
                Class<?> result=findLoadedClass(name);if(result==null)result=defineClass(name,definition,0,definition.length);if(resolve)resolveClass(result);return result;
            }
        };
        ViewGroup different=(ViewGroup)isolated.loadClass(className).getConstructor(Context.class).newInstance(context);root.removeView(top);different.addView(top);activity.setContentView(different);
        different.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY));different.layout(0,0,1000,2000);top.requestLayout();
        assertNotSame(different.getClass().getClassLoader(),top.getClass().getClassLoader());Outcome result=walk(different,top,top,0);assertWhy(result,LayoutDiagnosticDetail.LAYOUT_REQ);assertEquals(LayoutDiagnosticDetail.OTHER,LayoutDiagnosticDetail.top(result.code));assertEquals(LayoutDiagnosticDetail.OTHER,LayoutDiagnosticDetail.node(result.code));
    }
    @Test public void detailedDenialPreservesOriginalCoarseReasonAndSafeEmptyRegion()throws Exception{
        Object capture=captureConstructor.newInstance();int code=LayoutDiagnosticDetail.code(LayoutDiagnosticDetail.LAYOUT_REQ,LayoutDiagnosticDetail.SHARED,LayoutDiagnosticDetail.OTHER);captureCode.setInt(capture,code);
        LockSurfaceSnapshot result=(LockSurfaceSnapshot)denied.invoke(null,"FOREGROUND_LAYOUT_UNKNOWN",capture);
        assertFalse(result.allowed);assertEquals("FOREGROUND_LAYOUT_UNKNOWN",result.reason);assertEquals(code,result.layoutDetailCode);assertNull(result.background);assertTrue(result.exclusions.isEmpty());assertFalse(result.contains(100,100));assertFalse(result.sameLayout(result));
    }
    @Test public void ordinaryDenialNeverInventsLayoutDetail(){LockSurfaceSnapshot result=LockSurfaceSnapshot.denied("BOUNCER_SHOWING");assertEquals(0,result.layoutDetailCode);assertFalse(result.allowed);assertEquals("BOUNCER_SHOWING",result.reason);}
    @Test public void diagnosticOffNeverReadsAdditionalAnimationProperties()throws Exception{
        View view=leaf();final int[] extra={0};view.setAnimation(new Animation(){
            @Override public boolean hasEnded(){extra[0]++;throw new IllegalStateException("not allowed off");}
            @Override public boolean hasStarted(){extra[0]++;return false;}
            @Override public boolean getFillAfter(){extra[0]++;return false;}
        });
        ArrayList<Rect> exclusions=new ArrayList<>();assertFalse((Boolean)legacy.invoke(null,root,view,root,exclusions,new int[]{0}));assertEquals(0,extra[0]);
        Outcome on=walk(view,0);assertWhy(on,LayoutDiagnosticDetail.UNKNOWN);assertEquals(1,extra[0]);
    }
    @Test public void defaultSnapshotReadUsesDiagnosticOffAndKeepsRootRejection(){
        LockSurfaceSnapshot old=LockSurfaceSnapshot.read(null),off=LockSurfaceSnapshot.read(null,false),on=LockSurfaceSnapshot.read(null,true);
        assertEquals("ROOT_NOT_ATTACHED_PRIMARY",old.reason);assertEquals(old.reason,off.reason);assertEquals(old.reason,on.reason);
        assertFalse(old.allowed);assertFalse(off.allowed);assertFalse(on.allowed);assertEquals(0,old.layoutDetailCode);assertEquals(0,off.layoutDetailCode);assertEquals(0,on.layoutDetailCode);
    }
}
