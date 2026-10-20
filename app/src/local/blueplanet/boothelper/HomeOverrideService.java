package com.redmi.tvtoolkit;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;

public final class HomeOverrideService extends AccessibilityService {
    private static HomeOverrideService current;
    private final HomeOverrideRetry retry = new HomeOverrideRetry();
    private final LaunchBudget budget = new LaunchBudget();
    private final Handler handler = new Handler();
    private int revision = -1, eventWindow = -1;
    private String eventClass = "", eventPackage = "";
    private long eventAt;
    private boolean selecting, stopped;
    private final Runnable verify = new Runnable() {
        public void run() { evaluate(); }
    };

    static void configurationChanged() {
        if (current != null) current.handler.post(current.verify);
    }

    @Override protected void onServiceConnected() {
        current = this;
        stopped = false;
        RuntimeStatus.bound = true;
        Log.i(RecoveryActions.TAG, "HomeOverride bound profile=" + DeviceProfile.detect().id);
        evaluate(); // Boot repair can bind after Home was already visible.
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
        String cls = event.getClassName() == null ? "" : event.getClassName().toString();
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            eventPackage = pkg;
            eventClass = cls;
            eventWindow = event.getWindowId();
            eventAt = SystemClock.elapsedRealtime();
        }
        Log.d(RecoveryActions.TAG, "event type=" + event.getEventType() + " package=" + pkg
                + " class=" + cls + " time=" + event.getEventTime());
        evaluate();
    }

    private String[] foreground() {
        // Read only package/window identity. No text, screenshots or node tree is retained.
        List<AccessibilityWindowInfo> windows = getWindows();
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION
                    || (!window.isActive() && !window.isFocused())) continue;
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) continue;
            try {
                String pkg = root.getPackageName() == null ? "" : root.getPackageName().toString();
                String cls = window.getId() == eventWindow && pkg.equals(eventPackage)
                        && SystemClock.elapsedRealtime() - eventAt < 1500 ? eventClass : "";
                return new String[]{pkg, cls};
            } finally { root.recycle(); }
        }
        // Xiaomi Home can emit a window-state event while its active root is unreadable.
        // The event hint is short lived and exact; a readable foreground root always wins.
        if (HomeEventHint.matches(eventPackage, eventClass, SystemClock.elapsedRealtime(), eventAt)) {
            Log.i(RecoveryActions.TAG, "using fresh Home window event because active root is unavailable"
                    + " package=" + eventPackage + " class=" + eventClass);
            return new String[]{eventPackage, eventClass};
        }
        return new String[]{"", ""};
    }

    private static String pkg(String component) {
        int slash = component.indexOf('/');
        return slash < 0 ? "" : component.substring(0, slash);
    }

    private void evaluate() {
        handler.removeCallbacks(verify);
        if (stopped) return;
        try {
            HomeOverrideConfig config = HomeOverrideConfig.load(this);
            if (!config.enabled || !DeviceProfile.detect().homeOverride) {
                retry.reset();
                RuntimeStatus.outcome = "disabled";
                return;
            }
            if (config.revision != revision) {
                revision = config.revision;
                retry.reset();
                selecting = RuntimeStatus.requestedRevision == revision;
            }
            String[] front = foreground();
            RuntimeStatus.foreground = front[0];
            HomeOverrideRetry.Foreground state = front[0].equals(pkg(config.target))
                    ? HomeOverrideRetry.Foreground.TARGET
                    : !config.fallback.isEmpty() && front[0].equals(pkg(config.fallback))
                    ? HomeOverrideRetry.Foreground.FALLBACK
                    : DeviceProfile.detect().isSystemHome(front[0], front[1])
                    ? HomeOverrideRetry.Foreground.SYSTEM
                    : front[0].isEmpty() ? HomeOverrideRetry.Foreground.UNKNOWN : HomeOverrideRetry.Foreground.OTHER;
            if (selecting && !retry.active() && state == HomeOverrideRetry.Foreground.TARGET) {
                HomeOverrideConfig.observed(this, config.target, false);
                RuntimeStatus.outcome = "target_already_foreground";
                selecting = false;
                RuntimeStatus.requestedRevision = -1;
                return;
            }
            if (selecting && !retry.active()) state = HomeOverrideRetry.Foreground.SYSTEM;
            else if (selecting && state == HomeOverrideRetry.Foreground.OTHER) state = HomeOverrideRetry.Foreground.UNKNOWN;
            HomeOverrideRetry.Action action = retry.step(SystemClock.elapsedRealtime(), state, !config.fallback.isEmpty());
            Log.d(RecoveryActions.TAG, "decision=" + action + " foreground=" + front[0] + "/" + front[1]
                    + " target=" + config.target + " phase=" + state + " time=" + SystemClock.elapsedRealtime());
            switch (action) {
                case TARGET: launch(config.target); break;
                case FALLBACK: launch(config.fallback); break;
                case CONFIRMED_TARGET:
                case CONFIRMED_FALLBACK:
                    boolean fallback = action == HomeOverrideRetry.Action.CONFIRMED_FALLBACK;
                    HomeOverrideConfig.observed(this, fallback ? config.fallback : config.target, fallback);
                    RuntimeStatus.outcome = fallback ? "fallback_foreground" : "target_foreground";
                    selecting = false;
                    RuntimeStatus.requestedRevision = -1;
                    break;
                case DISABLE: stopOverride("foreground_verification_failed"); return;
                default: break;
            }
            if (retry.active()) handler.postDelayed(verify, 200);
        } catch (RuntimeException error) {
            Log.e(RecoveryActions.TAG, "override evaluation failed", error);
            stopOverride("override_error");
        }
    }

    private void launch(String component) {
        if (!budget.claim(SystemClock.elapsedRealtime())) { stopOverride("launch_rate_limit"); return; }
        try {
            LauncherTarget target = LauncherTarget.resolve(this, component);
            startActivity(target.intent());
            RuntimeStatus.outcome = "launch_requested";
            Log.i(RecoveryActions.TAG, "launch requested target=" + component + " category=" + target.category);
        } catch (RuntimeException error) {
            retry.launchFailed();
            RuntimeStatus.outcome = "launch_rejected";
            Log.e(RecoveryActions.TAG, "launch rejected target=" + component, error);
        }
    }

    private void stopOverride(String reason) {
        stopped = true;
        retry.reset();
        handler.removeCallbacks(verify);
        RuntimeStatus.outcome = reason;
        try { RecoveryActions.restore(this, reason); }
        catch (RuntimeException error) { Log.e(RecoveryActions.TAG, "persistent restore failed", error); }
        disableSelf(); // In-memory circuit breaker remains closed even if persistence failed.
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException error) { Log.e(RecoveryActions.TAG, "system Home launch rejected", error); }
    }

    @Override public void onInterrupt() { retry.reset(); handler.removeCallbacks(verify); }
    @Override public void onDestroy() {
        current = null;
        RuntimeStatus.bound = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
