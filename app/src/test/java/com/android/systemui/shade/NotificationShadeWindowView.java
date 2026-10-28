package com.android.systemui.shade;
public final class NotificationShadeWindowView extends android.widget.FrameLayout {
    public Object mInteractionEventHandler;
    public android.view.WindowInsets testInsets;
    public NotificationShadeWindowView(android.content.Context context){super(context);}
    @Override public android.view.WindowInsets getRootWindowInsets(){return testInsets==null?super.getRootWindowInsets():testInsets;}
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){return super.dispatchTouchEvent(event);}
}
