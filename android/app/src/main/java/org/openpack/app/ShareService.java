package org.openpack.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Keeps sharing alive while the screen is off or another app is open. Android stops background
 * apps unless they show a notification like this one. It also keeps the processor and Wi-Fi awake,
 * so a long copy to another phone is not cut off. It does nothing else.
 */
public class ShareService extends Service {
    static final String STOP = "org.openpack.app.STOP_SHARING";
    private static final String CHANNEL = "sharing";
    private PowerManager.WakeLock wake;
    private WifiManager.WifiLock wifi;

    static void start(Context c) {
        Intent i = new Intent(c, ShareService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) {
            // Sharing still works while the app is on screen.
        }
    }

    static void stop(Context c) {
        try { c.stopService(new Intent(c, ShareService.class)); } catch (Exception ignored) {}
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            final Library lib = Library.get(this);
            new Thread(new Runnable() {
                @Override public void run() {
                    try { if (lib.pack != null) lib.serve(false); } catch (Exception ignored) {}
                }
            }).start();
            sendBroadcast(new Intent(STOP).setPackage(getPackageName()));
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            Notification n = notification();
            if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(1, n);
        } catch (Exception e) {
            stopSelf(); // the phone refused; sharing still works while the app is on screen
            return START_NOT_STICKY;
        }
        try {
            if (wake == null) {
                wake = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "openpack:share");
                wake.setReferenceCounted(false);
                wake.acquire();
            }
            if (wifi == null) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                wifi = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "openpack:share");
                wifi.setReferenceCounted(false);
                wifi.acquire();
            }
        } catch (Exception ignored) {}
        return START_NOT_STICKY;
    }

    @SuppressWarnings("deprecation")
    private Notification notification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.share), NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        int immutable = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), immutable);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, ShareService.class).setAction(STOP), immutable);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_stat_share)
            .setContentTitle(getString(R.string.note_title))
            .setContentText(getString(R.string.note_text))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, getString(R.string.share_stop), stop) // this older form also works on Android 5
            .build();
    }

    @Override
    public void onDestroy() {
        try { if (wake != null && wake.isHeld()) wake.release(); } catch (Exception ignored) {}
        try { if (wifi != null && wifi.isHeld()) wifi.release(); } catch (Exception ignored) {}
        wake = null;
        wifi = null;
        super.onDestroy();
    }
}
