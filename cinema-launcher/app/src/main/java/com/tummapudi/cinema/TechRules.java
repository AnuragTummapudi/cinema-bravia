package com.tummapudi.cinema;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Conservative launch discovery from identity-pinned first-party channels; no static video list. */
final class TechRules {
    static final long WINDOW=14L*86400000;
    private static final Pattern EXCLUDE=Pattern.compile("(?i)\\b(how|tutorial|tips|getting started|guide|podcast|interview|conversation|fireside|office hours|earnings|stock price|reaction|rumou?rs?|leak(?:ed|s)?|prediction|explained|explainer|recap|weekly roundup|review|customer story|case study|customer stories|cooking with|ep[.]? \\d+|episode \\d+|celebration|event highlights|sizzle|brand film|behind the scenes|trailer|music video|livestream starting|watch party|countdown|subscribe|giveaway|vs[.]?|versus)\\b");
    private static final Pattern SIGNAL=Pattern.compile("(?i)\\b(introduc(?:ing|tion)|launch(?:es|ed)?|unveil(?:s|ed|ing)?|announc(?:e|es|ed|ing|ement)|reveal(?:s|ed)?|releas(?:e|es|ed|ing)|meet|new|first|debut|breakthrough|present(?:ing|s)?|available|demo(?:nstration)?|flight|flew|aircraft|robot|robotics|humanoid|autonomous|gpt[- ]?\\d|gemini|claude|sora|codex|grok|starship|atlas|optimus|cybercab|robotaxi)\\b");
    private static final Set<String> STOP=new HashSet<>(Arrays.asList("introducing","introduction","launch","launches","launched","official","video","meet","the","a","an","is","are","here","new","first","look","at","our","unveiling","unveiled","announce","announcing","announced","release","released","releasing","available","now","today","demo","demonstration","full","keynote","highlights","with","and","for","of","to","in","on","by","from","your","you","this","its","it","can","just","update","updates"));
    static boolean eligible(String title){
        if(title==null||EXCLUDE.matcher(title).find()||!SIGNAL.matcher(title).find())return false;
        if(Pattern.compile("(?i)^(build(?:ing)?|create|learn|using|use)\\b").matcher(title).find())return false;
        return Pattern.compile("(?i)\\b(introducing|introduction|launch(?:es|ed)?|unveil(?:s|ed|ing)?|announc(?:e|es|ed|ing|ement)|reveal(?:s|ed)?|releas(?:e|es|ed|ing)|meet|new|debut|breakthrough|available|flight|flew|aircraft|demo(?:nstration)?)\\b|^from.+to.+robot|^(?:gpt[- ]?\\d|gemini[- ]?\\d|claude (?:opus|sonnet|haiku|fable)[- ]?\\d)").matcher(title).find();
    }
    static String classify(String title,String description,String audio,String policy,long seconds,boolean live){
        if(live||seconds<20||seconds>5400)return "";
        if("startups".equals(policy))return "Startup video";
        if(!eligible(title))return "";
        return title.toLowerCase(Locale.ROOT).matches(".*\\b(demo|demonstration|flight|flew|robot|robotics|humanoid|autonomous)\\b.*")?"Demonstration":"Launch";
    }
    static long expiryDelay(long published,long now){if(published>now)return published-now;long remaining=WINDOW-(now-published);return remaining>=0?remaining+1:-1;}
    static boolean withinWindow(long published,long now){return published<=now&&now-published<=WINDOW;}
    static String label(String topic){switch(topic){case "ai":return "AI & models";case "startups":return "Startups";case "robotics":return "Robotics & mobility";case "space":return "Space";case "devices":return "Devices & tools";default:return "All updates";}}
    static String topic(String title,String policy){
        String s=title.toLowerCase(Locale.ROOT);
        if("startups".equals(policy))return "startups";
        if(s.matches(".*\\b(robot|robotics|humanoid|optimus|cybercab|robotaxi|autonomous|aircraft|flying|car|vehicle|battery)\\b.*"))return "robotics";
        if(s.matches(".*\\b(gpt|gemini|claude|grok|sora|codex|llm|language model|ai model|veo|ai agents?|chatgpt)\\b.*"))return "ai";
        return policy.matches("ai|robotics|space|devices")?policy:"devices";
    }
    static String eventKey(String title,String topic){
        String s=Normalizer.normalize(title,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('–','-').replace('—','-');
        s=s.replaceAll("^(?:uk|us|usa|united kingdom|germany|france|japan|india|spain|italy|brazil|canada):\\s*", "");
        // Preserve model families, versions and variants; group launch/demo/short cuts of the same product.
        Matcher model=Pattern.compile("\\b(gpt[- ]?\\d+(?:[.]\\d+)*(?:[- ](?:astra|sol|pro|mini|nano|turbo|instant|thinking))?|(?:claude|gemini)(?:[- ](?:opus|sonnet|haiku|pro|flash|fable|duet|ultra))?(?:[- ]\\d+(?:[.]\\d+)*)?(?:[- ](?:pro|flash|lite|thinking|opus|sonnet|haiku|fable))*|(?:sora|veo|grok)[- ]?\\d*(?:[.]\\d+)*)\\b").matcher(s);
        if(!"startups".equals(topic)&&model.find()&&!model.group().matches("claude|gemini|sora|veo|grok"))return "model/"+canonicalModel(model.group());
        Matcher mission=Pattern.compile("starship.*?\\b(?:flight|test)\\s*(\\d+)").matcher(s);if(!"startups".equals(topic)&&mission.find())return "space/starship/"+mission.group(1);
        Matcher product=Pattern.compile("\\b(chatgpt (?:atlas|voice)|optimus|cybercab|robotaxi|atlas|figure[ -]?\\d+|unitree[ -]?[a-z]\\d+|iphone[ -]?\\d+(?:[ -]?(?:pro|max|air))?|macbook[ -]?(?:pro|air)?|vision pro)\\b").matcher(s);
        if(!"startups".equals(topic)&&product.find())return topic+"/"+product.group().replaceAll("[^a-z0-9]","");
        s=s.split("[|]",2)[0];ArrayList<String> words=new ArrayList<>();String[] tokens=s.replaceAll("[^\\p{L}\\p{N}.]+"," ").trim().split("\\s+");for(int i=0;i<tokens.length;i++){String token=tokens[i];if(!token.isEmpty()&&(!STOP.contains(token)||(token.length()==1&&i==tokens.length-1)))words.add(token);}
        Collections.sort(words);return words.isEmpty()?"":topic+"/"+String.join("-",words);
    }
    private static String canonicalModel(String model){
        Matcher brand=Pattern.compile("^(gpt|claude|gemini|sora|veo|grok)").matcher(model);if(!brand.find())return model;
        String tail=model.substring(brand.end()),version="";Matcher number=Pattern.compile("\\d+(?:[.]\\d+)*").matcher(tail);if(number.find())version=number.group();
        ArrayList<String> variants=new ArrayList<>();for(String word:tail.replaceAll("[0-9.]+", " ").replaceAll("[^a-z]+"," ").trim().split("\\s+"))if(!word.isEmpty())variants.add(word);Collections.sort(variants);
        return brand.group()+version+String.join("",variants);
    }
    static boolean sameEvent(Trailer a,Trailer b){
        if(!a.category.equals("tech")||!b.category.equals("tech"))return false;
        if(!a.key.isEmpty()&&a.key.equals(b.key))return true;
        // For entirely new product names, merge only near-identical titles with a shared publisher.
        if(!a.channelId.equals(b.channelId)||!a.topic.equals(b.topic))return false;
        Set<String> x=words(a.title),y=words(b.title);if(x.size()<2||y.size()<2)return false;Set<String> union=new HashSet<>(x);union.addAll(y);x.retainAll(y);return x.size()>=2&&x.size()/(double)union.size()>=.8;
    }
    private static Set<String> words(String title){Set<String> words=new HashSet<>();for(String token:title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}.]+"," ").split("\\s+"))if(!STOP.contains(token)&&token.length()>1)words.add(token);return words;}
}
