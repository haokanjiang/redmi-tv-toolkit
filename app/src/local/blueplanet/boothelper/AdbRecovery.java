package com.redmi.tvtoolkit;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

/** MFTR0 can repair the secure setting only; it never calls blueplanet's provider or restarts adbd. */
final class AdbRecovery {
    static AdbState inspect(Context c) throws Exception {
        return new AdbState(
                Settings.Global.getInt(c.getContentResolver(), Settings.Global.ADB_ENABLED, -1),
                DeviceGuard.readProperty("persist.adb.tcp.port"),
                DeviceGuard.readProperty("service.adb.tcp.port"),
                DeviceGuard.readProperty("init.svc.adbd"),
                Settings.System.getString(c.getContentResolver(), "start_3rd_app"));
    }

    static AdbState restoreSetting(Context c, AdbState before) {
        if (!"mftr0".equals(DeviceProfile.detect().id)) {
            throw new SecurityException("MFTR0 setting repair requested on a different device");
        }
        BootJournal.recordAdb(c, "before", before);
        Log.i(RecoveryActions.TAG, "MFTR0 ADB before: " + before);
        if (before.enabled != 1) {
            RecoveryActions.requirePermission(c);
            if (!Settings.Global.putInt(c.getContentResolver(), Settings.Global.ADB_ENABLED, 1)
                    || Settings.Global.getInt(c.getContentResolver(), Settings.Global.ADB_ENABLED, -1) != 1) {
                BootJournal.recordPass(c, "adb_setting_failed", "adb_enabled write/read-back failed");
                throw new IllegalStateException("adb_enabled write/read-back failed");
            }
            Log.i(RecoveryActions.TAG, "MFTR0 adb_enabled restored and read back as 1");
        }
        try {
            AdbState after = inspect(c);
            BootJournal.recordAdb(c, "after", after);
            if (after.networkReady()) {
                Log.i(RecoveryActions.TAG, "MFTR0 network adbd verified: " + after);
            } else {
                Log.w(RecoveryActions.TAG, "MFTR0 adb_enabled may be set, but network adbd is unavailable: " + after);
            }
            return after;
        } catch (Exception error) {
            throw new IllegalStateException("MFTR0 ADB read-back failed", error);
        }
    }
}
