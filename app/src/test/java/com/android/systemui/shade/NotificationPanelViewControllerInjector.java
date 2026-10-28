package com.android.systemui.shade;
public final class NotificationPanelViewControllerInjector {
    public Object panelView,controller;
    public NotificationPanelViewController getPanelViewController(){return (NotificationPanelViewController)controller;}
}
