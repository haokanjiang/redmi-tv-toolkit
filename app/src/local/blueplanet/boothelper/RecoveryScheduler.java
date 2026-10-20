package com.redmi.tvtoolkit;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

final class RecoveryScheduler {
    static final int BOOT_JOB = 7420, WATCHDOG_JOB = 7421;

    static void ensure(Context c, boolean bootSignal) {
        HomeOverrideConfig config = HomeOverrideConfig.load(c);
        if (!DeviceProfile.detect().bootRecovery || !config.recoveryEnabled) { cancel(c); return; }
        JobScheduler scheduler = (JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) throw new IllegalStateException("JobScheduler unavailable");
        ComponentName component = new ComponentName(c, RecoveryJobService.class);
        // Registered at configuration time, not only inside a boot broadcast.
        if (scheduler.getPendingJob(WATCHDOG_JOB) == null) {
            int result = scheduler.schedule(new JobInfo.Builder(WATCHDOG_JOB, component)
                    .setPeriodic(15 * 60 * 1000L).setPersisted(true).build());
            if (result != JobScheduler.RESULT_SUCCESS) throw new IllegalStateException("watchdog schedule failed");
        }
        if (bootSignal && scheduler.getPendingJob(BOOT_JOB) == null) {
            int result = scheduler.schedule(new JobInfo.Builder(BOOT_JOB, component)
                    .setMinimumLatency(15000).setOverrideDeadline(60000).setPersisted(true).build());
            if (result != JobScheduler.RESULT_SUCCESS) throw new IllegalStateException("boot job schedule failed");
        }
    }

    static synchronized boolean claim(Context c) {
        SharedPreferences p = HomeOverrideConfig.storage(c).getSharedPreferences("recovery_budget", Context.MODE_PRIVATE);
        int boot = Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        int oldBoot = p.getInt("boot", -2), count = p.getInt("count", 0);
        long now = SystemClock.elapsedRealtime();
        if (!RecoveryBudget.allowed(boot, oldBoot, count, now, p.getLong("last", -1))) return false;
        if (!p.edit().putInt("boot", boot).putInt("count", boot == oldBoot ? count + 1 : 1)
                .putLong("last", now).commit()) throw new IllegalStateException("recovery budget persistence failed");
        Log.i(RecoveryActions.TAG, "recovery attempt boot=" + boot + " count=" + (boot == oldBoot ? count + 1 : 1));
        return true;
    }

    static void cancel(Context c) {
        JobScheduler scheduler = (JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) { scheduler.cancel(BOOT_JOB); scheduler.cancel(WATCHDOG_JOB); }
    }
}
