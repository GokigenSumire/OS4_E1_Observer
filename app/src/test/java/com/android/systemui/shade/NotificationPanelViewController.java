package com.android.systemui.shade;

/** 来自目标OEM NotificationPanelViewController的当前业务对象图。 */
public final class NotificationPanelViewController {
    public Object mKeyguardPanelViewInjector,mNotificationStackScrollLayoutController,mKeyguardStateController,mAlternateBouncerInteractor;
    public Object mStatusBarKeyguardViewManager,mCentralSurfaces,mKeyguardMediaController;
    public Object mView,mTouchHandler;
    public int mBarState=1;
    public boolean mDozing,expanding,tracking;
    public boolean isExpandingOrCollapsing(){return expanding;}
    public boolean isTracking(){return tracking;}
    public static final class TouchHandler {
        public Object this$0;
        public boolean onInterceptTouchEvent(android.view.MotionEvent event){return false;}
        public boolean onTouchEvent(android.view.MotionEvent event){return true;}
    }
}
