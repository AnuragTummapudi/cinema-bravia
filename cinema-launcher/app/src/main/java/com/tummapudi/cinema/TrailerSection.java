package com.tummapudi.cinema;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;

/** Native below-Favourites viewport. Existing Home and character layouts are unchanged. */
final class TrailerSection extends FrameLayout implements TrailerRepository.Listener {
    private final MainActivity host;
    private final boolean weekly;
    private final TrailerRepository repository;
    private final TrailerImages images;
    private final TextView status,empty,refresh,sources,heading,categoryButton;
    private final HorizontalScrollView scroll;
    private final LinearLayout row;
    private final ArrayList<Card> cards=new ArrayList<>();
    private List<Trailer> values=Collections.emptyList();
    private String category="telugu";private PopupWindow categoryMenu;
    private String selectedId="";private int selectedIndex=0,savedScroll=0;
    private boolean listening=false,visible=false,opening=false,rendered=false,restoring=false;
    private TrailerRepository.Snapshot state;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable expireBadges=()->updateBadges();
    private final Runnable periodic=()->{repositoryRefresh();};
    private final class Card extends FrameLayout {
        final Trailer trailer;final FrameLayout artwork;final ImageView image;final TextView fallback,label,newBadge;
        Card(Trailer trailer){
            super(host);this.trailer=trailer;setFocusable(true);setClickable(true);setId(View.generateViewId());setClipChildren(false);
            setContentDescription(trailer.title+", "+trailer.kind+", "+trailer.channel+", published "+date(trailer.published)+". Open in YouTube");
            artwork=new FrameLayout(host);artwork.setBackground(host.background(0xff121713,0,10));artwork.setClipToOutline(true);addView(artwork,new FrameLayout.LayoutParams(dp(352),dp(198)));
            image=new ImageView(host);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);artwork.addView(image,new FrameLayout.LayoutParams(-1,-1));
            fallback=host.text("",14,MainActivity.MUTED);fallback.setGravity(Gravity.CENTER);artwork.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
            {TextView badge=host.text("NEW",10,MainActivity.INK);badge.setTypeface(host.bold);badge.setGravity(Gravity.CENTER);badge.setBackground(host.background(0xffe8dfc9,0,3));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(44),dp(22));p.leftMargin=dp(12);p.topMargin=dp(12);artwork.addView(badge,p);newBadge=badge;newBadge.setVisibility(TrailerRules.isNew(trailer.published,System.currentTimeMillis())?VISIBLE:GONE);}
            label=host.text(weekly?trailer.title:TrailerRules.displayTitle(trailer.title),20,MainActivity.WHITE);label.setTypeface(host.bold);label.setMaxLines(2);label.setEllipsize(TextUtils.TruncateAt.END);label.setLineSpacing(dp(2),1);put(label,352,48,0,214);
            TextView details=host.text(trailer.channel,13,0xffc4d2c5);details.setSingleLine();details.setEllipsize(TextUtils.TruncateAt.END);put(details,352,22,0,266);
            TextView published=host.text((weekly?TechRules.label(trailer.topic):trailer.kind)+"  ·  "+date(trailer.published),12,0xffc4d2c5);put(published,352,20,0,289);
            setOnFocusChangeListener((v,on)->{
                animate().cancel();artwork.setForeground(host.background(0,on?MainActivity.WHITE:0,10));
                animate().scaleX(on?1.035f:1).scaleY(on?1.035f:1).setDuration(host.motion()?180:0).setInterpolator(new DecelerateInterpolator(2)).start();
                if(on){selectedId=trailer.id;selectedIndex=cards.indexOf(this);saveSelection();if(!restoring)post(()->{int left=getLeft()-dp(48);if(host.motion())scroll.smoothScrollTo(Math.max(0,left),0);else scroll.scrollTo(Math.max(0,left),0);updateImages();});}
            });setOnClickListener(v->open(trailer));
        }
        void put(View view,int w,int h,int x,int y){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(w),dp(h));p.leftMargin=dp(x);p.topMargin=dp(y);addView(view,p);}
        @Override public CharSequence getAccessibilityClassName(){return "android.widget.Button";}
    }
    TrailerSection(MainActivity host){this(host,false);}
    TrailerSection(MainActivity host,boolean weekly){
        super(host);this.host=host;this.weekly=weekly;repository=TrailerRepository.get(host);images=new TrailerImages(host);
        category=host.prefs.getString(pref("Category"),weekly?"all":"telugu");if(weekly){if(!Arrays.asList("all","ai","startups","robotics","space","devices").contains(category))category="all";}else if(!"hollywood".equals(category))category="telugu";loadSelection();
        setBackgroundColor(0xff090d13);setClipChildren(false);setFocusable(false);
        heading=host.text("",26,MainActivity.WHITE);heading.setTypeface(host.bold);put(heading,650,40,48,34);
        categoryButton=host.control("");categoryButton.setPadding(dp(12),0,dp(12),0);categoryButton.setTextSize(15);categoryButton.setCompoundDrawablePadding(dp(10));
        android.graphics.drawable.Drawable chevron=new android.graphics.drawable.Drawable(){
            private final android.graphics.Paint paint=new android.graphics.Paint(3);
            public void draw(android.graphics.Canvas canvas){paint.setColor(categoryButton.hasFocus()?MainActivity.INK:MainActivity.WHITE);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(dp(1.6f));paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);android.graphics.Rect b=getBounds();android.graphics.Path path=new android.graphics.Path();path.moveTo(b.left+dp(2),b.centerY()-dp(2));path.lineTo(b.centerX(),b.centerY()+dp(3));path.lineTo(b.right-dp(2),b.centerY()-dp(2));canvas.drawPath(path,paint);}
            public void setAlpha(int alpha){}public void setColorFilter(android.graphics.ColorFilter filter){}public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
        };chevron.setBounds(0,0,dp(16),dp(16));categoryButton.setCompoundDrawables(null,null,chevron,null);put(categoryButton,180,48,732,30);categoryButton.setOnClickListener(v->showCategories());updateCategoryLabel();
        status=host.text("Official trailers and teasers · YouTube",14,0xffc4d2c5);status.setSingleLine();status.setEllipsize(TextUtils.TruncateAt.END);put(status,810,28,48,79);
        ImageView youtube=new ImageView(host);youtube.setImageDrawable(host.getDrawable(R.drawable.trailer_youtube).mutate());youtube.setContentDescription("YouTube");put(youtube,36,26,876,84);
        scroll=new HorizontalScrollView(host);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipChildren(false);scroll.setClipToPadding(false);scroll.setOverScrollMode(OVER_SCROLL_NEVER);scroll.setFocusable(false);scroll.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        row=new LinearLayout(host);row.setClipChildren(false);row.setClipToPadding(false);row.setPadding(dp(48),dp(16),dp(48),dp(8));scroll.addView(row,new HorizontalScrollView.LayoutParams(-2,-1));put(scroll,960,338,0,126);
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{savedScroll=x;updateImages();});
        empty=host.text("",19,MainActivity.WHITE);empty.setGravity(Gravity.CENTER_VERTICAL);empty.setLineSpacing(dp(7),1);put(empty,800,190,48,147);
        refresh=host.control("Refresh");put(refresh,118,48,48,466);refresh.setOnClickListener(v->{if(state==null||!state.configured)setup();else repository.refresh(true,null);});
        sources=host.control("Sources");put(sources,118,48,184,466);sources.setOnClickListener(v->showSources());
        TextView hint=host.text("UP to My favourites",14,0xffc4d2c5);hint.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);hint.setText(weekly?"UP to trailers":"DOWN for weekly tech");put(hint,260,30,652,475);
    }
    private int dp(float value){return host.dp(value);}
    private void put(View view,int width,int height,int x,int y){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(width),dp(height));p.leftMargin=dp(x);p.topMargin=dp(y);addView(view,p);}
    void resume(){scroll.setSmoothScrollingEnabled(host.motion());updateBadges();if(!listening){listening=true;repository.add(this);}repository.refresh(false,null);handler.removeCallbacks(periodic);handler.postDelayed(periodic,TrailerRepository.SIX_HOURS);}
    void pause(){saveSelection();if(listening){repository.remove(this);listening=false;}handler.removeCallbacks(periodic);handler.removeCallbacks(expireBadges);if(categoryMenu!=null)categoryMenu.dismiss();}
    private void repositoryRefresh(){repository.refresh(false,null);handler.postDelayed(periodic,TrailerRepository.SIX_HOURS);}
    void enter(){visible=true;resume();post(this::restoreFocus);}
    void leave(){visible=false;handler.removeCallbacks(expireBadges);saveSelection();}
    @Override public void changed(TrailerRepository.Snapshot snapshot){
        state=snapshot;String note=snapshot.message;if(weekly)note=note.replace("trailers","updates")+" · Last 14 days";if(snapshot.updated>0&&!snapshot.refreshing)note+=" · "+new java.text.SimpleDateFormat("d MMM, h:mm a",Locale.getDefault()).format(new Date(snapshot.updated));status.setText(note);
        refresh.setText(snapshot.configured?"Refresh":"Set up");
        ArrayList<Trailer> filtered=new ArrayList<>();for(Trailer trailer:snapshot.items)if(weekly?("tech".equals(trailer.category)&&TechRules.withinWindow(trailer.published,System.currentTimeMillis())&&("all".equals(category)||category.equals(trailer.topic))):category.equals(trailer.category))filtered.add(trailer);
        if(rendered&&sameItems(values,filtered)){updateBadges();updateImages();return;}
        boolean hadCardFocus=cards.stream().anyMatch(View::hasFocus);int previous=selectedIndex;ArrayList<Trailer> next=filtered;
        rendered=true;values=next;row.removeAllViews();cards.clear();
        for(Trailer t:values){Card card=new Card(t);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(352),dp(312));p.rightMargin=dp(24);row.addView(card,p);cards.add(card);}
        empty.setVisibility(cards.isEmpty()?VISIBLE:GONE);scroll.setVisibility(cards.isEmpty()?GONE:VISIBLE);
        empty.setText(weekly?(snapshot.refreshing?"Checking recent official launches and YC videos…":snapshot.configured?"No matching launches from these sources in the last 14 days.\nChoose another filter or Refresh.":"Connect YouTube to discover official tech launches."):snapshot.refreshing?"Finding new trailers from official channels…":snapshot.configured?"No trailers are cached yet.\nConnect to the internet and choose Refresh.":"New films. Official first looks.\nConnect YouTube once to keep this collection up to date.");
        selectedIndex=Math.min(previous,Math.max(0,cards.size()-1));for(int i=0;i<values.size();i++)if(values.get(i).id.equals(selectedId))selectedIndex=i;
        if(!values.isEmpty())selectedId=values.get(selectedIndex).id;
        if(hadCardFocus)savedScroll=Math.max(0,savedScroll+dp(376)*(selectedIndex-previous));
        updateBadges();
        if(visible&&(hadCardFocus||host.getCurrentFocus()==null||host.getCurrentFocus()==refresh))post(this::restoreFocus);else post(this::updateImages);
    }
    private static boolean sameItems(List<Trailer> a,List<Trailer> b){if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++){Trailer x=a.get(i),y=b.get(i);if(!x.id.equals(y.id)||!x.title.equals(y.title)||!x.channel.equals(y.channel)||x.published!=y.published||!x.thumbnails.equals(y.thumbnails))return false;}return true;}
    private void restoreFocus(){
        if(!visible)return;if(cards.isEmpty()){refresh.requestFocus();return;}
        selectedIndex=Math.max(0,Math.min(selectedIndex,cards.size()-1));Card card=cards.get(selectedIndex);int offset=savedScroll;restoring=true;card.requestFocus();restoring=false;
        scroll.post(()->{int target=offset;if(card.getRight()<target||card.getLeft()>target+dp(960))target=Math.max(0,card.getLeft()-dp(48));scroll.scrollTo(target,0);updateImages();});
    }
    private void updateBadges(){
        handler.removeCallbacks(expireBadges);if(!visible)return;long now=System.currentTimeMillis(),next=Long.MAX_VALUE;
        if(weekly&&state!=null&&cards.stream().anyMatch(card->!TechRules.withinWindow(card.trailer.published,now))){changed(state);return;}
        for(Card card:cards){card.newBadge.setVisibility(TrailerRules.isNew(card.trailer.published,now)?VISIBLE:GONE);long delay=TrailerRules.newExpiryDelay(card.trailer.published,now);if(delay>0)next=Math.min(next,delay);if(weekly){long expiry=TechRules.expiryDelay(card.trailer.published,now);if(expiry>0)next=Math.min(next,expiry);}}
        if(next!=Long.MAX_VALUE)handler.postDelayed(expireBadges,next);
    }
    private void updateImages(){
        if(!visible||getVisibility()!=VISIBLE)return;int left=scroll.getScrollX(),right=left+dp(960);
        for(Card card:cards){if(card.getRight()>=left-dp(376)&&card.getLeft()<=right+dp(376))images.load(card.trailer,card.image,()->card.fallback.setText("Thumbnail unavailable"));else{card.image.setTag(null);card.image.setImageDrawable(null);}}
    }
    boolean navigate(int key){
        View focus=host.getCurrentFocus();int current=cards.indexOf(focus);
        if(key==KeyEvent.KEYCODE_DPAD_UP){if(current>=0||focus==categoryButton||cards.isEmpty()){if(weekly)host.hideWeekly(true);else host.hideTrailers(true);}else restoreFocus();return true;}
        if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(current>=0)refresh.requestFocus();else if(focus==categoryButton)restoreFocus();else if(!weekly)host.showWeekly(true);return true;}
        if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){int direction=key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1;
            if(current>=0)cards.get(Math.max(0,Math.min(cards.size()-1,current+direction))).requestFocus();else if(direction>0){if(focus==refresh)sources.requestFocus();else categoryButton.requestFocus();}else{if(focus==categoryButton)sources.requestFocus();else refresh.requestFocus();}return true;}
        return false;
    }
    private String pref(String suffix){return weekly?"weekly"+suffix:"trailer"+suffix;}
    private void updateCategoryLabel(){boolean hollywood="hollywood".equals(category);heading.setText(weekly?"Updates from the internet":hollywood?"Latest Hollywood Trailers":"Latest Telugu Trailers");categoryButton.setText(weekly?("robotics".equals(category)?"Robotics":TechRules.label(category)):hollywood?"Hollywood":"Telugu trailers");categoryButton.setContentDescription((weekly?"Tech filter: ":"Trailer category: ")+categoryButton.getText()+". Open choices");}
    private void loadSelection(){selectedIndex=0;selectedId=host.prefs.getString(pref("Selection.")+category,!weekly&&"telugu".equals(category)?host.prefs.getString("trailerSelection",""):"");savedScroll=host.prefs.getInt(pref("Scroll.")+category,!weekly&&"telugu".equals(category)?host.prefs.getInt("trailerScroll",0):0);}
    private void chooseCategory(String choice){
        if(choice.equals(category)){restoreFocus();return;}saveSelection();View focused=host.getCurrentFocus();if(focused!=null)focused.clearFocus();category=choice;host.prefs.edit().putString(pref("Category"),category).apply();loadSelection();int offset=savedScroll;rendered=false;updateCategoryLabel();if(state!=null)changed(state);savedScroll=offset;post(this::restoreFocus);repository.refresh(false,null);
    }
    private void showCategories(){
        LinearLayout menu=new LinearLayout(host);menu.setOrientation(LinearLayout.VERTICAL);menu.setPadding(dp(8),dp(8),dp(8),dp(8));menu.setBackground(host.background(0xff151b20,0xff46504b,10));
        String[] labels=weekly?new String[]{"All updates","AI & models","Startups","Robotics & mobility","Space","Devices & tools"}:new String[]{"Hollywood","Telugu trailers"};
        String[] choices=weekly?new String[]{"all","ai","startups","robotics","space","devices"}:new String[]{"hollywood","telugu"};TextView[] options=new TextView[labels.length];
        categoryMenu=new PopupWindow(menu,dp(weekly?252:212),dp(16+48*labels.length),true);categoryMenu.setBackgroundDrawable(host.background(0xff151b20,0,10));categoryMenu.setOutsideTouchable(true);categoryMenu.setElevation(dp(8));
        for(int i=0;i<labels.length;i++){final String choice=choices[i];TextView option=host.control(labels[i]);options[i]=option;option.setTextSize(16);option.setContentDescription(labels[i]+(category.equals(choice)?", selected":""));menu.addView(option,new LinearLayout.LayoutParams(-1,dp(48)));option.setOnClickListener(v->{categoryMenu.dismiss();chooseCategory(choice);});final int index=i;option.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){options[Math.max(0,Math.min(options.length-1,index+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1)))].requestFocus();return true;}return key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT;});}
        categoryMenu.showAsDropDown(categoryButton,-dp(weekly?72:32),dp(8));options[Arrays.asList(choices).indexOf(category)].requestFocus();
    }
    private void saveSelection(){android.content.SharedPreferences.Editor edit=host.prefs.edit().putString(pref("Category"),category);if(!selectedId.isEmpty())edit.putString(pref("Selection.")+category,selectedId).putInt(pref("Scroll.")+category,savedScroll);edit.apply();}
    void save(Bundle bundle){bundle.putString(pref("Category"),category);bundle.putString(pref("Id"),selectedId);bundle.putInt(pref("Scroll"),savedScroll);}
    void restore(Bundle bundle){category=bundle.getString(pref("Category"),category);loadSelection();selectedId=bundle.getString(pref("Id"),selectedId);savedScroll=bundle.getInt(pref("Scroll"),savedScroll);updateCategoryLabel();}
    void trim(){images.trim();}
    void close(){pause();images.close();handler.removeCallbacksAndMessages(null);}
    private static String date(long value){return new java.text.SimpleDateFormat("d MMM yyyy",Locale.getDefault()).format(new Date(value));}
    private void open(Trailer trailer){
        if(opening||!TrailerRules.validVideoId(trailer.id))return;opening=true;saveSelection();handler.postDelayed(()->opening=false,1000);
        Intent primary=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/watch?v="+trailer.id)).setPackage("com.google.android.youtube.tv");
        try{host.startActivity(primary);return;}catch(ActivityNotFoundException|SecurityException ignored){}
        try{host.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("vnd.youtube:"+trailer.id)).setPackage("com.google.android.youtube.tv"));return;}catch(ActivityNotFoundException|SecurityException ignored){}
        new AlertDialog.Builder(host).setTitle("Video link unavailable").setMessage("YouTube TV could not open this exact video. Open YouTube and search for “"+trailer.title+"”.").setPositiveButton("Open YouTube",(d,w)->host.launchApp("com.google.android.youtube.tv")).setNegativeButton("Back",null).show();
    }
    void showSources(){
        String[] options={"YouTube API key","Refresh now","YouTube terms and privacy","Disconnect and clear video metadata"};
        new AlertDialog.Builder(host).setTitle(weekly?"Weekly tech sources":"Trailer sources").setItems(options,(d,i)->{
            if(i==0)setup();else if(i==1)repository.refresh(true,null);else if(i==2)showPrivacy();else new AlertDialog.Builder(host).setTitle("Disconnect YouTube?").setMessage("Remove the encrypted API key and cached trailer and tech metadata from Cinema. Your YouTube account and viewing history are unchanged.").setPositiveButton("Disconnect",(a,b)->repository.removeKey()).setNegativeButton("Keep connected",null).show();
        }).setNegativeButton("Back",null).show();
    }
    private void showPrivacy(){new AlertDialog.Builder(host).setTitle("YouTube trailers").setMessage("Cinema requests public video and channel metadata from YouTube. The key is encrypted locally with Android Keystore. No account login, viewing history, analytics, or backend is used. Opening a video sends that watch request to the installed YouTube app. Cached metadata is refreshed or expires within 30 days.\n\nYouTube Terms: https://www.youtube.com/t/terms\nGoogle Privacy: https://policies.google.com/privacy").setPositiveButton("OK",null).show();}
    void setup(){
        LinearLayout body=new LinearLayout(host);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(28),dp(12),dp(28),dp(8));
        TextView explanation=host.text("Use a dedicated key with YouTube Data API v3 enabled and API-restricted. Enter it with the encrypted Mac remote. It is stored only on this TV and is never included in screenshots, logs or the APK.",16,MainActivity.WHITE);body.addView(explanation);
        EditText input=new EditText(host);input.setTextSize(18);input.setTextColor(MainActivity.WHITE);input.setHintTextColor(MainActivity.MUTED);input.setHint("YouTube API key");input.setContentDescription("YouTube API key, private entry");input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setTransformationMethod(PasswordTransformationMethod.getInstance());input.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);input.setSaveEnabled(false);body.addView(input,new LinearLayout.LayoutParams(-1,dp(64)));
        CheckBox consent=new CheckBox(host);consent.setText("I agree to YouTube Terms and Cinema’s local privacy notice");consent.setTextColor(MainActivity.WHITE);consent.setTextSize(14);consent.setChecked(host.getSharedPreferences("trailer_credentials",0).getBoolean("consent",false));body.addView(consent);
        AlertDialog dialog=new AlertDialog.Builder(host).setTitle("Connect YouTube").setView(body).setPositiveButton("Connect",null).setNeutralButton("Terms / privacy",null).setNegativeButton("Back",null).create();
        dialog.setOnShowListener(d->{
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->showPrivacy());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String key=input.getText().toString().trim();if(!consent.isChecked()){consent.setError("Please review and agree to the terms");consent.requestFocus();return;}
                if(key.length()<30||key.length()>200||!key.matches("[A-Za-z0-9_-]+")){input.setError("Enter the dedicated API key");input.requestFocus();return;}
                repository.saveKey(key,null);input.getText().clear();dialog.dismiss();});input.requestFocus();
        });dialog.setOnDismissListener(d->input.getText().clear());dialog.show();
    }
}
