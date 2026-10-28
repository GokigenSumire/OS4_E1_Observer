package personal.wallpaper.os4.e1.core;

/** 防止开机的旧开启请求晚到、覆盖用户更新的关闭意愿；不是系统文件持久化。 */
public final class PersistentCommandGuard {
    private long revision = -1;
    private boolean stoppedAtRevision;

    public synchronized boolean acceptOn(long requested) {
        if (requested < 0 || requested < revision || requested == revision && stoppedAtRevision) return false;
        revision = requested;
        stoppedAtRevision = false;
        return true;
    }

    public synchronized boolean acceptOff(long requested) {
        if (requested < 0 || requested < revision) return false;
        revision = requested;
        stoppedAtRevision = true;
        return true;
    }

    public synchronized void emergencyStop() { stoppedAtRevision = true; }
    public synchronized long revision() { return revision; }
}
