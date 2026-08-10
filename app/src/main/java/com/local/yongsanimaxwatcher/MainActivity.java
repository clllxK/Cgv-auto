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
import android.view.Gravity;
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
    private EditText restKey;
    private EditText clientSecret;
    private Spinner interval;
    private Button startStop;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
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

        TextView title = text("용아맥 오디세이 알리미", 26, true);
        root.addView(title);

        TextView subtitle = text(
                "용산아이파크몰 · 오디세이 · IMAX 새 날짜가 열리면 즉시 알려줘.", 14, false);
        subtitle.setTextColor(Color.DKGRAY);
        root.addView(subtitle, lpTop(6));

        LinearLayout card = card();
        status = text("상태 확인 중", 18, true);
        detail = text("", 13, false);
        detail.setTextColor(Color.DKGRAY);
        card.addView(status);
        card.addView(detail, lpTop(8));
        root.addView(card, lpTop(18));

        root.addView(section("1. 카카오 연결"), lpTop(22));

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
        test.setOnClickListener(v ->
                kakao.sendTestAsync(this, (ok, msg) -> showResult(ok ? "성공" : "실패", msg)));
        root.addView(test, lpTop(8));

        TextView kakaoGuide = text(
                "카카오디벨로퍼스에서 먼저:\n"
                        + "• 카카오 로그인 ON\n"
                        + "• REST API 키의 Redirect URI에 " + KakaoClient.REDIRECT_URI + " 등록\n"
                        + "• 동의항목에서 talk_message(카카오톡 메시지 전송) 사용\n"
                        + "• 제품 링크 관리 → 웹 도메인에 https://cgv.co.kr 등록\n"
                        + "• REST API 키와 Client Secret을 위에 입력", 13, false);
        kakaoGuide.setTextColor(Color.DKGRAY);
        root.addView(kakaoGuide, lpTop(10));

        root.addView(section("2. 감시 설정"), lpTop(24));

        interval = new Spinner(this);
        String[] labels = {"30초 (가장 빠름 · 배터리 사용↑)", "1분", "2분"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, labels);
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

        Button reset = button("현재 기준 날짜 다시 잡기");
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("기준 날짜 초기화")
                .setMessage("다음 감시 시작 때 현재 열려 있는 마지막 오디세이 IMAX 날짜를 다시 찾아. 기존 일정으로 알림은 보내지 않아.")
                .setPositiveButton("초기화", (d, w) -> {
                    prefs.resetBaseline();
                    toast("초기화했어. 감시를 다시 시작해줘.");
                })
                .setNegativeButton("취소", null)
                .show());
        root.addView(reset, lpTop(8));

        Button cgv = button("CGV 용산 예매 화면 열기");
        cgv.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(CgvClient.BOOKING_URL))));
        root.addView(cgv, lpTop(8));

        TextView note = text(
                "중요: 30초 감시는 화면이 꺼진 상태에서도 CPU를 깨워 두므로 배터리를 더 써. "
                        + "용아맥 오픈을 기다리는 기간에만 켜두는 걸 권장해. "
                        + "갤럭시 설정에서도 이 앱의 배터리를 '제한 없음'으로 두면 가장 안정적이야.", 13, false);
        note.setTextColor(Color.DKGRAY);
        root.addView(note, lpTop(14));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
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

        int seconds = interval.getSelectedItemPosition() == 2
                ? 120 : interval.getSelectedItemPosition() == 1 ? 60 : 30;
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

        detail.setText(
                prefs.getStatus()
                        + "\n마지막 확인: " + prefs.getLastChecked()
                        + "\n현재 기준 날짜: " + latestPretty
                        + "\n카카오: " + (kakao.hasRefreshToken() ? "연결됨" : "로그인 필요")
                        + "\n간격: " + prefs.getIntervalSeconds() + "초"
        );
        startStop.setText(watching ? "감시 중지" : "감시 시작");
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
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
        if (password) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
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
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void showResult(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("확인", null)
                .show();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(refresher);
        super.onDestroy();
    }
}
