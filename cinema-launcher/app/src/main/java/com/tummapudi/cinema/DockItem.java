package com.tummapudi.cinema;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** A transparent logo with finite focus motion. No rest-state plate or caption. */
final class DockItem extends View {
    private final Drawable logo;
    private final float density, logoWidth, logoHeight;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF frame=new RectF();
    private final Bitmap halo;
    private float focus=0;
    private ValueAnimator animator;
    DockItem(Context context,Drawable mark,float width,float height,Bitmap glow){
        super(context);density=getResources().getDisplayMetrics().density;
        logo=mark;logoWidth=width*density;logoHeight=height*density;halo=glow;
        setFocusable(true);setClickable(true);setId(View.generateViewId());
    }
    static Bitmap focusHalo(float density){
        Bitmap bitmap=Bitmap.createBitmap(Math.round(128*density),Math.round(100*density),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f*density);p.setColor(0x58f5eed8);
        // Blur is rasterized once, outside the GPU frame path; no live layer or idle animation.
        p.setShadowLayer(6*density,0,2*density,0x68f2dfaa);
        canvas.drawRoundRect(new RectF(11*density,7*density,117*density,93*density),15*density,15*density,p);
        bitmap.prepareToDraw();return bitmap;
    }
    void focus(boolean on,boolean motion){
        if(animator!=null)animator.cancel();
        if(!motion){focus=on?1:0;invalidate();return;}
        animator=ValueAnimator.ofFloat(focus,on?1:0);animator.setDuration(180);
        animator.setInterpolator(new DecelerateInterpolator(2));
        animator.addUpdateListener(a->{focus=(float)a.getAnimatedValue();invalidate();});animator.start();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float cx=getWidth()/2f,cy=getHeight()/2f;
        canvas.save();canvas.scale(1+.035f*focus,1+.035f*focus,cx,cy);
        if(focus>0){
            paint.setStyle(Paint.Style.FILL);paint.setAlpha(Math.round(255*focus));
            canvas.drawBitmap(halo,null,new RectF(cx-64*density,cy-50*density,cx+64*density,cy+50*density),paint);
            frame.set(cx-53*density,cy-43*density,cx+53*density,cy+43*density);
            paint.setColor(Color.argb(Math.round(105*focus),15,19,16));canvas.drawRoundRect(frame,15*density,15*density,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.2f*density);
            paint.setColor(Color.argb(Math.round(255*focus),245,243,237));canvas.drawRoundRect(frame,15*density,15*density,paint);
        }
        logo.setBounds(Math.round(cx-logoWidth/2),Math.round(cy-logoHeight/2),Math.round(cx+logoWidth/2),Math.round(cy+logoHeight/2));logo.draw(canvas);
        canvas.restore();
    }
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}

    /** Original official PNG, viewport isolates the star and excludes its adjoining wordmark. */
    static final class Hotstar extends Drawable {
        private final Bitmap source;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final Path viewport=new Path();
        Hotstar(Context context){
            try(java.io.InputStream in=context.getAssets().open("brands/jiohotstar-official.png")){source=BitmapFactory.decodeStream(in);}
            catch(java.io.IOException e){throw new IllegalStateException("Missing bundled JioHotstar brand asset",e);}
            // Convex clear-space envelope; original native alpha supplies the star's contour.
            viewport.moveTo(22.24f,81.59f);viewport.lineTo(142.01f,27.79f);viewport.lineTo(247.57f,17.64f);viewport.lineTo(248.58f,17.64f);viewport.lineTo(248.58f,18.66f);viewport.lineTo(215.09f,146.55f);viewport.lineTo(117.65f,219.63f);viewport.lineTo(116.63f,219.63f);viewport.lineTo(115.62f,218.61f);viewport.lineTo(59.79f,154.67f);viewport.lineTo(22.24f,82.6f);viewport.close();
        }
        @Override public void draw(Canvas canvas){
            Rect b=getBounds();canvas.save();canvas.translate(b.left,b.top);canvas.scale(b.width()/224f,b.height()/200f);canvas.translate(-24,-19);
            canvas.clipPath(viewport);canvas.drawBitmap(source,0,0,paint);canvas.restore();
        }
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
