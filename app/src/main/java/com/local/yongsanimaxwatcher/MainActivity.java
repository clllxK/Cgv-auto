package com.local.yongsanimaxwatcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private Prefs prefs;
    private KakaoClient kakao;
    private TextView status;
    private TextView detail;
    private TextView selectedSummary;
    private EditText theaterName;
    private EditText siteNo;
    private EditText movieKeyword;
    private EditText formatKeyword;
    private EditText restKey;
    private EditText clientSecret;
    private Spinner interval;
    private Button startStop;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();

    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            refreshStatus();
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        kakao = new KakaoClient(this);
        new NotificationHelper(this);
        requestNotificationPermission();
        setContentView(buildUi());
        ui.post(refresher);
    }

    private View buildUi() {
        int pad = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(246, 246, 246));

        root.addView(text("CGV 예매 알리미", 26, true));
        TextView subtitle = text("원하는 영화의 실제 상영 회차를 골라서 그 회차만 감시해.", 14, false);
        subtitle.setTextColor(Color.DKGRAY);
        root.addView(subtitle, lpTop(6));

        LinearLayout card = card();
        status = text("상태 확인 중", 18, true);
        detail = text("", 13, false);
        detail.setTextColor(Color.DKGRAY);
        card.addView(status);
        card.addView(detail, lpTop(8));
        TextView selectedTitle = text("🎬 감시 중인 회차", 16, true);
        card.addView(selectedTitle, lpTop(14));
        selectedSummary = text("선택된 회차 없음", 14, false);
        selectedSummary.setTextColor(Color.rgb(35, 35, 35));
        card.addView(selectedSummary, lpTop(6));
        root.addView(card, lpTop(18));

        root.addView(section("1. 극장 / 영화 / 회차 선택"), lpTop(22));

        theaterName = input("CGV 극장명 (예: 용산아이파크몰)", false);
        theaterName.setText(prefs.getTheaterName());
        root.addView(theaterName, lpTop(8));

        siteNo = input("CGV 극장 코드 4자리 (예: 0013)", false);
        siteNo.setInputType(InputType.TYPE_CLASS_NUMBER);
        siteNo.setText(prefs.getSiteNo());
        root.addView(siteNo, lpTop(8));

        movieKeyword = input("영화명 빠른 필터 (빈칸 = 전체 영화)", false);
        movieKeyword.setText(prefs.getMovieKeyword());
        root.addView(movieKeyword, lpTop(8));

        formatKeyword = input("상영관 필터 (예: IMAX, 4DX / 빈칸 = 전체)", false);
        formatKeyword.setText(prefs.getFormatKeyword());
        root.addView(formatKeyword, lpTop(8));

        Button loadShowtimes = button("영화/상영시간 불러와서 선택");
        loadShowtimes.setOnClickListener(v -> loadShowtimes(loadShowtimes));
        root.addView(loadShowtimes, lpTop(10));

        Button clearShowtimes = button("선택한 회차 모두 해제");
        clearShowtimes.setOnClickListener(v -> {
            prefs.clearSelectedShowtimes();
            prefs.resetBaseline();
            refreshStatus();
            toast("선택한 회차를 모두 해제했어.");
        });
        root.addView(clearShowtimes, lpTop(8));

        Button findCode = button("CGV 극장 코드 확인하기");
        findCode.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://cgv.co.kr/cnm/movieBook/cinema"))));
        root.addView(findCode, lpTop(8));

        TextView targetGuide = text(
                "극장/필터를 입력한 뒤 '영화/상영시간 불러오기'를 누르면 앞으로 8일간 실제 회차를 불러와. "
                        + "원하는 날짜·시간을 여러 개 체크하면 이후에는 체크한 회차만 알림이 와.", 13, false);
        targetGuide.setTextColor(Color.DKGRAY);
        root.addView(targetGuide, lpTop(10));

        root.addView(section("2. 카카오 연결"), lpTop(24));

        restKey = input("Kakao REST API 키", false);
        restKey.setText(kakao.getRestKey());
        root.addView(restKey, lpTop(8));

        clientSecret = input("Kakao Client Secret", true);
        clientSecret.setText(kakao.getClientSecret());
        root.addView(clientSecret, lpTop(8));

        Button save = button("키 저장");
        save.setOnClickListener(v -> {
            kakao.saveAppKeys(restKey.getText().toString(), clientSecret.getText().toString());
            toast("보안 저장소에 저장했어.");
        });
        root.addView(save, lpTop(8));

        Button login = button("카카오 로그인");
        login.setOnClickListener(v -> {
            kakao.saveAppKeys(restKey.getText().toString(), clientSecret.getText().toString());
            toast("브라우저에서 카카오 로그인을 완료해줘.");
            kakao.login(this, (ok, msg) -> showResult(ok ? "완료" : "실패", msg));
        });
        root.addView(login, lpTop(8));

        Button test = button("카카오톡 테스트 보내기");
        test.setOnClickListener(v -> kakao.sendTestAsync(this,
                (ok, msg) -> showResult(ok ? "성공" : "실패", msg)));
        root.addView(test, lpTop(8));

        root.addView(section("3. 감시 실행"), lpTop(24));

        interval = new Spinner(this);
        String[] labels = {"30초 (가장 빠름 · 배터리 사용↑)", "1분", "2분"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        interval.setAdapter(adapter);
        int current = prefs.getIntervalSeconds();
        interval.setSelection(current == 120 ? 2 : current == 60 ? 1 : 0);
        root.addView(interval, lpTop(8));

        CheckBox auto = new CheckBox(this);
        auto.setText("재부팅 후 자동으로 다시 감시");
        auto.setChecked(prefs.isAutoRestart());
        auto.setOnCheckedChangeListener((b, checked) -> prefs.setAutoRestart(checked));
        root.addView(auto, lpTop(8));

        startStop = button("감시 시작");
        startStop.setTextSize(18);
        startStop.setOnClickListener(v -> toggleWatch());
        root.addView(startStop, lpTop(10));

        Button battery = button("배터리 최적화 제외 설정");
        battery.setOnClickListener(v -> requestBatteryExemption());
        root.addView(battery, lpTop(8));

        Button reset = button("현재 좌석 기준 다시 잡기");
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("기준 초기화")
                .setMessage("다음 감시 시작 때 선택한 회차의 현재 잔여 좌석을 다시 기준으로 잡아. 기존 좌석 때문에 알림이 몰리는 걸 막아줘.")
                .setPositiveButton("초기화", (d, w) -> {
                    prefs.resetBaseline();
                    toast("초기화했어. 감시를 다시 시작해줘.");
                })
                .setNegativeButton("취소", null)
                .show());
        root.addView(reset, lpTop(8));

        Button cgv = button("현재 CGV 예매 화면 열기");
        cgv.setOnClickListener(v -> {
            saveTarget();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                    CgvClient.bookingUrl(prefs.getTheaterName(), prefs.getSiteNo()))));
        });
        root.addView(cgv, lpTop(8));

        TextView note = text(
                "선택 회차가 있으면 다른 영화나 다른 시간에 자리가 생겨도 알림하지 않아. "
                        + "현재는 CGV 시간표 API가 잔여 좌석 수만 제공해서 정확한 좌석 번호/중앙 명당 판별은 별도 좌석맵 연동이 필요해.", 13, false);
        note.setTextColor(Color.DKGRAY);
        root.addView(note, lpTop(14));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private void loadShowtimes(Button button) {
        if (!saveTarget()) return;
        button.setEnabled(false);
        button.setText("상영시간 불러오는 중...");

        String tn = prefs.getTheaterName();
        String sn = prefs.getSiteNo();
        String mk = prefs.getMovieKeyword();
        String fk = prefs.getFormatKeyword();

        loader.execute(() -> {
            ArrayList<CgvClient.Showtime> all = new ArrayList<>();
            Exception last = null;
            try {
                CgvClient client = new CgvClient(tn, sn, mk, fk);
                LocalDate today = DateUtil.today();
                for (int i = 0; i < 8; i++) {
                    try {
                        all.addAll(client.fetchMatches(DateUtil.ymd(today.plusDays(i))));
                    } catch (Exception e) {
                        last = e;
                    }
                    Thread.sleep(180);
                }
            } catch (Exception e) {
                last = e;
            }

            Exception error = last;
            runOnUiThread(() -> {
                button.setEnabled(true);
                button.setText("영화/상영시간 불러와서 선택");
                if (all.isEmpty()) {
                    showResult("회차를 못 불러왔어", error == null
                            ? "해당 극장/필터에 맞는 상영 회차가 없어."
                            : "CGV 조회 실패: " + clean(error.getMessage()));
                } else {
                    showShowtimePicker(all);
                }
            });
        });
    }

    private void showShowtimePicker(List<CgvClient.Showtime> source) {
        LinkedHashMap<String, CgvClient.Showtime> unique = new LinkedHashMap<>();
        for (CgvClient.Showtime s : source) unique.put(s.key(), s);
        ArrayList<CgvClient.Showtime> shows = new ArrayList<>(unique.values());

        String[] display = new String[shows.size()];
        boolean[] checked = new boolean[shows.size()];
        Set<String> current = prefs.getSelectedShowtimeKeys();
        for (int i = 0; i < shows.size(); i++) {
            CgvClient.Showtime s = shows.get(i);
            String seats = s.freeSeats >= 0 ? " · 잔여 " + s.freeSeats + "석" : "";
            display[i] = s.title + "\n" + DateUtil.pretty(s.date) + " " + s.time + " · " + s.hall + seats;
            checked[i] = current.contains(s.key());
        }

        new AlertDialog.Builder(this)
                .setTitle("알림 받을 회차 선택")
                .setMultiChoiceItems(display, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("선택 저장", (dialog, which) -> {
                    HashSet<String> keys = new HashSet<>();
                    HashSet<String> labels = new HashSet<>();
                    for (int i = 0; i < shows.size(); i++) {
                        if (!checked[i]) continue;
                        CgvClient.Showtime s = shows.get(i);
                        keys.add(s.key());
                        labels.add(DateUtil.pretty(s.date) + " · " + s.title + " · " + s.time + " · " + s.hall);
                    }
                    prefs.setSelectedShowtimes(keys, labels);
                    refreshStatus();
                    toast(keys.isEmpty() ? "선택한 회차가 없어." : keys.size() + "개 회차를 감시 대상으로 저장했어.");
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private boolean saveTarget() {
        String tn = theaterName.getText().toString().trim();
        String sn = siteNo.getText().toString().trim();
        String mk = movieKeyword.getText().toString().trim();
        String fk = formatKeyword.getText().toString().trim();
        if (tn.isEmpty()) {
            toast("CGV 극장명을 입력해줘.");
            return false;
        }
        if (!sn.matches("\\d{4}")) {
            toast("극장 코드는 4자리 숫자로 입력해줘. 예: 용산 0013");
            return false;
        }
        prefs.setTarget(tn, sn, mk, fk);
        return true;
    }

    private void toggleWatch() {
        if (prefs.isWatching()) {
            Intent i = new Intent(this, WatchService.class);
            i.setAction(WatchService.ACTION_STOP);
            startService(i);
            prefs.setWatching(false);
            refreshStatus();
            return;
        }

        if (!saveTarget()) return;
        int seconds = interval.getSelectedItemPosition() == 2 ? 120
                : interval.getSelectedItemPosition() == 1 ? 60 : 30;
        prefs.setIntervalSeconds(seconds);

        if (!prefs.hasSelectedShowtimes()) {
            new AlertDialog.Builder(this)
                    .setTitle("선택한 회차가 없어")
                    .setMessage("이대로 시작하면 예전 방식처럼 입력한 영화/상영관 전체를 감시해서 알림이 많이 올 수 있어. 먼저 원하는 회차를 선택하는 걸 추천해.")
                    .setPositiveButton("그래도 시작", (d, w) -> confirmKakaoAndStart())
                    .setNegativeButton("취소", null)
                    .show();
            return;
        }
        confirmKakaoAndStart();
    }

    private void confirmKakaoAndStart() {
        if (!kakao.hasRefreshToken()) {
            new AlertDialog.Builder(this)
                    .setTitle("카카오 로그인이 아직 안 됐어")
                    .setMessage("감시는 시작할 수 있지만 카톡은 안 오고 폰 알림만 와. 그래도 시작할까?")
                    .setPositiveButton("시작", (d, w) -> startWatchService())
                    .setNegativeButton("취소", null)
                    .show();
        } else {
            startWatchService();
        }
    }

    private void startWatchService() {
        Intent i = new Intent(this, WatchService.class);
        i.setAction(WatchService.ACTION_START);
        startForegroundService(i);
        prefs.setWatching(true);
        toast("감시 시작. 선택한 회차만 확인할게.");
        refreshStatus();
    }

    private void refreshStatus() {
        if (status == null) return;
        boolean watching = prefs.isWatching();
        status.setText(watching ? "● 감시 중" : "○ 정지됨");
        status.setTextColor(watching ? Color.rgb(0, 128, 65) : Color.DKGRAY);

        String latest = prefs.getLatestDate();
        String latestPretty = "-";
        if (latest != null && !latest.isEmpty()) {
            try { latestPretty = DateUtil.pretty(latest); } catch (Exception ignored) {}
        }

        detail.setText(prefs.getStatus()
                + "\n극장: " + prefs.getTheaterName()
                + "\n마지막 확인: " + prefs.getLastChecked()
                + "\n확인 기준일: " + latestPretty
                + "\n카카오: " + (kakao.hasRefreshToken() ? "연결됨" : "로그인 필요")
                + "\n간격: " + prefs.getIntervalSeconds() + "초");
        selectedSummary.setText(prefs.selectedShowtimesSummary());
        startStop.setText(watching ? "감시 중지" : "감시 시작");
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private void requestBatteryExemption() {
        try {
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
                toast("이미 배터리 최적화 제외 상태야.");
                return;
            }
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(16));
        l.setBackground(bg);
        return l;
    }

    private TextView section(String s) {
        TextView t = text(s, 18, true);
        t.setTextColor(Color.rgb(25, 25, 25));
        return t;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.BLACK);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private EditText input(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        if (password) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setMinHeight(dp(50));
        return b;
    }

    private LinearLayout.LayoutParams lpTop(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void showResult(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("확인", null).show();
        refreshStatus();
    }

    private static String clean(String s) {
        return s == null || s.trim().isEmpty() ? "알 수 없는 오류" : s.replace("\n", " ");
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(refresher);
        loader.shutdownNow();
        super.onDestroy();
    }
}
