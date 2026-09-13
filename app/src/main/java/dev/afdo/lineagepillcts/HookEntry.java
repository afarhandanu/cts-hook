package dev.afdo.lineagepillcts;

import android.os.Build;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookEntry implements IXposedHookLoadPackage {
    static final String SYSTEM_FRAMEWORK_PACKAGE = "android";
    static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    static final String PIXEL_LAUNCHER_PACKAGE = "com.google.android.apps.nexuslauncher";
    static final String LAUNCHER3_PACKAGE = "com.android.launcher3";

    private static final int ANDROID_15 = 35;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (loadPackageParam == null || loadPackageParam.packageName == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < ANDROID_15) {
            return;
        }

        String packageName = loadPackageParam.packageName;
        ClassLoader classLoader = loadPackageParam.classLoader;
        try {
            if (SYSTEM_FRAMEWORK_PACKAGE.equals(packageName)) {
                ContextualSearchPermissionHook.install(classLoader);
            } else if (SYSTEM_UI_PACKAGE.equals(packageName)) {
                SystemUiNavHandleHook.install(classLoader);
            } else if (isLauncherPackage(packageName)) {
                QuickstepNavHandleHook.install(classLoader, packageName);
            }
        } catch (Throwable throwable) {
            HookLogger.w("Failed while installing hooks for " + packageName, throwable);
        }
    }

    static boolean isLauncherPackage(String packageName) {
        return PIXEL_LAUNCHER_PACKAGE.equals(packageName)
                || LAUNCHER3_PACKAGE.equals(packageName);
    }
}
