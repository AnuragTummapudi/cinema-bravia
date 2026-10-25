package com.tummapudi.cinema;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.*;

/** Pure Java discovery policy; intentionally prefers precision to ambiguous matches. */
final class TrailerRules {
    private static final Pattern EXCLUDE=Pattern.compile("(?i)\\b(reactions?|reviews?|interviews?|songs?|lyrical|lyrics|jukebox|fan[ -]?made|fan edit|concept|unofficial|mashup|remix|shorts|promo|glimpse|announcement|launch|speech|vibing|audience|countdown|tomorrow|coming soon|begins soon|live event|press meet|making|behind the scenes|full movie|out now|outnow|web series|season|episodes?|hours? to go|minutes? to go)\\b|\\b(?:trailer|teaser)\\s+on\\b");
    private static final Pattern TYPE=Pattern.compile("(?i)\\b(trailer|teaser)\\b|ట్రైలర్|టీజర్");
    private static final Pattern TELUGU=Pattern.compile("(?i)\\btelugu\\b|తెలుగు");
    private static final Pattern OTHER=Pattern.compile("(?i)\\b(hindi|tamil|kannada|malayalam|bengali|marathi|english)\\b");
    static String classify(String title,String description,String audio,String languagePolicy,long seconds,boolean live){
        if(!titleEligible(title) || live || seconds<10 || seconds>600)return "";
        Matcher type=TYPE.matcher(title);if(!type.find())return "";
        boolean explicit=TELUGU.matcher(title).find();
        if(!explicit && OTHER.matcher(title).find())return "";
        boolean language=explicit || (audio!=null && audio.toLowerCase(Locale.ROOT).matches("te(?:[-_].*)?"))
            || "teluguOnly".equals(languagePolicy)
            || Pattern.compile("(?i)(?:telugu|తెలుగు)\\s+(?:official\\s+)?(?:movie|film|trailer|teaser)|(?:trailer|teaser)\\s*[-:(]\\s*telugu").matcher(description==null?"":description).find();
        if(!language)return "";
        String matched=type.group().toLowerCase(Locale.ROOT);return matched.equals("teaser")||matched.equals("టీజర్")?"Teaser":"Trailer";
    }
    static boolean titleEligible(String title){return title!=null&&!EXCLUDE.matcher(title).find()&&TYPE.matcher(title).find()&&(TELUGU.matcher(title).find()||!OTHER.matcher(title).find());}
    private static final Pattern NON_ENGLISH=Pattern.compile("(?i)\\b(telugu|hindi|tamil|kannada|malayalam|bengali|marathi|spanish|espa[nñ]ol|french|fran[cç]ais|german|deutsch|italian|portuguese|japanese|korean|mandarin|chinese|arabic|russian|dubbed)\\b|తెలుగు|हिंदी|தமிழ்");
    private static final Pattern NON_FILM=Pattern.compile("(?i)\\b(video game|gameplay|roblox|playable|game|fortnite|rivals|ps5|xbox|disney[+]|disney plus|streaming series|tv series|television|watch trailer now|trailer out|trailer just|stream only)\\b");
    private static final Pattern INDIAN_ORIGIN=Pattern.compile("(?i)\\b(indian (?:film|cinema|epic)|bollywood|tollywood|telugu[- ]language|hindi[- ]language|tamil[- ]language)\\b");
    static boolean titleEligible(String title,String category){
        if(!"hollywood".equals(category))return titleEligible(title);
        return title!=null&&!EXCLUDE.matcher(title).find()&&TYPE.matcher(title).find()&&!NON_ENGLISH.matcher(title).find()&&!NON_FILM.matcher(title).find();
    }
    static String classify(String title,String description,String audio,String policy,long seconds,boolean live,String category){
        if(!"hollywood".equals(category))return classify(title,description,audio,policy,seconds,live);
        if(!titleEligible(title,category)||live||seconds<30||seconds>600)return "";
        if(audio!=null&&!audio.isEmpty()&&!audio.toLowerCase(Locale.ROOT).matches("en(?:[-_].*)?"))return "";
        if(!"englishOnly".equals(policy)||INDIAN_ORIGIN.matcher(description==null?"":description).find())return "";
        Matcher type=TYPE.matcher(title);if(!type.find())return "";return type.group().equalsIgnoreCase("teaser")?"Teaser":"Trailer";
    }
    static String duplicateKey(String title,String kind){
        String s=Normalizer.normalize(title,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        // Credits after a pipe are not a movie title. Keep trailer edition numbers and 'release'.
        s=s.split("[|]",2)[0];String edition="";
        Matcher n=Pattern.compile("(?:trailer|teaser)\\s*(?:#|no[.]?\\s*)?(\\d+)").matcher(s);if(n.find())edition=n.group(1);
        if(s.contains("release trailer"))edition+="release";
        s=s.replaceAll("(?i)\\b(official|theatrical|telugu|trailer|teaser|4k|hd|uhd|imax)\\b", " ").replaceAll("ట్రైలర్|టీజర్|తెలుగు", " ");
        s=s.replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
        return s.length()<2?"":s+"/"+kind+"/te/"+edition;
    }
    static String displayTitle(String original){
        String segment=original.split("[|]",2)[0];Matcher type=TYPE.matcher(segment);
        if(type.find()&&type.start()>0){
            String film=segment.substring(0,type.start()).replaceAll("(?i)\\b(official|theatrical|telugu|4k|hd)\\b", " ").replaceAll("తెలుగు", " ").replaceAll("[\\p{So}#]", "").replaceAll("^[\\s:–—-]+|[\\s:–—-]+$", "").replaceAll("\\s+"," ").trim();
            if(film.length()>1)return film;
        }
        return original;
    }
    static boolean validVideoId(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{11}");}
    static long newExpiryDelay(long published,long now){if(published>now)return published-now;long remaining=72L*3600000-(now-published);return remaining>=0?remaining+1:-1;}
    static boolean isNew(long published,long now){return published<=now && now-published<=72L*3600000;}
}
