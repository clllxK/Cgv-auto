package com.local.yongsanimaxwatcher;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.time.LocalDate;
import java.util.Set;

public final class PairWatchService extends Service {
    public static final String ACTION_START="com.local.yongsanimaxwatcher.PAIR_START";
    public static final String ACTION_STOP="com.local.yongsanimaxwatcher.PAIR_STOP";

    private final Handler main=new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private NotificationHelper notifications;
    private KakaoClient kakao;
    private PowerManager.WakeLock wakeLock;
    private WebView web;
    private boolean running=false, seatPageReady=false;
    private String targetKey="", targetLabel="", date="", time="", hall="", movie="", lastPair="", lastUrl="";
    private int bootstrapAttempts=0;

    private final Runnable bootstrapTask=new Runnable(){@Override public void run(){if(!running||web==null)return;if(seatPageReady){scanSeats();return;}runAutoNavigation();if(++bootstrapAttempts%12==0)web.reload();main.postDelayed(this,3000);}};
    private final Runnable pollTask=new Runnable(){@Override public void run(){if(!running||web==null)return;if(seatPageReady){web.reload();}else runAutoNavigation();main.postDelayed(this,Math.max(30,prefs.getIntervalSeconds())*1000L);}};

    @Override public void onCreate(){
        super.onCreate();prefs=new Prefs(this);notifications=new NotificationHelper(this);kakao=new KakaoClient(this);
        PowerManager pm=getSystemService(PowerManager.class);wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":pairWatch");wakeLock.setReferenceCounted(false);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())){stopWatching();return START_NOT_STICKY;}
        if(running)return START_STICKY;
        if(!loadTarget()){prefs.setPairWatching(false);prefs.setStatus("연석 감시할 회차가 없어");stopSelf();return START_NOT_STICKY;}
        running=true;prefs.setPairWatching(true);prefs.setStatus("연석 감시 준비 중");
        startAsForeground("선택 회차로 자동 이동 중");
        if(!wakeLock.isHeld())wakeLock.acquire();
        main.post(this::createWebViewAndStart);
        return START_STICKY;
    }

    private boolean loadTarget(){
        Set<String>keys=prefs.getSelectedShowtimeKeys();if(keys.size()!=1)return false;targetKey=keys.iterator().next();String[]p=targetKey.split("\\|",4);if(p.length<4)return false;date=p[0];time=p[1];hall=p[2];movie=p[3];targetLabel=prefs.getSelectedShowtimeLabels().isEmpty()?DateUtil.pretty(date)+" · "+movie+" · "+time:prefs.getSelectedShowtimeLabels().iterator().next();return true;
    }

    private void startAsForeground(String text){android.app.Notification n=notifications.pairForeground(text);if(Build.VERSION.SDK_INT>=34)startForeground(NotificationHelper.PAIR_FOREGROUND_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(NotificationHelper.PAIR_FOREGROUND_ID,n);}

    private void createWebViewAndStart(){
        if(!running)return;
        try{
            web=new WebView(this);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setLoadsImagesAutomatically(false);s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);s.setUserAgentString(s.getUserAgentString()+" CGVTicketWatch/1.0");
            CookieManager cm=CookieManager.getInstance();cm.setAcceptCookie(true);cm.setAcceptThirdPartyCookies(web,true);web.addJavascriptInterface(new PairBridge(),"PairBridge");web.setWebChromeClient(new WebChromeClient());
            web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView view,String url){lastUrl=url==null?"":url;CookieManager.getInstance().flush();if(!running)return;main.postDelayed(()->{if(seatPageReady)scanSeats();else runAutoNavigation();},1200);}});
            web.loadUrl(CgvClient.bookingUrl(prefs.getTheaterName(),prefs.getSiteNo()));
            main.postDelayed(bootstrapTask,2200);main.postDelayed(pollTask,Math.max(30,prefs.getIntervalSeconds())*1000L);
        }catch(Throwable t){fail("연석 감시 초기화 실패: "+shortMsg(t));}
    }

    private void runAutoNavigation(){
        if(web==null||!running)return;
        String js=AUTO_NAV_JS.replace("__THEATER__",js(prefs.getTheaterName())).replace("__MOVIE__",js(movie)).replace("__DATE__",js(date)).replace("__TIME__",js(time)).replace("__HALL__",js(hall));
        web.evaluateJavascript(js,null);
    }

    private void scanSeats(){if(web==null||!running)return;web.evaluateJavascript(SEAT_SCAN_JS,null);}

    private final class PairBridge {
        @JavascriptInterface public void state(String state,int seatCount){main.post(()->handleState(state,seatCount));}
        @JavascriptInterface public void result(String pair,int count){main.post(()->handleResult(pair,count));}
    }

    private void handleState(String state,int seatCount){
        if(!running)return;String s=state==null?"":state;
        if("seat".equals(s)){if(!seatPageReady){seatPageReady=true;bootstrapAttempts=0;main.removeCallbacks(bootstrapTask);}prefs.setLastChecked(DateUtil.nowStamp());prefs.setStatus("연석 감시 중 · "+targetLabel);notifications.updatePairForeground(notifications.pairForeground("30초마다 실제 좌석 확인 · "+movie+" "+time));scanSeats();}
        else if("auth".equals(s)){prefs.setStatus("CGV 세션 자동 복구 시도 중");notifications.updatePairForeground(notifications.pairForeground("CGV 세션 자동 복구 시도 중"));}
        else{prefs.setStatus("연석 감시 준비 중 · CGV 회차 자동 선택 중");notifications.updatePairForeground(notifications.pairForeground("선택 회차로 자동 이동 중 · "+movie+" "+time));}
    }

    private void handleResult(String pair,int count){
        if(!running)return;seatPageReady=true;prefs.setLastChecked(DateUtil.nowStamp());String p=pair==null?"":pair.trim();
        if(p.isEmpty()){lastPair="";prefs.setStatus("연석 감시 중 · 현재 붙은 2자리 없음 · 후보 "+count+"석");notifications.updatePairForeground(notifications.pairForeground("붙은 2자리 없음 · 후보 "+count+"석 · "+movie+" "+time));return;}
        prefs.setStatus("🔥 연석 발견 · "+p+" · "+targetLabel);notifications.updatePairForeground(notifications.pairForeground("🔥 연석 발견 "+p+" · "+movie+" "+time));
        if(!p.equals(lastPair)){lastPair=p;String title="🔥 붙은 2자리 발생! "+p;String body=prefs.getTheaterName()+" · "+DateUtil.pretty(date)+" "+time+" · "+hall+"\n"+movie+" · 연속 좌석 "+p+" 발견";String url=lastUrl==null||lastUrl.isEmpty()?CgvClient.bookingUrl(prefs.getTheaterName(),prefs.getSiteNo()):lastUrl;notifications.hotPairAlert(title,body,8200+Math.abs((targetKey+p).hashCode()%700),url,targetLabel);try{kakao.sendMemo(title+"\n"+body);}catch(Exception ignored){}}
    }

    private void fail(String msg){prefs.setStatus(msg);notifications.alert("CGV 연석 감시 오류",msg,9301,CgvClient.bookingUrl(prefs.getTheaterName(),prefs.getSiteNo()));stopWatching();}

    private void stopWatching(){running=false;main.removeCallbacksAndMessages(null);prefs.setPairWatching(false);if(!prefs.isStandardWatching())prefs.setStatus("정지됨");if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();if(web!=null){try{web.stopLoading();web.removeJavascriptInterface("PairBridge");web.destroy();}catch(Exception ignored){}web=null;}stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onDestroy(){stopWatching();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}

    private static String js(String s){String x=s==null?"":s;return x.replace("\\","\\\\").replace("'","\\'").replace("\n"," ").replace("\r"," ");}
    private static String shortMsg(Throwable t){String m=t==null?"":t.getMessage();if(m==null||m.trim().isEmpty())m=t==null?"오류":t.getClass().getSimpleName();return m.length()>120?m.substring(0,120):m;}

    private static final String AUTO_NAV_JS=
            "(function(){try{"+
            "var theater='__THEATER__',movie='__MOVIE__',date='__DATE__',time='__TIME__',hall='__HALL__';"+
            "function txt(e){return ((e.innerText||e.textContent||e.getAttribute('aria-label')||e.getAttribute('title')||'')+'').replace(/\\s+/g,' ').trim();}"+
            "function visible(e){var r=e.getBoundingClientRect();var st=getComputedStyle(e);return st.display!=='none'&&st.visibility!=='hidden'&&r.width>=0&&r.height>=0;}"+
            "function selected(e){var c=((e.className||'')+' '+(e.getAttribute('aria-selected')||'')+' '+(e.getAttribute('aria-pressed')||'')).toLowerCase();return /selected|active|on|checked|true/.test(c);}"+
            "function elems(){return [].slice.call(document.querySelectorAll('button,a,[role=button],[role=option],li,label,span,div'));}"+
            "function clickText(words,exact){var es=elems();for(var i=0;i<es.length;i++){var e=es[i];if(!visible(e)||selected(e))continue;var t=txt(e);if(!t||t.length>120)continue;for(var j=0;j<words.length;j++){var w=words[j];if(!w)continue;if((exact&&t===w)||(!exact&&t.indexOf(w)>=0)){try{e.click();PairBridge.state('nav',0);return true;}catch(x){}}}}return false;}"+
            "var body=(document.body&&document.body.innerText||'').toLowerCase();if(/로그인|본인인증|captcha|자동입력/.test(body)){PairBridge.state('auth',0);}"+
            "var seatNodes=[].slice.call(document.querySelectorAll('button,a,[role=button],[onclick],input,label'));var sc=0;for(var z=0;z<seatNodes.length;z++){var q=((seatNodes[z].className||'')+' '+(seatNodes[z].id||'')+' '+(seatNodes[z].getAttribute('aria-label')||'')+' '+(seatNodes[z].getAttribute('title')||'')).toLowerCase();if(/seat|좌석/.test(q))sc++;}if(sc>=4){PairBridge.state('seat',sc);return;}"+
            "var d=date.replace(/[^0-9]/g,'');var mm=parseInt(d.substring(4,6),10),dd=parseInt(d.substring(6,8),10);var dates=[mm+'/'+dd,mm+'.'+dd,mm+'월 '+dd+'일',mm+'월'+dd+'일',d];"+
            "if(clickText([theater],false))return;"+
            "if(clickText([movie],false))return;"+
            "if(clickText(dates,false))return;"+
            "if(clickText([time,time.replace(':','')],false))return;"+
            "if(clickText(['좌석선택','좌석 선택','예매하기','예매 하기','다음'],false))return;"+
            "PairBridge.state('nav',0);"+
            "}catch(e){PairBridge.state('nav',0);}})();";

    private static final String SEAT_SCAN_JS=
            "(function(){try{"+
            "var q='button,a,[role=button],[onclick],input,label';var ns=[].slice.call(document.querySelectorAll(q));var out=[],seen={};"+
            "function bad(e){var c=((e.className||'')+' '+(e.id||'')+' '+(e.getAttribute('aria-disabled')||'')).toLowerCase();return e.disabled||e.getAttribute('aria-disabled')==='true'||/(disabled|sold|unavailable|reserved|occupied|used|complete|none)/.test(c);}"+
            "ns.forEach(function(e){if(bad(e))return;var ds='';try{ds=JSON.stringify(e.dataset||{});}catch(x){}var sig=((e.className||'')+' '+(e.id||'')+' '+ds+' '+(e.getAttribute('name')||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')).toLowerCase();if(!/(seat|좌석)/.test(sig))return;var t=((e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')+' '+(e.getAttribute('data-seat-no')||'')+' '+(e.getAttribute('data-seatno')||'')+' '+(e.value||'')+' '+(e.innerText||'')+' '+ds).replace(/\\s+/g,' ');var m=t.match(/(?:^|[^A-Z0-9])([A-Z])\\s*[- ]?\\s*0*(\\d{1,3})(?:[^0-9]|$)/i);if(!m)return;var row=m[1].toUpperCase(),num=parseInt(m[2],10);if(!num)return;var k=row+'-'+num;if(!seen[k]){seen[k]=1;out.push({r:row,n:num});}});"+
            "out.sort(function(a,b){return a.r===b.r?a.n-b.n:(a.r<b.r?-1:1);});var pair='';for(var i=0;i<out.length-1;i++){if(out[i].r===out[i+1].r&&out[i+1].n===out[i].n+1){pair=out[i].r+out[i].n+' · '+out[i+1].r+out[i+1].n;break;}}PairBridge.result(pair,out.length);"+
            "}catch(e){PairBridge.result('',0);}})();";
}
