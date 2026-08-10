package com.local.yongsanimaxwatcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

public final class NotificationHelper {
    public static final int FOREGROUND_ID = 1001;
    private static final String STATUS_CH = "watch_status";
    private static final String ALERT_CH = "ticket_alert";

    private final Context context;
    private final NotificationManager nm;

    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        nm = context.getSystemService(NotificationManager.class);
        createChannels();
    }

    private void createChannels() {
        NotificationChannel status = new NotificationChannel(
                STATUS_CH, "감시 상태", NotificationManager.IMPORTANCE_LOW);
        status.setDescription("용아맥 오디세이 감시가 실행 중임을 표시합니다.");
        nm.createNotificationChannel(status);

        NotificationChannel alert = new NotificationChannel(
                ALERT_CH, "예매 오픈 알림", NotificationManager.IMPORTANCE_HIGH);
        alert.setDescription("새 IMAX 날짜가 열렸을 때 울립니다.");
        alert.enableVibration(true);
        alert.setVibrationPattern(new long[]{0, 300, 180, 300, 180, 700});
        alert.enableLights(true);
        alert.setLightColor(Color.RED);
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build();
        alert.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, aa);
        nm.createNotificationChannel(alert);
    }

    public Notification foreground(String text) {
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(
                context, 11, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stop = new Intent(context, WatchService.class);
        stop.setAction(WatchService.ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                context, 12, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Builder(context, STATUS_CH)
                .setSmallIcon(com.local.yongsanimaxwatcher.R.drawable.ic_stat_ticket)
                .setContentTitle("용아맥 오디세이 감시 중")
                .setContentText(text)
                .setContentIntent(openPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        null, "감시 중지", stopPi).build())
                .build();
    }

    public void alert(String title, String text, int id) {
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(CgvClient.BOOKING_URL));
        PendingIntent pi = PendingIntent.getActivity(
                context, 20 + id, view, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new Notification.Builder(context, ALERT_CH)
                .setSmallIcon(com.local.yongsanimaxwatcher.R.drawable.ic_stat_ticket)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setPriority(Notification.PRIORITY_MAX)
                .build();
        nm.notify(id, n);
    }

    public void updateForeground(Notification n) {
        nm.notify(FOREGROUND_ID, n);
    }
}
