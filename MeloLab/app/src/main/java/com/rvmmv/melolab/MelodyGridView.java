package com.rvmmv.melolab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import com.rvmmv.melolab.MusicData.*;

/** One-bar piano roll. Tap opens an exact note editor instead of relying on finger precision. */
public final class MelodyGridView extends View {
    public interface Listener{void selected(int step,int midi);}
    private final Project project;
    private final int bar,base;
    private final boolean flats;
    private final Listener listener;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int playStep=-1;
    public MelodyGridView(Context context,Project project,int bar,int base,boolean flats,Listener listener){super(context);this.project=project;this.bar=bar;this.base=base;this.flats=flats;this.listener=listener;setContentDescription("Редактор мелодии. Нажми ячейку для добавления или изменения ноты.");}
    public void setPlayStep(int step){playStep=step;invalidate();}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float left=dp(35),top=dp(20),cw=(getWidth()-left)/16f,rh=(getHeight()-top)/12f;if(cw<=0||rh<=0)return;
        for(int row=0;row<12;row++){
            int midi=base+11-row;boolean valid=midi<=127;paint.setColor(!valid?0xFF161323:MusicTheory.inScale(midi,project.root,project.scaleId)?0xFF24203A:0xFF191626);c.drawRect(left,top+row*rh,getWidth(),top+(row+1)*rh,paint);
            paint.setColor(0xFFA49AB8);paint.setTextSize(dp(9));paint.setTextAlign(Paint.Align.RIGHT);if(valid)c.drawText(MusicTheory.noteName(midi,flats),left-dp(5),top+(row+.7f)*rh,paint);
            paint.setColor(0xFF342D49);paint.setStrokeWidth(dp(.5f));c.drawLine(left,top+row*rh,getWidth(),top+row*rh,paint);
        }
        paint.setTextAlign(Paint.Align.CENTER);for(int col=0;col<=16;col++){
            paint.setColor(col%4==0?0xFF655975:0xFF342D49);paint.setStrokeWidth(dp(col%4==0?1:.5f));c.drawLine(left+col*cw,top,left+col*cw,getHeight(),paint);
            if(col<16&&col%4==0){paint.setColor(0xFFA49AB8);paint.setTextSize(dp(10));c.drawText(String.valueOf(col/4+1),left+(col+.5f)*cw,dp(12),paint);}
        }
        int offset=bar*16;for(Note n:project.melody){if(n.midi<base||n.midi>=base+12||n.start+n.length<=offset||n.start>=offset+16)continue;int row=base+11-n.midi;float x1=left+Math.max(0,n.start-offset)*cw,x2=left+Math.min(16,n.start+n.length-offset)*cw;paint.setColor(MusicTheory.inScale(n.midi,project.root,project.scaleId)?0xFFC3A6FF:0xFFFFCE86);c.drawRoundRect(x1+dp(1),top+row*rh+dp(2),x2-dp(1),top+(row+1)*rh-dp(2),dp(3),dp(3),paint);}
        if(playStep>=offset&&playStep<offset+16){paint.setColor(0xFF7BE3CF);paint.setStrokeWidth(dp(2));c.drawLine(left+(playStep-offset)*cw,top,left+(playStep-offset)*cw,getHeight(),paint);}
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){getParent().requestDisallowInterceptTouchEvent(true);return true;}
        if(e.getAction()==MotionEvent.ACTION_UP){getParent().requestDisallowInterceptTouchEvent(false);performClick();float left=dp(35),top=dp(20),cw=(getWidth()-left)/16f,rh=(getHeight()-top)/12f;
            if(cw>0&&rh>0&&e.getX()>=left&&e.getX()<getWidth()&&e.getY()>=top&&e.getY()<getHeight()){int step=bar*16+(int)((e.getX()-left)/cw),midi=base+11-(int)((e.getY()-top)/rh);if(step<MusicData.totalSteps(project)&&midi<=127)listener.selected(step,midi);}return true;}
        if(e.getAction()==MotionEvent.ACTION_CANCEL)getParent().requestDisallowInterceptTouchEvent(false);return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
