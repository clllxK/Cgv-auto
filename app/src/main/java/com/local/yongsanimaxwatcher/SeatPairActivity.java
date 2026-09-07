package com.local.yongsanimaxwatcher;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class SeatPairActivity extends Activity {
    private static final long INTERVAL_MS = 30_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private TextView status;
    private Button watchButton;
    private boolean watching = false;
    private String lastPair = "";
    private String lastUrl = "";
    private String targetLabel = "";
    private NotificationHelper notifications;

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (!watching || web == null) return;
            status.setText("30초 감시 중 · 좌석표 새로고침 중…");
            web.reload();
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        notifications = new NotificationHelper(this);
        Prefs prefs = new Prefs(this);
        targetLabel = getIntent().getStringExtra("target");
        if (targetLabel == null || targetLabel.trim().isEmpty()) {
            if (!prefs.getSelectedShowtimeLabels().isEmpty()) {
                targetLabel = android.text.TextUtils.join(" / ", prefs.getSelectedShowtimeLabels());
            } else {
                targetLabel = prefs.getTheaterName() + " · 원하는 회차를 CGV에서 선택해줘";
            }
        }
        String url = getIntent().getStringExtra("url");
        if (url == null || url.trim().isEmpty()) url = CgvClient.bookingUrl(prefs.getTheaterName(), prefs.getSiteNo());
        setContentView(buildUi());
        configureWebView();
        web.loadUrl(url);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("👫 붙은 2자리 감시");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), dp(12), dp(16), dp(12));
        title.setBackgroundColor(Color.rgb(35,35,35));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView guide = new TextView(this);
        guide.setText("CGV에서 원하는 회차의 좌석 선택 화면까지 들어간 뒤 감시 시작을 눌러. 같은 행의 연속 2좌석이 보이면 즉시 강알림이 와. 자동 선택/선점/결제는 하지 않아.\n\n대상: " + targetLabel);
        guide.setTextSize(13);
        guide.setTextColor(Color.rgb(70,70,70));
        guide.setPadding(dp(14), dp(10), dp(14), dp(6));
        root.addView(guide);

        status = new TextView(this);
        status.setText("좌석 선택 화면으로 이동해줘.");
        status.setTextSize(14);
        status.setTextColor(Color.rgb(25,25,25));
        status.setPadding(dp(14), dp(8), dp(14), dp(8));
        root.addView(status);

        LinearLayout controls = new LinearLayout(this);
        controls.setPadding(dp(10), dp(4), dp(10), dp(8));
        Button back = button("←");
        back.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); else finish(); });
        controls.addView(back, new LinearLayout.LayoutParams(dp(58), dp(48)));

        Button check = button("지금 검사");
        check.setOnClickListener(v -> scanSeats());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(48), 1); cp.leftMargin = dp(6);
        controls.addView(check, cp);

        watchButton = button("30초 연석 감시 시작");
        watchButton.setOnClickListener(v -> toggleWatch());
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, dp(48), 2); wp.leftMargin = dp(6);
        controls.addView(watchButton, wp);
        root.addView(controls);

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);
        web.addJavascriptInterface(new SeatBridge(), "SeatBridge");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                lastUrl = url == null ? "" : url;
                if (watching) {
                    status.setText("30초 감시 중 · 좌석표 읽는 중…");
                    handler.postDelayed(() -> scanSeats(), 1800);
                }
            }
        });
    }

    private void toggleWatch() {
        watching = !watching;
        handler.removeCallbacks(refreshTask);
        if (watching) {
            lastPair = "";
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            watchButton.setText("연석 감시 중지");
            status.setText("30초 감시 시작 · 현재 좌석표 검사 중…");
            scanSeats();
            handler.postDelayed(refreshTask, INTERVAL_MS);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            watchButton.setText("30초 연석 감시 시작");
            status.setText("연석 감시 중지됨");
        }
    }

    private void scanSeats() {
        if (web == null) return;
        status.setText(watching ? "30초 감시 중 · 붙은 2자리 확인 중…" : "붙은 2자리 확인 중…");
        web.evaluateJavascript(SEAT_SCAN_JS, null);
    }

    private final class SeatBridge {
        @JavascriptInterface public void result(String pair, int count) {
            runOnUiThread(() -> {
                String p = pair == null ? "" : pair.trim();
                if (p.isEmpty()) {
                    lastPair = "";
                    status.setText((watching ? "30초 감시 중 · " : "") + "현재 확인된 연속 2좌석 없음 · 후보 좌석 " + count + "개");
                    return;
                }
                status.setText((watching ? "30초 감시 중 · " : "") + "연석 발견: " + p + " · 후보 좌석 " + count + "개");
                if (!p.equals(lastPair)) {
                    lastPair = p;
                    String title = "🔥 붙은 2자리 발생! " + p;
                    String body = targetLabel + "\n연속 좌석 " + p + " 발견 · 바로 CGV 좌석표를 확인해.";
                    notifications.hotPairAlert(title, body, 8200 + Math.abs((targetLabel + p).hashCode() % 700), lastUrl, targetLabel);
                    Toast.makeText(SeatPairActivity.this, "붙은 2자리 발견: " + p, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    // CGV가 현재 WebView에 정상적으로 렌더링한 좌석 DOM만 읽는다. 쿠키/토큰을 추출하거나 보호 요청을 재현하지 않는다.
    private static final String SEAT_SCAN_JS =
            "(function(){try{" +
            "var q='button,a,[role=button],[onclick],input,label';var ns=[].slice.call(document.querySelectorAll(q));" +
            "var out=[],seen={};" +
            "function bad(e){var c=((e.className||'')+' '+(e.id||'')+' '+(e.getAttribute('aria-disabled')||'')).toLowerCase();return e.disabled||e.getAttribute('aria-disabled')==='true'||/(disabled|sold|unavailable|reserved|occupied|used|complete|none)/.test(c);}" +
            "ns.forEach(function(e){if(bad(e))return;var ds='';try{ds=JSON.stringify(e.dataset||{});}catch(x){}" +
            "var sig=((e.className||'')+' '+(e.id||'')+' '+ds+' '+(e.getAttribute('name')||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')).toLowerCase();" +
            "if(!/(seat|좌석)/.test(sig))return;var t=((e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')+' '+(e.getAttribute('data-seat-no')||'')+' '+(e.getAttribute('data-seatno')||'')+' '+(e.value||'')+' '+(e.innerText||'')+' '+ds).replace(/\\s+/g,' ');" +
            "var m=t.match(/(?:^|[^A-Z0-9])([A-Z])\\s*[- ]?\\s*0*(\\d{1,3})(?:[^0-9]|$)/i);if(!m)return;var row=m[1].toUpperCase(),num=parseInt(m[2],10);if(!num)return;var k=row+'-'+num;if(!seen[k]){seen[k]=1;out.push({r:row,n:num});}});" +
            "out.sort(function(a,b){return a.r===b.r?a.n-b.n:(a.r<b.r?-1:1);});var pair='';for(var i=0;i<out.length-1;i++){if(out[i].r===out[i+1].r&&out[i+1].n===out[i].n+1){pair=out[i].r+out[i].n+' · '+out[i+1].r+out[i+1].n;break;}}" +
            "SeatBridge.result(pair,out.length);}catch(e){SeatBridge.result('',0);}})();";

    private Button button(String text) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(13); return b;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        watching = false;
        handler.removeCallbacksAndMessages(null);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (web != null) { web.removeJavascriptInterface("SeatBridge"); web.destroy(); }
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
