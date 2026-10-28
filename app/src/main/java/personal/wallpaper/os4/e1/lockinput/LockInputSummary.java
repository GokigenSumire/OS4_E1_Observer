package personal.wallpaper.os4.e1.lockinput;

/** 私有session里只允许这一固定粗粒度末原因，不传认证阶段事件/轨迹/时间。 */
public final class LockInputSummary {
    public static final int UNREGISTERED=0,WAITING_FIRST_INPUT=1,HOST_NOT_READY=2,READY_BACKGROUND=3,
            KEYGUARD_NOT_SHOWING=4,AUTHENTICATION_BLOCKED=5,DOZING_AOD=6,PANEL_OR_TRANSITION=7,
            ROI_EMPTY=8,OUTSIDE_ROI=9,PROPERTY_UNKNOWN=10,SOURCE_CHANGED=11,ROOT_UNKNOWN=12,
            GESTURE_REJECTED=13,COMMAND_ACCEPTED=14,COMMAND_REJECTED=15,EDIT_ENTRY_SENT=16,EDIT_ENTRY_UNAVAILABLE=17;
    private LockInputSummary(){ }
    public static boolean valid(int code){return code>=0&&code<=17;}
    public static int fromReason(String reason){
        if(reason==null)return PROPERTY_UNKNOWN;
        if(reason.startsWith("NATIVE_"))return nativeReason(reason);
        if(reason.equals("ALLOW_METADATA_OBSERVATION"))return READY_BACKGROUND;
        if(reason.equals("KEYGUARD_NOT_SHOWING"))return KEYGUARD_NOT_SHOWING;
        if(reason.contains("BOUNCER")||reason.equals("SIM_AUTH_REQUIRED")||reason.equals("REMOTE_INPUT_OR_AUTH_VISIBLE"))return AUTHENTICATION_BLOCKED;
        if(reason.equals("DOZING_OR_AOD")||reason.equals("NOT_INTERACTIVE"))return DOZING_AOD;
        if(reason.contains("TRANSITION")||reason.equals("CONTROL_CENTER_EXPANDED"))return PANEL_OR_TRANSITION;
        if(reason.contains("EMPTY"))return ROI_EMPTY;
        if(reason.equals("OUTSIDE_POSITIVE_ROI"))return OUTSIDE_ROI;
        if(reason.equals("SOURCE_CHANGED"))return SOURCE_CHANGED;
        if(reason.equals("HOST_NOT_READY"))return HOST_NOT_READY;
        if(reason.equals("PRIORITY_NOTIFICATION_AREA_COVERED"))return ROI_EMPTY;
        if(reason.startsWith("PRIORITY_"))return PROPERTY_UNKNOWN;
        if(reason.startsWith("UNKNOWN_")||reason.contains("UNKNOWN")||reason.contains("NOT_VERIFIED"))return PROPERTY_UNKNOWN;
        if(reason.equals("COMMAND_ACCEPTED"))return COMMAND_ACCEPTED;
        if(reason.equals("COMMAND_REJECTED")||reason.equals("COMMAND_CHANNEL_UNAVAILABLE"))return COMMAND_REJECTED;
        if(reason.equals("EDIT_AUTH_ENTRY_SENT"))return EDIT_ENTRY_SENT;
        if(reason.equals("EDIT_AUTH_ENTRY_UNAVAILABLE"))return EDIT_ENTRY_UNAVAILABLE;
        if(reason.startsWith("ROOT_")||reason.equals("POSITIVE_BACKGROUND_NOT_PRESENT"))return ROOT_UNKNOWN;
        return GESTURE_REJECTED;
    }
    private static int nativeReason(String reason){
        if(reason.equals("NATIVE_ALLOWED"))return READY_BACKGROUND;
        if(reason.equals("NATIVE_SESSION_INVALID"))return HOST_NOT_READY;
        if(reason.equals("NATIVE_AUTHENTICATION")||reason.equals("NATIVE_SIM_AUTH")||reason.equals("NATIVE_REMOTE_AUTH"))return AUTHENTICATION_BLOCKED;
        if(reason.equals("NATIVE_NOT_KEYGUARD"))return KEYGUARD_NOT_SHOWING;
        if(reason.equals("NATIVE_DOZING_AOD")||reason.equals("NATIVE_NOT_INTERACTIVE"))return DOZING_AOD;
        if(reason.contains("TRANSITION")||reason.equals("NATIVE_QS")||reason.equals("NATIVE_CONTROL_CENTER")||reason.equals("NATIVE_SHORTCUT_MOVING"))return PANEL_OR_TRANSITION;
        if(reason.equals("NATIVE_SOURCE_CHANGED"))return SOURCE_CHANGED;
        if(reason.equals("NATIVE_COMMAND_ACCEPTED"))return COMMAND_ACCEPTED;
        if(reason.equals("NATIVE_COMMAND_REJECTED")||reason.equals("NATIVE_COMMAND_CHANNEL_LOST"))return COMMAND_REJECTED;
        if(reason.equals("NATIVE_AUTH_ENTRY_SENT"))return EDIT_ENTRY_SENT;
        if(reason.equals("NATIVE_AUTH_ENTRY_UNAVAILABLE"))return EDIT_ENTRY_UNAVAILABLE;
        if(reason.endsWith("_REGION"))return OUTSIDE_ROI;
        if(reason.startsWith("NATIVE_BINDING_")||reason.endsWith("_UNKNOWN")||reason.endsWith("_UNAVAILABLE")||reason.endsWith("_LAYOUT"))return PROPERTY_UNKNOWN;
        return GESTURE_REJECTED;
    }
}
