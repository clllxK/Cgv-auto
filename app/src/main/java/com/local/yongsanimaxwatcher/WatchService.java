package com.local.yongsanimaxwatcher;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WatchService extends Service {
    public static final String ACTION_START = "com.local.yongsanimaxwatcher.START";
    public static final String ACTION_STOP = "com.local.yongsanimaxwatcher.STOP";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;
    private Prefs prefs;
    private CgvClient cgv;
    private KakaoClient kakao;
    private NotificationHelper notifications;
    private PowerManager.WakeLock wakeLock;

    // 기존에 열린 날짜의 취소표도 자주 확인하고, 그 사이에 최신 날짜 이후도 순환 확인한다.
    private final int[] offsetPattern = {0, 1, 0, 2, 0, 3, 1, 4, 0, 5, 1, 6, 2, 7};
    private int patternIndex = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        cgv = new CgvClient();
        kakao = new KakaoClient(this);
        notifications = new NotificationHelper(this);
        executor = Executors.newSingleThreadExecutor();

        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                getPackageName() + ":cinemaWatch");
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopWatching();
            return START_NOT_STICKY;
        }

        startAsForeground();
        if (running.compareAndSet(false, true)) {
            prefs.setWatching(true);
            prefs.setStatus("시작 중...");
            if (!wakeLock.isHeld()) wakeLock.acquire();
            executor.execute(this::runLoop);
        }
        return START_STICKY;
    }

    private void startAsForeground() {
        android.app.Notification n = notifications.foreground("초기화 중");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    NotificationHelper.FOREGROUND_ID,
                    n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NotificationHelper.FOREGROUND_ID, n);
        }
    }

    private void runLoop() {
        try {
            if (prefs.getLatestDate().isEmpty()) initializeBaseline();

            while (running.get()) {
                checkOneDate();
                int interval = Math.max(30, prefs.getIntervalSeconds());
                sleepInterruptibly(interval * 1000L);
            }
        } catch (Throwable t) {
            prefs.setStatus("감시 오류: " + shortMsg(t));
            notifications.alert(
                    "용아맥 알리미 오류",
                    "감시가 중단됐어. 앱을 열어서 확인해줘.\n" + shortMsg(t),
                    9001);
        } finally {
            running.set(false);
            prefs.setWatching(false);
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void initializeBaseline() throws Exception {
        prefs.setStatus("현재 IMAX 일정/좌석 기준 만드는 중...");
        notifications.updateForeground(
                notifications.foreground("첫 실행: 현재 일정과 좌석 기준 확인 중"));

        LocalDate today = DateUtil.today();
        String latest = "";
        int success = 0;
        Exception last = null;

        for (int i = 0; i <= 17 && running.get(); i++) {
            String d = DateUtil.ymd(today.plusDays(i));
            try {
                List<CgvClient.Showtime> matches = cgv.fetchMatches(d);
                success++;
                if (!matches.isEmpty()) latest = d;
                snapshot(matches);
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(350);
        }

        if (success < 3) {
            throw new IllegalStateException(
                    "CGV 시간표 조회에 실패했어. "
                            + (last == null ? "" : shortMsg(last)));
        }

        if (latest.isEmpty()) latest = DateUtil.ymd(today);
        prefs.setLatestDate(latest);
        prefs.setStatus("기준 설정 완료: " + DateUtil.pretty(latest));
        notifications.updateForeground(
                notifications.foreground("새 날짜 + 취소표 감시 중 · 기준 " + DateUtil.pretty(latest)));
    }

    private void snapshot(List<CgvClient.Showtime> matches) {
        for (CgvClient.Showtime s : matches) {
            if (s.freeSeats >= 0) prefs.setSeatCount(s.key(), s.freeSeats);
        }
    }

    private void checkOneDate() {
        try {
            String latestText = prefs.getLatestDate();
            LocalDate latest = latestText.isEmpty() ? DateUtil.today() : DateUtil.parseYmd(latestText);
            LocalDate today = DateUtil.today();

            int offset = offsetPattern[patternIndex++ % offsetPattern.length];
            LocalDate candidate;
            if (offset <= 2) {
                candidate = today.plusDays(offset);
            } else {
                LocalDate floor = latest.isBefore(today) ? today : latest;
                candidate = floor.plusDays(offset - 2L);
            }

            String date = DateUtil.ymd(candidate);
            List<CgvClient.Showtime> matches = cgv.fetchMatches(date);
            prefs.setLastChecked(DateUtil.nowStamp());
            prefs.setConsecutiveErrors(0);

            boolean isNewDate = !matches.isEmpty() && candidate.isAfter(latest);
            if (isNewDate) {
                notifyNewDate(date, matches);
                prefs.setLatestDate(date);
                patternIndex = 0;
            }

            for (CgvClient.Showtime s : matches) {
                if (s.freeSeats < 0) continue;
                int old = prefs.getSeatCount(s.key());
                if (old != Integer.MIN_VALUE && s.freeSeats > old) {
                    notifySeatIncrease(s, old, s.freeSeats);
                }
                prefs.setSeatCount(s.key(), s.freeSeats);
            }

            String baseline = prefs.getLatestDate();
            String status = "정상 감시 중 · 새 날짜+취소표 · 기준 "
                    + (baseline.isEmpty() ? "-" : DateUtil.pretty(baseline))
                    + " · 방금 " + DateUtil.pretty(date) + " 확인";
            prefs.setStatus(status);
            notifications.updateForeground(notifications.foreground(status));
        } catch (Exception e) {
            int errors = prefs.getConsecutiveErrors() + 1;
            prefs.setConsecutiveErrors(errors);
            prefs.setLastChecked(DateUtil.nowStamp());
            prefs.setStatus("CGV 조회 실패 " + errors + "회 · 재시도 중");
            notifications.updateForeground(
                    notifications.foreground("조회 실패 · 자동 재시도 중"));

            if (errors == 5 || errors == 20) {
                notifications.alert(
                        "용아맥 알리미 조회 지연",
                        "CGV 조회가 연속 " + errors + "회 실패했어. 네트워크나 CGV 페이지 변경 가능성이 있어.\n"
                                + shortMsg(e),
                        9100 + errors);
            }
        }
    }

    private void notifySeatIncrease(CgvClient.Showtime s, int oldSeats, int newSeats) {
        int added = newSeats - oldSeats;
        String pretty = DateUtil.pretty(s.date);
        String title = "🎟️ 용아맥 취소표/새 자리 " + added + "석!";
        String body = pretty + " " + s.time + " · " + s.hall
                + "\n잔여 " + oldSeats + " → " + newSeats + "석\n지금 바로 예매 화면을 확인해.";
        notifications.alert(title, body, 4000 + Math.abs(s.key().hashCode() % 4000));
        prefs.setStatus("새 자리 발견! " + pretty + " " + s.time + " · " + newSeats + "석");

        try {
            kakao.sendMemo(title + "\n" + body);
        } catch (Exception ignored) {
            // 폰 알림이 핵심이므로 카카오 전송 실패는 감시를 중단하지 않는다.
        }
    }

    private void notifyNewDate(String date, List<CgvClient.Showtime> matches) {
        StringBuilder times = new StringBuilder();
        for (CgvClient.Showtime s : matches) {
            if (s.time == null || s.time.isEmpty()) continue;
            if (times.length() > 0) times.append(", ");
            if (times.indexOf(s.time) < 0) times.append(s.time);
            if (times.length() > 90) break;
        }

        String pretty = DateUtil.pretty(date);
        String timeText = times.length() == 0 ? "회차 확인 필요" : times.toString();
        String title = "🚨 용아맥 오디세이 IMAX 새 날짜!";
        String body = pretty + "  " + timeText + "\n지금 CGV 예매를 확인해.";
        notifications.alert(title, body, 2000 + Math.abs(date.hashCode() % 5000));

        try {
            kakao.sendMemo(title + "\n" + body);
        } catch (Exception ignored) {}
    }

    private void sleepInterruptibly(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (running.get() && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(Math.min(1000, end - System.currentTimeMillis()));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void stopWatching() {
        running.set(false);
        prefs.setWatching(false);
        prefs.setStatus("정지됨");
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private static String shortMsg(Throwable t) {
        String s = t == null ? "" : t.getMessage();
        if (s == null || s.isEmpty()) s = t == null ? "알 수 없는 오류" : t.getClass().getSimpleName();
        s = s.replace("\n", " ");
        return s.length() > 180 ? s.substring(0, 180) : s;
    }

    @Override
    public void onDestroy() {
        running.set(false);
        prefs.setWatching(false);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
