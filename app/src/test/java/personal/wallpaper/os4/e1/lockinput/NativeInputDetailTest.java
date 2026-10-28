package personal.wallpaper.os4.e1.lockinput;

import android.os.Parcel;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
public final class NativeInputDetailTest {
    @Test public void completeDictionaryRoundTripsWithoutAliases(){HashSet<String> labels=new HashSet<>();for(int i=0;i<NativeInputDetail.count();i++){int code=NativeInputDetail.BASE+i;assertTrue(NativeInputDetail.valid(code));assertTrue(labels.add(NativeInputDetail.label(code)));assertEquals(code,NativeInputDetail.code(NativeInputDetail.label(code)));}}
    @Test public void everyBusinessSnapshotReasonHasFixedRegisteredCode(){for(String reason:NativeBusinessSnapshot.REASONS)assertTrue(reason,NativeInputDetail.valid(NativeInputDetail.code(reason)));}
    @Test public void priorityAdmissionReasonsRemainFixedAndBounded(){
        assertTrue(NativeInputDetail.valid(NativeInputDetail.code("NATIVE_PRIORITY_LAYOUT_UNKNOWN")));
        assertTrue(NativeInputDetail.valid(NativeInputDetail.code("NATIVE_PRIORITY_PROTECTED_REGION")));
    }
    @Test public void localLayoutDiagnosticsNeverAcquireRelayPrivilege(){for(String name:new String[]{"NATIVE_LAYOUT_ROOT","NATIVE_LAYOUT_STACK","NATIVE_LAYOUT_PRIORITY","NATIVE_LAYOUT_NOTIFICATIONS","NATIVE_LAYOUT_MEDIA","NATIVE_LAYOUT_SHORTCUTS","NATIVE_LAYOUT_TRANSFORM","NATIVE_LAYOUT_DETACHED","NATIVE_LAYOUT_HIDDEN","NATIVE_LAYOUT_EMPTY","NATIVE_LAYOUT_PROPERTY_UNKNOWN","NATIVE_PRIORITY_MEDIA_BINDING","NATIVE_PRIORITY_MEDIA_LAYOUT","NATIVE_PRIORITY_MEDIA_GEOMETRY","NATIVE_PRIORITY_MEDIA_UNACCOUNTED","NATIVE_PRIORITY_MEDIA_PROPERTIES_UNKNOWN","NATIVE_PRIORITY_NOTIFICATIONS_UNKNOWN"}){int code=NativeInputDetail.code(name);assertTrue(name,NativeInputDetail.valid(code));assertFalse(name,NativeInputDetail.isRelayResult(code));}}
    @Test public void unknownNamesAndOutOfRangeValuesDoNotAcquireMeaning(){assertEquals(0,NativeInputDetail.code(null));assertEquals(0,NativeInputDetail.code("private notification text"));for(int code:new int[]{Integer.MIN_VALUE,0,NativeInputDetail.BASE-1,NativeInputDetail.BASE+NativeInputDetail.count(),Integer.MAX_VALUE}){assertFalse(NativeInputDetail.valid(code));assertFalse(NativeInputDetail.isEditResult(code));assertEquals("NATIVE_UNKNOWN",NativeInputDetail.label(code));}}
    @Test public void nativeCodesOnlyBelongToInputStage(){for(int i=0;i<NativeInputDetail.count();i++){int code=NativeInputDetail.BASE+i;assertTrue(LockDiagnosticCause.validPair(LockDiagnosticCause.INPUT,code));for(int stage=1;stage<=4;stage++)assertFalse(LockDiagnosticCause.validPair(stage,code));}}
    @Test public void hostResultsAreExactlyEighteenFixedCausesAndThreeTransportStates(){HashSet<Integer> allowed=new HashSet<>();for(int cause=1;cause<=18;cause++){int code=NativeInputDetail.editCause(cause);assertTrue(NativeInputDetail.isEditResult(code));assertTrue(allowed.add(code));}allowed.add(NativeInputDetail.code("NATIVE_EDIT_RESULTS_UNAVAILABLE"));allowed.add(NativeInputDetail.code("NATIVE_EDIT_RESULTS_TRUNCATED"));allowed.add(NativeInputDetail.code("NATIVE_EDIT_BASELINE_UNAVAILABLE"));for(int i=0;i<NativeInputDetail.count();i++)assertEquals(allowed.contains(NativeInputDetail.BASE+i),NativeInputDetail.isEditResult(NativeInputDetail.BASE+i));assertEquals(21,allowed.size());}
    @Test public void unknownHostCauseCannotBecomeNativeEvent(){for(int cause:new int[]{Integer.MIN_VALUE,0,19,Integer.MAX_VALUE})assertEquals(0,NativeInputDetail.editCause(cause));}
    @Test public void eachMaximumCountRowFitsExistingAsciiLineAndRealParcelBudget(){for(int i=0;i<NativeInputDetail.count();i++){int code=NativeInputDetail.BASE+i;String line="row=2000 stage=INPUT cause="+NativeInputDetail.label(code)+" total="+Long.MAX_VALUE;assertTrue(line,line.matches("[A-Z_a-z0-9= ]+"));assertTrue(line,line.getBytes(StandardCharsets.US_ASCII).length+1<=LockDiagnosticRing.MAX_LINE_BYTES);Parcel parcel=Parcel.obtain();try{parcel.writeNoException();parcel.writeString("diagnostic-token");parcel.writeLong(2001);parcel.writeInt(0);parcel.writeInt(2);parcel.writeInt(2000);parcel.writeInt(131072);parcel.writeInt(3);for(int row=0;row<3;row++)parcel.writeString(line);assertTrue(line,parcel.dataSize()<=1024);}finally{parcel.recycle();}}}
    @Test public void boundedRingAcceptsEveryRegisteredNativeInputCode(){LockDiagnosticRing ring=new LockDiagnosticRing();var state=ring.start(0);for(int i=0;i<NativeInputDetail.count();i++)assertTrue(ring.record(state.token,1,0,NativeInputDetail.BASE+i,Long.MAX_VALUE));assertEquals(NativeInputDetail.count(),ring.state(1).rows);}
    @Test public void motionActionLabelsAreFixedAndDoNotIncludeInputNumbers(){assertEquals("NATIVE_EVENT_DOWN",NativeInputDetail.eventName(0));assertEquals("NATIVE_EVENT_POINTER_UP",NativeInputDetail.eventName(6));assertEquals("NATIVE_EVENT_OTHER",NativeInputDetail.eventName(999));assertEquals("NATIVE_EVENT_OTHER",NativeInputDetail.eventName(Integer.MIN_VALUE));}
    @Test public void originalDictionaryPositionsRemainStable(){assertEquals(NativeInputDetail.BASE,NativeInputDetail.code("NATIVE_ENTRY_NOT_HIT"));assertEquals(NativeInputDetail.BASE+117,NativeInputDetail.code("NATIVE_EDIT_BASELINE_UNAVAILABLE"));assertEquals(NativeInputDetail.BASE+118,NativeInputDetail.code("NATIVE_ROOT_THREAD"));}
    @Test public void onlyFixedRawSourcesAndActionsArePublished(){
        for(String source:new String[]{"ROOT","PANEL","INTERCEPT","TOUCH_HANDLER","INJECTOR"})for(int action:new int[]{0,1,2,3,5,6,999}){
            String name=NativeInputDetail.rawEventName(source,action);assertTrue(name,NativeInputDetail.valid(NativeInputDetail.code(name)));assertTrue(name,name.startsWith("NATIVE_RAW_"+source+"_"));
        }
        for(String source:new String[]{null,"","private view class","HELPER","root"})assertEquals("NATIVE_EVENT_OTHER",NativeInputDetail.rawEventName(source,0));
    }
    @Test public void commandResultsDoNotGiveProducerReportingArbitraryInputCodes(){
        HashSet<Integer> expected=new HashSet<>();
        for(String status:new String[]{"COMMITTED","FAILED","STALE_TICKET","CANCELLED","EXPIRED","NO_ENGINE","TARGET_NOT_LOCK","BUSY_EDIT","UNKNOWN_STATE"}){
            int code=NativeInputDetail.commandResult(status);assertEquals("NATIVE_HOST_"+status,NativeInputDetail.label(code));assertTrue(expected.add(code));
        }
        int invalid=NativeInputDetail.code("NATIVE_HOST_CALLBACK_INVALID");expected.add(invalid);
        for(String status:new String[]{null,"","ACCEPTED","SENT","private path"})assertEquals(invalid,NativeInputDetail.commandResult(status));
        for(int i=0;i<NativeInputDetail.count();i++){int code=NativeInputDetail.BASE+i;assertEquals(expected.contains(code)||NativeInputDetail.isEditResult(code),NativeInputDetail.isRelayResult(code));}
        for(int code:new int[]{0,NativeInputDetail.BASE-1,NativeInputDetail.BASE+NativeInputDetail.count(),Integer.MAX_VALUE})assertFalse(NativeInputDetail.isRelayResult(code));
    }
    @Test public void internalCancelObservationsNeverAcquireHostResultPrivilege(){for(String name:new String[]{"NATIVE_INTERNAL_CANCEL_SOURCE_REJECTED","NATIVE_HOOK_CANCEL_CALLERS_READY","NATIVE_HOOK_CANCEL_CALLERS_UNAVAILABLE","NATIVE_INTERNAL_CANCEL_DANGEROUS_SOURCE"}){int code=NativeInputDetail.code(name);assertTrue(NativeInputDetail.valid(code));assertFalse(NativeInputDetail.isRelayResult(code));}}
}
