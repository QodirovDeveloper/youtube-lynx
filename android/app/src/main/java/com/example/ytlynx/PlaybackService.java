package com.example.ytlynx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

// Foreground-servis: bildirishnoma turgan ekan, Android ilova jarayonini o'ldirmaydi,
// ekran qulfida esa ⏮ ⏯ ⏭ tugmalari ko'rinadi (MediaStyle + MediaSession).
// Holatni Player'dan oladi, tugmalar bosilsa Player.command() ga uzatadi.
public class PlaybackService extends Service {
    private static final String CHANNEL_ID = "playback";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_COMMAND = "com.example.ytlynx.COMMAND";
    private static final String EXTRA_COMMAND = "command";

    private static PlaybackService sRunning;

    // Ijro boshlandi: servisni ishga tushiramiz yoki bildirishnomani yangilaymiz
    static void show(Context context) {
        if (sRunning != null) {
            sRunning.updateNotification();
            return;
        }
        Intent intent = new Intent(context, PlaybackService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException e) {
            // Android 12+: ilova fonda turganda foreground-servisni boshlash taqiqlangan bo'lishi mumkin
            Log.w(MainActivity.TAG, "servis boshlanmadi: " + e);
        }
    }

    static void refresh() {
        if (sRunning != null) sRunning.updateNotification();
    }

    static void stop(Context context) {
        if (sRunning != null) context.stopService(new Intent(context, PlaybackService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sRunning = this;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // startForegroundService'dan keyin 5 soniya ichida chaqirilishi shart
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        String command = intent == null ? null : intent.getStringExtra(EXTRA_COMMAND);
        if (command != null) Player.get().command(command, 0);
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // "So'nggi ilovalar"dan surib yopildi — hammasini to'xtatamiz
        Player.get().exit();
    }

    @Override
    public void onDestroy() {
        sRunning = null;
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void updateNotification() {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification());
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification() {
        Player p = Player.get();
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(p.title.isEmpty() ? getString(R.string.app_name) : p.title)
                .setContentText(p.channel)
                .setContentIntent(open)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(p.playing)
                .setShowWhen(false)
                .addAction(action(R.drawable.ic_prev, R.string.prev, "prev", 1))
                .addAction(p.playing
                        ? action(R.drawable.ic_pause, R.string.pause, "pause", 2)
                        : action(R.drawable.ic_play, R.string.play, "play", 2))
                .addAction(action(R.drawable.ic_next, R.string.next, "next", 3))
                .addAction(action(R.drawable.ic_close, R.string.exit, Player.ACTION_EXIT, 4))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(p.session().getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        if (p.art != null) builder.setLargeIcon(p.art);
        return builder.build();
    }

    private Notification.Action action(int icon, int title, String command, int requestCode) {
        Intent intent = new Intent(this, PlaybackService.class)
                .setAction(ACTION_COMMAND)
                .putExtra(EXTRA_COMMAND, command);
        PendingIntent pending = PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), getString(title), pending)
                .build();
    }
}
