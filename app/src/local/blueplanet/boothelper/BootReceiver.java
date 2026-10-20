package com.redmi.tvtoolkit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_USER_UNLOCKED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        Log.i(RecoveryActions.TAG, "boot signal=" + action);
        try {
            BootJournal.recordSignal(c, action);
            RecoveryScheduler.ensure(c, true);
            // Vendor provider work runs in a Job, outside the broadcast time limit.
            if (!DeviceProfile.detect().blueplanet && HomeOverrideConfig.load(c).recoveryEnabled) {
                android.content.BroadcastReceiver.PendingResult pending = goAsync();
                try { new RecoveryWorker(c, pending, null, action).start(); }
                catch (RuntimeException error) { pending.finish(); throw error; }
            }
        } catch (RuntimeException error) {
            BootJournal.recordPass(c, "boot_scheduling_failed", error.toString());
            Log.e(RecoveryActions.TAG, "boot scheduling failed", error);
        }
    }
}
