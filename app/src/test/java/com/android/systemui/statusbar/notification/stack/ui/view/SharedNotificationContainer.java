package com.android.systemui.statusbar.notification.stack.ui.view;
public final class SharedNotificationContainer extends android.widget.FrameLayout {
    public SharedNotificationContainerInjector injector;
    public SharedNotificationContainer(android.content.Context context){super(context);injector=new SharedNotificationContainerInjector(this);}
}
