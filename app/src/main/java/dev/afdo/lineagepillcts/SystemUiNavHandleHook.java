package dev.afdo.lineagepillcts;

import android.content.res.Resources;
import android.view.HapticFeedbackConstants;
import android.view.View;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

final class SystemUiNavHandleHook {
    private static final String[] NAV_HANDLE_CLASSES = {
            "com.android.systemui.navigationbar.gestural.NavigationHandle",
            "com.android.systemui.navigationbar.gestural.QuickswitchOrientedNavHandle",
            "com.android.systemui.statusbar.phone.NavigationHandle"
    };

    private static final String[] NAV_BAR_VIEW_CLASSES = {
            "com.android.systemui.navigationbar.NavigationBarView",
            "com.android.systemui.statusbar.phone.NavigationBarView"
    };

    private static final String[] BUTTON_DISPATCHER_CLASSES = {
            "com.android.systemui.navigationbar.buttons.ButtonDispatcher",
            "com.android.systemui.statusbar.phone.ButtonDispatcher"
    };

    private static final Set<View> INSTALLED_VIEWS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static final View.OnLongClickListener CTS_LONG_CLICK_LISTENER = view -> {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        return ContextualSearchStarter.start(view.getContext());
    };

    private SystemUiNavHandleHook() {
    }

    static void install(ClassLoader classLoader) {
        int installedHooks = 0;
        for (String className : NAV_HANDLE_CLASSES) {
            installedHooks += hookNavHandleClass(classLoader, className) ? 1 : 0;
        }
        for (String className : NAV_BAR_VIEW_CLASSES) {
            installedHooks += hookNavigationBarView(classLoader, className) ? 1 : 0;
        }
        for (String className : BUTTON_DISPATCHER_CLASSES) {
            installedHooks += hookButtonDispatcher(classLoader, className) ? 1 : 0;
        }
        HookLogger.i("SystemUI nav handle hooks installed: " + installedHooks);
    }

    private static boolean hookNavHandleClass(ClassLoader classLoader, String className) {
        Class<?> hookClass = HookUtils.findClassIfExists(className, classLoader);
        if (hookClass == null) {
            return false;
        }

        try {
            XposedBridge.hookAllConstructors(hookClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View) {
                        installOnView((View) param.thisObject, className + "#constructor");
                    }
                }
            });
        } catch (Throwable throwable) {
            HookLogger.w("Could not hook " + className + " constructors", throwable);
        }

        HookUtils.hookAllMethodsIfExists(hookClass, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof View) {
                    installOnView((View) param.thisObject, className + "#onAttachedToWindow");
                }
            }
        });
        return true;
    }

    private static boolean hookNavigationBarView(ClassLoader classLoader, String className) {
        Class<?> hookClass = HookUtils.findClassIfExists(className, classLoader);
        if (hookClass == null) {
            return false;
        }

        XC_MethodHook afterNavBarMutation = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                installFromNavigationBarView(param.thisObject, className);
            }
        };

        String[] methodNames = {
                "onFinishInflate",
                "reorient",
                "updateOrientationViews",
                "updateNavButtonIcons",
                "onNavigationModeChanged"
        };
        boolean hooked = false;
        for (String methodName : methodNames) {
            hooked |= HookUtils.hookAllMethodsIfExists(hookClass, methodName, afterNavBarMutation);
        }
        return hooked;
    }

    private static boolean hookButtonDispatcher(ClassLoader classLoader, String className) {
        Class<?> hookClass = HookUtils.findClassIfExists(className, classLoader);
        if (hookClass == null) {
            return false;
        }

        XC_MethodHook afterViewAdded = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                installIfDispatcherTargetsHomeHandle(param.thisObject, param.args);
            }
        };

        boolean hooked = false;
        hooked |= HookUtils.hookAllMethodsIfExists(hookClass, "addView", afterViewAdded);
        hooked |= HookUtils.hookAllMethodsIfExists(hookClass, "setCurrentView", afterViewAdded);
        return hooked;
    }

    private static void installFromNavigationBarView(Object navigationBarView, String source) {
        Object dispatcher = HookUtils.callMethodOrNull(navigationBarView, "getHomeHandle");
        if (dispatcher != null) {
            installOnDispatcher(dispatcher, source + "#getHomeHandle");
        }

        if (navigationBarView instanceof View) {
            View view = (View) navigationBarView;
            int homeHandleId = getIdentifier(view, "home_handle");
            if (homeHandleId != 0) {
                installOnView(view.findViewById(homeHandleId), source + "#findViewById");
            }
        }
    }

    private static void installIfDispatcherTargetsHomeHandle(Object dispatcher, Object[] args) {
        boolean sawHandle = false;
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof View && isLikelyHomeHandle((View) arg)) {
                    installOnView((View) arg, "ButtonDispatcher#arg");
                    sawHandle = true;
                }
            }
        }

        Object currentView = HookUtils.callMethodOrNull(dispatcher, "getCurrentView");
        if (currentView instanceof View && isLikelyHomeHandle((View) currentView)) {
            installOnView((View) currentView, "ButtonDispatcher#getCurrentView");
            sawHandle = true;
        }

        if (sawHandle) {
            installOnDispatcher(dispatcher, "ButtonDispatcher#home_handle");
        }
    }

    private static void installOnDispatcher(Object dispatcher, String source) {
        HookUtils.callMethodOrNull(dispatcher, "setLongClickable", true);
        HookUtils.callMethodOrNull(dispatcher, "setOnLongClickListener", CTS_LONG_CLICK_LISTENER);

        Object currentView = HookUtils.callMethodOrNull(dispatcher, "getCurrentView");
        if (currentView instanceof View) {
            installOnView((View) currentView, source + "#current");
        }

        Object views = HookUtils.callMethodOrNull(dispatcher, "getViews");
        if (views instanceof Iterable<?>) {
            for (Object candidate : (Iterable<?>) views) {
                if (candidate instanceof View) {
                    installOnView((View) candidate, source + "#views");
                }
            }
        }
    }

    private static void installOnView(View view, String source) {
        if (view == null) {
            return;
        }

        boolean firstInstall;
        synchronized (INSTALLED_VIEWS) {
            firstInstall = INSTALLED_VIEWS.add(view);
        }
        view.setHapticFeedbackEnabled(true);
        view.setLongClickable(true);
        view.setOnLongClickListener(CTS_LONG_CLICK_LISTENER);
        view.postDelayed(() -> {
            view.setLongClickable(true);
            view.setOnLongClickListener(CTS_LONG_CLICK_LISTENER);
        }, 300L);

        if (firstInstall) {
            HookLogger.i("Attached CTS long-press listener to " + view.getClass().getName()
                    + " via " + source);
        }
    }

    private static int getIdentifier(View view, String name) {
        try {
            return view.getResources().getIdentifier(name, "id", HookEntry.SYSTEM_UI_PACKAGE);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean isLikelyHomeHandle(View view) {
        if (view == null) {
            return false;
        }
        try {
            String entryName = view.getResources().getResourceEntryName(view.getId());
            if ("home_handle".equals(entryName)) {
                return true;
            }
        } catch (Resources.NotFoundException ignored) {
        } catch (Throwable ignored) {
        }

        String className = view.getClass().getName();
        return className.endsWith(".NavigationHandle")
                || className.endsWith(".QuickswitchOrientedNavHandle");
    }
}
