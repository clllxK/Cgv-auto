package com.local.yongsanimaxwatcher;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WatchService extends Service {
    public static final String ACTION_START="com.local.yongsanimaxwatcher.START";
    public static final String ACTION_STOP="com.local.yongsanimaxwatcher.STOP";

    private final AtomicBoolean running=new AtomicBoolean(false);
    private ExecutorService executor;
    private Prefs prefs;
    private CgvClient seatClient, discoveryClient;
    private KakaoClient kakao;
    private NotificationHelper notifications;
    private PowerManager.WakeLock wakeLock;
    private int selectedDateIndex=0, probeIndex=0;
    private final int[] probeOffsets={0,1,2,3,4,5,6,7,8,9,10,12,14,17,21,24,28};

    @Override public void onCreate(){super.onCreate();prefs=new Prefs(this);kakao=new KakaoClient(this);notifications=new NotificationHelper(this);executor=Executors.newSingleThreadExecutor();PowerManager pm=getSystemService(PowerManager.class);wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":cinemaWatch");wakeLock.setReferenceCounted(false);}

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())){stopWatching();return START_NOT_STICKY;}
        startAsForeground();
        if(running.compareAndSet(false,true)){prefs.setWatching(true);prefs.setStatus("시작 중...");seatClient=new CgvClient(prefs.getTheaterName(),prefs.getSiteNo(),"","");discoveryClient=new CgvClient(prefs.getTheaterName(),prefs.getSiteNo(),"",prefs.getFormatKeyword());if(!wakeLock.isHeld())wakeLock.acquire();executor.execute(this::runLoop);}
        return START_STICKY;
    }

    private void startAsForeground(){android.app.Notification n=notifications.foreground("초기화 중");if(Build.VERSION.SDK_INT>=34)startForeground(NotificationHelper.FOREGROUND_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(NotificationHelper.FOREGROUND_ID,n);}

    private void runLoop(){
        try{
            if(prefs.getLatestDate().isEmpty())initializeBaseline();
            while(running.get()){
                try{
                    checkSelectedShowtime();probeNewDate();prefs.setLastChecked(DateUtil.nowStamp());prefs.setConsecutiveErrors(0);String s="감시 중 · "+prefs.targetLabel();prefs.setStatus(s);notifications.updateForeground(notifications.foreground(s));
                }catch(Exception e){int errors=prefs.getConsecutiveErrors()+1;prefs.setConsecutiveErrors(errors);prefs.setLastChecked(DateUtil.nowStamp());prefs.setStatus("조회 실패 "+errors+"회 · 자동 재시도 중");notifications.updateForeground(notifications.foreground("CGV 조회 실패 · 자동 재시도 중"));if(errors==10||errors==30)notifications.alert("CGV 알리미 조회 지연","CGV 조회가 연속 "+errors+"회 실패했어. 감시는 계속 재시도 중이야.\n"+shortMsg(e),9100+errors,seatClient.bookingUrl());}
                sleepInterruptibly(Math.max(30,prefs.getIntervalSeconds())*1000L);
            }
        }catch(Throwable t){prefs.setStatus("감시 오류: "+shortMsg(t));notifications.alert("CGV 알리미 오류","감시가 중단됐어.\n"+shortMsg(t),9001,seatClient==null?CgvClient.BOOKING_URL:seatClient.bookingUrl());}
        finally{running.set(false);prefs.setWatching(false);if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    }

    private void initializeBaseline() throws Exception {
        notifications.updateForeground(notifications.foreground("현재 일정 기준 확인 중"));
        // 연석 모드의 선택 회차는 SeatPairActivity가 실제 좌석표로 담당한다.
        if(prefs.hasSelectedShowtimes()&&!prefs.isAdjacentSeatOnly()){
            int ok=0;Exception last=null;for(String d:prefs.getSelectedDates()){try{snapshot(seatClient.fetchMatches(d));ok++;}catch(Exception e){last=e;}Thread.sleep(180);}if(ok==0)throw new IllegalStateException("선택 회차 조회 실패 "+(last==null?"":shortMsg(last)));
        }
        if(!prefs.getNewDateMovies().isEmpty()){
            LocalDate today=DateUtil.today();int ok=0;String latest="";for(int i=0;i<=31&&running.get();i++){String d=DateUtil.ymd(today.plusDays(i));try{List<CgvClient.Showtime>m=filterSelectedMovies(discoveryClient.fetchMatches(d));boolean open=!m.isEmpty();prefs.setDateOpenState(d,open);if(open)latest=d;ok++;}catch(Exception ignored){}Thread.sleep(120);}if(ok==0)throw new IllegalStateException("새 날짜 기준 조회 실패");prefs.setLatestDate(latest.isEmpty()?DateUtil.ymd(today):latest);
        }else prefs.setLatestDate(DateUtil.ymd(DateUtil.today()));
    }

    private void snapshot(List<CgvClient.Showtime>list){Set<String>keys=prefs.getSelectedShowtimeKeys();for(CgvClient.Showtime s:list)if(keys.contains(s.key())&&s.freeSeats>=0)prefs.setSeatCount(s.key(),s.freeSeats);}

    private void checkSelectedShowtime() throws Exception {
        if(prefs.isAdjacentSeatOnly())return;
        List<String>dates=prefs.getSelectedDates();if(dates.isEmpty())return;String d=dates.get(selectedDateIndex++%dates.size());List<CgvClient.Showtime>list=seatClient.fetchMatches(d);Set<String>keys=prefs.getSelectedShowtimeKeys();for(CgvClient.Showtime s:list){if(!keys.contains(s.key())||s.freeSeats<0)continue;int old=prefs.getSeatCount(s.key());if(old!=Integer.MIN_VALUE&&s.freeSeats>old)notifySeatIncrease(s,old,s.freeSeats);prefs.setSeatCount(s.key(),s.freeSeats);}
    }

    private void probeNewDate() throws Exception {
        if(prefs.getNewDateMovies().isEmpty())return;LocalDate candidate=DateUtil.today().plusDays(probeOffsets[probeIndex++%probeOffsets.length]);String d=DateUtil.ymd(candidate);List<CgvClient.Showtime>matches=filterSelectedMovies(discoveryClient.fetchMatches(d));boolean open=!matches.isEmpty();int oldState=prefs.getDateOpenState(d);
        if(oldState==1){if(open)prefs.setDateOpenState(d,true);return;}
        if(oldState==0&&open){notifyNewDate(d,matches);String latest=prefs.getLatestDate();if(latest.isEmpty()||candidate.isAfter(DateUtil.parseYmd(latest)))prefs.setLatestDate(d);prefs.setDateOpenState(d,true);return;}
        if(oldState==-1)prefs.setDateOpenState(d,open);else if(!open)prefs.setDateOpenState(d,false);
    }

    private List<CgvClient.Showtime> filterSelectedMovies(List<CgvClient.Showtime>in){Set<String>titles=prefs.getSelectedMovieTitles();ArrayList<CgvClient.Showtime>out=new ArrayList<>();for(CgvClient.Showtime s:in){String st=s.title==null?"":s.title.toLowerCase(Locale.KOREA);for(String t:titles){String w=t==null?"":t.toLowerCase(Locale.KOREA);if(!w.isEmpty()&&(st.equals(w)||st.contains(w)||w.contains(st))){out.add(s);break;}}}return out;}

    private void notifySeatIncrease(CgvClient.Showtime s,int old,int now){int add=now-old;String pretty=DateUtil.pretty(s.date);boolean hot=old==0&&now>0;String title=hot?"🔥 매진 회차 좌석 발생! "+s.title+" "+s.time:"🎟️ "+s.title+" "+s.time+" 취소표 "+add+"석!";String body=prefs.getTheaterName()+" · "+pretty+" "+s.time+" · "+s.hall+"\n"+s.format+" · 잔여 "+old+" → "+now+"석";if(hot)notifications.hotAlert(title,body,7000+Math.abs(s.key().hashCode()%1500),seatClient.bookingUrl());else notifications.alert(title,body,4000+Math.abs(s.key().hashCode()%2500),seatClient.bookingUrl());try{kakao.sendMemo(title+"\n"+body);}catch(Exception ignored){}}

    private void notifyNewDate(String d,List<CgvClient.Showtime>matches){StringBuilder times=new StringBuilder();for(CgvClient.Showtime s:matches){if(times.length()>0)times.append(", ");times.append(s.time);if(times.length()>100)break;}String movie=matches.isEmpty()?"선택 영화":matches.get(0).title;String fmt=prefs.getFormatKeyword();String title="🚨 "+movie+" · "+fmt+" 새 날짜 오픈!";String body=prefs.getTheaterName()+" · "+DateUtil.pretty(d)+"\n"+fmt+" 회차: "+times;notifications.hotAlert(title,body,2000+Math.abs((d+title).hashCode()%3000),seatClient.bookingUrl());try{kakao.sendMemo(title+"\n"+body);}catch(Exception ignored){}}

    private void sleepInterruptibly(long ms)throws InterruptedException{long end=System.currentTimeMillis()+ms;while(running.get()&&System.currentTimeMillis()<end)Thread.sleep(Math.min(1000,end-System.currentTimeMillis()));}
    private void stopWatching(){running.set(false);prefs.setWatching(false);prefs.setStatus("정지됨");if(executor!=null)executor.shutdownNow();if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    private static String shortMsg(Throwable t){String m=t==null?"":t.getMessage();if(m==null||m.trim().isEmpty())m=t==null?"오류":t.getClass().getSimpleName();return m.length()>140?m.substring(0,140):m;}
    @Override public IBinder onBind(Intent i){return null;}
}
