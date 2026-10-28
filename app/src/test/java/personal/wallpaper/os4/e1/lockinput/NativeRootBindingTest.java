package personal.wallpaper.os4.e1.lockinput;

import android.view.View;
import android.widget.FrameLayout;
import com.android.keyguard.injector.KeyguardPanelViewInjector;
import com.android.systemui.shade.NotificationShadeWindowView;
import com.android.systemui.shade.NotificationShadeWindowViewController;
import com.android.systemui.shade.NotificationShadeWindowViewController$1;
import com.android.systemui.shade.NotificationPanelView;
import com.android.systemui.shade.NotificationPanelViewController;
import com.android.systemui.shade.NotificationPanelViewControllerInjector;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** 真实Android窗与已核OEM字段图，仅测前置业务绑定，不冒ART/手机可达。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE,qualifiers="w1200dp-h2400dp-mdpi")
public final class NativeRootBindingTest {
    public static final class CurrentStatus {public int state=1;public boolean dozing;public int getCurrentOrUpcomingState(){return state;}public boolean isDozing(){return dozing;}}
    public static final class CurrentCentral {
        public Object mKeyguardStateController,mStatusBarKeyguardViewManager,mKeyguardUpdateMonitor;
        public boolean mDeviceInteractive=true,mBouncerShowing,mDozing,mLaunchEmergencyActionOnFinishedGoingToSleep,mLaunchEmergencyActionWhenFinishedWaking,ignore;
        public boolean shouldIgnoreTouch(){return ignore;}
    }
    static final class Fixture {
        final NativeBusinessSnapshotTest guard=new NativeBusinessSnapshotTest();
        NotificationShadeWindowView root;NotificationShadeWindowViewController window;
        NotificationPanelView panelView;NotificationPanelViewController panel;NotificationPanelViewControllerInjector panelInjector;
        KeyguardPanelViewInjector injector;Object oldWindow;
        CurrentCentral central;CurrentStatus status;
        void before()throws Exception{
            guard.before();root=(NotificationShadeWindowView)get(guard,"shade");injector=(KeyguardPanelViewInjector)get(guard,"injector");panel=(NotificationPanelViewController)get(guard,"panel");oldWindow=get(guard,"window");
            window=new NotificationShadeWindowViewController();window.mView=root;window.mPrimaryBouncerInteractor=get(oldWindow,"mPrimaryBouncerInteractor");window.mAodInterceptingTouches=get(oldWindow,"mAodInterceptingTouches");
            Object oldCentral=panel.mCentralSurfaces;central=new CurrentCentral();central.mKeyguardStateController=get(oldCentral,"mKeyguardStateController");central.mStatusBarKeyguardViewManager=get(oldCentral,"mStatusBarKeyguardViewManager");central.mKeyguardUpdateMonitor=get(oldCentral,"mKeyguardUpdateMonitor");panel.mCentralSurfaces=central;window.mService=central;status=new CurrentStatus();window.mStatusBarStateController=status;com.miui.systemui.util.CommonUtil.defaultTheme=true;
            panelInjector=new NotificationPanelViewControllerInjector();panelView=new NotificationPanelView(root.getContext());root.addView(panelView,new FrameLayout.LayoutParams(-1,-1));
            NotificationPanelViewController.TouchHandler handler=new NotificationPanelViewController.TouchHandler();handler.this$0=panel;panel.mTouchHandler=handler;panelView.mTouchHandler=handler;panel.mView=panelView;
            panelInjector.controller=panel;panelInjector.panelView=panelView;window.mNPVCInjector=panelInjector;
            NotificationShadeWindowViewController$1 interaction=new NotificationShadeWindowViewController$1();interaction.this$0=window;root.mInteractionEventHandler=interaction;
            root.forceLayout();root.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY));root.layout(0,0,1000,2000);
            // 新增NPV后的真实ViewRoot遍历必须先结束，不能以布局未收敛假装手势期间稳定。
            org.robolectric.shadows.ShadowLooper.idleMainLooper();NativeBusinessSnapshot settled=NativeBusinessSnapshot.read(injector,1,false,false,false);
            assertTrue(settled.reason,settled.allowed);org.robolectric.shadows.ShadowLooper.idleMainLooper();NativeBusinessSnapshot stable=NativeBusinessSnapshot.read(injector,1,false,false,false);
            assertTrue("真实窗口需在合成输入前收敛",settled.sameLayout(stable));
        }
    }
    private Fixture fixture;
    @Before public void before()throws Exception{fixture=new Fixture();fixture.before();}
    static Object get(Object object,String name)throws Exception{Field field=LockInputBroker.field(object.getClass(),name);return field.get(object);}
    @Test public void exactCurrentRootGraphReturnsLiveBusinessFields(){NativeRootBinding.Result result=NativeRootBinding.read(fixture.root);assertTrue(result.reason,result.allowed);assertSame(fixture.injector,result.injector);assertSame(fixture.window,result.windowController);assertSame(fixture.panel,result.panelController);assertEquals(1,result.barState);assertFalse(result.qsExpanded);}
    @Test public void oldAnonymousInteractionIsNotAcceptedAsCurrentExactController(){fixture.root.mInteractionEventHandler=new Object();assertEquals("NATIVE_ROOT_WINDOW_BINDING",NativeRootBinding.read(fixture.root).reason);}
    @Test public void wrongWindowReverseRejects(){fixture.window.mView=new FrameLayout(fixture.root.getContext());assertEquals("NATIVE_ROOT_WINDOW_BINDING",NativeRootBinding.read(fixture.root).reason);}
    @Test public void wrongPanelViewReverseRejects(){fixture.panelInjector.panelView=new FrameLayout(fixture.root.getContext());assertEquals("NATIVE_ROOT_PANEL_BINDING",NativeRootBinding.read(fixture.root).reason);}
    @Test public void touchHandlerOwnerMustBeSameController(){((NotificationPanelViewController.TouchHandler)fixture.panel.mTouchHandler).this$0=new Object();assertEquals("NATIVE_ROOT_PANEL_BINDING",NativeRootBinding.read(fixture.root).reason);}
    @Test public void panelViewMustDescendFromTheCurrentRoot(){fixture.root.removeView(fixture.panelView);assertEquals("NATIVE_ROOT_PANEL_BINDING",NativeRootBinding.read(fixture.root).reason);}
    @Test public void injectorReverseMustMatchSamePanel(){fixture.injector.panel=new Object();assertEquals("NATIVE_ROOT_INJECTOR_BINDING",NativeRootBinding.read(fixture.root).reason);}
    @Test public void injectorShadeMustMatchCurrentRoot(){fixture.injector.notificationShadeWindowView=new FrameLayout(fixture.root.getContext());assertEquals("NATIVE_ROOT_INJECTOR_BINDING",NativeRootBinding.read(fixture.root).reason);}
    @Test public void businessStateIsReadFreshNotAssumedFalse(){fixture.panel.mBarState=2;fixture.panel.expanding=true;fixture.panel.mDozing=true;NativeRootBinding.Result result=NativeRootBinding.read(fixture.root);assertTrue(result.allowed);assertEquals(2,result.barState);assertTrue(result.expanding);assertTrue(result.dozing);}
    @Test public void unknownQsFailsClosed(){fixture.injector.quickSettingsControllerImpl=new Object();assertEquals("NATIVE_ROOT_STATE_UNKNOWN",NativeRootBinding.read(fixture.root).reason);}
    @Test public void offMainThreadDoesNotTraverseViewGraph()throws Exception{AtomicReference<NativeRootBinding.Result> result=new AtomicReference<>();Thread worker=new Thread(()->result.set(NativeRootBinding.read(fixture.root)));worker.start();worker.join(3000);assertFalse(worker.isAlive());assertEquals("NATIVE_ROOT_THREAD",result.get().reason);}
    @Test public void exactDefaultThemeBranchEligibleWithoutChangingOrdinaryBinding(){assertTrue(NativeRootBinding.cancelSourceEligible(NativeRootBinding.read(fixture.root)));com.miui.systemui.util.CommonUtil.defaultTheme=false;assertTrue(NativeRootBinding.read(fixture.root).allowed);assertFalse(NativeRootBinding.cancelSourceEligible(NativeRootBinding.read(fixture.root)));}
    @Test public void brightnessMirrorRejectsOnlyCancellationException(){fixture.window.mBrightnessMirror=new FrameLayout(fixture.root.getContext());assertTrue(NativeRootBinding.read(fixture.root).allowed);assertFalse(NativeRootBinding.cancelSourceEligible(NativeRootBinding.read(fixture.root)));((View)fixture.window.mBrightnessMirror).setVisibility(View.INVISIBLE);assertTrue(NativeRootBinding.cancelSourceEligible(NativeRootBinding.read(fixture.root)));}
    @Test public void collapsingAndSleepingCannotMasqueradeAsDefaultPointerCancellation(){fixture.panel.tracking=true;assertFalse(NativeRootBinding.cancelSourceEligible(NativeRootBinding.read(fixture.root)));fixture.panel.tracking=false;fixture.central.mDeviceInteractive=false;assertFalse(NativeRootBinding.cancelSourceEligible(NativeRootBinding.read(fixture.root)));}
    @Test public void dozeIgnoreTouchAndUnknownControllerStateFailClosed(){fixture.status.dozing=true;assertFalse(NativeRootBinding.cancelSourceEligible(NativeRootBinding.read(fixture.root)));fixture.status.dozing=false;fixture.central.ignore=true;assertFalse(NativeRootBinding.cancelSourceEligible(NativeRootBinding.read(fixture.root)));fixture.central.ignore=false;fixture.window.mStatusBarStateController=new Object();assertFalse(NativeRootBinding.cancelSourceEligible(NativeRootBinding.read(fixture.root)));}
    @Test public void alreadyCancelledRootCannotReceiveCancellationException(){fixture.window.mTouchCancelled=true;assertFalse(NativeRootBinding.cancelSourceEligible(NativeRootBinding.read(fixture.root)));}
}
