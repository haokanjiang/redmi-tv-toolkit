package com.redmi.tvtoolkit;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/** Original blueplanet repair primitives; called only through capability-gated RecoveryPlan. */
final class LegacyRecoveryActions {
    private static final String TAG = "RedmiTvToolkit";
    private static final String STATE_PREFS = "legacy_recovery";
    private static final String ADB_RESTART_BOOT = "adb_restart_boot";
    private static final String ADB_RESTART_COUNT = "adb_restart_count";
    private static final String ADB_RESTART_ELAPSED = "adb_restart_elapsed";

    static void startup(Context context) {
        SharedPreferences prefs = protectedPrefs(context);
        if (!prefs.contains("original_startup")) {
            String previous = Settings.System.getString(context.getContentResolver(), "start_3rd_app");
            if (!prefs.edit().putString("original_startup", previous == null ? "" : previous).commit()) {
                throw new IllegalStateException("startup backup failed");
            }
        }
        String current = Settings.System.getString(context.getContentResolver(), "start_3rd_app");
        if (!context.getPackageName().equals(current)) {
            XiaomiBridge.setStartupMediator(context);
            if (!context.getPackageName().equals(Settings.System.getString(context.getContentResolver(), "start_3rd_app"))) {
                throw new IllegalStateException("startup mediator read-back failed");
            }
        }
    }

    private static String command(String... args) {
        Process process = null;
        try {
            process = new ProcessBuilder(args).redirectErrorStream(true).start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) throw new IllegalStateException("legacy command timed out");
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append('\n');
            }
            if (process.exitValue() != 0) throw new IllegalStateException("legacy command failed: " + output);
            return output.toString();
        } catch (Exception error) { throw new IllegalStateException("legacy command failed", error); }
        finally { if (process != null) process.destroyForcibly(); }
    }

    private static String periodicState() {
        String output = command("/system/bin/dumpsys", "periodic");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Enable=(true|false)").matcher(output);
        if (!m.find()) throw new IllegalStateException("PeriodicCleaner state unknown; no modification");
        return m.group(1);
    }

    static void periodic(Context context) {
        if (!DeviceProfile.detect().capabilities.periodicCleaner) return;
        SharedPreferences prefs = protectedPrefs(context);
        String current = periodicState();
        if (!prefs.contains("original_periodic")
                && !prefs.edit().putString("original_periodic", current).commit()) {
            throw new IllegalStateException("PeriodicCleaner backup failed");
        }
        if ("true".equals(current)) {
            command("/system/bin/cmd", "periodic", "enable", "false");
            if (!"false".equals(periodicState())) throw new IllegalStateException("PeriodicCleaner read-back failed");
        }
    }

    static void restore(Context context) {
        if (!DeviceProfile.detect().capabilities.startThirdApp) return;
        SharedPreferences prefs = protectedPrefs(context);
        if (prefs.contains("original_periodic")) {
            String expected = prefs.getString("original_periodic", "false");
            if (!expected.equals(periodicState())) {
                command("/system/bin/cmd", "periodic", "enable", expected);
                if (!expected.equals(periodicState())) throw new IllegalStateException("PeriodicCleaner rollback failed");
            }
        }
        if (prefs.contains("original_startup")) {
            String expected = prefs.getString("original_startup", "");
            XiaomiBridge.restoreStartupSetting(context, expected);
            String actual = Settings.System.getString(context.getContentResolver(), "start_3rd_app");
            if (!expected.equals(actual == null ? "" : actual)) throw new IllegalStateException("startup rollback read-back failed");
        }
    }

    private static SharedPreferences protectedPrefs(Context context) {
        return HomeOverrideConfig.storage(context).getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
    }
    static void ensureNetworkAdb(Context context) {
        if (!DeviceProfile.detect().capabilities.adbRecovery || !HomeOverrideConfig.load(context).recoveryEnabled) return;
        try {
            int enabled = Settings.Global.getInt(
                    context.getContentResolver(), Settings.Global.ADB_ENABLED, 0);
            boolean changed = false;
            if (enabled != 1) {
                changed = Settings.Global.putInt(
                        context.getContentResolver(), Settings.Global.ADB_ENABLED, 1);
                int verified = Settings.Global.getInt(
                        context.getContentResolver(), Settings.Global.ADB_ENABLED, 0);
                Log.i(TAG, "restored adb_enabled: write=" + changed
                        + " verified=" + verified);
                if (verified != 1) {
                    Log.e(TAG, "WRITE_SECURE_SETTINGS grant is missing");
                    return;
                }
            }

            String persistentPort = DeviceGuard.readProperty("persist.adb.tcp.port");
            String servicePort = DeviceGuard.readProperty("service.adb.tcp.port");
            String adbdState = DeviceGuard.readProperty("init.svc.adbd");
            if ("5555".equals(persistentPort)
                    && "5555".equals(servicePort)
                    && "running".equals(adbdState)) {
                Log.i(TAG, "network ADB active on port 5555");
                return;
            }
            if (!changed) {
                restartAdbdWithRateLimit(context);
            }
        } catch (Throwable error) {
            Log.e(TAG, "unable to restore network ADB", error);
        }
    }

    private static void restartAdbdWithRateLimit(Context context) {
        try {
            int bootCount = Settings.Global.getInt(
                    context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
            long now = android.os.SystemClock.elapsedRealtime();
            SharedPreferences prefs = protectedPrefs(context);
            int storedBoot = prefs.getInt(ADB_RESTART_BOOT, -2);
            int count = storedBoot == bootCount ? prefs.getInt(ADB_RESTART_COUNT, 0) : 0;
            long last = storedBoot == bootCount
                    ? prefs.getLong(ADB_RESTART_ELAPSED, -1L) : -1L;
            if (count >= 2 || (last >= 0 && now - last < 45_000L)) {
                return;
            }
            prefs.edit()
                    .putInt(ADB_RESTART_BOOT, bootCount)
                    .putInt(ADB_RESTART_COUNT, count + 1)
                    .putLong(ADB_RESTART_ELAPSED, now)
                    .apply();
            XiaomiBridge.restartAdbd(context);
        } catch (Throwable error) {
            Log.e(TAG, "unable to request guarded adbd restart", error);
        }
    }

}
