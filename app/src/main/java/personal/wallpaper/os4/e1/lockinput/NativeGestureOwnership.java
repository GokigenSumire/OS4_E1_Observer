package personal.wallpaper.os4.e1.lockinput;

import java.util.ArrayDeque;
import java.util.Arrays;
import personal.wallpaper.protocol.lock.LockProtocol;

/**
 * 原生入口的纯本地所有权。这里只核对象身份、代次和同步调用范围，不判断认证或区域安全。
 * 不持有 MotionEvent，不发送命令，也不保存或重放原生息屏动作。
 */
public final class NativeGestureOwnership {
    public static final int MAX_DEPTH = 8;
    public static final int MAX_RECENT_EVENTS = 32;
    public static final int MAX_POINTERS = 32;
    public static final long INVALID_GENERATION = -1;

    public static final class Binding {
        public final Object injector, controller, root, helper, listener, channel;
        public final long actorEpoch, modeRevision, authorityGeneration;
        public final String ticket;

        public Binding(Object injector, Object controller, Object root, Object helper,
                Object listener, Object channel, long actorEpoch, String ticket,
                long modeRevision, long authorityGeneration) {
            if (injector == null || controller == null || root == null || helper == null
                    || listener == null || channel == null || actorEpoch <= 0
                    || !LockProtocol.token(ticket) || modeRevision < 0 || authorityGeneration < 0)
                throw new IllegalArgumentException("INVALID_NATIVE_BINDING");
            this.injector = injector; this.controller = controller; this.root = root;
            this.helper = helper; this.listener = listener; this.channel = channel;
            this.actorEpoch = actorEpoch; this.ticket = ticket;
            this.modeRevision = modeRevision; this.authorityGeneration = authorityGeneration;
        }

        public boolean matches(Binding other) {
            return other != null && injector == other.injector && controller == other.controller
                    && root == other.root && helper == other.helper && listener == other.listener
                    && channel == other.channel && actorEpoch == other.actorEpoch
                    && ticket.equals(other.ticket) && modeRevision == other.modeRevision
                    && authorityGeneration == other.authorityGeneration;
        }
    }

    /** 指针按稳定 ID 排序；坐标仅作当前进程短期去重，不导出或形成轨迹。 */
    public static final class EventStamp {
        private final long downTime, eventTime;
        private final int action, actionPointerId;
        private final int[] ids, xBits, yBits;

        public EventStamp(long downTime, long eventTime, int action, int actionPointerId,
                int[] pointerIds, float[] xs, float[] ys) {
            if (downTime < 0 || eventTime < downTime || action < 0 || action > 12
                    || pointerIds == null || xs == null || ys == null || pointerIds.length < 1
                    || pointerIds.length > MAX_POINTERS || pointerIds.length != xs.length
                    || pointerIds.length != ys.length || actionPointerId < -1)
                throw new IllegalArgumentException("INVALID_NATIVE_EVENT_STAMP");
            int count = pointerIds.length;
            ids = pointerIds.clone(); xBits = new int[count]; yBits = new int[count];
            boolean pointerAction = action == LockGestureRecognizer.DOWN || action == LockGestureRecognizer.UP
                    || action == LockGestureRecognizer.POINTER_DOWN || action == LockGestureRecognizer.POINTER_UP;
            // MOVE/CANCEL 没有 action pointer；不能把数组第 0 项误作其稳定身份。
            int stableActionId = pointerAction ? actionPointerId : -1;
            boolean actionFound = !pointerAction;
            for (int i = 0; i < count; i++) {
                if (ids[i] < 0 || !Float.isFinite(xs[i]) || !Float.isFinite(ys[i]))
                    throw new IllegalArgumentException("INVALID_NATIVE_POINTER");
                if (ids[i] == stableActionId) actionFound = true;
                xBits[i] = Float.floatToIntBits(xs[i]); yBits[i] = Float.floatToIntBits(ys[i]);
            }
            if (!actionFound) throw new IllegalArgumentException("MISSING_NATIVE_ACTION_POINTER");
            for (int i = 1; i < count; i++) {
                int id = ids[i], x = xBits[i], y = yBits[i], at = i;
                while (at > 0 && ids[at - 1] > id) {
                    ids[at] = ids[at - 1]; xBits[at] = xBits[at - 1]; yBits[at] = yBits[at - 1]; at--;
                }
                ids[at] = id; xBits[at] = x; yBits[at] = y;
            }
            for (int i = 1; i < count; i++)
                if (ids[i] == ids[i - 1]) throw new IllegalArgumentException("DUPLICATE_NATIVE_POINTER");
            this.downTime = downTime; this.eventTime = eventTime;
            this.action = action; this.actionPointerId = stableActionId;
        }

        private boolean same(EventStamp other) {
            return other != null && downTime == other.downTime && eventTime == other.eventTime
                    && action == other.action && actionPointerId == other.actionPointerId
                    && Arrays.equals(ids, other.ids) && Arrays.equals(xBits, other.xBits)
                    && Arrays.equals(yBits, other.yBits);
        }
    }

    /** 仅可在对应入口的 try/finally 中使用；duplicate 不得再次交给识别器。 */
    public static final class Frame {
        public final boolean duplicate, admitted, rejected;
        public final long generation;
        private final NativeGestureOwnership owner;
        private final Binding binding;
        private final EventStamp stamp;
        private final Thread thread;
        private final boolean authorized;
        private volatile boolean active;
        private boolean gestureClaim;

        private Frame(NativeGestureOwnership owner, Binding binding, EventStamp stamp, long generation,
                boolean duplicate, boolean admitted, boolean authorized, boolean rejected) {
            this.owner = owner; this.binding = binding; this.generation = generation;
            this.stamp = stamp;
            this.duplicate = duplicate; this.admitted = admitted; this.authorized = authorized;
            this.rejected = rejected; this.thread = Thread.currentThread(); this.active = !rejected;
        }

        public boolean isActive() { return active; }
        public boolean shouldRecognize() { return active && admitted && !duplicate; }
    }

    private static final class SeenEvent {
        final EventStamp stamp;
        final boolean admitted, authorized;
        boolean claimed;
        SeenEvent(EventStamp stamp, boolean admitted, boolean authorized) {
            this.stamp = stamp; this.admitted = admitted; this.authorized = authorized;
        }
    }

    private final ArrayDeque<Frame> frames = new ArrayDeque<>();
    private final ArrayDeque<SeenEvent> recentEvents = new ArrayDeque<>();
    private Binding binding, sequenceBinding;
    private long generation, sequenceGeneration = INVALID_GENERATION;
    private boolean exhausted;

    /** 返回是否发生绑定变更；相同字段的新 Binding 对象不会使现有手势失效。 */
    public synchronized boolean bind(Binding next) {
        if (next == null) throw new IllegalArgumentException("MISSING_NATIVE_BINDING");
        if (!exhausted && next.matches(binding)) return false;
        clearAll(); advanceGeneration();
        binding = exhausted ? null : next;
        return true;
    }

    public synchronized long currentGeneration() { return exhausted ? INVALID_GENERATION : generation; }
    public synchronized boolean bindingMatches(Binding candidate) {
        return !exhausted && binding != null && binding.matches(candidate);
    }
    public synchronized boolean bindingValid(Binding candidate, long capturedGeneration) {
        return capturedGeneration > 0 && generation == capturedGeneration && bindingMatches(candidate);
    }

    /** 三参入口不隐含原生息屏接管授权。 */
    public synchronized Frame beginFrame(Binding candidate, EventStamp stamp, boolean eligible) {
        return beginFrame(candidate, stamp, eligible, false);
    }

    public synchronized Frame beginFrame(Binding candidate, EventStamp stamp, boolean eligible,
            boolean arbitrationAuthorized) {
        if (!bindingMatches(candidate) || stamp == null || frames.size() >= MAX_DEPTH
                || !frames.isEmpty() && frames.peekLast().thread != Thread.currentThread()) {
            revoke();
            return new Frame(this, candidate, null, generation, false, false, false, true);
        }
        boolean duplicate = false, admitted = eligible, authorized = arbitrationAuthorized, claimed = false;
        // 即使活动父回调期间经过超过缓存上限的子调用，也不能把同一父事件再次识别。
        for (Frame active : frames) if (active.stamp.same(stamp)) {
            duplicate = true; admitted &= active.admitted; authorized &= active.authorized;
            claimed |= active.gestureClaim; break;
        }
        for (SeenEvent recent : recentEvents) if (recent.stamp.same(stamp)) {
            duplicate = true; admitted &= recent.admitted; authorized &= recent.authorized;
            claimed |= recent.claimed; break;
        }
        if (!duplicate) {
            recentEvents.addLast(new SeenEvent(stamp, admitted, authorized));
            if (recentEvents.size() > MAX_RECENT_EVENTS) recentEvents.removeFirst();
        }
        // 重复的自然事件可保留本次同步仲裁，但不能重复识别和发送壁纸命令。
        // 首次未准入或未授权的事件不能借重复回调升级成只有息屏接管、没有壁纸识别的动作。
        Frame frame = new Frame(this, binding, stamp, generation, duplicate, admitted, authorized, false);
        frame.gestureClaim = claimed && admitted && authorized;
        frames.addLast(frame);
        return frame;
    }

    /** 识别器已确认由模块接管的双击候选／完整手势后才调用；单次区域准入不是手势认领。 */
    public synchronized boolean claimGesture(Frame frame) {
        if (frame == null || frame.owner != this || frames.peekLast() != frame || !frame.active
                || frame.thread != Thread.currentThread() || !frame.admitted || !frame.authorized
                || frame.generation != generation || !bindingMatches(frame.binding)
                || frame.duplicate && !frame.gestureClaim) return false;
        frame.gestureClaim = true;
        for (SeenEvent seen : recentEvents) if (seen.stamp.same(frame.stamp)) { seen.claimed = true; break; }
        return true;
    }

    /** 非 LIFO、跨线程或外来 Frame 一律撤销所有权，不向宿主抛异常。 */
    public synchronized boolean endFrame(Frame frame) {
        if (frame != null && frame.owner == this && frame.rejected) return false;
        if (frame == null || frame.owner != this || !frame.active
                || frame.thread != Thread.currentThread() || frames.peekLast() != frame) {
            revoke(); return false;
        }
        frames.removeLast(); frame.active = false; frame.gestureClaim = false;
        return true;
    }

    public synchronized long beginSequence(Binding candidate) {
        if (!bindingMatches(candidate)) { clearSequence(); return INVALID_GENERATION; }
        sequenceBinding = binding; sequenceGeneration = generation;
        return sequenceGeneration;
    }
    public synchronized boolean sequenceValid(Binding candidate, long capturedGeneration) {
        return bindingValid(candidate, capturedGeneration) && sequenceGeneration == capturedGeneration
                && sequenceBinding != null && sequenceBinding.matches(candidate);
    }
    /** send 后清识别票据不得清掉仍在原方法栈内的本次仲裁 Frame。 */
    public synchronized void clearSequence() {
        sequenceBinding = null; sequenceGeneration = INVALID_GENERATION;
    }

    /** 外层必须再次核实时系统状态与区域；此结果本身不是认证或区域凭证。 */
    public synchronized boolean maySuppress(Object listener, Object helper, Binding current) {
        Frame frame = frames.peekLast();
        return frame != null && frame.active && frame.thread == Thread.currentThread()
                && frame.admitted && frame.authorized && frame.gestureClaim && frame.generation == generation
                && bindingMatches(current) && frame.binding.matches(current)
                && current.listener == listener && current.helper == helper;
    }

    public synchronized void revoke() {
        clearAll(); binding = null; advanceGeneration();
    }
    private void clearAll() {
        for (Frame frame : frames) { frame.active = false; frame.gestureClaim = false; }
        frames.clear(); recentEvents.clear(); clearSequence();
    }
    private void advanceGeneration() {
        if (exhausted) return;
        if (generation == Long.MAX_VALUE) { exhausted = true; generation = INVALID_GENERATION; }
        else generation++;
    }
}
