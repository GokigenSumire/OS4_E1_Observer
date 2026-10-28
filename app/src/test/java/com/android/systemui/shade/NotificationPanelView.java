package com.android.systemui.shade;

/** 精确dispatch签名测试外壳；不模拟OEM分发策略。 */
public final class NotificationPanelView extends android.widget.FrameLayout {
    public Object mTouchHandler;
    public NotificationPanelView(android.content.Context context){super(context);}
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){return super.dispatchTouchEvent(event);}
}
