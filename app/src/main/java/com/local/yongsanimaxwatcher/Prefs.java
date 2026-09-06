package com.local.yongsanimaxwatcher;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String NAME = "watcher_prefs";
    private final SharedPreferences p;

    public Prefs(Context context) {
        p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public int getIntervalSeconds() { return p.getInt("interval_seconds", 30); }
    public void setIntervalSeconds(int seconds) { p.edit().putInt("interval_seconds", seconds).apply(); }

    public String getTheaterName() { return p.getString("theater_name", "용산아이파크몰"); }
    public String getSiteNo() { return p.getString("site_no", "0013"); }
    public String getMovieKeyword() { return p.getString("movie_keyword", ""); }
    public String getFormatKeyword() { return p.getString("format_keyword", ""); }

    public void setTarget(String theaterName, String siteNo, String movieKeyword, String formatKeyword) {
        String tn = clean(theaterName);
        String sn = clean(siteNo);
        String mk = clean(movieKeyword);
        String fk = clean(formatKeyword);
        boolean changed = !tn.equals(getTheaterName()) || !sn.equals(getSiteNo())
                || !mk.equals(getMovieKeyword()) || !fk.equals(getFormatKeyword());
        p.edit()
                .putString("theater_name", tn)
                .putString("site_no", sn)
                .putString("movie_keyword", mk)
                .putString("format_keyword", fk)
                .apply();
        if (changed) resetBaseline();
    }

    public String targetLabel() {
        String movie = getMovieKeyword().isEmpty() ? "모든 영화" : getMovieKeyword();
        String format = getFormatKeyword().isEmpty() ? "전체관" : getFormatKeyword();
        return getTheaterName() + " · " + movie + " · " + format;
    }

    public String getLatestDate() { return p.getString("latest_date", ""); }
    public void setLatestDate(String value) { p.edit().putString("latest_date", value == null ? "" : value).apply(); }

    public int getSeatCount(String showtimeKey) { return p.getInt("seat_" + showtimeKey, Integer.MIN_VALUE); }
    public void setSeatCount(String showtimeKey, int value) { p.edit().putInt("seat_" + showtimeKey, value).apply(); }

    public String getLastChecked() { return p.getString("last_checked", "-"); }
    public void setLastChecked(String value) { p.edit().putString("last_checked", value).apply(); }
    public String getStatus() { return p.getString("status", "정지됨"); }
    public void setStatus(String value) { p.edit().putString("status", value).apply(); }
    public boolean isWatching() { return p.getBoolean("watching", false); }
    public void setWatching(boolean value) { p.edit().putBoolean("watching", value).apply(); }
    public boolean isAutoRestart() { return p.getBoolean("auto_restart", true); }
    public void setAutoRestart(boolean value) { p.edit().putBoolean("auto_restart", value).apply(); }
    public int getConsecutiveErrors() { return p.getInt("consecutive_errors", 0); }
    public void setConsecutiveErrors(int value) { p.edit().putInt("consecutive_errors", value).apply(); }

    public void resetBaseline() {
        SharedPreferences.Editor e = p.edit().remove("latest_date");
        for (String key : p.getAll().keySet()) {
            if (key.startsWith("seat_")) e.remove(key);
        }
        e.apply();
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
