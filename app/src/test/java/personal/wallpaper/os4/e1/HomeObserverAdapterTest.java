package personal.wallpaper.os4.e1;

import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import org.junit.Test;
import personal.wallpaper.os4.e1.core.ContrastController;
import personal.wallpaper.os4.e1.core.ObservationBuffer;
import personal.wallpaper.os4.e1.core.SessionEpoch;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * 执行真实 HomeObserver 的匿名回调、guard、bind、恢复和 tick。
 * Android 时钟与 Xposed 安装 API 仅替身；不声称执行 ART、真实 WMS 或设备恢复。
 */
public class HomeObserverAdapterTest {
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

    private static Object nested(HomeObserver observer, String name) throws Exception {
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

    private static XC_MethodHook pair(HomeObserver observer, String name, String key, int index) throws Exception {
        Method method = HomeObserver.class.getDeclaredMethod("pair", String.class, String.class, int.class);
        method.setAccessible(true);
        return guarded(observer, (XC_MethodHook) method.invoke(observer, name, key, index));
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
    private static final class Wrapper {
        Input mHandle = new Input();
        boolean mChanged;
    }
    private static final class Window {
        Layout mAttrs = new Layout();
        ActivityRecord mActivityRecord = new ActivityRecord();
        Wrapper mInputWindowHandle = new Wrapper();
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
        final ObservationBuffer buffer = new ObservationBuffer();
        final HomeObserver observer = new HomeObserver(HomeObserverAdapterTest.class.getClassLoader(), buffer);
        final Window window = new Window();
        final Service service = new Service();
        final ContrastController contrast;
        final XC_MethodHook populate, getter, target, keyguard, has;

        Fixture() throws Exception {
            SystemClock.setElapsedRealtime(1_000);
            service.mWindowMap.put("HOME", window);
            observer.setService(service);
            assertTrue(buffer.start(1_000, "adapter-test"));
            assertTrue(observer.resetIdentity(1_000));
            contrast = (ContrastController) get(observer, "contrast");
            // 编号由生产 install 中真实匿名类顺序确定；不复制回调业务逻辑。
            populate = guarded(observer, anonymous(observer, 1));
            target = guarded(observer, anonymous(observer, 2));
            keyguard = guarded(observer, anonymous(observer, 3));
            getter = guarded(observer, anonymous(observer, 4));
            has = pair(observer, "HAS", "requested", 0);
        }

        XC_MethodHook.MethodHookParam enter() throws Throwable {
            XC_MethodHook.MethodHookParam p = param(null, null, window.mInputWindowHandle, window);
            synchronized (service.mGlobalLock) { populate.callBefore(p); }
            return p;
        }

        void leave(XC_MethodHook.MethodHookParam p) throws Throwable {
            populate.callAfter(p);
        }

        void gates(boolean targetValue, boolean keyguardValue) throws Throwable {
            XC_MethodHook.MethodHookParam t = param(null, targetValue, window);
            target.callBefore(t); target.callAfter(t);
            XC_MethodHook.MethodHookParam k = param(service.mPolicy, keyguardValue);
            keyguard.callBefore(k); keyguard.callAfter(k);
        }

        XC_MethodHook.MethodHookParam getter(boolean original) throws Throwable {
            XC_MethodHook.MethodHookParam p = param(window.mAttrs, original);
            getter.callBefore(p); getter.callAfter(p);
            return p;
        }

        void has(boolean requested, boolean throwsException) throws Throwable {
            XC_MethodHook.MethodHookParam p = param(window.mInputWindowHandle, null, requested);
            has.callBefore(p);
            if (throwsException) p.setThrowable(new IllegalStateException("INJECTED_HAS_FAILURE"));
            else window.mInputWindowHandle.mHandle.inputConfig = requested ? 32 : 0;
            has.callAfter(p);
        }

        void recoveryPopulate(boolean original, boolean hasThrows) throws Throwable {
            XC_MethodHook.MethodHookParam p = enter();
            gates(true, false);
            getter(original);
            has(original, hasThrows);
            leave(p);
        }
    }

    @Test public void realPopulateBindsOnlyUniqueCertifiedHomeUnderWmLock() throws Throwable {
        Fixture f = new Fixture();
        XC_MethodHook.MethodHookParam populate = f.enter();
        assertSame(f.window, get(f.observer, "home"));
        assertSame(f.window.mAttrs, get(f.observer, "attrs"));
        assertSame(f.window.mInputWindowHandle, get(f.observer, "wrapper"));
        assertEquals(1, get(f.observer, "inFlight"));
        f.leave(populate);
        assertEquals(0, get(f.observer, "inFlight"));
        assertNull(local(f.observer, "frame").get());
    }

    @Test public void populateWithoutWmLockDoesNotCertifyOrCountHome() throws Throwable {
        Fixture f = new Fixture();
        XC_MethodHook.MethodHookParam p = param(null, null, f.window.mInputWindowHandle, f.window);
        f.populate.callBefore(p); f.populate.callAfter(p);
        assertNull(get(f.observer, "home"));
        assertEquals(0, get(f.observer, "inFlight"));
        assertEquals(0, f.contrast.appliedCount());
    }

    @Test public void multipleHomeWindowsForceRecoveryBeforeAnyOverride() throws Throwable {
        Fixture f = new Fixture();
        f.service.mWindowMap.put("SECOND", new Window());
        assertTrue(f.observer.enable(1_000));
        XC_MethodHook.MethodHookParam p = f.enter();
        assertEquals("RECOVERY", f.contrast.mode());
        assertEquals(Boolean.FALSE, f.getter(false).getResult());
        f.leave(p);
        assertEquals(0, f.contrast.appliedCount());
        assertTrue(f.contrast.needsRecovery());
    }

    @Test public void baselineGetterPreservesFalseAndOriginalField() throws Throwable {
        Fixture f = new Fixture();
        XC_MethodHook.MethodHookParam p = f.enter();
        f.gates(true, false);
        assertEquals(Boolean.FALSE, f.getter(false).getResult());
        assertFalse(f.window.mAttrs.mWallpaperTouchEventsEnabled);
        assertEquals(0, f.contrast.appliedCount());
        f.has(false, false); f.leave(p);
    }

    @Test public void enableChangesOnlyExactGetterFalseToTrueWithoutChangingField() throws Throwable {
        Fixture f = new Fixture();
        assertTrue(f.observer.enable(1_000));
        XC_MethodHook.MethodHookParam p = f.enter();
        f.gates(true, false);
        assertEquals(Boolean.TRUE, f.getter(false).getResult());
        assertFalse(f.window.mAttrs.mWallpaperTouchEventsEnabled);
        assertEquals(1, f.contrast.appliedCount());
        assertEquals(1, get(f.observer, "taintedInFlight"));
        f.has(true, false); f.leave(p);
        assertEquals(0, get(f.observer, "inFlight"));
        assertEquals(0, get(f.observer, "taintedInFlight"));
    }

    @Test public void targetFalseKeyguardTrueAndOriginalTrueNeverApplyOverride() throws Throwable {
        for (int branch = 0; branch < 3; branch++) {
            Fixture f = new Fixture();
            assertTrue(f.observer.enable(1_000));
            XC_MethodHook.MethodHookParam p = f.enter();
            f.gates(branch != 0, branch == 1);
            assertEquals(branch == 2, f.getter(branch == 2).getResult());
            assertEquals(0, f.contrast.appliedCount());
            f.leave(p);
        }
    }

    @Test public void unrelatedAttrsGetterAndMissingPopulateNeverApplyOverride() throws Throwable {
        Fixture f = new Fixture();
        assertTrue(f.observer.enable(1_000));
        assertEquals(Boolean.FALSE, f.getter(false).getResult());
        XC_MethodHook.MethodHookParam populate = f.enter(); f.gates(true, false);
        XC_MethodHook.MethodHookParam other = param(new Layout(), false);
        f.getter.callBefore(other); f.getter.callAfter(other);
        assertEquals(Boolean.FALSE, other.getResult());
        assertEquals(0, f.contrast.appliedCount());
        f.leave(populate);
    }

    @Test public void getterThrowableCannotBeErasedIntoSuccessfulOverride() throws Throwable {
        Fixture f = new Fixture();
        assertTrue(f.observer.enable(1_000));
        XC_MethodHook.MethodHookParam populate = f.enter(); f.gates(true, false);
        XC_MethodHook.MethodHookParam getter = param(f.window.mAttrs, false);
        Throwable original = new IllegalStateException("ORIGINAL_GETTER_FAILURE");
        getter.setThrowable(original);
        f.getter.callBefore(getter); f.getter.callAfter(getter);
        assertSame(original, getter.getThrowable());
        assertEquals(0, f.contrast.appliedCount());
        f.leave(populate);
    }

    @Test public void stopRevokesGetterBeforeNaturalRecoveryIsObserved() throws Throwable {
        Fixture f = new Fixture();
        assertTrue(f.observer.enable(1_000));
        f.observer.recover(1_001, "USER_STOP");
        SystemClock.setElapsedRealtime(1_001);
        XC_MethodHook.MethodHookParam populate = f.enter(); f.gates(true, false);
        assertEquals(Boolean.FALSE, f.getter(false).getResult());
        assertFalse(f.contrast.recoveryProved());
        f.has(false, false); f.leave(populate);
        assertTrue(f.contrast.recoveryProved());
        assertFalse(f.contrast.needsRecovery());
        assertTrue(f.observer.tick(1_002));
        assertTrue(f.observer.contrastStatus(1_002).contains("RECOVERY_CONFIRMED"));
    }

    @Test public void enabledPopulateCannotBecomeRecoveryProofMerelyByFinishingAfterStop() throws Throwable {
        Fixture f = new Fixture();
        assertTrue(f.observer.enable(1_000));
        XC_MethodHook.MethodHookParam populate = f.enter();
        f.gates(true, false); assertEquals(Boolean.TRUE, f.getter(false).getResult());
        f.observer.recover(1_001, "USER_STOP"); SystemClock.setElapsedRealtime(1_001);
        f.has(false, false); f.leave(populate);
        assertFalse(f.contrast.recoveryProved());
        assertTrue(f.contrast.needsRecovery());
        f.recoveryPopulate(false, false);
        assertTrue(f.contrast.recoveryProved());
    }

    @Test public void successfulHasAndDupFalseAreRequiredForNaturalRecoveryProof() throws Throwable {
        Fixture f = new Fixture();
        f.observer.recover(1_000, "USER_STOP");
        f.recoveryPopulate(false, true);
        assertFalse(f.contrast.recoveryProved());
        assertTrue(f.contrast.needsRecovery());
        f.recoveryPopulate(false, false);
        assertTrue(f.contrast.recoveryProved());
    }

    @Test public void missingHasCallCannotServeAsProofEvenWhenDupWasAlreadyFalse() throws Throwable {
        Fixture f = new Fixture();
        f.observer.recover(1_000, "USER_STOP");
        XC_MethodHook.MethodHookParam p = f.enter();
        f.gates(true, false); f.getter(false); f.leave(p);
        assertFalse(f.contrast.recoveryProved());
        assertTrue(f.contrast.needsRecovery());
    }

    @Test public void laterOriginalGetterTrueWithdrawsEarlierRecoveryProof() throws Throwable {
        Fixture f = new Fixture();
        f.observer.recover(1_000, "USER_STOP");
        f.recoveryPopulate(false, false);
        assertTrue(f.contrast.recoveryProved());
        f.recoveryPopulate(true, false);
        assertFalse(f.contrast.recoveryProved());
        assertTrue(f.contrast.needsRecovery());
    }

    @Test public void laterHasFailureWithdrawsEarlierRecoveryProof() throws Throwable {
        Fixture f = new Fixture();
        f.observer.recover(1_000, "USER_STOP");
        f.recoveryPopulate(false, false);
        assertTrue(f.contrast.recoveryProved());
        f.recoveryPopulate(false, true);
        assertFalse(f.contrast.recoveryProved());
    }

    @Test public void identityGenerationChangeDuringPopulateCannotProveRecovery() throws Throwable {
        Fixture f = new Fixture();
        f.observer.recover(1_000, "USER_STOP");
        XC_MethodHook.MethodHookParam p = f.enter(); f.gates(true, false); f.getter(false);
        f.observer.recover(1_001, "IDENTITY_CHANGED"); SystemClock.setElapsedRealtime(1_001);
        f.has(false, false); f.leave(p);
        assertFalse(f.contrast.recoveryProved());
    }

    @Test public void oldEpochAfterPreservesNewEpochFrameAndRequest() throws Throwable {
        Fixture f = new Fixture();
        XC_MethodHook old = guarded(f.observer, new XC_MethodHook() { });
        XC_MethodHook.MethodHookParam p = param(null, null);
        old.callBefore(p);
        long epoch = ((SessionEpoch) get(f.observer, "sessions")).advance();
        Object currentFrame = nested(f.observer, "Frame"); set(currentFrame, "epoch", epoch);
        Object currentRequest = nested(f.observer, "Request"); set(currentRequest, "epoch", epoch);
        local(f.observer, "frame").set(currentFrame);
        local(f.observer, "request").set(currentRequest);
        old.callAfter(p);
        assertSame(currentFrame, local(f.observer, "frame").get());
        assertSame(currentRequest, local(f.observer, "request").get());
    }

    @Test public void beforeFailureStillReleasesActualPopulateCountersAndContext() throws Throwable {
        Fixture f = new Fixture();
        XC_MethodHook.MethodHookParam p = param(null, null, f.window.mInputWindowHandle, f.window);
        SystemClock.failOnceAfterReads(1);
        synchronized (f.service.mGlobalLock) { f.populate.callBefore(p); }
        assertEquals("RECOVERY", f.contrast.mode());
        f.populate.callAfter(p);
        assertEquals(0, get(f.observer, "inFlight"));
        assertEquals(0, get(f.observer, "taintedInFlight"));
        assertNull(local(f.observer, "frame").get());
    }

    @Test public void getterPartialSetResultFailureRollsBackAndCannotProveRecovery() throws Throwable {
        Fixture f = new Fixture();
        assertTrue(f.observer.enable(1_000));
        XC_MethodHook.MethodHookParam p = f.enter(); f.gates(true, false);
        XC_MethodHook.MethodHookParam getter = param(f.window.mAttrs, false);
        getter.throwAfterTrueResult = true;
        f.getter.callBefore(getter); f.getter.callAfter(getter);
        assertEquals(Boolean.FALSE, getter.getResult());
        assertFalse(f.window.mAttrs.mWallpaperTouchEventsEnabled);
        assertEquals("RECOVERY", f.contrast.mode());
        assertFalse(f.contrast.recoveryProved());
        f.leave(p);
        assertEquals(0, get(f.observer, "inFlight"));
        assertEquals(0, get(f.observer, "taintedInFlight"));
    }

    @Test public void hardDeadlineRevokesEvenAnAlreadyEnteredGetter() throws Throwable {
        Fixture f = new Fixture();
        assertTrue(f.observer.enable(1_000));
        XC_MethodHook.MethodHookParam p = f.enter(); f.gates(true, false);
        SystemClock.setElapsedRealtime(181_000);
        assertEquals(Boolean.FALSE, f.getter(false).getResult());
        assertEquals("RECOVERY", f.contrast.mode());
        assertEquals(0, f.contrast.appliedCount());
        f.has(false, false); f.leave(p);
        assertFalse(f.contrast.recoveryProved());
    }

    @Test public void totalTwoHundredSecondsEndsUnconfirmedWithoutNaturalProof() throws Throwable {
        Fixture f = new Fixture();
        assertTrue(f.observer.enable(1_000));
        SystemClock.setElapsedRealtime(181_000);
        assertFalse(f.observer.tick(181_000));
        assertEquals("RECOVERY", f.contrast.mode());
        SystemClock.setElapsedRealtime(201_000);
        assertTrue(f.observer.tick(201_000));
        assertFalse(f.observer.canStart());
        assertTrue(f.observer.contrastStatus(201_000).contains("RECOVERY_UNCONFIRMED"));
    }

    @Test public void outstandingPopulateAtRecoveryDeadlineCannotClaimConfirmed() throws Throwable {
        Fixture f = new Fixture();
        f.observer.recover(1_000, "USER_STOP");
        f.recoveryPopulate(false, false); assertTrue(f.contrast.recoveryProved());
        XC_MethodHook.MethodHookParam pending = f.enter();
        SystemClock.setElapsedRealtime(21_000);
        assertTrue(f.observer.tick(21_000));
        assertTrue(f.contrast.needsRecovery());
        assertFalse(f.contrast.recoveryProved());
        assertTrue(f.observer.contrastStatus(21_000).contains("RECOVERY_UNCONFIRMED"));
        f.leave(pending);
    }

    @Test public void stopIsSerializedWithRealGetterOverrideAndThenRejectsNewGetter() throws Throwable {
        Fixture f = new Fixture();
        assertTrue(f.observer.enable(1_000));
        CountDownLatch overrideEntered = new CountDownLatch(1);
        CountDownLatch letOverrideFinish = new CountDownLatch(1);
        CountDownLatch stopEntered = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> override = executor.submit(() -> {
                try {
                    XC_MethodHook.MethodHookParam p = f.enter(); f.gates(true, false);
                    XC_MethodHook.MethodHookParam getter = param(f.window.mAttrs, false);
                    getter.resultObserver = result -> {
                        if (Boolean.TRUE.equals(result)) {
                            overrideEntered.countDown();
                            try { assertTrue(letOverrideFinish.await(5, TimeUnit.SECONDS)); }
                            catch (InterruptedException e) { throw new AssertionError(e); }
                        }
                    };
                    f.getter.callBefore(getter); f.getter.callAfter(getter);
                    assertEquals(Boolean.TRUE, getter.getResult());
                    f.has(true, false); f.leave(p);
                } catch (Throwable t) { throw new AssertionError(t); }
            });
            assertTrue(overrideEntered.await(5, TimeUnit.SECONDS));
            Future<?> stop = executor.submit(() -> {
                stopEntered.countDown();
                f.observer.recover(1_001, "USER_STOP");
            });
            assertTrue(stopEntered.await(5, TimeUnit.SECONDS));
            assertFalse(stop.isDone());
            letOverrideFinish.countDown();
            override.get(5, TimeUnit.SECONDS); stop.get(5, TimeUnit.SECONDS);
            SystemClock.setElapsedRealtime(1_001);
            XC_MethodHook.MethodHookParam p = f.enter(); f.gates(true, false);
            assertEquals(Boolean.FALSE, f.getter(false).getResult());
            f.has(false, false); f.leave(p);
            assertEquals(1, f.contrast.appliedCount());
        } finally {
            letOverrideFinish.countDown(); executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test public void oldRealPopulateAfterCannotCloseOrRemoveNewExperimentContext() throws Throwable {
        Fixture f = new Fixture();
        XC_MethodHook.MethodHookParam oldPopulate = f.enter();
        long next = ((SessionEpoch) get(f.observer, "sessions")).advance();
        Object newFrame = nested(f.observer, "Frame");
        set(newFrame, "epoch", next); set(newFrame, "window", f.window);
        Object newRequest = nested(f.observer, "Request"); set(newRequest, "epoch", next);
        local(f.observer, "frame").set(newFrame); local(f.observer, "request").set(newRequest);
        set(f.observer, "inFlight", 1);
        f.leave(oldPopulate);
        assertSame(newFrame, local(f.observer, "frame").get());
        assertSame(newRequest, local(f.observer, "request").get());
        assertEquals(1, get(f.observer, "inFlight"));
        assertEquals(Boolean.FALSE, get(newFrame, "closed"));
    }

    @Test public void recoveryPopulateEnteredWhileTaintedFrameExistsCannotProveRecovery() throws Throwable {
        Fixture f = new Fixture();
        assertTrue(f.observer.enable(1_000));
        XC_MethodHook.MethodHookParam outer = f.enter(); f.gates(true, false);
        assertEquals(Boolean.TRUE, f.getter(false).getResult());
        f.observer.recover(1_001, "USER_STOP"); SystemClock.setElapsedRealtime(1_001);
        f.recoveryPopulate(false, false);
        assertFalse(f.contrast.recoveryProved());
        assertEquals(1, get(f.observer, "taintedInFlight"));
        f.has(false, false); f.leave(outer);
        assertFalse(f.contrast.recoveryProved());
        assertEquals(0, get(f.observer, "inFlight"));
        f.recoveryPopulate(false, false);
        assertTrue(f.contrast.recoveryProved());
    }

    @Test public void attrsReplacementInsideBoundPopulateRevokesExperiment() throws Throwable {
        Fixture f = new Fixture();
        assertTrue(f.observer.enable(1_000));
        XC_MethodHook.MethodHookParam p = f.enter(); f.gates(true, false);
        Layout original = f.window.mAttrs;
        f.window.mAttrs = new Layout();
        XC_MethodHook.MethodHookParam getter = param(original, false);
        f.getter.callBefore(getter); f.getter.callAfter(getter);
        assertEquals(Boolean.FALSE, getter.getResult());
        assertEquals("RECOVERY", f.contrast.mode());
        assertTrue(f.contrast.needsRecovery());
        assertEquals(0, f.contrast.appliedCount());
        f.leave(p);
    }

    @Test public void identityPackageChangeInsideBoundPopulateRevokesExperiment() throws Throwable {
        Fixture f = new Fixture();
        assertTrue(f.observer.enable(1_000));
        XC_MethodHook.MethodHookParam p = f.enter(); f.gates(true, false);
        f.window.mAttrs.packageName = "unrelated.package";
        assertEquals(Boolean.FALSE, f.getter(false).getResult());
        assertEquals("RECOVERY", f.contrast.mode());
        assertEquals(0, f.contrast.appliedCount());
        f.leave(p);
    }

    @Test public void lateWatchdogDoesNotExtendRecoveryPastTotalTwoHundredSeconds() throws Throwable {
        Fixture f = new Fixture();
        assertTrue(f.observer.enable(1_000));
        SystemClock.setElapsedRealtime(210_000);
        assertTrue(f.observer.tick(210_000));
        assertEquals("DISARMED", f.contrast.mode());
        assertTrue(f.contrast.needsRecovery());
        assertTrue(f.observer.contrastStatus(210_000).contains("RECOVERY_UNCONFIRMED"));
    }

    @Test public void completedPopulateAfterHookIsSafeWhenUnexpectedlyRepeated() throws Throwable {
        Fixture f = new Fixture();
        XC_MethodHook.MethodHookParam p = f.enter(); f.leave(p);
        assertEquals(0, get(f.observer, "inFlight"));
        f.leave(p);
        assertEquals(0, get(f.observer, "inFlight"));
        assertEquals(0, get(f.observer, "taintedInFlight"));
        assertNull(local(f.observer, "frame").get());
    }

    @Test public void observedBooleanFailureOrUnknownWithdrawsOldProofEvenIfOuterCallSwallowsIt() throws Throwable {
        for (String callback : new String[]{"TARGET", "KEYGUARD", "GETTER"}) {
            for (boolean throwsException : new boolean[]{true, false}) {
                Fixture f = new Fixture();
                f.observer.recover(1_000, "USER_STOP");
                f.recoveryPopulate(false, false);
                assertTrue(f.contrast.recoveryProved());
                XC_MethodHook.MethodHookParam populate = f.enter();
                XC_MethodHook hook;
                XC_MethodHook.MethodHookParam result;
                switch (callback) {
                    case "TARGET":
                        hook = f.target;
                        result = param(null, true, f.window);
                        break;
                    case "KEYGUARD":
                        hook = f.keyguard;
                        result = param(f.service.mPolicy, false);
                        break;
                    default:
                        hook = f.getter;
                        result = param(f.window.mAttrs, false);
                }
                Throwable original = new IllegalStateException("INJECTED_BOOLEAN_FAILURE");
                if (throwsException) result.setThrowable(original);
                else result.setResult("UNKNOWN");
                hook.callBefore(result); hook.callAfter(result);
                assertEquals(callback, "RECOVERY", f.contrast.mode());
                assertFalse(callback, f.contrast.recoveryProved());
                assertTrue(callback, f.contrast.needsRecovery());
                if (throwsException) assertSame(original, result.getThrowable());
                else assertEquals("UNKNOWN", result.getResult());
                // 最外调用不报告 Throwable，模拟调用方或另一模块吞掉错误。
                f.leave(populate);
                assertFalse(callback, f.contrast.recoveryProved());
                assertEquals(0, get(f.observer, "inFlight"));
            }
        }
    }
}
