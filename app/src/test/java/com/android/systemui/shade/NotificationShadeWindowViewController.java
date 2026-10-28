package com.android.systemui.shade;

/** 同机OEM字段及精确内部取消签名外壳；不执行系统取消或反射伪事件。 */
public final class NotificationShadeWindowViewController {
    public Object mView,mNPVCInjector,mPrimaryBouncerInteractor,mAodInterceptingTouches;
    public Object mService,mStatusBarStateController,mBrightnessMirror;
    public boolean mTouchActive=true,mTouchCancelled;
    public boolean mHandlingExternalTouch,mExternalTouchIntercepted,mIsOcclusionTransitionRunning,mExpandAnimationRunning,mIsTrackingBarGesture;
    public void cancelCurrentTouch$1(){ }
}
