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
import com.android.keyguard.DoubleTapHelper;
import com.android.keyguard.injector.KeyguardPanelViewInjector;
import com.android.keyguard.injector.KeyguardPanelViewInjector$bindView$2;
import com.android.keyguard.panel.KeyguardPanelViewController;
import com.android.keyguard.widget.HyperOSKeyguardRootView;
import com.android.systemui.media.controls.ui.controller.KeyguardMediaController;
import com.android.systemui.shade.NotificationPanelViewController;
import com.android.systemui.shade.NotificationShadeWindowView;
import com.android.systemui.statusbar.notification.row.ExpandableView;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.StackScrollerDecorView;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController;
import com.android.systemui.statusbar.phone.KeyguardBottomAreaView;
import com.android.keyguard.shortcut.MiuiShortcutController;
import com.miui.systemui.controlcenter.ControlCenterImpl;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPowerManager;
import static org.junit.Assert.*;

/** 使用OEM真实绑定字段、非空通知/Decor及真实Android窗口，不冒充设备Hook或OEM执行。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE,qualifiers="w1200dp-h2400dp-mdpi")
public final class NativeBusinessSnapshotTest {
    private Context context;
    private NotificationShadeWindowView shade;
    private HyperOSKeyguardRootView root;
    private NotificationStackScrollLayout stack;
    private NotificationStackScrollLayoutController stackController;
    private KeyguardPanelViewInjector injector;
    private KeyguardPanelViewController controller;
    private NotificationPanelViewController panel;
    private DoubleTapHelper helper;
    private KeyguardPanelViewInjector$bindView$2 listener;
    private KeyguardMediaController media;
    private Monitor monitor;
    private Keyguard keyguard;
    private Manager manager;
    private Window window;
    private Editor editor;
    private Move move;
    private Alternate alternate;
    private Qs qs;
    private Bottom bottom;
    private Ambient ambient;
    private ControlCenterImpl controlCenter;
    private boolean priorityResources;
    public static final class Flow {public Object value=false;public Object getValue(){return value;}}
    public static final class Lazy {public Object value;Lazy(Object value){this.value=value;}public Object getValue(){return value;}}
    public static final class Alternate {public boolean visible;public boolean isVisibleState(){return visible;}}
    public static final class Primary {public Flow isShowing=new Flow(),isShowingSoon=new Flow();}
    public static final class Keyguard {
        public boolean mShowing=true,mPrimaryBouncerShowing,mOccluded,mKeyguardGoingAway,mKeyguardFadingAway,mMiKeyguardFadingAway,
                mLaunchTransitionFadingAway,mFlingingToDismissKeyguard,mKeyguardNegativeScreen;
    }
    public static final class Manager {public boolean mDozing,mRemoteInputActive,mSecureLockDeviceBiometricAuthVisible;}
    public static final class Monitor {
        public boolean mDeviceInteractive=true,mPrimaryBouncerIsOrWillBeShowing,sim;
        public List<RoiExclusionTraceAdapterTest.Sensor> mFingerprintSensorProperties=List.of(new RoiExclusionTraceAdapterTest.Sensor());
        public boolean isSimPinSecure(){return sim;}
    }
    public static final class Central {
        public Object mKeyguardStateController,mStatusBarKeyguardViewManager,mKeyguardUpdateMonitor;
        public boolean mBouncerShowing,mDozing,mLaunchEmergencyActionOnFinishedGoingToSleep,mLaunchEmergencyActionWhenFinishedWaking;
    }
    public static final class Window {
        public View mView;public Primary mPrimaryBouncerInteractor=new Primary();public Flow mAodInterceptingTouches=new Flow();
        public boolean mHandlingExternalTouch,mExternalTouchIntercepted,mIsOcclusionTransitionRunning,mExpandAnimationRunning,mIsTrackingBarGesture;
    }
    public static final class Interaction {public Window this$0;}
    public enum EditorState {IDEL,EXITING,EXITINGFROMBOUNCER,ENTERING}
    public enum WakeState {IDLE,PLAYING,FINISHED}
    public static final class Editor {public Object mState=EditorState.IDEL;}
    public static final class Move {
        public boolean mIsShortcutMoving,mSwipingInProgress,left,magazine;public Object mSwipeFolme,mResetFolme;
        public boolean isInLeftView(){return left;}public boolean isInMagazineAnimation(){return magazine;}
    }
    public static final class Qs {public boolean expanded;public boolean getExpanded(){return expanded;}}
    public static final class Bottom {public Object mKeyguardPanelViewInjector,mKeyguardBottomAreaView,mCustomLockscreenButton,mIndicationArea,miuiShortcutController;}
    public static final class Ambient {public boolean mIsSwipingUp,mIsFlinging,mIsClosing,mDozing;}
    @Before public void before()throws Exception{
        ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup();Resources original=activity.get().getResources();
        Resources resources=new Resources(original.getAssets(),original.getDisplayMetrics(),original.getConfiguration()){
            @Override public int getIdentifier(String name,String type,String pkg){
                if(!"id".equals(type)||!"com.android.systemui".equals(pkg))return 0;
                if("overlay_container".equals(name))return 0x7f07800a;
                if(priorityResources){String[] names={"notification_stack_scroller","media_carousel_scroller","tiny_media_session_container","keyguard_shortcut_container","custom_lockscreen_button","emergency_call_button","miui_tiny_keyguard_camera_view"};for(int i=0;i<names.length;i++)if(names[i].equals(name))return 0x7f077001+i;}
                return 0;
            }
        };
        context=new ContextWrapper(activity.get()){@Override public Resources getResources(){return resources;}};
        shade=new NotificationShadeWindowView(context);shade.testInsets=new WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),Insets.NONE).build();
        activity.get().setContentView(shade);root=new HyperOSKeyguardRootView(context);shade.addView(root,new FrameLayout.LayoutParams(-1,-1));
        stack=new NotificationStackScrollLayout(context);shade.addView(stack,new FrameLayout.LayoutParams(-1,-1));
        stackController=new NotificationStackScrollLayoutController();stackController.mView=stack;stack.mController=stackController;
        ambient=new Ambient();stack.mAmbientState=ambient;
        injector=new KeyguardPanelViewInjector();controller=new KeyguardPanelViewController();panel=new NotificationPanelViewController();
        injector.controller=controller;injector.panel=panel;injector.keyguardRootView=root;injector.notificationShadeWindowView=shade;
        controller.keyguardPanelViewInjector=injector;controller.keyguardRootView=root;controller.notificationShadeWindowView=shade;
        controller.wakeupAnimState=WakeState.IDLE;
        controller.notificationStackScrollLayoutController=stackController;controller.notificationStackScrollLayout=stack;
        panel.mKeyguardPanelViewInjector=injector;panel.mNotificationStackScrollLayoutController=stackController;
        helper=new DoubleTapHelper();listener=new KeyguardPanelViewInjector$bindView$2();listener.$tmp0=injector;helper.mView=root;helper.mDoubleTapListener=listener;injector.doubleTapHelper=helper;
        monitor=new Monitor();keyguard=new Keyguard();manager=new Manager();injector.keyguardUpdateMonitor=monitor;controller.keyguardUpdateMonitor=monitor;
        controller.keyguardStateController=keyguard;panel.mKeyguardStateController=keyguard;controller.statusBarKeyguardViewManager=manager;panel.mStatusBarKeyguardViewManager=manager;
        alternate=new Alternate();panel.mAlternateBouncerInteractor=alternate;
        Central central=new Central();central.mKeyguardStateController=keyguard;central.mStatusBarKeyguardViewManager=manager;central.mKeyguardUpdateMonitor=monitor;panel.mCentralSurfaces=central;
        window=new Window();window.mView=shade;Interaction interaction=new Interaction();interaction.this$0=window;shade.mInteractionEventHandler=interaction;
        editor=new Editor();injector.keyguardEditorHelper$delegate=new Lazy(editor);qs=new Qs();injector.quickSettingsControllerImpl=qs;
        controlCenter=new ControlCenterImpl();controlCenter.expandedStateInternal=new Flow();injector.controlCenterImpl=controlCenter;
        move=new Move();injector.keyguardMoveHelper=move;
        media=new KeyguardMediaController();media.visible$delegate=new Flow();panel.mKeyguardMediaController=media;
        bottom=new Bottom();bottom.mKeyguardPanelViewInjector=injector;KeyguardBottomAreaView bottomView=new KeyguardBottomAreaView(context);
        root.addView(bottomView,new FrameLayout.LayoutParams(-1,-1));bottom.mKeyguardBottomAreaView=bottomView;
        android.widget.LinearLayout indication=new android.widget.LinearLayout(context);FrameLayout.LayoutParams footer=new FrameLayout.LayoutParams(1000,100);footer.topMargin=1900;
        bottomView.addView(indication,footer);bottom.mIndicationArea=indication;
        FrameLayout shortcut=new FrameLayout(context);FrameLayout.LayoutParams empty=new FrameLayout.LayoutParams(1000,0);empty.topMargin=2000;bottomView.addView(shortcut,empty);
        MiuiShortcutController shortcutController=new MiuiShortcutController();shortcutController.injector=injector;shortcutController.shortcutContainerView=shortcut;bottom.miuiShortcutController=shortcutController;
        FrameLayout overlay=new FrameLayout(context);overlay.setId(0x7f07800a);bottomView.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        injector.keyguardBottomAreaInjector$delegate=new Lazy(bottom);
        ((ShadowPowerManager)Shadow.extract(context.getSystemService(PowerManager.class))).setIsInteractive(true);
        RoiTestWindow.show(activity);layout();
        assertTrue(root.isAttachedToWindow());assertTrue(stack.isLaidOut());assertFalse(stack.isLayoutRequested());
    }
    private void layout(){shade.forceLayout();shade.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY));shade.layout(0,0,1000,2000);}
    private NativeBusinessSnapshot read(){return NativeBusinessSnapshot.read(injector,1,false,false,false);}
    private NativeBusinessSnapshot good(){NativeBusinessSnapshot result=read();assertTrue(result.reason,result.allowed);return result;}
    private void denied(String reason){NativeBusinessSnapshot result=read();assertFalse(result.allowed);assertEquals(reason,result.reason);assertFalse(result.contains(700,1500));assertEquals(0,stack.hitTests);}
    private ExpandableView row(int width,int height,int y){ExpandableView row=new ExpandableView(context);stack.addView(row,new FrameLayout.LayoutParams(width,height));row.setTranslationY(y);layout();assertEquals("夹具实际Android平移必须已应用",(float)y,row.getSuperTranslationY(),0f);float[] matrix=new float[9];row.getMatrix().getValues(matrix);assertEquals("夹具实际矩阵平移必须已应用",(float)y,matrix[android.graphics.Matrix.MTRANS_Y],0f);return row;}
    @Test public void actualBindingAndBlankPointUsePureBusinessHitTest(){
        NativeBusinessSnapshot snapshot=good();assertSame(root,snapshot.root);assertSame(shade,snapshot.binding.shadeRoot);
        assertTrue(snapshot.point(700,1500).reason,snapshot.contains(700,1500));assertEquals(2,stack.hitTests);
        assertTrue(stack.lastRequireMinHeight);assertFalse(stack.lastIgnoreDecor);assertTrue(stack.lastWholeWidth);
    }
    @Test public void priorityUsesVerifiedNativeBindingWithoutLegacyRootAdapter(){
        NativeBusinessSnapshot result=NativeBusinessSnapshot.read(injector,1,false,false,false,true);
        assertTrue(result.reason,result.allowed);assertTrue(result.contains(700,1500));
    }
    @Test public void priorityDoesNotUseUnrelatedThemePendingLayoutAsAdmission(){
        priorityGood();root.addView(new View(context),new FrameLayout.LayoutParams(1000,2000));assertTrue(root.isLayoutRequested());
        var snapshot=NativeBusinessSnapshot.read(injector,1,false,false,false,true);
        assertTrue(snapshot.reason+"/"+snapshot.diagnosticCause,snapshot.allowed);assertTrue(snapshot.contains(700,1500));
    }
    @Test public void priorityUsesCurrentBoundBusinessGraphWithoutLegacyResourceOrCollection(){
        priorityResources=false;stackController.mNotifCollection=null;
        var snapshot=NativeBusinessSnapshot.read(injector,1,false,false,false,true);
        assertTrue(snapshot.reason+"/"+snapshot.diagnosticCause,snapshot.allowed);assertTrue(snapshot.contains(700,1500));
    }
    private NativeBusinessSnapshot priority(){
        priorityResources=true;stack.setId(0x7f077001);
        if(stackController.mNotifCollection==null)stackController.mNotifCollection=new com.android.systemui.statusbar.notification.collection.NotifCollection();
        layout();return NativeBusinessSnapshot.read(injector,1,false,false,false,true);
    }
    private NativeBusinessSnapshot priorityGood(){NativeBusinessSnapshot result=priority();assertTrue(result.reason+"/"+result.diagnosticCause,result.allowed);return result;}
    @Test public void priorityAllowsMediaAreaWithoutLegacyResourceRequirement(){
        FrameLayout actual=new FrameLayout(context);FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(900,200);size.topMargin=1200;shade.addView(actual,size);
        media.singlePaneContainer=actual;((Flow)media.visible$delegate).value=true;
        NativeBusinessSnapshot snapshot=priorityGood();assertTrue(snapshot.contains(700,1300));assertTrue(snapshot.contains(700,1500));
    }
    @Test public void priorityAllowsNotificationMediaTopAndEdgeAfterExplicitScopeChange(){
        ExpandableNotificationRow row=new ExpandableNotificationRow(context);FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(900,150);size.topMargin=650;stack.addView(row,size);
        var collection=new com.android.systemui.statusbar.notification.collection.NotifCollection();collection.mNotificationSet=java.util.Map.of("test",new Object());stackController.mNotifCollection=collection;
        FrameLayout actual=new FrameLayout(context);FrameLayout.LayoutParams mediaSize=new FrameLayout.LayoutParams(900,200);mediaSize.topMargin=1200;shade.addView(actual,mediaSize);media.singlePaneContainer=actual;((Flow)media.visible$delegate).value=true;
        NativeBusinessSnapshot snapshot=priorityGood();assertTrue(snapshot.contains(700,700));assertTrue(snapshot.contains(700,1300));assertTrue(snapshot.contains(700,1500));assertTrue(snapshot.contains(700,300));assertTrue(snapshot.contains(5,1500));
    }
    @Test public void priorityDoesNotRequireMediaContainer(){((Flow)media.visible$delegate).value=true;assertTrue(priorityGood().contains(700,1500));}
    @Test public void priorityDoesNotUseMediaControllerTypeForGestureAdmission(){panel.mKeyguardMediaController=new Object();assertTrue(priorityGood().contains(700,1500));}
    @Test public void priorityDoesNotReadMediaVisibility(){((Flow)media.visible$delegate).value="unknown";assertTrue(priorityGood().contains(700,1500));}
    @Test public void priorityMediaReplacementDoesNotResetSequence(){var snapshot=priorityGood();panel.mKeyguardMediaController=new Object();assertTrue(snapshot.contains(700,1500));}
    @Test public void priorityFreshSnapshotIgnoresUnprotectedMediaReplacement(){var before=priorityGood();panel.mKeyguardMediaController=new Object();var after=priorityGood();assertTrue(before.sameLayout(after));assertTrue(after.sameLayout(before));}
    @Test public void priorityEmptyInactiveMediaDoesNotDisableWallpaper(){FrameLayout empty=new FrameLayout(context);shade.addView(empty,new FrameLayout.LayoutParams(1000,0));media.splitShadeContainer=empty;assertTrue(priorityGood().contains(700,1500));}
    @Test public void priorityEmptyVisibleMediaDoesNotBlockGesture(){FrameLayout empty=new FrameLayout(context);shade.addView(empty,new FrameLayout.LayoutParams(1000,0));media.singlePaneContainer=empty;((Flow)media.visible$delegate).value=true;assertTrue(priorityGood().contains(700,1500));}
    @Test public void priorityMediaMovementDoesNotResetSequence(){FrameLayout actual=new FrameLayout(context);FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(900,200);size.topMargin=1200;shade.addView(actual,size);media.singlePaneContainer=actual;var snapshot=priorityGood();actual.setTranslationY(50);assertTrue(snapshot.contains(700,1500));}
    @Test public void protectedGeometryDiagnosticKeepsPrimaryReasonAndFixedScope(){stack.setScaleX(.8f);var result=read();assertFalse(result.allowed);assertEquals("NATIVE_PROTECTED_LAYOUT",result.reason);assertEquals("NATIVE_LAYOUT_STACK",result.diagnosticScope);assertEquals("NATIVE_LAYOUT_TRANSFORM",result.diagnosticCause);}
    @Test public void controllerBackReferenceCannotBeGuessed(){controller.keyguardPanelViewInjector=new Object();denied("NATIVE_BINDING_CONTROLLER");}
    @Test public void helperListenerOwnerMustMatchCurrentInjector(){listener.$tmp0=new Object();denied("NATIVE_BINDING_HELPER");}
    @Test public void helperRootMustMatchCurrentView(){helper.mView=shade;denied("NATIVE_BINDING_HELPER");}
    @Test public void oldRootAfterRebindIsRejected(){controller.keyguardRootView=new HyperOSKeyguardRootView(context);denied("NATIVE_BINDING_ROOT");}
    @Test public void currentStackBackReferenceIsRequired(){stack.mController=new NotificationStackScrollLayoutController();denied("NATIVE_BINDING_NOTIFICATION");}
    @Test public void stackOutsideBoundWindowCannotBeUsed(){shade.removeView(stack);denied("NATIVE_PROTECTED_LAYOUT");}
    @Test public void noNotifCollectionIsRequiredByNewRoute(){stackController.mNotifCollection=null;assertTrue(good().contains(700,1500));}
    @Test public void reportedNotificationCannotBeAssumedEmptyWhenNoActualRow(){controller.hasNotification=true;denied("NATIVE_NOTIFICATION_UNACCOUNTED");}
    @Test public void actualVisibleNotificationBoundToControllerIsAccounted(){ExpandableNotificationRow row=new ExpandableNotificationRow(context);stack.addView(row,new FrameLayout.LayoutParams(900,200));row.setTranslationY(650);controller.hasNotification=true;layout();assertEquals(650f,row.getSuperTranslationY(),0f);NativeBusinessSnapshot snapshot=good();assertEquals("NATIVE_NOTIFICATION_REGION",snapshot.point(700,700).reason);assertTrue(snapshot.contains(700,1500));}
    @Test public void primaryAuthenticationRejectsBeforePureHit(){monitor.mPrimaryBouncerIsOrWillBeShowing=true;denied("NATIVE_AUTHENTICATION");}
    @Test public void soonAuthenticationRejectsBeforeCoordinates(){window.mPrimaryBouncerInteractor.isShowingSoon.value=true;denied("NATIVE_AUTHENTICATION");}
    @Test public void alternateAuthenticationRejectsBeforePureHit(){alternate.visible=true;denied("NATIVE_AUTHENTICATION");}
    @Test public void simAuthenticationRejects(){monitor.sim=true;denied("NATIVE_SIM_AUTH");}
    @Test public void remoteInputAndSecureBiometricAreNotBackground(){manager.mRemoteInputActive=true;denied("NATIVE_REMOTE_AUTH");manager.mRemoteInputActive=false;manager.mSecureLockDeviceBiometricAuthVisible=true;denied("NATIVE_REMOTE_AUTH");}
    @Test public void occludedAndGoingAwayReject(){keyguard.mOccluded=true;denied("NATIVE_TRANSITION");keyguard.mOccluded=false;keyguard.mKeyguardGoingAway=true;denied("NATIVE_TRANSITION");}
    @Test public void aodAndDozingReject(){window.mAodInterceptingTouches.value=true;denied("NATIVE_DOZING_AOD");window.mAodInterceptingTouches.value=false;panel.mDozing=true;denied("NATIVE_DOZING_AOD");}
    @Test public void unknownAodValueIsNotFalse(){window.mAodInterceptingTouches.value="unknown";denied("NATIVE_STATE_UNKNOWN");}
    @Test public void editorExitingIsNotIdleEvenIfOemExposedWouldAcceptIt(){editor.mState=EditorState.EXITING;denied("NATIVE_EDITOR");}
    @Test public void qsRejects(){qs.expanded=true;denied("NATIVE_QS");}
    @Test public void controlCenterRejects(){((Flow)controlCenter.expandedStateInternal).value=true;denied("NATIVE_CONTROL_CENTER");}
    @Test public void shortcutMovementRejects(){move.mIsShortcutMoving=true;denied("NATIVE_SHORTCUT_MOVING");}
    @Test public void panelExpansionRejects(){panel.expanding=true;denied("NATIVE_TRANSITION");}
    @Test public void wakeupPlayingAndStartedUnlockAnimatorReject(){controller.wakeupAnimState=WakeState.PLAYING;denied("NATIVE_TRANSITION");controller.wakeupAnimState=WakeState.FINISHED;android.animation.ValueAnimator animator=android.animation.ValueAnimator.ofFloat(0,1);animator.setDuration(1000);animator.start();controller.unlockAlphaAndScaleAnimator=animator;denied("NATIVE_TRANSITION");animator.cancel();}
    @Test public void nativeArgumentsCannotOverrideActualState(){NativeBusinessSnapshot result=NativeBusinessSnapshot.read(injector,1,false,false,true);assertEquals("NATIVE_DOZING_AOD",result.reason);assertEquals(0,stack.hitTests);}
    @Test public void notShowingAndNoninteractiveReject(){controller.keyguardShowing=false;denied("NATIVE_NOT_KEYGUARD");controller.keyguardShowing=true;monitor.mDeviceInteractive=false;denied("NATIVE_NOT_INTERACTIVE");}
    @Test public void actualNotificationFullPhysicalRegionProtected(){row(900,200,650);NativeBusinessSnapshot snapshot=good();assertEquals("NATIVE_NOTIFICATION_REGION",snapshot.point(600,700).reason);assertTrue(snapshot.contains(700,1500));}
    @Test public void nativeHitTestAlsoProtectsFullStackWidthOutsideNarrowRow(){row(200,200,650);NativeBusinessSnapshot snapshot=good();assertEquals("NATIVE_HIT_TEST_REGION",snapshot.point(800,700).reason);assertEquals(1,stack.hitTests);}
    @Test public void visibleDecorIsProtectedNotSkippedAsEmpty(){StackScrollerDecorView decor=new StackScrollerDecorView(context);stack.addView(decor,new FrameLayout.LayoutParams(900,200));decor.setTranslationY(650);layout();assertEquals(650f,decor.getSuperTranslationY(),0f);assertEquals("NATIVE_NOTIFICATION_REGION",good().point(600,700).reason);}
    @Test public void unknownNotificationChildFailsClosed(){stack.addView(new View(context),new FrameLayout.LayoutParams(100,100));layout();denied("NATIVE_NOTIFICATION_CHILD_UNKNOWN");}
    @Test public void notificationAnimationFailsClosed(){stack.mAnimationRunning=true;denied("NATIVE_NOTIFICATION_TRANSITION");}
    @Test public void queuedRemovedNotificationFailsClosed(){stack.mChildrenToRemoveAnimated=List.of(new Object());denied("NATIVE_NOTIFICATION_TRANSITION");}
    @Test public void pendingPreDrawCannotBeMistakenForStableLayout(){stack.mChildrenUpdateRequested=true;denied("NATIVE_NOTIFICATION_TRANSITION");}
    @Test public void pendingReorderAndAnimationEventsRemainProtected(){stack.mChildrenChangingPositions=List.of(new Object());denied("NATIVE_NOTIFICATION_TRANSITION");stack.mChildrenChangingPositions=List.of();stack.mAnimationEvents=List.of(new Object());denied("NATIVE_NOTIFICATION_TRANSITION");}
    @Test public void ambientSwipeFlingCloseAndDozeReject(){ambient.mIsSwipingUp=true;denied("NATIVE_NOTIFICATION_TRANSITION");ambient.mIsSwipingUp=false;ambient.mIsFlinging=true;denied("NATIVE_NOTIFICATION_TRANSITION");ambient.mIsFlinging=false;ambient.mIsClosing=true;denied("NATIVE_NOTIFICATION_TRANSITION");ambient.mIsClosing=false;ambient.mDozing=true;denied("NATIVE_NOTIFICATION_TRANSITION");}
    @Test public void pendingLocalNotificationLayoutFailsClosed(){row(500,200,650).requestLayout();denied("NATIVE_NOTIFICATION_LAYOUT");}
    @Test public void localScaleOrAncestorTransformCannotBecomeBlank(){stack.setScaleX(.8f);denied("NATIVE_PROTECTED_LAYOUT");}
    @Test public void requestedIdentityDoesNotHideActualOemScale(){ExpandableView row=row(300,200,650);row.setSuperScaleX(.8f);assertEquals(1f,row.getScaleX(),0f);assertEquals(.8f,row.getSuperScaleX(),0f);denied("NATIVE_PROTECTED_LAYOUT");}
    @Test public void requestedZeroDoesNotHideActualOemRotationOrZ(){ExpandableView row=row(300,200,650);row.setSuperRotationZ(8);assertEquals(0f,row.getRotation(),0f);denied("NATIVE_PROTECTED_LAYOUT");row.setSuperRotationZ(0);row.setSuperTranslationZ(4);assertEquals(0f,row.getTranslationZ(),0f);denied("NATIVE_PROTECTED_LAYOUT");}
    @Test public void settledPureTranslationRemainsAllowedAndProtectsFractionalActualEdge(){
        ExpandableView row=row(300,200,650);row.setTranslationX(100);
        row.setTranslationY(650.6f);layout();assertEquals(100f,row.getSuperTranslationX(),0f);assertEquals(650.6f,row.getSuperTranslationY(),0f);float[] actual=new float[9];row.getMatrix().getValues(actual);assertEquals(1f,actual[android.graphics.Matrix.MSCALE_X],0f);assertEquals(1f,actual[android.graphics.Matrix.MSCALE_Y],0f);assertEquals(100f,actual[android.graphics.Matrix.MTRANS_X],0f);assertEquals(650.6f,actual[android.graphics.Matrix.MTRANS_Y],0f);
        NativeBusinessSnapshot snapshot=good();int[] screen=new int[2],stackScreen=new int[2];row.getLocationOnScreen(screen);stack.getLocationOnScreen(stackScreen);
        assertEquals("夹具卡片须真实位于48px边缘禁区内侧",stackScreen[0]+100,screen[0]);assertEquals("卡片内部20px样本须为安全区X120",120,screen[0]+20);assertTrue("不得删除或绕过原48px边缘禁区",screen[0]+20>48);assertTrue("样本仍在卡片内部",20<row.getWidth());assertTrue("目标须在顶部20%禁区下，且实际屏幕Y须随真实矩阵",screen[1]>400&&Math.abs(screen[1]-(stackScreen[1]+row.getTop()+650.6f))<=1f);
        assertEquals("NATIVE_NOTIFICATION_REGION",snapshot.point(screen[0]+20,screen[1]-.4f).reason);assertTrue(snapshot.contains(700,1500));
    }
    @Test public void requestedTranslationCannotMoveProtectionAwayFromActualOemCard(){
        ExpandableView row=row(300,200,1200);row.setTranslationX(100);
        row.setSuperTranslationY(650.6f);layout();assertEquals(100f,row.getSuperTranslationX(),0f);assertEquals(1200f,row.getTranslationY(),0f);assertEquals(650.6f,row.getSuperTranslationY(),0f);float[] actual=new float[9];row.getMatrix().getValues(actual);assertEquals(1f,actual[android.graphics.Matrix.MSCALE_X],0f);assertEquals(1f,actual[android.graphics.Matrix.MSCALE_Y],0f);assertEquals(100f,actual[android.graphics.Matrix.MTRANS_X],0f);assertEquals(650.6f,actual[android.graphics.Matrix.MTRANS_Y],0f);
        NativeBusinessSnapshot snapshot=good();int[] screen=new int[2],stackScreen=new int[2];row.getLocationOnScreen(screen);stack.getLocationOnScreen(stackScreen);
        assertEquals("夹具卡片须真实位于48px边缘禁区内侧",stackScreen[0]+100,screen[0]);assertEquals("卡片内部20px样本须为安全区X120",120,screen[0]+20);assertTrue("不得删除或绕过原48px边缘禁区",screen[0]+20>48);assertTrue("样本仍在卡片内部",20<row.getWidth());assertTrue("屏幕位置必须使用实际650.6而非requested1200",Math.abs(screen[1]-(stackScreen[1]+row.getTop()+650.6f))<=1f);
        assertEquals("NATIVE_NOTIFICATION_REGION",snapshot.point(screen[0]+20,screen[1]+20).reason);
    }
    @Test public void sameLayoutIncludesActualMatrixEvenWhenRequestedValuesRemainIdentical(){ExpandableView row=row(300,200,650);NativeBusinessSnapshot before=good();row.setSuperTranslationY(650.25f);assertEquals(650f,row.getTranslationY(),0f);NativeBusinessSnapshot after=good();assertFalse(before.sameLayout(after));assertEquals("NATIVE_LOCAL_LAYOUT_CHANGED",before.point(700,1500).reason);}
    @Test public void unrelatedThemeChildIsNotTraversedOrRequiredToBeLaidOut(){root.addView(new View(context),new FrameLayout.LayoutParams(1000,2000));assertTrue(root.isLayoutRequested());NativeBusinessSnapshot snapshot=good();assertTrue(snapshot.contains(700,1500));}
    @Test public void activeMediaWithoutBoundViewIsUnknownNotBlank(){((Flow)media.visible$delegate).value=true;denied("NATIVE_MEDIA_UNACCOUNTED");}
    @Test public void activeMediaBoundViewHasIndependentProtection(){FrameLayout mediaView=new FrameLayout(context);FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(900,200);size.topMargin=1200;shade.addView(mediaView,size);media.singlePaneContainer=mediaView;((Flow)media.visible$delegate).value=true;layout();assertEquals("NATIVE_MEDIA_REGION",good().point(700,1300).reason);}
    @Test public void unknownMediaControllerIsNotAFalseVisibility(){panel.mKeyguardMediaController=new Object();denied("NATIVE_MEDIA_BINDING");}
    @Test public void emptyZeroSizedMediaPlaceholderDoesNotDisableUnrelatedWallpaper(){
        FrameLayout empty=new FrameLayout(context);shade.addView(empty,new FrameLayout.LayoutParams(0,0));media.singlePaneContainer=empty;layout();
        assertTrue(good().contains(700,1500));
    }
    @Test public void unusedZeroSizedSplitPlaceholderDoesNotDisableActiveSinglePaneMedia(){
        FrameLayout active=new FrameLayout(context);FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(900,200);size.topMargin=1200;
        shade.addView(active,size);media.singlePaneContainer=active;((Flow)media.visible$delegate).value=true;
        FrameLayout empty=new FrameLayout(context);shade.addView(empty,new FrameLayout.LayoutParams(0,0));media.splitShadeContainer=empty;layout();
        NativeBusinessSnapshot snapshot=good();assertEquals("NATIVE_MEDIA_REGION",snapshot.point(700,1300).reason);assertTrue(snapshot.contains(700,1500));
    }
    @Test public void visibleMediaCannotBeAccountedByOnlyEmptyPlaceholder(){
        FrameLayout empty=new FrameLayout(context);shade.addView(empty,new FrameLayout.LayoutParams(0,0));media.singlePaneContainer=empty;
        ((Flow)media.visible$delegate).value=true;layout();denied("NATIVE_MEDIA_UNACCOUNTED");
    }
    @Test public void emptyMediaGainingContentInvalidatesOldSnapshot(){
        FrameLayout empty=new FrameLayout(context);shade.addView(empty,new FrameLayout.LayoutParams(0,0));media.singlePaneContainer=empty;layout();
        NativeBusinessSnapshot snapshot=good();empty.addView(new View(context),new FrameLayout.LayoutParams(200,200));layout();
        assertEquals("NATIVE_LOCAL_LAYOUT_CHANGED",snapshot.point(700,1500).reason);
    }
    @Test public void zeroHeightMediaPlaceholderDoesNotDisableWallpaper(){FrameLayout empty=new FrameLayout(context);shade.addView(empty,new FrameLayout.LayoutParams(1000,0));media.singlePaneContainer=empty;layout();assertTrue(good().contains(700,1500));}
    @Test public void zeroWidthMediaPlaceholderDoesNotDisableWallpaper(){FrameLayout empty=new FrameLayout(context);shade.addView(empty,new FrameLayout.LayoutParams(0,100));media.singlePaneContainer=empty;layout();assertTrue(good().contains(700,1500));}
    @Test public void zeroSizedMediaWithTouchListenerIsNotEmpty(){FrameLayout empty=new FrameLayout(context);empty.setOnTouchListener((view,event)->true);shade.addView(empty,new FrameLayout.LayoutParams(0,0));media.singlePaneContainer=empty;layout();denied("NATIVE_PROTECTED_LAYOUT");}
    @Test public void zeroSizedMediaWithChildIsNotEmpty(){FrameLayout empty=new FrameLayout(context);empty.addView(new View(context),new FrameLayout.LayoutParams(100,100));shade.addView(empty,new FrameLayout.LayoutParams(0,0));media.singlePaneContainer=empty;layout();denied("NATIVE_PROTECTED_LAYOUT");}
    @Test public void emptyMediaWithAnimationCannotBeIgnored(){FrameLayout empty=new FrameLayout(context);shade.addView(empty,new FrameLayout.LayoutParams(0,0));media.singlePaneContainer=empty;layout();empty.setAnimation(new android.view.animation.AlphaAnimation(0,1));denied("NATIVE_PROTECTED_LAYOUT");}
    @Test public void emptyMediaDetachedFromBoundRootCannotBeIgnored(){FrameLayout empty=new FrameLayout(context);media.singlePaneContainer=empty;empty.layout(0,0,0,0);denied("NATIVE_MEDIA_BINDING");}
    @Test public void inputFirstDoesNotAvoidControlCenter(){priorityResources=true;((Flow)controlCenter.expandedStateInternal).value=true;assertTrue(priorityGood().contains(700,1500));}
    @Test public void inputFirstRecognizesInLockAuthenticationUi(){monitor.mPrimaryBouncerIsOrWillBeShowing=true;assertTrue(priorityGood().contains(700,1500));assertEquals(0,stack.hitTests);}
    @Test public void inputFirstDoesNotAvoidLockedShadePanelState(){var result=NativeBusinessSnapshot.read(injector,2,true,true,false,true);assertTrue(result.reason,result.allowed);}
    @Test public void inputFirstStillRequiresActualLockedContext(){keyguard.mShowing=false;var result=priority();assertFalse(result.allowed);assertEquals("NATIVE_NOT_KEYGUARD",result.reason);}
    @Test public void inputFirstDoesNotUsePowerOrAodStateAsGestureProtection(){((ShadowPowerManager)Shadow.extract(context.getSystemService(PowerManager.class))).setIsInteractive(false);panel.mDozing=true;assertTrue(priorityGood().contains(700,1500));}
    @Test public void priorityAllowsOrdinaryMediaAnimation(){FrameLayout actual=new FrameLayout(context);shade.addView(actual,new FrameLayout.LayoutParams(900,200));media.singlePaneContainer=actual;layout();actual.setAnimation(new android.view.animation.AlphaAnimation(0,1));assertTrue(priorityGood().contains(700,1500));}
    @Test public void defaultMediaEdgeRemainsProtectedWhilePriorityAllowsIt(){FrameLayout actual=new FrameLayout(context);shade.addView(actual,new FrameLayout.LayoutParams(800,200));actual.setTranslationX(100.6f);actual.setTranslationY(1200.6f);media.singlePaneContainer=actual;layout();int[] xy=new int[2];actual.getLocationOnScreen(xy);assertEquals("NATIVE_MEDIA_REGION",good().point(xy[0]-.5f,xy[1]+20).reason);assertTrue(priorityGood().contains(xy[0]-.5f,xy[1]+20));}
    @Test public void inputFirstDoesNotReadAuthenticationUiLayout(){window.mPrimaryBouncerInteractor.isShowing.value="unknown";assertTrue(priorityGood().contains(700,1500));}
    @Test public void inputFirstDoesNotAvoidBiometricUiForRecognition(){manager.mSecureLockDeviceBiometricAuthVisible=true;assertTrue(priorityGood().contains(700,1500));}
    @Test public void priorityStillRejectsNonfiniteAndOutsideWindowPoints(){var result=priorityGood();assertFalse(result.contains(Float.NaN,1500));assertFalse(result.contains(-1,1500));assertFalse(result.contains(1000,1500));assertFalse(result.contains(500,2000));}
    @Test public void priorityDoesNotRequireOrdinaryFingerprintIconRegion(){monitor.mFingerprintSensorProperties=List.of();assertTrue(priorityGood().contains(500,1000));}
    @Test public void priorityAndDefaultModesCannotContinueSameSequence(){assertFalse(good().sameLayout(priorityGood()));assertFalse(priorityGood().sameLayout(good()));}
    @Test public void defaultModeAlsoRejectsEqualBoundsMediaControllerReplacement(){var before=good();var replacement=new KeyguardMediaController();replacement.visible$delegate=new Flow();panel.mKeyguardMediaController=replacement;assertEquals("NATIVE_BINDING_CHANGED",before.point(700,1500).reason);assertFalse(before.sameLayout(good()));}
    @Test public void shortcutBottomBoundRegionIsProtected(){assertEquals("NATIVE_SHORTCUT_REGION",good().point(700,1920).reason);}
    @Test public void realFullscreenBottomParentAndEmptyOemOverlayDoNotEraseBlank(){assertEquals(2000,((View)bottom.mKeyguardBottomAreaView).getHeight());assertTrue(good().contains(700,1500));}
    @Test public void mismatchedBottomOwnerCannotBeUsed(){bottom.mKeyguardPanelViewInjector=new Object();denied("NATIVE_SHORTCUT_BINDING");}
    @Test public void sensorUninitializedFailsClosed(){monitor.mFingerprintSensorProperties=List.of();denied("NATIVE_FINGERPRINT_UNKNOWN");}
    @Test public void topEdgesAndFingerprintRemainProtected(){NativeBusinessSnapshot snapshot=good();assertEquals("NATIVE_EDGE_TOP_REGION",snapshot.point(700,300).reason);assertEquals("NATIVE_EDGE_TOP_REGION",snapshot.point(5,1500).reason);assertEquals("NATIVE_FINGERPRINT_REGION",snapshot.point(500,1000).reason);}
    @Test public void nonfinitePointNeverReachesPureHit(){NativeBusinessSnapshot snapshot=good();assertEquals("NATIVE_POINT_NONFINITE",snapshot.point(Float.NaN,1500).reason);assertEquals(0,stack.hitTests);}
    @Test public void laterHelperReplacementRevokesSnapshot(){NativeBusinessSnapshot snapshot=good();injector.doubleTapHelper=new DoubleTapHelper();assertEquals("NATIVE_BINDING_CHANGED",snapshot.point(700,1500).reason);assertEquals(0,stack.hitTests);}
    @Test public void laterLocalGeometryChangeCannotUseOldHitTest(){NativeBusinessSnapshot snapshot=good();stack.setTranslationY(25);assertEquals("NATIVE_LOCAL_LAYOUT_CHANGED",snapshot.point(700,1500).reason);assertEquals(0,stack.hitTests);}
    @Test public void sameLayoutIncludesHelperAndProtectionGeometry(){NativeBusinessSnapshot first=good();assertTrue(first.sameLayout(good()));row(300,100,650);assertFalse(first.sameLayout(good()));}
    @Test public void sameLayoutIncludesCurrentHostNotOnlyEqualRectangles(){NativeBusinessSnapshot first=good();DoubleTapHelper next=new DoubleTapHelper();next.mView=root;next.mDoubleTapListener=listener;injector.doubleTapHelper=next;assertFalse(first.sameLayout(good()));}
    @Test public void hiddenMediaBecomingVisibleCancelsOldRegion(){FrameLayout hidden=new FrameLayout(context);hidden.setVisibility(View.GONE);shade.addView(hidden,new FrameLayout.LayoutParams(200,200));media.singlePaneContainer=hidden;layout();NativeBusinessSnapshot snapshot=good();hidden.setVisibility(View.VISIBLE);layout();assertEquals("NATIVE_LOCAL_LAYOUT_CHANGED",snapshot.point(700,1500).reason);}
}
