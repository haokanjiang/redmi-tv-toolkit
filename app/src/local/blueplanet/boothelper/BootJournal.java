package com.redmi.tvtoolkit;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;
import org.json.JSONObject;

/** Device-protected, bounded-size evidence survives a lost logcat buffer and a cold reboot. */
final class BootJournal {
    private static SharedPreferences prefs(Context c) {
        return HomeOverrideConfig.storage(c).getSharedPreferences("boot_journal", Context.MODE_PRIVATE);
    }

    static synchronized void recordSignal(Context c, String signal) {
        SharedPreferences p = prefs(c);
        int boot = Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        int count = p.getInt("signalBoot", -2) == boot ? p.getInt("signalCount", 0) + 1 : 1;
        boolean saved = p.edit().putInt("signalBoot", boot).putInt("signalCount", count)
                .putString("lastSignal", signal).putLong("signalWallTime", System.currentTimeMillis()).commit();
        if (!saved) Log.e(RecoveryActions.TAG, "boot signal journal persistence failed");
    }

    static synchronized void recordAdb(Context c, String when, AdbState state) {
        if (!prefs(c).edit().putString("adb_" + when, state.toString()).commit()) {
            Log.e(RecoveryActions.TAG, "ADB journal persistence failed at " + when);
        }
    }

    static synchronized void recordSource(Context c, String source) {
        if (!prefs(c).edit().putString("runSource", source)
                .putLong("runWallTime", System.currentTimeMillis()).commit()) {
            Log.e(RecoveryActions.TAG, "recovery source journal persistence failed");
        }
    }

    static synchronized void recordPass(Context c, String outcome, String detail) {
        boolean saved = prefs(c).edit().putString("outcome", outcome).putString("detail", detail)
                .putLong("passWallTime", System.currentTimeMillis()).commit();
        if (!saved) Log.e(RecoveryActions.TAG, "recovery journal persistence failed");
    }

    static synchronized void addStatus(Context c, JSONObject json) throws Exception {
        SharedPreferences p = prefs(c);
        json.put("bootSignal", p.getString("lastSignal", ""))
                .put("bootSignalCount", p.getInt("signalCount", 0))
                .put("bootSignalBootCount", p.getInt("signalBoot", -1))
                .put("bootSignalWallTime", p.getLong("signalWallTime", 0))
                .put("recoverySource", p.getString("runSource", ""))
                .put("recoverySourceWallTime", p.getLong("runWallTime", 0))
                .put("recoveryOutcome", p.getString("outcome", ""))
                .put("recoveryDetail", p.getString("detail", ""))
                .put("recoveryWallTime", p.getLong("passWallTime", 0))
                .put("adbBefore", p.getString("adb_before", ""))
                .put("adbAfter", p.getString("adb_after", ""));
    }
}
