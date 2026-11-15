package personal.wallpaper.os4.e1;

/** 主线程所有者：保存桌面意愿与系统准入分开，后来的操作使旧异步回执失效。 */
final class DesktopToggleController {
    interface Store {
        CompatPreferenceStore.State read();
        boolean save(boolean enabled);
    }
    interface Callback { void accept(Response response); }
    interface Backend {
        void status(Callback callback);
        void enable(CompatPreferenceStore.State saved, Ticket ticket, Callback callback);
        void stop(long revision, Callback callback);
    }
    static final class Ticket {
        final long epoch; final String boot;
        Ticket(long epoch, String boot) { this.epoch = epoch; this.boot = boot; }
        boolean valid() { return epoch >= 0 && boot != null && !boot.isEmpty(); }
    }
    static final class Response {
        final DashboardState actual; final Ticket ticket; final String issue;
        Response(DashboardState actual, Ticket ticket, String issue) {
            this.actual = actual; this.ticket = ticket; this.issue = issue;
        }
    }
    private final Store store;
    private final Backend backend;
    private final Runnable changed;
    private long generation;
    private boolean closed;
    private Boolean lastChoice;
    boolean pending;
    boolean savingFailed;
    String message = "";
    DashboardState actual;

    DesktopToggleController(Store store, Backend backend, Runnable changed) {
        this.store = store; this.backend = backend; this.changed = changed;
    }
    CompatPreferenceStore.State saved() {
        try { return store.read(); }
        catch (RuntimeException unavailable) { return new CompatPreferenceStore.State(false, false, -1, true); }
    }
    void change(boolean enabled) {
        if (closed) return;
        lastChoice = enabled;
        long request = ++generation;
        boolean committed;
        try { committed = store.save(enabled); }
        catch (RuntimeException unavailable) { committed = false; }
        CompatPreferenceStore.State saved = saved();
        savingFailed = !committed || !saved.available || saved.enabled != enabled;
        if (enabled && savingFailed) {
            pending = false; message = "开启设置未保存，请重试"; changed.run(); return;
        }
        if (enabled) beginEnable(request, saved);
        else beginStop(request, committed && saved.available ? saved.revision : -1);
    }
    void refresh() {
        if (closed || pending) return;
        long request = ++generation;
        pending = true; message = "正在确认桌面状态"; changed.run();
        try { backend.status(response -> finish(request, response)); }
        catch (RuntimeException unavailable) { finish(request, null); }
    }
    void retry() {
        if (closed) return;
        if (pending) { message = "正在处理，仍可修改桌面开关"; changed.run(); return; }
        CompatPreferenceStore.State saved = saved();
        if (savingFailed || !saved.available) {
            if (lastChoice != null) change(lastChoice);
            else { message = "设置未确认，请重新选择开关"; changed.run(); }
            return;
        }
        long request = ++generation;
        if (saved.enabled) beginEnable(request, saved);
        else beginStop(request, saved.revision);
    }
    private void beginEnable(long request, CompatPreferenceStore.State saved) {
        pending = true; message = "开启已保存，正在确认状态"; changed.run();
        try {
            backend.status(response -> {
                if (!current(request)) return;
                CompatPreferenceStore.State latest = saved();
                if (!latest.available || !latest.enabled || latest.revision != saved.revision) {
                    pending = false; message = "设置已变化，请重新确认"; changed.run(); return;
                }
                if (response == null || response.issue != null || response.actual == null) {
                    finish(request, response); return;
                }
                actual = response.actual;
                if (actual.kind == DashboardState.Kind.ACTIVE || actual.kind == DashboardState.Kind.WAITING_TARGET) {
                    finish(request, response); return;
                }
                if (!actual.canEnable || response.ticket == null || !response.ticket.valid()) {
                    pending = false; message = "开启已保存，当前尚未生效"; changed.run(); return;
                }
                message = "正在开启桌面兼容"; changed.run();
                try { backend.enable(saved, response.ticket, result -> finish(request, result)); }
                catch (RuntimeException unavailable) { finish(request, null); }
            });
        } catch (RuntimeException unavailable) { finish(request, null); }
    }
    private void beginStop(long request, long revision) {
        pending = true; message = savingFailed ? "关闭未保存，仍在请求停止" : "正在关闭桌面兼容"; changed.run();
        try { backend.stop(revision, response -> finish(request, response)); }
        catch (RuntimeException unavailable) { finish(request, null); }
    }
    private boolean current(long request) { return !closed && request == generation; }
    private void finish(long request, Response response) {
        if (!current(request)) return;
        pending = false;
        actual = response == null ? null : response.actual;
        String issue = response == null ? "NO_CONTROL_RESPONSE" : response.issue;
        if (savingFailed) message = "设置未保存，请重新选择开关";
        else if ("MODULE_VERSION_MISMATCH".equals(issue)) message = "系统进程仍是旧模块，请正常重载";
        else if (issue != null || actual == null) message = "未确认当前状态，可刷新或修改开关";
        else message = "";
        changed.run();
    }
    void close() { closed = true; generation++; }
}
