package de.robv.android.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** 只在测试中用 Java 反射提供与被测适配器所用 API 相同的查找语义。 */
public final class XposedHelpers {
    private XposedHelpers() { }
    public static Object getObjectField(Object object, String name) {
        if (object == null) throw new NullPointerException("NULL_TEST_OBJECT");
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException absent) {
                // 与实际辅助 API 一样沿继承链查找。
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("TEST_FIELD_FAILED", failure);
            }
        }
        throw new IllegalStateException("TEST_FIELD_MISSING");
    }
    public static Object callMethod(Object object, String name, Object... args) {
        if (object == null) throw new NullPointerException("NULL_TEST_OBJECT");
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                try {
                    method.setAccessible(true);
                    return method.invoke(object, args);
                } catch (InvocationTargetException failure) {
                    throw new IllegalStateException("TEST_METHOD_THROW", failure.getCause());
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("TEST_METHOD_FAILED", failure);
                }
            }
        }
        throw new IllegalStateException("TEST_METHOD_MISSING");
    }
}
