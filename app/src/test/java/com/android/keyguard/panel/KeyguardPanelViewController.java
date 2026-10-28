package com.android.keyguard.panel;

/** 对应onKeyguardViewBind维护的真实双向字段结构。 */
public final class KeyguardPanelViewController {
    public Object keyguardPanelViewInjector,keyguardRootView,notificationShadeWindowView,keyguardUpdateMonitor,keyguardStateController,statusBarKeyguardViewManager;
    public Object notificationStackScrollLayoutController,notificationStackScrollLayout;
    public boolean keyguardShowing=true,keyguardBouncerShowing,hasNotification;
    public Object wakeupAnimState,unlockAlphaAndScaleAnimator,unlockBlurAnimator,unlockSmartScaleAnimator,unlockSmartTransAnimator,bouncerFractionAnimator;
    public void onKeyguardViewBind(androidx.constraintlayout.widget.ConstraintLayout view){ }
    public void onViewDetachedFromWindow(android.view.View view){ }
}
