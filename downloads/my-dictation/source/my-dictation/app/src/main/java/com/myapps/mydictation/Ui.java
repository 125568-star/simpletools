package com.myapps.mydictation;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;

final class Ui {
    static final int BG=Color.rgb(11,16,32), CARD=Color.rgb(25,35,57), PRIMARY=Color.rgb(145,174,255), TEXT=Color.rgb(242,245,255), MUTED=Color.rgb(170,183,210);
    static int foreground(int bg){int r=Color.red(bg),g=Color.green(bg),b=Color.blue(bg);return (r*299+g*587+b*114)/1000>145?Color.rgb(18,25,40):TEXT;}
    static int dp(Context c,float v){return (int)(v*c.getResources().getDisplayMetrics().density+0.5f);}
    static GradientDrawable shape(int color, Context c){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,16));return d;}
    static LinearLayout vertical(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout horizontal(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(android.view.Gravity.CENTER_VERTICAL);return l;}
    static TextView text(Context c,String s,int sp){TextView t=new TextView(c);t.setText(s);t.setTextColor(TEXT);t.setTextSize(sp);return t;}
    static TextView heading(Context c,String s){TextView t=text(c,s,25);t.setTypeface(null, Typeface.BOLD);return t;}
    static Button button(Context c,String s){Button b=new Button(c);b.setText(s);b.setAllCaps(false);b.setTextColor(BG);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PRIMARY));return b;}
    static Button secondary(Context c,String s){Button b=button(c,s);b.setTextColor(TEXT);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(CARD));return b;}
    static EditText edit(Context c,String hint){EditText e=new EditText(c);e.setHint(hint);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PRIMARY));return e;}
    static void pad(View v,Context c,int all){int p=dp(c,all);v.setPadding(p,p,p,p);}
    static LinearLayout.LayoutParams gap(Context c,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(c,top);return lp;}
    static void add(LinearLayout l,View v,int top){l.addView(v,gap(l.getContext(),top));}
    static ScrollView root(Context c,LinearLayout body){ScrollView scroll=new ScrollView(c);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);scroll.addView(body);pad(body,c,18);return scroll;}
}
