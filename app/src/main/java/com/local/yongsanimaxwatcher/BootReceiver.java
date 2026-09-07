package com.local.yongsanimaxwatcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Prefs prefs=new Prefs(context);
        if(!prefs.isAutoRestart()||!prefs.isWatching())return;
        try{
            if(prefs.isStandardWatching()){
                Intent s=new Intent(context,WatchService.class);s.setAction(WatchService.ACTION_START);context.startForegroundService(s);
            }
            if(prefs.isPairWatching()&&prefs.isAdjacentSeatOnly()&&prefs.getSelectedShowtimeKeys().size()==1){
                Intent p=new Intent(context,PairWatchService.class);p.setAction(PairWatchService.ACTION_START);context.startForegroundService(p);
            }
        }catch(Exception e){prefs.setStatus("재부팅 후 자동 시작 실패 · 앱에서 감시 시작을 눌러줘");prefs.clearWatchingFlags();}
    }
}
