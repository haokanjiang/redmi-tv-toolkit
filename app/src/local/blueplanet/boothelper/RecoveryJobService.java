package com.redmi.tvtoolkit;
import android.app.job.JobParameters;import android.app.job.JobService;
public final class RecoveryJobService extends JobService { @Override public boolean onStartJob(JobParameters p){RecoveryActions.run(this);return false;} @Override public boolean onStopJob(JobParameters p){return false;} }
