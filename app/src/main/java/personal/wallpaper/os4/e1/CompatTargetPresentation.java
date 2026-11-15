package personal.wallpaper.os4.e1;

/** 每个目标只接收自己的状态，不由另一目标的开关、连接或异常推断生效。 */
final class CompatTargetPresentation {
    final String text;
    final boolean positive;
    private CompatTargetPresentation(String text, boolean positive) { this.text = text; this.positive = positive; }
    static CompatTargetPresentation desktop(DashboardState actual, CompatPreferenceStore.State saved, boolean pending) {
        if (!saved.available) return new CompatTargetPresentation("设置未确认", false);
        if (pending) return new CompatTargetPresentation(saved.enabled ? "正在确认开启" : "正在确认关闭", false);
        if (actual == null) return new CompatTargetPresentation(saved.enabled ? "已保存开启 · 状态待确认" : "已保存关闭 · 状态待确认", false);
        switch (actual.kind) {
            case ACTIVE: return new CompatTargetPresentation(saved.enabled ? "当前已生效" : "关闭待确认 · 当前仍生效", saved.enabled);
            case READY: return new CompatTargetPresentation(saved.enabled ? "已保存开启 · 等待应用" : "已关闭", !saved.enabled);
            case WAITING_TARGET: return new CompatTargetPresentation("等待桌面壁纸", false);
            case RECOVERING: case LOCKED: return new CompatTargetPresentation("等待桌面恢复 · 可修改开关", false);
            case HOOK_FAILURE: return new CompatTargetPresentation("桌面适配暂不可用", false);
            default: return new CompatTargetPresentation("桌面状态待确认", false);
        }
    }
    static CompatTargetPresentation lock(boolean desired, String raw) {
        if ("SAVE_UNCONFIRMED".equals(raw)) return new CompatTargetPresentation("锁屏设置未保存，请重试", false);
        if (!desired) return new CompatTargetPresentation("已关闭", true);
        if (raw == null || raw.equals("NOT_CONNECTED") || raw.contains("UNAVAILABLE"))
            return new CompatTargetPresentation("已保存开启 · 等待锁屏连接", false);
        if (raw.contains("SETTING_UNCONFIRMED")) return new CompatTargetPresentation("锁屏选项未保存，请重试", false);
        if (raw.startsWith("主LOCK就绪") || raw.startsWith("主锁屏就绪")) {
            // 宿主就绪、接通或ARMED不能单独当作手势已经验证。
            return new CompatTargetPresentation(raw.contains("输入连接尚未开启") ? "壁纸就绪 · 等待输入连接" : "锁屏壁纸就绪 · 输入状态待验证", false);
        }
        if (raw.contains("PAUSED") || raw.contains("BUSY")) return new CompatTargetPresentation("锁屏兼容暂时等待", false);
        return new CompatTargetPresentation("已保存开启 · 等待锁屏就绪", false);
    }
}
