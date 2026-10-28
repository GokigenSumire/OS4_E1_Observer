package com.android.keyguard.shortcut;

/** 对应addShortcutViews保存的容器与getKeyguardPanelViewInjector回指。 */
public final class MiuiShortcutController {
    public Object injector,shortcutContainerView;
    public Object getKeyguardPanelViewInjector(){return injector;}
}
