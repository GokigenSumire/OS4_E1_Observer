package personal.wallpaper.os4.e1.core;

/** 每个关闭请求都撤销已发出的开启票据，即使关闭时无法读取持久revision。 */
public final class PersistentControlEpoch {
    private long epoch;
    private boolean exhausted;
    public PersistentControlEpoch() { this(0); }
    PersistentControlEpoch(long initial) {
        if (initial < 0) throw new IllegalArgumentException("INVALID_EPOCH");
        epoch=initial;
    }
    public synchronized long current() { return epoch; }
    public synchronized boolean acceptsOn(long expected) { return !exhausted && expected>=0 && expected==epoch && epoch<Long.MAX_VALUE; }
    public synchronized long advance() {
        if (exhausted || epoch==Long.MAX_VALUE) { exhausted=true; return -1; }
        return ++epoch;
    }
    public synchronized long revoke() {
        advance();
        return epoch;
    }
}
