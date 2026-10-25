package com.tummapudi.cinema;
public final class TrailerRulesTest {
 static int checks;
 static void equal(Object a,Object b){checks++;if(!java.util.Objects.equals(a,b))throw new AssertionError("Expected "+b+", received "+a);}
 static String test(String title,String desc,String language,String policy,long seconds){return TrailerRules.classify(title,desc,language,policy,seconds,false);}
 public static void main(String[] args){
  equal(test("A New Film - Official Telugu Trailer","","","explicitTelugu",142),"Trailer");
  equal(test("Another Film Official Teaser","Telugu movie teaser","","explicitTelugu",80),"Teaser");
  equal(test("A Film Teaser","","te-IN","explicitTelugu",20),"Teaser");
  equal(test("A Film Official Trailer","","","teluguOnly",170),"Trailer");
  equal(test("Dubbed Film Telugu Trailer","","ta","explicitTelugu",160),"Trailer");
  equal(test("Ranabaali Official Trailer (Kannada)","Telugu movie","te","explicitTelugu",236),"");
  equal(test("Aakasamlo Oka Tara Teaser on October 17th","Telugu movie","te","explicitTelugu",28),"");
  equal(test("3 HOURS TO GO For The Movie Teaser","Telugu movie","te","explicitTelugu",15),"");
  equal(test("Hindi Trailer","Telugu trailer playlist link","hi","explicitTelugu",120),"");
  equal(test("Official Tamil Trailer","","te","explicitTelugu",120),"");
  equal(test("Unknown Movie Trailer","Multilingual release","","explicitTelugu",150),"");
  for(String word:new String[]{"Reaction","Review","Interview","Song","Lyrical","Fan Made","Fan Edit","Concept","Unofficial","Mashup","Remix","Shorts","Promo","Glimpse","Announcement","Launch Event","Launch Live","Launch","Speech","Vibing","Audience","Reactions","Tomorrow","Coming Soon","Press Meet","Making","Behind The Scenes","Full Movie","Out Now","OutNow","Web Series","Season","Episode"})equal(test("Telugu Official Trailer "+word,"","te","explicitTelugu",120),"");
  equal(test("Telugu Film Song","","te","explicitTelugu",120),"");
  equal(TrailerRules.classify("Telugu Trailer","","te","explicitTelugu",120,true),"");
  equal(test("Telugu Teaser","","te","explicitTelugu",9),"");equal(test("Telugu Trailer","","te","explicitTelugu",601),"");
  equal(TrailerRules.duplicateKey("My Film Official Telugu Trailer | Actor | Label","Trailer"),TrailerRules.duplicateKey("My Film Theatrical Trailer HD","Trailer"));
  if(TrailerRules.duplicateKey("My Film Telugu Trailer 2","Trailer").equals(TrailerRules.duplicateKey("My Film Telugu Trailer","Trailer")))throw new AssertionError("Edition collapsed");checks++;
  if(TrailerRules.duplicateKey("My Film Telugu Teaser","Teaser").equals(TrailerRules.duplicateKey("My Film Telugu Trailer","Trailer")))throw new AssertionError("Teaser collapsed");checks++;
  equal(TrailerRules.displayTitle("Donga Naa Koduku - Official Telugu Teaser | Actor"),"Donga Naa Koduku");
  equal(TrailerRules.validVideoId("AbC_dEf-gH1"),true);equal(TrailerRules.validVideoId("../secret"),false);
  long now=1000000000;equal(TrailerRules.isNew(now-72*3600000L,now),true);equal(TrailerRules.isNew(now-72*3600000L-1,now),false);equal(TrailerRules.isNew(now+1,now),false);
  equal(TrailerRules.newExpiryDelay(now-72*3600000L,now),1L);equal(TrailerRules.newExpiryDelay(now-72*3600000L-1,now),-1L);equal(TrailerRules.newExpiryDelay(now+1000,now),1000L);
  equal(TrailerRules.classify("Film - Official Trailer","","en-US","englishOnly",150,false,"hollywood"),"Trailer");
  equal(TrailerRules.classify("Film - Official Teaser","","","englishOnly",90,false,"hollywood"),"Teaser");
  for(String title:new String[]{"Film Hindi Trailer","Film Spanish Trailer","Film Fan Made Trailer","Film Song Teaser","Film Season 2 Trailer","Film Trailer Promo","Marvel Rivals Official Trailer","Film Roblox Trailer","Film Playable Trailer","Film | Watch Trailer Now","Movie trailer out"})equal(TrailerRules.titleEligible(title,"hollywood"),false);
  equal(TrailerRules.classify("Film Official Trailer","","es","englishOnly",90,false,"hollywood"),"");
  equal(TrailerRules.classify("Film English Trailer","","en","englishOnly",90,true,"hollywood"),"");
  equal(TrailerRules.titleEligible("Film Telugu Trailer","hollywood"),false);
  equal(TrailerRules.titleEligible("Film Telugu Trailer","telugu"),true);
  equal(TrailerRules.classify("Film Official Teaser","","en","englishOnly",16,false,"hollywood"),"");
  equal(TrailerRules.classify("Film Official Trailer","An Indian film epic","en","englishOnly",120,false,"hollywood"),"");
  System.out.println(checks+" trailer policy assertions passed");
 }
}
