package personal.wallpaper.os4.e1.lockinput;

/** 只记录固定拒绝阶段，不接收通知内容或任意运行时字符串。 */
final class PriorityInputDetail {
    static final int BASE=65536;
    private static final String[] NAMES={"PRIORITY_ROOT_UNSTABLE","PRIORITY_NOTIFICATION_IDENTITY","PRIORITY_NOTIFICATION_COLLECTION",
            "PRIORITY_NOTIFICATION_TRANSITION","PRIORITY_NOTIFICATION_LAYOUT","PRIORITY_NOTIFICATION_GEOMETRY",
            "PRIORITY_NOTIFICATION_UNACCOUNTED","PRIORITY_MEDIA_IDENTITY","PRIORITY_MEDIA_GEOMETRY",
            "PRIORITY_NOTIFICATION_PROPERTIES_UNKNOWN","PRIORITY_NOTIFICATION_AREA_COVERED","PRIORITY_MODE_INCONSISTENT",
            "PRIORITY_CONTROL_IDENTITY","PRIORITY_CONTROL_LAYOUT","PRIORITY_CONTROL_GEOMETRY"};
    private PriorityInputDetail(){ }
    static int code(String reason){for(int i=0;i<NAMES.length;i++)if(NAMES[i].equals(reason))return BASE+i;return 0;}
    static boolean valid(int code){return code>=BASE&&code<BASE+NAMES.length;}
    static String label(int code){return valid(code)?NAMES[code-BASE]:"PRIORITY_NOTIFICATION_PROPERTIES_UNKNOWN";}
}
