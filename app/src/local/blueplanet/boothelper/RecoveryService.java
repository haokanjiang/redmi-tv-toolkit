package com.redmi.tvtoolkit;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Legacy in-app entry; boot never starts a background/foreground Service on Android 14. */
public final class RecoveryService extends Service {
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        RecoveryScheduler.ensure(this, true);
        stopSelf(id);
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
