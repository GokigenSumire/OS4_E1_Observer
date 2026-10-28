package personal.wallpaper.os4.e1.core;

/**
 * 只管理本进程内的日常 HOME getter 许可，不读取、持久化或修改 Android 对象。
 * 默认关闭；新实例不会自动启用。UI 退出不改变显式启用的本次会话。
 * 调用方必须以同一外层锁包住 permit、实际改返回值、overrideApplied 和撤权。
 * now 须来自包含休眠时间的单调时钟；状态查询与回调均不能续期。
 */
public final class DailyController implements SessionPolicy {
    public enum DurationPolicy { PERMANENT, TIMED }
    public static final long NO_DEADLINE = -1L;
    // 仅供保留的限时构造；生产默认策略没有时长上限。
    public static final long MAX_DURATION_MS = 43_200_000L;
    public static final long MAX_RATE_OVERRIDES = 2_000L;
    public static final long RATE_WINDOW_MS = 60_000L;
    public static final String DISARMED = "DISARMED";
    public static final String BASELINE = "BASELINE";
    public static final String ENABLE = "ENABLE";
    public static final String RECOVERY = "RECOVERY";
    public static final String FINAL_CONFIRMED = "FINAL_CONFIRMED";
    public static final String FINAL_UNCONFIRMED = "FINAL_UNCONFIRMED";

    private final SessionEpoch epochs;
    private final long durationLimit;
    private final DurationPolicy durationPolicy;
    private final long rateWindow;
    private final long[] reservations;
    private int rateHead;
    private int rateSize;
    private String mode = DISARMED;
    private String reason = "NOT_STARTED";
    private String finalState = "NONE";
    private long startedAt;
    private long latestAt;
    private long deadlineAt;
    private long activeEndedAt;
    private long recoveryStartedAt;
    private long permits;
    private long applied;
    private long recoveryObservations;
    private boolean recoveryProved;
    private boolean fieldViolation;
    private Thread pendingOwner;

    public DailyController() {
        this(DurationPolicy.PERMANENT, 0, MAX_RATE_OVERRIDES, RATE_WINDOW_MS, 0);
    }

    /** 测试只允许降低时长、降低次数或延长计数窗口，不能放宽生产限制。 */
    DailyController(long durationLimit, long rateLimit, long rateWindow) {
        this(durationLimit, rateLimit, rateWindow, 0);
    }

    /** 初始票据仅供溢出边界测试；票据用尽后不回绕或复用。 */
    DailyController(long durationLimit, long rateLimit, long rateWindow, long initialEpoch) {
        this(DurationPolicy.TIMED, durationLimit, rateLimit, rateWindow, initialEpoch);
    }

    private DailyController(DurationPolicy policy, long durationLimit, long rateLimit,
                            long rateWindow, long initialEpoch) {
        if (policy == null || (policy == DurationPolicy.TIMED
                && (durationLimit < 1 || durationLimit > MAX_DURATION_MS))
                || (policy == DurationPolicy.PERMANENT && durationLimit != 0)
                || rateLimit < 1 || rateLimit > MAX_RATE_OVERRIDES
                || rateWindow < RATE_WINDOW_MS) {
            throw new IllegalArgumentException("INVALID_LIMIT");
        }
        this.epochs = new SessionEpoch(initialEpoch);
        this.durationLimit = durationLimit;
        this.durationPolicy = policy;
        this.deadlineAt = policy == DurationPolicy.PERMANENT ? NO_DEADLINE : 0;
        this.rateWindow = rateWindow;
        this.reservations = new long[(int) rateLimit];
    }

    /** 只能由用户请求新建 BASELINE；0 表示拒绝，不清除现有会话事实。 */
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
        deadlineAt = isPermanent() ? NO_DEADLINE : now > Long.MAX_VALUE - durationLimit
                ? Long.MAX_VALUE : now + durationLimit;
        activeEndedAt = now;
        recoveryStartedAt = 0;
        permits = 0;
        applied = 0;
        recoveryObservations = 0;
        rateHead = 0;
        rateSize = 0;
        recoveryProved = false;
        fieldViolation = false;
        pendingOwner = null;
        finalState = "NONE";
        mode = BASELINE;
        reason = "RUNNING";
        return epochs.current();
    }

    /** 同一会话只允许从 BASELINE 启用一次；查询和重复开启不能建立新会话。 */
    public synchronized boolean enable(long now) {
        tick(now);
        if (!BASELINE.equals(mode)) return false;
        mode = ENABLE;
        return true;
    }

    /** 先撤许可，再等新的自然原路径；重复停止不覆盖首因或重置恢复起点。 */
    public synchronized boolean beginRecovery(long now, String stopReason) {
        tick(now);
        if (DISARMED.equals(mode)) return false;
        enterRecovery(safeReason(stopReason));
        return true;
    }

    /**
     * 只为同一认证 HOME、同一次自然 populate、原 false getter 预留同线程许可。
     * 限速窗口为 (当前单调时间 - 60 秒, 当前时间]；预约即占次数，避免未确认放宽限制。
     * 旧票据在校时前拒绝，不能推进、超时或重置新会话。
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
        expireReservations();
        if (rateSize == reservations.length) {
            enterRecovery("LIMIT_RATE");
            return false;
        }
        reservations[(rateHead + rateSize) % reservations.length] = latestAt;
        rateSize++;
        permits = increment(permits);
        pendingOwner = Thread.currentThread();
        return true;
    }

    /** 必须紧跟实际 setResult，一个预约只能由同线程确认一次。 */
    public synchronized boolean overrideApplied(long now, long ticket) {
        if (!epochs.accepts(ticket)) return false;
        tick(now);
        if (!ENABLE.equals(mode) || pendingOwner != Thread.currentThread()) return false;
        pendingOwner = null;
        applied = increment(applied);
        return true;
    }

    /** 记录已认证同会话原字段的违反事实；不冒充一次自然恢复路径或正向证明。 */
    public synchronized boolean noteFieldViolation(long now, long ticket) {
        if (!epochs.accepts(ticket)) return false;
        tick(now);
        if (DISARMED.equals(mode)) return false;
        fieldViolation = true;
        recoveryProved = false;
        enterRecovery("ERROR");
        return true;
    }

    /**
     * exactWrapper 由调用方证明：在途覆盖已退出，新的认证 HOME populate 进入时已撤权，
     * 并自然走原 getter 路径。原字段违反事实粘性保留，后续正常观察不能洗掉。
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

    /** 未确认恢复会锁定本实例；只有新的系统进程实例能解除，UI 重开不能解除。 */
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

    /** 只导出受控状态和数值，不回显外部文本、异常、对象或路径。 */
    public synchronized String status(long now) {
        tick(now);
        return "dailyMode=" + mode + " dailyEpoch=" + epochs.current()
                + " dailyPolicy=" + durationPolicy
                + " dailyReason=" + reason + " dailyElapsedMs=" + elapsed()
                + " dailyDurationMs=" + durationLimit + " dailyDeadline=" + deadlineAt
                + " dailyActiveMs=" + activeDurationMillis()
                + " dailyRateLimit=" + reservations.length + " dailyRateWindowMs=" + rateWindow
                + " dailyPermits=" + permits + " dailyApplied=" + applied
                + " dailyPending=" + (pendingOwner != null)
                + " dailyRecoveryObservations=" + recoveryObservations
                + " dailyRecoveryProved=" + recoveryProved
                + " dailyFieldViolation=" + fieldViolation
                + " dailyNeedsRecovery=" + needsRecovery()
                + " dailyFinal=" + finalState;
    }

    public synchronized long epoch() { return epochs.current(); }
    public synchronized String mode() { return mode; }
    public synchronized long appliedCount() { return applied; }
    public synchronized long permitCount() { return permits; }
    public synchronized long durationLimitMillis() { return durationLimit; }
    public boolean isPermanent() { return durationPolicy == DurationPolicy.PERMANENT; }
    public synchronized long deadline() { return deadlineAt; }
    public synchronized long activeDurationMillis(long now) {
        tick(now);
        return activeDurationMillis();
    }
    /** 从 BASELINE 开始到首次撤权的会话时长，恢复过程或后来的错误不会增长此值。 */
    public synchronized long activeDurationMillis() {
        long end = BASELINE.equals(mode) || ENABLE.equals(mode) ? latestAt : activeEndedAt;
        return isPermanent() ? end - startedAt : Math.min(durationLimit, end - startedAt);
    }
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
        if (!isPermanent() && (BASELINE.equals(mode) || ENABLE.equals(mode)) && latestAt >= deadlineAt) {
            enterRecovery("TIMEOUT");
        }
    }

    private void expireReservations() {
        while (rateSize > 0 && latestAt - reservations[rateHead] >= rateWindow) {
            rateHead = (rateHead + 1) % reservations.length;
            rateSize--;
        }
    }

    private long elapsed() { return latestAt - startedAt; }

    private void enterRecovery(String stopReason) {
        pendingOwner = null;
        if (RECOVERY.equals(mode)) {
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
        activeEndedAt = isPermanent() ? latestAt : Math.min(latestAt, deadlineAt);
        recoveryProved = false;
    }

    private static long increment(long count) {
        return count == Long.MAX_VALUE ? count : count + 1;
    }

    private static String safeReason(String value) {
        if (value == null) return "ERROR";
        switch (value) {
            case "USER_STOP": case "USER_RECOVERY": case "TIMEOUT":
            case "LIMIT_RATE": case "LIMIT_OVERRIDES": case "LIMIT_ROWS":
            case "LIMIT_BYTES": case "LIMIT_LINE": case "ERROR":
            case "AMBIGUOUS_HOME": case "IDENTITY_CHANGED": case "UNHOOK_REQUESTED":
                return value;
            default: return "ERROR";
        }
    }
}
