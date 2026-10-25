package com.tummapudi.cinema;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;
import java.util.*;
import java.util.concurrent.*;

/** Two bounded workers, local JPEG cache and no decoding in a focus animation. */
final class TrailerImages {
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(8*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}};
    private final File directory;
    private final Set<String> pending=Collections.synchronizedSet(new HashSet<>());
    private final ConcurrentHashMap<String,Long> checked=new ConcurrentHashMap<>();
    private volatile boolean closed;
    TrailerImages(Context context){directory=new File(context.getCacheDir(),"trailer-thumbnails");}
    void load(Trailer trailer,ImageView view,Runnable unavailable){
        String key=trailer.id;view.setTag(key);Bitmap hit=memory.get(key);if(hit!=null && System.currentTimeMillis()-checked.getOrDefault(key,0L)<TrailerRepository.SIX_HOURS){view.setImageBitmap(hit);return;}
        // Each visible ImageView owns one pending delivery; callbacks verify its current binding.
        String request=key+":"+System.identityHashCode(view);if(!pending.add(request))return;
        workers.execute(()->{
            Bitmap bitmap=null;
            try{
                directory.mkdirs();File file=new File(directory,key+".jpg");
                if(file.isFile()&&System.currentTimeMillis()-file.lastModified()<TrailerRepository.SIX_HOURS)bitmap=decode(file);
                if(bitmap==null)for(String url:trailer.thumbnails){try{download(url,file);bitmap=decode(file);if(bitmap!=null)break;}catch(IOException ignored){}}
                if(bitmap!=null){bitmap.prepareToDraw();memory.put(key,bitmap);checked.put(key,System.currentTimeMillis());}prune();
            }catch(Exception ignored){}finally{pending.remove(request);}
            Bitmap result=bitmap;main.post(()->{if(!closed&&key.equals(view.getTag())){if(result!=null)view.setImageBitmap(result);else unavailable.run();}});
        });
    }
    private static Bitmap decode(File file)throws IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>4096||bounds.outHeight>4096){file.delete();return null;}
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
        while(bounds.outWidth/(options.inSampleSize*2)>=704&&bounds.outHeight/(options.inSampleSize*2)>=396)options.inSampleSize*=2;
        Bitmap result=BitmapFactory.decodeFile(file.getPath(),options);if(result==null)return null;
        float scale=Math.min(1,Math.min(704f/result.getWidth(),396f/result.getHeight()));
        if(scale<1){Bitmap resized=Bitmap.createScaledBitmap(result,Math.max(1,Math.round(result.getWidth()*scale)),Math.max(1,Math.round(result.getHeight()*scale)),true);if(resized!=result)result.recycle();result=resized;}
        return result;
    }
    private void download(String url,File target)throws IOException{
        URI uri=URI.create(url);if(!"https".equals(uri.getScheme())||!"i.ytimg.com".equals(uri.getHost()))throw new IOException("Image host invalid");
        HttpsURLConnection c=(HttpsURLConnection)uri.toURL().openConnection();c.setConnectTimeout(7000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);
        if(target.isFile())c.setIfModifiedSince(target.lastModified());
        File temporary=File.createTempFile("thumb-",".tmp",directory);
        try{
            int code=c.getResponseCode();if(code==304&&target.isFile()){target.setLastModified(System.currentTimeMillis());return;}if(code!=200)throw new IOException("Image unavailable");int total=0;
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(temporary)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(closed||Thread.currentThread().isInterrupted())throw new InterruptedIOException();total+=n;if(total>3000000)throw new IOException("Image too large");out.write(buffer,0,n);}}
            synchronized(this){if(!temporary.renameTo(target))throw new IOException("Cache write failed");}
        }finally{c.disconnect();temporary.delete();}
    }
    private synchronized void prune(){
        File[] files=directory.listFiles((d,n)->n.endsWith(".jpg"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long size=0,now=System.currentTimeMillis();
        for(File f:files){if(now-f.lastModified()>TrailerRepository.MAX_STALE)f.delete();else size+=f.length();}
        for(File f:files)if(size>64L*1024*1024&&f.exists()){long n=f.length();if(f.delete())size-=n;}
    }
    void trim(){memory.evictAll();}
    void close(){closed=true;workers.shutdownNow();main.removeCallbacksAndMessages(null);memory.evictAll();}
}
