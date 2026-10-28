package com.android.systemui.biometrics;
public final class AuthRippleView extends android.view.View {
    public boolean drawDwell,drawRipple;
    public android.animation.Animator dwellPulseOutAnimator,fadeDwellAnimator,retractDwellAnimator,unlockedRippleAnimator;
    public AuthRippleView(android.content.Context context){super(context);setVisibility(GONE);}
}
