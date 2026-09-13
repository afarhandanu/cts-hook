package dev.afdo.lineagepillcts;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class QuickstepFeatureGateHook {
    private static final String FEATURE_FLAGS_CLASS =
            "com.android.launcher3.config.FeatureFlags";
    private static final String LONG_PRESS_FLAG = "ENABLE_LONG_PRESS_NAV_HANDLE";
    private static final String SYSTEM_UI_PROXY = "com.android.quickstep.SystemUiProxy";
    private static final String[] DEVICE_CONFIG_CLASSES = {
            "com.android.quickstep.DeviceConfigWrapper",
            "com.android.launcher3.util.DeviceConfigWrapper"
    };

    private QuickstepFeatureGateHook() {
    }

    static void install(ClassLoader classLoader, String packageName) {
        boolean legacyFlag = hookLegacyFeatureFlag(classLoader);
        boolean deviceConfig = hookDeviceConfigGate(classLoader);
        boolean navHandle = hookNavHandleCapability(classLoader);
        HookLogger.i("Quickstep input gates for " + packageName
                + ": legacyFlag=" + legacyFlag
                + ", deviceConfig=" + deviceConfig
                + ", navHandle=" + navHandle);
    }

    private static boolean hookLegacyFeatureFlag(ClassLoader classLoader) {
        Class<?> featureFlags = HookUtils.findClassIfExists(FEATURE_FLAGS_CLASS, classLoader);
        if (featureFlags == null) {
            return false;
        }

        XC_MethodHook factoryHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!containsFlagName(param.args)) {
                    return;
                }
                for (int i = 0; i < param.args.length; i++) {
                    Object enabled = enabledEquivalent(param.args[i]);
                    if (enabled != null) {
                        param.args[i] = enabled;
                    }
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!containsFlagName(param.args)) {
                    return;
                }
                Object enabled = enabledEquivalent(param.getResult());
                if (enabled != null) {
                    param.setResult(enabled);
                }
            }
        };

        boolean factoryHooked = HookUtils.hookDeclaredMethodsIf(
                featureFlags,
                method -> ("getReleaseFlag".equals(method.getName())
                        || "getDebugFlag".equals(method.getName()))
                        && method.getReturnType() != void.class,
                factoryHook);

        try {
            Class.forName(FEATURE_FLAGS_CLASS, true, classLoader);
        } catch (Throwable throwable) {
            HookLogger.w("Could not initialize Launcher3 feature flags", throwable);
        }

        return forceFeatureFlagField(featureFlags) || factoryHooked;
    }

    private static boolean forceFeatureFlagField(Class<?> featureFlags) {
        try {
            Field field = featureFlags.getDeclaredField(LONG_PRESS_FLAG);
            field.setAccessible(true);
            if (field.getType() == boolean.class) {
                XposedHelpers.setStaticBooleanField(featureFlags, LONG_PRESS_FLAG, true);
                return true;
            }

            Object current = field.get(null);
            Object enabled = enabledEquivalent(current);
            if (enabled != null) {
                XposedHelpers.setStaticObjectField(featureFlags, LONG_PRESS_FLAG, enabled);
                return true;
            }

            // Non-enum flag implementations normally have one object per flag.
            return current != null
                    && !current.getClass().isEnum()
                    && hookSpecificBooleanGetter(current);
        } catch (NoSuchFieldException ignored) {
            return false;
        } catch (Throwable throwable) {
            HookLogger.w("Could not force Launcher3 long-press feature flag", throwable);
            return false;
        }
    }

    private static boolean hookSpecificBooleanGetter(Object flagObject) {
        Class<?> flagClass = flagObject.getClass();
        while (flagClass != null) {
            for (Method method : flagClass.getDeclaredMethods()) {
                if (!"get".equals(method.getName())
                        || method.getParameterTypes().length != 0
                        || (method.getReturnType() != boolean.class
                        && method.getReturnType() != Boolean.class)) {
                    continue;
                }
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.thisObject == flagObject) {
                                param.setResult(true);
                            }
                        }
                    });
                    return true;
                } catch (Throwable throwable) {
                    HookLogger.w("Could not hook Launcher3 flag getter", throwable);
                    return false;
                }
            }
            flagClass = flagClass.getSuperclass();
        }
        return false;
    }

    private static boolean hookDeviceConfigGate(ClassLoader classLoader) {
        boolean hooked = false;
        for (String className : DEVICE_CONFIG_CLASSES) {
            Class<?> configClass = HookUtils.findClassIfExists(className, classLoader);
            if (configClass == null) {
                continue;
            }
            hooked |= HookUtils.hookDeclaredMethodsIf(
                    configClass,
                    method -> ("getEnableLongPressNavHandle".equals(method.getName())
                            || "isLongPressNavHandleEnabled".equals(method.getName()))
                            && (method.getReturnType() == boolean.class
                            || method.getReturnType() == Boolean.class),
                    forceTrue());
        }
        return hooked;
    }

    private static boolean hookNavHandleCapability(ClassLoader classLoader) {
        Class<?> proxyClass = HookUtils.findClassIfExists(SYSTEM_UI_PROXY, classLoader);
        return HookUtils.hookDeclaredMethodsIf(
                proxyClass,
                method -> "canNavHandleBeLongPressed".equals(method.getName())
                        && method.getParameterTypes().length == 0
                        && (method.getReturnType() == boolean.class
                        || method.getReturnType() == Boolean.class),
                forceTrue());
    }

    private static XC_MethodHook forceTrue() {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(true);
            }
        };
    }

    private static boolean containsFlagName(Object[] args) {
        if (args == null) {
            return false;
        }
        for (Object arg : args) {
            if (LONG_PRESS_FLAG.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static Object enabledEquivalent(Object value) {
        if (value instanceof Boolean) {
            return Boolean.TRUE;
        }
        if (value == null || !value.getClass().isEnum()) {
            return null;
        }
        Object[] constants = value.getClass().getEnumConstants();
        if (constants == null) {
            return null;
        }
        for (Object constant : constants) {
            String name = ((Enum<?>) constant).name();
            if ("ENABLED".equals(name) || "TRUE".equals(name)) {
                return constant;
            }
        }
        return null;
    }
}
