package personal.wallpaper.os4.e1.lockinput;

/** 唯一固定原因表。只传整数，不接受异常正文、路径、触摸或任意字符串。 */
public final class LockDiagnosticCause {
    public static final int INPUT=0,SOURCE=1,LOADER=2,HOOK=3,BOOTSTRAP=4;
    private static final String[] NAMES={
        "UNKNOWN_STATE","DIAG_STARTED","SOURCE_NOT_REPORTED","SOURCE_FILE_MATCHED_LOCAL_BYTES","SOURCE_SIZE_MISMATCH","SOURCE_SHA_MISMATCH",
        "PACKAGE_OR_SPLITS_NOT_PINNED","DUPLICATE_APK_MEMBER","DEX_SET_CHANGED","DEX_MEMBER_OUT_OF_BOUNDS","DEX_SHA_MISMATCH","SOURCE_CHANGED_WHILE_READING","SOURCE_VERIFICATION_UNAVAILABLE",
        "LOADER_NOT_REPORTED","DECLARING_LOADER_SOURCE_MATCHED","DECLARING_LOADER_NOT_MATCHED",
        "HOOK_NOT_REPORTED","HOOK_AVAILABLE","HOOK_UNAVAILABLE","ROOT_CLASS_UNAVAILABLE","BOOTSTRAP_AVAILABLE","PRODUCER_REGISTERED","PRODUCER_NOT_REGISTERED",
        "HOST_NOT_READY","ALLOW_METADATA_OBSERVATION","KEYGUARD_NOT_SHOWING","BOUNCER_SHOWING","BOUNCER_SHOWING_OR_SOON","ALTERNATE_BOUNCER_VISIBLE","KEYGUARD_TRANSITION_OR_OCCLUDED",
        "DOZING_OR_AOD","REMOTE_INPUT_OR_AUTH_VISIBLE","EMERGENCY_TRANSITION","SIM_AUTH_REQUIRED","CONTROL_CENTER_LOADER_NOT_VERIFIED","CONTROL_CENTER_EXPANDED","EXTERNAL_OR_PANEL_TRANSITION",
        "ROOT_NOT_ATTACHED_PRIMARY","ROTATION_NOT_PINNED","NOT_INTERACTIVE","ROOT_CONTROLLER_MISMATCH","POSITIVE_BACKGROUND_NOT_PRESENT","BACKGROUND_EMPTY","INSETS_UNKNOWN","WINDOW_RECT_UNKNOWN","SAFE_BACKGROUND_EMPTY",
        "BACKGROUND_CHILD_LAYOUT_UNKNOWN","ROOT_CHILDREN_UNKNOWN","FOREGROUND_LAYOUT_UNKNOWN","FINGERPRINT_REGION_UNKNOWN","POSITIVE_ROI_EMPTY_AFTER_EXCLUSIONS","REQUIRED_PROPERTY_UNKNOWN",
        "OUTSIDE_POSITIVE_ROI","POINTER_OR_CANCEL_REJECTED","STALE_GESTURE_TICKET","SOURCE_CHANGED","ROOT_THREAD_UNKNOWN","OBSERVER_REJECTED","COMMAND_ACCEPTED","COMMAND_REJECTED","COMMAND_CHANNEL_UNAVAILABLE","EDIT_AUTH_ENTRY_SENT","EDIT_AUTH_ENTRY_UNAVAILABLE",
        "UNKNOWN_mInteractionEventHandler","UNKNOWN_this$0","UNKNOWN_mView","UNKNOWN_mService","UNKNOWN_mKeyguardStateController","UNKNOWN_mShowing","UNKNOWN_mPrimaryBouncerShowing","UNKNOWN_mBouncerShowing",
        "UNKNOWN_mPrimaryBouncerInteractor","UNKNOWN_isShowing","UNKNOWN_isShowingSoon","UNKNOWN_mAlternateBouncerInteractor","UNKNOWN_mOccluded","UNKNOWN_mKeyguardGoingAway","UNKNOWN_mKeyguardFadingAway","UNKNOWN_mMiKeyguardFadingAway","UNKNOWN_mLaunchTransitionFadingAway","UNKNOWN_mFlingingToDismissKeyguard","UNKNOWN_mKeyguardNegativeScreen",
        "UNKNOWN_mStatusBarKeyguardViewManager","UNKNOWN_mDozing","UNKNOWN_mAodInterceptingTouches","UNKNOWN_mRemoteInputActive","UNKNOWN_mSecureLockDeviceBiometricAuthVisible","UNKNOWN_mLaunchEmergencyActionOnFinishedGoingToSleep","UNKNOWN_mLaunchEmergencyActionWhenFinishedWaking","UNKNOWN_mKeyguardUpdateMonitor","UNKNOWN_mControlCenter","UNKNOWN_expandedStateInternal","UNKNOWN_mHandlingExternalTouch","UNKNOWN_mExternalTouchIntercepted","UNKNOWN_mIsOcclusionTransitionRunning","UNKNOWN_mExpandAnimationRunning","UNKNOWN_mIsTrackingBarGesture","UNKNOWN_mFingerprintSensorProperties","UNKNOWN_sensorType","UNKNOWN_sensorLocationX","UNKNOWN_sensorLocationY","UNKNOWN_sensorRadius",
        "UNKNOWN_FLOW_PRIMARY_SHOWING","UNKNOWN_FLOW_PRIMARY_SOON","UNKNOWN_FLOW_AOD","UNKNOWN_FLOW_CONTROL_CENTER","UNKNOWN_METHOD_isVisibleState","UNKNOWN_METHOD_isSimPinSecure","UNKNOWN_METHOD_getLocation","UNKNOWN_METHOD_getValue",
        "HOST_CHANNEL_ABSENT","HOST_STATE_ABSENT","HOST_STATE_NOT_READY","HOST_CHANNEL_MISMATCH","HOST_STATUS_STALE",
        "SOURCE_INSTALLED_COMPONENT_VERIFIED",
        "SOURCE_VERIFY_STARTED","PRODUCER_INITIALIZATION_QUEUED","PRODUCER_INITIALIZATION_STARTED",
        "PRODUCER_INITIALIZATION_FINISHED","PRODUCER_INITIALIZATION_FAILED","PRODUCER_SOURCE_REJECTED",
        "PRODUCER_DIAGNOSTIC_NOT_CONTACTED","PRODUCER_DIAGNOSTIC_CONNECTED",
        "PRODUCER_SOURCE_CHECK_PENDING","PRODUCER_SOURCE_CHECK_REJECTED"
    };
    private LockDiagnosticCause(){ }
    public static int code(String fixedName){for(int i=0;i<NAMES.length;i++)if(NAMES[i].equals(fixedName))return i;int nativeCode=NativeInputDetail.code(fixedName);return nativeCode!=0?nativeCode:PriorityInputDetail.code(fixedName);}
    public static int input(String coarseReason,int layoutDetailCode){return detail(layoutDetailCode)||hostDetail(layoutDetailCode)?layoutDetailCode:code(coarseReason);}
    public static int host(boolean hasChannel,boolean hasState,boolean ready,boolean sameChannel,boolean stale){
        if(!hasChannel)return code("HOST_CHANNEL_ABSENT");
        if(!hasState)return code("HOST_STATE_ABSENT");
        if(!ready)return code("HOST_STATE_NOT_READY");
        if(!sameChannel)return code("HOST_CHANNEL_MISMATCH");
        return stale?code("HOST_STATUS_STALE"):0;
    }
    private static boolean hostDetail(int value){return value>=code("HOST_CHANNEL_ABSENT")&&value<=code("HOST_STATUS_STALE");}
    public static boolean detail(int code){return LayoutDiagnosticDetail.valid(code)||RoiDiagnosticDetail.valid(code)||PriorityInputDetail.valid(code)||NativeInputDetail.valid(code);}
    public static boolean valid(int code){return (code>=0&&code<NAMES.length)||detail(code);}
    public static String label(int code){return NativeInputDetail.valid(code)?NativeInputDetail.label(code):PriorityInputDetail.valid(code)?PriorityInputDetail.label(code):RoiDiagnosticDetail.valid(code)?RoiDiagnosticDetail.label(code):LayoutDiagnosticDetail.valid(code)?LayoutDiagnosticDetail.label(code):(code>=0&&code<NAMES.length?NAMES[code]:NAMES[0]).replace('$','_');}
    public static boolean validStage(int stage){return stage>=0&&stage<=4;}
    public static boolean validPair(int stage,int code){
        if(!validStage(stage)||!valid(code))return false;
        if(detail(code))return stage==INPUT;
        if(code==0)return true;
        if(code==code("SOURCE_INSTALLED_COMPONENT_VERIFIED"))return stage==SOURCE;
        if(code==code("SOURCE_VERIFY_STARTED"))return stage==SOURCE;
        if(code>=code("PRODUCER_INITIALIZATION_QUEUED")&&code<=code("PRODUCER_SOURCE_CHECK_REJECTED"))return stage==BOOTSTRAP;
        if(stage==SOURCE)return code>=2&&code<=12;
        if(stage==LOADER)return code>=13&&code<=15;
        if(stage==HOOK)return code>=16&&code<=19;
        if(stage==BOOTSTRAP)return code==1||code>=20&&code<=22;
        return code>=23;
    }
    public static String stage(int stage){return validStage(stage)?new String[]{"INPUT","SOURCE","LOADER","HOOK","BOOTSTRAP"}[stage]:"UNKNOWN";}
}
