package com.android.systemui.statusbar.notification.stack;
public final class NotificationStackScrollLayout extends android.widget.FrameLayout {
    public Object mController;
    public boolean mAnimationRunning,mNeedsAnimation,mIsExpansionChanging;
    public java.util.Collection<?> mChildrenToAddAnimated=java.util.List.of(),mChildrenToRemoveAnimated=java.util.List.of(),mSwipedOutViews=java.util.List.of();
    public boolean mChildrenUpdateRequested;
    public java.util.Collection<?> mChildrenChangingPositions=java.util.List.of(),mAnimationEvents=java.util.List.of();
    public Object mAmbientState;
    public int hitTests;
    public boolean lastRequireMinHeight,lastIgnoreDecor,lastWholeWidth;
    public NotificationStackScrollLayout(android.content.Context context){super(context);}
    /** 按OEM5995-6198的直接child/clip/translation/actualHeight规则；没有业务副作用。 */
    public com.android.systemui.statusbar.notification.row.ExpandableView getChildAtPosition(float x,float y,boolean requireMinHeight,boolean ignoreDecor,boolean wholeWidth){
        hitTests++;lastRequireMinHeight=requireMinHeight;lastIgnoreDecor=ignoreDecor;lastWholeWidth=wholeWidth;
        for(int i=0;i<getChildCount();i++){
            android.view.View candidate=getChildAt(i);
            if(!(candidate instanceof com.android.systemui.statusbar.notification.row.ExpandableView))continue;
            com.android.systemui.statusbar.notification.row.ExpandableView child=(com.android.systemui.statusbar.notification.row.ExpandableView)candidate;
            if(child.getVisibility()!=android.view.View.VISIBLE)continue;
            if(ignoreDecor&&candidate instanceof com.android.systemui.statusbar.notification.row.StackScrollerDecorView)continue;
            float top=child.getSuperTranslationY()+Math.max(child.getClipTopAmount(),child.getTopOverlap());
            float bottom=child.getSuperTranslationY()+child.getActualHeight()-Math.max(child.getClipBottomAmount(),child.getBottomOverlap());
            if(requireMinHeight&&bottom-top<1)continue;
            float left=wholeWidth?0:child.getLeft(),right=wholeWidth?getWidth():child.getRight();
            if(x>=left&&x<=right&&y>=top&&y<=bottom)return child;
        }
        return null;
    }
}
