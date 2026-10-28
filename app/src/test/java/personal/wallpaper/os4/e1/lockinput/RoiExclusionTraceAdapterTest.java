package personal.wallpaper.os4.e1.lockinput;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;
import com.android.systemui.shade.NotificationShadeWindowView;
import com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainer;
import com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

/** 实际View/Rect与生产递归，不执行OEM代码或读取手机。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE,qualifiers="w1200dp-h2400dp-mdpi")
public final class RoiExclusionTraceAdapterTest {
    private static final int SHARED=0x7f075010,STATUS=0x7f075011;
    private Context context;
    private NotificationShadeWindowView root;
    private SharedNotificationContainer top;
    private Method walk,classify;
    private final Rect area=new Rect(48,400,952,1952);
    @Before public void before()throws Exception{
        ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup();Activity activity=controller.get();Resources original=activity.getResources();
        Resources resources=new Resources(original.getAssets(),original.getDisplayMetrics(),original.getConfiguration()){
            @Override public int getIdentifier(String name,String type,String pkg){if("id".equals(type)&&"com.android.systemui".equals(pkg)){if("shared_notification_container".equals(name))return SHARED;if("keyguard_status_bar".equals(name))return STATUS;}return 0;}
        };
        context=new ContextWrapper(activity){@Override public Resources getResources(){return resources;}};
        root=new NotificationShadeWindowView(context);activity.setContentView(root);
        top=new SharedNotificationContainer(context);top.setId(SHARED);root.addView(top,new FrameLayout.LayoutParams(1000,2000));
        Class<?> capture=Class.forName(LockSurfaceSnapshot.class.getName()+"$LayoutCapture");
        walk=LockSurfaceSnapshot.class.getDeclaredMethod("foreground",View.class,View.class,View.class,List.class,int[].class,capture,View.class,Rect.class,boolean.class);walk.setAccessible(true);
        classify=LockSurfaceSnapshot.class.getDeclaredMethod("layoutNode",View.class,View.class);classify.setAccessible(true);RoiTestWindow.show(controller);layout();
        Rect actual=new Rect();assertTrue(root.getGlobalVisibleRect(actual));assertTrue("宿主必须包含待测允许区",actual.contains(area));assertEquals(View.VISIBLE,root.getWindowVisibility());
    }
    private void layout(){root.forceLayout();root.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY));root.layout(0,0,1000,2000);}
    private ArrayList<Rect> trace(){return RoiExclusionTrace.create(true,root,area,true,node->(Integer)classify.invoke(null,root,node));}
    private boolean walk(View view,ArrayList<Rect> output,int[] budget,boolean force)throws Exception{return (Boolean)walk.invoke(null,root,view,root,output,budget,null,view,area,force);}
    private boolean has(int[] codes,int code){return Arrays.stream(codes).anyMatch(value->value==code);}
    private boolean hasLabel(int[] codes,String part){return Arrays.stream(codes).mapToObj(RoiDiagnosticDetail::label).anyMatch(value->value.contains(part));}
    @Test public void backgroundContainerIsAttributedWithoutChangingRealExclusionsOrBudget()throws Exception{
        top.setBackgroundColor(0xff000000);layout();ArrayList<Rect> plain=new ArrayList<>(),trace=trace();int[] first={0},second={0};
        assertTrue(walk(top,plain,first,false));assertTrue(walk(top,trace,second,false));assertEquals(plain,trace);assertArrayEquals(first,second);
        assertTrue(hasLabel(RoiExclusionTrace.emptyDetails(trace,true),"FULL_BACKGROUND_TOP_SHARED_NODE_SHARED"));
    }
    @Test public void interactiveContainerKeepsFullHitboxAndReasonPriority()throws Exception{
        top.setClickable(true);top.setBackgroundColor(0xff000000);layout();ArrayList<Rect> trace=trace();assertTrue(walk(top,trace,new int[]{0},false));assertTrue(hasLabel(RoiExclusionTrace.emptyDetails(trace,true),"FULL_INTERACTIVE_TOP_SHARED_NODE_SHARED"));assertEquals(1000,trace.get(0).width());assertEquals(2000,trace.get(0).height());
    }
    @Test public void ordinaryLeafKeepsOwningTopWhileUnknownNodeRemainsOther()throws Exception{
        View leaf=new View(context);top.addView(leaf,new FrameLayout.LayoutParams(-1,-1));layout();ArrayList<Rect> trace=trace();assertTrue(walk(top,trace,new int[]{0},false));assertTrue(hasLabel(RoiExclusionTrace.emptyDetails(trace,true),"FULL_LEAF_TOP_SHARED_NODE_OTHER"));
    }
    @Test public void wrongClassWithKnownIdCannotMasqueradeAsShared(){
        FrameLayout fake=new FrameLayout(context);fake.setId(SHARED);root.removeView(top);root.addView(fake);ArrayList<Rect> trace=trace();RoiExclusionTrace.context(trace,fake,fake);trace.add(new Rect(area));assertTrue(hasLabel(RoiExclusionTrace.emptyDetails(trace,true),"TOP_OTHER_NODE_OTHER"));
    }
    @Test public void duplicateKnownIdStillRequiresActualFirstObject(){
        SharedNotificationContainer duplicate=new SharedNotificationContainer(context);duplicate.setId(SHARED);root.addView(duplicate);ArrayList<Rect> trace=trace();RoiExclusionTrace.context(trace,duplicate,duplicate);trace.add(new Rect(area));assertTrue(hasLabel(RoiExclusionTrace.emptyDetails(trace,true),"TOP_OTHER_NODE_OTHER"));
    }
    @Test public void nestedKnownViewCannotPretendToBeRootDirectTop(){
        root.removeView(top);FrameLayout wrapper=new FrameLayout(context);root.addView(wrapper);wrapper.addView(top);ArrayList<Rect> trace=trace();RoiExclusionTrace.context(trace,top,top);trace.add(new Rect(area));assertTrue(hasLabel(RoiExclusionTrace.emptyDetails(trace,true),"TOP_OTHER_NODE_SHARED"));
    }
    @Test public void classifierExceptionCannotRemoveActualRectOrChangeTraversal()throws Exception{
        top.setBackgroundColor(0xff000000);layout();ArrayList<Rect> trace=RoiExclusionTrace.create(true,root,area,true,node->{throw new IllegalStateException("private/never/output");});assertTrue(walk(top,trace,new int[]{0},false));assertEquals(1,trace.size());int[] details=RoiExclusionTrace.emptyDetails(trace,true);assertTrue(has(details,RoiDiagnosticDetail.INCOMPLETE));assertFalse(hasLabel(details,"private"));
    }
    @Test public void disabledTraceIsPlainListAndNeverInvokesClassifier(){
        ArrayList<Rect> trace=RoiExclusionTrace.create(false,root,area,true,node->{throw new AssertionError();});assertEquals(ArrayList.class,trace.getClass());Rect value=new Rect(area);RoiExclusionTrace.context(trace,top,top);trace.add(value);assertSame(value,trace.get(0));
    }
    @Test public void failedOptionalCreationFallsBackToOrdinaryList(){ArrayList<Rect> trace=RoiExclusionTrace.create(true,root,null,true,node->0);assertEquals(ArrayList.class,trace.getClass());trace.add(new Rect(area));assertEquals(1,trace.size());assertTrue(has(RoiExclusionTrace.emptyDetails(trace,true),RoiDiagnosticDetail.INCOMPLETE));}
    @Test public void invalidClassifierCodeNamesOtherAndMarksIncomplete(){ArrayList<Rect> trace=RoiExclusionTrace.create(true,root,area,true,node->999);RoiExclusionTrace.context(trace,top,top);trace.add(new Rect(area));int[] details=RoiExclusionTrace.emptyDetails(trace,true);assertTrue(has(details,RoiDiagnosticDetail.INCOMPLETE));assertTrue(hasLabel(details,"TOP_OTHER_NODE_OTHER"));}
    @Test public void forcedStatusKeepsWholeParentExclusionAndOriginalAllowDecision()throws Exception{
        MiuiKeyguardStatusBarView status=new MiuiKeyguardStatusBarView(context);status.setId(STATUS);root.addView(status,new FrameLayout.LayoutParams(1000,80));View pending=new View(context){@Override public boolean isLayoutRequested(){return true;}};status.addView(pending,new FrameLayout.LayoutParams(0,0));layout();
        ArrayList<Rect> plain=new ArrayList<>(),trace=trace();assertTrue(walk(status,plain,new int[]{0},true));assertTrue(walk(status,trace,new int[]{0},true));assertEquals(plain,trace);assertEquals(80,trace.get(0).height());
        // 状态栏在允许区上方，不伪报为遮挡者；仍不把原父范围改为较小clip。
        assertFalse(hasLabel(RoiExclusionTrace.emptyDetails(trace,true),"STATUS_TOP_STATUS"));
    }
    @Test public void diagnosticOnDoesNotBypassOtherPendingLayout()throws Exception{
        View pending=new View(context){@Override public boolean isLayoutRequested(){return true;}};top.addView(pending);layout();assertFalse(walk(top,new ArrayList<>(),new int[]{0},true));assertFalse(walk(top,trace(),new int[]{0},true));
    }
    public static final class Sensor {public int sensorType=2;public Location getLocation(){return new Location();}}
    public static final class Location {public int sensorLocationX=500,sensorLocationY=1000,sensorRadius=10;}
    public static final class Monitor {public List<Sensor> mFingerprintSensorProperties=List.of(new Sensor());}
    @Test public void actualFingerprintAdderHasItsOwnSourceAndUnchangedRect()throws Exception{
        Method fingerprint=LockSurfaceSnapshot.class.getDeclaredMethod("fingerprintRegions",Object.class,List.class,Rect.class);fingerprint.setAccessible(true);ArrayList<Rect> trace=trace();assertEquals(Boolean.TRUE,fingerprint.invoke(null,new Monitor(),trace,new Rect(0,0,1000,2000)));assertEquals(new Rect(480,980,520,1020),trace.get(0));assertTrue(hasLabel(RoiExclusionTrace.emptyDetails(trace,true),"PART_FINGERPRINT_TOP_OTHER_NODE_OTHER"));
    }
    @Test public void snapshotDetailArraysAreDefensiveAndLegacyConstructorsStayEmpty()throws Exception{
        Constructor<?> modern=LockSurfaceSnapshot.class.getDeclaredConstructor(boolean.class,String.class,Rect.class,List.class,int.class,int.class,boolean.class,int[].class);modern.setAccessible(true);
        int[] input={RoiDiagnosticDetail.MODE_FORCE};LockSurfaceSnapshot snapshot=(LockSurfaceSnapshot)modern.newInstance(false,"POSITIVE_ROI_EMPTY_AFTER_EXCLUSIONS",null,List.of(),0,0,true,input);input[0]=0;int[] result=snapshot.roiDetails();assertEquals(RoiDiagnosticDetail.MODE_FORCE,result[0]);result[0]=0;assertEquals(RoiDiagnosticDetail.MODE_FORCE,snapshot.roiDetails()[0]);assertEquals(0,LockSurfaceSnapshot.denied("OLD").roiDetails().length);
    }
}
