package personal.wallpaper.os4.e1.lockinput;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RoiDiagnosticBatchTest {
    private final int[] codes={RoiDiagnosticDetail.MODE_FORCE,RoiDiagnosticDetail.SINGLE_COVER};
    @Test public void repeatedSameSessionBatchIsSuppressed(){RoiDiagnosticBatch value=new RoiDiagnosticBatch();assertArrayEquals(codes,value.take("one",codes));assertNull(value.take("one",codes));}
    @Test public void newSessionOrChangedDetailsCanReport(){RoiDiagnosticBatch value=new RoiDiagnosticBatch();assertNotNull(value.take("one",codes));assertNotNull(value.take("two",codes));assertNotNull(value.take("two",new int[]{RoiDiagnosticDetail.MODE_CONSERVATIVE}));}
    @Test public void inputAndOutputArraysCannotMutateDedupState(){RoiDiagnosticBatch value=new RoiDiagnosticBatch();int[] input=codes.clone(),output=value.take("one",input);input[0]=0;output[0]=0;assertNull(value.take("one",codes));}
    @Test public void invalidBatchDoesNotPoisonLaterValidSession(){RoiDiagnosticBatch value=new RoiDiagnosticBatch();assertNull(value.take(null,codes));assertNull(value.take("one",null));assertNull(value.take("one",new int[0]));assertNull(value.take("one",new int[]{0}));assertNull(value.take("one",new int[]{RoiDiagnosticDetail.BATCH_COMPLETE}));assertNull(value.take("one",new int[516]));assertNotNull(value.take("one",codes));}
    @Test public void clearAllowsSameDetailsAfterRetry(){RoiDiagnosticBatch value=new RoiDiagnosticBatch();value.take("one",codes);value.clear();assertNotNull(value.take("one",codes));}
}
