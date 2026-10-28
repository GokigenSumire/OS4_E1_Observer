package personal.wallpaper.os4.e1.core;

/**
 * 仅管理临时 HOME getter 对照实验的许可，不读取或写入 Android 对象。
 * 所有方法共用实例锁；调用方还须用同一外层锁包住 permit、实际改返回值和
 * overrideApplied，避免 STOP 在获许可与改返回值之间穿插。
 * now 必须来自持续计时的单调时钟，休眠或切换模式不延长硬截止。
 */
public final class ContrastController implements SessionPolicy {
    public static final long MAX_DURATION_MS = 180_000L;
    public static final long MAX_OVERRIDES = 2_000L;
    public static final String DISARMED = "DISARMED";
    public static final String BASELINE = "BASELINE";
    public static final String ENABLE = "ENABLE";
    public static final String RECOVERY = "RECOVERY";
    public static final String FINAL_CONFIRMED = "FINAL_CONFIRMED";
    public static final String FINAL_UNCONFIRMED = "FINAL_UNCONFIRMED";

    private final SessionEpoch epochs;
    private final long durationLimit;
    private final long overrideLimit;
    private String mode = DISARMED;
    private String reason = "NOT_STARTED";
    private String finalState = "NONE";
    private long startedAt;
    private long latestAt;
    private long recoveryStartedAt;
    private long permits;
    private long applied;
    private long recoveryObservations;
    private boolean recoveryProved;
    private boolean fieldViolation;
    private Thread pendingOwner;

    public ContrastController() {
        this(MAX_DURATION_MS, MAX_OVERRIDES, 0);
    }

    /** 测试可降低但不能抬高生产硬限额。 */
    ContrastController(long durationLimit, long overrideLimit) {
        this(durationLimit, overrideLimit, 0);
    }

    /** 初始票据仅供边界测试；票据用尽后不回绕、不复用。 */
    ContrastController(long durationLimit, long overrideLimit, long initialEpoch) {
        if (durationLimit < 1 || durationLimit > MAX_DURATION_MS
                || overrideLimit < 1 || overrideLimit > MAX_OVERRIDES) {
            throw new IllegalArgumentException("INVALID_LIMIT");
        }
        this.epochs = new SessionEpoch(initialEpoch);
        this.durationLimit = durationLimit;
        this.overrideLimit = overrideLimit;
    }

    /** 新实验只能从 BASELINE 开始；0 表示拒绝，原实验计数不清除。 */
    public synchronized long start(long now, String requestedMode) {
        if (now < 0 || !BASELINE.equals(requestedMode)) {
            if (!DISARMED.equals(mode)) enterRecovery("ERROR");
            return 0;
        }
        tick(now);
        if (!DISARMED.equals(mode) || FINAL_UNCONFIRMED.equals(finalState)) return 0;
        try {
            epochs.advance();
        } catch (IllegalStateException exhausted) {
            reason = "EPOCH_EXHAUSTED";
            return 0;
        }
        startedAt = now;
        latestAt = now;
        recoveryStartedAt = 0;
        permits = 0;
        applied = 0;
        recoveryObservations = 0;
        recoveryProved = false;
        fieldViolation = false;
        pendingOwner = null;
        finalState = "NONE";
        mode = BASELINE;
        reason = "RUNNING";
        return epochs.current();
    }

    /** 只允许当前实验由基线进入一次 ENABLE；不续期、不清计数。 */
    public synchronized boolean enable(long now) {
        tick(now);
        if (!BASELINE.equals(mode)) return false;
        mode = ENABLE;
        return true;
    }

    /** 先撤销许可再等待自然恢复证据；重复请求不重置恢复起点或首个原因。 */
    public synchronized boolean beginRecovery(long now, String stopReason) {
        tick(now);
        if (DISARMED.equals(mode)) return false;
        enterRecovery(safeReason(stopReason));
        return true;
    }

    /**
     * 只预留一次同线程许可。参数是调用方在同一自然 populate 中取得的真实条件。
     * 无关对象／短路分支只拒绝许可；身份变化须由调用方显式 beginRecovery。
     * 旧票据在校时前拒绝，因此旧回调不能使新实验超时或清除新计数。
     */
    public synchronized boolean permit(long now, long ticket, boolean certified,
                                       boolean exactAttrs, boolean insidePopulate,
                                       boolean target, boolean keyguard,
                                       boolean originalFalse) {
        if (!epochs.accepts(ticket)) return false;
        tick(now);
        if (!ENABLE.equals(mode)) return false;
        if (!certified || !exactAttrs || !insidePopulate
                || !target || keyguard || !originalFalse) return false;
        if (pendingOwner != null) {
            enterRecovery("ERROR");
            return false;
        }
        if (permits >= overrideLimit) {
            enterRecovery("LIMIT_OVERRIDES");
            return false;
        }
        permits++;
        pendingOwner = Thread.currentThread();
        return true;
    }

    /** 必须紧跟真实 setResult；一个许可只能确认一次，且不能由另一线程确认。 */
    public synchronized boolean overrideApplied(long now, long ticket) {
        if (!epochs.accepts(ticket)) return false;
        tick(now);
        if (!ENABLE.equals(mode) || pendingOwner != Thread.currentThread()) return false;
        pendingOwner = null;
        applied++;
        if (applied >= overrideLimit) enterRecovery("LIMIT_OVERRIDES");
        return true;
    }

    /**
     * exactWrapper 必须证明：当前认证 HOME 的自然 populate 在进入时已是 RECOVERY，
     * 且走过原 getter 路径。不能用旧 ENABLE wrapper、UI STOP 或 publish 未变化替代。
     * 一次原字段被改动的观察会锁定该实验为未确认，后来的正常值不能洗掉违反事实。
     */
    public synchronized boolean observeRecovery(long now, long ticket, boolean exactWrapper,
                                                boolean dupFalse,
                                                boolean baselineFieldIntact) {
        if (!epochs.accepts(ticket)) return false;
        tick(now);
        if (!RECOVERY.equals(mode) || now < recoveryStartedAt || !exactWrapper) return false;
        recoveryObservations = increment(recoveryObservations);
        if (!baselineFieldIntact) fieldViolation = true;
        if (!dupFalse || !baselineFieldIntact || fieldViolation) {
            recoveryProved = false;
            return false;
        }
        recoveryProved = true;
        return true;
    }

    /**
     * 结束恢复观察。确认状态冻结；未确认即使未实际改值也不能冒充恢复成功，
     * 并锁定本实例不得再次开始实验（重建系统服务后由新实例自然复位）。
     */
    public synchronized String finalizeRecovery(long now, long ticket) {
        if (!epochs.accepts(ticket)) return "STALE_EPOCH";
        if (!"NONE".equals(finalState)) return finalState;
        tick(now);
        if (!RECOVERY.equals(mode)) enterRecovery("USER_STOP");
        pendingOwner = null;
        finalState = recoveryProved && !fieldViolation ? FINAL_CONFIRMED : FINAL_UNCONFIRMED;
        mode = DISARMED;
        return finalState;
    }
    public synchronized boolean noteFieldViolation(long now,long ticket) {
        if(!epochs.accepts(ticket))return false;
        tick(now);if(DISARMED.equals(mode))return false;
        fieldViolation=true;recoveryProved=false;enterRecovery("ERROR");return true;
    }

    /** 仅固定字段、受控枚举及数值；不回显异常、对象、URI 或调用方文本。 */
    public synchronized String status(long now) {
        tick(now);
        return "contrastMode=" + mode + " contrastEpoch=" + epochs.current()
                + " contrastReason=" + reason + " contrastElapsedMs=" + elapsed()
                + " contrastDurationMs=" + durationLimit + " contrastOverrideLimit=" + overrideLimit
                + " contrastPermits=" + permits + " contrastApplied=" + applied
                + " contrastPending=" + (pendingOwner != null)
                + " contrastRecoveryObservations=" + recoveryObservations
                + " contrastRecoveryProved=" + recoveryProved
                + " contrastFieldViolation=" + fieldViolation
                + " contrastNeedsRecovery=" + needsRecovery()
                + " contrastFinal=" + finalState;
    }

    public synchronized long epoch() { return epochs.current(); }
    public long durationLimitMillis() { return durationLimit; }
    public synchronized String mode() { return mode; }
    public synchronized long appliedCount() { return applied; }
    public synchronized long permitCount() { return permits; }
    public synchronized boolean recoveryProved() { return recoveryProved; }
    public synchronized boolean needsRecovery() {
        return FINAL_UNCONFIRMED.equals(finalState)
                || RECOVERY.equals(mode) && !recoveryProved;
    }

    private void tick(long now) {
        if (DISARMED.equals(mode)) return;
        if (now < 0) {
            enterRecovery("ERROR");
            return;
        }
        latestAt = Math.max(latestAt, now);
        if ((BASELINE.equals(mode) || ENABLE.equals(mode)) && elapsed() >= durationLimit) {
            enterRecovery("TIMEOUT");
        }
    }

    private long elapsed() { return latestAt - startedAt; }

    private void enterRecovery(String stopReason) {
        pendingOwner = null;
        if (RECOVERY.equals(mode)) {
            // 已有证明不能跨越新的身份变化或错误；重复用户停止不延长恢复观察窗。
            if ("ERROR".equals(stopReason) || "IDENTITY_CHANGED".equals(stopReason)
                    || "AMBIGUOUS_HOME".equals(stopReason)) {
                recoveryProved = false;
                recoveryStartedAt = latestAt;
            }
            return;
        }
        mode = RECOVERY;
        reason = stopReason;
        recoveryStartedAt = latestAt;
        recoveryProved = false;
    }

    private static long increment(long count) {
        return count == Long.MAX_VALUE ? count : count + 1;
    }

    private static String safeReason(String value) {
        if (value == null) return "ERROR";
        switch (value) {
            case "USER_STOP": case "USER_RECOVERY": case "TIMEOUT":
            case "LIMIT_OVERRIDES": case "LIMIT_ROWS": case "LIMIT_BYTES":
            case "LIMIT_LINE": case "ERROR": case "AMBIGUOUS_HOME":
            case "IDENTITY_CHANGED": case "UNHOOK_REQUESTED":
                return value;
            default: return "ERROR";
        }
    }
}
