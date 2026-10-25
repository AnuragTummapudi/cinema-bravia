package com.tummapudi.cinema;
import java.util.*;
import org.json.*;
public final class TrailerCacheTest {
 static int assertions;static void check(boolean b,String message){assertions++;if(!b)throw new AssertionError(message);}
 static Trailer item(String id,String title,String kind,long publication,long verified,long duration){return new Trailer(id,"UCNApqoVYJbYSrni4YsbXzyQ","Official label",title,kind,publication,verified,duration,Arrays.asList("https://i.ytimg.com/vi/"+id+"/maxresdefault.jpg"));}
 public static void main(String[] args)throws Exception{
  long now=System.currentTimeMillis(),day=86400000;
  Trailer first=item("ABCDEFGHIJK","Movie Telugu Official Trailer","Trailer",now-day,now,140),duplicate=item("LMNOPQRSTUV","Movie Theatrical Trailer HD","Trailer",now-day*2,now,141),teaser=item("abcdefghijk","Movie Telugu Official Teaser","Teaser",now-day*3,now,80),edition=item("lmnopqrstuv","Movie Telugu Trailer 2","Trailer",now-1000,now,150);
  ArrayList<Trailer> out=TrailerRepository.deduplicate(Arrays.asList(first,first,duplicate,teaser,edition));
  check(out.size()==3,"Dedup lost distinct types/edition or retained duplicate");check(out.get(0).id.equals(edition.id),"Not newest first");check(out.get(1).id.equals(first.id),"Wrong duplicate retained");
  check(TrailerRepository.validCached(Arrays.asList(item("12345678901","Old Telugu Trailer","Trailer",now-day,now-31*day,100)),now).isEmpty(),"30-day stale record remained");
  check(TrailerRepository.validCached(Arrays.asList(item("12345678901","Old Telugu Trailer","Trailer",now-91*day,now,100)),now).isEmpty(),"Outside publication window remained");
  check(TrailerRepository.validCached(Arrays.asList(first),now).size()==1,"Offline cache lost valid result");
  Trailer roundtrip=Trailer.parse(first.json());check(roundtrip.id.equals(first.id)&&roundtrip.title.equals(first.title),"Cache roundtrip changed source metadata");
  try{Trailer.parse(new JSONObject("{bad"));throw new AssertionError("Corrupt JSON accepted");}catch(JSONException expected){assertions++;}
  JSONObject malicious=first.json().put("id","../bad");try{Trailer.parse(malicious);throw new AssertionError("Invalid ID accepted");}catch(JSONException expected){assertions++;}
  JSONObject hostile=first.json().put("thumbnails",new JSONArray(Arrays.asList("https://evil.invalid/test")));check(Trailer.parse(hostile).thumbnails.isEmpty(),"Foreign image host accepted");
  check(TrailerRepository.timestamp("2026-10-25T00:00:00Z")>0,"ISO date failed");check(TrailerRepository.timestamp("wrong")==0,"Invalid date accepted");
  byte[] bytes=new byte[40];try{TrailerRepository.read(new java.io.ByteArrayInputStream(bytes),20);throw new AssertionError("Oversized response accepted");}catch(java.io.IOException expected){assertions++;}
  Trailer english=new Trailer("01234567890","UCq0OueAsdxH6b8nyAspwViw","Studio","Film Official Trailer","Trailer",now,now,120,Collections.emptyList(),"hollywood");
  check(Trailer.parse(english.json()).category.equals("hollywood"),"Category cache roundtrip failed");
  JSONObject legacy=first.json();legacy.remove("category");check(Trailer.parse(legacy).category.equals("telugu"),"Legacy cache migration failed");
  ArrayList<Trailer> both=new ArrayList<>();for(int i=0;i<35;i++){String id=String.format("%011d",i);both.add(item(id,"Movie "+i+" Telugu Trailer","Trailer",now-i*1000,now,100));both.add(new Trailer(String.format("H%010d",i),english.channelId,"Studio","English film "+i+" Official Trailer","Trailer",now-i*1000,now,100,Collections.emptyList(),"hollywood"));}
  ArrayList<Trailer> limited=TrailerRepository.validCached(both,now);check(limited.size()==60,"Both categories must independently retain 30 results");check(limited.stream().filter(t->t.category.equals("hollywood")).count()==30,"Hollywood cap wrong");check(limited.stream().filter(t->t.category.equals("telugu")).count()==30,"Telugu cap wrong");
  check(TrailerRepository.validCached(Arrays.asList(english),now).size()==1,"English cache incorrectly filtered with Telugu policy");
  System.out.println(assertions+" real cache/sort/dedup assertions passed");
 }
}
