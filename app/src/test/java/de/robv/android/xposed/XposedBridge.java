package de.robv.android.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 仅记录测试夹具 hook 句柄；没有原生 hook 或 Android 进程操作。 */
public final class XposedBridge {
    public static final ClassLoader BOOTCLASSLOADER = XposedBridge.class.getClassLoader();
    private static final Map<Member, XC_MethodHook> hooks = new LinkedHashMap<>();
    private XposedBridge() { }
    public static synchronized XC_MethodHook.Unhook hookMethod(Member member, XC_MethodHook hook) {
        hooks.put(member, hook);
        return hook.new Unhook(member);
    }
    public static synchronized void unhookMethod(Member member, XC_MethodHook hook) {
        hooks.remove(member, hook);
    }
    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> type, String name, XC_MethodHook hook) {
        Set<XC_MethodHook.Unhook> result = new HashSet<>();
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) result.add(hookMethod(method, hook));
        }
        return result;
    }
    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> type, XC_MethodHook hook) {
        Set<XC_MethodHook.Unhook> result = new HashSet<>();
        for (Constructor<?> constructor : type.getDeclaredConstructors()) result.add(hookMethod(constructor, hook));
        return result;
    }
    public static void log(String ignored) { }
    public static void log(Throwable ignored) { }
}
