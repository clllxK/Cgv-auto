package com.local.yongsanimaxwatcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Prefs prefs = new Prefs(context);
        if (!prefs.isAutoRestart() || !prefs.isWatching()) return;

        Intent service = new Intent(context, WatchService.class);
        service.setAction(WatchService.ACTION_START);
        try {
            context.startForegroundService(service);
        } catch (Exception e) {
            prefs.setStatus("재부팅 후 자동 시작 실패 · 앱에서 감시 시작을 눌러줘");
            prefs.setWatching(false);
        }
    }
}
