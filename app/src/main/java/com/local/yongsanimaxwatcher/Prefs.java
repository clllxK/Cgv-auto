package com.local.yongsanimaxwatcher;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

public final class Prefs {
    private static final String NAME="watcher_prefs"; private final SharedPreferences p;
    public Prefs(Context c){p=c.getSharedPreferences(NAME,Context.MODE_PRIVATE);}
    public int getIntervalSeconds(){return p.getInt("interval_seconds",30);} public void setIntervalSeconds(int s){p.edit().putInt("interval_seconds",s).apply();}
    public String getTheaterName(){return p.getString("theater_name","용산아이파크몰");} public String getSiteNo(){return p.getString("site_no","0013");}
    public String getMovieKeyword(){return p.getString("movie_keyword","");} public String getFormatKeyword(){return p.getString("format_keyword","IMAX");}
    public String getUiMovie(){return p.getString("ui_movie","");} public void setUiMovie(String t){p.edit().putString("ui_movie",clean(t)).apply();}
    public void setFormatKeyword(String f){String n=clean(f);if(n.isEmpty())n="전체";if(!n.equals(getFormatKeyword())){p.edit().putString("format_keyword",n).apply();clearSelectedShowtimes();resetBaseline();}}
    public void setTarget(String theaterName,String siteNo,String movieKeyword,String formatKeyword){String tn=clean(theaterName),sn=clean(siteNo),mk=clean(movieKeyword),fk=clean(formatKeyword);if(fk.isEmpty())fk=getFormatKeyword();boolean changed=!tn.equals(getTheaterName())||!sn.equals(getSiteNo());p.edit().putString("theater_name",tn).putString("site_no",sn).putString("movie_keyword",mk).putString("format_keyword",fk).apply();if(changed){clearSelectedShowtimes();clearNewDateMovies();setUiMovie("");resetBaseline();}}
    public Set<String> getSelectedShowtimeKeys(){return new HashSet<>(p.getStringSet("selected_showtime_keys",Collections.emptySet()));} public Set<String> getSelectedShowtimeLabels(){return new HashSet<>(p.getStringSet("selected_showtime_labels",Collections.emptySet()));}
    public void setSelectedShowtimes(Set<String>k,Set<String>l){p.edit().putStringSet("selected_showtime_keys",new HashSet<>(k)).putStringSet("selected_showtime_labels",new HashSet<>(l)).apply();resetBaseline();}
    public void clearSelectedShowtimes(){p.edit().remove("selected_showtime_keys").remove("selected_showtime_labels").apply();} public boolean hasSelectedShowtimes(){return !getSelectedShowtimeKeys().isEmpty();}
    public Set<String> getNewDateMovies(){return new HashSet<>(p.getStringSet("new_date_movies",Collections.emptySet()));} public boolean isNewDateMovie(String t){return getNewDateMovies().contains(clean(t));}
    public void toggleNewDateMovie(String t){Set<String>s=getNewDateMovies();String x=clean(t);if(s.contains(x))s.remove(x);else s.add(x);p.edit().putStringSet("new_date_movies",new HashSet<>(s)).apply();resetBaseline();}
    public void setOnlyNewDateMovie(String t){HashSet<String>s=new HashSet<>();if(!clean(t).isEmpty())s.add(clean(t));p.edit().putStringSet("new_date_movies",s).apply();resetBaseline();}
    public void clearNewDateMovies(){p.edit().remove("new_date_movies").apply();} public Set<String> getSelectedMovieTitles(){return getNewDateMovies();}
    public boolean hasAnyWatch(){return hasSelectedShowtimes()||!getNewDateMovies().isEmpty();}
    public List<String> getSelectedDates(){HashSet<String>d=new HashSet<>();for(String k:getSelectedShowtimeKeys()){int i=k.indexOf('|');if(i>0)d.add(k.substring(0,i));}ArrayList<String>o=new ArrayList<>(d);Collections.sort(o);return o;}
    public String getLatestDate(){return p.getString("latest_date","");} public void setLatestDate(String v){p.edit().putString("latest_date",v==null?"":v).apply();}
    public int getSeatCount(String k){return p.getInt("seat_"+k,Integer.MIN_VALUE);} public void setSeatCount(String k,int v){p.edit().putInt("seat_"+k,v).apply();}
    public String getLastChecked(){return p.getString("last_checked","-");} public void setLastChecked(String v){p.edit().putString("last_checked",v).apply();} public String getStatus(){return p.getString("status","정지됨");} public void setStatus(String v){p.edit().putString("status",v).apply();}
    public boolean isWatching(){return p.getBoolean("watching",false);} public void setWatching(boolean v){p.edit().putBoolean("watching",v).apply();} public boolean isAutoRestart(){return p.getBoolean("auto_restart",true);} public void setAutoRestart(boolean v){p.edit().putBoolean("auto_restart",v).apply();}
    public int getConsecutiveErrors(){return p.getInt("consecutive_errors",0);} public void setConsecutiveErrors(int v){p.edit().putInt("consecutive_errors",v).apply();}
    public String targetLabel(){String m=getNewDateMovies().isEmpty()?getUiMovie():android.text.TextUtils.join(",",getNewDateMovies());return getTheaterName()+" · "+m+" · "+getFormatKeyword();}
    public void resetBaseline(){SharedPreferences.Editor e=p.edit().remove("latest_date");for(String k:p.getAll().keySet())if(k.startsWith("seat_"))e.remove(k);e.apply();}
    private static String clean(String s){return s==null?"":s.trim();}
}
