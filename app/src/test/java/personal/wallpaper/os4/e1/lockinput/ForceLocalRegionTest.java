package personal.wallpaper.os4.e1.lockinput;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ForceLocalRegionTest {
    @Test public void upperFifthRoundsUpAndNeverUnderExcludes(){
        assertEquals(1,ForceLocalRegion.topBoundary(1));assertEquals(1,ForceLocalRegion.topBoundary(5));
        assertEquals(2,ForceLocalRegion.topBoundary(6));assertEquals(400,ForceLocalRegion.topBoundary(2000));
        assertEquals(401,ForceLocalRegion.topBoundary(2001));
    }
    @Test public void maximumDisplayHeightDoesNotOverflow(){assertEquals(429496730,ForceLocalRegion.topBoundary(Integer.MAX_VALUE));}
    @Test public void invalidDisplayHeightIsNotAnEmptyBan(){for(int value:new int[]{0,-1,Integer.MIN_VALUE})try{ForceLocalRegion.topBoundary(value);fail();}catch(IllegalArgumentException expected){}}
    @Test public void forceModeRequiresExactAuthenticatedReason(){
        assertTrue(ForceLocalRegion.enabled("READY_FORCE_LOCAL"));
        for(String reason:new String[]{null,"READY","ready_force_local","READY_FORCE_LOCAL ","NO_ENGINE","UNAUTHORIZED_OR_NOT_READY"})assertFalse(ForceLocalRegion.enabled(reason));
    }
}
