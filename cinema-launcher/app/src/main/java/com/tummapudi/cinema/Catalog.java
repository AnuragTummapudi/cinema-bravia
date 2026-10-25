package com.tummapudi.cinema;

import android.content.Context;
import android.util.Log;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class Catalog {
    final ArrayList<String> collections = new ArrayList<>();
    final ArrayList<Movie> movies = new ArrayList<>();
    final ArrayList<String> dock = new ArrayList<>();
    final ArrayList<String> favorites = new ArrayList<>();
    String homeMovie="the-batman", homeArtwork="home/doctor-doom.jpg";
    String warning = "";
    static final class Movie {
        final String id,title,character,collection,year,runtime,summary,art,uri,pkg,action,characterArt;
        final int tint;
        Movie(JSONObject j) throws JSONException {
            id=j.getString("id"); title=j.getString("title"); character=j.getString("character");
            collection=j.getString("collection"); year=j.getString("year"); runtime=j.getString("runtime");
            summary=j.getString("summary"); art=safe(j.getString("art"));
            characterArt=j.optString("characterArt","");if(!characterArt.isEmpty() && !characterArt.matches("characters/[a-zA-Z0-9_-]+\\.(png|webp)"))throw new JSONException("Unsafe character path");
            int parsedTint=0xff315f85;try{parsedTint=android.graphics.Color.parseColor(j.optString("tint","#315f85"));}catch(IllegalArgumentException ignored){}tint=parsedTint;
            uri=j.getString("uri"); pkg=j.getString("package"); action=j.getString("actionLabel");
            if(pkg.toLowerCase(java.util.Locale.ROOT).matches(".*(factory|facmenu|diagnos|servicemode|productiontest).*")) throw new JSONException("Unsafe provider package");
            android.net.Uri parsed=android.net.Uri.parse(uri);
            if (!"https".equals(parsed.getScheme()) || parsed.getHost()==null || parsed.getUserInfo()!=null)
                throw new JSONException("Movie destinations must be HTTPS URLs");
            if (title.isEmpty() || character.isEmpty() || action.isEmpty() || title.length()>150 || summary.length()>400)
                throw new JSONException("Invalid movie text");
        }
    }
    static String safe(String path) throws JSONException {
        if (!path.matches("art/[a-zA-Z0-9_-]+\\.(jpg|png|webp)")) throw new JSONException("Unsafe artwork path");
        return path;
    }
    static String read(InputStream input) throws IOException {
        try(InputStream in=input; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int n;
            while((n=in.read(buffer))!=-1) {
                if(out.size()+n>512*1024) throw new IOException("Catalog exceeds 512 KiB");
                out.write(buffer,0,n);
            }
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    static Catalog load(Context c) {
        File external=new File(c.getExternalFilesDir(null),"catalog.json");
        if(external.exists()) try { return parse(read(new FileInputStream(external))); }
        catch(Exception e) { Log.w("Cinema","Custom catalog rejected",e); }
        try {
            Catalog result=parse(read(c.getAssets().open("catalog.json")));
            if(external.exists()) result.warning="Custom catalog could not load. Showing bundled films. Check catalog.json.";
            return result;
        } catch(Exception e) { throw new IllegalStateException("Bundled catalog is invalid",e); }
    }
    private static Catalog parse(String source) throws JSONException {
        JSONObject j=new JSONObject(source); Catalog c=new Catalog();
        JSONArray cols=j.getJSONArray("collections"), films=j.getJSONArray("movies"), apps=j.getJSONArray("dock");
        if(cols.length()<1 || cols.length()>5 || films.length()>100 || films.length()<1 || apps.length()>5)
            throw new JSONException("Catalog limits exceeded");
        HashSet<String> ids=new HashSet<>();
        for(int i=0;i<cols.length();i++) { String name=cols.getString(i); if(name.length()>20 || name.isEmpty() || c.collections.contains(name)) throw new JSONException("Invalid collection"); c.collections.add(name); }
        for(int i=0;i<films.length();i++) { Movie m=new Movie(films.getJSONObject(i)); if(!c.collections.contains(m.collection) || !ids.add(m.id)) throw new JSONException("Unknown collection or duplicate film"); c.movies.add(m); }
        for(String col:c.collections) if(c.in(col).isEmpty()) throw new JSONException("Empty collection");
        for(int i=0;i<apps.length();i++) c.dock.add(apps.getString(i));
        JSONArray favorites=j.optJSONArray("favorites");
        if(favorites!=null){if(favorites.length()>7)throw new JSONException("At most seven favourites");for(int i=0;i<favorites.length();i++){String id=favorites.getString(i);if(!ids.contains(id)||c.favorites.contains(id))throw new JSONException("Unknown or duplicate favourite");c.favorites.add(id);}}
        if(c.favorites.isEmpty())for(Movie m:c.movies)if(!m.characterArt.isEmpty() && c.favorites.size()<7)c.favorites.add(m.id);
        if(c.favorites.isEmpty())c.favorites.add(c.movies.get(0).id);
        c.homeMovie=j.optString("homeMovie",c.favorites.get(0));
        c.homeArtwork=j.optString("homeArtwork","home/doctor-doom.jpg");
        if(!c.homeArtwork.matches("home/[a-zA-Z0-9_-]+\\.(jpg|png|webp)"))throw new JSONException("Unsafe home artwork path");
        return c;
    }
    ArrayList<Movie> in(String collection) {
        ArrayList<Movie> result=new ArrayList<>(); for(Movie m:movies) if(m.collection.equals(collection)) result.add(m); return result;
    }
}
