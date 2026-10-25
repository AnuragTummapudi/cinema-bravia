package com.tummapudi.cinema;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import java.io.*;
import java.util.concurrent.*;

/** A single decoder and a byte-bounded cache. No background traffic or animation loop. */
final class Artwork {
    interface Result { void ready(Bitmap bitmap); }
    final Context context;
    final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(14*1024*1024) {
        protected int sizeOf(String key,Bitmap value) { return value.getAllocationByteCount(); }
    };
    volatile boolean closed;
    Artwork(Context c) { context=c; }
    InputStream open(String path) throws IOException {
        File file=new File(context.getExternalFilesDir(null),path);
        return file.isFile()?new FileInputStream(file):context.getAssets().open(path);
    }
    void load(String path,boolean small,Result result) {
        String key=path+(small?":small":":large"); Bitmap hit=cache.get(key);
        if(hit!=null) { result.ready(hit); return; }
        worker.execute(()->{
            Bitmap bitmap=null;
            try {
                BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
                try(InputStream in=open(path)) { BitmapFactory.decodeStream(in,null,bounds); }
                if(bounds.outWidth<=0 || bounds.outHeight<=0 || bounds.outWidth>8192 || bounds.outHeight>8192) throw new IOException("Artwork dimensions invalid");
                BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inSampleSize=1;
                int width=small?240:1280, height=small?160:720;
                while(bounds.outWidth/(opts.inSampleSize*2)>=width && bounds.outHeight/(opts.inSampleSize*2)>=height) opts.inSampleSize*=2;
                opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
                try(InputStream in=open(path)) { bitmap=BitmapFactory.decodeStream(in,null,opts); }
                if(bitmap!=null) {bitmap.prepareToDraw();cache.put(key,bitmap);}
            } catch(IOException | RuntimeException e) { android.util.Log.w("Cinema","Artwork unavailable: "+path,e); }
            final Bitmap ready=bitmap;
            if(!closed) main.post(()->{if(!closed) result.ready(ready);});
        });
    }
    void trim() { cache.evictAll(); }
    void close() { closed=true; worker.shutdownNow(); main.removeCallbacksAndMessages(null); cache.evictAll(); }
}
