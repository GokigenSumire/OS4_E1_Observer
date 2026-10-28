package personal.wallpaper.os4.e1;

import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import org.junit.Test;
import personal.wallpaper.os4.e1.core.DailyController;
import personal.wallpaper.os4.e1.core.ObservationBuffer;
import personal.wallpaper.os4.e1.core.SessionEpoch;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * 执行真实日常 HomeObserver 回调和撤权路径，整个自然调用夹具持有 WM 锁。
 * 时钟、WMS 对象与 Xposed API 是 JVM 替身；不声称执行 ART、真实系统调用或设备恢复。
 */
public class HomeObserverDailyAdapterTest {
    @Test public void oemShortCircuitWithoutGetterIsNotAFieldMutation() throws Throwable {
        for(boolean original : new boolean[]{false,true}) {
            for(boolean keyguard : new boolean[]{false,true}) {
                Fixture f=new Fixture();f.home.mAttrs.mWallpaperTouchEventsEnabled=original;f.enable();
                synchronized(f.service.mGlobalLock){
                    XC_MethodHook.MethodHookParam entered=f.enter();
                    // OEM && 短路：非壁纸目标或keyguard时，不调用触摸getter。
                    f.gates(keyguard,keyguard);f.has(false,false);f.leave(entered);
                }
                assertEquals("ENABLE",f.daily.mode());
                assertFalse(f.daily.status(1000).contains("dailyFieldViolation=true"));
                assertEquals(0,f.daily.appliedCount());f.assertClean();
            }
        }
    }

    @Test public void shortCircuitDuringRecoveryDoesNotProveRecoveryOrPoisonTheField() throws Throwable {
        Fixture f=new Fixture();f.enable();f.observer.recover(1001,"USER_STOP");
        synchronized(f.service.mGlobalLock){
            XC_MethodHook.MethodHookParam entered=f.enter();f.gates(false,false);f.has(false,false);f.leave(entered);
        }
        assertFalse(f.daily.recoveryProved());
        assertFalse(f.daily.status(1001).contains("dailyFieldViolation=true"));
        SystemClock.setElapsedRealtime(1002);f.cycle(false);assertTrue(f.daily.recoveryProved());f.assertClean();
    }

    @Test public void actualFieldMutationWithoutGetterStillLocksRecovery() throws Throwable {
        Fixture f=new Fixture();f.enable();
        synchronized(f.service.mGlobalLock){
            XC_MethodHook.MethodHookParam entered=f.enter();f.gates(false,false);
            f.home.mAttrs.mWallpaperTouchEventsEnabled=true;f.has(false,false);f.leave(entered);
        }
        assertEquals("RECOVERY",f.daily.mode());
        assertTrue(f.daily.status(1001).contains("dailyFieldViolation=true"));f.assertClean();
    }
    @Test public void nativeTrueWhileEnabledPassesThroughWithoutFieldViolation()throws Throwable{Fixture f=new Fixture();f.home.mAttrs.mWallpaperTouchEventsEnabled=true;f.enable();assertEquals(Boolean.TRUE,f.cycle(true).getResult());assertEquals(0,f.daily.appliedCount());assertEquals("ENABLE",f.daily.mode());assertTrue(f.observer.contrastStatus(1000).contains("homeForwarding=NATIVE_FORWARDING_ALLOWED"));assertFalse(f.observer.contrastStatus(1000).contains("dailyFieldViolation=true"));}
    @Test public void nativeTrueWhileOffNeverReservesCorrection()throws Throwable{Fixture f=new Fixture();f.home.mAttrs.mWallpaperTouchEventsEnabled=true;assertEquals(Boolean.TRUE,f.cycle(true).getResult());assertEquals(0,f.daily.appliedCount());}
    @Test public void nativeTrueNaturalRecoveryRestoresTrueNotFalse()throws Throwable{Fixture f=new Fixture();f.home.mAttrs.mWallpaperTouchEventsEnabled=true;f.enable();f.cycle(true);f.observer.recover(1001,"USER_STOP");SystemClock.setElapsedRealtime(1001);assertEquals(Boolean.TRUE,f.cycle(true).getResult());assertTrue(f.daily.recoveryProved());assertEquals(0,f.daily.appliedCount());}
    @Test public void nonFocusedHomeDoesNotRepairEvenWhenWallpaperTargetStale()throws Throwable{Fixture f=new Fixture();f.enable();f.display.mCurrentFocus=new Object();assertEquals(Boolean.FALSE,f.cycle(false).getResult());assertEquals(0,f.daily.appliedCount());}
    @Test public void temporaryLossOfFocusAfterCorrectionSuspendsOnlyThisPopulate() throws Throwable {
        Fixture f=new Fixture();f.enable();assertEquals(Boolean.TRUE,f.cycle(false).getResult());
        f.display.mCurrentFocus=new Object();assertEquals(Boolean.FALSE,f.cycle(false).getResult());
        assertEquals("ENABLE",f.daily.mode());assertFalse(f.daily.needsRecovery());
        assertEquals(1,f.daily.appliedCount());
        f.display.mCurrentFocus=f.home;assertEquals(Boolean.TRUE,f.cycle(false).getResult());
        assertEquals(2,f.daily.appliedCount());f.assertClean();
    }
    @Test public void focusSuspensionDoesNotBypassExplicitStopOrNaturalRecoveryProof() throws Throwable {
        Fixture f=new Fixture();f.enable();f.cycle(false);f.display.mCurrentFocus=new Object();
        assertEquals(Boolean.FALSE,f.cycle(false).getResult());assertEquals("ENABLE",f.daily.mode());
        f.observer.recover(1001,"USER_STOP");SystemClock.setElapsedRealtime(1001);f.cycle(false);
        assertEquals(1,f.daily.appliedCount());
        f.display.mCurrentFocus=f.home;f.cycle(false);
        assertTrue(f.daily.recoveryProved());assertEquals(1,f.daily.appliedCount());f.assertClean();
    }
    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static Object nested(String name) throws Exception {
        Class<?> type = Class.forName(HomeObserver.class.getName() + "$" + name);
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static XC_MethodHook anonymous(HomeObserver observer, int number) throws Exception {
        Class<?> type = Class.forName(HomeObserver.class.getName() + "$" + number);
        Constructor<?> constructor = type.getDeclaredConstructor(HomeObserver.class);
        constructor.setAccessible(true);
        return (XC_MethodHook) constructor.newInstance(observer);
    }

    private static XC_MethodHook guarded(HomeObserver observer, XC_MethodHook callback) throws Exception {
        Method method = HomeObserver.class.getDeclaredMethod("guard", XC_MethodHook.class);
        method.setAccessible(true);
        return (XC_MethodHook) method.invoke(observer, callback);
    }

    private static XC_MethodHook hasHook(HomeObserver observer) throws Exception {
        Method method = HomeObserver.class.getDeclaredMethod("pair", String.class, String.class, int.class);
        method.setAccessible(true);
        return guarded(observer, (XC_MethodHook) method.invoke(observer, "HAS", "requested", 0));
    }

    @SuppressWarnings("unchecked")
    private static ThreadLocal<Object> local(HomeObserver observer, String name) throws Exception {
        return (ThreadLocal<Object>) get(observer, name);
    }

    private static XC_MethodHook.MethodHookParam param(Object object, Object result, Object... args) {
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.thisObject = object;
        param.args = args;
        param.setResult(result);
        return param;
    }

    private static final class Layout {
        String packageName = "com.miui.home";
        int type = 1;
        boolean mWallpaperTouchEventsEnabled;
    }

    private static final class ActivityRecord {
        String packageName = "com.miui.home";
        int activityType = 2;
        int getActivityType() { return activityType; }
    }

    private static final class Input { int inputConfig; }
    private static final class Wrapper { Input mHandle = new Input(); boolean mChanged; }
    private static final class Wallpaper {
        Layout mAttrs = new Layout();
        Object mToken;
        int display;
        int getDisplayId() { return display; }
    }

    private static final class Token {
        final List<Object> children = new ArrayList<>();
        boolean countThrows, childThrows;
        Integer countOverride;
        int getChildCount() {
            if (countThrows) throw new IllegalStateException("INJECTED_TOKEN_COUNT");
            return countOverride == null ? children.size() : countOverride;
        }
        Object getChildAt(int index) {
            if (childThrows) throw new IllegalStateException("INJECTED_TOKEN_CHILD");
            return children.get(index);
        }
    }

    private static final class WallpaperController {
        Object mWallpaperTarget;
        Object token;
        Object visible;
        boolean tokenThrows, visibleThrows;
        Object getTokenForTarget(Object target) {
            if (tokenThrows) throw new IllegalStateException("INJECTED_SELECTED_TOKEN");
            return target == mWallpaperTarget ? token : null;
        }
        Object getTopVisibleWallpaper() {
            if (visibleThrows) throw new IllegalStateException("INJECTED_VISIBLE_WALLPAPER");
            return visible;
        }
    }

    private static final class Display {
        Object mWallpaperController;
        Object mCurrentFocus;
    }

    private static final class Window {
        Layout mAttrs = new Layout();
        ActivityRecord mActivityRecord = new ActivityRecord();
        Wrapper mInputWindowHandle = new Wrapper();
        Object mDisplayContent;
        int display;
        int getDisplayId() { return display; }
    }

    private static final class Policy { boolean isKeyguardShowing() { return false; } }
    private static final class Service {
        Object mGlobalLock = new Object();
        Map<Object, Window> mWindowMap = new LinkedHashMap<>();
        Policy mPolicy = new Policy();
    }

    private static final class Fixture {
        final ObservationBuffer buffer;
        final HomeObserver observer;
        final DailyController daily;
        final Window home = new Window();
        final Wallpaper wallpaper = new Wallpaper();
        final Token token = new Token();
        final WallpaperController controller = new WallpaperController();
        final Display display = new Display();
        final Service service = new Service();
        final XC_MethodHook populate, target, keyguard, getter, has;

        Fixture() throws Exception { this(new ObservationBuffer(), false); }
        Fixture(boolean diagnostics) throws Exception { this(new ObservationBuffer(), diagnostics); }
        Fixture(ObservationBuffer buffer, boolean diagnostics) throws Exception {
            SystemClock.setElapsedRealtime(1_000);
            this.buffer = buffer;
            observer = new HomeObserver(HomeObserverDailyAdapterTest.class.getClassLoader(), buffer, true);
            wallpaper.mAttrs.packageName = BuildConfig.WALLPAPER_APPLICATION_ID;
            wallpaper.mAttrs.type = 2013;
            wallpaper.mToken = token;
            token.children.add(wallpaper);
            controller.mWallpaperTarget = home;
            controller.token = token;
            controller.visible = wallpaper;
            display.mWallpaperController = controller;
            display.mCurrentFocus = home;
            home.mDisplayContent = display;
            service.mWindowMap.put("HOME", home);
            observer.setService(service);
            if (diagnostics) assertTrue(buffer.start(1_000, "daily-adapter"));
            assertTrue(observer.resetIdentity(1_000));
            daily = (DailyController) get(observer, "contrast");
            // 使用生产 install 中的真实匿名类，不复制 getter/资格/恢复逻辑。
            populate = guarded(observer, anonymous(observer, 1));
            target = guarded(observer, anonymous(observer, 2));
            keyguard = guarded(observer, anonymous(observer, 3));
            getter = guarded(observer, anonymous(observer, 4));
            has = hasHook(observer);
        }

        void requireLock() { assertTrue(Thread.holdsLock(service.mGlobalLock)); }
        void enable() { assertTrue(observer.enable(SystemClock.elapsedRealtime())); }

        XC_MethodHook.MethodHookParam enter() throws Throwable {
            requireLock();
            XC_MethodHook.MethodHookParam result = param(null, null, home.mInputWindowHandle, home);
            populate.callBefore(result);
            return result;
        }

        void leave(XC_MethodHook.MethodHookParam result) throws Throwable {
            requireLock();
            populate.callAfter(result);
        }

        void gates(boolean targetValue, boolean keyguardValue) throws Throwable {
            requireLock();
            XC_MethodHook.MethodHookParam t = param(controller, targetValue, home);
            target.callBefore(t); target.callAfter(t);
            XC_MethodHook.MethodHookParam k = param(service.mPolicy, keyguardValue);
            keyguard.callBefore(k); keyguard.callAfter(k);
        }

        XC_MethodHook.MethodHookParam getter(boolean original) throws Throwable {
            requireLock();
            XC_MethodHook.MethodHookParam result = param(home.mAttrs, original);
            getter.callBefore(result); getter.callAfter(result);
            return result;
        }

        void has(boolean requested, boolean throwsException) throws Throwable {
            requireLock();
            XC_MethodHook.MethodHookParam result = param(home.mInputWindowHandle, null, requested);
            has.callBefore(result);
            if (throwsException) result.setThrowable(new IllegalStateException("INJECTED_HAS_FAILURE"));
            else home.mInputWindowHandle.mHandle.inputConfig = requested ? 32 : 0;
            has.callAfter(result);
        }

        XC_MethodHook.MethodHookParam cycle(boolean original) throws Throwable {
            synchronized (service.mGlobalLock) {
                XC_MethodHook.MethodHookParam entered = enter();
                gates(true, false);
                XC_MethodHook.MethodHookParam result = getter(original);
                has(Boolean.TRUE.equals(result.getResult()), false);
                leave(entered);
                return result;
            }
        }

        void assertClean() throws Exception {
            assertEquals(0, get(observer, "inFlight"));
            assertEquals(0, get(observer, "taintedInFlight"));
            assertNull(local(observer, "frame").get());
        }
    }

    @Test public void defaultDailyInstanceIsOffAndHasNoAutomaticRestartOrOverride() throws Throwable {
        ObservationBuffer buffer = new ObservationBuffer();
        HomeObserver observer = new HomeObserver(getClass().getClassLoader(), buffer, true);
        assertFalse(observer.running());
        assertTrue(observer.canStart());
        assertFalse(observer.enable(1_000));
        assertEquals("DISARMED", ((DailyController) get(observer, "contrast")).mode());
    }

    @Test public void manualEnableWorksWithoutStartingDiagnosticBuffer() throws Throwable {
        Fixture f = new Fixture();
        assertFalse(f.buffer.active(1_000));
        f.enable();
        assertEquals(Boolean.TRUE, f.cycle(false).getResult());
        assertEquals(1, f.daily.appliedCount());
        assertEquals(0, f.buffer.size());
        assertFalse(f.home.mAttrs.mWallpaperTouchEventsEnabled);
        assertEquals(32, f.home.mInputWindowHandle.mHandle.inputConfig);
        assertTrue(f.observer.contrastStatus(1_000).contains("targetWallpaper=PROJECT_WALLPAPER"));
        f.assertClean();
    }

    @Test public void baselineDoesNotOverrideEvenWithExactProjectWallpaper() throws Throwable {
        Fixture f = new Fixture();
        assertEquals(Boolean.FALSE, f.cycle(false).getResult());
        assertEquals(0, f.daily.appliedCount());
        assertEquals("BASELINE", f.daily.mode());
        assertFalse(f.home.mAttrs.mWallpaperTouchEventsEnabled);
        f.assertClean();
    }

    @Test public void diagnosticTimeoutAfterTwoHundredSecondsDoesNotStopDailyOverride() throws Throwable {
        Fixture f = new Fixture(true);
        f.enable();
        assertEquals(Boolean.TRUE, f.cycle(false).getResult());
        SystemClock.setElapsedRealtime(201_000);
        assertFalse(f.buffer.active(201_000));
        assertFalse(f.observer.tick(201_000));
        assertEquals(Boolean.TRUE, f.cycle(false).getResult());
        assertEquals("ENABLE", f.daily.mode());
        assertEquals(2, f.daily.appliedCount());
        assertTrue(f.buffer.status(201_000).contains("reason=TIMEOUT "));
        f.assertClean();
    }

    @Test public void manualDiagnosticStopDoesNotStopDailyOrChangeItsDeadline() throws Throwable {
        Fixture f = new Fixture(true);
        f.enable();
        long deadline = f.daily.deadline();
        f.buffer.stop("USER_STOP");
        assertEquals(Boolean.TRUE, f.cycle(false).getResult());
        assertFalse(f.observer.tick(2_000));
        assertEquals(deadline, f.daily.deadline());
        assertEquals("ENABLE", f.daily.mode());
    }

    @Test public void allDiagnosticCapacityLimitsRemainIndependentOfDailyPermission() throws Throwable {
        for (int[] limits : new int[][]{{1, 1_572_864, 1_024},
                {6_000, 128, 1_024}, {6_000, 1_572_864, 80}}) {
            Constructor<ObservationBuffer> constructor = ObservationBuffer.class.getDeclaredConstructor(
                    int.class, int.class, int.class, long.class);
            constructor.setAccessible(true);
            Fixture f = new Fixture(constructor.newInstance(limits[0], limits[1], limits[2], 200_000), true);
            f.enable();
            assertEquals(Boolean.TRUE, f.cycle(false).getResult());
            assertFalse(f.buffer.active(1_000));
            assertFalse(f.observer.tick(1_000));
            assertEquals(Boolean.TRUE, f.cycle(false).getResult());
            assertEquals("ENABLE", f.daily.mode());
            assertEquals(2, f.daily.appliedCount());
            f.assertClean();
        }
    }

    @Test public void restartingDiagnosticsCannotResetDailyEpochCountsOrLease() throws Throwable {
        Fixture f = new Fixture(true);
        f.enable();
        f.cycle(false);
        long epoch = f.daily.epoch();
        long deadline = f.daily.deadline();
        f.buffer.stop("USER_STOP");
        SystemClock.setElapsedRealtime(60_000);
        assertTrue(f.buffer.start(60_000, "second-diagnostic"));
        assertFalse(f.observer.enable(60_000));
        assertEquals(epoch, f.daily.epoch());
        assertEquals(deadline, f.daily.deadline());
        assertEquals(1, f.daily.appliedCount());
        assertEquals(Boolean.TRUE, f.cycle(false).getResult());
    }

    @Test public void dailyKeepsWorkingAfterOldThreeMinuteLeaseAndBeforeTwelveHours() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        SystemClock.setElapsedRealtime(181_001);
        assertFalse(f.observer.tick(181_001));
        assertEquals(Boolean.TRUE, f.cycle(false).getResult());
        SystemClock.setElapsedRealtime(1_000 + DailyController.MAX_DURATION_MS - 1);
        assertEquals(Boolean.TRUE, f.cycle(false).getResult());
        assertEquals("ENABLE", f.daily.mode());
    }

    @Test public void exactOldTwelveHourBoundaryStillAllowsTheEnteredPermanentGetter() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        synchronized (f.service.mGlobalLock) {
            XC_MethodHook.MethodHookParam entered = f.enter();
            f.gates(true, false);
            SystemClock.setElapsedRealtime(1_000 + DailyController.MAX_DURATION_MS);
            assertEquals(Boolean.TRUE, f.getter(false).getResult());
            assertEquals("ENABLE", f.daily.mode());
            assertEquals(1, f.daily.appliedCount());
            f.has(true, false); f.leave(entered);
            assertFalse(f.daily.recoveryProved());
        }
        f.assertClean();
    }

    @Test public void thirteenHourWatchdogDoesNotExpireOrRestartThePermanentSession() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        f.cycle(false);
        long thirteenHours = 1_000 + 13 * 3_600_000L;
        SystemClock.setElapsedRealtime(thirteenHours);
        long epoch = f.daily.epoch();
        assertFalse(f.observer.tick(thirteenHours));
        assertEquals("ENABLE", f.daily.mode());
        assertFalse(f.daily.needsRecovery());
        assertFalse(f.observer.canStart());
        assertFalse(f.observer.resetIdentity(thirteenHours));
        assertFalse(f.observer.enable(thirteenHours));
        assertEquals(Boolean.TRUE, f.cycle(false).getResult());
        assertEquals(2, f.daily.appliedCount());
        assertEquals(epoch, f.daily.epoch());
        assertEquals(DailyController.NO_DEADLINE, f.daily.deadline());
    }

    @Test public void stopAfterHoursStillAllowsNewNaturalProofWithinTwentySeconds() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        f.cycle(false);
        long now = 40L * 24 * 3_600_000;
        SystemClock.setElapsedRealtime(now);
        f.observer.recover(now, "USER_STOP");
        assertEquals(Boolean.FALSE, f.cycle(false).getResult());
        assertTrue(f.daily.recoveryProved());
        assertTrue(f.observer.tick(now + 1));
        assertFalse(f.daily.needsRecovery());
        assertTrue(f.observer.canStart());
        assertTrue(f.observer.contrastStatus(now + 1).contains("RECOVERY_CONFIRMED"));
        f.assertClean();
    }

    @Test public void recoveryFinalizationUsesRecoveryCompleteDiagnosticReason() throws Throwable {
        Fixture f = new Fixture(true);
        f.enable();
        f.cycle(false);
        SystemClock.setElapsedRealtime(2_000);
        f.observer.recover(2_000, "USER_STOP");
        f.cycle(false);
        assertTrue(f.observer.tick(2_001));
        assertTrue(f.buffer.status(2_001).contains("reason=RECOVERY_COMPLETE "));
        assertTrue(f.daily.status(2_001).contains("dailyReason=USER_STOP "));
    }

    @Test public void repeatedErrorDoesNotExtendOuterTwentySecondRecoveryWindow() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        SystemClock.setElapsedRealtime(2_000);
        f.observer.recover(2_000, "USER_STOP");
        SystemClock.setElapsedRealtime(21_999);
        f.observer.recover(21_999, "ERROR");
        assertEquals(2_000L, get(f.observer, "recoveryStarted"));
        SystemClock.setElapsedRealtime(22_000);
        assertTrue(f.observer.tick(22_000));
        assertTrue(f.daily.needsRecovery());
        assertFalse(f.observer.canStart());
    }

    @Test public void permanentStopAfterMonthsStillHasOnlyTwentySecondsToRecover() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        long stopped = 90L * 24 * 3_600_000;
        SystemClock.setElapsedRealtime(stopped);
        assertFalse(f.observer.tick(stopped));
        f.observer.recover(stopped, "USER_STOP");
        SystemClock.setElapsedRealtime(stopped + 19_999);
        assertFalse(f.observer.tick(stopped + 19_999));
        assertEquals(stopped, get(f.observer, "recoveryStarted"));
        SystemClock.setElapsedRealtime(stopped + 20_000);
        assertTrue(f.observer.tick(stopped + 20_000));
        assertTrue(f.daily.needsRecovery());
        assertFalse(f.observer.canStart());
    }

    @Test public void permanentGetterAtMaximumClockStillAllowsANewNaturalRecovery() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        SystemClock.setElapsedRealtime(Long.MAX_VALUE - 2);
        assertEquals(Boolean.TRUE, f.cycle(false).getResult());
        assertFalse(f.observer.tick(Long.MAX_VALUE - 2));
        SystemClock.setElapsedRealtime(Long.MAX_VALUE - 1);
        f.observer.recover(Long.MAX_VALUE - 1, "USER_STOP");
        assertEquals(Boolean.FALSE, f.cycle(false).getResult());
        assertTrue(f.daily.recoveryProved());
        SystemClock.setElapsedRealtime(Long.MAX_VALUE);
        assertTrue(f.observer.tick(Long.MAX_VALUE));
        assertTrue(f.observer.canStart());
        assertFalse(f.daily.needsRecovery());
        assertTrue(f.observer.contrastStatus(Long.MAX_VALUE).contains("RECOVERY_CONFIRMED"));
        f.assertClean();
    }

    @Test public void unprovedPermanentRecoveryAtMaximumClockLocksWithoutWrapping() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        long stopped = Long.MAX_VALUE - 20_000;
        SystemClock.setElapsedRealtime(stopped);
        f.observer.recover(stopped, "USER_STOP");
        SystemClock.setElapsedRealtime(Long.MAX_VALUE - 1);
        assertFalse(f.observer.tick(Long.MAX_VALUE - 1));
        f.observer.recover(Long.MAX_VALUE - 1, "ERROR");
        assertEquals(stopped, get(f.observer, "recoveryStarted"));
        SystemClock.setElapsedRealtime(Long.MAX_VALUE);
        assertTrue(f.observer.tick(Long.MAX_VALUE));
        assertTrue(f.daily.needsRecovery());
        assertFalse(f.observer.canStart());
        assertFalse(f.observer.resetIdentity(Long.MAX_VALUE));
    }

    @Test public void stoppingLongRunningInFlightOverrideNeedsAnotherNaturalPopulate() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        long now = 400L * 24 * 3_600_000;
        SystemClock.setElapsedRealtime(now);
        synchronized (f.service.mGlobalLock) {
            XC_MethodHook.MethodHookParam entered = f.enter();
            f.gates(true, false);
            assertEquals(Boolean.TRUE, f.getter(false).getResult());
            f.observer.recover(now, "USER_STOP");
            f.has(true, false);
            f.leave(entered);
            assertFalse(f.daily.recoveryProved());
            assertFalse(f.observer.tick(now));
        }
        SystemClock.setElapsedRealtime(now + 1);
        assertEquals(Boolean.FALSE, f.cycle(false).getResult());
        assertTrue(f.daily.recoveryProved());
        assertTrue(f.observer.tick(now + 1));
        assertTrue(f.observer.canStart());
        f.assertClean();
    }

    @Test public void unrelatedWallpaperPackagesNeverReceiveProjectCompatibility() throws Throwable {
        for (String owner : new String[]{"com.android.systemui", "personal.wallpaper.os3".equals(BuildConfig.WALLPAPER_APPLICATION_ID)?"personal.wallpaper.os3.debug":"personal.wallpaper.os3",
                "personal.wallpaper.os4.e1.debug", "personal.wallpaper.os3.debug ", "", null}) {
            Fixture f = new Fixture();
            f.wallpaper.mAttrs.packageName = owner;
            f.enable();
            assertEquals(Boolean.FALSE, f.cycle(false).getResult());
            assertEquals(0, f.daily.appliedCount());
            assertEquals("ENABLE", f.daily.mode());
            assertFalse(f.home.mAttrs.mWallpaperTouchEventsEnabled);
            f.assertClean();
        }
    }

    @Test public void wrongWallpaperTypeOrDisplayCannotAuthorizeOverride() throws Throwable {
        for (int variant = 0; variant < 4; variant++) {
            Fixture f = new Fixture();
            if (variant < 2) f.wallpaper.mAttrs.type = variant == 0 ? 1 : 2011;
            else f.wallpaper.display = variant == 2 ? 1 : -1;
            f.enable();
            assertEquals(Boolean.FALSE, f.cycle(false).getResult());
            assertEquals(0, f.daily.appliedCount());
            assertEquals("ENABLE", f.daily.mode());
        }
    }

    @Test public void currentHomeMustRemainExactWallpaperTarget() throws Throwable {
        Fixture f = new Fixture();
        f.controller.mWallpaperTarget = new Window();
        f.enable();
        assertEquals(Boolean.FALSE, f.cycle(false).getResult());
        assertEquals(0, f.daily.appliedCount());
        assertTrue(f.observer.contrastStatus(1_000).contains("targetWallpaper=NOT_TARGET"));
    }

    @Test public void nullSelectedTokenOrNullVisibleWallpaperRejectsOverride() throws Throwable {
        for (boolean tokenMissing : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            if (tokenMissing) f.controller.token = null;
            else f.controller.visible = null;
            f.enable();
            assertEquals(Boolean.FALSE, f.cycle(false).getResult());
            assertEquals(0, f.daily.appliedCount());
            assertTrue(f.observer.contrastStatus(1_000).contains("targetWallpaper=NOT_VISIBLE"));
        }
    }

    @Test public void visibleWallpaperMustBelongToTheExactSelectedToken() throws Throwable {
        Fixture f = new Fixture();
        f.wallpaper.mToken = new Token();
        f.enable();
        assertEquals(Boolean.FALSE, f.cycle(false).getResult());
        assertEquals(0, f.daily.appliedCount());
    }

    @Test public void visibleWallpaperMustAppearExactlyOnceInSelectedTokenChildren() throws Throwable {
        for (int variant = 0; variant < 2; variant++) {
            Fixture f = new Fixture();
            if (variant == 0) f.token.children.set(0, new Wallpaper());
            else f.token.children.add(f.wallpaper);
            f.enable();
            assertEquals(Boolean.FALSE, f.cycle(false).getResult());
            assertEquals(0, f.daily.appliedCount());
        }
    }

    @Test public void tokenChildCountIsBoundedAndNeverScansAnUnboundedContainer() throws Throwable {
        for (int count : new int[]{-1, 0, 17, Integer.MAX_VALUE}) {
            Fixture f = new Fixture();
            f.token.countOverride = count;
            f.enable();
            assertEquals(Boolean.FALSE, f.cycle(false).getResult());
            assertEquals(0, f.daily.appliedCount());
            assertTrue(f.observer.contrastStatus(1_000).contains("targetWallpaper=UNKNOWN"));
        }
    }

    @Test public void sixteenTokenChildrenAreAcceptedOnlyWithOneExactVisibleChild() throws Throwable {
        Fixture f = new Fixture();
        for (int i = 1; i < 16; i++) f.token.children.add(new Wallpaper());
        f.enable();
        assertEquals(Boolean.TRUE, f.cycle(false).getResult());
        assertEquals(1, f.daily.appliedCount());
    }

    @Test public void wallpaperReadExceptionsAndMissingReflectionFieldsFailClosed() throws Throwable {
        for (int variant = 0; variant < 8; variant++) {
            Fixture f = new Fixture();
            switch (variant) {
                case 0: f.controller.tokenThrows = true; break;
                case 1: f.controller.visibleThrows = true; break;
                case 2: f.token.countThrows = true; break;
                case 3: f.token.childThrows = true; break;
                case 4: f.home.mDisplayContent = new Object(); break;
                case 5: f.display.mWallpaperController = new Object(); break;
                case 6: f.controller.visible = new Object(); break;
                default: f.controller.token = new Object();
            }
            f.enable();
            assertEquals(Boolean.FALSE, f.cycle(false).getResult());
            assertEquals(0, f.daily.appliedCount());
            assertEquals("ENABLE", f.daily.mode());
            assertTrue(f.observer.contrastStatus(1_000).contains("targetWallpaper=UNKNOWN"));
            f.assertClean();
        }
    }

    @Test public void wallpaperSwitchAfterOneAppliedOverrideRevokesDailyImmediately() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        assertEquals(Boolean.TRUE, f.cycle(false).getResult());
        f.wallpaper.mAttrs.packageName = "com.example.other.wallpaper";
        assertEquals(Boolean.FALSE, f.cycle(false).getResult());
        assertEquals(1, f.daily.appliedCount());
        assertEquals("RECOVERY", f.daily.mode());
        assertTrue(f.daily.needsRecovery());
        assertTrue(f.daily.status(1_000).contains("dailyReason=IDENTITY_CHANGED "));
        f.assertClean();
    }

    @Test public void wallpaperReflectionFailureAfterAnOverrideAlsoRevokesDaily() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        f.cycle(false);
        f.controller.visibleThrows = true;
        assertEquals(Boolean.FALSE, f.cycle(false).getResult());
        assertEquals("RECOVERY", f.daily.mode());
        assertEquals(1, f.daily.appliedCount());
        assertTrue(f.daily.needsRecovery());
    }

    @Test public void losingWmLockAtGetterNeverOverridesAndRevokesAfterPriorUse() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        f.cycle(false);
        XC_MethodHook.MethodHookParam entered;
        synchronized (f.service.mGlobalLock) { entered = f.enter(); f.gates(true, false); }
        XC_MethodHook.MethodHookParam getter = param(f.home.mAttrs, false);
        f.getter.callBefore(getter); f.getter.callAfter(getter);
        assertEquals(Boolean.FALSE, getter.getResult());
        assertEquals("RECOVERY", f.daily.mode());
        assertEquals(1, f.daily.appliedCount());
        synchronized (f.service.mGlobalLock) { f.has(false, false); f.leave(entered); }
        f.assertClean();
    }

    @Test public void targetFalseKeyguardTrueOrOriginalTrueNeverOverrides() throws Throwable {
        for (int variant = 0; variant < 3; variant++) {
            Fixture f = new Fixture();
            f.enable();
            synchronized (f.service.mGlobalLock) {
                XC_MethodHook.MethodHookParam entered = f.enter();
                f.gates(variant != 0, variant == 1);
                assertEquals(variant == 2, f.getter(variant == 2).getResult());
                assertEquals(0, f.daily.appliedCount());
                f.leave(entered);
            }
            f.assertClean();
        }
    }

    @Test public void getterThrowableIsPreservedAndWithdrawsPreviouslyProvedRecovery() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        f.cycle(false);
        f.observer.recover(1_000, "USER_STOP");
        f.cycle(false);
        assertTrue(f.daily.recoveryProved());
        synchronized (f.service.mGlobalLock) {
            XC_MethodHook.MethodHookParam entered = f.enter(); f.gates(true, false);
            XC_MethodHook.MethodHookParam getter = param(f.home.mAttrs, false);
            Throwable original = new IllegalStateException("ORIGINAL_GETTER_FAILURE");
            getter.setThrowable(original);
            f.getter.callBefore(getter); f.getter.callAfter(getter);
            assertSame(original, getter.getThrowable());
            assertFalse(f.daily.recoveryProved());
            assertEquals(1, f.daily.appliedCount());
            f.leave(entered);
        }
        f.assertClean();
    }

    @Test public void partialSetResultFailureRollsBackAndReleasesActualCounters() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        synchronized (f.service.mGlobalLock) {
            XC_MethodHook.MethodHookParam entered = f.enter(); f.gates(true, false);
            XC_MethodHook.MethodHookParam getter = param(f.home.mAttrs, false);
            getter.throwAfterTrueResult = true;
            f.getter.callBefore(getter); f.getter.callAfter(getter);
            assertEquals(Boolean.FALSE, getter.getResult());
            assertFalse(f.home.mAttrs.mWallpaperTouchEventsEnabled);
            assertEquals("RECOVERY", f.daily.mode());
            assertEquals(0, f.daily.appliedCount());
            f.leave(entered);
        }
        f.assertClean();
    }

    @Test public void callbackFailureBeforePopulateFinallyStillCleansOwnedCounters() throws Throwable {
        Fixture f = new Fixture(true);
        f.enable();
        synchronized (f.service.mGlobalLock) {
            XC_MethodHook.MethodHookParam entered = param(null, null, f.home.mInputWindowHandle, f.home);
            SystemClock.failOnceAfterReads(1);
            f.populate.callBefore(entered);
            assertEquals("RECOVERY", f.daily.mode());
            f.leave(entered);
        }
        f.assertClean();
    }

    @Test public void stoppingInsideAnEnabledPopulateRequiresAnotherNaturalRecoveryPopulate() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        synchronized (f.service.mGlobalLock) {
            XC_MethodHook.MethodHookParam entered = f.enter(); f.gates(true, false);
            assertEquals(Boolean.TRUE, f.getter(false).getResult());
            f.observer.recover(1_001, "USER_STOP");
            SystemClock.setElapsedRealtime(1_001);
            f.has(false, false); f.leave(entered);
            assertFalse(f.daily.recoveryProved());
        }
        assertEquals(Boolean.FALSE, f.cycle(false).getResult());
        assertTrue(f.daily.recoveryProved());
        f.assertClean();
    }

    @Test public void taintedOuterPopulatePreventsNestedRecoveryProofUntilItExits() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        synchronized (f.service.mGlobalLock) {
            XC_MethodHook.MethodHookParam outer = f.enter(); f.gates(true, false);
            assertEquals(Boolean.TRUE, f.getter(false).getResult());
            f.observer.recover(1_001, "USER_STOP");
            SystemClock.setElapsedRealtime(1_001);
            f.cycle(false);
            assertFalse(f.daily.recoveryProved());
            assertEquals(1, get(f.observer, "taintedInFlight"));
            f.has(false, false); f.leave(outer);
        }
        assertFalse(f.daily.recoveryProved());
        f.cycle(false);
        assertTrue(f.daily.recoveryProved());
        f.assertClean();
    }

    @Test public void missingOrThrowingHasCannotProveDailyRestoration() throws Throwable {
        for (boolean missing : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            f.observer.recover(1_000, "USER_STOP");
            synchronized (f.service.mGlobalLock) {
                XC_MethodHook.MethodHookParam entered = f.enter(); f.gates(true, false); f.getter(false);
                if (!missing) f.has(false, true);
                f.leave(entered);
            }
            assertFalse(f.daily.recoveryProved());
            assertTrue(f.daily.needsRecovery());
            f.cycle(false);
            assertTrue(f.daily.recoveryProved());
        }
    }

    @Test public void fieldViolationCannotBeHiddenBySubsequentNaturalRecovery() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        f.cycle(false);
        f.observer.recover(1_001, "USER_STOP");
        SystemClock.setElapsedRealtime(1_001);
        f.home.mAttrs.mWallpaperTouchEventsEnabled = true;
        f.cycle(false);
        assertFalse(f.daily.recoveryProved());
        f.home.mAttrs.mWallpaperTouchEventsEnabled = false;
        f.cycle(false);
        assertFalse(f.daily.recoveryProved());
        assertTrue(f.daily.needsRecovery());
        assertTrue(f.daily.status(1_001).contains("dailyFieldViolation=true "));
        assertTrue(f.daily.status(1_001).contains("dailyReason=USER_STOP "));
        assertFalse(f.home.mAttrs.mWallpaperTouchEventsEnabled);
        f.assertClean();
        SystemClock.setElapsedRealtime(21_001);
        assertTrue(f.observer.tick(21_001));
        assertTrue(f.daily.needsRecovery());
        assertFalse(f.observer.canStart());
        assertTrue(f.observer.contrastStatus(21_001).contains("RECOVERY_UNCONFIRMED"));
    }

    @Test public void actualDailyGetterRateLimitRevokesBeforeTheTwoThousandFirstOverride() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        for (int i = 0; i < 2_000; i++) assertEquals(Boolean.TRUE, f.cycle(false).getResult());
        assertEquals("ENABLE", f.daily.mode());
        assertEquals(Boolean.FALSE, f.cycle(false).getResult());
        assertEquals("RECOVERY", f.daily.mode());
        assertEquals(2_000, f.daily.appliedCount());
        assertTrue(f.daily.status(1_000).contains("dailyReason=LIMIT_RATE "));
        assertFalse(f.home.mAttrs.mWallpaperTouchEventsEnabled);
        f.assertClean();
    }

    @Test public void observedBooleanThrowableOrUnknownRevokesOldDailyProofEvenWhenOuterSwallowsIt()
            throws Throwable {
        for (String callback : new String[]{"TARGET", "KEYGUARD", "GETTER"}) {
            for (boolean throwsException : new boolean[]{false, true}) {
                Fixture f = new Fixture();
                f.observer.recover(1_000, "USER_STOP");
                f.cycle(false);
                assertTrue(f.daily.recoveryProved());
                synchronized (f.service.mGlobalLock) {
                    XC_MethodHook.MethodHookParam entered = f.enter();
                    XC_MethodHook hook;
                    XC_MethodHook.MethodHookParam result;
                    if ("TARGET".equals(callback)) {
                        hook = f.target; result = param(f.controller, true, f.home);
                    } else if ("KEYGUARD".equals(callback)) {
                        hook = f.keyguard; result = param(f.service.mPolicy, false);
                    } else {
                        hook = f.getter; result = param(f.home.mAttrs, false);
                    }
                    Throwable original = new IllegalStateException("INJECTED_BOOLEAN_FAILURE");
                    if (throwsException) result.setThrowable(original);
                    else result.setResult("UNKNOWN");
                    hook.callBefore(result); hook.callAfter(result);
                    assertFalse(callback, f.daily.recoveryProved());
                    assertTrue(callback, f.daily.needsRecovery());
                    if (throwsException) assertSame(original, result.getThrowable());
                    else assertEquals("UNKNOWN", result.getResult());
                    f.leave(entered);
                    assertFalse(callback, f.daily.recoveryProved());
                }
                f.assertClean();
            }
        }
    }

    @Test public void completedPopulateAfterCallbackIsIdempotentForRealCounters() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        synchronized (f.service.mGlobalLock) {
            XC_MethodHook.MethodHookParam entered = f.enter(); f.gates(true, false);
            f.getter(false); f.has(true, false); f.leave(entered); f.leave(entered);
        }
        f.assertClean();
        assertEquals(1, f.daily.appliedCount());
    }

    @Test public void oldPopulateAfterCannotCloseNewEpochFrameOrCounters() throws Throwable {
        Fixture f = new Fixture();
        synchronized (f.service.mGlobalLock) {
            XC_MethodHook.MethodHookParam old = f.enter();
            long next = ((SessionEpoch) get(f.observer, "sessions")).advance();
            Object frame = nested("Frame");
            set(frame, "epoch", next); set(frame, "window", f.home);
            Object request = nested("Request"); set(request, "epoch", next);
            local(f.observer, "frame").set(frame);
            local(f.observer, "request").set(request);
            set(f.observer, "inFlight", 1);
            f.leave(old);
            assertSame(frame, local(f.observer, "frame").get());
            assertSame(request, local(f.observer, "request").get());
            assertEquals(1, get(f.observer, "inFlight"));
            assertEquals(Boolean.FALSE, get(frame, "closed"));
        }
    }

    @Test public void oldGetterCallbackCannotAffectRestartedDailySession() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        f.cycle(false);
        f.observer.recover(1_001, "USER_STOP");
        SystemClock.setElapsedRealtime(1_001);
        f.cycle(false); assertTrue(f.observer.tick(1_002));
        assertTrue(f.observer.resetIdentity(2_000));
        assertTrue(f.observer.enable(2_001));
        SystemClock.setElapsedRealtime(2_002);
        XC_MethodHook.MethodHookParam old = param(f.home.mAttrs, false);
        synchronized (f.service.mGlobalLock) { f.getter.callBefore(old); f.getter.callAfter(old); }
        assertEquals(Boolean.FALSE, old.getResult());
        assertEquals(0, f.daily.appliedCount());
        assertEquals("ENABLE", f.daily.mode());
    }

    @Test public void staleGuardDoesNotUseItsClockReadToExpireTheNewDailySession() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        f.cycle(false);
        f.observer.recover(1_001, "USER_STOP");
        SystemClock.setElapsedRealtime(1_001);
        f.cycle(false); assertTrue(f.observer.tick(1_002));
        assertTrue(f.observer.resetIdentity(2_000));
        assertTrue(f.observer.enable(2_001));
        long currentEpoch = f.daily.epoch();
        SystemClock.setElapsedRealtime(Long.MAX_VALUE);
        XC_MethodHook.MethodHookParam old = param(f.home.mAttrs, false);
        synchronized (f.service.mGlobalLock) { f.getter.callBefore(old); f.getter.callAfter(old); }
        assertEquals(Boolean.FALSE, old.getResult());
        assertEquals(currentEpoch, f.daily.epoch());
        assertEquals("ENABLE", f.daily.mode());
        assertEquals(0, f.daily.appliedCount());
        assertEquals(1, f.daily.activeDurationMillis());
    }

    @Test public void stopSerializesWithActualGetterOverrideAndThenOnlyNaturalFalseRuns() throws Throwable {
        Fixture f = new Fixture();
        f.enable();
        CountDownLatch overrideEntered = new CountDownLatch(1);
        CountDownLatch releaseOverride = new CountDownLatch(1);
        CountDownLatch stopEntered = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> worker = executor.submit(() -> {
                try {
                    synchronized (f.service.mGlobalLock) {
                        XC_MethodHook.MethodHookParam entered = f.enter(); f.gates(true, false);
                        XC_MethodHook.MethodHookParam getter = param(f.home.mAttrs, false);
                        getter.resultObserver = result -> {
                            if (Boolean.TRUE.equals(result)) {
                                overrideEntered.countDown();
                                try { assertTrue(releaseOverride.await(5, TimeUnit.SECONDS)); }
                                catch (InterruptedException failure) { throw new AssertionError(failure); }
                            }
                        };
                        f.getter.callBefore(getter); f.getter.callAfter(getter);
                        assertEquals(Boolean.TRUE, getter.getResult());
                        f.has(true, false); f.leave(entered);
                    }
                } catch (Throwable failure) { throw new AssertionError(failure); }
            });
            assertTrue(overrideEntered.await(5, TimeUnit.SECONDS));
            Future<?> stop = executor.submit(() -> {
                stopEntered.countDown();
                f.observer.recover(1_001, "USER_STOP");
            });
            assertTrue(stopEntered.await(5, TimeUnit.SECONDS));
            assertFalse(stop.isDone());
            releaseOverride.countDown();
            worker.get(5, TimeUnit.SECONDS); stop.get(5, TimeUnit.SECONDS);
            SystemClock.setElapsedRealtime(1_001);
            assertEquals(Boolean.FALSE, f.cycle(false).getResult());
            assertTrue(f.daily.recoveryProved());
            assertEquals(1, f.daily.appliedCount());
            f.assertClean();
        } finally {
            releaseOverride.countDown(); executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
