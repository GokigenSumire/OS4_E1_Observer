package personal.wallpaper.os4.e1;

import android.content.Context;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.Set;
import java.lang.reflect.Constructor;
import personal.wallpaper.os4.e1.lockinput.LockInputBroker;
import personal.wallpaper.os4.e1.lockinput.LockSystemUiObserver;

/** HOME限系统服务进程；LOCK按系统组件来源和已知接口族，两个开关独立。 */
public final class ObserverEntry implements IXposedHookLoadPackage {
    private static boolean bootstrapped;
    private static boolean systemUiBootstrapped;
    @Override public synchronized void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) {
        if ("com.android.systemui".equals(load.packageName) && "com.android.systemui".equals(load.processName)
                && !systemUiBootstrapped) {
            systemUiBootstrapped = true;
            LockSystemUiObserver.install(load.classLoader, load.appInfo);
            return;
        }
        if (!"android".equals(load.packageName) || !"android".equals(load.processName) || bootstrapped) return;
        bootstrapped = true;
        LockInputBroker.install(load.classLoader);
        try {
            Class<?> wms = XposedHelpers.findClass("com.android.server.wm.WindowManagerService", load.classLoader);
            final ControlServer server = new ControlServer(load.classLoader);
            Constructor<?> constructor=HomeBootstrapContract.select(wms);
            final Set<XC_MethodHook.Unhook> hooks = Set.of(XposedBridge.hookMethod(constructor, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) return;
                    try {
                        Object ctx = XposedHelpers.getObjectField(param.thisObject, "mContext");
                        if (param.thisObject.getClass()==wms&&ctx instanceof Context&&param.args!=null&&param.args.length==9&&ctx==param.args[0])server.bind((Context)ctx,param.thisObject);
                    } catch (Throwable ignored) { XposedBridge.log("OS4_E1_BOOTSTRAP_UNAVAILABLE"); }
                }
            }));
            server.setBootstrapHooks(hooks);
        } catch (Throwable ignored) { XposedBridge.log("OS4_E1_BOOTSTRAP_FAILED"); }
    }
}
