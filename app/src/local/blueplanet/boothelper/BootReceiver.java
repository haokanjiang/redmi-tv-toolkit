package local.blueplanet.boothelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "RedmiTvToolkit";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!DeviceGuard.isSupportedDevice()) {
            return;
        }
        Log.i(TAG, "boot signal=" + (intent == null ? "?" : intent.getAction()));
        RecoveryActions.ensureNetworkAdbNow(context);
        RecoveryActions.scheduleRecoveryJob(context);
        RecoveryActions.scheduleBootAlarm(context);
        final PendingResult pending = goAsync();
        new RecoveryWorker(context, pending).start();
    }
}

