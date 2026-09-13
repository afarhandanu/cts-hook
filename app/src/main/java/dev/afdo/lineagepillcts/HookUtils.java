package dev.afdo.lineagepillcts;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class HookUtils {
    private HookUtils() {
    }

    static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        try {
            if (classLoader == null) {
                return Class.forName(className);
            }
            return XposedHelpers.findClass(className, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean hookAllMethodsIfExists(
            Class<?> hookClass,
            String methodName,
            XC_MethodHook callback) {
        if (hookClass == null) {
            return false;
        }
        try {
            XposedBridge.hookAllMethods(hookClass, methodName, callback);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    static boolean hookDeclaredMethodsIf(
            Class<?> hookClass,
            MethodPredicate predicate,
            XC_MethodHook callback) {
        if (hookClass == null) {
            return false;
        }
        boolean hooked = false;
        for (Method method : hookClass.getDeclaredMethods()) {
            if (!predicate.matches(method)) {
                continue;
            }
            try {
                XposedBridge.hookMethod(method, callback);
                hooked = true;
            } catch (Throwable ignored) {
            }
        }
        return hooked;
    }

    static Object callMethodOrNull(Object object, String methodName, Object... args) {
        if (object == null) {
            return null;
        }
        try {
            return XposedHelpers.callMethod(object, methodName, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Object getObjectFieldOrNull(Object object, String fieldName) {
        if (object == null) {
            return null;
        }
        try {
            return XposedHelpers.getObjectField(object, fieldName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    interface MethodPredicate {
        boolean matches(Method method);
    }
}
