package com.local.yongsanimaxwatcher;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int RED=Color.rgb(232,32,48), BG=Color.rgb(246,246,246), TEXT=Color.rgb(28,28,28), SUB=Color.rgb(105,105,105), BORDER=Color.rgb(224,224,224), DARK=Color.rgb(35,35,35);
    private static final String[] FORMATS={"IMAX","4DX","SCREENX","ULTRA 4DX","SOUNDX","전체"};
    private final String[][] THEATERS={{"용산아이파크몰","0013"},{"왕십리","0074"},{"영등포","0059"},{"강남","0056"},{"여의도","0112"},{"신촌아트레온","0150"},{"홍대","0191"},{"건대입구","0229"},{"동대문","0252"},{"천호","0199"},{"압구정","0040"},{"청담씨네시티","0107"},{"구로","0010"},{"상봉","0046"},{"중계","0131"},{"직접 입력",""}};

    private Prefs prefs;
    private KakaoClient kakao;
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Map<String,List<CgvClient.Showtime>> schedules=new LinkedHashMap<>();

    private Spinner theaterSpinner;
    private LinearLayout movieRow,formatRow,dateRow,timeList;
    private TextView status,summary,guide,adjacentHint;
    private Button alertButton,startStop,refresh;
    private CheckBox adjacentOnly;
    private String selectedMovie="",selectedDate="";
    private boolean loading=false,renderingAdjacent=false;
    private final Runnable statusLoop=new Runnable(){public void run(){refreshStatus();ui.postDelayed(this,1500);}};

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=new Prefs(this);
        kakao=new KakaoClient(this);
        new NotificationHelper(this);
        selectedMovie=prefs.getUiMovie();
        requestNotificationPermission();
        setContentView(buildUi());
        ui.post(statusLoop);
        ui.postDelayed(()->loadAll(false),200);
    }

    private View buildUi(){
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(BG);page.addView(header());
        ScrollView scroll=new ScrollView(this);
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(16),dp(100));
        c.addView(statusCard());
        c.addView(label("극장"),top(18));c.addView(theaterCard(),top(7));
        c.addView(stepTitle("1","영화 선택"),top(20));movieRow=new LinearLayout(this);movieRow.setOrientation(LinearLayout.VERTICAL);c.addView(movieRow,top(8));
        c.addView(stepTitle("2","특별관 선택"),top(18));formatRow=new LinearLayout(this);formatRow.setOrientation(LinearLayout.HORIZONTAL);HorizontalScrollView fs=new HorizontalScrollView(this);fs.setHorizontalScrollBarEnabled(false);fs.addView(formatRow);c.addView(fs,top(8));
        guide=text("특별관을 고르면 해당 포맷의 열린 날짜와 시간만 보여줘.",13,false,SUB);c.addView(guide,top(8));
        alertButton=redButton("🔔 새 날짜 알림 받기");alertButton.setOnClickListener(v->toggleNewDate());c.addView(alertButton,top(10));
        c.addView(stepTitle("3","열린 날짜 · 시간"),top(20));dateRow=new LinearLayout(this);dateRow.setOrientation(LinearLayout.HORIZONTAL);HorizontalScrollView ds=new HorizontalScrollView(this);ds.setHorizontalScrollBarEnabled(false);ds.addView(dateRow);c.addView(ds,top(8));
        timeList=new LinearLayout(this);timeList.setOrientation(LinearLayout.VERTICAL);c.addView(timeList,top(10));

        LinearLayout pairCard=card();
        adjacentOnly=new CheckBox(this);adjacentOnly.setText("👫 붙은 2자리만 알림");adjacentOnly.setTextSize(16);adjacentOnly.setTextColor(TEXT);adjacentOnly.setChecked(prefs.isAdjacentSeatOnly());
        adjacentOnly.setOnCheckedChangeListener((buttonView,isChecked)->{
            if(renderingAdjacent)return;
            stopForConfigChange(false);
            prefs.setAdjacentSeatOnly(isChecked);
            if(isChecked&&prefs.getSelectedShowtimeKeys().size()>1){prefs.clearSelectedShowtimes();toast("연석 감시는 한 회차씩 정확히 보게 돼. 원하는 시간 하나만 다시 골라줘.");renderTimes();}
            refreshAdjacentUi();refreshStatus();
        });
        pairCard.addView(adjacentOnly);
        adjacentHint=text("OFF: 기존처럼 잔여석 증가를 감지해.\nON: 선택한 한 회차의 실제 CGV 좌석 화면에서 같은 행의 연속 2자리를 30초마다 확인해. 연석 모드는 CGV 좌석 화면을 열어둬야 해.",12,false,SUB);adjacentHint.setLineSpacing(dp(2),1);pairCard.addView(adjacentHint,top(5));
        c.addView(pairCard,top(12));

        scroll.addView(c);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));page.addView(bottom());return page;
    }

    private View header(){LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);h.setPadding(dp(18),dp(13),dp(8),dp(13));h.setBackgroundColor(DARK);TextView logo=text("CGV 티켓워치",21,true,Color.WHITE);h.addView(logo,new LinearLayout.LayoutParams(0,-2,1));Button open=flatButton("CGV 열기",Color.WHITE);open.setOnClickListener(v->openCgv());h.addView(open);Button set=flatButton("설정",Color.WHITE);set.setOnClickListener(v->showSettings());h.addView(set);return h;}
    private View statusCard(){LinearLayout card=card();LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.addView(text("내 감시",17,true,TEXT),new LinearLayout.LayoutParams(0,-2,1));status=text("정지",12,true,SUB);status.setPadding(dp(10),dp(5),dp(10),dp(5));r.addView(status);card.addView(r);summary=text("아직 선택 없음",15,true,TEXT);summary.setLineSpacing(dp(2),1);card.addView(summary,top(9));return card;}
    private View theaterCard(){LinearLayout card=card();theaterSpinner=new Spinner(this);ArrayList<String>names=new ArrayList<>();int sel=0;for(int i=0;i<THEATERS.length;i++){names.add("CGV "+THEATERS[i][0]);if(THEATERS[i][1].equals(prefs.getSiteNo()))sel=i;}theaterSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));theaterSpinner.setSelection(sel);theaterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){boolean first=true;public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){if(first){first=false;return;}if(pos==THEATERS.length-1){customTheater();return;}changeTheater(THEATERS[pos][0],THEATERS[pos][1]);}public void onNothingSelected(android.widget.AdapterView<?>p){}});card.addView(theaterSpinner);refresh=smallButton("영화/시간표 새로고침");refresh.setOnClickListener(v->loadAll(true));card.addView(refresh,top(6));return card;}
    private View bottom(){LinearLayout b=new LinearLayout(this);b.setPadding(dp(16),dp(10),dp(16),dp(14));b.setBackgroundColor(Color.WHITE);Button clear=outlinedButton("전체 해제");clear.setOnClickListener(v->{stopForConfigChange(false);prefs.clearSelectedShowtimes();prefs.clearNewDateMovies();prefs.resetBaseline();render();refreshStatus();});b.addView(clear,new LinearLayout.LayoutParams(dp(104),dp(54)));startStop=redButton("감시 시작");startStop.setOnClickListener(v->toggleWatch());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1);lp.leftMargin=dp(10);b.addView(startStop,lp);return b;}

    private void loadAll(boolean manual){
        if(loading)return;loading=true;refresh.setEnabled(false);refresh.setText("불러오는 중…");movieRow.removeAllViews();movieRow.addView(text("현재 상영/예정 영화를 불러오는 중…",14,false,SUB));String tn=prefs.getTheaterName(),sn=prefs.getSiteNo();
        loader.execute(()->{Map<String,List<CgvClient.Showtime>>out=new LinkedHashMap<>();Exception last=null;CgvClient client=new CgvClient(tn,sn,"","");LocalDate today=DateUtil.today();for(int i=0;i<8;i++){String d=DateUtil.ymd(today.plusDays(i));try{out.put(d,client.fetchMatches(d));}catch(Exception e){last=e;out.put(d,new ArrayList<>());}try{Thread.sleep(150);}catch(Exception ignored){}}Exception err=last;runOnUiThread(()->{loading=false;refresh.setEnabled(true);refresh.setText("영화/시간표 새로고침");schedules.clear();schedules.putAll(out);ensureMovieSelection();render();if(allEmpty()&&err!=null)toast("CGV 시간표를 못 불러왔어: "+clean(err.getMessage()));else if(manual)toast("최신 영화/시간표로 갱신했어.");});});
    }

    private void ensureMovieSelection(){Set<String>movies=allMovies();if(selectedMovie.isEmpty()||!movies.contains(selectedMovie)){selectedMovie=movies.isEmpty()?"":movies.iterator().next();prefs.setUiMovie(selectedMovie);}if(selectedDate.isEmpty())selectedDate=firstDateForSelection();}
    private Set<String> allMovies(){TreeSet<String>s=new TreeSet<>();for(List<CgvClient.Showtime>l:schedules.values())for(CgvClient.Showtime x:l)if(x.title!=null&&!x.title.trim().isEmpty())s.add(x.title);return s;}
    private void render(){renderMovies();renderFormats();renderDates();renderTimes();renderAlertButton();refreshAdjacentUi();refreshStatus();}

    private void renderMovies(){movieRow.removeAllViews();Set<String>movies=allMovies();if(movies.isEmpty()){movieRow.addView(text("불러온 영화가 없어.",14,false,SUB));return;}LinearLayout card=card();card.addView(text("선택한 영화",12,true,SUB));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView title=text(selectedMovie.isEmpty()?"영화를 선택해줘":selectedMovie,18,true,TEXT);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button change=smallButton("영화 바꾸기");change.setOnClickListener(v->showMoviePicker());row.addView(change);card.addView(row,top(5));card.addView(text("현재 불러온 영화 "+movies.size()+"개 · 목록은 눌렀을 때만 펼쳐져",12,false,SUB),top(6));movieRow.addView(card);}
    private void showMoviePicker(){ArrayList<String>movies=new ArrayList<>(allMovies());if(movies.isEmpty()){toast("불러온 영화가 없어.");return;}String[]items=movies.toArray(new String[0]);int checked=Math.max(0,movies.indexOf(selectedMovie));new AlertDialog.Builder(this).setTitle("영화 선택").setSingleChoiceItems(items,checked,(d,which)->{selectedMovie=movies.get(which);prefs.setUiMovie(selectedMovie);selectedDate=firstDateForSelection();d.dismiss();render();}).setNegativeButton("닫기",null).show();}

    private void renderFormats(){formatRow.removeAllViews();String current=prefs.getFormatKeyword();for(String f:FORMATS){boolean on=f.equals(current);TextView chip=text(f,14,on,on?Color.WHITE:TEXT);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(16),dp(10),dp(16),dp(10));chip.setBackground(roundBg(on?RED:Color.WHITE,dp(22),on?RED:BORDER,1));chip.setOnClickListener(v->{if(f.equals(prefs.getFormatKeyword()))return;stopForConfigChange(true);prefs.setFormatKeyword(f);selectedDate=firstDateForSelection();render();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(44));lp.rightMargin=dp(8);formatRow.addView(chip,lp);}}
    private void renderDates(){dateRow.removeAllViews();List<String>dates=availableDates();if(dates.isEmpty()){dateRow.addView(text("선택한 영화 · 특별관의 열린 날짜가 아직 없어.",14,false,SUB));selectedDate="";return;}if(!dates.contains(selectedDate))selectedDate=dates.get(0);DateTimeFormatter day=DateTimeFormatter.ofPattern("M/d",Locale.KOREA),dow=DateTimeFormatter.ofPattern("E",Locale.KOREA);for(String d:dates){LocalDate ld=DateUtil.parseYmd(d);boolean on=d.equals(selectedDate);TextView chip=text(dow.format(ld)+"\n"+day.format(ld),13,on,on?Color.WHITE:TEXT);chip.setGravity(Gravity.CENTER);chip.setBackground(roundBg(on?RED:Color.WHITE,dp(14),on?RED:BORDER,1));chip.setOnClickListener(v->{selectedDate=d;renderDates();renderTimes();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(66),dp(62));lp.rightMargin=dp(8);dateRow.addView(chip,lp);}}

    private void renderTimes(){
        timeList.removeAllViews();
        if(selectedMovie.isEmpty()||selectedDate.isEmpty()){timeList.addView(text("영화와 특별관을 선택하면 열린 회차가 여기에 보여.",14,false,SUB));return;}
        List<CgvClient.Showtime>list=filtered(schedules.get(selectedDate));if(list.isEmpty()){timeList.addView(text("이 날짜에는 해당 특별관 회차가 없어.",14,false,SUB));return;}
        timeList.addView(text(prefs.isAdjacentSeatOnly()?"시간 하나 선택 = 그 회차의 붙은 2자리 감시":"시간 버튼 = 해당 회차 취소표 알림",13,true,SUB));
        int idx=0;while(idx<list.size()){LinearLayout row=new LinearLayout(this);for(int col=0;col<3;col++){if(idx>=list.size()){row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(1),1));continue;}CgvClient.Showtime s=list.get(idx++);boolean on=prefs.getSelectedShowtimeKeys().contains(s.key());String seat=s.freeSeats>=0?"\n"+s.freeSeats+"석":"";TextView b=text((on?"✓ ":"")+s.time+seat,14,true,on?Color.WHITE:TEXT);b.setGravity(Gravity.CENTER);b.setBackground(roundBg(on?RED:Color.WHITE,dp(10),on?RED:BORDER,1));b.setOnClickListener(v->toggleShowtime(s));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(60),1);if(col>0)lp.leftMargin=dp(7);row.addView(b,lp);}timeList.addView(row,top(7));}
    }

    private void refreshAdjacentUi(){if(adjacentOnly==null)return;renderingAdjacent=true;adjacentOnly.setChecked(prefs.isAdjacentSeatOnly());renderingAdjacent=false;if(adjacentHint!=null)adjacentHint.setText(prefs.isAdjacentSeatOnly()?"ON · 연석 감시는 정확도를 위해 한 회차씩 선택해. 감시 시작을 누르면 CGV 예매 화면이 열리고, 좌석 선택 화면에서 30초마다 실제 연속 2좌석을 확인해.":"OFF · 기존처럼 선택한 회차의 잔여석 증가를 백그라운드에서 감지해.");}
    private void renderAlertButton(){String fmt=prefs.getFormatKeyword(),m=selectedMovie.isEmpty()?"영화":selectedMovie;boolean on=prefs.isNewDateMovie(selectedMovie);alertButton.setText(on?"🔔 "+m+" · "+fmt+" 새 날짜 알림 ON":"🔕 "+m+" · "+fmt+" 새 날짜 알림 받기");alertButton.setBackground(roundBg(on?DARK:RED,dp(13),Color.TRANSPARENT,0));alertButton.setEnabled(!selectedMovie.isEmpty());guide.setText(fmt.equals("전체")?"전체 포맷 기준이야. 특별관만 받고 싶으면 IMAX/4DX/SCREENX 등을 골라.":fmt+"만 표시하고, 새 날짜 알림도 "+fmt+" 회차가 새로 생길 때만 울려.");}
    private List<String> availableDates(){ArrayList<String>out=new ArrayList<>();for(Map.Entry<String,List<CgvClient.Showtime>>e:schedules.entrySet())if(!filtered(e.getValue()).isEmpty())out.add(e.getKey());return out;}
    private String firstDateForSelection(){List<String>d=availableDates();return d.isEmpty()?"":d.get(0);}
    private List<CgvClient.Showtime> filtered(List<CgvClient.Showtime>src){ArrayList<CgvClient.Showtime>out=new ArrayList<>();if(src==null)return out;for(CgvClient.Showtime s:src)if(s.title.equals(selectedMovie)&&CgvClient.isFormat(s,prefs.getFormatKeyword()))out.add(s);Collections.sort(out,Comparator.comparing(x->x.time));return out;}
    private boolean allEmpty(){for(List<CgvClient.Showtime>l:schedules.values())if(l!=null&&!l.isEmpty())return false;return true;}

    private void toggleNewDate(){if(selectedMovie.isEmpty())return;stopForConfigChange(true);if(prefs.isNewDateMovie(selectedMovie))prefs.clearNewDateMovies();else prefs.setOnlyNewDateMovie(selectedMovie);renderAlertButton();refreshStatus();}
    private void toggleShowtime(CgvClient.Showtime s){
        stopForConfigChange(true);Set<String>keys=prefs.getSelectedShowtimeKeys(),labels=prefs.getSelectedShowtimeLabels();String label=DateUtil.pretty(s.date)+" · "+s.title+" · "+s.format+" · "+s.time;
        if(keys.contains(s.key())){keys.remove(s.key());labels.remove(label);}else{if(prefs.isAdjacentSeatOnly()){keys.clear();labels.clear();}keys.add(s.key());labels.add(label);}prefs.setSelectedShowtimes(keys,labels);renderTimes();refreshStatus();
    }
    private void stopForConfigChange(boolean notify){if(!prefs.isWatching())return;stopWatch();if(notify)toast("감시 설정이 바뀌어서 일단 중지했어. 선택 끝나면 감시 시작을 눌러줘.");}

    private void toggleWatch(){
        if(prefs.isAdjacentSeatOnly()){
            if(!prefs.hasSelectedShowtimes()){new AlertDialog.Builder(this).setTitle("연석 감시할 시간을 골라줘").setMessage("붙은 2자리 감시는 시간 하나를 먼저 선택해야 해.").setPositiveButton("확인",null).show();return;}
            launchAdjacentWatch();return;
        }
        if(prefs.isWatching()){stopWatch();return;}
        if(!prefs.hasAnyWatch()){new AlertDialog.Builder(this).setTitle("알림을 선택해줘").setMessage("새 날짜 알림을 켜거나 원하는 상영시간을 눌러 취소표 알림을 선택해.").setPositiveButton("확인",null).show();return;}
        startWatch();
    }

    private void launchAdjacentWatch(){
        if(!prefs.getNewDateMovies().isEmpty()&&!prefs.isWatching())startWatch(false);
        String target=prefs.getSelectedShowtimeLabels().isEmpty()?prefs.targetLabel():prefs.getSelectedShowtimeLabels().iterator().next();
        Intent i=new Intent(this,SeatPairActivity.class);i.putExtra("target",target);i.putExtra("url",CgvClient.bookingUrl(prefs.getTheaterName(),prefs.getSiteNo()));startActivity(i);toast("CGV에서 선택한 회차의 좌석 선택 화면까지 들어가면 30초 연석 감시가 시작돼.");
    }
    private void startWatch(){startWatch(true);}private void startWatch(boolean toastIt){Intent i=new Intent(this,WatchService.class);i.setAction(WatchService.ACTION_START);startForegroundService(i);prefs.setWatching(true);refreshStatus();if(toastIt)toast("감시 시작했어.");}
    private void stopWatch(){Intent i=new Intent(this,WatchService.class);i.setAction(WatchService.ACTION_STOP);startService(i);prefs.setWatching(false);refreshStatus();}

    private void refreshStatus(){
        if(status==null)return;boolean w=prefs.isWatching();boolean pair=prefs.isAdjacentSeatOnly()&&prefs.hasSelectedShowtimes();status.setText(w?"● 감시 중":pair?"연석 준비":"정지");status.setTextColor(w?Color.WHITE:pair?RED:SUB);status.setBackground(roundBg(w?RED:Color.rgb(235,235,235),dp(20),Color.TRANSPARENT,0));StringBuilder b=new StringBuilder();if(!prefs.getNewDateMovies().isEmpty())b.append("🔔 ").append(android.text.TextUtils.join(", ",prefs.getNewDateMovies())).append(" · ").append(prefs.getFormatKeyword()).append(" 새 날짜");int n=prefs.getSelectedShowtimeKeys().size();if(n>0){if(b.length()>0)b.append("\n");b.append(prefs.isAdjacentSeatOnly()?"👫 붙은 2자리 감시 · "+n+"회차":"🎟 취소표 회차 "+n+"개");}if(b.length()==0)b.append("영화 → 특별관 → 알림을 선택해.");summary.setText(b.toString());if(prefs.isAdjacentSeatOnly())startStop.setText("연석 감시 열기");else startStop.setText(w?"감시 중지":"감시 시작");startStop.setBackground(roundBg(w&&!prefs.isAdjacentSeatOnly()?DARK:RED,dp(13),Color.TRANSPARENT,0));
    }

    private void changeTheater(String n,String s){stopForConfigChange(false);prefs.setTarget(n,s,"",prefs.getFormatKeyword());selectedMovie="";selectedDate="";loadAll(false);}
    private void customTheater(){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(20),0,dp(20),0);EditText n=input("극장명",false),c=input("극장 코드 4자리",false);c.setInputType(InputType.TYPE_CLASS_NUMBER);b.addView(n);b.addView(c);new AlertDialog.Builder(this).setTitle("CGV 직접 입력").setView(b).setPositiveButton("적용",(d,w)->{String nn=n.getText().toString().trim(),cc=c.getText().toString().trim();if(nn.isEmpty()||!cc.matches("\\d{4}")){toast("극장명과 4자리 코드를 확인해줘.");return;}changeTheater(nn,cc);}).setNegativeButton("취소",null).show();}
    private void showSettings(){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(20),0,dp(20),0);EditText rest=input("Kakao REST API 키",false),secret=input("Kakao Client Secret",true);rest.setText(kakao.getRestKey());secret.setText(kakao.getClientSecret());b.addView(text("카카오 알림 (선택)",16,true,TEXT));b.addView(rest);b.addView(secret);Button login=outlinedButton(kakao.hasRefreshToken()?"카카오 재로그인":"카카오 로그인");b.addView(login,top(6));Spinner interval=new Spinner(this);interval.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"30초","1분","2분"}));interval.setSelection(prefs.getIntervalSeconds()==120?2:prefs.getIntervalSeconds()==60?1:0);b.addView(text("감시 간격",16,true,TEXT),top(14));b.addView(interval);AlertDialog d=new AlertDialog.Builder(this).setTitle("설정").setView(b).setPositiveButton("저장",null).setNegativeButton("닫기",null).create();d.setOnShowListener(x->{login.setOnClickListener(v->{kakao.saveAppKeys(rest.getText().toString(),secret.getText().toString());kakao.login(this,(ok,msg)->runOnUiThread(()->toast(ok?"카카오 연결 완료":msg)));});d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{kakao.saveAppKeys(rest.getText().toString(),secret.getText().toString());prefs.setIntervalSeconds(interval.getSelectedItemPosition()==2?120:interval.getSelectedItemPosition()==1?60:30);d.dismiss();refreshStatus();});});d.show();}
    private void openCgv(){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(CgvClient.bookingUrl(prefs.getTheaterName(),prefs.getSiteNo()))));}

    private TextView stepTitle(String n,String s){return text(n+"  "+s,18,true,TEXT);}private TextView label(String s){return text(s,14,true,SUB);}private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(14),dp(16),dp(14));l.setBackground(roundBg(Color.WHITE,dp(16),BORDER,1));return l;}private TextView text(String s,int sp,boolean bold,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}private Button redButton(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setAllCaps(false);b.setBackground(roundBg(RED,dp(13),Color.TRANSPARENT,0));return b;}private Button outlinedButton(String s){Button b=new Button(this);b.setText(s);b.setTextColor(TEXT);b.setAllCaps(false);b.setBackground(roundBg(Color.WHITE,dp(12),BORDER,1));return b;}private Button smallButton(String s){Button b=outlinedButton(s);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(10),dp(5),dp(10),dp(5));return b;}private Button flatButton(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(color);b.setAllCaps(false);b.setBackgroundColor(Color.TRANSPARENT);return b;}private EditText input(String hint,boolean pass){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);if(pass)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}private GradientDrawable roundBg(int fill,int radius,int stroke,int sw){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(radius);if(sw>0)g.setStroke(dp(sw),stroke);return g;}private LinearLayout.LayoutParams top(int x){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(x);return p;}private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}private static String clean(String s){return s==null?"오류":s.replace("\n"," ");}private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},100);}@Override protected void onDestroy(){ui.removeCallbacks(statusLoop);loader.shutdownNow();super.onDestroy();}
}
