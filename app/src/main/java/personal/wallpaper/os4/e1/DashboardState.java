package personal.wallpaper.os4.e1;

import java.util.HashMap;
import java.util.Map;

/** 只把实际回执与已保存意愿转换为展示状态，不持有系统许可。 */
final class DashboardState {
    enum Kind { UNKNOWN, READY, ACTIVE, WAITING_TARGET, RECOVERING, LOCKED, HOOK_FAILURE, CONTROL_ERROR }
    enum Mutation { ENABLE, STOP, REJECT }
    final Kind kind;
    final boolean canEnable;
    final boolean canStop;
    final boolean desiredAvailable;
    final boolean desiredEnabled;
    final boolean positive;
    final boolean needsApply;
    final boolean needsStop;

    private DashboardState(Kind kind, boolean canEnable, boolean canStop,
                           boolean desiredAvailable, boolean desiredEnabled) {
        this.kind = kind;
        this.canEnable = canEnable;
        this.canStop = canStop;
        this.desiredAvailable = desiredAvailable;
        this.desiredEnabled = desiredEnabled;
        this.positive = desiredAvailable && (desiredEnabled && kind == Kind.ACTIVE
                || !desiredEnabled && kind == Kind.READY);
        this.needsApply = desiredAvailable && desiredEnabled && canEnable;
        this.needsStop = canStop && (!desiredAvailable || !desiredEnabled);
    }

    static DashboardState from(String dailyText, String hooks, String bootstrap,
                               String coverage, String error, boolean desiredAvailable, boolean desiredEnabled) {
        Map<String, String> daily = fields(dailyText);
        String mode = daily.get("dailyMode");
        boolean active = "ENABLE".equals(mode);
        boolean recovering = "RECOVERY".equals(mode);
        boolean canStop = active || recovering || "BASELINE".equals(mode);
        Kind kind = Kind.UNKNOWN;
        boolean canEnable = false;
        if ("FINAL_UNCONFIRMED".equals(daily.get("dailyFinal"))
                || "RECOVERY_UNCONFIRMED".equals(daily.get("recoveryStatus"))
                || ("DISARMED".equals(mode) && "true".equals(daily.get("dailyNeedsRecovery")))
                || "UNHOOK_FAILED".equals(hooks) || "BOOTSTRAP_UNHOOK_FAILED".equals(bootstrap)) {
            kind = Kind.LOCKED;
        } else if (coverage != null && (coverage.contains("HOOK_MISSING") || coverage.contains("HOOK_FAILED"))) {
            kind = Kind.HOOK_FAILURE;
        } else if (error != null && !error.isEmpty() && !"NONE".equals(error)) {
            kind = Kind.CONTROL_ERROR;
        } else if (!"PERMANENT".equals(daily.get("dailyPolicy"))) {
            kind = Kind.UNKNOWN;
        } else if (recovering) {
            kind = Kind.RECOVERING;
        } else if (active && "HOOKED".equals(hooks)) {
            kind = "PROJECT_WALLPAPER".equals(daily.get("targetWallpaper"))
                    ? Kind.ACTIVE : Kind.WAITING_TARGET;
        } else if ("DISARMED".equals(mode) && "UNHOOKED".equals(hooks)
                && "BOOTSTRAP_UNHOOKED".equals(bootstrap)
                && "false".equals(daily.get("dailyNeedsRecovery"))
                && ("NONE".equals(daily.get("dailyFinal"))
                    || "FINAL_CONFIRMED".equals(daily.get("dailyFinal")))) {
            kind = Kind.READY;
            canEnable = true;
        }
        return new DashboardState(kind, canEnable, canStop, desiredAvailable, desiredEnabled);
    }

    /** 重复键和损坏字段不能洗成正常状态。 */
    static Map<String, String> fields(String text) {
        Map<String, String> result = new HashMap<>();
        if (text == null || text.trim().isEmpty()) return result;
        for (String token : text.trim().split("\\s+")) {
            int equals = token.indexOf('=');
            if (equals <= 0 || equals == token.length() - 1) return new HashMap<>();
            String key = token.substring(0, equals);
            if (result.put(key, token.substring(equals + 1)) != null) return new HashMap<>();
        }
        return result;
    }

    /** 开启必须已保存且能确认当前意愿；关闭保存失败仍优先撤销当前许可。 */
    static Mutation afterPersistence(boolean enable, boolean committed, boolean available,
                                     boolean savedEnabled, long revision) {
        if (!enable) return Mutation.STOP;
        return committed && available && savedEnabled && revision >= 0 ? Mutation.ENABLE : Mutation.REJECT;
    }

    static long commandRevision(boolean enable, boolean committed, boolean available, long revision) {
        return !enable && (!committed || !available) ? -1 : revision;
    }
}
