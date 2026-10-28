package personal.wallpaper.os4.e1.lockinput;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** 纯几何正向矩形减去全部前景；不把未命中按钮等同背景。 */
public final class BackgroundRegion {
    public static final class Box {
        final int left,top,right,bottom;
        public Box(int left,int top,int right,int bottom){
            if(right<=left||bottom<=top)throw new IllegalArgumentException("EMPTY_BOX");
            this.left=left;this.top=top;this.right=right;this.bottom=bottom;
        }
        boolean contains(float x,float y){return x>=left&&x<right&&y>=top&&y<bottom;}
    }
    private final Box background;
    private final List<Box> foreground;
    public BackgroundRegion(Box background,List<Box> foreground){
        if(background==null||foreground==null||foreground.size()>512)throw new IllegalArgumentException("REGION_UNVERIFIED");
        for(Box box:foreground)if(box==null)throw new IllegalArgumentException("REGION_UNVERIFIED");
        this.background=background;this.foreground=List.copyOf(foreground);
    }
    public boolean contains(float x,float y){
        if(!Float.isFinite(x)||!Float.isFinite(y)||!background.contains(x,y))return false;
        for(Box box:foreground)if(box.contains(x,y))return false;
        return true;
    }
    /** 有界纵切片求并集，确认正向允许区域真的非空。 */
    public boolean hasSpace(){
        TreeSet<Integer> boundaries=new TreeSet<>();boundaries.add(background.left);boundaries.add(background.right);
        for(Box box:foreground){if(box.right>background.left&&box.left<background.right){boundaries.add(Math.max(box.left,background.left));boundaries.add(Math.min(box.right,background.right));}}
        Integer previous=null;
        for(int boundary:boundaries){
            if(previous!=null&&boundary>previous){
                double x=((double)previous+boundary)/2;
                ArrayList<Box> intersecting=new ArrayList<>();
                for(Box box:foreground)if(x>=box.left&&x<box.right&&box.bottom>background.top&&box.top<background.bottom)intersecting.add(box);
                intersecting.sort(java.util.Comparator.comparingInt(box->box.top));
                int y=background.top;
                for(Box box:intersecting){if(box.top>y)return true;y=Math.max(y,box.bottom);if(y>=background.bottom)break;}
                if(y<background.bottom)return true;
            }
            previous=boundary;
        }
        return false;
    }
}
