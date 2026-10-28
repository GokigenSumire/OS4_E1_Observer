package personal.wallpaper.os4.e1.lockinput;

import android.graphics.Rect;
import android.view.View;

/** 用户选择的风险兼容路径：20%禁区＋精确STATUS整父排除，不宣硬裁剪证明。 */
final class ForceLocalRegion {
    static final String READY="READY_FORCE_LOCAL";
    private ForceLocalRegion(){ }
    static int topBoundary(int displayHeight){
        if(displayHeight<=0)throw new IllegalArgumentException("DISPLAY_HEIGHT_UNKNOWN");
        return (int)(((long)displayHeight+4)/5);
    }
    static boolean constrain(Rect area,int displayHeight){
        int top=topBoundary(displayHeight);
        if(area==null||area.isEmpty())return false;
        if(area.bottom<=top)return false;
        area.top=Math.max(area.top,top);return !area.isEmpty();
    }
    static boolean enabled(String authenticatedReason){return WallpaperPriorityMode.forceLocal(authenticatedReason)||WallpaperPriorityMode.enabled(authenticatedReason);}
    static Rect statusExclusion(View root,View status){
        try{
            if(root==null||status==null||status.getParent()!=root
                    ||!"com.android.systemui.shade.NotificationShadeWindowView".equals(root.getClass().getName())
                    ||!"com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView".equals(status.getClass().getName())
                    ||root.getClass().getClassLoader()!=status.getClass().getClassLoader())return null;
            int id=root.getResources().getIdentifier("keyguard_status_bar","id","com.android.systemui");
            if(id==0||status.getId()!=id||root.findViewById(id)!=status)return null;
            int[] xy=new int[2];status.getLocationOnScreen(xy);
            int[] origin=new int[2];root.getLocationOnScreen(origin);
            long right=(long)xy[0]+status.getWidth(),bottom=(long)xy[1]+status.getHeight();
            long rootRight=(long)origin[0]+root.getWidth(),rootBottom=(long)origin[1]+root.getHeight();
            if(status.getWidth()<=0||status.getHeight()<=0||root.getWidth()<=0||root.getHeight()<=0
                    ||right>Integer.MAX_VALUE||bottom>Integer.MAX_VALUE||rootRight>Integer.MAX_VALUE||rootBottom>Integer.MAX_VALUE
                    ||xy[0]<origin[0]||xy[1]<origin[1]||right>rootRight||bottom>rootBottom)return null;
            return new Rect(xy[0],xy[1],(int)right,(int)bottom);
        }catch(RuntimeException ignored){return null;}
    }
}
