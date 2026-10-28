package personal.wallpaper.os4.e1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import personal.wallpaper.os4.e1.core.CompatStartupPolicy;

/** 仅用户先前已保存开启时恢复。普通应用进程读取自有偏好，系统进程保持只在内存工作。 */
public final class CompatStartupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !(Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))) return;
        CompatPreferenceStore.State saved = CompatPreferenceStore.read(context);
        if (!saved.available || !saved.enabled) return;
        PendingResult pending = goAsync();
        try { new Restore(context.getApplicationContext(), pending, saved.revision).start(); }
        catch (RuntimeException ignored) { pending.finish(); }
    }

    private static final class Restore {
        final Context context;
        final PendingResult pending;
        final long revision;
        final Handler handler = new Handler(Looper.getMainLooper());
        boolean finished;
        final Runnable timeout = this::finish;
        Restore(Context context, PendingResult pending, long revision) {
            this.context = context; this.pending = pending; this.revision = revision;
        }
        void start() {
            handler.postDelayed(timeout, 8000);
            send(new Intent(ControlServer.ACTION).setPackage("android").putExtra("cmd", "STATUS"), true);
        }
        void send(Intent request, boolean query) {
            try {
                context.sendOrderedBroadcast(request, null, new BroadcastReceiver() {
                    @Override public void onReceive(Context ignored, Intent resultIntent) {
                        if (finished) return;
                        if (!query) { finish(); return; }
                        Bundle result = getResultExtras(false);
                        if (result == null) { finish(); return; }
                        CompatStartupPolicy.Decision decision = CompatStartupPolicy.evaluate(result.getString("module"),
                                BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, result.getString("contrast"),
                                result.getString("hooks"), result.getString("bootstrap"), result.getString("error"),
                                result.getLong("epoch", -1), result.getString("bootSession"));
                        CompatPreferenceStore.State current = CompatPreferenceStore.read(context);
                        if (decision != CompatStartupPolicy.Decision.START || !current.available
                                || !current.enabled || current.revision != revision) { finish(); return; }
                        send(new Intent(ControlServer.ACTION).setPackage("android").putExtra("cmd", "DAILY_ON")
                                .putExtra("bootSession", result.getString("bootSession"))
                                .putExtra("epoch", result.getLong("epoch", -1))
                                .putExtra("desiredRevision", revision), false);
                    }
                }, handler, 0, null, null);
            } catch (RuntimeException ignored) { finish(); }
        }
        void finish() {
            if (finished) return;
            finished = true;
            handler.removeCallbacks(timeout);
            pending.finish();
        }
    }
}
