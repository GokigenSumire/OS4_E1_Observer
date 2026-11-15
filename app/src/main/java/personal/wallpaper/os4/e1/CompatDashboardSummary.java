package personal.wallpaper.os4.e1;

/** Display only: saved intent selects visible domains; never grants or infers input readiness. */
final class CompatDashboardSummary {
    final String title, body;
    private CompatDashboardSummary(String title, String body) { this.title = title; this.body = body; }
    static CompatDashboardSummary from(boolean homeAvailable, boolean homeEnabled, boolean lockEnabled,
                                       String homeTitle, String homeBody, String lockStatus) {
        String lock = lockStatus == null || lockStatus.isBlank() ? "尚未读取" : lockStatus;
        if (!homeAvailable) return new CompatDashboardSummary("桌面设置待确认" + (lockEnabled ? "／锁屏兼容" : ""),
                "桌面：设置暂不可读，当前状态未确认。" + (lockEnabled ? "\n锁屏：" + lock : ""));
        if (homeEnabled && lockEnabled) return new CompatDashboardSummary("桌面／锁屏兼容", "桌面：" + homeTitle + "\n锁屏：" + lock);
        if (lockEnabled) return new CompatDashboardSummary("锁屏兼容", "锁屏：" + lock);
        if (homeEnabled) return new CompatDashboardSummary("桌面兼容", "桌面：" + homeTitle);
        return new CompatDashboardSummary("兼容开关已关闭", "桌面：" + homeTitle + "\n" + homeBody + "\n锁屏：" + lock);
    }
}
