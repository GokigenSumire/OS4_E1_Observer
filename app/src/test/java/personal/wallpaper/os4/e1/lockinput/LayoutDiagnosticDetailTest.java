package personal.wallpaper.os4.e1.lockinput;

import java.util.HashSet;
import org.junit.Test;
import static org.junit.Assert.*;

/** 纯固定编码与原原因表回归；不是手机运行时验证。 */
public final class LayoutDiagnosticDetailTest {
    @Test public void everyTupleRoundTripsWithoutCollisionOrNonAscii(){
        HashSet<Integer> seen=new HashSet<>();
        for(int why=LayoutDiagnosticDetail.UNKNOWN;why<=LayoutDiagnosticDetail.ANIM_ENDED_FILL;why++)
            for(int top=LayoutDiagnosticDetail.OTHER;top<=LayoutDiagnosticDetail.LIGHT;top++)
                for(int node=LayoutDiagnosticDetail.OTHER;node<=LayoutDiagnosticDetail.LIGHT;node++){
                    int code=LayoutDiagnosticDetail.code(why,top,node);
                    assertTrue(seen.add(code));assertTrue(LayoutDiagnosticDetail.valid(code));
                    assertEquals(why,LayoutDiagnosticDetail.why(code));assertEquals(top,LayoutDiagnosticDetail.top(code));assertEquals(node,LayoutDiagnosticDetail.node(code));
                    assertTrue(LockDiagnosticCause.detail(code));assertTrue(LockDiagnosticCause.valid(code));
                    assertEquals(LayoutDiagnosticDetail.label(code),LockDiagnosticCause.label(code));
                    assertTrue(LockDiagnosticCause.label(code).matches("[A-Z_]+"));
                }
        assertEquals(4608,seen.size());
    }
    @Test public void fixedDetailIsInputOnlyForEveryTuple(){
        for(int code=LayoutDiagnosticDetail.BASE;LayoutDiagnosticDetail.valid(code);code++){
            assertTrue(LockDiagnosticCause.validPair(LockDiagnosticCause.INPUT,code));
            for(int stage=1;stage<=4;stage++)assertFalse(LockDiagnosticCause.validPair(stage,code));
            assertFalse(LockDiagnosticCause.validPair(-1,code));assertFalse(LockDiagnosticCause.validPair(5,code));
        }
    }
    @Test public void invalidPartsCannotEncode(){
        for(int value:new int[]{-1,24,Integer.MIN_VALUE,Integer.MAX_VALUE}){
            assertEquals(0,LayoutDiagnosticDetail.code(0,value,0));assertEquals(0,LayoutDiagnosticDetail.code(0,0,value));
        }
        for(int value:new int[]{-1,8,Integer.MIN_VALUE,Integer.MAX_VALUE})assertEquals(0,LayoutDiagnosticDetail.code(value,0,0));
    }
    @Test public void outsideFixedRangeCannotBeInterpretedAsDetail(){
        for(int code:new int[]{Integer.MIN_VALUE,-1,0,48,4095,8704,Integer.MAX_VALUE}){
            assertFalse(LayoutDiagnosticDetail.valid(code));assertFalse(LockDiagnosticCause.detail(code));
            assertEquals(-1,LayoutDiagnosticDetail.why(code));assertEquals(-1,LayoutDiagnosticDetail.top(code));assertEquals(-1,LayoutDiagnosticDetail.node(code));
        }
        assertFalse(LockDiagnosticCause.valid(4095));assertFalse(LockDiagnosticCause.valid(8704));
        assertEquals("UNKNOWN_STATE",LockDiagnosticCause.label(Integer.MAX_VALUE));
    }
    @Test public void originalCauseIndicesRemainFrozen(){
        String[] old={"UNKNOWN_STATE","DIAG_STARTED","SOURCE_NOT_REPORTED","SOURCE_FILE_MATCHED_LOCAL_BYTES","SOURCE_SIZE_MISMATCH","SOURCE_SHA_MISMATCH",
                "PACKAGE_OR_SPLITS_NOT_PINNED","DUPLICATE_APK_MEMBER","DEX_SET_CHANGED","DEX_MEMBER_OUT_OF_BOUNDS","DEX_SHA_MISMATCH","SOURCE_CHANGED_WHILE_READING","SOURCE_VERIFICATION_UNAVAILABLE",
                "LOADER_NOT_REPORTED","DECLARING_LOADER_SOURCE_MATCHED","DECLARING_LOADER_NOT_MATCHED","HOOK_NOT_REPORTED","HOOK_AVAILABLE","HOOK_UNAVAILABLE","ROOT_CLASS_UNAVAILABLE",
                "BOOTSTRAP_AVAILABLE","PRODUCER_REGISTERED","PRODUCER_NOT_REGISTERED","HOST_NOT_READY","ALLOW_METADATA_OBSERVATION","KEYGUARD_NOT_SHOWING","BOUNCER_SHOWING","BOUNCER_SHOWING_OR_SOON",
                "ALTERNATE_BOUNCER_VISIBLE","KEYGUARD_TRANSITION_OR_OCCLUDED","DOZING_OR_AOD","REMOTE_INPUT_OR_AUTH_VISIBLE","EMERGENCY_TRANSITION","SIM_AUTH_REQUIRED",
                "CONTROL_CENTER_LOADER_NOT_VERIFIED","CONTROL_CENTER_EXPANDED","EXTERNAL_OR_PANEL_TRANSITION","ROOT_NOT_ATTACHED_PRIMARY","ROTATION_NOT_PINNED","NOT_INTERACTIVE",
                "ROOT_CONTROLLER_MISMATCH","POSITIVE_BACKGROUND_NOT_PRESENT","BACKGROUND_EMPTY","INSETS_UNKNOWN","WINDOW_RECT_UNKNOWN","SAFE_BACKGROUND_EMPTY",
                "BACKGROUND_CHILD_LAYOUT_UNKNOWN","ROOT_CHILDREN_UNKNOWN","FOREGROUND_LAYOUT_UNKNOWN","FINGERPRINT_REGION_UNKNOWN","POSITIVE_ROI_EMPTY_AFTER_EXCLUSIONS","REQUIRED_PROPERTY_UNKNOWN",
                "OUTSIDE_POSITIVE_ROI","POINTER_OR_CANCEL_REJECTED","STALE_GESTURE_TICKET","SOURCE_CHANGED","ROOT_THREAD_UNKNOWN","OBSERVER_REJECTED","COMMAND_ACCEPTED","COMMAND_REJECTED",
                "COMMAND_CHANNEL_UNAVAILABLE","EDIT_AUTH_ENTRY_SENT","EDIT_AUTH_ENTRY_UNAVAILABLE","UNKNOWN_mInteractionEventHandler","UNKNOWN_this$0","UNKNOWN_mView","UNKNOWN_mService",
                "UNKNOWN_mKeyguardStateController","UNKNOWN_mShowing","UNKNOWN_mPrimaryBouncerShowing","UNKNOWN_mBouncerShowing","UNKNOWN_mPrimaryBouncerInteractor","UNKNOWN_isShowing",
                "UNKNOWN_isShowingSoon","UNKNOWN_mAlternateBouncerInteractor","UNKNOWN_mOccluded","UNKNOWN_mKeyguardGoingAway","UNKNOWN_mKeyguardFadingAway","UNKNOWN_mMiKeyguardFadingAway",
                "UNKNOWN_mLaunchTransitionFadingAway","UNKNOWN_mFlingingToDismissKeyguard","UNKNOWN_mKeyguardNegativeScreen","UNKNOWN_mStatusBarKeyguardViewManager","UNKNOWN_mDozing",
                "UNKNOWN_mAodInterceptingTouches","UNKNOWN_mRemoteInputActive","UNKNOWN_mSecureLockDeviceBiometricAuthVisible","UNKNOWN_mLaunchEmergencyActionOnFinishedGoingToSleep",
                "UNKNOWN_mLaunchEmergencyActionWhenFinishedWaking","UNKNOWN_mKeyguardUpdateMonitor","UNKNOWN_mControlCenter","UNKNOWN_expandedStateInternal","UNKNOWN_mHandlingExternalTouch",
                "UNKNOWN_mExternalTouchIntercepted","UNKNOWN_mIsOcclusionTransitionRunning","UNKNOWN_mExpandAnimationRunning","UNKNOWN_mIsTrackingBarGesture","UNKNOWN_mFingerprintSensorProperties",
                "UNKNOWN_sensorType","UNKNOWN_sensorLocationX","UNKNOWN_sensorLocationY","UNKNOWN_sensorRadius","UNKNOWN_FLOW_PRIMARY_SHOWING","UNKNOWN_FLOW_PRIMARY_SOON","UNKNOWN_FLOW_AOD",
                "UNKNOWN_FLOW_CONTROL_CENTER","UNKNOWN_METHOD_isVisibleState","UNKNOWN_METHOD_isSimPinSecure","UNKNOWN_METHOD_getLocation","UNKNOWN_METHOD_getValue"};
        for(int i=0;i<old.length;i++){assertEquals(old[i],i,LockDiagnosticCause.code(old[i]));assertEquals(old[i].replace('$','_'),LockDiagnosticCause.label(i));assertFalse(LockDiagnosticCause.detail(i));}
        String[] host={"HOST_CHANNEL_ABSENT","HOST_STATE_ABSENT","HOST_STATE_NOT_READY","HOST_CHANNEL_MISMATCH","HOST_STATUS_STALE"};
        for(int i=0;i<host.length;i++)assertEquals(old.length+i,LockDiagnosticCause.code(host[i]));
        assertEquals(old.length+host.length,LockDiagnosticCause.code("SOURCE_INSTALLED_COMPONENT_VERIFIED"));
        assertTrue(LockDiagnosticCause.validPair(LockDiagnosticCause.SOURCE,old.length+host.length));
        assertFalse(LockDiagnosticCause.validPair(LockDiagnosticCause.INPUT,old.length+host.length));
        String[] startup={"SOURCE_VERIFY_STARTED","PRODUCER_INITIALIZATION_QUEUED","PRODUCER_INITIALIZATION_STARTED","PRODUCER_INITIALIZATION_FINISHED","PRODUCER_INITIALIZATION_FAILED","PRODUCER_SOURCE_REJECTED","PRODUCER_DIAGNOSTIC_NOT_CONTACTED","PRODUCER_DIAGNOSTIC_CONNECTED","PRODUCER_SOURCE_CHECK_PENDING","PRODUCER_SOURCE_CHECK_REJECTED"};
        for(int i=0;i<startup.length;i++)assertEquals(old.length+host.length+1+i,LockDiagnosticCause.code(startup[i]));
        assertFalse(LockDiagnosticCause.valid(old.length+host.length+1+startup.length));
    }
    @Test public void inputSelectionAcceptsEveryFixedDetailAndFallsBackForEverythingElse(){
        for(int code=LayoutDiagnosticDetail.BASE;LayoutDiagnosticDetail.valid(code);code++)assertEquals(code,LockDiagnosticCause.input("FOREGROUND_LAYOUT_UNKNOWN",code));
        for(int code:new int[]{Integer.MIN_VALUE,-1,0,48,4095,8704,Integer.MAX_VALUE})assertEquals(48,LockDiagnosticCause.input("FOREGROUND_LAYOUT_UNKNOWN",code));
        assertEquals(0,LockDiagnosticCause.input("private/unknown/value",-1));
    }
    @Test public void maximumCounterAndRowStillFitAsciiLineLimitForAllTuples(){
        for(int code=LayoutDiagnosticDetail.BASE;LayoutDiagnosticDetail.valid(code);code++){
            String line="row=2000 stage=INPUT cause="+LockDiagnosticCause.label(code)+" total="+Long.MAX_VALUE+"\n";
            assertTrue(line,line.length()<=LockDiagnosticRing.MAX_LINE_BYTES);
            assertEquals(line.length(),line.getBytes(java.nio.charset.StandardCharsets.US_ASCII).length);
        }
    }
    @Test public void actualRingAcceptsDetailAndRejectsOtherStages(){
        int code=LayoutDiagnosticDetail.code(LayoutDiagnosticDetail.ANIM_ENDED_FILL,LayoutDiagnosticDetail.BRIGHTNESS,LayoutDiagnosticDetail.BRIGHTNESS);
        LockDiagnosticRing ring=new LockDiagnosticRing();String token=ring.start(0).token;
        for(int stage=1;stage<=4;stage++)assertFalse(ring.record(token,0,stage,code,Long.MAX_VALUE));
        assertTrue(ring.record(token,0,0,code,Long.MAX_VALUE));LockDiagnosticRing.State state=ring.stop(token,1);
        String row=ring.page(token,state.revision,0,1).get(0);assertEquals(row.length()+1,state.bytes);
        assertFalse(ring.record(token,2,0,code,Long.MAX_VALUE));
    }
    @Test public void hostDetailDistinguishesAllFiveOriginalFailureBranches(){
        assertEquals(LockDiagnosticCause.code("HOST_CHANNEL_ABSENT"),LockDiagnosticCause.host(false,true,true,true,false));
        assertEquals(LockDiagnosticCause.code("HOST_STATE_ABSENT"),LockDiagnosticCause.host(true,false,true,true,false));
        assertEquals(LockDiagnosticCause.code("HOST_STATE_NOT_READY"),LockDiagnosticCause.host(true,true,false,true,false));
        assertEquals(LockDiagnosticCause.code("HOST_CHANNEL_MISMATCH"),LockDiagnosticCause.host(true,true,true,false,false));
        assertEquals(LockDiagnosticCause.code("HOST_STATUS_STALE"),LockDiagnosticCause.host(true,true,true,true,true));
        assertEquals(0,LockDiagnosticCause.host(true,true,true,true,false));
    }
    @Test public void everyHostBooleanCombinationUsesOriginalLeftToRightPriority(){
        for(int bits=0;bits<32;bits++){
            boolean channel=(bits&1)!=0,state=(bits&2)!=0,ready=(bits&4)!=0,same=(bits&8)!=0,stale=(bits&16)!=0;
            String expected=!channel?"HOST_CHANNEL_ABSENT":!state?"HOST_STATE_ABSENT":!ready?"HOST_STATE_NOT_READY":!same?"HOST_CHANNEL_MISMATCH":stale?"HOST_STATUS_STALE":null;
            assertEquals(expected==null?0:LockDiagnosticCause.code(expected),LockDiagnosticCause.host(channel,state,ready,same,stale));
        }
    }
    @Test public void inputAcceptsOnlyHostWhitelistBesidesFixedLayoutTuples(){
        for(String name:new String[]{"HOST_CHANNEL_ABSENT","HOST_STATE_ABSENT","HOST_STATE_NOT_READY","HOST_CHANNEL_MISMATCH","HOST_STATUS_STALE"}){
            int code=LockDiagnosticCause.code(name);assertEquals(code,LockDiagnosticCause.input("HOST_NOT_READY",code));
            assertTrue(LockDiagnosticCause.validPair(0,code));for(int stage=1;stage<=4;stage++)assertFalse(LockDiagnosticCause.validPair(stage,code));
        }
        int old=LockDiagnosticCause.code("HOST_NOT_READY");
        for(int code:new int[]{Integer.MIN_VALUE,-1,0,1,17,48,4095,8704,Integer.MAX_VALUE})assertEquals(old,LockDiagnosticCause.input("HOST_NOT_READY",code));
    }
}
