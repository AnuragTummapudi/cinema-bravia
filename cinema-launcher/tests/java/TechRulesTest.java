package com.tummapudi.cinema;
import java.util.*;
public final class TechRulesTest {
 static int checks;static void check(boolean condition,String note){checks++;if(!condition)throw new AssertionError(note);}
 static Trailer item(String id,String title,String topic,long time,long duration){return new Trailer(id,"UCXZCJLdBC09xxGZ6gcdrc6A","Official source",title,"Launch",time,time,duration,Collections.emptyList(),"tech",topic);}
 public static void main(String[] args)throws Exception{
  for(String s:new String[]{"Introducing GPT-7","Meet Claude Sonnet 6","Gemini 4 Pro is here","Starship Flight 14","Unitree G2 robot demonstration","The world’s largest electric aircraft just flew","Introducing a completely new Quantum Widget"})check(TechRules.eligible(s),"Missed launch: "+s);
  for(String s:new String[]{"GPT-7 reaction","How to use Claude","New model rumors","Interview with the CEO","Tesla earnings","GPT-7 leaked","Gemini weekly recap","Introducing GPT-7 tutorial","Model launch countdown","Our podcast: new AI","New phone review","Getting started with Gemini","How Factory is building autonomous software with Claude","This Is How You Should Actually Make Launch Videos","Cooking with Codex: Ep. 1","From analyst to builder with Codex | Customer","Build a Better Shopping Experience with OpenAI | Shopping Agent Demo","NVIDIA Halos SAFE: Autonomous Vehicle Safety Evaluation","A new beginning for Windows PCs deserves a global celebration."})check(!TechRules.eligible(s),"Accepted noise: "+s);
  check(TechRules.classify("Introducing GPT-7","","en","ai",80,false).equals("Launch"),"Launch classification");check(TechRules.classify("Robot first flight","","zxx","robotics",80,false).equals("Demonstration"),"Silent demo rejected");
  check(TechRules.classify("Introducing GPT-7","","","ai",19,false).isEmpty(),"Social clip accepted");check(TechRules.classify("Introducing GPT-7","","","ai",80,true).isEmpty(),"Unfinished livestream accepted");
  check(TechRules.classify("First time founders need this","","","startups",80,false).equals("Startup video"),"Recent official YC video missing");
  check(TechRules.topic("Introducing Gemini 4","mixed").equals("ai"),"AI filter");check(TechRules.topic("New Optimus humanoid","mixed").equals("robotics"),"Robotics filter");
  check(TechRules.eventKey("Introducing GPT-7 Pro","ai").equals(TechRules.eventKey("GPT-7 Pro launch demo","ai")),"Same model split");
  check(!TechRules.eventKey("Introducing Gemini 4 Pro","ai").equals(TechRules.eventKey("Introducing Gemini 4 Flash","ai")),"Different models merged");
  check(!TechRules.eventKey("Starship Flight 14","space").equals(TechRules.eventKey("Starship Flight 15","space")),"Different missions merged");
  check(!TechRules.eventKey("Introducing Claude NewThing","ai").equals(TechRules.eventKey("Introducing Claude OtherThing","ai")),"Unknown new products collapsed to brand");
  check(TechRules.eventKey("UK: This is the new ChatGPT Voice, powered by GPT Live","ai").equals(TechRules.eventKey("Germany: This is the new ChatGPT Voice, powered by GPT Live","ai")),"Regional duplicates split");
  check(TechRules.eventKey("Introducing Claude Sonnet 6","ai").equals(TechRules.eventKey("Claude 6 Sonnet launch demo","ai")),"Model family ordering split duplicates");
  check(!TechRules.eventKey("Introducing Gemini 4 Flash Lite","ai").equals(TechRules.eventKey("Introducing Gemini 4 Flash","ai")),"Lite variant collapsed");
  check(!TechRules.eventKey("Unitree Introducing: H2-X Kung Fu Legend Guardian","robotics").equals(TechRules.eventKey("Unitree Introducing: G1","robotics")),"Different colon robot names merged");
  check(!TechRules.eventKey("Introducing: Product A","devices").equals(TechRules.eventKey("Introducing: Product B","devices")),"Unknown colon products merged");
  check(TechRules.eventKey("Introducing: The New","devices").isEmpty(),"Generic empty identity should remain empty");
  check(TechRules.classify("This Is How You Should Actually Make Launch Videos","","","startups",2829,false).equals("Startup video"),"YC launch-video discussion omitted");
  check(!TechRules.eventKey("Introducing GPT-7","ai").equals(TechRules.eventKey("Introducing GPT-7","startups")),"YC discussion merged into model launch");
  long now=System.currentTimeMillis();
  check(TechRules.withinWindow(now-TechRules.WINDOW,now),"Fourteen-day boundary prematurely removed");check(!TechRules.withinWindow(now-TechRules.WINDOW-1,now),"Expired week retained");check(TechRules.expiryDelay(now-TechRules.WINDOW,now)==1,"Boundary callback not scheduled");check(TechRules.expiryDelay(now-TechRules.WINDOW-1,now)==-1,"Expired callback loops");Trailer a=item("ABCDEFGHIJK","Introducing GPT-7 Pro","ai",now-1000,100),b=item("LMNOPQRSTUV","GPT-7 Pro launch demo","ai",now-2000,110),c=item("abcdefghijk","Introducing GPT-7 Mini","ai",now-3000,90);
  check(TrailerRepository.deduplicate(Arrays.asList(a,b,c)).size()==2,"Repeated topic not grouped or variant lost");
  check(TrailerRepository.validCached(Arrays.asList(item("12345678901","Introducing GPT-7","ai",now-TechRules.WINDOW-1,80)),now).isEmpty(),"Outside fourteen-day window retained");
  check(Trailer.parse(a.json()).category.equals("tech")&&Trailer.parse(a.json()).topic.equals("ai"),"Topic cache roundtrip");
  System.out.println(checks+" tech filtering/grouping/window assertions passed");
 }
}
