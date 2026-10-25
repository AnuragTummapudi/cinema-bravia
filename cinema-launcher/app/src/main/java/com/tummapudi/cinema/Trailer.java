package com.tummapudi.cinema;

import org.json.*;
import java.util.*;

final class Trailer {
    final String id,channelId,channel,title,kind,key,category,topic;
    final long published,validated,duration;
    final List<String> thumbnails;
    Trailer(String id,String channelId,String channel,String title,String kind,long published,long validated,long duration,List<String> thumbnails){
        this(id,channelId,channel,title,kind,published,validated,duration,thumbnails,"telugu");
    }
    Trailer(String id,String channelId,String channel,String title,String kind,long published,long validated,long duration,List<String> thumbnails,String category){
        this(id,channelId,channel,title,kind,published,validated,duration,thumbnails,category,"");
    }
    Trailer(String id,String channelId,String channel,String title,String kind,long published,long validated,long duration,List<String> thumbnails,String category,String topic){
        this.category="tech".equals(category)?"tech":"hollywood".equals(category)?"hollywood":"telugu";this.topic=topic;
        this.id=id;this.channelId=channelId;this.channel=channel;this.title=title;this.kind=kind;this.published=published;this.validated=validated;this.duration=duration;
        this.thumbnails=Collections.unmodifiableList(new ArrayList<>(thumbnails));String event="tech".equals(this.category)?TechRules.eventKey(title,topic):TrailerRules.duplicateKey(title,kind);key=event.isEmpty()?"":this.category+"/"+event;
    }
    JSONObject json()throws JSONException{
        return new JSONObject().put("category",category).put("topic",topic).put("id",id).put("channelId",channelId).put("channel",channel).put("title",title).put("kind",kind).put("published",published).put("validated",validated).put("duration",duration).put("thumbnails",new JSONArray(thumbnails));
    }
    static Trailer parse(JSONObject j)throws JSONException{
        String id=j.getString("id");if(!TrailerRules.validVideoId(id))throw new JSONException("Invalid video id");
        ArrayList<String> thumbs=new ArrayList<>();JSONArray a=j.getJSONArray("thumbnails");for(int i=0;i<a.length();i++){String url=a.getString(i);if(url.startsWith("https://i.ytimg.com/"))thumbs.add(url);}
        return new Trailer(id,j.getString("channelId"),j.getString("channel"),j.getString("title"),j.getString("kind"),j.getLong("published"),j.getLong("validated"),j.optLong("duration"),thumbs,j.optString("category","telugu"),j.optString("topic",""));
    }
}
