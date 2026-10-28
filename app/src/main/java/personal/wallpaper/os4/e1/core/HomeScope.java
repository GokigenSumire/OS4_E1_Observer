package personal.wallpaper.os4.e1.core;

/** 仅接受主显示上的真正 HOME 基础窗口；缺少任一字段即拒绝。 */
public final class HomeScope {
    private HomeScope() { }
    public static boolean allowed(Object owner, Object type, Object display, Object activityOwner, Object activityType) {
        return "com.miui.home".equals(owner) && Integer.valueOf(1).equals(type)
                && Integer.valueOf(0).equals(display) && "com.miui.home".equals(activityOwner)
                && Integer.valueOf(2).equals(activityType);
    }
    public static boolean exportAllowed(long expectedEpoch,long actualEpoch,int expectedRows,int actualRows,boolean active) {
        return expectedEpoch>0 && expectedEpoch==actualEpoch && expectedRows>=0 && expectedRows==actualRows && !active;
    }
    public static boolean commandAllowed(long expectedEpoch,long actualEpoch){return expectedEpoch>0&&expectedEpoch==actualEpoch;}
    public static boolean responseAllowed(String module,int expectedCode,String expectedName){
        return expectedCode>0&&expectedName!=null&&expectedName.matches("[0-9A-Za-z._-]{1,64}")
                &&(expectedCode+"/"+expectedName).equals(module);
    }
    public static boolean recoveryWindow(long now,long started,long recoveryStarted){
        return started>=0&&recoveryStarted>=started&&now>=recoveryStarted&&now-started<200_000L&&now-recoveryStarted<20_000L;
    }
    public static boolean dailyRecoveryWindow(long now,long started,long recoveryStarted,long duration){
        return duration>0&&duration<=43_200_000L&&started>=0&&recoveryStarted>=started&&now>=recoveryStarted&&now-started<duration+20_000L&&now-recoveryStarted<20_000L;
    }
    /** 永久运行的恢复期限只从本次首次撤权起算，不计算可溢出的绝对截止。 */
    public static boolean permanentRecoveryWindow(long now,long started,long recoveryStarted){
        return started>=0&&recoveryStarted>=started&&now>=recoveryStarted&&now-recoveryStarted<20_000L;
    }
    public static boolean dailyCommandAllowed(long expected,long actual){return expected>=0&&expected==actual;}
    public static boolean targetWallpaperAllowed(Object owner,Object type,Object display,boolean selectedToken,boolean visibleToken){
        return "personal.wallpaper.os3.debug".equals(owner)&&Integer.valueOf(2013).equals(type)&&Integer.valueOf(0).equals(display)&&selectedToken&&visibleToken;
    }
    public static boolean targetWallpaperAllowed(Object owner,Object type,Object display,boolean selectedToken,boolean visibleToken,String expectedPackage){
        return ("personal.wallpaper.os3".equals(expectedPackage)||"personal.wallpaper.os3.debug".equals(expectedPackage))
                &&expectedPackage.equals(owner)&&Integer.valueOf(2013).equals(type)&&Integer.valueOf(0).equals(display)&&selectedToken&&visibleToken;
    }
}
