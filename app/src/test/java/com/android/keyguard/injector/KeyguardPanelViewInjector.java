package com.android.keyguard.injector;

/** 字段及getters来自目标OEM smali；只承载测试绑定，不执行OEM动作。 */
public final class KeyguardPanelViewInjector {
    public Object controller,panel,keyguardRootView,notificationShadeWindowView,doubleTapHelper,keyguardUpdateMonitor;
    public Object keyguardEditorHelper$delegate,quickSettingsControllerImpl,controlCenterImpl,keyguardMoveHelper,keyguardBottomAreaInjector$delegate;
    public boolean editLoading;
    public com.android.keyguard.panel.KeyguardPanelViewController getKeyguardPanelViewController(){return (com.android.keyguard.panel.KeyguardPanelViewController)controller;}
    public com.android.systemui.shade.NotificationPanelViewController getNotificationPanelViewController(){return (com.android.systemui.shade.NotificationPanelViewController)panel;}
    public boolean isShowEditLoading(){return editLoading;}
    public boolean onTouchEvent(android.view.MotionEvent event,int state,float x,float y,boolean expanding,boolean qs,boolean dozing){return false;}
}
