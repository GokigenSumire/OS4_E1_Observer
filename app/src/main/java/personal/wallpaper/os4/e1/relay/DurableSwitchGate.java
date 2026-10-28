package personal.wallpaper.os4.e1.relay;

/** commit失败可能已更新SharedPreferences内存；磁盘未确认的值不能成为运行授权。 */
final class DurableSwitchGate {
    private boolean confirmed = true;
    synchronized void beginWrite() { confirmed = false; }
    synchronized void finishedWrite(boolean success) { confirmed = success; }
    synchronized boolean allows(boolean storedEnabled) { return confirmed && storedEnabled; }
    synchronized boolean confirmed() { return confirmed; }
}
