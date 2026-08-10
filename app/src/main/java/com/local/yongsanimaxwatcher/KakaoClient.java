package com.local.yongsanimaxwatcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class KakaoClient {
    public static final String REDIRECT_URI = "http://localhost:8765/callback";
    private static final String AUTHORIZE = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN = "https://kauth.kakao.com/oauth/token";
    private static final String MEMO = "https://kapi.kakao.com/v2/api/talk/memo/default/send";

    private final SecureStore store;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public interface Callback {
        void done(boolean ok, String message);
    }

    public KakaoClient(Context context) {
        store = new SecureStore(context.getApplicationContext());
    }

    public void saveAppKeys(String restKey, String clientSecret) {
        store.put("rest_key", restKey == null ? "" : restKey.trim());
        store.put("client_secret", clientSecret == null ? "" : clientSecret.trim());
    }

    public String getRestKey() {
        return store.get("rest_key");
    }

    public String getClientSecret() {
        return store.get("client_secret");
    }

    public boolean hasRefreshToken() {
        return !store.get("refresh_token").isEmpty();
    }

    public void login(Activity activity, Callback callback) {
        final String restKey = getRestKey();
        final String secret = getClientSecret();
        if (restKey.isEmpty() || secret.isEmpty()) {
            callback.done(false, "REST API 키와 Client Secret을 먼저 저장해줘.");
            return;
        }

        executor.execute(() -> {
            try (ServerSocket server = new ServerSocket(8765, 1, InetAddress.getByName("127.0.0.1"))) {
                server.setSoTimeout(180_000);

                String authUrl = AUTHORIZE
                        + "?response_type=code"
                        + "&client_id=" + enc(restKey)
                        + "&redirect_uri=" + enc(REDIRECT_URI)
                        + "&scope=" + enc("talk_message");

                activity.runOnUiThread(() -> {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
                    activity.startActivity(i);
                });

                String code;
                try (Socket socket = server.accept()) {
                    code = handleCallback(socket);
                }

                if (code == null || code.isEmpty()) {
                    throw new IllegalStateException("인가 코드를 받지 못했어.");
                }

                String form = "grant_type=authorization_code"
                        + "&client_id=" + enc(restKey)
                        + "&redirect_uri=" + enc(REDIRECT_URI)
                        + "&code=" + enc(code)
                        + "&client_secret=" + enc(secret);

                JSONObject token = new JSONObject(HttpUtil.postForm(TOKEN, form, null));
                saveTokenResponse(token, true);
                activity.runOnUiThread(() ->
                        callback.done(true, "카카오 로그인 완료. 이제 테스트 메시지를 보내봐."));
            } catch (Exception e) {
                activity.runOnUiThread(() ->
                        callback.done(false, "카카오 로그인 실패: " + clean(e.getMessage())));
            }
        });
    }

    private String handleCallback(Socket socket) throws Exception {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String request = br.readLine();
        if (request == null) throw new IllegalStateException("빈 콜백 요청");
        String[] parts = request.split(" ");
        if (parts.length < 2) throw new IllegalStateException("잘못된 콜백 요청");

        Uri uri = Uri.parse(parts[1]);
        String code = uri.getQueryParameter("code");
        String error = uri.getQueryParameter("error_description");

        String html;
        if (code != null) {
            html = "<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width'>"
                    + "<body style='font-family:sans-serif;padding:32px'>"
                    + "<h2>카카오 인증 완료 ✅</h2><p>이 창을 닫고 알리미 앱으로 돌아가면 돼.</p></body>";
        } else {
            html = "<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width'>"
                    + "<body style='font-family:sans-serif;padding:32px'>"
                    + "<h2>카카오 인증 실패</h2><p>" + escapeHtml(error) + "</p></body>";
        }

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        bw.write("HTTP/1.1 200 OK\r\n");
        bw.write("Content-Type: text/html; charset=utf-8\r\n");
        bw.write("Content-Length: " + bytes.length + "\r\n");
        bw.write("Connection: close\r\n\r\n");
        bw.write(html);
        bw.flush();

        if (code == null && error != null) throw new IllegalStateException(error);
        return code;
    }

    private void saveTokenResponse(JSONObject token, boolean requireRefresh) {
        String access = token.optString("access_token", "");
        String refresh = token.optString("refresh_token", "");
        long expiresIn = token.optLong("expires_in", 0);
        if (access.isEmpty()) throw new IllegalStateException("access_token 없음");
        if (requireRefresh && refresh.isEmpty()) throw new IllegalStateException("refresh_token 없음");

        store.put("access_token", access);
        if (!refresh.isEmpty()) store.put("refresh_token", refresh);
        long expiresAt = System.currentTimeMillis() + Math.max(60, expiresIn - 60) * 1000L;
        store.putLong("access_expires_at", expiresAt);
    }

    private synchronized String ensureAccessToken() throws Exception {
        String access = store.get("access_token");
        long expiresAt = store.getLong("access_expires_at", 0);
        if (!access.isEmpty() && expiresAt > System.currentTimeMillis() + 5 * 60_000L) {
            return access;
        }

        String refresh = store.get("refresh_token");
        String restKey = getRestKey();
        String secret = getClientSecret();
        if (refresh.isEmpty()) throw new IllegalStateException("카카오 로그인이 필요해.");

        String form = "grant_type=refresh_token"
                + "&client_id=" + enc(restKey)
                + "&refresh_token=" + enc(refresh)
                + "&client_secret=" + enc(secret);

        JSONObject token = new JSONObject(HttpUtil.postForm(TOKEN, form, null));
        saveTokenResponse(token, false);
        return store.get("access_token");
    }

    public void sendMemo(String message) throws Exception {
        String access = ensureAccessToken();

        JSONObject link = new JSONObject();
        link.put("web_url", CgvClient.BOOKING_URL);
        link.put("mobile_web_url", CgvClient.BOOKING_URL);

        JSONObject template = new JSONObject();
        template.put("object_type", "text");
        template.put("text", message);
        template.put("link", link);
        template.put("button_title", "CGV 예매 확인");

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + access);

        String form = "template_object=" + enc(template.toString());
        JSONObject result = new JSONObject(HttpUtil.postForm(MEMO, form, headers));
        if (result.optInt("result_code", -999) != 0) {
            throw new IllegalStateException("Kakao result_code=" + result.optInt("result_code"));
        }
    }

    public void sendTestAsync(Activity activity, Callback callback) {
        executor.execute(() -> {
            try {
                sendMemo("✅ 용아맥 오디세이 알리미 테스트 성공!\n"
                        + "새 IMAX 날짜가 열리면 이런 식으로 카톡이 와.");
                activity.runOnUiThread(() -> callback.done(true, "카카오톡 테스트 발송 성공"));
            } catch (Exception e) {
                activity.runOnUiThread(() ->
                        callback.done(false, "테스트 실패: " + clean(e.getMessage())));
            }
        });
    }

    public void logoutLocal() {
        store.clearTokens();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String clean(String s) {
        return s == null ? "알 수 없는 오류" : s.replace("\n", " ");
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
