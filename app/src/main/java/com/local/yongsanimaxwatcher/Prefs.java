package com.local.yongsanimaxwatcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Prefs {
    private static final String NAME = "watcher_prefs";
    private final SharedPreferences p;
    public Prefs(Context context) { p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE); }
    public int getIntervalSeconds() { return p.getInt("interval_seconds", 30); }
    public void setIntervalSeconds(int seconds) { p.edit().putInt("interval_seconds", seconds).apply(); }
    public String getTheaterName() { return p.getString("theater_name", "용산아이파크몰"); }
    public String getSiteNo() { return p.getString("site_no", "0013"); }
    public String getMovieKeyword() { return p.getString("movie_keyword", ""); }
    public String getFormatKeyword() { return p.getString("format_keyword", ""); }
    public void setTarget(String theaterName, String siteNo, String movieKeyword, String formatKeyword) {
        String tn=clean(theaterName), sn=clean(siteNo), mk=clean(movieKeyword), fk=clean(formatKeyword);
        boolean changed=!tn.equals(getTheaterName())||!sn.equals(getSiteNo())||!mk.equals(getMovieKeyword())||!fk.equals(getFormatKeyword());
        p.edit().putString("theater_name",tn).putString("site_no",sn).putString("movie_keyword",mk).putString("format_keyword",fk).apply();
        if(changed){ clearSelectedShowtimes(); clearNewDateMovies(); resetBaseline(); }
    }
    public Set<String> getSelectedShowtimeKeys(){ return new HashSet<>(p.getStringSet("selected_showtime_keys", Collections.emptySet())); }
    public Set<String> getSelectedShowtimeLabels(){ return new HashSet<>(p.getStringSet("selected_showtime_labels", Collections.emptySet())); }
    public void setSelectedShowtimes(Set<String> keys,Set<String> labels){ p.edit().putStringSet("selected_showtime_keys",new HashSet<>(keys)).putStringSet("selected_showtime_labels",new HashSet<>(labels)).apply(); resetBaseline(); }
    public void clearSelectedShowtimes(){ p.edit().remove("selected_showtime_keys").remove("selected_showtime_labels").apply(); }
    public boolean hasSelectedShowtimes(){ return !getSelectedShowtimeKeys().isEmpty(); }
    public Set<String> getNewDateMovies(){ return new HashSet<>(p.getStringSet("new_date_movies",Collections.emptySet())); }
    public boolean isNewDateMovie(String title){ return getNewDateMovies().contains(clean(title)); }
    public void toggleNewDateMovie(String title){ Set<String>s=getNewDateMovies(); String t=clean(title); if(s.contains(t))s.remove(t);else s.add(t); p.edit().putStringSet("new_date_movies",new HashSet<>(s)).apply(); resetBaseline(); }
    public void clearNewDateMovies(){ p.edit().remove("new_date_movies").apply(); }
    public Set<String> getSelectedMovieTitles(){ return getNewDateMovies(); }
    public boolean hasAnyWatch(){ return hasSelectedShowtimes() || !getNewDateMovies().isEmpty(); }
    public List<String> getSelectedDates(){ HashSet<String>d=new HashSet<>(); for(String key:getSelectedShowtimeKeys()){int i=key.indexOf('|');if(i>0)d.add(key.substring(0,i));} ArrayList<String>o=new ArrayList<>(d);Collections.sort(o);return o; }
    public String selectedShowtimesSummary(){ List<String>l=new ArrayList<>(getSelectedShowtimeLabels());Collections.sort(l);if(l.isEmpty())return "선택된 회차 없음";StringBuilder b=new StringBuilder();for(String x:l){if(b.length()>0)b.append('\n');b.append("• ").append(x);}return b.toString(); }
    public String targetLabel(){ return getTheaterName()+" · 새 날짜 "+getNewDateMovies().size()+"개 · 취소표 "+getSelectedShowtimeKeys().size()+"개"; }
    public String getLatestDate(){return p.getString("latest_date","");} public void setLatestDate(String v){p.edit().putString("latest_date",v==null?"":v).apply();}
    public int getSeatCount(String k){return p.getInt("seat_"+k,Integer.MIN_VALUE);} public void setSeatCount(String k,int v){p.edit().putInt("seat_"+k,v).apply();}
    public String getLastChecked(){return p.getString("last_checked","-");} public void setLastChecked(String v){p.edit().putString("last_checked",v).apply();}
    public String getStatus(){return p.getString("status","정지됨");} public void setStatus(String v){p.edit().putString("status",v).apply();}
    public boolean isWatching(){return p.getBoolean("watching",false);} public void setWatching(boolean v){p.edit().putBoolean("watching",v).apply();}
    public boolean isAutoRestart(){return p.getBoolean("auto_restart",true);} public void setAutoRestart(boolean v){p.edit().putBoolean("auto_restart",v).apply();}
    public int getConsecutiveErrors(){return p.getInt("consecutive_errors",0);} public void setConsecutiveErrors(int v){p.edit().putInt("consecutive_errors",v).apply();}
    public void resetBaseline(){SharedPreferences.Editor e=p.edit().remove("latest_date");for(String k:p.getAll().keySet())if(k.startsWith("seat_"))e.remove(k);e.apply();}
    private static String clean(String s){return s==null?"":s.trim();}
}
