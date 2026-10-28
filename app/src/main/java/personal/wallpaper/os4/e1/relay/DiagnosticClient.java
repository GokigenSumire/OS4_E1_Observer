package personal.wallpaper.os4.e1.relay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 同进程转接端的有界只读快照核验；不持有开关、宿主或播放能力。 */
final class DiagnosticClient {
    static final int START = 7, STOP = 8, STATE = 9, PAGE = 10;
    static final int MAX_ROWS = 2000, MAX_BYTES = 131072, MAX_LINE = 128, PAGE_ROWS = 8;
    interface Transport { Reply call(int code, String token, long revision, int offset) throws Exception; }
    static final class Reply {
        final String token;
        final long revision;
        final int active, reason, rows, bytes;
        final List<String> lines;
        Reply(String token, long revision, int active, int reason, int rows, int bytes, List<String> lines) {
            this.token = token; this.revision = revision; this.active = active; this.reason = reason;
            this.rows = rows; this.bytes = bytes;
            this.lines = lines == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(lines));
        }
    }
    static final class Result {
        final boolean success, active, complete;
        final String operation, detail;
        final int reasonCode, totalRows, bytes;
        final List<String> lines;
        Result(boolean success, String operation, String detail, boolean active, boolean complete,
                int reasonCode, int totalRows, int bytes, List<String> lines) {
            this.success = success; this.operation = operation; this.detail = detail; this.active = active;
            this.complete = complete; this.reasonCode = reasonCode; this.totalRows = totalRows; this.bytes = bytes;
            this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        }
        static Result failure(String operation, String detail) {
            return new Result(false, safeOperation(operation), detail, false, false, -1, 0, 0, List.of());
        }
    }
    private Result cached;

    synchronized Result request(String operation, Transport transport) {
        if (!validOperation(operation)) return Result.failure(operation, "INVALID_OPERATION");
        try {
            Reply header;
            if ("START".equals(operation)) {
                Reply current = head(transport.call(STATE, null, 0, 0));
                if (current.active == 1)
                    return new Result(false, operation, "ALREADY_RUNNING", true, false,
                            current.reason, current.rows, current.bytes, List.of());
                header = head(transport.call(START, null, 0, 0));
                if (header.active != 1 || header.reason != 1 || header.revision <= 0 || "NONE".equals(header.token))
                    throw new Invalid("START_NOT_CONFIRMED");
                cached = null; cacheToken = null;
                return metadata(operation, header);
            }
            if ("STOP".equals(operation)) {
                Reply current = head(transport.call(STATE, null, 0, 0));
                if ("NONE".equals(current.token)) return Result.failure(operation, "NOT_STARTED");
                header = head(transport.call(STOP, current.token, 0, 0));
                if (!current.token.equals(header.token) || header.active != 0)
                    throw new Invalid("STOP_NOT_CONFIRMED");
                return readSnapshot(operation, header, transport);
            }
            header = head(transport.call(STATE, null, 0, 0));
            if ("STATUS".equals(operation)) return metadata(operation, header);
            if (header.active == 1) return Result.failure(operation, "STOP_FIRST");
            if ("NONE".equals(header.token)) return Result.failure(operation, "NOT_STARTED");
            return readSnapshot(operation, header, transport);
        } catch (Invalid invalid) {
            return Result.failure(operation, invalid.getMessage());
        } catch (Exception ignored) {
            return Result.failure(operation, "CHANNEL_UNAVAILABLE");
        }
    }

    private Result readSnapshot(String operation, Reply header, Transport transport) throws Exception {
        if (cached != null && cached.totalRows == header.rows && cached.bytes == header.bytes
                && cached.reasonCode == header.reason && cacheToken != null
                && cacheToken.equals(header.token) && cacheRevision == header.revision) {
            return new Result(true, operation, "FROZEN_SNAPSHOT", false, true,
                    header.reason, header.rows, header.bytes, cached.lines);
        }
        ArrayList<String> rows = new ArrayList<>();
        int observedBytes = 0;
        for (int offset = 0; offset < header.rows;) {
            Reply page = checked(transport.call(PAGE, header.token, header.revision, offset));
            if (!sameHeader(header, page)) throw new Invalid("SNAPSHOT_CHANGED");
            int count = page.lines.size();
            if (count < 1 || count > PAGE_ROWS || count > header.rows - offset)
                throw new Invalid("INVALID_PAGE_COUNT");
            for (String line : page.lines) {
                if (!safeLine(line)) throw new Invalid("UNSAFE_DIAGNOSTIC_LINE");
                observedBytes += line.length() + 1;
                if (observedBytes > MAX_BYTES) throw new Invalid("SNAPSHOT_BYTES_LIMIT");
                rows.add(line);
            }
            offset += count;
        }
        if (rows.size() != header.rows || observedBytes != header.bytes)
            throw new Invalid("SNAPSHOT_BYTES_MISMATCH");
        Reply finalHeader = head(transport.call(STATE, null, 0, 0));
        if (!sameHeader(header, finalHeader)) throw new Invalid("SNAPSHOT_CHANGED");
        Result result = new Result(true, operation, "FROZEN_SNAPSHOT", false, true,
                header.reason, header.rows, header.bytes, rows);
        cached = result; cacheToken = header.token; cacheRevision = header.revision;
        return result;
    }
    private String cacheToken;
    private long cacheRevision;
    private static Result metadata(String operation, Reply header) {
        return new Result(true, operation, header.active == 1 ? "RUNNING" : header.reason == 0 ? "NOT_STARTED" : "STOPPED",
                header.active == 1, false, header.reason, header.rows, header.bytes, List.of());
    }
    private static Reply checked(Reply reply) throws Invalid {
        if (reply == null || reply.token == null || !reply.token.matches("[A-Za-z0-9_.-]{1,128}")
                || reply.revision < 0 || (reply.active != 0 && reply.active != 1)
                || reply.reason < 0 || reply.reason > 6 || reply.rows < 0 || reply.rows > MAX_ROWS
                || reply.bytes < 0 || reply.bytes > MAX_BYTES || reply.lines.size() > PAGE_ROWS
                || (reply.rows == 0 && reply.bytes != 0) || (reply.rows > 0 && reply.bytes == 0)
                || (reply.active == 1 && reply.reason != 1) || (reply.active == 0 && reply.reason == 1)
                || ("NONE".equals(reply.token) && (reply.active != 0 || reply.reason != 0 || reply.rows != 0 || reply.bytes != 0 || reply.revision != 0))
                || (!"NONE".equals(reply.token) && (reply.revision <= 0 || reply.reason == 0)))
            throw new Invalid("INVALID_HEADER");
        return reply;
    }
    private static Reply head(Reply reply) throws Invalid {
        checked(reply);
        if (!reply.lines.isEmpty()) throw new Invalid("UNEXPECTED_HEADER_LINES");
        return reply;
    }
    private static boolean sameHeader(Reply left, Reply right) {
        return left.token.equals(right.token) && left.revision == right.revision && left.active == right.active
                && left.reason == right.reason && left.rows == right.rows && left.bytes == right.bytes;
    }
    static boolean validOperation(String operation) {
        return "START".equals(operation) || "STOP".equals(operation) || "STATUS".equals(operation) || "SNAPSHOT".equals(operation);
    }
    static Result display(boolean success, String operation, String detail, boolean active, boolean complete,
            int reasonCode, int totalRows, int bytes, List<String> lines) {
        boolean runningRefusal = !success && "START".equals(operation) && "ALREADY_RUNNING".equals(detail)
                && active && !complete && reasonCode == 1 && lines != null && lines.isEmpty();
        if ((!validOperation(operation) && !"INVALID".equals(operation)) || detail == null
                || !detail.matches("[A-Z_]{1,64}") || lines == null || totalRows < 0 || totalRows > MAX_ROWS
                || bytes < 0 || bytes > MAX_BYTES || (!complete && !lines.isEmpty())
                || (complete && (active || lines.size() != totalRows))
                || (totalRows == 0 && bytes != 0) || (totalRows > 0 && bytes == 0)
                || (success && (reasonCode < 0 || reasonCode > 6 || (active != (reasonCode == 1))))
                || (!success && !runningRefusal && (active || complete || reasonCode != -1 || totalRows != 0 || bytes != 0 || !lines.isEmpty())))
            throw new IllegalArgumentException("INVALID_DIAGNOSTIC_DISPLAY");
        int measured = 0;
        for (String line : lines) { if (!safeLine(line)) throw new IllegalArgumentException("INVALID_DIAGNOSTIC_LINE"); measured += line.length() + 1; }
        if (complete && measured != bytes) throw new IllegalArgumentException("DIAGNOSTIC_BYTES_MISMATCH");
        return new Result(success, operation, detail, active, complete, reasonCode, totalRows, bytes, lines);
    }
    private static String safeOperation(String operation) { return validOperation(operation) ? operation : "INVALID"; }
    private static boolean safeLine(String line) {
        return line != null && line.length() >= 1 && line.length() + 1 <= MAX_LINE && line.matches("[A-Za-z0-9_= .:-]+");
    }
    private static final class Invalid extends Exception { Invalid(String reason) { super(reason); } }
}
