package personal.wallpaper.os4.e1.lockinput;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Insets;
import android.os.PowerManager;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import com.android.keyguard.widget.HyperOSKeyguardRootView;
import com.android.systemui.shade.NotificationShadeWindowView;
import com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainer;
import com.miui.systemui.controlcenter.ControlCenterImpl;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPowerManager;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

/** 完整生产read链：真实Android布局＋可注入安全属性，不伪OEM/手机执行。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE,qualifiers="w1200dp-h2400dp-mdpi")
public final class RoiSnapshotIntegrationTest {
    private NotificationShadeWindowView root;
    private SharedNotificationContainer foreground;
    private Controller controller;
    public static final class Flow {public Object getValue(){return false;}}
    public static final class Alternate {public boolean isVisibleState(){return false;}}
    public static final class Primary {public Flow isShowing=new Flow(),isShowingSoon=new Flow();}
    public static final class Keyguard {
        public boolean mShowing=true,mPrimaryBouncerShowing,mOccluded,mKeyguardGoingAway,mKeyguardFadingAway,mMiKeyguardFadingAway,
                mLaunchTransitionFadingAway,mFlingingToDismissKeyguard,mKeyguardNegativeScreen;
    }
    public static final class Manager {public boolean mDozing,mRemoteInputActive,mSecureLockDeviceBiometricAuthVisible;}
    public static final class Monitor {
        public List<RoiExclusionTraceAdapterTest.Sensor> mFingerprintSensorProperties=List.of(new RoiExclusionTraceAdapterTest.Sensor());
        public boolean isSimPinSecure(){return false;}
    }
    public static final class Central {
        public Keyguard mKeyguardStateController=new Keyguard();public Manager mStatusBarKeyguardViewManager=new Manager();
        public Monitor mKeyguardUpdateMonitor=new Monitor();
        public boolean mBouncerShowing,mDozing,mLaunchEmergencyActionOnFinishedGoingToSleep,mLaunchEmergencyActionWhenFinishedWaking;
    }
    public static final class Controller {
        public View mView;public Central mService=new Central();public Primary mPrimaryBouncerInteractor=new Primary();public Alternate mAlternateBouncerInteractor=new Alternate();
        public Flow mAodInterceptingTouches=new Flow();public ControlCenterImpl mControlCenter=new ControlCenterImpl();
        public boolean mHandlingExternalTouch,mExternalTouchIntercepted,mIsOcclusionTransitionRunning,mExpandAnimationRunning,mIsTrackingBarGesture;
    }
    public static final class Interaction {public Controller this$0;}
    @Before public void before()throws Exception{
        ActivityController<Activity> activityController=Robolectric.buildActivity(Activity.class).setup();Activity activity=activityController.get();Resources original=activity.getResources();
        Resources resources=new Resources(original.getAssets(),original.getDisplayMetrics(),original.getConfiguration()){
            @Override public int getIdentifier(String name,String type,String pkg){if("id".equals(type)&&"com.android.systemui".equals(pkg)){if("keyguard_root_view".equals(name))return 0x7f076001;if("shared_notification_container".equals(name))return 0x7f076002;if("notification_stack_scroller".equals(name))return 0x7f077001;if("media_carousel_scroller".equals(name))return 0x7f077002;if("tiny_media_session_container".equals(name))return 0x7f077003;if("keyguard_shortcut_container".equals(name))return 0x7f077004;if("custom_lockscreen_button".equals(name))return 0x7f077005;if("emergency_call_button".equals(name))return 0x7f077006;if("miui_tiny_keyguard_camera_view".equals(name))return 0x7f077007;}return 0;}
        };
        Context context=new ContextWrapper(activity){@Override public Resources getResources(){return resources;}};
        root=new NotificationShadeWindowView(context);root.testInsets=new WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),Insets.NONE).build();activity.setContentView(root);
        HyperOSKeyguardRootView positive=new HyperOSKeyguardRootView(context);positive.setId(0x7f076001);positive.setWillNotDraw(true);root.addView(positive,new FrameLayout.LayoutParams(-1,-1));
        foreground=new SharedNotificationContainer(context);foreground.setId(0x7f076002);foreground.setBackgroundColor(0x00000000);root.addView(foreground,new FrameLayout.LayoutParams(-1,-1));
        controller=new Controller();controller.mView=root;controller.mControlCenter.expandedStateInternal=new Flow();Interaction interaction=new Interaction();interaction.this$0=controller;root.mInteractionEventHandler=interaction;
        ((ShadowPowerManager)Shadow.extract(context.getSystemService(PowerManager.class))).setIsInteractive(true);
        RoiTestWindow.show(activityController);
        root.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY));root.layout(0,0,1000,2000);
        assertTrue(root.isAttachedToWindow());assertTrue(root.isShown());
        assertEquals(View.VISIBLE,root.getWindowVisibility());assertNotNull(root.getDisplay());assertEquals(0,root.getDisplay().getDisplayId());
    }
    private boolean has(int[] codes,int code){return Arrays.stream(codes).anyMatch(value->value==code);}
    @Test public void fullReadProducesAppliedForceModeAndActualOccludingSource(){
        LockSurfaceSnapshot result=LockSurfaceSnapshot.read(root,true,true);assertFalse(result.reason,result.allowed);assertEquals("POSITIVE_ROI_EMPTY_AFTER_EXCLUSIONS",result.reason);assertTrue(has(result.roiDetails(),RoiDiagnosticDetail.MODE_FORCE));
        assertTrue(Arrays.stream(result.roiDetails()).mapToObj(RoiDiagnosticDetail::label).anyMatch(label->label.contains("FULL_BACKGROUND_TOP_SHARED_NODE_SHARED")));
    }
    @Test public void diagnosticOnAndOffKeepSameFailureButOffHasNoDetails(){LockSurfaceSnapshot on=LockSurfaceSnapshot.read(root,true,true),off=LockSurfaceSnapshot.read(root,false,true);assertEquals(off.allowed,on.allowed);assertEquals(off.reason,on.reason);assertEquals(0,off.roiDetails().length);assertTrue(on.roiDetails().length>0);}
    @Test public void conservativeModeIsReportedFromReadArgumentNotUiPreference(){LockSurfaceSnapshot result=LockSurfaceSnapshot.read(root,true,false);assertEquals("POSITIVE_ROI_EMPTY_AFTER_EXCLUSIONS",result.reason);assertTrue(has(result.roiDetails(),RoiDiagnosticDetail.MODE_CONSERVATIVE));assertFalse(has(result.roiDetails(),RoiDiagnosticDetail.MODE_FORCE));}
    @Test public void authenticationStillRejectsBeforeAnyRegionDetails(){controller.mService.mKeyguardStateController.mPrimaryBouncerShowing=true;LockSurfaceSnapshot result=LockSurfaceSnapshot.read(root,true,true);assertEquals("BOUNCER_SHOWING",result.reason);assertEquals(0,result.roiDetails().length);}
    @Test public void originalAllowedSnapshotDoesNotInventAnEmptyRegionReport(){foreground.setBackground(null);foreground.setWillNotDraw(true);root.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY));root.layout(0,0,1000,2000);assertFalse(foreground.isLayoutRequested());LockSurfaceSnapshot on=LockSurfaceSnapshot.read(root,true,true),off=LockSurfaceSnapshot.read(root,false,true);assertTrue(on.reason,on.allowed);assertEquals(off.reason,on.reason);assertEquals(off.background,on.background);assertEquals(off.exclusions,on.exclusions);assertTrue(on.sameLayout(off));assertEquals(0,on.roiDetails().length);}
    @Test public void securityInsetsEmptyPathIsNotMislabelledAsForegroundCoverage(){root.testInsets=new WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),Insets.of(0,0,0,4000)).build();LockSurfaceSnapshot result=LockSurfaceSnapshot.read(root,true,true);assertEquals("SAFE_BACKGROUND_EMPTY",result.reason);assertEquals(0,result.roiDetails().length);}
}
