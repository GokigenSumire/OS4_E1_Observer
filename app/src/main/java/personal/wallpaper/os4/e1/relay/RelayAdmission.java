package personal.wallpaper.os4.e1.relay;

import java.util.LinkedHashMap;
import personal.wallpaper.protocol.lock.LockProtocol;

/** 中继不接原始触摸；同一时刻只保一个动作，生产者序列与当前图票据不重定向。 */
final class RelayAdmission {
    static final int ACCEPTED = 0, NOT_READY = 1, REJECTED = 2, BUSY = 3;
    private final LinkedHashMap<String, Long> highWater = new LinkedHashMap<>();
    private boolean inFlight;
    synchronized int admit(boolean enabled, boolean ready, int action, long seq, long issued,
            long actorEpoch, String actorSession, String ticket, long now) {
        if (!enabled || !ready) return NOT_READY;
        if (!LockProtocol.action(action) || seq <= 0 || seq == Long.MAX_VALUE || actorEpoch <= 0
                || !LockProtocol.token(actorSession) || !LockProtocol.token(ticket)
                || !LockProtocol.timely(action, issued, now)) return REJECTED;
        String key = actorEpoch + ":" + actorSession;
        Long previous = highWater.get(key);
        if (previous != null && seq <= previous) return REJECTED;
        highWater.put(key, seq);
        if (highWater.size() > 8) highWater.remove(highWater.keySet().iterator().next());
        if (inFlight) return BUSY;
        inFlight = true;
        return ACCEPTED;
    }
    synchronized void complete() { inFlight = false; }
    synchronized void clear() { inFlight = false; highWater.clear(); }
    synchronized int size() { return highWater.size(); }
}
