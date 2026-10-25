package com.tummapudi.cinema;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Readability scrims protect text without obscuring the selected character. */
final class SceneView extends View {
    Bitmap art;
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    final RectF destination=new RectF();
    LinearGradient side,bottom,top;
    SceneView(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    void setArt(Bitmap bitmap) { art=bitmap; invalidate(); }
    protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        side=new LinearGradient(w*.22f,0,w*.70f,0,new int[]{0xff101310,0xf0101310,0x00101310},new float[]{0,.33f,1},Shader.TileMode.CLAMP);
        bottom=new LinearGradient(0,h*.40f,0,h*.88f,new int[]{0x00101310,0xb0101310,0xff101310},new float[]{0,.65f,1},Shader.TileMode.CLAMP);
        top=new LinearGradient(0,0,0,h*.23f,0xb0101310,0x00101310,Shader.TileMode.CLAMP);
    }
    protected void onDraw(Canvas canvas) {
        int w=getWidth(),h=getHeight(); canvas.drawColor(0xff101310);
        if(art!=null) {
            float scale=Math.max(w*.78f/art.getWidth(),h*.94f/art.getHeight());
            float dw=art.getWidth()*scale,dh=art.getHeight()*scale;
            float cx=w*.65f;
            destination.set(cx-dw/2,0,cx+dw/2,dh);
            paint.setShader(null); paint.setAlpha(255); canvas.drawBitmap(art,null,destination,paint);
        }
        paint.setShader(side); canvas.drawRect(0,0,w,h,paint);
        paint.setShader(bottom); canvas.drawRect(0,0,w,h,paint);
        paint.setShader(top); canvas.drawRect(0,0,w,h,paint); paint.setShader(null);
    }
}
