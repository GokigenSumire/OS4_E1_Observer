package com.android.keyguard;

/** 对应OEM创建绑定时的两个真实final引用；测试可替换用于注入宿主重建。 */
public final class DoubleTapHelper {
    public Object mView,mDoubleTapListener;
    public void onTouchEvent(android.view.MotionEvent event){ }
}
