package dev.afdo.lineagepillcts;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class ContextualSearchStarter {
    static final int ENTRYPOINT_LONG_PRESS_NAV_HANDLE = 1;

    private static final String CONTEXTUAL_SEARCH_SERVICE = "contextual_search";
    private static final String CONTEXTUAL_SEARCH_INTERFACE =
            "android.app.contextualsearch.IContextualSearchManager";

    private ContextualSearchStarter() {
    }

    static boolean start(Context context) {
        if (context == null || isKeyguardLocked(context)) {
            return false;
        }

        Object manager = getContextualSearchManager(context);
        if (manager != null && startViaManager(manager)) {
            HookLogger.i("Contextual search started via ContextualSearchManager");
            return true;
        }

        boolean started = startViaBinder();
        HookLogger.i("Contextual search binder fallback result=" + started);
        return started;
    }

    private static boolean isKeyguardLocked(Context context) {
        try {
            Object service = context.getSystemService(Context.KEYGUARD_SERVICE);
            return service instanceof KeyguardManager
                    && ((KeyguardManager) service).isKeyguardLocked();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object getContextualSearchManager(Context context) {
        try {
            Object manager = context.getSystemService(CONTEXTUAL_SEARCH_SERVICE);
            if (manager != null) {
                return manager;
            }
        } catch (Throwable ignored) {
        }

        try {
            Context appContext = context.getApplicationContext();
            return appContext == null ? null : appContext.getSystemService(CONTEXTUAL_SEARCH_SERVICE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean startViaManager(Object manager) {
        try {
            Method startContextualSearch =
                    manager.getClass().getMethod("startContextualSearch", int.class);
            startContextualSearch.invoke(manager, ENTRYPOINT_LONG_PRESS_NAV_HANDLE);
            return true;
        } catch (InvocationTargetException exception) {
            HookLogger.w(
                    "ContextualSearchManager rejected startContextualSearch",
                    exception.getTargetException());
            return false;
        } catch (Throwable throwable) {
            HookLogger.w("Could not call ContextualSearchManager directly", throwable);
            return false;
        }
    }

    private static boolean startViaBinder() {
        Parcel data = null;
        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Method getService = serviceManager.getDeclaredMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, CONTEXTUAL_SEARCH_SERVICE);
            if (binder == null) {
                HookLogger.i("contextual_search service is not registered");
                return false;
            }

            data = Parcel.obtain();
            data.writeInterfaceToken(CONTEXTUAL_SEARCH_INTERFACE);
            data.writeInt(ENTRYPOINT_LONG_PRESS_NAV_HANDLE);
            return binder.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
        } catch (Throwable throwable) {
            HookLogger.w("Could not call contextual_search binder directly", throwable);
            return false;
        } finally {
            if (data != null) {
                data.recycle();
            }
        }
    }
}
