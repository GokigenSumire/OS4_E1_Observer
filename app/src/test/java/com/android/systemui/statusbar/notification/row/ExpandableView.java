package com.android.systemui.statusbar.notification.row;

/** OEM业务hit-test的共同父类结构，几何使用真实Android View。 */
public class ExpandableView extends com.miui.systemui.widget.PropertiesFrameLayout {
    public int clipTop,clipBottom,topOverlap,bottomOverlap;
    public ExpandableView(android.content.Context context){super(context);}
    public int getActualHeight(){return getHeight();}
    public int getClipTopAmount(){return clipTop;}
    public int getClipBottomAmount(){return clipBottom;}
    public int getTopOverlap(){return topOverlap;}
    public int getBottomOverlap(){return bottomOverlap;}
}
