package personal.wallpaper.os4.e1.lockinput;

import android.view.View;
import android.widget.FrameLayout;
import com.android.systemui.shade.NotificationShadeWindowView;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController;
import com.android.systemui.statusbar.notification.collection.NotifCollection;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE,qualifiers="w1200dp-h2400dp-mdpi")
public final class WallpaperPrioritySnapshotTest {
    private NotificationShadeWindowView root;
    private RoiSnapshotIntegrationTest.Controller controller;
    private NotificationStackScrollLayout stack;
    @Before public void before()throws Exception{
        RoiSnapshotIntegrationTest baseline=new RoiSnapshotIntegrationTest();baseline.before();
        Field rootField=RoiSnapshotIntegrationTest.class.getDeclaredField("root");rootField.setAccessible(true);root=(NotificationShadeWindowView)rootField.get(baseline);
        Field controllerField=RoiSnapshotIntegrationTest.class.getDeclaredField("controller");controllerField.setAccessible(true);controller=(RoiSnapshotIntegrationTest.Controller)controllerField.get(baseline);
        stack=new NotificationStackScrollLayout(root.getContext());stack.setId(0x7f077001);root.addView(stack,new FrameLayout.LayoutParams(-1,-1));
        NotificationStackScrollLayoutController control=new NotificationStackScrollLayoutController();control.mView=stack;control.mNotifCollection=new NotifCollection();stack.mController=control;layout();
    }
    private void layout(){root.forceLayout();root.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY));root.layout(0,0,1000,2000);}
    @Test public void fullScreenThemeLeafAndTransparentPanelNoLongerEraseAllowedArea(){
        View theme=new View(root.getContext());root.addView(theme,new FrameLayout.LayoutParams(-1,-1));layout();
        assertEquals("POSITIVE_ROI_EMPTY_AFTER_EXCLUSIONS",LockSurfaceSnapshot.read(root,true,true).reason);
        LockSurfaceSnapshot priority=LockSurfaceSnapshot.read(root,true,true,true);assertTrue(priority.reason,priority.allowed);assertTrue(priority.contains(700,1500));assertEquals(RoiDiagnosticDetail.MODE_PRIORITY,priority.roiDetails()[0]);
    }
    @Test public void actualNotificationCardProtectedWhileBlankStillAllowed(){
        ExpandableNotificationRow row=new ExpandableNotificationRow(root.getContext());FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(900,200);size.topMargin=700;stack.addView(row,size);((NotifCollection)((NotificationStackScrollLayoutController)stack.mController).mNotifCollection).mNotificationSet=Map.of("test",new Object());layout();
        LockSurfaceSnapshot result=LockSurfaceSnapshot.read(root,true,true,true);assertTrue(result.reason,result.allowed);int[] position=new int[2];row.getLocationOnScreen(position);assertFalse(result.contains(position[0]+300,position[1]+50));assertTrue(result.contains(700,1500));
    }
    @Test public void missingNotificationListDoesNotBecomeThemeException(){root.removeView(stack);layout();LockSurfaceSnapshot result=LockSurfaceSnapshot.read(root,true,true,true);assertFalse(result.allowed);assertEquals("PRIORITY_NOTIFICATION_IDENTITY",result.reason);}
    @Test public void realAuthenticationGuardStillRunsBeforePriority(){controller.mService.mKeyguardStateController.mPrimaryBouncerShowing=true;assertEquals("BOUNCER_SHOWING",LockSurfaceSnapshot.read(root,true,true,true).reason);}
    @Test public void topTwentyPercentAndFingerprintStayProtected(){LockSurfaceSnapshot result=LockSurfaceSnapshot.read(root,true,true,true);assertTrue(result.reason,result.allowed);assertFalse(result.contains(500,300));assertFalse(result.contains(500,1000));assertTrue(result.contains(700,1500));}
    @Test public void sameGeometryCannotContinueAcrossPriorityModeChange()throws Exception{
        var current=LockSurfaceSnapshot.read(root,true,true,true);assertTrue(current.reason,current.allowed);
        var constructor=LockSurfaceSnapshot.class.getDeclaredConstructor(boolean.class,String.class,android.graphics.Rect.class,java.util.List.class,int.class,int.class,boolean.class,int[].class);constructor.setAccessible(true);
        LockSurfaceSnapshot old=(LockSurfaceSnapshot)constructor.newInstance(true,"ALLOW_BACKGROUND",current.background,current.exclusions,current.layoutKey,0,true,new int[0]);assertFalse(current.sameLayout(old));
    }
    @Test public void disablingExtraModeRetainsOldLocalPolicy(){assertEquals("POSITIVE_ROI_EMPTY_AFTER_EXCLUSIONS",LockSurfaceSnapshot.read(root,false,true,false).reason);}
    @Test public void inconsistentModeCannotTurnOffTopBan(){assertEquals("PRIORITY_MODE_INCONSISTENT",LockSurfaceSnapshot.read(root,true,false,true).reason);}
}
