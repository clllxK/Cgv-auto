package com.local.yongsanimaxwatcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CgvClient {
    public static final String SITE_NO = "0013";
    public static final String MOVIE = "오디세이";
    public static final String FORMAT = "IMAX";
    public static final String BOOKING_URL =
            "https://cgv.co.kr/cnm/movieBook/cinema?siteNm="
                    + urlEncode("용산아이파크몰") + "&siteNo=0013";

    private static final String API =
            "https://cgv.co.kr/api/v1/booking/searchMovScnInfo";

    public static final class Showtime {
        public final String date;
        public final String time;
        public final String hall;
        public final String title;
        public final int freeSeats;
        public final int totalSeats;

        Showtime(String date, String time, String hall, String title, int freeSeats, int totalSeats) {
            this.date = date;
            this.time = time;
            this.hall = hall;
            this.title = title;
            this.freeSeats = freeSeats;
            this.totalSeats = totalSeats;
        }

        public String key() {
            return date + "|" + time + "|" + hall;
        }
    }

    public List<Showtime> fetchMatches(String date) throws Exception {
        String url = API
                + "?coCd=A420"
                + "&siteNo=" + SITE_NO
                + "&scnYmd=" + date
                + "&rtctlScopCd=08";

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36");
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.7");
        headers.put("Referer", BOOKING_URL);

        String body = HttpUtil.get(url, headers);
        Object root;
        String trimmed = body.trim();
        if (trimmed.startsWith("[")) root = new JSONArray(trimmed);
        else root = new JSONObject(trimmed);

        LinkedHashMap<String, Showtime> unique = new LinkedHashMap<>();
        walk(root, date, unique);
        return new ArrayList<>(unique.values());
    }

    private void walk(Object node, String date, LinkedHashMap<String, Showtime> out) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;

            String movie = first(o,
                    "prodNm", "expoProdNm", "movNm", "movieNm", "movieName", "prodName");
            String hall = first(o,
                    "scnsNm", "expoScnsNm", "scnsName", "theaterNm", "hallNm", "screenNm");
            String format = first(o,
                    "movkndDsplNm", "movKndNm", "formatNm", "specialTypeNm", "screenTypeNm");

            String allStrings = collectStrings(o);
            String combined = (hall + " " + format + " " + allStrings).toUpperCase();
            boolean titleMatch = movie.contains(MOVIE) || allStrings.contains(MOVIE);
            boolean formatMatch = combined.contains(FORMAT);

            if (titleMatch && formatMatch) {
                String time = normalizeTime(first(o,
                        "scnsrtTm", "scnStartTm", "startTm", "startTime", "scnsrtTime"));
                String actualHall = hall.isEmpty() ? "IMAX" : hall;
                String actualMovie = movie.isEmpty() ? MOVIE : movie;
                int freeSeats = firstInt(o, -1,
                        "frSeatCnt", "remainSeatCnt", "restSeatCnt", "availableSeatCnt", "seatRemainCnt");
                int totalSeats = firstInt(o, -1,
                        "stcnt", "totSeatCnt", "totalSeatCnt", "seatCnt");
                String key = date + "|" + time + "|" + actualHall;
                out.put(key, new Showtime(date, time, actualHall, actualMovie, freeSeats, totalSeats));
            }

            JSONArray names = o.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    Object child = o.opt(names.optString(i));
                    if (child instanceof JSONObject || child instanceof JSONArray) {
                        walk(child, date, out);
                    }
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) {
                Object child = a.opt(i);
                if (child instanceof JSONObject || child instanceof JSONArray) {
                    walk(child, date, out);
                }
            }
        }
    }

    private String collectStrings(JSONObject o) {
        StringBuilder sb = new StringBuilder();
        JSONArray names = o.names();
        if (names == null) return "";
        for (int i = 0; i < names.length(); i++) {
            Object v = o.opt(names.optString(i));
            if (v instanceof String) sb.append(' ').append(v);
        }
        return sb.toString();
    }

    private static String first(JSONObject o, String... keys) {
        for (String key : keys) {
            Object v = o.opt(key);
            if (v != null && v != JSONObject.NULL) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s;
            }
        }
        return "";
    }

    private static int firstInt(JSONObject o, int fallback, String... keys) {
        for (String key : keys) {
            Object v = o.opt(key);
            if (v == null || v == JSONObject.NULL) continue;
            if (v instanceof Number) return ((Number) v).intValue();
            try {
                String s = String.valueOf(v).replaceAll("[^0-9-]", "");
                if (!s.isEmpty()) return Integer.parseInt(s);
            } catch (Exception ignored) {}
        }
        return fallback;
    }

    private static String normalizeTime(String t) {
        if (t == null) return "";
        String d = t.replaceAll("[^0-9]", "");
        if (d.length() == 4) return d.substring(0, 2) + ":" + d.substring(2);
        if (d.length() >= 6) return d.substring(0, 2) + ":" + d.substring(2, 4);
        return t;
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
