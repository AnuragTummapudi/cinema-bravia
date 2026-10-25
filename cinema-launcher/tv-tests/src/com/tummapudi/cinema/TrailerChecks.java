package com.tummapudi.cinema;

import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Separate signed test APK. Public metadata only is exported; credentials never leave the TV. */
public final class TrailerChecks extends Instrumentation {
    Bundle args;
    public void onCreate(Bundle arguments){args=arguments;start();}
    public void onStart(){Bundle result=new Bundle();Context context=getTargetContext();File file=new File(context.getFilesDir(),"trailers-v1.json");byte[] baseline=null;Map<String,?> preferences=null;
        try{
            baseline=java.nio.file.Files.readAllBytes(file.toPath());JSONObject data=new JSONObject(new String(baseline,java.nio.charset.StandardCharsets.UTF_8));
            String scenario=args.getString("scenario","export");
            if(scenario.equals("reschedule")){TrailerRefreshJob.cancel(context);TrailerRefreshJob.schedule(context);result.putString("assertions","Own six-hour persisted job re-registered after force-run test");
            }else if(scenario.equals("expire")){
                data.put("updated",0).put("nextAttempt",0).put("failures",0);JSONObject states=data.getJSONObject("sources");java.util.Iterator<String> ids=states.keys();while(ids.hasNext())states.getJSONObject(ids.next()).put("polled",0);
                java.nio.file.Files.write(file.toPath(),data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));result.putString("assertions","Only own-app refresh timestamps aged for scheduled-job test");
            }else if(scenario.equals("export")){
                result.putString("metadata",data.toString());result.putString("apiCallsToday",Integer.toString(context.getSharedPreferences("trailers",0).getInt("quotaUsed",0)));
                String encrypted=context.getSharedPreferences("trailer_credentials",0).getString("encryptedKey","");
                result.putString("encryptedCredentialStored",Boolean.toString(encrypted.contains(":")&&encrypted.length()>50));
            }else{
                SharedPreferences prefs=context.getSharedPreferences("trailers",0);preferences=new HashMap<>(prefs.getAll());prefs.edit().remove("lastManual").apply();
                if(scenario.equals("corrupt"))java.nio.file.Files.write(file.toPath(),"{broken cache".getBytes());
                AtomicInteger requests=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicReference<CountDownLatch> hold=new AtomicReference<>(new CountDownLatch(0));
                URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL url)throws IOException{
                    requests.incrementAndGet();entered.countDown();try{hold.get().await(3,TimeUnit.SECONDS);}catch(InterruptedException e){throw new InterruptedIOException();}
                    if(!scenario.equals("quota")&&!scenario.equals("auth"))throw new IOException("Offline fixture");
                    return new HttpsURLConnection(url){
                        public void disconnect(){}public boolean usingProxy(){return false;}public void connect(){}
                        public String getCipherSuite(){return "test";}public Certificate[] getLocalCertificates(){return null;}public Certificate[] getServerCertificates(){return null;}
                        public int getResponseCode(){return scenario.equals("quota")?403:400;}
                        public InputStream getErrorStream(){return new ByteArrayInputStream(("{\"error\":{\"errors\":[{\"reason\":\""+(scenario.equals("quota")?"quotaExceeded":"keyInvalid")+"\"}]}}").getBytes());}
                    };
                }}:null);
                TrailerRepository repo=TrailerRepository.get(context);int expected=data.getJSONArray("items").length();
                CountDownLatch initialized=new CountDownLatch(1);TrailerRepository.Listener loaded=state->{if(state.configured)initialized.countDown();};repo.add(loaded);
                if(!initialized.await(8,TimeUnit.SECONDS))throw new AssertionError("Cache load timed out");repo.remove(loaded);
                // Wait for initialization's freshness check before launching the deliberate failure.
                CountDownLatch settled=new CountDownLatch(1);release.countDown();repo.refresh(false,retry->settled.countDown());if(!settled.await(8,TimeUnit.SECONDS))throw new AssertionError("Initial check timed out");
                if(scenario.equals("corrupt")){if(!repo.snapshot().items.isEmpty())throw new AssertionError("Corrupt cache became content");result.putString("assertions","Corrupt cache rejected; no fabricated results");}
                else{
                    prefs.edit().remove("lastManual").apply();CountDownLatch done=new CountDownLatch(2);
                    hold.set(new CountDownLatch(1));repo.refresh(true,retry->done.countDown());if(!entered.await(3,TimeUnit.SECONDS))throw new AssertionError("First refresh did not request data");repo.refresh(true,retry->done.countDown());hold.get().countDown();
                    if(!done.await(8,TimeUnit.SECONDS))throw new AssertionError("Coalesced refresh did not complete");
                    if(repo.snapshot().items.size()!=expected)throw new AssertionError("Failure erased cached results");
                    String message=repo.snapshot().message;
                    if(scenario.equals("quota")&&!message.contains("limit"))throw new AssertionError("Quota failure not identified: "+message);
                    if(scenario.equals("auth")&&!message.contains("key"))throw new AssertionError("Credential failure not identified: "+message);
                    if(scenario.equals("offline")&&!message.contains("refresh"))throw new AssertionError("Offline failure not identified: "+message);
                    result.putString("assertions","Cached results preserved; both refresh callers completed; "+scenario+" error identified");if((scenario.equals("quota")||scenario.equals("auth"))&&requests.get()!=1)throw new AssertionError("Concurrent refresh was not coalesced");
                    result.putString("networkAttempts",Integer.toString(requests.get()));
                }
            }
            result.putString("result","PASS");
        }catch(Throwable e){result.putString("result","FAIL");result.putString("failure",e.getClass().getSimpleName()+": "+e.getMessage());}
        finally{
            try{if(baseline!=null&&preferences!=null){java.nio.file.Files.write(file.toPath(),baseline);SharedPreferences.Editor editor=context.getSharedPreferences("trailers",0).edit().clear();for(Map.Entry<String,?> e:preferences.entrySet()){Object v=e.getValue();if(v instanceof Long)editor.putLong(e.getKey(),(Long)v);else if(v instanceof Integer)editor.putInt(e.getKey(),(Integer)v);else if(v instanceof Boolean)editor.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof String)editor.putString(e.getKey(),(String)v);}editor.commit();}}catch(Exception restoration){result.putString("restoration","FAIL");}
            finish(result.getString("result","FAIL").equals("PASS")?-1:0,result);
        }
    }
}
