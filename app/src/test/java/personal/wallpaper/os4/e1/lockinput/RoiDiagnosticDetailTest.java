package personal.wallpaper.os4.e1.lockinput;

import java.util.HashSet;
import org.junit.Test;
import static org.junit.Assert.*;

public final class RoiDiagnosticDetailTest {
    @Test public void everyTupleIsUniqueInputOnlyAndFitsOriginalLineLimit(){
        HashSet<Integer> seen=new HashSet<>();
        for(int mode=0;mode<2;mode++)for(int full=0;full<2;full++)for(int why=0;why<8;why++)for(int top=0;top<24;top++)for(int node=0;node<24;node++){
            int code=RoiDiagnosticDetail.code(mode==1,full==1,why,top,node);
            assertTrue(seen.add(code));assertTrue(RoiDiagnosticDetail.valid(code));assertFalse(LayoutDiagnosticDetail.valid(code));
            assertEquals(RoiDiagnosticDetail.label(code),LockDiagnosticCause.label(code));
            assertTrue(LockDiagnosticCause.validPair(0,code));for(int stage=1;stage<=4;stage++)assertFalse(LockDiagnosticCause.validPair(stage,code));
            String row="row=2000 stage=INPUT cause="+LockDiagnosticCause.label(code)+" total="+Long.MAX_VALUE+"\n";
            assertTrue(row,row.length()<=128);assertTrue(row.matches("[A-Za-z0-9_= \n]+"));
        }
        assertEquals(18432,seen.size());
    }
    @Test public void markersAreFixedAndCannotBecomeSourceOrHookEvents(){
        for(int code:new int[]{RoiDiagnosticDetail.BATCH_COMPLETE,RoiDiagnosticDetail.MODE_FORCE,RoiDiagnosticDetail.MODE_CONSERVATIVE,RoiDiagnosticDetail.SINGLE_COVER,RoiDiagnosticDetail.UNION_COVER,RoiDiagnosticDetail.INCOMPLETE}){
            assertTrue(LockDiagnosticCause.validPair(0,code));for(int stage=1;stage<=4;stage++)assertFalse(LockDiagnosticCause.validPair(stage,code));assertTrue(LockDiagnosticCause.label(code).startsWith("ROI_"));
        }
    }
    @Test public void invalidTupleDoesNotNameAnArbitraryView(){
        for(int bad:new int[]{-1,24,Integer.MIN_VALUE,Integer.MAX_VALUE}){assertEquals(RoiDiagnosticDetail.INCOMPLETE,RoiDiagnosticDetail.code(true,true,0,bad,0));assertEquals(RoiDiagnosticDetail.INCOMPLETE,RoiDiagnosticDetail.code(true,true,0,0,bad));}
        for(int bad:new int[]{-1,8,Integer.MAX_VALUE})assertEquals(RoiDiagnosticDetail.INCOMPLETE,RoiDiagnosticDetail.code(false,false,bad,0,0));
    }
    @Test public void codeNamespacesAndUnknownLabelsStayBounded(){
        for(int code:new int[]{Integer.MIN_VALUE,-1,0,4096,8703,8704,16376,34816,Integer.MAX_VALUE})assertFalse(RoiDiagnosticDetail.valid(code));
        assertEquals("ROI_DETAIL_INCOMPLETE",RoiDiagnosticDetail.label(Integer.MAX_VALUE));assertEquals("OTHER",LayoutDiagnosticDetail.nodeLabel(-1));
    }
    @Test public void ringAcceptsBothModeSourcesAndDoesNotRecordAfterStop(){
        LockDiagnosticRing ring=new LockDiagnosticRing();String token=ring.start(0).token;
        int cause=RoiDiagnosticDetail.code(false,true,RoiDiagnosticDetail.FINGERPRINT,19,19);
        assertTrue(ring.record(token,0,0,cause,Long.MAX_VALUE));LockDiagnosticRing.State stopped=ring.stop(token,1);
        assertEquals(1,ring.page(token,stopped.revision,0,1).size());assertFalse(ring.record(token,2,0,cause,1));
    }
}
