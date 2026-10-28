package personal.wallpaper.os4.e1.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class FormalWallpaperTargetTest {
    @Test public void formalOwnerRequiresFormalExpectedPackage(){assertTrue(HomeScope.targetWallpaperAllowed("personal.wallpaper.os3",2013,0,true,true,"personal.wallpaper.os3"));}
    @Test public void formalNeverAuthorizesDebugOwner(){assertFalse(HomeScope.targetWallpaperAllowed("personal.wallpaper.os3.debug",2013,0,true,true,"personal.wallpaper.os3"));}
    @Test public void debugNeverAuthorizesFormalOwner(){assertFalse(HomeScope.targetWallpaperAllowed("personal.wallpaper.os3",2013,0,true,true,"personal.wallpaper.os3.debug"));}
    @Test public void expectedPackageMustBeAllowlisted(){for(String p:new String[]{null,"","other.package","personal.wallpaper.os3 ","personal.wallpaper.os3.prod"})assertFalse(HomeScope.targetWallpaperAllowed(p,2013,0,true,true,p));}
    @Test public void bothTokenAndDisplayGatesRemain(){for(int flags=0;flags<8;flags++)assertEquals(flags==7,HomeScope.targetWallpaperAllowed("personal.wallpaper.os3",2013,(flags&1)!=0?0:1,(flags&2)!=0,(flags&4)!=0,"personal.wallpaper.os3"));}
    @Test public void typeIsStillWallpaper(){assertFalse(HomeScope.targetWallpaperAllowed("personal.wallpaper.os3",1,0,true,true,"personal.wallpaper.os3"));}
}
