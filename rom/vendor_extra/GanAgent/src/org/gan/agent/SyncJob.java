package org.gan.agent;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Periodic sync with the management server, run by the system when there is a network. */
public class SyncJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            Sync.run(this);
            jobFinished(params, false);
        }, "gan-sync-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
