package com.redmi.tvtoolkit;

import android.content.BroadcastReceiver.PendingResult;
import android.content.Context;
import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;

final class RecoveryWorker extends Thread {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private final Context context;
    private final PendingResult pending;
    private final Runnable completed;
    private final String source;

    RecoveryWorker(Context c, PendingResult p, Runnable completed, String source) {
        super("toolkit-recovery");
        context = c.getApplicationContext();
        pending = p;
        this.completed = completed;
        this.source = source;
    }

    @Override public void run() {
        boolean acquired = RUNNING.compareAndSet(false, true);
        try {
            if (acquired && !isInterrupted()) {
                BootJournal.recordSource(context, source);
                Log.i(RecoveryActions.TAG, "recovery worker started source=" + source);
                RecoveryActions.run(context);
            }
            else Log.i(RecoveryActions.TAG, "duplicate/cancelled recovery worker ignored");
        } catch (RuntimeException error) {
            BootJournal.recordPass(context, "worker_failed", error.toString());
            Log.e(RecoveryActions.TAG, "recovery pass failed", error);
        } finally {
            if (acquired) RUNNING.set(false);
            if (pending != null) pending.finish();
            if (completed != null) completed.run();
        }
    }
}
