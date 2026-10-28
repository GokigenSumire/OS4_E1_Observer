package personal.wallpaper.os4.e1.relay;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import personal.wallpaper.os4.e1.BuildConfig;
import personal.wallpaper.os4.e1.lockinput.NativeInputDetail;
import personal.wallpaper.protocol.lock.LockProtocol;
import static org.junit.Assert.*;

/** 实际Service/Parcel/主线程回调适配，不执行OEM或真实跨进程Binder。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, manifest=Config.NONE)
public final class DiagnosticRelayAdapterTest {
    private LockRelayService service;
    private Context own;
    private FakeServer server;
    private static final String LINE = "row=1 stage=INPUT cause=UNKNOWN_mShowing total=1";
    @Before public void before() throws Exception {
        service = new LockRelayService(); server = new FakeServer();
        set(null, "diagnosticService", service); set(service, "serverSession", server);
        own = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public String getPackageName() { return BuildConfig.APPLICATION_ID; }
            @Override public ApplicationInfo getApplicationInfo() { ApplicationInfo info = new ApplicationInfo(); info.uid = Process.myUid(); return info; }
            @Override public boolean bindService(Intent intent, ServiceConnection connection, int flags) { throw new AssertionError("diagnostic must not bind or start a service"); }
        };
    }
    @After public void after() throws Exception {
        if (service != null) {
            set(service, "armed", false); set(service, "hostSession", null);
            service.onDestroy();
        }
        set(null, "diagnosticService", null);
    }
    @Test public void ownProcessCallbackArrivesOnMainWithImmutableStoppedSnapshot() throws Exception {
        server.active = 0; server.reason = 2; server.lines.add(LINE);
        AtomicReference<LockRelayService.DiagSnapshot> output = new AtomicReference<>();
        AtomicReference<Looper> callbackLooper = new AtomicReference<>();
        LockRelayService.requestDiag(own, "SNAPSHOT", result -> { callbackLooper.set(Looper.myLooper()); output.set(result); });
        LockRelayService.DiagSnapshot result = await(output);
        assertTrue(result.success); assertTrue(result.complete); assertFalse(result.active);
        assertEquals(Looper.getMainLooper(), callbackLooper.get()); assertEquals(List.of(LINE), result.lines);
        assertFalse(result.toText().contains(server.token)); assertFalse(result.toText().contains("actor"));
        assertTrue(result.toText().contains("module=" + BuildConfig.VERSION_CODE + "/" + BuildConfig.VERSION_NAME));
        try { result.lines.clear(); fail(); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void startStopDoNotChangePermanentSwitchHostHoldOrDailyState() throws Exception {
        set(service, "armed", true); set(service, "hostReady", true); set(service, "hostSession", "held-authentication");
        set(service, "commandSequence", 17L); set(null, "liveStatus", "UNCHANGED_DAILY_STATUS");
        Object admission = get(service, "admission");
        assertTrue(call("START").active); server.lines.add(LINE);
        LockRelayService.DiagSnapshot stopped = call("STOP");
        assertTrue(stopped.complete); assertTrue((Boolean)get(service, "armed")); assertTrue((Boolean)get(service, "hostReady"));
        assertEquals("held-authentication", get(service, "hostSession")); assertEquals(17L, get(service, "commandSequence"));
        assertSame(admission, get(service, "admission")); assertSame(server, get(service, "serverSession"));
        assertEquals("UNCHANGED_DAILY_STATUS", get(null, "liveStatus"));
        assertFalse(server.codes.contains(1)); assertFalse(server.codes.contains(2)); assertFalse(server.codes.contains(3));
        set(service, "hostSession", null); // 测试清理不执行模拟宿主CLOSE。
    }
    @Test public void diagnosticWorksWhenLockOffAndHostNotReady() throws Exception {
        assertFalse((Boolean)get(service, "armed")); assertFalse((Boolean)get(service, "hostReady"));
        assertTrue(call("START").success); assertTrue(call("STOP").complete);
        assertFalse((Boolean)get(service, "armed")); assertFalse((Boolean)get(service, "hostReady"));
    }
    @Test public void unavailableServiceDoesNotStartOrBindAnythingImplicitly() throws Exception {
        set(null, "diagnosticService", null);
        assertEquals("SERVICE_UNAVAILABLE", call("STATUS").detail); assertTrue(server.codes.isEmpty());
    }
    @Test public void wrongContextPackageOrUidRejectedLocally() throws Exception {
        Context badPackage = new ContextWrapper(own) { @Override public String getPackageName() { return "other.package"; } };
        AtomicReference<LockRelayService.DiagSnapshot> first = new AtomicReference<>();
        LockRelayService.requestDiag(badPackage, "STATUS", first::set);
        assertEquals("LOCAL_CALLER_REQUIRED", await(first).detail);
        Context badUid = new ContextWrapper(own) { @Override public ApplicationInfo getApplicationInfo() { ApplicationInfo info = new ApplicationInfo(); info.uid = Process.myUid() + 1; return info; } };
        AtomicReference<LockRelayService.DiagSnapshot> second = new AtomicReference<>();
        LockRelayService.requestDiag(badUid, "STATUS", second::set);
        assertEquals("LOCAL_CALLER_REQUIRED", await(second).detail); assertTrue(server.codes.isEmpty());
    }
    @Test public void invalidOperationNeverTouchesRemoteChannel() throws Exception {
        assertEquals("INVALID_OPERATION", call("DAILY_ON").detail); assertTrue(server.codes.isEmpty());
    }
    @Test public void cancellingCallbackDoesNotStopDiagnosticOrIssueAnyExtraCommand() throws Exception {
        server.entered = new CountDownLatch(1); server.release = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        LockRelayService.DiagRequest request = LockRelayService.requestDiag(own, "START", result -> callbacks.incrementAndGet());
        assertTrue(server.entered.await(2, TimeUnit.SECONDS)); request.cancel(); server.release.countDown();
        waitIdle(); assertEquals(0, callbacks.get());
        assertEquals(List.of(DiagnosticClient.STATE, DiagnosticClient.STATE, DiagnosticClient.START, DiagnosticClient.STATE, 11), server.codes);
        assertEquals(List.of(NativeInputDetail.code("NATIVE_EDIT_BASELINE_UNAVAILABLE")),server.editResultReports);
        // 新START只读预采游标及明确缺口不属于ARM／播放／编辑授权；取消页面回调不STOP或导出。
        for(int code=1;code<=6;code++)assertFalse(server.codes.contains(code));
        assertFalse(server.codes.contains(DiagnosticClient.STOP));assertFalse(server.codes.contains(DiagnosticClient.PAGE));
        assertEquals(1, server.active);
    }
    @Test public void concurrentRequestRejectedBusyWithoutQueuingOrReplacingChannel() throws Exception {
        server.entered = new CountDownLatch(1); server.release = new CountDownLatch(1);
        AtomicReference<LockRelayService.DiagSnapshot> first = new AtomicReference<>(), second = new AtomicReference<>();
        LockRelayService.requestDiag(own, "STATUS", first::set); assertTrue(server.entered.await(2, TimeUnit.SECONDS));
        LockRelayService.requestDiag(own, "STATUS", second::set);
        assertEquals("BUSY", await(second).detail); assertEquals(1, server.codes.size());
        server.release.countDown(); assertTrue(await(first).success); assertSame(server, get(service, "serverSession"));
    }
    @Test public void oversizedParcelRejectedBeforeParsingOrDisplayingPayload() throws Exception {
        server.token = "x".repeat(600);
        LockRelayService.DiagSnapshot result = call("STATUS");
        assertFalse(result.success); assertEquals("CHANNEL_UNAVAILABLE", result.detail); assertTrue(result.lines.isEmpty());
    }
    @Test public void trailingParcelDataRejectedWithoutWideningPublicBinderSurface() throws Exception {
        server.trailing = true;
        assertEquals("CHANNEL_UNAVAILABLE", call("STATUS").detail);
        Parcel input = Parcel.obtain(), output = Parcel.obtain();
        try {
            input.writeInterfaceToken(LockProtocol.RELAY);
            assertFalse(service.onBind(new Intent()).transact(DiagnosticClient.START, input, output, 0));
        } finally { input.recycle(); output.recycle(); }
    }
    @Test public void snapshotFactoryRejectsUnsanitizedTextAndNeverExposesWireToken() {
        LockRelayService.DiagSnapshot result = LockRelayService.DiagSnapshot.create(true, "STOP", "FROZEN_SNAPSHOT", false,
                true, 2, 1, LINE.length() + 1, List.of(LINE));
        assertTrue(result.complete); assertEquals(1, result.lines.size()); assertFalse(result.toText().contains("session="));
        try { LockRelayService.DiagSnapshot.create(true, "STOP", "FROZEN_SNAPSHOT", false, true, 2, 1,
                15, List.of("/private/file")); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void sessionReplacementDuringRequestCannotReturnSuccessfulOldSnapshot() throws Exception {
        server.entered = new CountDownLatch(1); server.release = new CountDownLatch(1);
        AtomicReference<LockRelayService.DiagSnapshot> result = new AtomicReference<>();
        LockRelayService.requestDiag(own, "STATUS", result::set); assertTrue(server.entered.await(2, TimeUnit.SECONDS));
        set(service, "serverSession", new Binder()); server.release.countDown();
        assertEquals("CHANNEL_UNAVAILABLE", await(result).detail);
    }
    private LockRelayService.DiagSnapshot call(String operation) throws Exception {
        AtomicReference<LockRelayService.DiagSnapshot> output = new AtomicReference<>();
        LockRelayService.requestDiag(own, operation, output::set); return await(output);
    }
    private static LockRelayService.DiagSnapshot await(AtomicReference<LockRelayService.DiagSnapshot> value) throws Exception {
        for (int i = 0; i < 200; i++) { mainLooper().idle(); if (value.get() != null) return value.get(); Thread.sleep(10); }
        throw new AssertionError("bounded callback wait expired");
    }
    private void waitIdle() throws Exception {
        for (int i = 0; i < 200; i++) { mainLooper().idle(); if (!((java.util.concurrent.atomic.AtomicBoolean)get(service, "diagnosticBusy")).get()) { Thread.sleep(10); mainLooper().idle(); return; } Thread.sleep(10); }
        throw new AssertionError("bounded diagnostic wait expired");
    }
    private static Object get(Object instance, String name) throws Exception {
        Field field = LockRelayService.class.getDeclaredField(name); field.setAccessible(true); return field.get(instance);
    }
    private static ShadowLooper mainLooper() { return Shadow.extract(Looper.getMainLooper()); }
    private static void set(Object instance, String name, Object value) throws Exception {
        Field field = LockRelayService.class.getDeclaredField(name); field.setAccessible(true); field.set(instance, value);
    }
    private static final class FakeServer extends Binder {
        final ArrayList<Integer> codes = new ArrayList<>(); final ArrayList<String> lines = new ArrayList<>();
        final ArrayList<Integer> editResultReports=new ArrayList<>();
        String token = "internal-diagnostic-token"; long revision = 1; int active, reason = 2; boolean trailing;
        CountDownLatch entered, release;
        @Override protected boolean onTransact(int code, Parcel input, Parcel output, int flags) {
            codes.add(code); input.enforceInterface(LockProtocol.SESSION);
            if (entered != null) entered.countDown();
            if (release != null) try { if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("release timeout"); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
            if(code==11){
                assertEquals(token,input.readString());assertEquals(0,input.readInt());int cause=input.readInt();
                assertEquals(NativeInputDetail.code("NATIVE_EDIT_BASELINE_UNAVAILABLE"),cause);
                assertTrue(NativeInputDetail.isEditResult(cause));assertEquals(1L,input.readLong());assertEquals(0,input.dataAvail());
                editResultReports.add(cause);output.writeNoException();output.writeInt(0);return true;
            }
            if (code == DiagnosticClient.STOP) { assertEquals(token, input.readString()); active = 0; reason = 2; revision++; }
            else if (code == DiagnosticClient.START) { token = "fresh-internal-diagnostic-token"; active = 1; reason = 1; revision++; lines.clear(); }
            int offset = 0;
            if (code == DiagnosticClient.PAGE) { assertEquals(token, input.readString()); assertEquals(revision, input.readLong()); offset = input.readInt(); }
            assertEquals(0, input.dataAvail()); output.writeNoException(); output.writeString(token); output.writeLong(revision);
            output.writeInt(active); output.writeInt(reason); output.writeInt(lines.size()); output.writeInt(lines.stream().mapToInt(row -> row.length() + 1).sum());
            if (code == DiagnosticClient.PAGE) { int count = Math.min(3, lines.size() - offset); output.writeInt(count); for (int i = 0; i < count; i++) output.writeString(lines.get(offset + i)); }
            if (trailing) output.writeInt(123);
            return true;
        }
    }
}
