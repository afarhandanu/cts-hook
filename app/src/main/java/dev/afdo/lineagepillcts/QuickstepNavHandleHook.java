package dev.afdo.lineagepillcts;

import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;

final class QuickstepNavHandleHook {
    private static final String NAV_HANDLE_LONG_PRESS_HANDLER =
            "com.android.quickstep.inputconsumers.NavHandleLongPressHandler";
    private static final String CONTEXTUAL_SEARCH_INVOKER =
            "com.android.quickstep.util.ContextualSearchInvoker";

    private QuickstepNavHandleHook() {
    }

    static void install(ClassLoader classLoader, String packageName) {
        Class<?> handlerClass = HookUtils.findClassIfExists(NAV_HANDLE_LONG_PRESS_HANDLER, classLoader);
        if (handlerClass == null) {
            HookLogger.i("Quickstep nav handle handler not found in " + packageName);
            return;
        }

        boolean hookedGate = hookFeatureGate(handlerClass);
        boolean hookedRunnableFallback = hookRunnableFallback(handlerClass);
        HookLogger.i("Quickstep hooks for " + packageName
                + ": featureGate=" + hookedGate
                + ", fallbackRunnable=" + hookedRunnableFallback);
    }

    private static boolean hookFeatureGate(Class<?> handlerClass) {
        return HookUtils.hookDeclaredMethodsIf(
                handlerClass,
                method -> "isContextualSearchEntrypointEnabled".equals(method.getName())
                        && method.getReturnType() == boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(true);
                    }
                });
    }

    private static boolean hookRunnableFallback(Class<?> handlerClass) {
        return HookUtils.hookDeclaredMethodsIf(
                handlerClass,
                method -> "getLongPressRunnable".equals(method.getName())
                        && Runnable.class.isAssignableFrom(method.getReturnType()),
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.getResult() != null) {
                            return;
                        }

                        Object invoker = findFieldValue(
                                param.thisObject,
                                "mContextualSearchInvoker",
                                CONTEXTUAL_SEARCH_INVOKER);
                        if (!launcherChecksPass(invoker)) {
                            return;
                        }

                        Context context = findContext(param.thisObject);
                        param.setResult((Runnable) () -> {
                            if (!invokeThroughLauncherInvoker(invoker)) {
                                ContextualSearchStarter.start(context);
                            }
                        });
                    }
                });
    }

    private static boolean launcherChecksPass(Object invoker) {
        Object result = HookUtils.callMethodOrNull(
                invoker,
                "runContextualSearchInvocationChecksAndLogFailures");
        return Boolean.TRUE.equals(result);
    }

    private static boolean invokeThroughLauncherInvoker(Object invoker) {
        Object result = HookUtils.callMethodOrNull(
                invoker,
                "invokeContextualSearchUncheckedWithHaptic",
                ContextualSearchStarter.ENTRYPOINT_LONG_PRESS_NAV_HANDLE);
        if (Boolean.TRUE.equals(result)) {
            return true;
        }

        result = HookUtils.callMethodOrNull(
                invoker,
                "show",
                ContextualSearchStarter.ENTRYPOINT_LONG_PRESS_NAV_HANDLE);
        return Boolean.TRUE.equals(result);
    }

    private static Context findContext(Object owner) {
        Object namedContext = HookUtils.getObjectFieldOrNull(owner, "mContext");
        if (namedContext instanceof Context) {
            return (Context) namedContext;
        }

        if (owner == null) {
            return null;
        }
        Class<?> ownerClass = owner.getClass();
        while (ownerClass != null) {
            for (Field field : ownerClass.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(owner);
                    if (value instanceof Context) {
                        return (Context) value;
                    }
                } catch (Throwable ignored) {
                }
            }
            ownerClass = ownerClass.getSuperclass();
        }
        return null;
    }

    private static Object findFieldValue(Object owner, String preferredName, String expectedClassName) {
        Object value = HookUtils.getObjectFieldOrNull(owner, preferredName);
        if (hasClassName(value, expectedClassName)) {
            return value;
        }

        if (owner == null) {
            return null;
        }
        Class<?> ownerClass = owner.getClass();
        while (ownerClass != null) {
            for (Field field : ownerClass.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    value = field.get(owner);
                    if (hasClassName(value, expectedClassName)) {
                        return value;
                    }
                } catch (Throwable ignored) {
                }
            }
            ownerClass = ownerClass.getSuperclass();
        }
        return null;
    }

    private static boolean hasClassName(Object object, String className) {
        return object != null && className.equals(object.getClass().getName());
    }
}
