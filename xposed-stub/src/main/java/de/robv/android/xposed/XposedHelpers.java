package de.robv.android.xposed;

public final class XposedHelpers {
    private XposedHelpers() {
    }

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        return null;
    }

    public static XC_MethodHook.Unhook findAndHookMethod(
            Class<?> hookClass,
            String methodName,
            Object... parameterTypesAndCallback) {
        return null;
    }

    public static Object callMethod(Object object, String methodName, Object... args) {
        return null;
    }

    public static Object getObjectField(Object object, String fieldName) {
        return null;
    }
}
