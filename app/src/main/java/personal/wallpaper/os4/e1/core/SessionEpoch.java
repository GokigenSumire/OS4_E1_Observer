package personal.wallpaper.os4.e1.core;

/**
 * 单实例单调会话票据。0 表示尚未开始，永不接受；旧票据不能授权新会话。
 * 该类不代表会话正在采集；调用方仍须在同一临界区核对活跃状态和执行操作。
 */
public final class SessionEpoch {
    private long epoch;

    public SessionEpoch() {
        this(0);
    }

    /** 仅用于覆盖接近上限的测试，不允许负数或回绕。 */
    SessionEpoch(long initialEpoch) {
        if (initialEpoch < 0) {
            throw new IllegalArgumentException("INVALID_EPOCH");
        }
        epoch = initialEpoch;
    }

    public synchronized long advance() {
        if (epoch == Long.MAX_VALUE) {
            throw new IllegalStateException("EPOCH_EXHAUSTED");
        }
        return ++epoch;
    }

    public synchronized long current() {
        return epoch;
    }

    public synchronized boolean accepts(long ticket) {
        return ticket > 0 && ticket == epoch;
    }
}
