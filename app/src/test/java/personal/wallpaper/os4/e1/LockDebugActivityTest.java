package personal.wallpaper.os4.e1;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowLooper;
import personal.wallpaper.os4.e1.relay.LockRelayService;
import static org.junit.Assert.*;

/** 真Activity/SAF交互适配；不运行手机/SystemUI/Binder/DocumentProvider。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@LooperMode(LooperMode.Mode.PAUSED)
public final class LockDebugActivityTest {
    private ActivityController<TestActivity> controller;
    private TestActivity activity;
    public static final class TestActivity extends LockDebugActivity {
        final ArrayList<String> operations = new ArrayList<>();
        final ArrayList<LockRelayService.DiagCallback> replies = new ArrayList<>();
        int cancelled, writes, pickerCode;
        Intent picker;
        String written;
        boolean writerSuccess = true;
        @Override protected boolean bindDiagnosticService(ServiceConnection target) { return false; }
        @Override protected void unbindDiagnosticService(ServiceConnection target) { }
        @Override protected LockRelayService.DiagRequest dispatchDiagnostic(String operation, LockRelayService.DiagCallback callback) {
            operations.add(operation); replies.add(callback); return () -> cancelled++;
        }
        @Override protected void openExportDocument(Intent intent, int code) { picker = intent; pickerCode = code; }
        @Override protected void writeExport(Uri destination, String text, ExportCompletion result) {
            writes++; written = text; result.complete(writerSuccess);
        }
        void reply(LockRelayService.DiagSnapshot value) { replies.get(replies.size() - 1).onResult(value); }
        void chooser(int result, Intent value) { onActivityResult(pickerCode, result, value); }
    }
    @Before public void create() {
        controller = Robolectric.buildActivity(TestActivity.class).create().start().resume(); activity = controller.get();
    }
    @After public void cleanup() {
        if (controller != null) { controller.pause().stop().destroy(); controller = null; }
    }
    private Button button(String key) { return activity.getWindow().getDecorView().findViewWithTag("diag_" + key); }
    private String state() { return ((TextView) activity.getWindow().getDecorView().findViewWithTag("diag_state")).getText().toString(); }
    private static LockRelayService.DiagSnapshot stopped(String cause) {
        String line = "row=1 stage=PROPERTY cause=" + cause + " total=1";
        return LockRelayService.DiagSnapshot.create(true, "STOP", "FROZEN_SNAPSHOT", false, true, 2, 1,
                line.length() + 1, List.of(line));
    }
    private static LockRelayService.DiagSnapshot running() {
        return LockRelayService.DiagSnapshot.create(true, "START", "RUNNING", true, false, 1, 0, 0, List.of());
    }
    private void ready() { button("stop").performClick(); activity.reply(stopped("UNKNOWN_FLOW_AOD")); }

    @Test public void openingPageDoesNotStartOrChangeCompatibility() {
        assertTrue(activity.operations.isEmpty()); assertFalse(button("export").isEnabled());
        assertEquals(0, activity.writes);
        assertEquals(0, activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Test public void diagnosticActivityIsNotExportedAndDoesNotShowPrivateScreenOnLock() throws Exception {
        ActivityInfo info = activity.getPackageManager().getActivityInfo(new ComponentName(activity, LockDebugActivity.class), 0);
        assertFalse(info.exported);
        assertNotEquals(0, activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE);
        assertEquals(0, activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }
    @Test public void explicitStartIsImmediateAndRequiresNoTenSecondWait() {
        button("start").performClick(); assertEquals(List.of("START"), activity.operations);
        activity.reply(running()); assertTrue(state().contains("RUNNING")); assertFalse(button("export").isEnabled());
    }
    @Test public void stopReadsImmutableCompleteSnapshotBeforeEnablingExport() {
        ready(); assertTrue(button("export").isEnabled());
        assertTrue(((TextView) activity.getWindow().getDecorView().findViewWithTag("diag_log")).getText().toString().contains("UNKNOWN_FLOW_AOD"));
    }
    @Test public void emptyCompleteSnapshotCannotBeExportedAsUsefulEvidence() {
        button("stop").performClick();
        activity.reply(LockRelayService.DiagSnapshot.create(true, "STOP", "FROZEN_SNAPSHOT", false, true, 2, 0, 0, List.of()));
        assertFalse(button("export").isEnabled()); assertNull(activity.picker); assertEquals(0, activity.writes);
    }
    @Test public void failedRefreshDoesNotDiscardPriorCompleteSnapshot() {
        ready(); button("refresh").performClick();
        activity.reply(LockRelayService.DiagSnapshot.create(false, "SNAPSHOT", "CHANNEL_UNAVAILABLE", false, false, -1, 0, 0, List.of()));
        assertTrue(button("export").isEnabled()); assertTrue(state().contains("CHANNEL_UNAVAILABLE"));
    }
    @Test public void newSuccessfulStartRetiresOldExportWithoutWritingFiles() {
        ready(); button("start").performClick(); activity.reply(running());
        assertFalse(button("export").isEnabled()); assertEquals(0, activity.writes);
    }
    @Test public void createDocumentUsesExplicitUserSelectedPlainTextDestination() {
        ready(); button("export").performClick();
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, activity.picker.getAction());
        assertEquals("text/plain", activity.picker.getType());
        assertTrue(activity.picker.hasCategory(Intent.CATEGORY_OPENABLE));
        assertTrue(activity.picker.getStringExtra(Intent.EXTRA_TITLE).startsWith("HWC-LOCK-"));
        assertEquals(0, activity.writes);
    }
    @Test public void cancellingDocumentPickerDoesNotWriteAndKeepsSnapshot() {
        ready(); button("export").performClick(); activity.chooser(Activity.RESULT_CANCELED, null);
        assertEquals(0, activity.writes); assertTrue(button("export").isEnabled()); assertTrue(state().contains("取消"));
    }
    @Test public void successfulSaveWritesFrozenTextAndNotPrivatePaths() {
        ready(); button("export").performClick();
        activity.chooser(Activity.RESULT_OK, new Intent().setData(Uri.parse("content://chosen/document/1")));
        assertEquals(1, activity.writes); assertTrue(activity.written.contains("UNKNOWN_FLOW_AOD"));
        assertTrue(activity.written.contains("module=")); assertFalse(activity.written.contains("content://"));
        assertTrue(state().contains("已保存"));
    }
    @Test public void failedSaveKeepsOriginalSnapshotForRetry() {
        ready(); activity.writerSuccess = false; button("export").performClick();
        activity.chooser(Activity.RESULT_OK, new Intent().setData(Uri.parse("content://chosen/document/1")));
        assertTrue(state().contains("失败")); assertTrue(button("export").isEnabled());
    }
    @Test public void duplicateCallbackCannotReplaceAcceptedSnapshot() {
        ready(); activity.reply(stopped("LATE_OTHER_REASON")); button("export").performClick();
        activity.chooser(Activity.RESULT_OK, new Intent().setData(Uri.parse("content://chosen/document/1")));
        assertTrue(activity.written.contains("UNKNOWN_FLOW_AOD")); assertFalse(activity.written.contains("LATE_OTHER_REASON"));
    }
    @Test public void requestTimeoutRejectsLateCallbackWithoutPretendingStopped() {
        button("start").performClick();
        ShadowLooper looper = Shadow.extract(Looper.getMainLooper()); looper.idleFor(Duration.ofSeconds(11));
        assertTrue(state().contains("未确认")); assertTrue(activity.cancelled > 0);
        activity.reply(running()); assertFalse(state().contains("诊断状态：RUNNING")); assertFalse(button("export").isEnabled());
    }
    @Test public void destroyCancelsOnlyCallbackAndNeverStopsOrChangesSwitches() {
        button("start").performClick(); controller.pause().stop().destroy(); controller = null;
        activity.reply(running()); assertEquals(List.of("START"), activity.operations); assertTrue(activity.cancelled > 0);
    }
    @Test public void diagnosticsLeavePermanentHomeAndLockPreferencesUnchanged() {
        android.content.Context storage = activity.createDeviceProtectedStorageContext();
        android.content.SharedPreferences home = storage.getSharedPreferences("home_compat_permanent", 0);
        android.content.SharedPreferences lock = storage.getSharedPreferences("lock_compat_permanent", 0);
        home.edit().putBoolean("enabled", true).commit(); lock.edit().putBoolean("enabled", false).commit();
        java.util.Map<String, ?> beforeHome = new java.util.HashMap<>(home.getAll());
        java.util.Map<String, ?> beforeLock = new java.util.HashMap<>(lock.getAll());
        button("start").performClick(); activity.reply(running()); ready();
        assertEquals(beforeHome, home.getAll()); assertEquals(beforeLock, lock.getAll());
    }
    @Test public void dashboardAdvancedEntryOpensOnlyPrivateDiagnosticActivity() {
        ActivityController<MainActivity> main = Robolectric.buildActivity(MainActivity.class).create();
        try {
            Button entry = findEntry(main.get().getWindow().getDecorView()); assertNotNull(entry);
            entry.performClick(); ShadowActivity shadow = Shadow.extract(main.get()); Intent next = shadow.getNextStartedActivity();
            assertEquals(new ComponentName(main.get(), LockDebugActivity.class), next.getComponent());
            assertNull(next.getAction()); assertNull(next.getData());
        } finally { main.destroy(); }
    }
    private Button findEntry(View root) {
        if (root instanceof Button && "调试与日志".contentEquals(((Button) root).getText())) return (Button) root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            Button found = findEntry(((ViewGroup) root).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    @Test public void activeWindowCannotRestartOrClearItsOwnEvidence() {
        button("start").performClick(); activity.reply(running());
        assertFalse(button("start").isEnabled()); button("start").performClick();
        assertEquals(List.of("START"), activity.operations);
        ready(); assertTrue(button("start").isEnabled());
    }
    @Test public void alreadyRunningMetadataDoesNotPretendToStartAnotherSession() {
        button("start").performClick();
        activity.reply(LockRelayService.DiagSnapshot.create(false, "START", "ALREADY_RUNNING", true, false, 1, 0, 0, List.of()));
        assertTrue(state().contains("ALREADY_RUNNING")); assertFalse(button("start").isEnabled());
        assertFalse(button("export").isEnabled());
    }
}
