package com.local.yongsanimaxwatcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class HomeActivity extends Activity {
    private static final int RED=Color.rgb(232,32,48), DARK=Color.rgb(35,35,35), SUB=Color.rgb(95,95,95), BG=Color.rgb(246,246,246);

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(buildUi());
    }

    private LinearLayout buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22),dp(30),dp(22),dp(22));
        root.setBackgroundColor(BG);

        TextView title=text("CGV 티켓워치",28,true,DARK);
        root.addView(title);
        TextView desc=text("원하는 감시 방식을 골라.",15,false,SUB);
        root.addView(desc,top(8));

        Button normal=button("🎟 영화 · 시간 취소표 감시\n새 날짜 / 선택 회차 잔여석 감시",DARK);
        normal.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
        root.addView(normal,top(28));

        Button pair=button("👫 붙은 2자리 연석 감시\nCGV 좌석 선택 화면에서 30초마다 실제 연속 좌석 확인",RED);
        pair.setOnClickListener(v->startActivity(new Intent(this,SeatPairActivity.class)));
        root.addView(pair,top(14));

        TextView note=text("연석 감시는 CGV 좌석 선택 화면까지 들어간 뒤 시작해. 자동 좌석 선택/선점/결제는 하지 않아.",13,false,SUB);
        note.setLineSpacing(dp(3),1f);
        root.addView(note,top(18));
        return root;
    }

    private Button button(String s,int color){
        Button b=new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(18),dp(14),dp(18),dp(14));
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(16));b.setBackground(g);
        b.setMinHeight(dp(92));
        return b;
    }
    private TextView text(String s,int sp,boolean bold,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout.LayoutParams top(int x){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(x);return p;}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
}
