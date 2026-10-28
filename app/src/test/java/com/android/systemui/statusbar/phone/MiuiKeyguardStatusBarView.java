package com.android.systemui.statusbar.phone;

/** 最小宿主同名夹具，只复现已核final onLayout硬裁剪；不是OEM源码执行。 */
public final class MiuiKeyguardStatusBarView extends android.widget.FrameLayout {
    public MiuiKeyguardStatusBarView(android.content.Context context){super(context);setClipChildren(false);setClipToPadding(false);}
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        super.onLayout(changed,left,top,right,bottom);
        setClipBounds(new android.graphics.Rect(0,0,getWidth(),getHeight()));
    }
}
