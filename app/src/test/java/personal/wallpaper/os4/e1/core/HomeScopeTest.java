package personal.wallpaper.os4.e1.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomeScopeTest {
    @Test public void exactHomeAllowed() {assertTrue(HomeScope.allowed("com.miui.home",1,0,"com.miui.home",2));}
    @Test public void otherPackageRejected() {assertFalse(HomeScope.allowed("other",1,0,"com.miui.home",2));}
    @Test public void overlayRejected() {assertFalse(HomeScope.allowed("com.miui.home",2038,0,"com.miui.home",2));}
    @Test public void otherDisplayRejected() {assertFalse(HomeScope.allowed("com.miui.home",1,1,"com.miui.home",2));}
    @Test public void nonHomeActivityRejected() {assertFalse(HomeScope.allowed("com.miui.home",1,0,"com.miui.home",1));}
    @Test public void otherActivityOwnerRejected() {assertFalse(HomeScope.allowed("com.miui.home",1,0,"other",2));}
    @Test public void unknownNeverFalseInferred() {assertFalse(HomeScope.allowed("com.miui.home",1,0,null,null));}
    @Test public void malformedTypeRejected() {assertFalse(HomeScope.allowed("com.miui.home","1",0,"com.miui.home",2));}
    @Test public void stoppedSameSessionExportAllowed() {assertTrue(HomeScope.exportAllowed(2,2,3,3,false));}
    @Test public void newSessionExportRejected() {assertFalse(HomeScope.exportAllowed(2,3,3,3,false));}
    @Test public void rowsChangedExportRejected() {assertFalse(HomeScope.exportAllowed(2,2,3,4,false));}
    @Test public void activeExportRejected() {assertFalse(HomeScope.exportAllowed(2,2,3,3,true));}
    @Test public void uninitializedExportRejected() {assertFalse(HomeScope.exportAllowed(0,0,0,0,false));}
}
