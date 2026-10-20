package com.redmi.tvtoolkit;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public final class RecoveryJobService extends JobService {
    private RecoveryWorker worker;
    @Override public boolean onStartJob(final JobParameters params) {
        Log.i(RecoveryActions.TAG, "recovery job started id=" + params.getJobId());
        worker = new RecoveryWorker(this, null, new Runnable() {
            public void run() {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        Log.i(RecoveryActions.TAG, "recovery job finished id=" + params.getJobId());
                        jobFinished(params, false);
                    }
                });
            }
        }, "job:" + params.getJobId());
        worker.start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        Log.w(RecoveryActions.TAG, "recovery job stopped id=" + params.getJobId());
        if (worker != null) worker.interrupt();
        return false; // no unbounded OS retry loop
    }
}
