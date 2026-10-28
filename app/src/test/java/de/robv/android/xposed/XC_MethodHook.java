package de.robv.android.xposed;

import java.lang.reflect.Member;
import java.util.function.Consumer;

/** 仅测试替身：模拟结果／异常互斥和成对 hook 调用，不模拟 ART 安装。 */
public abstract class XC_MethodHook {
    public final int priority;
    public XC_MethodHook() { this(50); }
    public XC_MethodHook(int priority) { this.priority=priority; }
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable { }
    protected void afterHookedMethod(MethodHookParam param) throws Throwable { }
    public final void callBefore(MethodHookParam param) throws Throwable { beforeHookedMethod(param); }
    public final void callAfter(MethodHookParam param) throws Throwable { afterHookedMethod(param); }

    public static final class MethodHookParam {
        public Object[] args = new Object[0];
        public Member method;
        public Object thisObject;
        private Object result;
        private Throwable throwable;
        public Consumer<Object> resultObserver;
        public boolean throwAfterTrueResult;
        public Object getResult() { return result; }
        public void setResult(Object value) {
            result = value;
            throwable = null;
            if (resultObserver != null) resultObserver.accept(value);
            if (Boolean.TRUE.equals(value) && throwAfterTrueResult) {
                throw new IllegalStateException("INJECTED_RESULT_FAILURE");
            }
        }
        public Throwable getThrowable() { return throwable; }
        public boolean hasThrowable() { return throwable != null; }
        public void setThrowable(Throwable value) { throwable = value; result = null; }
        public Object getResultOrThrowable() throws Throwable {
            if (throwable != null) throw throwable;
            return result;
        }
    }

    public class Unhook {
        private final Member member;
        public Unhook(Member member) { this.member = member; }
        public Member getHookedMethod() { return member; }
        public XC_MethodHook getCallback() { return XC_MethodHook.this; }
        public void unhook() { XposedBridge.unhookMethod(member, XC_MethodHook.this); }
    }
}
