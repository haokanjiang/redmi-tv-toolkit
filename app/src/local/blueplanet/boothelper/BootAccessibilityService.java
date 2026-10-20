package com.redmi.tvtoolkit;

import android.accessibilityservice.AccessibilityService;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BootAccessibilityService extends AccessibilityService {
    private ContentObserver observer;
    private final AtomicBoolean checking = new AtomicBoolean();

    private void checkAdb() {
        if (!checking.compareAndSet(false, true)) return;
        new Thread(new Runnable() {
            public void run() {
                try { RecoveryActions.ensureNetworkAdbNow(BootAccessibilityService.this); }
                finally { checking.set(false); }
            }
        }, "blueplanet-adb-recovery").start();
    }

    @Override protected void onServiceConnected() {
        if (!DeviceProfile.detect().capabilities.adbRecovery || !HomeOverrideConfig.load(this).recoveryEnabled) {
            disableSelf();
            return;
        }
        checkAdb();
        observer = new ContentObserver(new Handler(getMainLooper())) {
            @Override public void onChange(boolean selfChange) { checkAdb(); }
        };
        getContentResolver().registerContentObserver(Settings.Global.getUriFor(Settings.Global.ADB_ENABLED), false, observer);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        if (observer != null) getContentResolver().unregisterContentObserver(observer);
        super.onDestroy();
    }
}
