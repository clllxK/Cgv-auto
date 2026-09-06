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

public final class MainActivity extends Activity {
    private Prefs prefs;
    private KakaoClient kakao;
    private TextView status;
    private TextView detail;
    private EditText theaterName;
    private EditText siteNo;
    private EditText movieKeyword;
    private EditText formatKeyword;
    private EditText restKey;
    private EditText clientSecret;
    private Spinner interval;
    private Button startStop;
    private final Handler ui = new Handler(Looper.getMainLooper());

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
        TextView subtitle = text("원하는 CGV 극장과 영화를 지정하면 새 날짜와 취소표를 감시해.", 14, false);
        subtitle.setTextColor(Color.DKGRAY);
        root.addView(subtitle, lpTop(6));

        LinearLayout card = card();
        status = text("상태 확인 중", 18, true);
        detail = text("", 13, false);
        detail.setTextColor(Color.DKGRAY);
        card.addView(status);
        card.addView(detail, lpTop(8));
        root.addView(card, lpTop(18));

        root.addView(section("1. 감시 대상"), lpTop(22));

        theaterName = input("CGV 극장명 (예: 용산아이파크몰)", false);
        theaterName.setText(prefs.getTheaterName());
        root.addView(theaterName, lpTop(8));

        siteNo = input("CGV 극장 코드 4자리 (예: 0013)", false);
        siteNo.setInputType(InputType.TYPE_CLASS_NUMBER);
        siteNo.setText(prefs.getSiteNo());
        root.addView(siteNo, lpTop(8));

        movieKeyword = input("영화명 (빈칸 = 모든 영화)", false);
        movieKeyword.setText(prefs.getMovieKeyword());
        root.addView(movieKeyword, lpTop(8));

        formatKeyword = input("포맷/특별관 (예: IMAX, 4DX / 빈칸 = 전체)", false);
        formatKeyword.setText(prefs.getFormatKeyword());
        root.addView(formatKeyword, lpTop(8));

        Button saveTarget = button("감시 대상 저장");
        saveTarget.setOnClickListener(v -> {
            if (saveTarget()) toast("감시 대상을 저장했어. 대상이 바뀌면 기준 좌석도 자동 초기화돼.");
        });
        root.addView(saveTarget, lpTop(8));

        Button findCode = button("CGV 극장 코드 확인하기");
        findCode.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://cgv.co.kr/cnm/movieBook/cinema"))));
        root.addView(findCode, lpTop(8));

        TextView targetGuide = text(
                "극장 코드는 CGV 극장별 예매 주소의 siteNo= 뒤 4자리야. "
                        + "예: 용산아이파크몰 0013 · 왕십리 0074 · 영등포 0059 · 강남 0056. "
                        + "영화명을 비우면 해당 극장의 모든 영화 취소표/새 날짜를 감시해.", 13, false);
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

        Button reset = button("현재 일정/좌석 기준 다시 잡기");
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("기준 초기화")
                .setMessage("다음 감시 시작 때 현재 일정과 잔여 좌석을 다시 기준으로 잡아. 기존 좌석으로 오탐 알림이 가는 걸 막아줘.")
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
                "빈 영화명 + 빈 포맷으로 두면 그 극장의 모든 영화를 감시할 수 있지만 알림이 많아질 수 있어. "
                        + "특정 신작만 노릴 때는 영화명을 넣는 게 좋아. 30초 감시는 배터리 사용량도 커져.", 13, false);
        note.setTextColor(Color.DKGRAY);
        root.addView(note, lpTop(14));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
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
        toast("감시 시작. 상단에 고정 알림이 뜰 거야.");
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
                + "\n대상: " + prefs.targetLabel()
                + "\n마지막 확인: " + prefs.getLastChecked()
                + "\n현재 기준 날짜: " + latestPretty
                + "\n카카오: " + (kakao.hasRefreshToken() ? "연결됨" : "로그인 필요")
                + "\n간격: " + prefs.getIntervalSeconds() + "초");
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

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(refresher);
        super.onDestroy();
    }
}
