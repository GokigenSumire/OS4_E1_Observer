package personal.wallpaper.os4.e1.relay;

import org.junit.Test;
import personal.wallpaper.os4.e1.lockinput.NativeInputDetail;
import static org.junit.Assert.*;

public final class HostOutcomeTotalsTest {
    @Test public void resultsCountIndependentlyAndNewCaptureStartsAtOne(){
        var totals=new HostOutcomeTotals();int committed=NativeInputDetail.commandResult("COMMITTED"),failed=NativeInputDetail.commandResult("FAILED");
        assertEquals(1,totals.next("capture-A",committed));assertEquals(2,totals.next("capture-A",committed));assertEquals(1,totals.next("capture-A",failed));
        assertEquals(1,totals.next("capture-B",committed));totals.stopped();assertEquals(1,totals.next("capture-B",committed));
    }
    @Test public void arbitraryInputAndEditCodesCannotBecomeHostResultSlots(){
        var totals=new HostOutcomeTotals();
        for(int code:new int[]{0,-1,NativeInputDetail.code("NATIVE_EVENT_DOWN"),NativeInputDetail.code("NATIVE_AUTH_ERROR"),Integer.MAX_VALUE})assertEquals(0,totals.next("capture",code));
        assertEquals(0,totals.next("",NativeInputDetail.commandResult("COMMITTED")));
        assertEquals(1,totals.next("capture",NativeInputDetail.commandResult("COMMITTED")));
    }
}
