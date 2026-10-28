package personal.wallpaper.os4.e1;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import personal.wallpaper.os4.e1.relay.LockRelayService;

/** 受控独立诊断页，不是壁纸/日常开关的第二个控制入口。 */
public class LockDebugActivity extends Activity {
    private static final int SAVE_DOCUMENT = 7201;
    private static final int MAX_EXPORT_BYTES = 192 * 1024;
    private static final long REQUEST_TIMEOUT_MS = 10_000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), r -> {
                Thread thread = new Thread(r, "LOCK-diagnostic-export"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private TextView state, log;
    private Button start, stop, refresh, export;
    private boolean bound, destroyed, busy, choosingDocument, writing, collecting;
    private long requestEpoch;
    private LockRelayService.DiagRequest request;
    private LockRelayService.DiagSnapshot snapshot;
    private String pendingExport;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (!destroyed) issue("STATUS");
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            invalidateRequest();
            if (!destroyed) { state.setText("诊断通道已断开，未改变永久开关。可返回后重开本页。"); renderButtons(); }
        }
    };
    private static final class Retained {
        final LockRelayService.DiagSnapshot snapshot;
        final String pending;
        final boolean collecting;
        Retained(LockRelayService.DiagSnapshot value, String pending, boolean collecting) {
            snapshot = value; this.pending = pending; this.collecting = collecting;
        }
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_HomeCompat);
        super.onCreate(savedInstanceState);
        windowSecurity();
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof Retained) {
            snapshot = ((Retained) retained).snapshot;
            pendingExport = ((Retained) retained).pending;
            collecting = ((Retained) retained).collecting;
            choosingDocument = pendingExport != null;
        }
        buildPage();
        if (snapshot != null) renderSnapshot(snapshot);
        renderButtons();
        try { bound = bindDiagnosticService(connection); }
        catch (RuntimeException ignored) { bound = false; }
        if (!bound) state.setText("诊断服务暂不可用；未开启兼容或改变任何图库配置。");
    }
    private void windowSecurity() {
        windowFlags();
        setShowWhenLocked(false);
    }
    private void windowFlags() { getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); }
    protected boolean bindDiagnosticService(ServiceConnection target) {
        return bindService(new Intent(this, LockRelayService.class), target, Context.BIND_AUTO_CREATE);
    }
    protected void unbindDiagnosticService(ServiceConnection target) { unbindService(target); }
    protected LockRelayService.DiagRequest dispatchDiagnostic(String operation, LockRelayService.DiagCallback callback) {
        return LockRelayService.requestDiag(this, operation, callback);
    }

    private void buildPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(16), dp(20), dp(24));
        column.setBackgroundColor(getColor(R.color.dashboard_background));
        scroll.addView(column);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        label(column, "调试与日志", 24);
        label(column, "独立LOCK诊断，最多200秒。请先停止再开始新一轮；开始只清已停止的旧诊断记录，不改图库、顺序或HOME／LOCK永久开关。", 14);
        label(column, "开始后锁屏立即尝试动作，再正常解锁返回、停止并读取。无需等待11秒，不延长锁屏亮屏时间。", 14);
        label(column, "布局拒绝会细分固定原因及层类别；诊断记录不等于手势次数，也不代表安全条件已放行。", 14);
        state = label(column, "尚未读取诊断状态。", 14); state.setTag("diag_state");
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        start = button(column, "开始新诊断", "diag_start", () -> issue("START"));
        stop = button(column, "停止并读取日志", "diag_stop", () -> issue("STOP"));
        refresh = button(column, "刷新状态／读取已停止日志", "diag_refresh", () -> issue("SNAPSHOT"));
        export = button(column, "导出已停止日志", "diag_export", this::chooseExport);
        log = label(column, "尚无完整日志；空白不代表手势通过。", 12); log.setTag("diag_log");
        log.setTextIsSelectable(true);
        setContentView(scroll);
    }
    private TextView label(LinearLayout parent, String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size);
        view.setTextColor(getColor(R.color.dashboard_text)); view.setPadding(0, dp(7), 0, dp(7));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private Button button(LinearLayout parent, String text, String tag, Runnable click) {
        Button button = new Button(this); button.setText(text); button.setTag(tag);
        button.setAllCaps(false); button.setMinHeight(dp(52)); button.setOnClickListener(v -> click.run());
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2)); return button;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void issue(String operation) {
        if (destroyed || busy || choosingDocument || writing) return;
        if ("START".equals(operation) && collecting) return;
        final long epoch = ++requestEpoch;
        busy = true; state.setText("正在处理诊断请求；不会切换日常开关。"); renderButtons();
        main.postDelayed(() -> {
            if (destroyed || requestEpoch != epoch || !busy) return;
            invalidateRequest();
            state.setText("诊断请求尚未返回，实际状态未确认。未强制中断系统请求，可稍后刷新。");
            renderButtons();
        }, REQUEST_TIMEOUT_MS);
        try {
            request = dispatchDiagnostic(operation, result -> {
                if (destroyed || requestEpoch != epoch || !busy) return;
                busy = false; request = null;
                if (result == null) { state.setText("诊断未返回有效结果；先前完整快照仍保留。"); renderButtons(); return; }
                if (result.success || "ALREADY_RUNNING".equals(result.detail)) collecting = result.active;
                if (result.success && result.complete && !result.active) snapshot = result;
                else if (result.success && "START".equals(result.operation)) snapshot = null;
                renderSnapshot(result); renderButtons();
            });
        } catch (RuntimeException ignored) {
            if (requestEpoch != epoch || destroyed) return;
            busy = false; request = null; state.setText("诊断请求失败，先前完整快照未丢弃。"); renderButtons();
        }
    }
    private void renderSnapshot(LockRelayService.DiagSnapshot value) {
        state.setText((value.success ? "诊断状态：" : "诊断请求未完成：") + value.state
                + "\n原因：" + value.detail + "\n记录 " + value.totalRows + " 行，" + value.bytes + " 字节。"
                + (value.active ? "\n采集仍在进行，可现在锁屏操作；返回后停止并读取。" : ""));
        if (!value.success) return;
        if (value.complete && !value.active) {
            int first = Math.max(0, value.lines.size() - 60);
            StringBuilder tail = new StringBuilder("以下显示末60行；导出包含本轮全部记录。\n");
            for (int i = first; i < value.lines.size(); i++) tail.append(value.lines.get(i)).append('\n');
            log.setText(tail.toString());
        } else if ("START".equals(value.operation)) {
            log.setText("新诊断已开始。首次动作即时记录，不依赖logcat或10秒摘要。");
        }
    }
    private void renderButtons() {
        boolean idle = !destroyed && !busy && !choosingDocument && !writing;
        start.setEnabled(idle && !collecting); stop.setEnabled(idle); refresh.setEnabled(idle);
        export.setEnabled(idle && !collecting && exportable(snapshot));
    }
    private static boolean exportable(LockRelayService.DiagSnapshot value) {
        return value != null && value.success && value.complete && !value.active
                && value.totalRows > 0 && value.bytes > 0 && value.lines.size() == value.totalRows;
    }
    private void chooseExport() {
        if (!exportable(snapshot) || collecting || destroyed) return;
        String text = snapshot.toText();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_EXPORT_BYTES) {
            state.setText("日志超过导出上限，本次未写文件。"); return;
        }
        pendingExport = text; choosingDocument = true; renderButtons();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain").putExtra(Intent.EXTRA_TITLE, "HWC-LOCK-" + BuildConfig.VERSION_CODE
                        + "-" + System.currentTimeMillis() + ".txt");
        try { openExportDocument(intent, SAVE_DOCUMENT); }
        catch (RuntimeException ignored) {
            pendingExport = null; choosingDocument = false; state.setText("系统保存页面未打开；完整快照仍保留，可重试。"); renderButtons();
        }
    }
    protected void openExportDocument(Intent intent, int code) { startActivityForResult(intent, code); }
    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != SAVE_DOCUMENT || destroyed) return;
        choosingDocument = false;
        String text = pendingExport; pendingExport = null;
        if (result != RESULT_OK) { state.setText("已取消保存，未写入日志文件；完整快照仍保留。"); renderButtons(); return; }
        Uri destination = data == null ? null : data.getData();
        if (text == null || destination == null) {
            state.setText("导出上下文已失效，本次未写文件。请重新读取完整快照再导出。"); renderButtons(); return;
        }
        writing = true; renderButtons();
        WeakReference<LockDebugActivity> target = new WeakReference<>(this);
        writeExport(destination, text, success -> {
            LockDebugActivity live = target.get();
            if (live == null || live.destroyed) return;
            live.writing = false;
            live.state.setText(success ? "日志已保存到你选择的位置，可将该txt复制到电脑回传。"
                    : "文件写入失败；目标文件可能为空或不完整，完整快照仍保留，可重试。");
            live.renderButtons();
        });
    }
    protected interface ExportCompletion { void complete(boolean success); }
    protected void writeExport(Uri destination, String text, ExportCompletion completion) {
        ContentResolver resolver = getApplicationContext().getContentResolver();
        WeakReference<LockDebugActivity> owner = new WeakReference<>(this);
        // 此Runnable不捕获Activity；任意阻塞DocumentProvider仍不能承诺强制中止。
        try { writer.execute(() -> {
            boolean success = false;
            try (OutputStream stream = resolver.openOutputStream(destination, "w")) {
                if (stream != null) { stream.write(text.getBytes(StandardCharsets.UTF_8)); stream.flush(); success = true; }
            } catch (Exception ignored) { }
            boolean saved = success;
            new Handler(Looper.getMainLooper()).post(() -> {
                LockDebugActivity live = owner.get();
                if (live != null && !live.destroyed) completion.complete(saved);
            });
        }); } catch (RuntimeException ignored) { completion.complete(false); }
    }
    @Override public Object onRetainNonConfigurationInstance() { return new Retained(snapshot, pendingExport, collecting); }
    private void invalidateRequest() {
        requestEpoch++; busy = false;
        if (request != null) request.cancel();
        request = null;
    }
    @Override protected void onDestroy() {
        destroyed = true; invalidateRequest(); main.removeCallbacksAndMessages(null);
        if (bound) try { unbindDiagnosticService(connection); } catch (RuntimeException ignored) { }
        super.onDestroy();
    }
}
