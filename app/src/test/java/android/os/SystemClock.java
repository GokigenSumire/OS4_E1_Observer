package android.os;

/** 仅 JVM 适配夹具使用；不属于 main 源集，不进入 APK。 */
public final class SystemClock {
    private static volatile long now;
    private static int successfulReadsBeforeFailure = -1;
    private SystemClock() { }
    public static synchronized long elapsedRealtime() {
        if (successfulReadsBeforeFailure == 0) {
            successfulReadsBeforeFailure = -1;
            throw new IllegalStateException("INJECTED_CLOCK_FAILURE");
        }
        if (successfulReadsBeforeFailure > 0) successfulReadsBeforeFailure--;
        return now;
    }
    public static synchronized void setElapsedRealtime(long value) {
        now = value;
        successfulReadsBeforeFailure = -1;
    }
    public static synchronized void failOnceAfterReads(int successfulReads) {
        successfulReadsBeforeFailure = successfulReads;
    }
}
