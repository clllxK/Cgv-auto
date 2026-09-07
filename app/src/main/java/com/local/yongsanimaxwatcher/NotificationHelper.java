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

public final class NotificationHelper {
    public static final int FOREGROUND_ID = 1001;
    public static final int PAIR_FOREGROUND_ID = 1002;
    private static final String STATUS_CH = "watch_status";
    private static final String ALERT_CH = "ticket_alert_v2";
    private static final String HOT_CH = "hot_ticket_alert_v1";

    private final Context context;
    private final NotificationManager nm;

    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        nm = context.getSystemService(NotificationManager.class);
        createChannels();
    }

    private void createChannels() {
        NotificationChannel status = new NotificationChannel(STATUS_CH, "감시 상태", NotificationManager.IMPORTANCE_LOW);
        status.setDescription("CGV 예매 감시가 실행 중임을 표시합니다.");
        nm.createNotificationChannel(status);
        AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build();
        NotificationChannel alert = new NotificationChannel(ALERT_CH, "CGV 예매/취소표 알림", NotificationManager.IMPORTANCE_HIGH);
        alert.setDescription("새 날짜나 취소표가 발견되면 울립니다.");alert.enableVibration(true);alert.setVibrationPattern(new long[]{0,300,180,300,180,700});alert.enableLights(true);alert.setLightColor(Color.RED);alert.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,aa);nm.createNotificationChannel(alert);
        NotificationChannel hot = new NotificationChannel(HOT_CH, "🔥 최우선 좌석 알림", NotificationManager.IMPORTANCE_HIGH);
        hot.setDescription("매진 회차 좌석이나 붙은 2자리가 생기면 강하게 알립니다.");hot.enableVibration(true);hot.setVibrationPattern(new long[]{0,700,120,700,120,700,120,1200});hot.enableLights(true);hot.setLightColor(Color.RED);hot.setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,aa);nm.createNotificationChannel(hot);
    }

    public Notification foreground(String text) {
        Intent open=new Intent(context,MainActivity.class);PendingIntent openPi=PendingIntent.getActivity(context,11,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop=new Intent(context,WatchService.class);stop.setAction(WatchService.ACTION_STOP);PendingIntent stopPi=PendingIntent.getService(context,12,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(context,STATUS_CH).setSmallIcon(R.drawable.ic_stat_ticket).setContentTitle("CGV 예매 감시 중").setContentText(text).setContentIntent(openPi).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"감시 중지",stopPi).build()).build();
    }

    public Notification pairForeground(String text) {
        Intent open=new Intent(context,MainActivity.class);PendingIntent openPi=PendingIntent.getActivity(context,21,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop=new Intent(context,PairWatchService.class);stop.setAction(PairWatchService.ACTION_STOP);PendingIntent stopPi=PendingIntent.getService(context,22,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(context,STATUS_CH).setSmallIcon(R.drawable.ic_stat_ticket).setContentTitle("👫 붙은 2자리 감시 중").setContentText(text).setContentIntent(openPi).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"연석 감시 중지",stopPi).build()).build();
    }

    public void alert(String title,String text,int id,String bookingUrl){post(ALERT_CH,title,text,id,bookingUrl);}
    public void hotAlert(String title,String text,int id,String bookingUrl){post(HOT_CH,title,text,id,bookingUrl);}

    public void hotPairAlert(String title,String text,int id,String pageUrl,String targetLabel) {
        Intent open=new Intent(Intent.ACTION_VIEW,Uri.parse(pageUrl));PendingIntent pi=PendingIntent.getActivity(context,30000+id,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(context,HOT_CH).setSmallIcon(R.drawable.ic_stat_ticket).setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(pi).setAutoCancel(true).setCategory(Notification.CATEGORY_ALARM).setPriority(Notification.PRIORITY_MAX).build();nm.notify(id,n);
    }

    private void post(String channel,String title,String text,int id,String bookingUrl){Intent view=new Intent(Intent.ACTION_VIEW,Uri.parse(bookingUrl));PendingIntent pi=PendingIntent.getActivity(context,20+id,view,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);Notification n=new Notification.Builder(context,channel).setSmallIcon(R.drawable.ic_stat_ticket).setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(pi).setAutoCancel(true).setCategory(Notification.CATEGORY_ALARM).setPriority(Notification.PRIORITY_MAX).build();nm.notify(id,n);}
    public void alert(String title,String text,int id){alert(title,text,id,"https://cgv.co.kr/cnm/movieBook");}
    public void updateForeground(Notification n){nm.notify(FOREGROUND_ID,n);} public void updatePairForeground(Notification n){nm.notify(PAIR_FOREGROUND_ID,n);}
}
