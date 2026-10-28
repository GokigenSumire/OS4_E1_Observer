package personal.wallpaper.os4.e1.lockinput;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.FrameLayout;
import com.android.systemui.shade.NotificationShadeWindowView;
import com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE,qualifiers="w1200dp-h2400dp-mdpi")
public final class ForceLocalAdapterTest {
    private static final int STATUS=0x7f071401;
    private Context context;
    private NotificationShadeWindowView root;
    private MiuiKeyguardStatusBarView status;
    private View pending;
    private Method walk;
    @Before public void create()throws Exception{
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();Resources original=activity.getResources();
        Resources resources=new Resources(original.getAssets(),original.getDisplayMetrics(),original.getConfiguration()){
            @Override public int getIdentifier(String name,String kind,String pkg){return "keyguard_status_bar".equals(name)&&"id".equals(kind)&&"com.android.systemui".equals(pkg)?STATUS:super.getIdentifier(name,kind,pkg);}
        };
        context=new ContextWrapper(activity){@Override public Resources getResources(){return resources;}};
        root=new NotificationShadeWindowView(context);activity.setContentView(root);
        status=new MiuiKeyguardStatusBarView(context);status.setId(STATUS);root.addView(status,new FrameLayout.LayoutParams(1000,80));
        pending=new View(context){@Override public boolean isLayoutRequested(){return true;}};
        status.addView(pending,new FrameLayout.LayoutParams(0,0));layout();
        Class<?> capture=Class.forName(LockSurfaceSnapshot.class.getName()+"$LayoutCapture");
        walk=LockSurfaceSnapshot.class.getDeclaredMethod("foreground",View.class,View.class,View.class,List.class,int[].class,capture,View.class,Rect.class,boolean.class);walk.setAccessible(true);
    }
    private void layout(){root.forceLayout();root.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY));root.layout(0,0,1000,2000);}
    private boolean traverse(View view,boolean force,List<Rect> excluded,int[] budget)throws Exception{return (Boolean)walk.invoke(null,root,view,root,excluded,budget,null,view,new Rect(48,400,952,1952),force);}
    @Test public void confirmedForceSkipsOnlyExactStatusPendingDescendant()throws Exception{
        assertFalse(traverse(status,false,new ArrayList<>(),new int[]{0}));
        List<Rect> holes=new ArrayList<>();int[] budget={0};assertTrue(traverse(status,true,holes,budget));assertEquals(1,budget[0]);assertEquals(1,holes.size());assertTrue(pending.isLayoutRequested());
    }
    @Test public void forceDoesNotRequireHardClipOrProbePrivateSubtreeState()throws Exception{
        status.setClipBounds(null);assertTrue(traverse(status,true,new ArrayList<>(),new int[]{0}));assertFalse(traverse(status,false,new ArrayList<>(),new int[]{0}));
    }
    @Test public void forceExcludesWholeParentNotSmallerClip()throws Exception{
        status.setClipBounds(new Rect(0,0,1000,10));List<Rect> holes=new ArrayList<>();assertTrue(traverse(status,true,holes,new int[]{0}));assertEquals(1000,holes.get(0).width());assertEquals(80,holes.get(0).height());
    }
    @Test public void statusOwnPendingLayoutStillRejects()throws Exception{status.requestLayout();assertTrue(status.isLayoutRequested());assertFalse(traverse(status,true,new ArrayList<>(),new int[]{0}));}
    @Test public void statusOwnAnimationStillRejects()throws Exception{status.setAnimation(new AlphaAnimation(0,1));assertFalse(traverse(status,true,new ArrayList<>(),new int[]{0}));}
    @Test public void otherForegroundPendingLayoutIsNotSkipped()throws Exception{FrameLayout other=new FrameLayout(context);root.addView(other);status.removeView(pending);other.addView(pending);layout();pending.requestLayout();assertFalse(traverse(other,true,new ArrayList<>(),new int[]{0}));}
    @Test public void wrongClassWithSameIdCannotBorrowStatusExemption()throws Exception{
        root.removeView(status);FrameLayout fake=new FrameLayout(context);fake.setId(STATUS);root.addView(fake,new FrameLayout.LayoutParams(1000,80));fake.addView(new View(context){@Override public boolean isLayoutRequested(){return true;}});layout();assertNull(ForceLocalRegion.statusExclusion(root,fake));assertFalse(traverse(fake,true,new ArrayList<>(),new int[]{0}));
    }
    @Test public void duplicateResourceCannotNameAnotherInstance()throws Exception{MiuiKeyguardStatusBarView duplicate=new MiuiKeyguardStatusBarView(context);duplicate.setId(STATUS);root.addView(duplicate,new FrameLayout.LayoutParams(1000,80));layout();assertNull(ForceLocalRegion.statusExclusion(root,duplicate));}
    @Test public void nestedStatusIsNotDirectRootStatus(){root.removeView(status);FrameLayout wrapper=new FrameLayout(context);root.addView(wrapper);wrapper.addView(status);layout();assertNull(ForceLocalRegion.statusExclusion(root,status));}
    @Test public void forcedPathKeepsOriginalNodeBudget()throws Exception{assertFalse(traverse(status,true,new ArrayList<>(),new int[]{512}));}
    @Test public void statusPhysicalOverflowOrOutsideRootDoesNotCreateHole(){status.layout(0,1900,1000,2100);assertNull(ForceLocalRegion.statusExclusion(root,status));}
    @Test public void topTwentyPercentIsExcludedIncludingFractionalPixel(){Rect area=new Rect(48,48,952,1952);assertTrue(ForceLocalRegion.constrain(area,2001));assertEquals(401,area.top);BackgroundRegion region=new BackgroundRegion(new BackgroundRegion.Box(area.left,area.top,area.right,area.bottom),List.of());assertFalse(region.contains(500,400.99f));assertTrue(region.contains(500,401));}
    @Test public void topBanDoesNotExpandAlreadySmallerSafeRegion(){Rect area=new Rect(100,600,900,1700);assertTrue(ForceLocalRegion.constrain(area,2000));assertEquals(new Rect(100,600,900,1700),area);}
    @Test public void areaEntirelyInsideTopBanIsRejected(){assertFalse(ForceLocalRegion.constrain(new Rect(10,10,300,400),2000));assertFalse(ForceLocalRegion.constrain(null,2000));}
    @Test public void modeChangeInvalidatesLayoutEvenWithSameGeometry()throws Exception{
        var constructor=LockSurfaceSnapshot.class.getDeclaredConstructor(boolean.class,String.class,Rect.class,List.class,int.class,int.class,boolean.class);constructor.setAccessible(true);
        LockSurfaceSnapshot old=(LockSurfaceSnapshot)constructor.newInstance(true,"ALLOW_BACKGROUND",new Rect(0,500,1000,1900),List.of(),1,0,false);
        LockSurfaceSnapshot forced=(LockSurfaceSnapshot)constructor.newInstance(true,"ALLOW_BACKGROUND",new Rect(0,500,1000,1900),List.of(),1,0,true);
        assertFalse(old.sameLayout(forced));assertTrue(forced.sameLayout(forced));
    }
}
