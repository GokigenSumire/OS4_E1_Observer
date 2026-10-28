package personal.wallpaper.os4.e1.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 只保存受限标量的临时内存观测记录，不进行文件、网络或 Android 系统操作。
 * 所有公开方法共用实例锁；记录在返回前复制，不持有调用方 Map。
 * 时间由调用方传入单调时钟，带时间的方法立即检查超时；停止后时间和首个原因冻结。
 * 显式非法启动请求会报告新的拒绝原因，但不会清除此前记录。
 */
public final class ObservationBuffer {
    public static final long MAX_DURATION_MS = 200_000L;
    public static final int MAX_ROWS = 6_000;
    public static final int MAX_BYTES = 1_572_864;
    public static final int MAX_LINE_BYTES = 1_024;

    private static final int MAX_SESSION_LENGTH = 64;
    private static final int MAX_SCALAR_LENGTH = 20;

    private static final Set<String> POINTS = fixedSet(
            "SNAPSHOT", "POPULATE_BEFORE", "POPULATE_AFTER", "TARGET_RESULT",
            "KEYGUARD_RESULT", "BOOL_GETTER", "HAS_BEFORE", "HAS_AFTER",
            "MASK_BEFORE", "MASK_AFTER", "PUBLISH_BEFORE", "PUBLISH_AFTER",
            "SURFACE_SET_BEFORE", "SURFACE_SET_AFTER", "WMS_RELAYOUT_AFTER",
            "WMS_ADD_AFTER", "COPY_BEFORE", "COPY_AFTER", "PHASE_CHANGE", "OVERRIDE",
            "RECOVERY_RESULT", "USER_MARK");

    private static final Set<String> KEYS = fixedSet(
            "touch", "target", "keyguard", "dup", "inputConfig", "changed",
            "requested", "mask", "value", "sourceTouch", "destTouch", "arrivalTouch",
            "returnCode", "threw", "qualified", "showWallpaper", "hasSurface",
            "visible", "focus", "copyChanges", "submitted", "homeUnique",
            "phase", "original", "effective", "applied", "recovery", "mark");

    private static final Set<String> ENUMS = fixedSet(
            "UNKNOWN", "WMS_RELAYOUT", "WMS_ADD", "OTHER", "POPULATE",
            "SNAPSHOT", "NOT_OBSERVED", "JAVA_CALL", "JAVA_COMPLETED",
            "BASELINE", "ENABLE", "RECOVERY", "DISARMED", "CONFIRMED", "UNCONFIRMED",
            "PENDING", "DOUBLE", "TRIPLE", "TWO", "LOCK", "UNLOCK", "SWIPE", "ICON",
            "EDITOR", "PREVIEW");

    private static final Set<String> STOP_REASONS = fixedSet(
            "USER_STOP", "TIMEOUT", "LIMIT_ROWS", "LIMIT_BYTES", "LIMIT_LINE",
            "ERROR", "AMBIGUOUS_HOME", "UNHOOK_REQUESTED", "INVALID_SESSION", "RECOVERY_COMPLETE");

    private final int rowLimit;
    private final int byteLimit;
    private final int lineLimit;
    private final long durationLimit;
    private final ArrayList<String> rows = new ArrayList<>();

    private boolean collecting;
    private String session = "NONE";
    private String reason = "NOT_STARTED";
    private long startedAt;
    private long elapsedMs;
    private long dropped;
    private long rejected;
    private int bytes;

    public ObservationBuffer() {
        this(MAX_ROWS, MAX_BYTES, MAX_LINE_BYTES, MAX_DURATION_MS);
    }

    /** 测试可降低上限；任何构造参数都不能提升生产上限。 */
    ObservationBuffer(int rowLimit, int byteLimit, int lineLimit, long durationLimit) {
        if (rowLimit < 1 || rowLimit > MAX_ROWS
                || byteLimit < 1 || byteLimit > MAX_BYTES
                || lineLimit < 1 || lineLimit > MAX_LINE_BYTES
                || durationLimit < 1 || durationLimit > MAX_DURATION_MS) {
            throw new IllegalArgumentException("INVALID_LIMIT");
        }
        this.rowLimit = rowLimit;
        this.byteLimit = byteLimit;
        this.lineLimit = lineLimit;
        this.durationLimit = durationLimit;
    }

    /**
     * 仅合法且当前已停止的会话可以启动并清空旧记录。
     * 活跃时重复启动返回 false，保留原会话；非法标识不保存、不回显，且停止当前记录。
     */
    public synchronized boolean start(long now, String sessionId) {
        if (!safeSession(sessionId) || now < 0) {
            collecting = false;
            reason = now < 0 ? "ERROR" : "INVALID_SESSION";
            return false;
        }
        expire(now);
        if (collecting) {
            return false;
        }
        rows.clear();
        bytes = 0;
        dropped = 0;
        rejected = 0;
        elapsedMs = 0;
        startedAt = now;
        session = sessionId;
        reason = "RUNNING";
        collecting = true;
        return true;
    }

    /** 仅接受固定原因；其他输入统一记 ERROR，不传播异常消息或其他文本。 */
    public synchronized void stop(String stopReason) {
        if (collecting) {
            finish(STOP_REASONS.contains(stopReason) ? stopReason : "ERROR");
        }
    }

    /**
     * 接受完整安全记录才返回 true。非法记录整行拒绝，增加 dropped/rejected。
     * null 字段值表示 UNKNOWN；缺失字段保持缺失，不推断 false。
     * 每行及总字节数均包括用于导出的一个 LF 字节；安全编码全为 ASCII。
     */
    public synchronized boolean record(long now, String point, int generation,
                                       Map<String, String> values) {
        expire(now);
        if (!collecting) {
            dropped = increment(dropped);
            return false;
        }
        if (!POINTS.contains(point) || values == null) {
            return reject();
        }

        final TreeMap<String, String> safeValues = new TreeMap<>();
        try {
            if (values.size() > KEYS.size()) {
                return reject();
            }
            int entries = 0;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (++entries > KEYS.size() || entry == null) {
                    return reject();
                }
                String key = entry.getKey();
                String value = entry.getValue();
                if (!KEYS.contains(key) || !safeScalar(value)) {
                    return reject();
                }
                if (safeValues.put(key, value == null ? "UNKNOWN" : value) != null) {
                    return reject();
                }
            }
        } catch (RuntimeException ignored) {
            // 调用方 Map 失效、并发变更或类型错误只影响观测；不打印异常或原输入。
            return reject();
        }

        StringBuilder row = new StringBuilder(128);
        row.append("seq=").append(rows.size() + 1)
                .append(" elapsedMs=").append(elapsedMs)
                .append(" session=").append(session)
                .append(" point=").append(point)
                .append(" generation=").append(generation);
        for (Map.Entry<String, String> entry : safeValues.entrySet()) {
            row.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
        }
        int lineBytes = row.length() + 1;
        if (lineBytes > lineLimit) {
            dropped = increment(dropped);
            finish("LIMIT_LINE");
            return false;
        }
        if (lineBytes > byteLimit - bytes) {
            dropped = increment(dropped);
            finish("LIMIT_BYTES");
            return false;
        }
        rows.add(row.toString());
        bytes += lineBytes;
        if (rows.size() >= rowLimit) {
            finish("LIMIT_ROWS");
        } else if (bytes >= byteLimit) {
            finish("LIMIT_BYTES");
        } else if (lineBytes >= lineLimit) {
            finish("LIMIT_LINE");
        }
        return true;
    }

    /** 只包含固定字段、安全会话标识、受限原因和数值；不会包含调用方异常文本。 */
    public synchronized String status(long now) {
        expire(now);
        return "active=" + collecting + " session=" + session + " reason=" + reason
                + " rows=" + rows.size() + " bytes=" + bytes + " dropped=" + dropped
                + " rejected=" + rejected + " elapsedMs=" + elapsedMs;
    }

    /** 返回不可修改的分页快照；非法范围返回空表，巨大 count 不会发生整数溢出。 */
    public synchronized List<String> page(int offset, int count) {
        if (offset < 0 || count <= 0 || offset >= rows.size()) {
            return Collections.emptyList();
        }
        int end = offset + Math.min(count, rows.size() - offset);
        return Collections.unmodifiableList(new ArrayList<>(rows.subList(offset, end)));
    }

    public synchronized int size() {
        return rows.size();
    }

    public synchronized boolean active(long now) {
        expire(now);
        return collecting;
    }

    private void expire(long now) {
        if (!collecting) {
            return;
        }
        // 回退时间不降低 elapsed；start 保证 startedAt 非负，正差不会溢出。
        long delta = now > startedAt ? now - startedAt : 0;
        elapsedMs = Math.min(durationLimit, Math.max(elapsedMs, delta));
        if (elapsedMs >= durationLimit) {
            finish("TIMEOUT");
        }
    }

    private void finish(String stopReason) {
        collecting = false;
        reason = stopReason;
    }

    private boolean reject() {
        dropped = increment(dropped);
        rejected = increment(rejected);
        return false;
    }

    private static long increment(long count) {
        return count == Long.MAX_VALUE ? count : count + 1;
    }

    private static boolean safeSession(String value) {
        if (value == null || value.length() < 1 || value.length() > MAX_SESSION_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= 'A' && c <= 'Z')
                    && !(c >= '0' && c <= '9') && c != '-' && c != '_') {
                return false;
            }
        }
        return true;
    }

    private static boolean safeScalar(String value) {
        if (value == null) {
            return true;
        }
        if (value.isEmpty() || value.length() > MAX_SCALAR_LENGTH) {
            return false;
        }
        if ("true".equals(value) || "false".equals(value) || ENUMS.contains(value)) {
            return true;
        }
        if (value.startsWith("0x") && value.length() >= 3 && value.length() <= 18) {
            for (int i = 2; i < value.length(); i++) {
                char c = value.charAt(i);
                if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')
                        && !(c >= 'A' && c <= 'F')) {
                    return false;
                }
            }
            return true;
        }
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start == value.length()) {
            return false;
        }
        for (int i = start; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Set<String> fixedSet(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }
}
