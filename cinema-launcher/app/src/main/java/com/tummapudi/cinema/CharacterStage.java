package com.tummapudi.cinema;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Seven independent alpha layers, finite focus motion, and no video or background render loop. */
final class CharacterStage extends View {
    final Bitmap[] portraits=new Bitmap[7];
    Bitmap wallpaper;
    float homeDrift=0;ValueAnimator driftAnimator;
    LinearGradient homeShade,homeFloor;
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    final Rect source=new Rect();final RectF destination=new RectF();
    final float[] progress=new float[7],start=new float[7];
    final float[] crops={.64f,.62f,1f,.90f,1f,1f,1f};
    final float[] centers={.22f,.31f,.40f,.50f,.59f,.69f,.78f};
    final float[] tops={.42f,.34f,.42f,.31f,.40f,.34f,.42f};
    final float[] heights={.47f,.55f,.50f,.55f,.51f,.54f,.48f};
    final int[] order={3,1,5,0,6,2,4};
    final ColorMatrixColorFilter cool;
    LinearGradient bottom;RadialGradient ambience;
    ValueAnimator animator;
    int selected=4,count=7,tint=0xff315f85;
    boolean home=false,enabled=true;
    CharacterStage(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);ColorMatrix matrix=new ColorMatrix();matrix.setSaturation(0);ColorMatrix blue=new ColorMatrix(new float[]{.55f,0,0,0,0, 0,.75f,0,0,0, 0,0,1f,0,12, 0,0,0,1,0});matrix.postConcat(blue);cool=new ColorMatrixColorFilter(matrix);progress[selected]=1;}
    void configure(int total){count=Math.min(total,7);selected=Math.min(selected,count-1);if(count!=7)for(int i=0;i<count;i++)centers[i]=count==1?.5f:.22f+.56f*i/(count-1f);}
    void setPortrait(int index,Bitmap b){portraits[index]=b;invalidate();}
    void setWallpaper(Bitmap b){wallpaper=b;invalidate();}
    void shiftHome(float value,boolean motion){
        if(driftAnimator!=null)driftAnimator.cancel();
        if(!motion){homeDrift=value;invalidate();return;}
        driftAnimator=ValueAnimator.ofFloat(homeDrift,value);driftAnimator.setDuration(360);driftAnimator.setInterpolator(new DecelerateInterpolator(2));
        driftAnimator.addUpdateListener(a->{homeDrift=(float)a.getAnimatedValue();if(home)invalidate();});driftAnimator.start();
    }
    void setHome(boolean value){home=value;updateShaders();invalidate();}
    void setArtwork(boolean value){enabled=value;invalidate();}
    void select(int index,int color,boolean animate){
        if(animator!=null)animator.cancel();System.arraycopy(progress,0,start,0,7);selected=index;tint=color;updateShaders();
        if(!animate){for(int i=0;i<7;i++)progress[i]=i==selected?1:0;invalidate();return;}
        animator=ValueAnimator.ofFloat(0,1);animator.setDuration(180);animator.setInterpolator(new DecelerateInterpolator(2));
        animator.addUpdateListener(a->{float f=(float)a.getAnimatedValue();for(int i=0;i<7;i++)progress[i]=start[i]+((i==selected?1:0)-start[i])*f;invalidate();});animator.start();
    }
    void stop(){if(animator!=null)animator.cancel();if(driftAnimator!=null)driftAnimator.cancel();}
    protected void onSizeChanged(int w,int h,int oldw,int oldh){updateShaders();}
    void updateShaders(){
        int w=getWidth(),h=getHeight();if(w==0||h==0)return;
        homeShade=new LinearGradient(0,0,w*.68f,0,new int[]{0xb8070b09,0x40070b09,0x00070b09},new float[]{0,.65f,1},Shader.TileMode.CLAMP);
        homeFloor=new LinearGradient(0,h*.58f,0,h,new int[]{0x00070b09,0xe6070b09,0xff070b09},new float[]{0,.65f,1},Shader.TileMode.CLAMP);
        int mid=Color.argb(160,Color.red(tint),Color.green(tint),Color.blue(tint));
        ambience=new RadialGradient(w*(home?.56f:centers[Math.max(0,selected)]),h*.47f,w*.44f,new int[]{mid,0x401d3447,0x00090d13},new float[]{0,.50f,1},Shader.TileMode.CLAMP);
        bottom=new LinearGradient(0,h*.68f,0,h*.88f,new int[]{0x00090d13,0xd0090d13,0xff090d13},new float[]{0,.8f,1},Shader.TileMode.CLAMP);
    }
    protected void onDraw(Canvas c){
        int w=getWidth(),h=getHeight();c.drawColor(0xff090d13);if(!enabled)return;
        if(home){drawHome(c);return;}
        paint.setColorFilter(null);paint.setShader(ambience);paint.setAlpha(255);c.drawRect(0,0,w,h,paint);paint.setShader(null);
        if(home){drawPortrait(c,Math.max(0,selected),true);}else{for(int i:order)if(i<count && i!=selected)drawPortrait(c,i,false);drawPortrait(c,Math.max(0,selected),false);}
        paint.setColorFilter(null);paint.setShader(bottom);paint.setAlpha(255);c.drawRect(0,0,w,h,paint);paint.setShader(null);
    }
    void drawHome(Canvas c){
        int w=getWidth(),h=getHeight();
        paint.setColorFilter(null);paint.setShader(null);paint.setAlpha(255);
        if(wallpaper!=null&&!wallpaper.isRecycled()){
            float scale=Math.max(w/(float)wallpaper.getWidth(),h/(float)wallpaper.getHeight())*1.14f;
            float width=wallpaper.getWidth()*scale,height=wallpaper.getHeight()*scale;
            float x=(w-width)/2+w*(.040f+homeDrift*.009f),y=(h-height)/2;
            source.set(0,0,wallpaper.getWidth(),wallpaper.getHeight());destination.set(x,y,x+width,y+height);c.drawBitmap(wallpaper,source,destination,paint);
        }
        paint.setShader(homeShade);c.drawRect(0,0,w,h,paint);paint.setShader(homeFloor);c.drawRect(0,0,w,h,paint);paint.setShader(null);
    }
    void drawPortrait(Canvas c,int i,boolean single){
        Bitmap bitmap=portraits[i];if(bitmap==null||bitmap.isRecycled())return;
        int w=getWidth(),h=getHeight();
        source.set(0,0,bitmap.getWidth(),Math.max(1,Math.round(bitmap.getHeight()*(single?Math.min(1,crops[i]+.08f):crops[i]))));
        float focus=progress[i],height=h*(single?.88f:heights[i])*(1+focus*.065f),width=height*source.width()/source.height();
        float center=w*(single?.63f:centers[i]),top=h*(single?.08f:tops[i])-focus*h*.022f;
        destination.set(center-width/2,top,center+width/2,top+height);
        paint.setColorFilter(single?cool:(i==selected?null:cool));paint.setAlpha(single?185:(i==selected?255:200));c.drawBitmap(bitmap,source,destination,paint);paint.setColorFilter(null);paint.setAlpha(255);
    }
    protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}
