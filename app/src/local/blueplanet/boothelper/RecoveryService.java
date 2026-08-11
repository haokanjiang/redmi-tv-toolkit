package local.blueplanet.boothelper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public final class RecoveryService extends Service {
    private static final String CHANNEL_ID = "redmi_tv_recovery";
    private static final int NOTIFICATION_ID = 7401;
    private static final int MAX_ATTEMPTS = 4;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int attempts;

    private final Runnable recovery = new Runnable() {
        @Override
        public void run() {
            attempts++;
            RecoveryActions.run(RecoveryService.this);
            if (attempts < MAX_ATTEMPTS) {
                handler.postDelayed(this, 10_000L);
            } else {
                stopSelf();
            }
        }
    };

    static void start(Context context) {
        Intent intent = new Intent(context, RecoveryService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle(getString(R.string.status_title))
                    .setContentText(getString(R.string.status_text))
                    .setContentIntent(pending)
                    .setOngoing(true)
                    .build();
        } else {
            notification = new Notification.Builder(this)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle(getString(R.string.status_title))
                    .setContentText(getString(R.string.status_text))
                    .setContentIntent(pending)
                    .setOngoing(true)
                    .build();
        }
        startForeground(NOTIFICATION_ID, notification);
        handler.post(recovery);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.status_title),
                        NotificationManager.IMPORTANCE_LOW));
            }
        }
    }
}

