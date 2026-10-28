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
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController;
import com.android.systemui.statusbar.notification.collection.NotifCollection;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Android真实View区域与固定通知属性；不执行OEM通知代码或读取通知内容。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE,qualifiers="w1200dp-h2400dp-mdpi")
public final class PriorityNotificationRegionsTest {
    private static final int STACK=0x7f077001;
    private Context context;
    private NotificationShadeWindowView root;
    private NotificationStackScrollLayout stack;
    private NotificationStackScrollLayoutController controller;
    private NotifCollection collection;
    private ExpandableNotificationRow row;
    @Before public void before()throws Exception{
        ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup();Resources original=activity.get().getResources();
        Resources resources=new Resources(original.getAssets(),original.getDisplayMetrics(),original.getConfiguration()){
            @Override public int getIdentifier(String name,String type,String pkg){if("id".equals(type)&&"com.android.systemui".equals(pkg)){if("notification_stack_scroller".equals(name))return STACK;if("media_carousel_scroller".equals(name))return 0x7f077002;if("tiny_media_session_container".equals(name))return 0x7f077003;if("keyguard_shortcut_container".equals(name))return 0x7f077004;if("custom_lockscreen_button".equals(name))return 0x7f077005;if("emergency_call_button".equals(name))return 0x7f077006;if("miui_tiny_keyguard_camera_view".equals(name))return 0x7f077007;}return 0;}
        };
        context=new ContextWrapper(activity.get()){@Override public Resources getResources(){return resources;}};
        root=new NotificationShadeWindowView(context);activity.get().setContentView(root);
        stack=new NotificationStackScrollLayout(context);stack.setId(STACK);root.addView(stack,new FrameLayout.LayoutParams(-1,-1));
        controller=new NotificationStackScrollLayoutController();controller.mView=stack;collection=new NotifCollection();controller.mNotifCollection=collection;stack.mController=controller;
        row=new ExpandableNotificationRow(context);FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(900,200);size.topMargin=700;size.leftMargin=50;stack.addView(row,size);collection.mNotificationSet=Map.of("test",new Object());
        RoiTestWindow.show(activity);layout();
    }
    private void layout(){root.forceLayout();root.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY));root.layout(0,0,1000,2000);}
    private void reason(String expected){PriorityNotificationRegions.Result result=PriorityNotificationRegions.read(root);assertFalse(result.reason,result.allowed);assertEquals(expected,result.reason);assertTrue(result.areas().isEmpty());}
    private Rect physical(View view){int[] xy=new int[2];view.getLocationOnScreen(xy);return new Rect(xy[0],xy[1],xy[0]+view.getWidth(),xy[1]+view.getHeight());}
    @Test public void realCardIsProtectedButBlankAboveAndBelowIsNotWholeStack(){var result=PriorityNotificationRegions.read(root);assertTrue(result.reason,result.allowed);assertEquals(1,result.areas().size());assertEquals(physical(row),result.areas().get(0));assertFalse(result.areas().get(0).contains(500,1500));}
    @Test public void unknownThemeFullScreenLeafDoesNotBecomeNotificationCard(){View theme=new View(context);root.addView(theme,new FrameLayout.LayoutParams(-1,-1));layout();var result=PriorityNotificationRegions.read(root);assertTrue(result.reason,result.allowed);assertEquals(List.of(physical(row)),result.areas());}
    @Test public void controllerMustPointBackToExactCurrentStack(){controller.mView=new View(context);reason("PRIORITY_NOTIFICATION_IDENTITY");}
    @Test public void wrongControllerClassCannotBePinnedByFields(){stack.mController=new Object();reason("PRIORITY_NOTIFICATION_IDENTITY");}
    @Test public void missingCollectionDoesNotInventEmptyNotifications(){controller.mNotifCollection=null;reason("PRIORITY_NOTIFICATION_COLLECTION");}
    @Test public void existingCollectionWithoutVisibleRowsFailsClosed(){row.setVisibility(View.GONE);layout();reason("PRIORITY_NOTIFICATION_UNACCOUNTED");}
    @Test public void zeroEntriesMayUseConfirmedEmptyLayout(){stack.removeAllViews();collection.mNotificationSet=Map.of();layout();var result=PriorityNotificationRegions.read(root);assertTrue(result.reason,result.allowed);assertTrue(result.areas().isEmpty());}
    @Test public void hiddenStackWithKnownNotificationsFailsClosed(){stack.setVisibility(View.INVISIBLE);layout();reason("PRIORITY_NOTIFICATION_UNACCOUNTED");}
    @Test public void stackAnimationAndQueuedRemovalReject(){stack.mAnimationRunning=true;reason("PRIORITY_NOTIFICATION_TRANSITION");stack.mAnimationRunning=false;stack.mChildrenToRemoveAnimated=List.of(row);reason("PRIORITY_NOTIFICATION_TRANSITION");}
    @Test public void anyActualCardPendingLayoutRejects(){row.requestLayout();assertTrue(row.isLayoutRequested());assertTrue(root.isLayoutRequested());reason("PRIORITY_ROOT_UNSTABLE");}
    @Test public void viewAnimationRejectsNotificationMovement(){row.setAnimation(new AlphaAnimation(0,1));reason("PRIORITY_NOTIFICATION_LAYOUT");}
    @Test public void clippedCardStillProtectsWholePhysicalParent(){row.setClipBounds(new Rect(0,0,20,20));var result=PriorityNotificationRegions.read(root);assertTrue(result.reason,result.allowed);assertEquals(physical(row),result.areas().get(0));}
    @Test public void scaledCardNotMisreadAsUnscaledSafeArea(){row.setScaleX(0.8f);reason("PRIORITY_NOTIFICATION_LAYOUT");}
    @Test public void translatedStableCardUsesItsRealLocation(){row.setTranslationY(60);var result=PriorityNotificationRegions.read(root);assertTrue(result.reason,result.allowed);assertEquals(physical(row),result.areas().get(0));}
    @Test public void unknownVisibleItemIsStillProtectedAlongsideNotification(){View other=new View(context);FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(900,180);size.topMargin=1100;stack.addView(other,size);layout();var result=PriorityNotificationRegions.read(root);assertTrue(result.reason,result.allowed);assertTrue(result.areas().contains(physical(other)));}
    @Test public void unknownItemsCannotSubstituteForRealNotificationWhenCollectionNonempty(){stack.removeView(row);stack.addView(new View(context),new FrameLayout.LayoutParams(400,100));layout();reason("PRIORITY_NOTIFICATION_UNACCOUNTED");}
    @Test public void duplicateStackResourceOutsideProtectedCardsRejects(){View duplicate=new View(context);duplicate.setId(STACK);root.addView(duplicate);layout();reason("PRIORITY_NOTIFICATION_IDENTITY");}
    @Test public void wrongKnownMediaClassIsNotTreatedAsAbsent(){View media=new View(context);media.setId(0x7f077002);root.addView(media,new FrameLayout.LayoutParams(400,100));layout();reason("PRIORITY_MEDIA_IDENTITY");}
    @Test public void unknownPropertiesAreExplicitlyRejected(){stack.mChildrenToAddAnimated=null;reason("PRIORITY_NOTIFICATION_TRANSITION");}
    @Test public void returnedRegionCopiesCannotMutateProtection(){var result=PriorityNotificationRegions.read(root);assertTrue(result.allowed);Rect expected=physical(row);result.areas().get(0).setEmpty();assertEquals(expected,result.areas().get(0));}
    @Test public void childBudgetRemainsBounded(){for(int i=0;i<128;i++)stack.addView(new View(context),new FrameLayout.LayoutParams(1,1));layout();reason("PRIORITY_NOTIFICATION_LAYOUT");}
    @Test public void systemShortcutRegionIsProtectedAndNotTreatedAsTheme(){FrameLayout shortcuts=new FrameLayout(context);shortcuts.setId(0x7f077004);FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(300,180);size.topMargin=1600;root.addView(shortcuts,size);layout();var result=PriorityNotificationRegions.read(root);assertTrue(result.reason,result.allowed);assertTrue(result.areas().contains(physical(shortcuts)));}
    @Test public void wrongClassShortcutCannotPretendItsBoundsAreKnown(){View fake=new View(context);fake.setId(0x7f077004);root.addView(fake,new FrameLayout.LayoutParams(100,100));layout();reason("PRIORITY_CONTROL_IDENTITY");}
    @Test public void zeroSizedEmptyKnownShortcutContainerHasNoTouchRegion(){FrameLayout empty=new FrameLayout(context);empty.setId(0x7f077004);root.addView(empty,new FrameLayout.LayoutParams(0,0));layout();var result=PriorityNotificationRegions.read(root);assertTrue(result.reason,result.allowed);assertEquals(1,result.areas().size());}
    @Test public void offscreenCardStillKeepsPhysicalBoundsInsteadOfFalseGeometryFailure(){FrameLayout.LayoutParams size=(FrameLayout.LayoutParams)row.getLayoutParams();size.topMargin=3000;row.setLayoutParams(size);layout();assertFalse(row.getGlobalVisibleRect(new Rect()));var result=PriorityNotificationRegions.read(root);assertTrue(result.reason,result.allowed);assertEquals(physical(row),result.areas().get(0));}
}
