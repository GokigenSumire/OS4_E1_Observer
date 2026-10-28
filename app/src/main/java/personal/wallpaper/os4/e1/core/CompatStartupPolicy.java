package personal.wallpaper.os4.e1.core;

import java.util.HashMap;
import java.util.Map;

/** 开机只在精确版本、完整永久策略且干净关闭状态下恢复，不重试安全锁。 */
public final class CompatStartupPolicy {
    public enum Decision { START, ALREADY_RUNNING, REJECT }
    private CompatStartupPolicy() { }

    public static Decision evaluate(String module, int code, String name, String contrast,
                                    String hooks, String bootstrap, String error, long epoch, String boot) {
        if (!HomeScope.responseAllowed(module, code, name) || epoch < 0 || boot == null
                || boot.trim().isEmpty() || boot.length() > 128
                || error != null && !error.isEmpty() && !"NONE".equals(error)
                || !"BOOTSTRAP_UNHOOKED".equals(bootstrap)) return Decision.REJECT;
        Map<String, String> fields = fields(contrast);
        if (fields == null || !"PERMANENT".equals(fields.get("dailyPolicy"))
                || !"0".equals(fields.get("dailyDurationMs")) || !"-1".equals(fields.get("dailyDeadline"))
                || !"false".equals(fields.get("dailyNeedsRecovery"))) return Decision.REJECT;
        if ("ENABLE".equals(fields.get("dailyMode")) && "HOOKED".equals(hooks)) return Decision.ALREADY_RUNNING;
        if (!"DISARMED".equals(fields.get("dailyMode")) || !"UNHOOKED".equals(hooks)) return Decision.REJECT;
        String terminal = fields.get("dailyFinal"), recovery = fields.get("recoveryStatus");
        if (!("NONE".equals(terminal) && "NOT_VERIFIED".equals(recovery)
                || "FINAL_CONFIRMED".equals(terminal) && "RECOVERY_CONFIRMED".equals(recovery))) return Decision.REJECT;
        return Decision.START;
    }

    private static Map<String, String> fields(String value) {
        if (value == null || value.length() > 16384) return null;
        Map<String, String> result = new HashMap<>();
        for (String token : value.trim().split("\\s+")) {
            int equal = token.indexOf('=');
            if (equal <= 0 || equal == token.length() - 1) return null;
            String key = token.substring(0, equal), field = token.substring(equal + 1);
            if (result.put(key, field) != null) return null;
        }
        return result;
    }
}
