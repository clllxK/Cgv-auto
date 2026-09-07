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
    public static final String ACTION_START = "com.local.yongsanimaxwatcher.START";
    public static final String ACTION_STOP = "com.local.yongsanimaxwatcher.STOP";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;
    private Prefs prefs;
    private CgvClient cgv;
    private CgvClient discoveryCgv;
    private KakaoClient kakao;
    private NotificationHelper notifications;
    private PowerManager.WakeLock wakeLock;

    private final int[] offsetPattern = {0, 1, 0, 2, 0, 3, 1, 4, 0, 5, 1, 6, 2, 7};
    private final int[] discoveryOffsets = {1, 2, 1, 3, 1, 4, 2, 5, 1, 6, 3, 7};
    private int patternIndex = 0;
    private int selectedDateIndex = 0;
    private int discoveryProbeIndex = 0;
    private int selectedLoopCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        kakao = new KakaoClient(this);
        notifications = new NotificationHelper(this);
        executor = Executors.newSingleThreadExecutor();

        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":cinemaWatch");
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
            boolean selected = prefs.hasSelectedShowtimes();
            cgv = new CgvClient(
                    prefs.getTheaterName(),
                    prefs.getSiteNo(),
                    selected ? "" : prefs.getMovieKeyword(),
                    selected ? "" : prefs.getFormatKeyword());
            discoveryCgv = selected
                    ? new CgvClient(prefs.getTheaterName(), prefs.getSiteNo(), "", prefs.getFormatKeyword())
                    : cgv;
            if (!wakeLock.isHeld()) wakeLock.acquire();
            executor.execute(this::runLoop);
        }
        return START_STICKY;
    }

    private void startAsForeground() {
        android.app.Notification n = notifications.foreground("초기화 중");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NotificationHelper.FOREGROUND_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
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
            notifications.alert("CGV 알리미 오류",
                    "감시가 중단됐어. 앱을 열어서 확인해줘.\n" + shortMsg(t), 9001, cgv.bookingUrl());
        } finally {
            running.set(false);
            prefs.setWatching(false);
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void initializeBaseline() throws Exception {
        prefs.setStatus("현재 일정/좌석 기준 만드는 중...");
        notifications.updateForeground(notifications.foreground("첫 실행: 현재 좌석 기준 확인 중"));

        if (prefs.hasSelectedShowtimes()) {
            List<String> dates = prefs.getSelectedDates();
            if (dates.isEmpty()) throw new IllegalStateException("선택된 상영 회차가 없어.");
            int success = 0;
            Exception last = null;
            for (String date : dates) {
                if (!running.get()) break;
                try {
                    List<CgvClient.Showtime> matches = cgv.fetchMatches(date);
                    snapshot(matches);
                    success++;
                } catch (Exception e) {
                    last = e;
                }
                Thread.sleep(250);
            }
            if (success == 0) {
                throw new IllegalStateException("선택한 회차 조회에 실패했어. " + (last == null ? "" : shortMsg(last)));
            }

            String latestMovieDate = findCurrentLatestSelectedMovieDate();
            if (latestMovieDate.isEmpty()) latestMovieDate = dates.get(dates.size() - 1);
            prefs.setLatestDate(latestMovieDate);
            prefs.setStatus("선택 회차 " + prefs.getSelectedShowtimeKeys().size() + "개 기준 설정 완료");
            notifications.updateForeground(notifications.foreground(
                    "선택 회차 + 새 날짜 감시 중 · " + prefs.getTheaterName()));
            return;
        }

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
            throw new IllegalStateException("CGV 시간표 조회에 실패했어. " + (last == null ? "" : shortMsg(last)));
        }

        if (latest.isEmpty()) latest = DateUtil.ymd(today);
        prefs.setLatestDate(latest);
        prefs.setStatus("기준 설정 완료: " + DateUtil.pretty(latest));
        notifications.updateForeground(notifications.foreground("새 날짜 + 취소표 감시 중 · " + prefs.targetLabel()));
    }

    private String findCurrentLatestSelectedMovieDate() {
        LocalDate today = DateUtil.today();
        String latest = "";
        for (int i = 0; i <= 17 && running.get(); i++) {
            String date = DateUtil.ymd(today.plusDays(i));
            try {
                List<CgvClient.Showtime> matches = filterSelectedMovies(discoveryCgv.fetchMatches(date));
                if (!matches.isEmpty()) latest = date;
            } catch (Exception ignored) {}
            try { Thread.sleep(180); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return latest;
    }

    private void snapshot(List<CgvClient.Showtime> matches) {
        Set<String> selected = prefs.getSelectedShowtimeKeys();
        for (CgvClient.Showtime s : matches) {
            if (!selected.isEmpty() && !selected.contains(s.key())) continue;
            if (s.freeSeats >= 0) prefs.setSeatCount(s.key(), s.freeSeats);
        }
    }

    private void checkOneDate() {
        try {
            boolean selectedMode = prefs.hasSelectedShowtimes();
            String date;
            LocalDate candidate;
            LocalDate latest;

            if (selectedMode) {
                List<String> dates = prefs.getSelectedDates();
                if (dates.isEmpty()) throw new IllegalStateException("선택된 회차가 없어.");
                date = dates.get(selectedDateIndex++ % dates.size());
                candidate = DateUtil.parseYmd(date);
                String latestText = prefs.getLatestDate();
                latest = latestText.isEmpty() ? candidate : DateUtil.parseYmd(latestText);
            } else {
                String latestText = prefs.getLatestDate();
                latest = latestText.isEmpty() ? DateUtil.today() : DateUtil.parseYmd(latestText);
                LocalDate today = DateUtil.today();
                int offset = offsetPattern[patternIndex++ % offsetPattern.length];
                if (offset <= 2) candidate = today.plusDays(offset);
                else {
                    LocalDate floor = latest.isBefore(today) ? today : latest;
                    candidate = floor.plusDays(offset - 2L);
                }
                date = DateUtil.ymd(candidate);
            }

            List<CgvClient.Showtime> matches = cgv.fetchMatches(date);
            prefs.setLastChecked(DateUtil.nowStamp());
            prefs.setConsecutiveErrors(0);

            if (!selectedMode) {
                boolean isNewDate = !matches.isEmpty() && candidate.isAfter(latest);
                if (isNewDate) {
                    notifyNewDate(date, matches);
                    prefs.setLatestDate(date);
                    patternIndex = 0;
                }
            }

            Set<String> selectedKeys = prefs.getSelectedShowtimeKeys();
            for (CgvClient.Showtime s : matches) {
                if (!selectedKeys.isEmpty() && !selectedKeys.contains(s.key())) continue;
                if (s.freeSeats < 0) continue;
                int old = prefs.getSeatCount(s.key());
                if (old != Integer.MIN_VALUE && s.freeSeats > old) notifySeatIncrease(s, old, s.freeSeats);
                prefs.setSeatCount(s.key(), s.freeSeats);
            }

            if (selectedMode && (++selectedLoopCount % 3 == 0)) {
                probeSelectedMovieNewDate();
            }

            String status = selectedMode
                    ? "선택 회차 + 새 날짜 감시 중 · 방금 " + DateUtil.pretty(date) + " 확인"
                    : "정상 감시 중 · " + prefs.targetLabel() + " · 방금 " + DateUtil.pretty(date) + " 확인";
            prefs.setStatus(status);
            notifications.updateForeground(notifications.foreground(status));
        } catch (Exception e) {
            int errors = prefs.getConsecutiveErrors() + 1;
            prefs.setConsecutiveErrors(errors);
            prefs.setLastChecked(DateUtil.nowStamp());
            prefs.setStatus("CGV 조회 실패 " + errors + "회 · 재시도 중");
            notifications.updateForeground(notifications.foreground("조회 실패 · 자동 재시도 중"));

            if (errors == 5 || errors == 20) {
                notifications.alert("CGV 알리미 조회 지연",
                        "CGV 조회가 연속 " + errors + "회 실패했어. 네트워크나 CGV 페이지 변경 가능성이 있어.\n" + shortMsg(e),
                        9100 + errors, cgv.bookingUrl());
            }
        }
    }

    private void probeSelectedMovieNewDate() {
        try {
            String latestText = prefs.getLatestDate();
            LocalDate latest = latestText.isEmpty() ? DateUtil.today() : DateUtil.parseYmd(latestText);
            LocalDate floor = latest.isBefore(DateUtil.today()) ? DateUtil.today() : latest;
            int offset = discoveryOffsets[discoveryProbeIndex++ % discoveryOffsets.length];
            LocalDate candidate = floor.plusDays(offset);
            String date = DateUtil.ymd(candidate);

            List<CgvClient.Showtime> matches = filterSelectedMovies(discoveryCgv.fetchMatches(date));
            if (!matches.isEmpty() && candidate.isAfter(latest)) {
                notifyNewDate(date, matches);
                prefs.setLatestDate(date);
                discoveryProbeIndex = 0;
            }
        } catch (Exception ignored) {
            // 선택 회차 감시는 계속 유지하고 새 날짜 탐색만 다음 차례에 재시도한다.
        }
    }

    private List<CgvClient.Showtime> filterSelectedMovies(List<CgvClient.Showtime> input) {
        Set<String> titles = prefs.getSelectedMovieTitles();
        if (titles.isEmpty()) return input;
        ArrayList<CgvClient.Showtime> out = new ArrayList<>();
        for (CgvClient.Showtime s : input) {
            String st = s.title == null ? "" : s.title.toLowerCase(Locale.KOREA);
            for (String title : titles) {
                String wanted = title == null ? "" : title.toLowerCase(Locale.KOREA);
                if (!wanted.isEmpty() && (st.equals(wanted) || st.contains(wanted) || wanted.contains(st))) {
                    out.add(s);
                    break;
                }
            }
        }
        return out;
    }

    private void notifySeatIncrease(CgvClient.Showtime s, int oldSeats, int newSeats) {
        int added = newSeats - oldSeats;
        String pretty = DateUtil.pretty(s.date);
        boolean soldOutReturn = oldSeats == 0 && newSeats > 0;

        String title;
        String body;
        if (soldOutReturn) {
            title = "🔥 매진 회차 좌석 발생! " + s.title + " " + s.time;
            body = prefs.getTheaterName() + " · " + pretty + " " + s.time + " · " + s.hall
                    + "\n매진 0석 → " + newSeats + "석\n가장 중요한 취소표야. 바로 좌석 위치를 확인해.";
            notifications.hotAlert(title, body,
                    7000 + Math.abs(s.key().hashCode() % 1500), cgv.bookingUrl());
        } else {
            title = "🎟️ " + s.title + " " + s.time + " 취소표 " + added + "석!";
            body = prefs.getTheaterName() + " · " + pretty + " " + s.time + " · " + s.hall
                    + "\n잔여 " + oldSeats + " → " + newSeats + "석\n선택한 회차에 자리가 생겼어. 지금 확인해.";
            notifications.alert(title, body,
                    4000 + Math.abs(s.key().hashCode() % 2500), cgv.bookingUrl());
        }
        prefs.setStatus("선택 회차 자리 발견! " + s.title + " · " + pretty + " " + s.time + " · " + newSeats + "석");

        try { kakao.sendMemo(title + "\n" + body); }
        catch (Exception ignored) {}
    }

    private void notifyNewDate(String date, List<CgvClient.Showtime> matches) {
        StringBuilder times = new StringBuilder();
        for (CgvClient.Showtime s : matches) {
            if (s.time == null || s.time.isEmpty()) continue;
            String token = s.time + " " + s.title;
            if (times.indexOf(token) >= 0) continue;
            if (times.length() > 0) times.append(", ");
            times.append(token);
            if (times.length() > 160) break;
        }

        String pretty = DateUtil.pretty(date);
        String timeText = times.length() == 0 ? "회차 확인 필요" : times.toString();
        String title = "🚨 " + watchedMovieLabel() + " 새 날짜 오픈!";
        String body = prefs.getTheaterName() + " · " + pretty + "\n" + timeText
                + "\n새 날짜가 열렸어. 원하는 회차를 앱에서 추가 선택해.";
        notifications.hotAlert(title, body,
                2000 + Math.abs((date + title).hashCode() % 3000), cgv.bookingUrl());

        try { kakao.sendMemo(title + "\n" + body); }
        catch (Exception ignored) {}
    }

    private String watchedMovieLabel() {
        Set<String> titles = prefs.getSelectedMovieTitles();
        if (titles.size() == 1) return titles.iterator().next();
        if (titles.size() > 1) return "선택 영화 " + titles.size() + "편";
        if (!prefs.getMovieKeyword().isEmpty()) return prefs.getMovieKeyword();
        return "CGV 영화";
    }

    private void sleepInterruptibly(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (running.get() && System.currentTimeMillis() < end) {
            try { Thread.sleep(Math.min(1000, end - System.currentTimeMillis())); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
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
    public IBinder onBind(Intent intent) { return null; }
}
