package com.android.systemui.scrim;
/** 同机公开字段/视觉行为的宿主fixture；不执行OEM DEX或替代手机证据。 */
public final class ScrimView extends android.view.View {
    public float mViewAlpha=1f;
    public android.graphics.drawable.Drawable mDrawable=new android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK);
    public ScrimView(android.content.Context context){super(context);}
    public void setVisualAlpha(float alpha){mViewAlpha=alpha;mDrawable.setAlpha((int)(255*alpha));}
}
