package dev.afdo.lineagepillcts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;

import java.util.Arrays;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;

final class ContextualSearchPermissionHook {
    private static final String CONTEXTUAL_SEARCH_MANAGER_SERVICE =
            "com.android.server.contextualsearch.ContextualSearchManagerService";
    private static final Set<String> ALLOWED_CALLER_PACKAGES = new HashSet<>(Arrays.asList(
            HookEntry.SYSTEM_UI_PACKAGE,
            HookEntry.PIXEL_LAUNCHER_PACKAGE,
            HookEntry.LAUNCHER3_PACKAGE
    ));

    private ContextualSearchPermissionHook() {
    }

    static void install(ClassLoader classLoader) {
        Class<?> serviceClass =
                HookUtils.findClassIfExists(CONTEXTUAL_SEARCH_MANAGER_SERVICE, classLoader);
        if (serviceClass == null) {
            HookLogger.i("ContextualSearchManagerService not found in system_server");
            return;
        }

        boolean hooked = HookUtils.hookAllMethodsIfExists(
                serviceClass,
                "enforcePermission",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args == null
                                || param.args.length == 0
                                || !"startContextualSearch".equals(param.args[0])) {
                            return;
                        }
                        if (isAllowedCaller(param.thisObject, Binder.getCallingUid())) {
                            param.setResult(null);
                        }
                    }
                });
        HookLogger.i("System Framework contextual search permission hook installed: " + hooked);
    }

    private static boolean isAllowedCaller(Object service, int uid) {
        Context context = getServiceContext(service);
        if (context == null) {
            return false;
        }

        try {
            PackageManager packageManager = context.getPackageManager();
            String[] packages = packageManager.getPackagesForUid(uid);
            if (packages == null) {
                return false;
            }
            for (String packageName : packages) {
                if (ALLOWED_CALLER_PACKAGES.contains(packageName)) {
                    return true;
                }
            }
        } catch (Throwable throwable) {
            HookLogger.w("Could not resolve caller uid " + uid, throwable);
        }
        return false;
    }

    private static Context getServiceContext(Object service) {
        Object context = HookUtils.callMethodOrNull(service, "getContext");
        if (context instanceof Context) {
            return (Context) context;
        }

        if (service == null) {
            return null;
        }
        Class<?> serviceClass = service.getClass();
        while (serviceClass != null) {
            for (Field field : serviceClass.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    context = field.get(service);
                    if (context instanceof Context) {
                        return (Context) context;
                    }
                } catch (Throwable ignored) {
                }
            }
            serviceClass = serviceClass.getSuperclass();
        }
        return null;
    }
}
