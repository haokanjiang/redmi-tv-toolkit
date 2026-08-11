package local.blueplanet.boothelper;

import android.app.job.JobParameters;
import android.app.job.JobService;

public final class RecoveryJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        RecoveryActions.run(this);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}

