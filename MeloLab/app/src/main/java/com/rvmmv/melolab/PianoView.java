package com.rvmmv.melolab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Touch keyboard: black keys are hit-tested before white keys. */
public final class PianoView extends View {
    public interface Listener { void pressed(int midi); }
    private final int base,octaves;
    private final Listener listener;
    private final Set<Integer> selected=new HashSet<>(),scale=new HashSet<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Integer> white=new ArrayList<>(),black=new ArrayList<>();
    private final List<Float> blackCenters=new ArrayList<>();
    private boolean flats;
    public PianoView(Context context,int base,int octaves,Set<Integer> highlighted,Set<Integer> scalePitches,boolean flats,Listener listener){
        super(context);this.base=base;this.octaves=octaves;this.listener=listener;this.flats=flats;
        if(highlighted!=null)selected.addAll(highlighted);if(scalePitches!=null)scale.addAll(scalePitches);
        for(int m=base;m<base+12*octaves&&m<=127;m++){
            int pc=m%12;if(pc==1||pc==3||pc==6||pc==8||pc==10){black.add(m);blackCenters.add((float)white.size());}else white.add(m);
        }
        setContentDescription("Клавиатура. Нажми клавишу, чтобы услышать или выбрать ноту.");
    }
    public void highlight(Set<Integer> notes){selected.clear();if(notes!=null)selected.addAll(notes);invalidate();}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(white.isEmpty())return;float width=getWidth()/(float)white.size(),h=getHeight();
        for(int i=0;i<white.size();i++){
            int midi=white.get(i);paint.setColor(selected.contains(midi)?0xFFC3A6FF:0xFFF0EAF7);canvas.drawRect(i*width+1,0,(i+1)*width-1,h-1,paint);
            if(scale.contains(midi%12)){paint.setColor(0xFF3F9E8D);canvas.drawCircle((i+.5f)*width,h-dp(32),dp(3),paint);}
            paint.setColor(0xFF333046);paint.setTextSize(dp(octaves>1?9:11));paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(MusicTheory.pitchName(midi%12,flats),(i+.5f)*width,h-dp(12),paint);
        }
        for(int i=0;i<black.size();i++){
            int midi=black.get(i);float center=blackCenters.get(i)*width;paint.setColor(selected.contains(midi)?0xFF9A75D9:0xFF282338);canvas.drawRect(center-width*.31f,0,center+width*.31f,h*.62f,paint);
            if(scale.contains(midi%12)){paint.setColor(0xFF7BE3CF);canvas.drawCircle(center,h*.62f-dp(12),dp(3),paint);}
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getAction()==MotionEvent.ACTION_DOWN){getParent().requestDisallowInterceptTouchEvent(true);return true;}
        if(event.getAction()==MotionEvent.ACTION_UP){
            getParent().requestDisallowInterceptTouchEvent(false);performClick();if(white.isEmpty())return true;float w=getWidth()/(float)white.size(),x=event.getX(),y=event.getY();
            if(x<0||x>=getWidth()||y<0||y>=getHeight())return true;
            if(y<getHeight()*.62f)for(int i=0;i<black.size();i++)if(Math.abs(x-blackCenters.get(i)*w)<w*.31f){listener.pressed(black.get(i));return true;}
            int i=Math.min(white.size()-1,(int)(x/w));listener.pressed(white.get(i));return true;
        }
        if(event.getAction()==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);return true;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
