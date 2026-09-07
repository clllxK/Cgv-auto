package com.local.yongsanimaxwatcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int RED = Color.rgb(232, 32, 48);
    private static final int BG = Color.rgb(247, 247, 247);
    private static final int TEXT = Color.rgb(25, 25, 25);
    private static final int SUB = Color.rgb(105, 105, 105);
    private static final int BORDER = Color.rgb(225, 225, 225);

    private final String[][] THEATERS = {
            {"용산아이파크몰", "0013"}, {"왕십리", "0074"}, {"영등포", "0059"},
            {"강남", "0056"}, {"여의도", "0112"}, {"신촌아트레온", "0150"},
            {"홍대", "0191"}, {"건대입구", "0229"}, {"동대문", "0252"},
            {"천호", "0199"}, {"압구정", "0040"}, {"청담씨네시티", "0107"},
            {"구로", "0010"}, {"상봉", "0046"}, {"중계", "0131"}, {"직접 입력", ""}
    };

    private Prefs prefs;
    private KakaoClient kakao;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();

    private Spinner theaterSpinner;
    private LinearLayout dateRow;
    private LinearLayout movieList;
    private TextView statusPill;
    private TextView statusDetail;
    private TextView selectedCount;
    private TextView selectedPreview;
    private TextView emptyHint;
    private Button refreshButton;
    private Button startStop;

    private final Map<String, List<CgvClient.Showtime>> showsByDate = new LinkedHashMap<>();
    private String currentDate = "";
    private boolean loading = false;

    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            refreshStatus();
            ui.postDelayed(this, 1500);
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
        ui.postDelayed(() -> loadSchedule(false), 250);
    }

    private View buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);

        page.addView(buildHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(120));

        content.addView(buildWatchCard());
        content.addView(sectionTitle("극장"), top(20));
        content.addView(buildTheaterSelector(), top(8));

        content.addView(sectionTitle("날짜"), top(22));
        dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView dateScroll = new HorizontalScrollView(this);
        dateScroll.setHorizontalScrollBarEnabled(false);
        dateScroll.addView(dateRow);
        content.addView(dateScroll, top(8));

        LinearLayout movieHeader = new LinearLayout(this);
        movieHeader.setOrientation(LinearLayout.HORIZONTAL);
        movieHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView mh = sectionTitle("영화 · 상영시간");
        movieHeader.addView(mh, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        refreshButton = smallButton("새로고침");
        refreshButton.setOnClickListener(v -> loadSchedule(true));
        movieHeader.addView(refreshButton);
        content.addView(movieHeader, top(22));

        emptyHint = text("극장을 선택하면 현재 상영 중인 영화와 시간을 불러올게.", 14, false, SUB);
        emptyHint.setPadding(0, dp(22), 0, dp(22));
        content.addView(emptyHint);

        movieList = new LinearLayout(this);
        movieList.setOrientation(LinearLayout.VERTICAL);
        content.addView(movieList);

        content.addView(buildLegend(), top(18));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        page.addView(buildBottomBar());
        return page;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(14), dp(10), dp(14));
        header.setBackgroundColor(Color.WHITE);

        TextView logo = text("CGV WATCH", 22, true, RED);
        header.addView(logo, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button cgv = flatButton("CGV 열기");
        cgv.setOnClickListener(v -> openCgv());
        header.addView(cgv);

        Button settings = flatButton("설정");
        settings.setOnClickListener(v -> showSettings());
        header.addView(settings);
        return header;
    }

    private View buildWatchCard() {
        LinearLayout card = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("내 감시", 18, true, TEXT);
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        statusPill = text("정지", 12, true, SUB);
        statusPill.setGravity(Gravity.CENTER);
        statusPill.setPadding(dp(11), dp(5), dp(11), dp(5));
        row.addView(statusPill);
        card.addView(row);

        selectedCount = text("선택한 회차 0개", 24, true, TEXT);
        card.addView(selectedCount, top(12));

        selectedPreview = text("시간표에서 원하는 회차를 눌러 선택해.", 13, false, SUB);
        selectedPreview.setLineSpacing(dp(2), 1f);
        card.addView(selectedPreview, top(5));

        statusDetail = text("", 12, false, SUB);
        card.addView(statusDetail, top(12));
        return card;
    }

    private View buildTheaterSelector() {
        LinearLayout card = card();
        theaterSpinner = new Spinner(this);
        ArrayList<String> names = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < THEATERS.length; i++) {
            names.add("CGV " + THEATERS[i][0]);
            if (THEATERS[i][1].equals(prefs.getSiteNo())) selected = i;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names);
        theaterSpinner.setAdapter(adapter);
        theaterSpinner.setSelection(selected);
        theaterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (first) { first = false; return; }
                if (position == THEATERS.length - 1) {
                    showCustomTheaterDialog();
                    return;
                }
                applyTheater(THEATERS[position][0], THEATERS[position][1]);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        card.addView(theaterSpinner);

        TextView guide = text("극장만 고르면 영화/시간은 자동으로 불러와.", 12, false, SUB);
        card.addView(guide, top(6));
        return card;
    }

    private View buildLegend() {
        LinearLayout box = card();
        TextView title = text("알림 방식", 15, true, TEXT);
        box.addView(title);
        TextView t = text("🚨 새 날짜 오픈  ·  🎟 취소표  ·  🔥 매진 회차 좌석 발생\n선택한 회차만 좌석 변화를 추적하고, 선택한 영화의 새 날짜도 계속 확인해.", 13, false, SUB);
        t.setLineSpacing(dp(3), 1f);
        box.addView(t, top(7));
        return box;
    }

    private View buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(16), dp(10), dp(16), dp(14));
        bar.setBackgroundColor(Color.WHITE);

        Button clear = outlinedButton("선택 해제");
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("선택한 회차를 모두 해제할까?")
                .setPositiveButton("해제", (d, w) -> {
                    prefs.clearSelectedShowtimes();
                    prefs.resetBaseline();
                    renderMovies();
                    refreshStatus();
                })
                .setNegativeButton("취소", null).show());
        bar.addView(clear, new LinearLayout.LayoutParams(dp(104), dp(54)));

        startStop = redButton("감시 시작");
        startStop.setOnClickListener(v -> toggleWatch());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(54), 1f);
        slp.leftMargin = dp(10);
        bar.addView(startStop, slp);
        return bar;
    }

    private void applyTheater(String name, String siteNo) {
        if (siteNo.equals(prefs.getSiteNo()) && name.equals(prefs.getTheaterName())) return;
        if (prefs.isWatching()) stopWatchService();
        prefs.setTarget(name, siteNo, "", "");
        showsByDate.clear();
        currentDate = "";
        loadSchedule(false);
    }

    private void showCustomTheaterDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), 0);
        EditText name = input("극장명", false);
        EditText code = input("극장 코드 4자리", false);
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(name);
        box.addView(code, top(6));
        new AlertDialog.Builder(this)
                .setTitle("CGV 직접 입력")
                .setView(box)
                .setPositiveButton("적용", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String c = code.getText().toString().trim();
                    if (n.isEmpty() || !c.matches("\\d{4}")) {
                        toast("극장명과 4자리 코드를 정확히 입력해줘.");
                        return;
                    }
                    applyTheater(n, c);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void loadSchedule(boolean manual) {
        if (loading) return;
        loading = true;
        refreshButtonSafe(false, "불러오는 중…");
        emptyHint.setVisibility(View.VISIBLE);
        emptyHint.setText("CGV 시간표 불러오는 중…");
        movieList.removeAllViews();

        final String theater = prefs.getTheaterName();
        final String site = prefs.getSiteNo();
        loader.execute(() -> {
            Map<String, List<CgvClient.Showtime>> result = new LinkedHashMap<>();
            Exception last = null;
            CgvClient client = new CgvClient(theater, site, "", "");
            LocalDate today = DateUtil.today();
            for (int i = 0; i < 8; i++) {
                String date = DateUtil.ymd(today.plusDays(i));
                try {
                    List<CgvClient.Showtime> list = client.fetchMatches(date);
                    result.put(date, dedupe(list));
                } catch (Exception e) {
                    last = e;
                    result.put(date, new ArrayList<>());
                }
                try { Thread.sleep(160); } catch (InterruptedException ignored) { break; }
            }
            Exception error = last;
            runOnUiThread(() -> {
                loading = false;
                refreshButtonSafe(true, "새로고침");
                showsByDate.clear();
                showsByDate.putAll(result);
                if (currentDate.isEmpty() || !showsByDate.containsKey(currentDate)) {
                    currentDate = firstDateWithShows(result);
                    if (currentDate.isEmpty()) currentDate = DateUtil.ymd(DateUtil.today());
                }
                renderDates();
                renderMovies();
                if (allEmpty(result)) {
                    emptyHint.setVisibility(View.VISIBLE);
                    emptyHint.setText(error == null
                            ? "현재 불러올 수 있는 상영시간이 없어."
                            : "시간표를 못 불러왔어. 잠시 뒤 새로고침해줘.\n" + clean(error.getMessage()));
                } else if (manual) {
                    toast("최신 시간표로 갱신했어.");
                }
            });
        });
    }

    private List<CgvClient.Showtime> dedupe(List<CgvClient.Showtime> source) {
        LinkedHashMap<String, CgvClient.Showtime> map = new LinkedHashMap<>();
        for (CgvClient.Showtime s : source) map.put(s.key(), s);
        ArrayList<CgvClient.Showtime> out = new ArrayList<>(map.values());
        Collections.sort(out, Comparator.comparing((CgvClient.Showtime s) -> s.title)
                .thenComparing(s -> s.time));
        return out;
    }

    private void renderDates() {
        dateRow.removeAllViews();
        LocalDate today = DateUtil.today();
        DateTimeFormatter day = DateTimeFormatter.ofPattern("M/d", Locale.KOREA);
        DateTimeFormatter dow = DateTimeFormatter.ofPattern("E", Locale.KOREA);
        for (int i = 0; i < 8; i++) {
            LocalDate d = today.plusDays(i);
            String key = DateUtil.ymd(d);
            boolean active = key.equals(currentDate);
            int count = showsByDate.containsKey(key) ? showsByDate.get(key).size() : 0;

            TextView chip = text((i == 0 ? "오늘\n" : dow.format(d) + "\n") + day.format(d), 13, active, active ? Color.WHITE : TEXT);
            chip.setGravity(Gravity.CENTER);
            chip.setMinWidth(dp(64));
            chip.setPadding(dp(10), dp(9), dp(10), dp(9));
            chip.setBackground(roundBg(active ? RED : Color.WHITE, dp(14), active ? RED : BORDER, 1));
            chip.setAlpha(count == 0 ? 0.55f : 1f);
            chip.setOnClickListener(v -> {
                currentDate = key;
                renderDates();
                renderMovies();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(66), dp(62));
            lp.rightMargin = dp(8);
            dateRow.addView(chip, lp);
        }
    }

    private void renderMovies() {
        movieList.removeAllViews();
        List<CgvClient.Showtime> list = showsByDate.get(currentDate);
        if (list == null || list.isEmpty()) {
            emptyHint.setVisibility(View.VISIBLE);
            emptyHint.setText("이 날짜에는 불러온 상영 회차가 없어.");
            return;
        }
        emptyHint.setVisibility(View.GONE);

        LinkedHashMap<String, List<CgvClient.Showtime>> byMovie = new LinkedHashMap<>();
        for (CgvClient.Showtime s : list) {
            byMovie.computeIfAbsent(s.title, k -> new ArrayList<>()).add(s);
        }
        for (Map.Entry<String, List<CgvClient.Showtime>> entry : byMovie.entrySet()) {
            movieList.addView(movieCard(entry.getKey(), entry.getValue()), top(10));
        }
    }

    private View movieCard(String title, List<CgvClient.Showtime> shows) {
        LinearLayout card = card();
        TextView movieTitle = text(title, 18, true, TEXT);
        card.addView(movieTitle);

        String hallSummary = buildHallSummary(shows);
        TextView halls = text(hallSummary, 12, false, SUB);
        card.addView(halls, top(4));

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.VERTICAL);
        card.addView(times, top(10));

        int index = 0;
        while (index < shows.size()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 3; col++) {
                if (index >= shows.size()) {
                    View spacer = new View(this);
                    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(1), 1f);
                    if (col > 0) sp.leftMargin = dp(7);
                    row.addView(sp, sp);
                    continue;
                }
                CgvClient.Showtime s = shows.get(index++);
                boolean selected = prefs.getSelectedShowtimeKeys().contains(s.key());
                TextView b = showtimeButton(s, selected);
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(58), 1f);
                if (col > 0) bp.leftMargin = dp(7);
                row.addView(b, bp);
            }
            times.addView(row, times.getChildCount() == 0 ? new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) : top(7));
        }
        return card;
    }

    private TextView showtimeButton(CgvClient.Showtime s, boolean selected) {
        String seats = s.freeSeats >= 0 ? "\n" + s.freeSeats + "석" : "";
        TextView b = text(s.time + seats, 14, true, selected ? Color.WHITE : TEXT);
        b.setGravity(Gravity.CENTER);
        b.setBackground(roundBg(selected ? RED : Color.WHITE, dp(10), selected ? RED : BORDER, 1));
        b.setOnClickListener(v -> toggleShowtime(s));
        return b;
    }

    private void toggleShowtime(CgvClient.Showtime s) {
        Set<String> keys = prefs.getSelectedShowtimeKeys();
        Set<String> labels = prefs.getSelectedShowtimeLabels();
        String label = DateUtil.pretty(s.date) + " · " + s.title + " · " + s.time + " · " + s.hall;
        if (keys.contains(s.key())) {
            keys.remove(s.key());
            labels.remove(label);
        } else {
            keys.add(s.key());
            labels.add(label);
        }
        prefs.setSelectedShowtimes(keys, labels);
        renderMovies();
        refreshStatus();
    }

    private String buildHallSummary(List<CgvClient.Showtime> shows) {
        ArrayList<String> halls = new ArrayList<>();
        for (CgvClient.Showtime s : shows) {
            if (s.hall != null && !s.hall.isEmpty() && !halls.contains(s.hall)) halls.add(s.hall);
            if (halls.size() >= 3) break;
        }
        return halls.isEmpty() ? "상영관 정보 없음" : android.text.TextUtils.join(" · ", halls);
    }

    private void toggleWatch() {
        if (prefs.isWatching()) {
            stopWatchService();
            return;
        }
        if (!prefs.hasSelectedShowtimes()) {
            new AlertDialog.Builder(this)
                    .setTitle("먼저 시간을 선택해줘")
                    .setMessage("영화 시간표에서 알림 받을 회차를 하나 이상 눌러줘.")
                    .setPositiveButton("확인", null).show();
            return;
        }
        if (!kakao.hasRefreshToken()) {
            new AlertDialog.Builder(this)
                    .setTitle("카카오는 아직 연결 안 됐어")
                    .setMessage("폰 알림은 정상적으로 받을 수 있어. 그대로 감시를 시작할까?")
                    .setPositiveButton("시작", (d, w) -> startWatchService())
                    .setNegativeButton("취소", null).show();
        } else startWatchService();
    }

    private void startWatchService() {
        Intent i = new Intent(this, WatchService.class);
        i.setAction(WatchService.ACTION_START);
        startForegroundService(i);
        prefs.setWatching(true);
        refreshStatus();
        toast("감시 시작했어.");
    }

    private void stopWatchService() {
        Intent i = new Intent(this, WatchService.class);
        i.setAction(WatchService.ACTION_STOP);
        startService(i);
        prefs.setWatching(false);
        refreshStatus();
    }

    private void refreshStatus() {
        if (statusPill == null) return;
        boolean watching = prefs.isWatching();
        statusPill.setText(watching ? "● 감시 중" : "정지");
        statusPill.setTextColor(watching ? Color.WHITE : SUB);
        statusPill.setBackground(roundBg(watching ? RED : Color.rgb(238,238,238), dp(20), Color.TRANSPARENT, 0));

        int count = prefs.getSelectedShowtimeKeys().size();
        selectedCount.setText("선택한 회차 " + count + "개");
        selectedPreview.setText(compactSelectionPreview());
        statusDetail.setText("마지막 확인  " + prefs.getLastChecked()
                + "  ·  " + prefs.getIntervalSeconds() + "초 간격"
                + (kakao.hasRefreshToken() ? "  ·  카카오 연결됨" : ""));
        startStop.setText(watching ? "감시 중지" : "감시 시작");
        startStop.setBackground(roundBg(watching ? Color.rgb(50,50,50) : RED, dp(13), Color.TRANSPARENT, 0));
    }

    private String compactSelectionPreview() {
        ArrayList<String> labels = new ArrayList<>(prefs.getSelectedShowtimeLabels());
        Collections.sort(labels);
        if (labels.isEmpty()) return "시간표에서 원하는 회차를 눌러 선택해.";
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(4, labels.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append("\n");
            sb.append("• ").append(labels.get(i));
        }
        if (labels.size() > limit) sb.append("\n외 ").append(labels.size() - limit).append("개");
        return sb.toString();
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), 0);

        TextView kakaoTitle = text("카카오 알림", 16, true, TEXT);
        box.addView(kakaoTitle);
        EditText rest = input("Kakao REST API 키", false);
        rest.setText(kakao.getRestKey());
        box.addView(rest, top(6));
        EditText secret = input("Kakao Client Secret", true);
        secret.setText(kakao.getClientSecret());
        box.addView(secret, top(6));

        LinearLayout kakaoButtons = new LinearLayout(this);
        kakaoButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button login = outlinedButton(kakao.hasRefreshToken() ? "카카오 재로그인" : "카카오 로그인");
        Button test = outlinedButton("테스트");
        kakaoButtons.addView(login, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        tp.leftMargin = dp(8);
        kakaoButtons.addView(test, tp);
        box.addView(kakaoButtons, top(8));

        TextView intervalTitle = text("감시 간격", 16, true, TEXT);
        box.addView(intervalTitle, top(18));
        Spinner interval = new Spinner(this);
        String[] intervals = {"30초", "1분", "2분"};
        interval.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, intervals));
        interval.setSelection(prefs.getIntervalSeconds() == 120 ? 2 : prefs.getIntervalSeconds() == 60 ? 1 : 0);
        box.addView(interval, top(4));

        CheckBox auto = new CheckBox(this);
        auto.setText("재부팅 후 자동 감시 재시작");
        auto.setChecked(prefs.isAutoRestart());
        box.addView(auto, top(8));

        Button battery = outlinedButton("배터리 최적화 제외 설정");
        battery.setOnClickListener(v -> requestBatteryExemption());
        box.addView(battery, top(8));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("설정")
                .setView(box)
                .setPositiveButton("저장", null)
                .setNegativeButton("닫기", null)
                .create();
        dialog.setOnShowListener(x -> {
            login.setOnClickListener(v -> {
                kakao.saveAppKeys(rest.getText().toString(), secret.getText().toString());
                kakao.login(this, (ok, msg) -> runOnUiThread(() -> showResult(ok ? "카카오 연결 완료" : "카카오 연결 실패", msg)));
            });
            test.setOnClickListener(v -> {
                kakao.saveAppKeys(rest.getText().toString(), secret.getText().toString());
                kakao.sendTestAsync(this, (ok, msg) -> runOnUiThread(() -> showResult(ok ? "테스트 성공" : "테스트 실패", msg)));
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                kakao.saveAppKeys(rest.getText().toString(), secret.getText().toString());
                prefs.setIntervalSeconds(interval.getSelectedItemPosition() == 2 ? 120 : interval.getSelectedItemPosition() == 1 ? 60 : 30);
                prefs.setAutoRestart(auto.isChecked());
                refreshStatus();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void openCgv() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                CgvClient.bookingUrl(prefs.getTheaterName(), prefs.getSiteNo()))));
    }

    private String firstDateWithShows(Map<String, List<CgvClient.Showtime>> data) {
        for (Map.Entry<String, List<CgvClient.Showtime>> e : data.entrySet()) {
            if (e.getValue() != null && !e.getValue().isEmpty()) return e.getKey();
        }
        return "";
    }

    private boolean allEmpty(Map<String, List<CgvClient.Showtime>> data) {
        for (List<CgvClient.Showtime> list : data.values()) if (list != null && !list.isEmpty()) return false;
        return true;
    }

    private void refreshButtonSafe(boolean enabled, String label) {
        if (refreshButton == null) return;
        refreshButton.setEnabled(enabled);
        refreshButton.setText(label);
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
        l.setPadding(dp(16), dp(15), dp(16), dp(15));
        l.setBackground(roundBg(Color.WHITE, dp(16), BORDER, 1));
        return l;
    }

    private TextView sectionTitle(String s) { return text(s, 17, true, TEXT); }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private EditText input(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setPadding(dp(12), dp(9), dp(12), dp(9));
        if (password) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    private Button redButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(roundBg(RED, dp(13), Color.TRANSPARENT, 0));
        return b;
    }

    private Button outlinedButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(roundBg(Color.WHITE, dp(12), BORDER, 1));
        return b;
    }

    private Button smallButton(String s) {
        Button b = outlinedButton(s);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(11), dp(4), dp(11), dp(4));
        return b;
    }

    private Button flatButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        return b;
    }

    private GradientDrawable roundBg(int fill, int radius, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (strokeDp > 0) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private LinearLayout.LayoutParams top(int dpTop) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(dpTop);
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
