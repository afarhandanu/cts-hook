package dev.afdo.lineagepillcts;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;

final class HookLogger {
    static final String TAG = "LineagePillCTS";

    private HookLogger() {
    }

    static void i(String message) {
        Log.i(TAG, message);
        XposedBridge.log(TAG + ": " + message);
    }

    static void w(String message, Throwable throwable) {
        Log.w(TAG, message, throwable);
        XposedBridge.log(TAG + ": " + message + " - " + throwable);
    }
}
