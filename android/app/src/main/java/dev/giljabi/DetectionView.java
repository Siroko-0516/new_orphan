package dev.giljabi;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** Draws image and boxes through the same center-fit transform. Bitmap ownership stays with Activity. */
final class DetectionView extends View {
    private Bitmap image;
    private List<YoloDetector.Box> boxes=Collections.emptyList();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    DetectionView(Context context){super(context);setMinimumHeight((int)(260*getResources().getDisplayMetrics().density));}
    void update(Bitmap image,List<YoloDetector.Box> boxes){this.image=image;this.boxes=boxes;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(Color.rgb(20,25,22));if(image==null||image.isRecycled())return;
        float scale=Math.min((float)getWidth()/image.getWidth(),(float)getHeight()/image.getHeight());
        float w=image.getWidth()*scale,h=image.getHeight()*scale,dx=(getWidth()-w)/2,dy=(getHeight()-h)/2;
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);canvas.drawBitmap(image,null,new RectF(dx,dy,dx+w,dy+h),paint);
        float density=getResources().getDisplayMetrics().density;paint.setTextSize(13*density);
        for(YoloDetector.Box b:boxes){
            float x1=dx+w*b.xy[0],y1=dy+h*b.xy[1],x2=dx+w*b.xy[2],y2=dy+h*b.xy[3];
            paint.setColor(Color.rgb(150,240,100));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);canvas.drawRect(x1,y1,x2,y2,paint);
            String label=YoloDetector.LABELS[b.label]+String.format(Locale.ROOT," %.0f%%",b.score*100);
            float baseline=Math.max(dy+15*density,y1-3*density),right=Math.min(getWidth(),x1+paint.measureText(label)+6*density);
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xCC102010);canvas.drawRect(x1,baseline-14*density,right,baseline+3*density,paint);
            paint.setColor(Color.WHITE);canvas.drawText(label,x1+2*density,baseline,paint);
        }
    }
}
