package personal.wallpaper.os4.e1.lockinput;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public final class RoiCoverageRecorderTest {
    private RoiCoverageRecorder recorder(boolean force){return new RoiCoverageRecorder(new BackgroundRegion.Box(10,20,100,200),force);}
    private boolean has(int[] codes,int code){return Arrays.stream(codes).anyMatch(value->value==code);}
    @Test public void exactFullBlockRecordsModeReasonAndOwner(){
        RoiCoverageRecorder value=recorder(true);value.record(2,18,RoiDiagnosticDetail.BACKGROUND,new BackgroundRegion.Box(10,20,100,200));int[] details=value.emptyDetails();
        assertTrue(has(details,RoiDiagnosticDetail.MODE_FORCE));assertTrue(has(details,RoiDiagnosticDetail.SINGLE_COVER));assertTrue(has(details,RoiDiagnosticDetail.code(true,true,2,2,18)));assertFalse(has(details,RoiDiagnosticDetail.UNION_COVER));
    }
    @Test public void twoPartialBlocksDoNotPretendEitherCoversWholeArea(){
        RoiCoverageRecorder value=recorder(false);value.record(2,0,3,new BackgroundRegion.Box(10,20,50,200));value.record(19,0,3,new BackgroundRegion.Box(50,20,100,200));int[] details=value.emptyDetails();
        assertTrue(has(details,RoiDiagnosticDetail.UNION_COVER));assertFalse(has(details,RoiDiagnosticDetail.SINGLE_COVER));assertTrue(has(details,RoiDiagnosticDetail.MODE_CONSERVATIVE));assertEquals(4,details.length);
    }
    @Test public void touchingEdgesAndOutsideRectanglesAreNotOccluders(){
        RoiCoverageRecorder value=recorder(true);value.record(1,1,0,new BackgroundRegion.Box(100,20,200,200));value.record(2,2,0,new BackgroundRegion.Box(10,-20,100,20));assertArrayEquals(new int[]{RoiDiagnosticDetail.MODE_FORCE,RoiDiagnosticDetail.INCOMPLETE},value.emptyDetails());
    }
    @Test public void duplicatesAreCollapsedAndReturnedArraysAreIndependent(){
        RoiCoverageRecorder value=recorder(true);for(int i=0;i<50;i++)value.record(0,0,3,new BackgroundRegion.Box(0,0,200,300));int[] details=value.emptyDetails();assertEquals(3,details.length);details[0]=0;assertEquals(RoiDiagnosticDetail.MODE_FORCE,value.emptyDetails()[0]);
    }
    @Test public void fingerprintSourceCannotBeConfusedWithStatus(){
        RoiCoverageRecorder value=recorder(true);value.record(0,0,RoiDiagnosticDetail.FINGERPRINT,new BackgroundRegion.Box(-100,-100,200,300));String[] labels=Arrays.stream(value.emptyDetails()).mapToObj(RoiDiagnosticDetail::label).toArray(String[]::new);assertTrue(Arrays.stream(labels).anyMatch(label->label.contains("FULL_FINGERPRINT")));assertFalse(Arrays.stream(labels).anyMatch(label->label.contains("STATUS")));
    }
    @Test public void badMetadataKeepsExplicitIncompleteInsteadOfFalseUnionProof(){
        RoiCoverageRecorder value=recorder(false);value.record(2,0,3,new BackgroundRegion.Box(10,20,50,200));value.incomplete();int[] details=value.emptyDetails();assertTrue(has(details,RoiDiagnosticDetail.INCOMPLETE));assertFalse(has(details,RoiDiagnosticDetail.UNION_COVER));
    }
    @Test public void invalidNodeTupleAndNullRectDoNotGrantRegion(){RoiCoverageRecorder value=recorder(true);value.record(99,0,0,new BackgroundRegion.Box(0,0,200,300));value.record(0,0,0,null);assertTrue(has(value.emptyDetails(),RoiDiagnosticDetail.INCOMPLETE));}
    @Test public void maximumOriginalBudgetStaysWithinBatchBound(){
        RoiCoverageRecorder value=recorder(true);for(int i=0;i<512;i++)value.record(i/24,i%24,i%8,new BackgroundRegion.Box(0,0,200,300));assertTrue(value.emptyDetails().length<=515);value.record(0,0,0,new BackgroundRegion.Box(0,0,200,300));assertTrue(has(value.emptyDetails(),RoiDiagnosticDetail.INCOMPLETE));assertTrue(value.emptyDetails().length<=515);
    }
    @Test public void extremeButValidIntegersDoNotOverflowContainment(){RoiCoverageRecorder value=recorder(true);value.record(0,0,3,new BackgroundRegion.Box(Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE));assertTrue(has(value.emptyDetails(),RoiDiagnosticDetail.SINGLE_COVER));}
}
