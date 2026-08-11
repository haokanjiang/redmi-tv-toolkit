package local.blueplanet.boothelper;

import android.accessibilityservice.AccessibilityService;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

public final class BootAccessibilityService extends AccessibilityService {
    private ContentObserver adbObserver;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (!DeviceGuard.isSupportedDevice()) {
            disableSelf();
            return;
        }
        RecoveryActions.ensureNetworkAdbNow(this);
        if (adbObserver == null) {
            adbObserver = new ContentObserver(new Handler(getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    RecoveryActions.ensureNetworkAdbNow(BootAccessibilityService.this);
                }
            };
            getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),
                    false,
                    adbObserver);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Deliberately empty. canRetrieveWindowContent=false in service metadata.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (adbObserver != null) {
            getContentResolver().unregisterContentObserver(adbObserver);
            adbObserver = null;
        }
        super.onDestroy();
    }
}

