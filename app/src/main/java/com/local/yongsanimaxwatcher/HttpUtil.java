package com.local.yongsanimaxwatcher;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class HttpUtil {
    public static String get(String url, Map<String, String> headers) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        c.setInstanceFollowRedirects(true);
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                c.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        int code = c.getResponseCode();
        String body = read(code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + ": " + abbreviate(body));
        }
        return body;
    }

    public static String postForm(String url, String formBody, Map<String, String> headers) throws Exception {
        byte[] data = formBody.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
        c.setRequestProperty("Content-Length", Integer.toString(data.length));
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                c.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        try (OutputStream os = c.getOutputStream()) {
            os.write(data);
        }
        int code = c.getResponseCode();
        String body = read(code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + ": " + abbreviate(body));
        }
        return body;
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int n;
            while ((n = br.read(buf)) >= 0) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private HttpUtil() {}
}
