package personal.wallpaper.os4.e1.lockinput;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public final class BackgroundRegionTest {
    private BackgroundRegion.Box box(int l,int t,int r,int b){return new BackgroundRegion.Box(l,t,r,b);}
    @Test public void positiveBackgroundRequired(){BackgroundRegion r=new BackgroundRegion(box(10,10,90,90),List.of());assertTrue(r.hasSpace());assertTrue(r.contains(50,50));assertFalse(r.contains(0,0));}
    @Test public void foregroundExcludesItsWholeRect(){BackgroundRegion r=new BackgroundRegion(box(0,0,100,100),List.of(box(20,20,80,80)));assertFalse(r.contains(30,30));assertTrue(r.contains(10,30));}
    @Test public void fullCoverageIsNotBackground(){assertFalse(new BackgroundRegion(box(0,0,100,100),List.of(box(0,0,100,100))).hasSpace());}
    @Test public void unionCoverageIsNotBackground(){assertFalse(new BackgroundRegion(box(0,0,100,100),List.of(box(0,0,50,100),box(50,0,100,100))).hasSpace());}
    @Test public void verticalUnionCoverageIsNotBackground(){assertFalse(new BackgroundRegion(box(0,0,100,100),List.of(box(0,0,100,50),box(0,50,100,100))).hasSpace());}
    @Test public void overlappingCoverageIsNotBackground(){assertFalse(new BackgroundRegion(box(0,0,100,100),List.of(box(-1,-1,70,101),box(50,0,101,100))).hasSpace());}
    @Test public void narrowVerifiedGapIsPresent(){assertTrue(new BackgroundRegion(box(0,0,100,100),List.of(box(0,0,49,100),box(50,0,100,100))).hasSpace());}
    @Test public void outsideForegroundDoesNotHideBackground(){assertTrue(new BackgroundRegion(box(0,0,100,100),List.of(box(101,101,200,200))).hasSpace());}
    @Test public void edgesUseHalfOpenBounds(){BackgroundRegion r=new BackgroundRegion(box(0,0,100,100),List.of(box(20,20,30,30)));assertFalse(r.contains(100,50));assertFalse(r.contains(20,20));assertTrue(r.contains(30,20));}
    @Test public void invalidPointCannotEnter(){BackgroundRegion r=new BackgroundRegion(box(0,0,100,100),List.of());assertFalse(r.contains(Float.NaN,2));assertFalse(r.contains(2,Float.POSITIVE_INFINITY));}
    @Test(expected=IllegalArgumentException.class) public void emptyPositiveRejected(){box(0,0,0,10);}
    @Test(expected=IllegalArgumentException.class) public void missingForegroundEvidenceRejected(){new BackgroundRegion(box(0,0,100,100),null);}
    @Test public void keepsForegroundImmutable(){java.util.ArrayList<BackgroundRegion.Box> holes=new java.util.ArrayList<>();holes.add(box(20,20,30,30));BackgroundRegion r=new BackgroundRegion(box(0,0,100,100),holes);holes.clear();assertFalse(r.contains(25,25));}
}
