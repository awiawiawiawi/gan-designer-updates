package org.gan.agent;

import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.util.Calendar;

/**
 * Process entry point. The app is persistent, so this runs at boot and stays alive.
 * It keeps the lock enforced and keeps the policy in sync with the server.
 */
public class GanApp extends Application {
    static final String TAG = "GanAgent";
    private static final int SYNC_JOB_ID = 1;
    private static final long SYNC_INTERVAL_MS = 15 * 60 * 1000;

    @Override
    public void onCreate() {
        super.onCreate();

        final IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                enforceLock(context);
                // Pick up a changed policy quickly, for example an unlock sent from the server.
                Sync.runAsync(context);
            }
        }, filter);

        getSystemService(JobScheduler.class).schedule(
                new JobInfo.Builder(SYNC_JOB_ID, new ComponentName(this, SyncJob.class))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPeriodic(SYNC_INTERVAL_MS)
                        .setPersisted(false)
                        .build());

        Sync.runAsync(this);
        enforceLock(this);
    }

    /** Turns the screen off if the current policy says the device is locked now. */
    static void enforceLock(Context context) {
        final Policy policy = Sync.currentPolicy(context);
        if (policy == null) {
            return;
        }
        final Calendar now = Calendar.getInstance();
        final int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (policy.isLockedAt(minute)) {
            Log.i(TAG, "Device is locked by policy, turning the screen off");
            context.getSystemService(PowerManager.class).goToSleep(SystemClock.uptimeMillis());
        }
    }
}
