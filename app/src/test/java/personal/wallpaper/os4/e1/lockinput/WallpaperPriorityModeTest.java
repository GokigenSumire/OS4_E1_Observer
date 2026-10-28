package personal.wallpaper.os4.e1.lockinput;
import org.junit.Test;
import static org.junit.Assert.*;
public final class WallpaperPriorityModeTest {
    @Test public void onlyFixedAuthenticatedMarkersEnablePriority(){
        for(String value:new String[]{"READY_WALLPAPER_PRIORITY","READY_NATIVE_INPUT+READY_WALLPAPER_PRIORITY","READY_NATIVE_INPUT+READY_FORCE_LOCAL+READY_WALLPAPER_PRIORITY"})assertTrue(value,WallpaperPriorityMode.enabled(value));
        for(String value:new String[]{null,"READY","READY_FORCE_LOCAL","READY_NATIVE_INPUT","READY_NATIVE_INPUT+READY_PRIORITY","ready_wallpaper_priority","READY_WALLPAPER_PRIORITY ","NO_ENGINE"})assertFalse(value,WallpaperPriorityMode.enabled(value));
    }
    @Test public void nativeMarkerCarriesOnlyItsDeclaredCapabilities(){
        assertTrue(WallpaperPriorityMode.nativeInput("READY_NATIVE_INPUT"));
        assertTrue(WallpaperPriorityMode.nativeInput("READY_NATIVE_INPUT+READY_FORCE_LOCAL+READY_WALLPAPER_PRIORITY"));
        assertFalse(WallpaperPriorityMode.nativeInput("READY_WALLPAPER_PRIORITY"));
        assertTrue(WallpaperPriorityMode.forceLocal("READY_NATIVE_INPUT+READY_FORCE_LOCAL"));
        assertFalse(WallpaperPriorityMode.forceLocal("READY_NATIVE_INPUT+READY_WALLPAPER_PRIORITY"));
    }
    @Test public void priorityAlsoUsesOriginalTopBanButNotOtherWayRound(){assertTrue(ForceLocalRegion.enabled("READY_WALLPAPER_PRIORITY"));assertTrue(ForceLocalRegion.enabled("READY_FORCE_LOCAL"));assertTrue(ForceLocalRegion.enabled("READY_NATIVE_INPUT+READY_FORCE_LOCAL"));assertFalse(WallpaperPriorityMode.enabled("READY_FORCE_LOCAL"));}
    @Test public void fixedCausesAreInputOnlyAndRemainUnderLineBudget(){for(int code=PriorityInputDetail.BASE;PriorityInputDetail.valid(code);code++){assertEquals(code,LockDiagnosticCause.code(PriorityInputDetail.label(code)));assertTrue(LockDiagnosticCause.validPair(0,code));for(int stage=1;stage<=4;stage++)assertFalse(LockDiagnosticCause.validPair(stage,code));String row="row=2000 stage=INPUT cause="+LockDiagnosticCause.label(code)+" total="+Long.MAX_VALUE+"\n";assertTrue(row,row.length()<=128);}assertEquals(0,PriorityInputDetail.code("secret/value"));}
}
