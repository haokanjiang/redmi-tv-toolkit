package local.blueplanet.boothelper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class RecoveryActions {
    private static final String TAG = "RedmiTvToolkit";
    private static final String PROJECTIVY_ACCESSIBILITY =
            "com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService";
    private static final String HELPER_ACCESSIBILITY =
            "local.blueplanet.boothelper/local.blueplanet.boothelper.BootAccessibilityService";
    private static final String PROJECTIVY_PACKAGE = "com.spocky.projengmenu";
    private static final String PROJECTIVY_ACTIVITY =
            "com.spocky.projengmenu.ui.home.MainActivity";
    private static final String STATE_PREFS = "recovery_state";
    private static final String ACCESSIBILITY_REBIND_BOOT = "accessibility_rebind_boot";
    private static final String ADB_RESTART_BOOT = "adb_restart_boot";
    private static final String ADB_RESTART_COUNT = "adb_restart_count";
    private static final String ADB_RESTART_ELAPSED = "adb_restart_elapsed";
    private static final int RECOVERY_JOB_ID = 7420;
    private static final int RECOVERY_ALARM_CODE = 7419;

    private RecoveryActions() {}

    static void run(Context context) {
        if (!DeviceGuard.isSupportedDevice()) {
            return;
        }
        ensureNetworkAdb(context);
        disablePeriodicCleaner();
        enableAccessibilityServices(context);
        XiaomiBridge.setStartupMediator(context);
    }

    static void prepareSoftWakeNow(Context context) {
        run(context);
    }

    static void ensureNetworkAdbNow(Context context) {
        if (DeviceGuard.isSupportedDevice()) {
            ensureNetworkAdb(context);
        }
    }

    private static void ensureNetworkAdb(Context context) {
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

    private static void enableAccessibilityServices(Context context) {
        try {
            String raw = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            Set<String> services = parseServices(raw);
            services.remove("com.spocky.projengmenu/.services.ProjectivyAccessibilityService");
            services.remove("local.blueplanet.boothelper/.BootAccessibilityService");

            int bootCount = Settings.Global.getInt(
                    context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
            SharedPreferences prefs = protectedPrefs(context);
            boolean firstRepairThisBoot =
                    prefs.getInt(ACCESSIBILITY_REBIND_BOOT, -2) != bootCount;

            if (firstRepairThisBoot) {
                // A two-step rewrite clears Android's Crashed services entry.
                Settings.Secure.putString(
                        context.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        TextUtils.join(":", services));
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }

            services.add(PROJECTIVY_ACCESSIBILITY);
            services.add(HELPER_ACCESSIBILITY);
            String updated = TextUtils.join(":", services);
            if (firstRepairThisBoot || !TextUtils.equals(raw, updated)) {
                Settings.Secure.putString(
                        context.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        updated);
            }
            Settings.Secure.putInt(
                    context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            if (firstRepairThisBoot) {
                prefs.edit().putInt(ACCESSIBILITY_REBIND_BOOT, bootCount).apply();
            }
        } catch (Throwable error) {
            Log.e(TAG, "unable to repair accessibility services", error);
        }
    }

    private static Set<String> parseServices(String raw) {
        Set<String> services = new LinkedHashSet<>();
        if (!TextUtils.isEmpty(raw) && !"null".equals(raw)) {
            for (String service : raw.split(":")) {
                if (!TextUtils.isEmpty(service)) {
                    services.add(service);
                }
            }
        }
        return services;
    }

    static void launchProjectivy(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            intent.setComponent(new ComponentName(PROJECTIVY_PACKAGE, PROJECTIVY_ACTIVITY));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            context.startActivity(intent);
        } catch (Throwable error) {
            Log.e(TAG, "unable to launch Projectivy", error);
        }
    }

    static void scheduleBootAlarm(Context context) {
        try {
            AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (manager == null) {
                return;
            }
            PendingIntent pending = PendingIntent.getActivity(
                    context,
                    RECOVERY_ALARM_CODE,
                    new Intent(context, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
            manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 60_000L,
                    pending);
        } catch (Throwable error) {
            Log.e(TAG, "unable to schedule recovery alarm", error);
        }
    }

    static void scheduleRecoveryJob(Context context) {
        JobScheduler scheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        try {
            scheduler.schedule(new JobInfo.Builder(
                            RECOVERY_JOB_ID,
                            new ComponentName(context, RecoveryJobService.class))
                    .setMinimumLatency(45_000L)
                    .setOverrideDeadline(90_000L)
                    .setPersisted(true)
                    .build());
        } catch (Throwable error) {
            Log.e(TAG, "unable to schedule recovery job", error);
        }
    }

    private static void disablePeriodicCleaner() {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/cmd", "periodic", "enable", "false")
                    .redirectErrorStream(true)
                    .start();
            boolean exited = process.waitFor(5, TimeUnit.SECONDS);
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            Log.i(TAG, "periodic cleaner exit=" + (exited ? process.exitValue() : -1)
                    + " output=" + output);
        } catch (Throwable error) {
            Log.e(TAG, "unable to disable PeriodicCleaner", error);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static SharedPreferences protectedPrefs(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
    }
}

