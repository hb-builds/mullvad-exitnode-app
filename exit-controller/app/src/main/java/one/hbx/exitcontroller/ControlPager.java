// SPDX-License-Identifier: GPL-3.0-only
package one.hbx.exitcontroller;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import java.util.function.IntConsumer;

final class ControlPager extends HorizontalScrollView {
    private final LinearLayout strip;
    private int selected;
    private float touchStart;
    private IntConsumer listener;
    ControlPager(Context context){
        super(context);setHorizontalScrollBarEnabled(false);setFillViewport(true);setOverScrollMode(OVER_SCROLL_NEVER);
        strip=new LinearLayout(context);strip.setOrientation(LinearLayout.HORIZONTAL);addView(strip,new LayoutParams(-2,-1));
    }
    void addPage(View page){strip.addView(page,new LinearLayout.LayoutParams(Math.max(1,getWidth()),-1));}
    void onPageChanged(IntConsumer listener){this.listener=listener;}
    int selected(){return selected;}
    int contentHeight(int page,int width){
        ScrollView scroll=(ScrollView)strip.getChildAt(page);
        View content=scroll.getChildAt(0);
        content.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        return content.getMeasuredHeight();
    }
    void select(int page){selected=Math.max(0,Math.min(strip.getChildCount()-1,page));smoothScrollTo(selected*getWidth(),0);if(listener!=null)listener.accept(selected);}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        for(int i=0;i<strip.getChildCount();i++){android.view.ViewGroup.LayoutParams p=strip.getChildAt(i).getLayoutParams();p.width=w;strip.getChildAt(i).setLayoutParams(p);}
        post(()->scrollTo(selected*getWidth(),0));
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN)touchStart=event.getX();return super.onInterceptTouchEvent(event);}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN)touchStart=event.getX();
        if(event.getActionMasked()==MotionEvent.ACTION_UP || event.getActionMasked()==MotionEvent.ACTION_CANCEL){
            float moved=touchStart-event.getX();int target=Math.round((float)getScrollX()/Math.max(1,getWidth()));
            if(Math.abs(moved)>getWidth()*.15f)target=selected+(moved>0?1:-1);
            if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)target=selected;
            super.onTouchEvent(event); // Release the framework's drag/velocity state.
            select(target);return true;
        }
        return super.onTouchEvent(event);
    }
    @Override public void fling(int velocity){ /* Page snapping is handled on touch-up. */ }
}
