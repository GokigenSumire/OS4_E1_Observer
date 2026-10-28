package personal.wallpaper.os4.e1.lockinput;

/** 只接受当前已鉴别宿主状态，不读取UI开关或任意前缀。 */
final class WallpaperPriorityMode {
    static final String READY="READY_WALLPAPER_PRIORITY";
    static final String NATIVE="READY_NATIVE_INPUT";
    static final String NATIVE_FORCE="READY_NATIVE_INPUT+READY_FORCE_LOCAL";
    static final String NATIVE_PRIORITY="READY_NATIVE_INPUT+READY_WALLPAPER_PRIORITY";
    static final String NATIVE_FORCE_PRIORITY="READY_NATIVE_INPUT+READY_FORCE_LOCAL+READY_WALLPAPER_PRIORITY";
    private WallpaperPriorityMode(){ }
    static boolean enabled(String reason){return READY.equals(reason)||NATIVE_PRIORITY.equals(reason)||NATIVE_FORCE_PRIORITY.equals(reason);}
    static boolean nativeInput(String reason){return NATIVE.equals(reason)||NATIVE_FORCE.equals(reason)||NATIVE_PRIORITY.equals(reason)||NATIVE_FORCE_PRIORITY.equals(reason);}
    static boolean forceLocal(String reason){return "READY_FORCE_LOCAL".equals(reason)||NATIVE_FORCE.equals(reason)||NATIVE_FORCE_PRIORITY.equals(reason);}
}
