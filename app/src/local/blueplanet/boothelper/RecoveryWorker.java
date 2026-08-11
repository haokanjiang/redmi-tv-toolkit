package local.blueplanet.boothelper;

import android.content.BroadcastReceiver.PendingResult;
import android.content.Context;
import android.util.Log;

final class RecoveryWorker extends Thread {
    private static final String TAG = "RedmiTvToolkit";
    private final Context context;
    private final PendingResult pending;

    RecoveryWorker(Context context, PendingResult pending) {
        super("toolkit-recovery");
        this.context = context.getApplicationContext();
        this.pending = pending;
    }

    @Override
    public void run() {
        try {
            RecoveryActions.run(context);
        } catch (Throwable error) {
            Log.e(TAG, "boot recovery failed", error);
        } finally {
            if (pending != null) {
                pending.finish();
            }
        }
    }
}

