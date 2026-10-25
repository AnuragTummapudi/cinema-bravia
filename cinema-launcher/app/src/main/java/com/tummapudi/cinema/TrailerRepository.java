package com.tummapudi.cinema;

import android.content.*;
import android.os.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared foreground/job pipeline. Only public YouTube metadata is requested. */
final class TrailerRepository {
    static final long SIX_HOURS=6L*3600000,DAY=86400000L,MAX_STALE=30*DAY,WINDOW=90*DAY;
    static final int JOB_ID=742610,POLICY_VERSION=2;
    interface Listener {void changed(Snapshot snapshot);}
    interface Completion {void done(boolean retry);}
    static final class Snapshot {
        final List<Trailer> items;final boolean refreshing,configured;final String message;final long updated;
        Snapshot(List<Trailer> items,boolean refreshing,boolean configured,String message,long updated){this.items=Collections.unmodifiableList(new ArrayList<>(items));this.refreshing=refreshing;this.configured=configured;this.message=message;this.updated=updated;}
    }
    static final class Source {
        String id,name,uploads,policy,category;long polled;String etag="",newest="";
    }
    static final class ApiFailure extends IOException {
        final int status;final String reason;
        ApiFailure(int status,String reason){super("YouTube request unavailable");this.status=status;this.reason=reason;}
    }
    static final class Response {JSONObject json;String etag;boolean unchanged;}
    private static TrailerRepository instance;
    static synchronized TrailerRepository get(Context context){if(instance==null)instance=new TrailerRepository(context.getApplicationContext());return instance;}
    final Context context;final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Completion> completions=new CopyOnWriteArrayList<>();
    private final Object gate=new Object();
    private volatile boolean foreground,stopRequested;
    private volatile HttpsURLConnection connection;
    void setForeground(boolean value){foreground=value;}
    void stopJobWork(){if(!foreground){stopRequested=true;HttpsURLConnection c=connection;if(c!=null)c.disconnect();}}
    private final AtomicBoolean busy=new AtomicBoolean();
    private final AtomicFile cache;
    private final ArrayList<Source> sources=new ArrayList<>();
    private ArrayList<Trailer> items=new ArrayList<>();
    private volatile Snapshot snapshot=new Snapshot(Collections.emptyList(),false,false,"Checking saved trailers",0);
    private boolean configured=false;private long updated=0,nextAttempt=0;private int failures=0;
    private final SharedPreferences prefs;
    private TrailerRepository(Context context){
        this.context=context;prefs=context.getSharedPreferences("trailers",0);cache=new AtomicFile(new File(context.getFilesDir(),"trailers-v1.json"));
        worker.execute(()->{load();publish(false,configured?"Saved trailers":"Connect YouTube to discover new trailers");refresh(false,null);});
    }
    Snapshot snapshot(){return snapshot;}
    void add(Listener listener){listeners.add(listener);main.post(()->{if(listeners.contains(listener))listener.changed(snapshot);});}
    void remove(Listener listener){listeners.remove(listener);}
    private void publish(boolean refreshing,String message){snapshot=new Snapshot(validCached(items,System.currentTimeMillis()),refreshing,configured,message,updated);Snapshot state=snapshot;main.post(()->{for(Listener listener:listeners)listener.changed(state);});}
    private void finish(boolean retry){
        final ArrayList<Completion> pending;
        synchronized(gate){pending=new ArrayList<>(completions);completions.clear();busy.set(false);}
        main.post(()->{for(Completion c:pending)c.done(retry);});
    }
    void refresh(boolean force,Completion completion){
        synchronized(gate){if(completion!=null)completions.add(completion);if(!busy.compareAndSet(false,true))return;stopRequested=false;}
        worker.execute(()->{
            boolean retry=false;
            try{
                String key=TrailerCredentials.read(context);configured=!key.isEmpty();long now=System.currentTimeMillis();
                if(!configured){publish(false,"Connect YouTube to discover new trailers");return;}
                boolean due=updated==0||now-updated>=SIX_HOURS||failures>0||sources.stream().anyMatch(source->source.polled==0||now-source.polled>=SIX_HOURS);
                if((!force&&!due)||now<nextAttempt){publish(false,items.isEmpty()?"No matching trailers yet":"Saved trailers ready");return;}
                if(force && now-prefs.getLong("lastManual",0)<60000){publish(false,"Checked recently. Please try again in a minute.");return;}
                if(force)prefs.edit().putLong("lastManual",now).apply();
                publish(true,"Checking official channels");
                failures=0;nextAttempt=0;fetch(key,force,now);save();
            }catch(ApiFailure failure){
                failures++;boolean auth=failure.status==400||failure.status==401||failure.reason.contains("key")||failure.reason.contains("accessNotConfigured")||failure.reason.contains("ipRefererBlocked");
                boolean quota=failure.reason.toLowerCase(Locale.ROOT).contains("quota")||failure.status==429;
                nextAttempt=System.currentTimeMillis()+(auth?SIX_HOURS:quota?SIX_HOURS:Math.min(SIX_HOURS,300000L*(1L<<Math.min(failures-1,6))));
                publish(false,auth?"YouTube key needs attention. Open Sources.":quota?"YouTube request limit reached. Saved trailers remain available.":"YouTube is unavailable. Showing saved trailers.");retry=!auth&&!quota;
                saveQuietly();
            }catch(Exception unavailable){
                failures++;nextAttempt=System.currentTimeMillis()+Math.min(SIX_HOURS,300000L*(1L<<Math.min(failures-1,6)));
                publish(false,"Could not refresh. Saved trailers remain available.");retry=true;saveQuietly();
            }finally{finish(retry);}
        });
    }
    void saveKey(String key,Completion completion){
        worker.execute(()->{
            try{TrailerCredentials.save(context,key);configured=true;nextAttempt=0;failures=0;prefs.edit().remove("lastManual").apply();publish(false,"YouTube connected. Checking official channels.");TrailerRefreshJob.schedule(context);refresh(true,completion);}
            catch(Exception failure){publish(false,"Could not securely save the key. Please try again.");if(completion!=null)main.post(()->completion.done(false));}
        });
    }
    void removeKey(){worker.execute(()->{try{TrailerCredentials.remove(context);}catch(Exception ignored){}configured=false;items.clear();updated=0;nextAttempt=0;cache.delete();prefs.edit().clear().apply();TrailerRefreshJob.cancel(context);publish(false,"YouTube disconnected. Local trailer metadata cleared.");});}
    private void load(){
        try{
            String raw;try(InputStream in=context.getAssets().open("trailer-sources.json")){raw=read(in,512000);}
            JSONArray array=new JSONObject(raw).getJSONArray("sources");
            for(int i=0;i<array.length();i++){JSONObject j=array.getJSONObject(i);Source s=new Source();s.id=j.getString("channelId");if(!s.id.matches("UC[A-Za-z0-9_-]{22}"))continue;s.name=j.getString("name");s.uploads=j.optString("uploadsPlaylistId","");s.policy=j.optString("languagePolicy","explicitTelugu");s.category=j.optString("category","telugu");sources.add(s);}
        }catch(Exception missing){android.util.Log.e("CinemaTrailers","Source configuration unavailable");}
        try{
            JSONObject data;try(InputStream in=cache.openRead()){data=new JSONObject(read(in,2000000));}
            updated=data.optLong("updated");nextAttempt=data.optLong("nextAttempt");failures=data.optInt("failures");
            JSONArray array=data.optJSONArray("items");if(array!=null)for(int i=0;i<array.length();i++){try{Trailer t=Trailer.parse(array.getJSONObject(i));if(hasSource(t.channelId))items.add(t);}catch(Exception invalid){}}
            JSONObject states=data.optJSONObject("sources");if(states!=null)for(Source s:sources){JSONObject state=states.optJSONObject(s.id);if(state!=null){s.polled=state.optLong("polled");s.etag=state.optString("etag");s.newest=state.optString("newest");String upload=state.optString("uploads");if(upload.matches("UU[A-Za-z0-9_-]{22}"))s.uploads=upload;}}
            if(data.optInt("policyVersion")!=POLICY_VERSION){updated=0;nextAttempt=0;for(Source source:sources){source.polled=0;source.etag="";source.newest="";}}
            if(data.optInt("hollywoodPolicyVersion")!=2){updated=0;nextAttempt=0;for(Source source:sources)if("hollywood".equals(source.category)){source.polled=0;source.etag="";source.newest="";}}
            if(data.optInt("techPolicyVersion")!=2){updated=0;nextAttempt=0;for(Source source:sources)if("tech".equals(source.category)){source.polled=0;source.etag="";source.newest="";}}
            items=validCached(items,System.currentTimeMillis());
        }catch(Exception missingOrCorrupt){items.clear();updated=0;nextAttempt=0;failures=0;}
        try{configured=!TrailerCredentials.read(context).isEmpty();if(configured)TrailerRefreshJob.schedule(context);}catch(Exception invalid){configured=false;}
    }
    private boolean hasSource(String id){for(Source s:sources)if(s.id.equals(id))return true;return false;}
    static long publicationWindow(String category){return "tech".equals(category)?TechRules.WINDOW:WINDOW;}
    private static boolean eligible(String title,String category){return "tech".equals(category)?TechRules.eligible(title):TrailerRules.titleEligible(title,category);}
    static ArrayList<Trailer> validCached(List<Trailer> values,long now){
        ArrayList<Trailer> result=new ArrayList<>();for(Trailer t:values)if(("tech".equals(t.category)&&"startups".equals(t.topic)||eligible(t.title,t.category))&&(!"hollywood".equals(t.category)||t.duration>=30)&&now-t.validated<=MAX_STALE&&t.validated<=now+300000&&now-t.published<=publicationWindow(t.category)&&t.published<=now+300000)result.add(t);return deduplicate(result);
    }
    static ArrayList<Trailer> deduplicate(List<Trailer> values){
        ArrayList<Trailer> sorted=new ArrayList<>(values);sorted.sort((a,b)->{int n=Long.compare(b.published,a.published);return n!=0?n:a.id.compareTo(b.id);});
        ArrayList<Trailer> result=new ArrayList<>();HashSet<String> ids=new HashSet<>();HashMap<String,Integer> counts=new HashMap<>();
        for(Trailer t:sorted){if(!ids.add(t.id))continue;boolean duplicate=false;
            for(Trailer existing:result)if(("tech".equals(t.category)&&TechRules.sameEvent(t,existing))||(!"tech".equals(t.category)&&!t.key.isEmpty()&&t.key.equals(existing.key)&&Math.abs(t.duration-existing.duration)<=2)){duplicate=true;break;}
            if(!duplicate&&counts.getOrDefault(t.category,0)<30){result.add(t);counts.put(t.category,counts.getOrDefault(t.category,0)+1);}
        }return result;
    }
    private void fetch(String apiKey,boolean force,long now)throws Exception{
        if(sources.isEmpty())throw new IOException("No verified sources");
        ArrayList<String[]> cursors=new ArrayList<>();for(Source s:sources)cursors.add(new String[]{Long.toString(s.polled),s.etag,s.newest});
        try{
        // Resolve actual uploads playlists from the API, not a name search or assumed prefix.
        if(now-prefs.getLong("channelChecked",0)>7*DAY || sources.stream().anyMatch(s->s.uploads.isEmpty())){
            ArrayList<String> ids=new ArrayList<>();for(Source s:sources)ids.add(s.id);
            JSONObject data=api("channels","part=snippet,contentDetails&maxResults=50&id="+encode(String.join(",",ids)),apiKey,"").json;
            JSONArray channels=data.getJSONArray("items");HashSet<String> verified=new HashSet<>();
            for(int i=0;i<channels.length();i++){JSONObject c=channels.getJSONObject(i);for(Source s:sources)if(s.id.equals(c.getString("id"))){s.uploads=c.getJSONObject("contentDetails").getJSONObject("relatedPlaylists").getString("uploads");s.name=c.getJSONObject("snippet").getString("title");verified.add(s.id);}}
            if(verified.isEmpty())throw new IOException("Sources unavailable");prefs.edit().putLong("channelChecked",now).apply();
        }
        LinkedHashSet<String> candidates=new LinkedHashSet<>();int success=0,failed=0;
        for(Source s:sources){
            if(!force&&s.polled>0&&now-s.polled<SIX_HOURS){success++;continue;}
            if(s.uploads.isEmpty()){failed++;continue;}
            try{
                String page="",first="",oldNewest=s.newest,newEtag=s.etag;boolean stop=false;
                for(int pass=0;pass<5&&!stop;pass++){
                    Response response=api("playlistItems","part=snippet,contentDetails&maxResults=50&playlistId="+encode(s.uploads)+(page.isEmpty()?"":"&pageToken="+encode(page)),apiKey,pass==0?s.etag:"");
                    if(response.unchanged)break;
                    if(pass==0)newEtag=response.etag;JSONArray array=response.json.getJSONArray("items");
                    for(int i=0;i<array.length();i++){
                        JSONObject entry=array.getJSONObject(i),snippet=entry.getJSONObject("snippet"),details=entry.getJSONObject("contentDetails");String id=details.optString("videoId");
                        if(first.isEmpty())first=id;if(id.equals(oldNewest)){stop=true;break;}
                        long published=timestamp(details.optString("videoPublishedAt",snippet.optString("publishedAt")));
                        if(published>0&&now-published>publicationWindow(s.category)){stop=true;break;}
                        String title=snippet.optString("title");if(TrailerRules.validVideoId(id)&&("tech".equals(s.category)&&"startups".equals(s.policy)||eligible(title,s.category)))candidates.add(id);
                    }
                    page=response.json.optString("nextPageToken");if(page.isEmpty())break;
                }
                if(!first.isEmpty())s.newest=first;s.etag=newEtag;s.polled=now;success++;
            }catch(ApiFailure e){if(e.status==400||e.status==401||e.status==403||e.status==429)throw e;failed++;}
            catch(IOException e){failed++;}
        }
        // Revalidate cached IDs every successful refresh, including playlists returning 304.
        LinkedHashMap<String,Trailer> merged=new LinkedHashMap<>();for(Trailer t:validCached(items,now)){merged.put(t.id,t);candidates.add(t.id);}
        ArrayList<String> ids=new ArrayList<>(candidates);
        for(int offset=0;offset<ids.size();offset+=50){
            List<String> batch=ids.subList(offset,Math.min(ids.size(),offset+50));JSONObject data=api("videos","part=snippet,contentDetails,status&id="+encode(String.join(",",batch)),apiKey,"").json;
            for(String id:batch)merged.remove(id); // Missing public video records are no longer offered.
            JSONArray array=data.getJSONArray("items");
            for(int i=0;i<array.length();i++){
                Trailer t=fromVideo(array.getJSONObject(i),now);if(t!=null)merged.put(t.id,t);
            }
        }
        if(success==0)throw new IOException("Sources unavailable");
        items=validCached(new ArrayList<>(merged.values()),now);updated=now;
        if(failed>0){failures=1;nextAttempt=now+3600000;}
        publish(false,(items.isEmpty()?"No matching videos in recent uploads":failed>0?"Updated · "+failed+" channels temporarily unavailable":"Updated from official channels"));
    }
        catch(Exception failure){for(int i=0;i<sources.size();i++){Source s=sources.get(i);String[] cursor=cursors.get(i);s.polled=Long.parseLong(cursor[0]);s.etag=cursor[1];s.newest=cursor[2];}throw failure;}
    }
    private Trailer fromVideo(JSONObject v,long now)throws JSONException{
        JSONObject s=v.getJSONObject("snippet"),d=v.getJSONObject("contentDetails"),status=v.getJSONObject("status");
        String channelId=s.getString("channelId"),id=v.getString("id");Source source=null;for(Source candidate:sources)if(candidate.id.equals(channelId))source=candidate;
        if(source==null||!TrailerRules.validVideoId(id)||!"public".equals(status.optString("privacyStatus")))return null;
        JSONObject restriction=d.optJSONObject("regionRestriction");if(restriction!=null){JSONArray blocked=restriction.optJSONArray("blocked"),allowed=restriction.optJSONArray("allowed");if(contains(blocked,"IN")||(allowed!=null&&!contains(allowed,"IN")))return null;}
        long duration;try{duration=Duration.parse(d.getString("duration")).getSeconds();}catch(Exception invalid){return null;}
        String kind="tech".equals(source.category)?TechRules.classify(s.getString("title"),s.optString("description"),s.optString("defaultAudioLanguage"),source.policy,duration,!"none".equals(s.optString("liveBroadcastContent","none"))):TrailerRules.classify(s.getString("title"),s.optString("description"),s.optString("defaultAudioLanguage"),source.policy,duration,!"none".equals(s.optString("liveBroadcastContent","none")),source.category);
        long published=timestamp(s.optString("publishedAt"));if(kind.isEmpty()||published<=0||now-published>publicationWindow(source.category)||published>now+300000)return null;
        ArrayList<String> thumbs=new ArrayList<>();JSONObject images=s.optJSONObject("thumbnails");
        if(images!=null)for(String size:new String[]{"maxres","standard","high","medium"}){JSONObject image=images.optJSONObject(size);if(image!=null){String url=image.optString("url");if(url.startsWith("https://i.ytimg.com/"))thumbs.add(url);}}
        return new Trailer(id,channelId,s.optString("channelTitle",source.name),s.getString("title"),kind,published,now,duration,thumbs,source.category,"tech".equals(source.category)?TechRules.topic(s.getString("title"),source.policy):"");
    }
    private static boolean contains(JSONArray array,String value){if(array!=null)for(int i=0;i<array.length();i++)if(value.equals(array.optString(i)))return true;return false;}
    static long timestamp(String text){try{return Instant.parse(text).toEpochMilli();}catch(Exception invalid){return 0;}}
    private Response api(String resource,String query,String apiKey,String etag)throws Exception{
        if(stopRequested)throw new InterruptedIOException("Job stopped");
        long day=System.currentTimeMillis()/DAY;long recorded=prefs.getLong("quotaDay",-1);int used=recorded==day?prefs.getInt("quotaUsed",0):0;
        if(used>=500)throw new ApiFailure(429,"quotaLocalLimit");prefs.edit().putLong("quotaDay",day).putInt("quotaUsed",used+1).apply();
        HttpsURLConnection c=(HttpsURLConnection)new URL("https://www.googleapis.com/youtube/v3/"+resource+"?"+query).openConnection();
        connection=c;c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept","application/json");c.setRequestProperty("x-goog-api-key",apiKey);
        if(!etag.isEmpty())c.setRequestProperty("If-None-Match",etag);
        try{
            int code=c.getResponseCode();Response response=new Response();if(code==304){response.unchanged=true;return response;}
            if(code!=200){String reason="unavailable";try(InputStream in=c.getErrorStream()){JSONObject error=new JSONObject(read(in,32000)).optJSONObject("error");if(error!=null){JSONArray errors=error.optJSONArray("errors");if(errors!=null&&errors.length()>0)reason=errors.getJSONObject(0).optString("reason","unavailable");}}catch(Exception ignored){}throw new ApiFailure(code,reason);}
            try(InputStream in=c.getInputStream()){response.json=new JSONObject(read(in,2000000));}response.etag=c.getHeaderField("ETag");if(response.etag==null)response.etag="";return response;
        }finally{connection=null;c.disconnect();}
    }
    static String read(InputStream in,int limit)throws IOException{
        if(in==null)throw new IOException("No response");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("Response too large");out.write(buffer,0,n);}return out.toString("UTF-8");
    }
    private static String encode(String s)throws UnsupportedEncodingException{return URLEncoder.encode(s,"UTF-8");}
    private void save()throws Exception{
        JSONObject data=new JSONObject().put("version",1).put("policyVersion",POLICY_VERSION).put("hollywoodPolicyVersion",2).put("techPolicyVersion",2).put("updated",updated).put("nextAttempt",nextAttempt).put("failures",failures),states=new JSONObject();JSONArray array=new JSONArray();
        for(Trailer t:validCached(items,System.currentTimeMillis()))array.put(t.json());data.put("items",array);
        for(Source s:sources)states.put(s.id,new JSONObject().put("polled",s.polled).put("etag",s.etag).put("newest",s.newest).put("uploads",s.uploads));data.put("sources",states);
        FileOutputStream stream=null;try{stream=cache.startWrite();stream.write(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));cache.finishWrite(stream);}catch(Exception failure){if(stream!=null)cache.failWrite(stream);throw failure;}
    }
    private void saveQuietly(){try{save();}catch(Exception ignored){}}
}
