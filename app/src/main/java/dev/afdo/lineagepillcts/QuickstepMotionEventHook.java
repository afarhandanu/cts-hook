package dev.afdo.lineagepillcts;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;

final class QuickstepMotionEventHook {
    private static final String TOUCH_INTERACTION_SERVICE =
            "com.android.quickstep.TouchInteractionService";
    private static final String INPUT_MONITOR_COMPAT =
            "com.android.systemui.shared.system.InputMonitorCompat";
    private static final String INPUT_CONSUMER = "com.android.quickstep.InputConsumer";

    private static final float HALF_TOUCH_WIDTH_DP = 90f;
    private static final float BOTTOM_TOUCH_HEIGHT_DP = 48f;

    private static final Map<Object, LongPressState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private QuickstepMotionEventHook() {
    }

    static void install(ClassLoader classLoader, String packageName) {
        Class<?> serviceClass = HookUtils.findClassIfExists(
                TOUCH_INTERACTION_SERVICE,
                classLoader);
        if (serviceClass == null) {
            HookLogger.i("TouchInteractionService not found in " + packageName);
            return;
        }

        boolean hooked = HookUtils.hookDeclaredMethodsIf(
                serviceClass,
                QuickstepMotionEventHook::isInputEventHandler,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!(param.thisObject instanceof Context)
                                || param.args == null
                                || param.args.length != 1
                                || !(param.args[0] instanceof MotionEvent)) {
                            return;
                        }

                        LongPressState state = stateFor(
                                param.thisObject,
                                (Context) param.thisObject);
                        if (state.onMotionEvent((MotionEvent) param.args[0])) {
                            param.setResult(null);
                        }
                    }
                });
        HookLogger.i("Quickstep raw MotionEvent fallback for " + packageName
                + " installed=" + hooked);
    }

    private static boolean isInputEventHandler(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return method.getReturnType() == void.class
                && parameterTypes.length == 1
                && InputEvent.class.isAssignableFrom(parameterTypes[0]);
    }

    private static LongPressState stateFor(Object owner, Context context) {
        synchronized (STATES) {
            LongPressState state = STATES.get(owner);
            if (state == null) {
                state = new LongPressState(owner, context);
                STATES.put(owner, state);
            }
            return state;
        }
    }

    private static final class LongPressState {
        private final WeakReference<Object> owner;
        private final WeakReference<Context> context;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final float touchSlopSquared;
        private final int longPressTimeout;
        private final Runnable trigger = this::triggerLongPress;

        private float downX;
        private float downY;
        private boolean tracking;
        private boolean triggered;

        LongPressState(Object owner, Context context) {
            this.owner = new WeakReference<>(owner);
            this.context = new WeakReference<>(context);
            float touchSlop = ViewConfiguration.get(context).getScaledTouchSlop() * 1.5f;
            touchSlopSquared = touchSlop * touchSlop;
            longPressTimeout = ViewConfiguration.getLongPressTimeout();
        }

        boolean onMotionEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (triggered) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    reset();
                }
                return true;
            }

            if (action == MotionEvent.ACTION_DOWN) {
                reset();
                Context currentContext = context.get();
                if (currentContext != null && isInPillRegion(currentContext, event)) {
                    downX = event.getX();
                    downY = event.getY();
                    tracking = true;
                    handler.postDelayed(trigger, longPressTimeout);
                }
                return false;
            }

            if (!tracking) {
                return false;
            }

            if (action == MotionEvent.ACTION_MOVE) {
                float deltaX = event.getX() - downX;
                float deltaY = event.getY() - downY;
                if ((deltaX * deltaX) + (deltaY * deltaY) > touchSlopSquared) {
                    reset();
                }
            } else if (action == MotionEvent.ACTION_POINTER_DOWN
                    || action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                reset();
            }
            return false;
        }

        private void triggerLongPress() {
            if (!tracking || triggered) {
                return;
            }

            Object currentOwner = owner.get();
            Context currentContext = context.get();
            if (currentOwner == null || currentContext == null) {
                reset();
                return;
            }

            if (!ContextualSearchStarter.start(currentContext)) {
                HookLogger.i("Raw pill long-press detected, but contextual search did not start");
                reset();
                return;
            }

            triggered = true;
            tracking = false;
            cancelQuickstepGesture(currentOwner);
            pilferPointers(currentOwner);
            vibrate(currentContext);
            HookLogger.i("Raw pill long-press invoked contextual search");
        }

        private void reset() {
            handler.removeCallbacks(trigger);
            tracking = false;
            triggered = false;
        }
    }

    private static boolean isInPillRegion(Context context, MotionEvent event) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float density = metrics.density <= 0f ? 1f : metrics.density;
        float centerX = metrics.widthPixels / 2f;
        float minimumY = metrics.heightPixels - (BOTTOM_TOUCH_HEIGHT_DP * density);
        return Math.abs(event.getX() - centerX) <= HALF_TOUCH_WIDTH_DP * density
                && event.getY() >= minimumY;
    }

    private static void cancelQuickstepGesture(Object owner) {
        Object consumer = findFieldByClassName(owner, "mUncheckedConsumer", INPUT_CONSUMER);
        if (consumer == null) {
            consumer = findFieldByClassName(owner, "mConsumer", INPUT_CONSUMER);
        }
        if (consumer == null) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(
                now,
                now,
                MotionEvent.ACTION_CANCEL,
                0f,
                0f,
                0);
        try {
            HookUtils.callMethodOrNull(consumer, "onMotionEvent", cancel);
        } finally {
            cancel.recycle();
        }
    }

    private static void pilferPointers(Object owner) {
        Object monitor = findFieldByClassName(owner, "mInputMonitorCompat", INPUT_MONITOR_COMPAT);
        HookUtils.callMethodOrNull(monitor, "pilferPointers");
    }

    private static Object findFieldByClassName(
            Object owner,
            String preferredName,
            String expectedClassName) {
        Object value = HookUtils.getObjectFieldOrNull(owner, preferredName);
        if (hasClassName(value, expectedClassName)) {
            return value;
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

    private static boolean hasClassName(Object value, String className) {
        if (value == null) {
            return false;
        }
        Class<?> valueClass = value.getClass();
        while (valueClass != null) {
            if (className.equals(valueClass.getName())) {
                return true;
            }
            for (Class<?> interfaceClass : valueClass.getInterfaces()) {
                if (className.equals(interfaceClass.getName())) {
                    return true;
                }
            }
            valueClass = valueClass.getSuperclass();
        }
        return false;
    }

    private static void vibrate(Context context) {
        try {
            Vibrator vibrator = context.getSystemService(Vibrator.class);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(
                        35L,
                        VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Throwable ignored) {
        }
    }
}
