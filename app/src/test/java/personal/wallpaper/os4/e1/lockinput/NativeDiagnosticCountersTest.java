package personal.wallpaper.os4.e1.lockinput;

import java.lang.reflect.Field;
import java.util.HashSet;
import org.junit.Test;
import static org.junit.Assert.*;

public final class NativeDiagnosticCountersTest {
    private final String token="diagnostic-session";
    private NativeDiagnosticCounters active(){NativeDiagnosticCounters counters=new NativeDiagnosticCounters();counters.session(token,true);return counters;}
    private int code(int index){return NativeInputDetail.BASE+index;}
    @Test public void inactiveStartsWithNoPendingRows(){NativeDiagnosticCounters counters=new NativeDiagnosticCounters();counters.hit(code(0));assertEquals(0,counters.pending("",16).length);}
    @Test public void hitProducesFixedCodeAndCumulativeTotal(){var counters=active();counters.hit(code(0));counters.hit(code(0));var rows=counters.pending(token,16);assertEquals(1,rows.length);assertEquals(code(0),rows[0].code);assertEquals(2,rows[0].total);}
    @Test public void invalidCodesCannotCreateSlots(){var counters=active();for(int code:new int[]{-1,0,NativeInputDetail.BASE-1,NativeInputDetail.BASE+NativeInputDetail.count(),Integer.MAX_VALUE})counters.hit(code);assertEquals(0,counters.pending(token,16).length);}
    @Test public void eachBatchIsAtMostSixteenAndAllSlotsRemainReachable(){var counters=active();for(int i=0;i<NativeInputDetail.count();i++)counters.hit(code(i));HashSet<Integer> seen=new HashSet<>();for(int round=0;round<20;round++){var rows=counters.pending(token,16);assertTrue(rows.length<=16);for(var row:rows){assertTrue(seen.add(row.code));counters.acknowledge(token,row);}}assertEquals(NativeInputDetail.count(),seen.size());assertEquals(0,counters.pending(token,16).length);}
    @Test public void invalidLimitsAndWrongSessionReturnNothing(){var counters=active();counters.hit(code(0));for(int limit:new int[]{-1,0,17,Integer.MAX_VALUE})assertEquals(0,counters.pending(token,limit).length);assertEquals(0,counters.pending("stale",16).length);}
    @Test public void unacknowledgedRowsAreNotLostByRead(){var counters=active();counters.hit(code(0));assertEquals(1,counters.pending(token,16).length);assertEquals(1,counters.pending(token,16).length);}
    @Test public void acknowledgementOnlyConfirmsCapturedTotal(){var counters=active();counters.hit(code(0));var row=counters.pending(token,16)[0];counters.hit(code(0));counters.acknowledge(token,row);var pending=counters.pending(token,16);assertEquals(1,pending.length);assertEquals(2,pending[0].total);counters.acknowledge(token,pending[0]);assertEquals(0,counters.pending(token,16).length);}
    @Test public void wrongSessionNullInvalidAndFutureAckCannotHidePending(){var counters=active();counters.hit(code(0));var row=counters.pending(token,16)[0];counters.acknowledge("other",row);counters.acknowledge(token,null);counters.acknowledge(token,new NativeDiagnosticCounters.Row(0,1));counters.acknowledge(token,new NativeDiagnosticCounters.Row(code(0),2));assertEquals(1,counters.pending(token,16).length);}
    @Test public void oldAcknowledgementCannotRollBackAlreadyConfirmedTotal(){var counters=active();counters.hit(code(0));var first=counters.pending(token,16)[0];counters.hit(code(0));var second=counters.pending(token,16)[0];counters.acknowledge(token,second);counters.acknowledge(token,first);assertEquals(0,counters.pending(token,16).length);}
    @Test public void stoppingImmediatelyStopsHitPendingAndAck(){var counters=active();counters.hit(code(0));var row=counters.pending(token,16)[0];counters.session(token,false);counters.hit(code(0));counters.acknowledge(token,row);assertEquals(0,counters.pending(token,16).length);counters.session(token,true);assertEquals(1,counters.pending(token,16)[0].total);}
    @Test public void newSessionClearsOldTotalsAndLateAck(){var counters=active();counters.hit(code(0));var old=counters.pending(token,16)[0];counters.session("new-session",true);counters.acknowledge(token,old);assertEquals(0,counters.pending("new-session",16).length);counters.hit(code(0));assertEquals(1,counters.pending("new-session",16)[0].total);}
    @Test public void repeatedSameActiveSessionDoesNotEraseUnacknowledgedRows(){var counters=active();counters.hit(code(0));counters.session(token,true);assertEquals(1,counters.pending(token,16).length);}
    @Test public void longMaxSaturatesRatherThanWrapping()throws Exception{var counters=active();Field field=NativeDiagnosticCounters.class.getDeclaredField("totals");field.setAccessible(true);long[] totals=(long[])field.get(counters);totals[0]=Long.MAX_VALUE-1;counters.hit(code(0));counters.hit(code(0));var row=counters.pending(token,16)[0];assertEquals(Long.MAX_VALUE,row.total);counters.acknowledge(token,row);assertEquals(0,counters.pending(token,16).length);}
}
