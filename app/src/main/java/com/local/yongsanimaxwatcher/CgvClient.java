package com.local.yongsanimaxwatcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CgvClient {
    private static final String API = "https://cgv.co.kr/api/v1/booking/searchMovScnInfo";
    public static final String BOOKING_URL = "https://cgv.co.kr/cnm/movieBook/cinema";

    private final String theaterName;
    private final String siteNo;
    private final String movieKeyword;
    private final String formatKeyword;

    public CgvClient(String theaterName, String siteNo, String movieKeyword, String formatKeyword) {
        this.theaterName = safe(theaterName);
        this.siteNo = safe(siteNo);
        this.movieKeyword = safe(movieKeyword);
        this.formatKeyword = safe(formatKeyword);
    }

    public String bookingUrl() { return bookingUrl(theaterName, siteNo); }
    public static String bookingUrl(String theaterName, String siteNo) {
        return BOOKING_URL + "?siteNm=" + urlEncode(theaterName) + "&siteNo=" + urlEncode(siteNo);
    }

    public static final class Showtime {
        public final String date;
        public final String time;
        public final String hall;
        public final String title;
        public final String format;
        public final int freeSeats;
        public final int totalSeats;

        Showtime(String date, String time, String hall, String title, String format, int freeSeats, int totalSeats) {
            this.date = date; this.time = time; this.hall = hall; this.title = title; this.format = format;
            this.freeSeats = freeSeats; this.totalSeats = totalSeats;
        }
        public String key() { return date + "|" + time + "|" + hall + "|" + title; }
    }

    public List<Showtime> fetchMatches(String date) throws Exception {
        if (!siteNo.matches("\\d{4}")) throw new IllegalArgumentException("CGV 극장 코드는 4자리 숫자여야 해.");
        String url = API + "?coCd=A420&siteNo=" + siteNo + "&scnYmd=" + date + "&rtctlScopCd=08";
        Map<String,String> headers = new HashMap<>();
        headers.put("User-Agent","Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36");
        headers.put("Accept","application/json, text/plain, */*");
        headers.put("Accept-Language","ko-KR,ko;q=0.9,en;q=0.7");
        headers.put("Referer", bookingUrl());
        String body = HttpUtil.get(url, headers);
        Object root = body.trim().startsWith("[") ? new JSONArray(body.trim()) : new JSONObject(body.trim());
        LinkedHashMap<String,Showtime> unique = new LinkedHashMap<>();
        walk(root,date,unique);
        return new ArrayList<>(unique.values());
    }

    private void walk(Object node,String date,LinkedHashMap<String,Showtime> out) {
        if (node instanceof JSONObject) {
            JSONObject o=(JSONObject)node;
            String movie=first(o,"prodNm","expoProdNm","movNm","movieNm","movieName","prodName");
            String hall=first(o,"scnsNm","expoScnsNm","scnsName","theaterNm","hallNm","screenNm");
            String rawFormat=first(o,"movkndDsplNm","movKndNm","formatNm","specialTypeNm","screenTypeNm");
            String all=collectStrings(o);
            String hay=(movie+" "+hall+" "+rawFormat+" "+all).toLowerCase(Locale.KOREA);
            boolean titleMatch=movieKeyword.isEmpty() || hay.contains(movieKeyword.toLowerCase(Locale.KOREA));
            boolean formatMatch=formatKeyword.isEmpty() || "전체".equalsIgnoreCase(formatKeyword) || matchesFormat(hay,formatKeyword);
            if (titleMatch && formatMatch) {
                String time=normalizeTime(first(o,"scnsrtTm","scnStartTm","startTm","startTime","scnsrtTime"));
                if(!time.isEmpty() && !movie.isEmpty()) {
                    String actualHall=hall.isEmpty()?"상영관":hall;
                    String format=detectFormat(hay,rawFormat,actualHall);
                    int free=firstInt(o,-1,"frSeatCnt","remainSeatCnt","restSeatCnt","availableSeatCnt","seatRemainCnt");
                    int total=firstInt(o,-1,"stcnt","totSeatCnt","totalSeatCnt","seatCnt");
                    Showtime s=new Showtime(date,time,actualHall,movie,format,free,total);
                    out.put(s.key(),s);
                }
            }
            JSONArray names=o.names(); if(names!=null) for(int i=0;i<names.length();i++){Object c=o.opt(names.optString(i));if(c instanceof JSONObject||c instanceof JSONArray)walk(c,date,out);} 
        } else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++){Object c=a.opt(i);if(c instanceof JSONObject||c instanceof JSONArray)walk(c,date,out);}}
    }

    public static boolean isFormat(Showtime s,String wanted){
        if(wanted==null||wanted.trim().isEmpty()||"전체".equalsIgnoreCase(wanted))return true;
        String hay=(safe(s.format)+" "+safe(s.hall)).toLowerCase(Locale.KOREA);
        return matchesFormat(hay,wanted);
    }

    private static boolean matchesFormat(String hay,String wanted){
        String w=safe(wanted).toLowerCase(Locale.KOREA).replace(" ","");
        String h=safe(hay).toLowerCase(Locale.KOREA).replace(" ","");
        if(w.equals("ultra4dx")) return h.contains("ultra4dx")||h.contains("울트라4dx");
        if(w.equals("4dx")) return h.contains("4dx")&&!h.contains("ultra4dx")&&!h.contains("울트라4dx");
        if(w.equals("screenx")) return h.contains("screenx")||h.contains("스크린x");
        if(w.equals("imax")) return h.contains("imax")||h.contains("아이맥스");
        if(w.equals("soundx")) return h.contains("soundx")||h.contains("사운드x");
        return h.contains(w);
    }

    private static String detectFormat(String hay,String raw,String hall){
        String h=(safe(hay)+" "+safe(raw)+" "+safe(hall)).toLowerCase(Locale.KOREA).replace(" ","");
        if(h.contains("ultra4dx")||h.contains("울트라4dx"))return "ULTRA 4DX";
        if(h.contains("imax")||h.contains("아이맥스"))return "IMAX";
        if(h.contains("screenx")||h.contains("스크린x"))return "SCREENX";
        if(h.contains("4dx"))return "4DX";
        if(h.contains("soundx")||h.contains("사운드x"))return "SOUNDX";
        return raw.isEmpty()?"일반":raw;
    }

    private static String collectStrings(JSONObject o){StringBuilder sb=new StringBuilder();JSONArray n=o.names();if(n==null)return"";for(int i=0;i<n.length();i++){Object v=o.opt(n.optString(i));if(v instanceof String)sb.append(' ').append(v);}return sb.toString();}
    private static String first(JSONObject o,String...keys){for(String k:keys){Object v=o.opt(k);if(v!=null&&v!=JSONObject.NULL){String s=String.valueOf(v).trim();if(!s.isEmpty()&&!"null".equalsIgnoreCase(s))return s;}}return"";}
    private static int firstInt(JSONObject o,int f,String...keys){for(String k:keys){Object v=o.opt(k);if(v==null||v==JSONObject.NULL)continue;if(v instanceof Number)return((Number)v).intValue();try{String s=String.valueOf(v).replaceAll("[^0-9-]","");if(!s.isEmpty())return Integer.parseInt(s);}catch(Exception ignored){}}return f;}
    private static String normalizeTime(String t){if(t==null)return"";String d=t.replaceAll("[^0-9]","");if(d.length()==4)return d.substring(0,2)+":"+d.substring(2);if(d.length()>=6)return d.substring(0,2)+":"+d.substring(2,4);return t;}
    private static String safe(String s){return s==null?"":s.trim();}
    private static String urlEncode(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){throw new IllegalStateException(e);}}
}
