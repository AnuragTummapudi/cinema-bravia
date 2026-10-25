package com.tummapudi.cinema;

import android.animation.ValueAnimator;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;

/** Standalone TV preview. No HOME filter, key interception service or privileged permissions. */
public final class MainActivity extends Activity {
    static final int INK=0xff101310, WHITE=0xfff5f3ed, MUTED=0xffc3c7c1, SURFACE=0xff282e29;
    FrameLayout root;
    CharacterStage stage;
    FrameLayout homeScreen,favoritesScreen;
    TextView dateView,homeTitle,homeDetail,favoritesButton,collectionsButton,favoriteSearch,homeButton;
    final ArrayList<View> homeNav=new ArrayList<>(),favoriteNav=new ArrayList<>();
    final ArrayList<Catalog.Movie> favorites=new ArrayList<>();
    boolean showingFavorites=false,showingTrailers=false,showingWeekly=false;
    TrailerSection trailers,weekly;
    int favoriteIndex=0,homeIndex=0;
    TextView title,meta,summary,clock,action,search,settings;
    LinearLayout tabs,dock;
    HorizontalScrollView selectorScroll;
    LinearLayout selector;
    Catalog catalog;
    Artwork artwork;
    Typeface face,bold;
    android.content.SharedPreferences prefs;
    final ArrayList<TextView> tabViews=new ArrayList<>();
    final ArrayList<View> movieViews=new ArrayList<>(),dockViews=new ArrayList<>();
    final ArrayList<App> installed=new ArrayList<>();
    final java.util.concurrent.ExecutorService appsWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    ArrayList<Catalog.Movie> films;
    Catalog.Movie selected;
    int collection=0, generation=0;
    String selectedId="";
    boolean ready=false,active=false;
    final Handler main=new Handler(Looper.getMainLooper());
    final Runnable ticker=new Runnable(){ public void run(){if(clock!=null) clock.setText(new java.text.SimpleDateFormat(android.text.format.DateFormat.is24HourFormat(MainActivity.this)?"HH:mm":"h:mm",Locale.getDefault()).format(new Date()));if(dateView!=null)dateView.setText(new java.text.SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(new Date())); if(active) main.postDelayed(this,30000);}};
    final Runnable artChange=new Runnable(){public void run(){ loadSelectedArt(); }};
    static final class App { String pkg,label; Drawable banner,icon; }
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    boolean motion(){return prefs.getBoolean("motion",true) && ValueAnimator.areAnimatorsEnabled();}
    protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        prefs=getSharedPreferences("cinema",MODE_PRIVATE);
        try {face=new Typeface.Builder(getAssets(),"fonts/geist.ttf").setWeight(450).build();bold=new Typeface.Builder(getAssets(),"fonts/geist.ttf").setWeight(650).build();}
        catch(Exception e){face=Typeface.create("sans-serif",0);bold=Typeface.create("sans-serif-medium",0);}
        artwork=new Artwork(this); catalog=Catalog.load(this);
        collection=Math.min(prefs.getInt("collection",0),catalog.collections.size()-1);
        selectedId=prefs.getString("selected","");
        build(); enumerateApps();
        trailers=new TrailerSection(this);trailers.setVisibility(View.GONE);root.addView(trailers,new FrameLayout.LayoutParams(-1,-1));
        weekly=new TrailerSection(this,true);weekly.setVisibility(View.GONE);root.addView(weekly,new FrameLayout.LayoutParams(-1,-1));
        if(state!=null && state.getBoolean("trailersVisible",false)){trailers.restore(state);setMode(true,false);showTrailers(false);if(state.getBoolean("weeklyVisible",false)){weekly.restore(state);showWeekly(false);}}
    }
    TextView text(String value,float size,int color){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(TypedValue.COMPLEX_UNIT_SP,size);t.setTextColor(color);t.setTypeface(face);t.setIncludeFontPadding(false);return t;
    }
    GradientDrawable background(int fill,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(2),stroke);return d;
    }
    TextView control(String label){
        TextView t=text(label,16,WHITE);t.setGravity(Gravity.CENTER);t.setPadding(dp(14),0,dp(14),0);t.setSingleLine();t.setFocusable(true);t.setClickable(true);t.setMinHeight(dp(48));t.setContentDescription(label);t.setId(View.generateViewId());
        t.setBackground(background(0x00101310,0,8));
        t.setOnFocusChangeListener((v,on)->{t.animate().cancel();t.setBackground(background(on?WHITE:0x00101310,0,8));t.setTextColor(on?INK:WHITE);t.animate().scaleX(on?1.03f:1f).scaleY(on?1.03f:1f).setDuration(motion()?160:0).setInterpolator(new DecelerateInterpolator(2)).start();});
        return t;
    }
    TextView iconControl(String label,int resource){
        TextView button=text("",16,WHITE);button.setFocusable(true);button.setClickable(true);button.setContentDescription(label);button.setId(View.generateViewId());
        Drawable icon=getDrawable(resource).mutate();icon.setBounds(0,0,dp(24),dp(24));button.setCompoundDrawables(null,icon,null,null);button.setGravity(Gravity.CENTER);button.setPadding(dp(12),dp(12),dp(12),0);
        button.setBackground(background(0x66131716,0,24));
        button.setOnFocusChangeListener((v,on)->{icon.setTint(on?INK:WHITE);button.setBackground(background(on?WHITE:0x66131716,0,24));button.animate().cancel();button.animate().scaleX(on?1.08f:1).scaleY(on?1.08f:1).setDuration(motion()?160:0).start();});return button;
    }
    void place(View v,int width,int height,int x,int y){
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));p.leftMargin=dp(x);p.topMargin=dp(y);root.addView(v,p);
    }
    void placeOn(FrameLayout parent,View view,int width,int height,int x,int y){
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(width),dp(height));lp.leftMargin=dp(x);lp.topMargin=dp(y);parent.addView(view,lp);
    }
    void build(){
        root=new FrameLayout(this);root.setClipChildren(false);setContentView(root);
        for(String id:catalog.favorites)for(Catalog.Movie movie:catalog.movies)if(movie.id.equals(id))favorites.add(movie);
        favoriteIndex=Math.max(0,Math.min(prefs.getInt("favoriteIndex",3),favorites.size()-1));
        for(int i=0;i<favorites.size();i++)if(favorites.get(i).id.equals(catalog.homeMovie))homeIndex=i;
        stage=new CharacterStage(this);stage.configure(favorites.size());root.addView(stage,new FrameLayout.LayoutParams(-1,-1));
        homeScreen=new FrameLayout(this);favoritesScreen=new FrameLayout(this);homeScreen.setClipChildren(false);favoritesScreen.setClipChildren(false);
        root.addView(homeScreen,new FrameLayout.LayoutParams(-1,-1));root.addView(favoritesScreen,new FrameLayout.LayoutParams(-1,-1));
        clock=text("",74,WHITE);clock.setLetterSpacing(-.035f);try{clock.setTypeface(new Typeface.Builder(getAssets(),"fonts/geist.ttf").setWeight(350).build());}catch(Exception ignored){}placeOn(homeScreen,clock,350,90,46,35);
        dateView=text("",15,0xffd0d8d1);dateView.setLetterSpacing(.06f);placeOn(homeScreen,dateView,390,30,51,132);
        favoritesButton=iconControl("Favourites",R.drawable.ic_favourites);search=iconControl("Search",R.drawable.ic_search);settings=iconControl("Settings",R.drawable.ic_settings);
        placeOn(homeScreen,favoritesButton,48,48,744,39);placeOn(homeScreen,search,48,48,804,39);placeOn(homeScreen,settings,48,48,864,39);
        homeNav.add(favoritesButton);homeNav.add(search);homeNav.add(settings);
        favoritesButton.setOnClickListener(v->setMode(true,true));search.setOnClickListener(v->showSearch());settings.setOnClickListener(v->showSettings());
        homeTitle=text("DOCTOR DOOM",36,WHITE);homeTitle.setTypeface(bold);homeTitle.setLetterSpacing(-.025f);placeOn(homeScreen,homeTitle,450,52,48,264);
        homeDetail=text("Avengers: Doomsday",18,0xffc4d2c5);placeOn(homeScreen,homeDetail,430,30,50,320);
        dock=new LinearLayout(this);dock.setClipChildren(false);dock.setGravity(Gravity.CENTER_VERTICAL);placeOn(homeScreen,dock,768,100,98,387);
        TextView hint=text("Down for favourites",14,0xffc4d2c5);placeOn(homeScreen,hint,240,24,50,369);
        artwork.load(catalog.homeArtwork,false,bitmap->{if(!isDestroyed())stage.setWallpaper(bitmap);});
        TextView heading=text("My favourites",19,WHITE);heading.setTypeface(bold);placeOn(favoritesScreen,heading,260,40,48,34);
        collectionsButton=iconControl("Collections",R.drawable.ic_apps);favoriteSearch=iconControl("Search",R.drawable.ic_search);homeButton=iconControl("Home",R.drawable.ic_home);
        placeOn(favoritesScreen,collectionsButton,48,48,744,27);placeOn(favoritesScreen,favoriteSearch,48,48,804,27);placeOn(favoritesScreen,homeButton,48,48,864,27);
        favoriteNav.add(collectionsButton);favoriteNav.add(favoriteSearch);favoriteNav.add(homeButton);
        collectionsButton.setOnClickListener(v->showCollections());favoriteSearch.setOnClickListener(v->showSearch());homeButton.setOnClickListener(v->setMode(false,true));
        title=text("",28,WHITE);title.setTypeface(bold);title.setGravity(Gravity.CENTER);title.setSingleLine();title.setEllipsize(TextUtils.TruncateAt.END);placeOn(favoritesScreen,title,640,42,160,83);
        action=text("",15,MUTED);action.setGravity(Gravity.CENTER);placeOn(favoritesScreen,action,640,29,160,129);
        meta=text("",16,WHITE);meta.setGravity(Gravity.CENTER);meta.setSingleLine();meta.setEllipsize(TextUtils.TruncateAt.END);placeOn(favoritesScreen,meta,740,30,110,477);
        summary=text("",17,WHITE);
        for(int i=0;i<favorites.size();i++){
            final int index=i;Catalog.Movie movie=favorites.get(i);
            View target=new View(this);target.setId(View.generateViewId());target.setFocusable(true);target.setClickable(true);target.setContentDescription(movie.character+", "+movie.title+". "+movie.action);
            placeOn(favoritesScreen,target,96,260,Math.round(stage.centers[i]*960)-48,178);movieViews.add(target);
            target.setOnFocusChangeListener((v,on)->{if(on)selectFavorite(index);});target.setOnClickListener(v->openMovie(movie));
            String asset=movie.characterArt.isEmpty()?movie.art:movie.characterArt;
            artwork.load(asset,false,bitmap->{if(isDestroyed())return;stage.setPortrait(index,bitmap);});
        }
        selectFavorite(favoriteIndex);setMode(false,false);
        root.post(()->favoritesButton.requestFocus());
        if(!catalog.warning.isEmpty())new AlertDialog.Builder(this).setMessage(catalog.warning).setPositiveButton("OK",null).show();
    }
    void setMode(boolean favoritesMode,boolean focus){
        if(showingWeekly){showingWeekly=false;weekly.leave();weekly.animate().cancel();weekly.setVisibility(View.GONE);trailers.setTranslationY(0);}
        if(showingTrailers){showingTrailers=false;trailers.leave();trailers.animate().cancel();trailers.setVisibility(View.GONE);stage.animate().cancel();favoritesScreen.animate().cancel();stage.setTranslationY(0);stage.setVisibility(View.VISIBLE);favoritesScreen.setTranslationY(0);}
        showingFavorites=favoritesMode;homeScreen.setVisibility(favoritesMode?View.GONE:View.VISIBLE);favoritesScreen.setVisibility(favoritesMode?View.VISIBLE:View.GONE);
        stage.setHome(!favoritesMode);stage.select(favoritesMode?favoriteIndex:homeIndex,favorites.get(favoritesMode?favoriteIndex:homeIndex).tint,false);stage.setArtwork(prefs.getBoolean("artwork",true));
        if(focus){if(favoritesMode)focusMovie();else if(!dockViews.isEmpty())dockViews.get(0).requestFocus();else favoritesButton.requestFocus();}
    }
    void showTrailers(boolean animate){
        if(trailers==null)return;
        showingTrailers=true;showingFavorites=true;homeScreen.setVisibility(View.GONE);stage.stop();
        trailers.animate().cancel();stage.animate().cancel();favoritesScreen.animate().cancel();
        float height=root.getHeight()>0?root.getHeight():dp(540);
        trailers.setVisibility(View.VISIBLE);trailers.setTranslationY(animate?height:0);
        long duration=animate&&motion()?240:0;
        trailers.animate().translationY(0).setDuration(duration).setInterpolator(new DecelerateInterpolator(2)).start();
        stage.animate().translationY(-height).setDuration(duration).start();
        favoritesScreen.animate().translationY(-height).setDuration(duration).withEndAction(()->{if(showingTrailers){favoritesScreen.setVisibility(View.GONE);stage.setVisibility(View.INVISIBLE);}}).start();
        trailers.enter();
    }
    void hideTrailers(boolean animate){
        if(!showingTrailers)return;showingTrailers=false;trailers.leave();
        trailers.animate().cancel();stage.animate().cancel();favoritesScreen.animate().cancel();
        float height=root.getHeight()>0?root.getHeight():dp(540);long duration=animate&&motion()?240:0;
        favoritesScreen.setVisibility(View.VISIBLE);stage.setVisibility(View.VISIBLE);
        stage.select(favoriteIndex,favorites.get(favoriteIndex).tint,false);
        favoritesScreen.animate().translationY(0).setDuration(duration).start();stage.animate().translationY(0).setDuration(duration).start();
        trailers.animate().translationY(height).setDuration(duration).setInterpolator(new DecelerateInterpolator(2)).withEndAction(()->{if(!showingTrailers)trailers.setVisibility(View.GONE);}).start();focusMovie();
    }
    void showWeekly(boolean animate){
        if(showingWeekly||!showingTrailers)return;showingWeekly=true;trailers.leave();trailers.animate().cancel();weekly.animate().cancel();
        float height=root.getHeight()>0?root.getHeight():dp(540);long duration=animate&&motion()?240:0;
        weekly.setVisibility(View.VISIBLE);weekly.setTranslationY(animate?height:0);weekly.animate().translationY(0).setDuration(duration).setInterpolator(new DecelerateInterpolator(2)).start();
        trailers.animate().translationY(-height).setDuration(duration).withEndAction(()->{if(showingWeekly)trailers.setVisibility(View.GONE);}).start();weekly.enter();
    }
    void hideWeekly(boolean animate){
        if(!showingWeekly)return;showingWeekly=false;weekly.leave();weekly.animate().cancel();trailers.animate().cancel();
        float height=root.getHeight()>0?root.getHeight():dp(540);long duration=animate&&motion()?240:0;trailers.setVisibility(View.VISIBLE);trailers.animate().translationY(0).setDuration(duration).start();
        weekly.animate().translationY(height).setDuration(duration).setInterpolator(new DecelerateInterpolator(2)).withEndAction(()->{if(!showingWeekly)weekly.setVisibility(View.GONE);}).start();trailers.enter();
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("trailersVisible",showingTrailers);if(trailers!=null)trailers.save(state);state.putBoolean("weeklyVisible",showingWeekly);if(weekly!=null)weekly.save(state);}
    void selectFavorite(int index){
        favoriteIndex=index;selected=favorites.get(index);selectedId=selected.id;
        title.setText(selected.title);action.setText(selected.action);
        meta.setText(selected.character+"    "+selected.year+" / "+selected.runtime);
        if(showingFavorites)stage.select(index,selected.tint,motion());
        prefs.edit().putInt("favoriteIndex",index).apply();android.util.Log.i("Cinema","SELECT "+selected.id);
    }
    void focusMovie(){if(!movieViews.isEmpty())movieViews.get(favoriteIndex).requestFocus();}
    void loadSelectedArt(){stage.setArtwork(prefs.getBoolean("artwork",true));}
    void showCollections(){
        String[] names=catalog.collections.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Collections").setItems(names,(d,index)->{
            ArrayList<Catalog.Movie> movies=catalog.in(names[index]);String[] labels=new String[movies.size()];
            for(int i=0;i<labels.length;i++)labels[i]=movies.get(i).title+" / "+movies.get(i).action;
            new AlertDialog.Builder(this).setTitle(names[index]).setItems(labels,(dialog,film)->openMovie(movies.get(film))).setNegativeButton("Back",null).show();
        }).setNegativeButton("Back",null).show();
    }
    void enumerateApps(){
        appsWorker.execute(()->{
            ArrayList<App> found=new ArrayList<>();PackageManager pm=getPackageManager();
            Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            HashSet<String> seen=new HashSet<>();
            for(ResolveInfo r:pm.queryIntentActivities(query,0)){
                String pkg=r.activityInfo.packageName;String lower=pkg.toLowerCase(Locale.ROOT);
                if(pkg.equals(getPackageName()) || !seen.add(pkg) || lower.matches(".*(factory|facmenu|diagnos|servicemode|productiontest).*"))continue;
                App a=new App();a.pkg=pkg;a.label=r.loadLabel(pm).toString();
                try{a.icon=compact(r.loadIcon(pm),dp(48),dp(48));a.banner=compact(r.activityInfo.loadBanner(pm),dp(110),dp(62));}catch(RuntimeException ignored){}
                found.add(a);
            }
            Collections.sort(found,(a,b)->a.label.compareToIgnoreCase(b.label));
            main.post(()->{if(isDestroyed())return;installed.clear();installed.addAll(found);fillDock();});
        });
    }
    Drawable compact(Drawable source,int width,int height){
        if(source==null)return null;
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);int iw=Math.max(1,source.getIntrinsicWidth()),ih=Math.max(1,source.getIntrinsicHeight());
        float scale=Math.min(width/(float)iw,height/(float)ih);int w=Math.round(iw*scale),h=Math.round(ih*scale);
        source.setBounds((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2);source.draw(canvas);bitmap.prepareToDraw();return new BitmapDrawable(getResources(),bitmap);
    }
    ArrayList<String> dockPackages(){
        String custom=prefs.getString("dock",null);return custom==null?new ArrayList<>(catalog.dock):new ArrayList<>(Arrays.asList(custom.split(",")));
    }
    void fillDock(){
        dock.removeAllViews();dockViews.clear();
        Bitmap halo=DockItem.focusHalo(getResources().getDisplayMetrics().density);
        for(String pkg:dockPackages()){
            App found=null;for(App candidate:installed)if(candidate.pkg.equals(pkg))found=candidate;
            if(found==null)continue;final App app=found;
            Drawable logo=app.icon;float width=48,height=48;
            if(pkg.equals("com.netflix.ninja")){logo=getDrawable(R.drawable.brand_netflix);width=56;height=56;}
            else if(pkg.equals("com.google.android.youtube.tv")){logo=getDrawable(R.drawable.dock_youtube);width=58;height=41;}
            else if(pkg.equals("com.amazon.amazonvideo.livingroom")){logo=getDrawable(R.drawable.dock_prime);width=74;height=18;}
            else if(pkg.equals("in.startv.hotstar")){logo=new DockItem.Hotstar(this);width=64;height=57;}
            else if(pkg.equals("com.graymatrix.did")){logo=getDrawable(R.drawable.dock_zee5);width=74;height=62;}
            if(logo==null)continue;
            DockItem tile=addDockItem(logo,width,height,"Open "+app.label,halo);
            tile.setOnClickListener(v->launchApp(app.pkg));
        }
        DockItem apps=addDockItem(getDrawable(R.drawable.dock_apps),34,34,"All apps",halo);
        apps.setOnClickListener(v->showApps(false));
        if(!showingFavorites && (getCurrentFocus()==favoritesButton||getCurrentFocus()==null)&&!dockViews.isEmpty())dockViews.get(0).requestFocus();
    }
    DockItem addDockItem(Drawable logo,float width,float height,String description,Bitmap halo){
        final int index=dockViews.size();DockItem tile=new DockItem(this,logo,width,height,halo);
        tile.setContentDescription(description);dock.addView(tile,new LinearLayout.LayoutParams(dp(128),dp(100)));dockViews.add(tile);
        tile.setOnFocusChangeListener((v,on)->{tile.focus(on,motion());if(on)stage.shiftHome(-1f+index*.4f,motion());});return tile;
    }
    void launchApp(String pkg){
        Intent intent=getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if(intent==null){error("App unavailable","This app is no longer installed or enabled.");return;}
        try{startActivity(intent);}catch(ActivityNotFoundException|SecurityException e){error("Could not open app","Use All apps or check the app in TV settings.");}
    }
    void openMovie(Catalog.Movie film){
        if(film==null)return;
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(film.uri)).setPackage(film.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(intent.resolveActivity(getPackageManager())==null){
            new AlertDialog.Builder(this).setTitle("Movie link unavailable").setMessage("The installed provider cannot open this link. Open the app and search for "+film.title+".").setPositiveButton("Open app",(d,w)->launchApp(film.pkg)).setNegativeButton("Back",null).show();return;
        }
        try{startActivity(intent);android.util.Log.i("Cinema","DESTINATION "+film.id+" "+film.pkg+" "+film.uri);}
        catch(ActivityNotFoundException|SecurityException e){error("Could not open movie","Open the provider from the app dock and search for "+film.title+".");}
    }
    void error(String title,String message){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show();}
    Dialog panel(String heading){
        Dialog dialog=new Dialog(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(32),dp(28),dp(32),dp(28));body.setBackgroundColor(INK);
        TextView h=text(heading,28,WHITE);h.setTypeface(bold);body.addView(h,new LinearLayout.LayoutParams(-1,dp(54)));
        dialog.setContentView(body);Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(INK));window.setLayout(dp(680),dp(440));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.8f);}
        dialog.setOnShowListener(d->{dialog.getWindow().setLayout(dp(680),dp(440));});return dialog;
    }
    LinearLayout panelBody(Dialog d){return (LinearLayout)((ViewGroup)d.findViewById(android.R.id.content)).getChildAt(0);}
    void showApps(boolean customize){
        if(customize){
            CharSequence[] labels=new CharSequence[installed.size()];boolean[] checked=new boolean[installed.size()];ArrayList<String> chosen=dockPackages();
            for(int i=0;i<labels.length;i++){labels[i]=installed.get(i).label;checked[i]=chosen.contains(installed.get(i).pkg);}
            new AlertDialog.Builder(this).setTitle("Choose up to five dock apps").setMultiChoiceItems(labels,checked,(d,index,on)->{if(on && chosen.size()>=5){((AlertDialog)d).getListView().setItemChecked(index,false);Toast.makeText(this,"Choose up to five apps.",Toast.LENGTH_SHORT).show();return;}if(on)chosen.add(installed.get(index).pkg);else chosen.remove(installed.get(index).pkg);})
                .setPositiveButton("Save",(d,w)->{prefs.edit().putString("dock",TextUtils.join(",",chosen)).apply();fillDock();}).setNegativeButton("Cancel",null).show();return;
        }
        Dialog d=panel("Your apps");LinearLayout body=panelBody(d);ListView list=new ListView(this);list.setDivider(null);list.setSelector(background(SURFACE,WHITE,8));String[] labels=new String[installed.size()];for(int i=0;i<labels.length;i++)labels[i]=installed.get(i).label;
        list.setAdapter(listAdapter(Arrays.asList(labels)));body.addView(list,new LinearLayout.LayoutParams(-1,0,1));list.setOnItemClickListener((p,v,index,id)->{d.dismiss();launchApp(installed.get(index).pkg);});d.show();list.requestFocus();
    }
    ArrayAdapter<String> listAdapter(java.util.List<String> labels){
        return new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels){public View getView(int p,View recycled,ViewGroup parent){TextView t=(TextView)super.getView(p,recycled,parent);t.setTextColor(WHITE);t.setTypeface(face);t.setTextSize(18);t.setMinHeight(dp(52));t.setGravity(Gravity.CENTER_VERTICAL);return t;}};
    }
    void showSearch(){
        Dialog d=panel("Search films and apps");LinearLayout body=panelBody(d);
        EditText input=new EditText(this);input.setTextColor(WHITE);input.setHintTextColor(MUTED);input.setHint("Film, character or app");input.setContentDescription("Search films, characters and installed apps");input.setSingleLine();input.setTextSize(18);input.setTypeface(face);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);body.addView(input,new LinearLayout.LayoutParams(-1,dp(52)));
        ListView results=new ListView(this);results.setDivider(null);results.setSelector(background(SURFACE,WHITE,8));body.addView(results,new LinearLayout.LayoutParams(-1,0,1));
        ArrayList<Runnable> actions=new ArrayList<>();
        Runnable filter=()->{
            String query=input.getText().toString().trim().toLowerCase(Locale.ROOT);ArrayList<String> names=new ArrayList<>();actions.clear();
            for(Catalog.Movie m:catalog.movies)if((m.title+" "+m.character).toLowerCase(Locale.ROOT).contains(query)) {names.add(m.title+"  /  "+m.action);actions.add(()->{d.dismiss();openMovie(m);});}
            for(App a:installed)if(a.label.toLowerCase(Locale.ROOT).contains(query)){names.add(a.label);actions.add(()->{d.dismiss();launchApp(a.pkg);});}
            if(names.isEmpty()){names.add("No matches. Try another film, character or app.");actions.add(()->{});}results.setAdapter(listAdapter(names));
        };
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter.run();}public void afterTextChanged(Editable e){}});
        input.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_SEARCH){results.requestFocus();return true;}return false;});
        results.setOnItemClickListener((p,v,index,id)->actions.get(index).run());filter.run();d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);d.show();input.requestFocus();
    }
    void showSettings(){
        String[] options={"TV settings","Choose dock apps","Motion: "+(prefs.getBoolean("motion",true)?"On":"Off"),"Artwork: "+(prefs.getBoolean("artwork",true)?"On":"Off"),"Larger text: "+(prefs.getBoolean("largeText",false)?"On":"Off"),"Reload custom catalog","Artwork credits and help","YouTube trailer sources"};
        new AlertDialog.Builder(this).setTitle("Cinema settings").setItems(options,(dialog,index)->{
            switch(index){
            case 0:try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(ActivityNotFoundException e){error("Settings unavailable","Use the Settings key on your Sony remote.");}break;
            case 1:showApps(true);break;
            case 2:prefs.edit().putBoolean("motion",!prefs.getBoolean("motion",true)).apply();stage.select(showingFavorites?favoriteIndex:homeIndex,favorites.get(showingFavorites?favoriteIndex:homeIndex).tint,false);showSettings();break;
            case 3:prefs.edit().putBoolean("artwork",!prefs.getBoolean("artwork",true)).apply();loadSelectedArt();showSettings();break;
            case 4:prefs.edit().putBoolean("largeText",!prefs.getBoolean("largeText",false)).apply();applyTextSize();showSettings();break;
            case 5:catalog=Catalog.load(this);collection=Math.min(collection,catalog.collections.size()-1);artwork.trim();recreate();break;
            case 6:showCredits();break;
            case 7:trailers.showSources();break;
            }
        }).setNegativeButton("Back",null).show();
    }
    void applyTextSize(){boolean large=prefs.getBoolean("largeText",false);summary.setTextSize(large?19:17);meta.setTextSize(large?18:16);}
    void showCredits(){
        Dialog d=panel("Artwork and app information");LinearLayout body=panelBody(d);ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.tmdb);logo.setContentDescription("The Movie Database");logo.setScaleType(ImageView.ScaleType.FIT_START);body.addView(logo,new LinearLayout.LayoutParams(dp(137),dp(30)));ScrollView scroll=new ScrollView(this);TextView copy=text("Movie metadata and artwork: The Movie Database (TMDB).\n\nThis product uses the TMDB API but is not endorsed or certified by TMDB.\n\nFilm images belong to their respective studios and rights holders. This is a personal, noncommercial preview; artwork is not licensed for redistribution. No TMDB API requests occur at runtime.\n\nGeist font: SIL Open Font License. Character cutouts: PNGimg.com and PNG Gallery, CC BY-NC 4.0 as stated at pngimg.com/license. Images displayed without repainted faces; source pixels are cropped and tinted at runtime. Character and film rights remain with their owners. Full source links in the supplied character-sources.json. Doctor Doom wallpaper: HDQWalls.com, a 3840 x 2160 fan wallpaper, not an official film still. Personal preview only.\n\nNative launcher patterns informed by Gathin's CleanYourTV (MIT).\n\nMovie links open the corresponding streaming app. Provider membership, rental and regional availability determine access. Some links start playback; others open the title details and Play controls.\n\nConfigure catalog.json and art/ in this app's external files directory, then choose Reload custom catalog.\n\nHome returns to Google TV. Back closes panels, then exits this standalone preview.",17,WHITE);copy.setLineSpacing(dp(6),1);scroll.addView(copy);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));d.show();scroll.setFocusable(true);scroll.requestFocus();
    }
    protected void onResume(){super.onResume();active=true;if(stage!=null&&!showingTrailers)stage.select(showingFavorites?favoriteIndex:homeIndex,favorites.get(showingFavorites?favoriteIndex:homeIndex).tint,false);if(trailers!=null)trailers.resume();if(weekly!=null)weekly.resume();TrailerRepository.get(this).setForeground(true);main.removeCallbacks(ticker);ticker.run();if(summary!=null)applyTextSize();}
    protected void onPause(){super.onPause();active=false;if(trailers!=null)trailers.pause();if(weekly!=null)weekly.pause();TrailerRepository.get(this).setForeground(false);main.removeCallbacks(ticker);if(stage!=null)stage.stop();}
    public void onTrimMemory(int level){super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW){if(artwork!=null)artwork.trim();if(trailers!=null)trailers.trim();if(weekly!=null)weekly.trim();}}
    protected void onDestroy(){if(trailers!=null)trailers.close();if(weekly!=null)weekly.close();main.removeCallbacksAndMessages(null);appsWorker.shutdownNow();if(artwork!=null)artwork.close();super.onDestroy();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if(key>=KeyEvent.KEYCODE_DPAD_UP && key<=KeyEvent.KEYCODE_DPAD_RIGHT){
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return true;
            if(showingWeekly)return weekly.navigate(key);if(showingTrailers)return trailers.navigate(key);
            View focus=getCurrentFocus();int movie=movieViews.indexOf(focus),app=dockViews.indexOf(focus);ArrayList<View> nav=showingFavorites?favoriteNav:homeNav;int top=nav.indexOf(focus);
            if(showingFavorites && movie>=0){
                if(key==KeyEvent.KEYCODE_DPAD_LEFT)movieViews.get(Math.max(0,movie-1)).requestFocus();
                else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)movieViews.get(Math.min(movieViews.size()-1,movie+1)).requestFocus();
                else if(key==KeyEvent.KEYCODE_DPAD_UP)collectionsButton.requestFocus();
                else if(key==KeyEvent.KEYCODE_DPAD_DOWN)showTrailers(true);
            }else if(!showingFavorites && app>=0){
                if(key==KeyEvent.KEYCODE_DPAD_LEFT)dockViews.get(Math.max(0,app-1)).requestFocus();
                else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)dockViews.get(Math.min(dockViews.size()-1,app+1)).requestFocus();
                else if(key==KeyEvent.KEYCODE_DPAD_UP)favoritesButton.requestFocus();
                else if(key==KeyEvent.KEYCODE_DPAD_DOWN)setMode(true,true);
            }else if(top>=0){
                if(key==KeyEvent.KEYCODE_DPAD_LEFT)nav.get(Math.max(0,top-1)).requestFocus();
                else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)nav.get(Math.min(nav.size()-1,top+1)).requestFocus();
                else if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(showingFavorites)focusMovie();else if(!dockViews.isEmpty())dockViews.get(0).requestFocus();}
            }else return super.dispatchKeyEvent(event);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    public void onBackPressed(){if(showingWeekly)hideWeekly(true);else if(showingTrailers)hideTrailers(true);else if(showingFavorites)setMode(false,true);else super.onBackPressed();}
}
