// SPDX-License-Identifier: GPL-3.0-only
package one.hbx.exitcontroller;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.security.keystore.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int BG=0xFF0B1728, INK=0xFFF5F7FA, MUTED=0xFFBBCADD, GREEN=0xFF44AD4D, WHITE=0xFFFFFFFF, SURFACE=0xFF294D73;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Map<String, JSONObject> countries=new TreeMap<>();
    private JSONObject pairing, state;
    private JSONArray locations=new JSONArray();
    private GatewayClient client;
    private LinearLayout root, countryRows;
    private TextView connection, place, profile, status;
    private Switch[] filters=new Switch[3];
    private Button refreshButton, chooseButton;
    private Switch dnsMaster, notificationSwitch;
    private ControlPager pager;
    private Runnable arrangeMain;
    private Button[] tabs=new Button[3];
    private int selectedPage=0;
    private boolean wide=false;
    private EditText search;
    private Dialog pickerDialog;
    private RelayPicker relayPicker;
    private one.hbx.exitcontroller.map.GlobeView globe;
    private boolean loading=false, busy=false, pendingMutation=false, updating=false, foreground=false, online=false;
    private String error="", announcedJob="";
    private long lastChecked=0;
    private long pairingGeneration=0;
    private final Runnable poll=() -> refresh(false);

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if(saved!=null)selectedPage=saved.getInt("page",0);
        try { loadPairing(); } catch(Exception e) { pairing=null; }
        if(pairing==null) showPairing(); else showMain();
    }
    @Override protected void onResume() { super.onResume(); foreground=true; if(globe!=null) globe.onResume(); if(pairing!=null) { refresh(true); ensureNotification(false); } }
    @Override protected void onPause() { foreground=false; if(globe!=null) globe.onPause(); handler.removeCallbacks(poll); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("page",pager==null?selectedPage:pager.selected());}
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null);if(pickerDialog!=null)pickerDialog.dismiss();executor.shutdownNow();super.onDestroy(); }

    private int dp(float n) { return (int)(n*getResources().getDisplayMetrics().density+.5f); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String s,int size,int color,boolean bold) {
        TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color);
        if(bold) v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        v.setLineSpacing(dp(3),1); return v;
    }
    private void space(LinearLayout parent,int n) { View v=new View(this); parent.addView(v,new LinearLayout.LayoutParams(1,dp(n))); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private void base() {
        FrameLayout frame=new FrameLayout(this); frame.setBackgroundColor(BG);
        frame.setOnApplyWindowInsetsListener((view,insets) -> {
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            int bottom=Math.max(bars.bottom,insets.getInsets(WindowInsets.Type.ime()).bottom);
            view.setPadding(bars.left,bars.top,bars.right,bottom); return insets;
        });
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); frame.addView(scroll);
        LinearLayout outer=column(); outer.setGravity(Gravity.CENTER_HORIZONTAL); scroll.addView(outer);
        root=column(); root.setPadding(dp(24),dp(24),dp(24),dp(32));
        int width=Math.min(getResources().getDisplayMetrics().widthPixels,dp(680));
        outer.addView(root,new LinearLayout.LayoutParams(width,ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(frame);
        root.addView(text("Mullvad Exit Node",30,INK,true)); space(root,6);
        root.addView(text("Your private gateway over Tailscale",15,MUTED,false)); space(root,16);
    }
    private Button button(String label, int background, int color) {
        Button b=new Button(this); b.setText(label); b.setTextColor(color); b.setTextSize(15); b.setAllCaps(false);
        b.setGravity(Gravity.CENTER); b.setBackground(shape(background,14)); b.setMinHeight(dp(52));
        b.setPadding(dp(16),dp(10),dp(16),dp(10)); return b;
    }
    private LinearLayout card(int color) {
        LinearLayout c=column(); c.setPadding(dp(20),dp(20),dp(20),dp(20)); c.setBackground(shape(color,22));
        root.addView(c,new LinearLayout.LayoutParams(-1,-2)); return c;
    }
    private void showPairing() {
        base(); LinearLayout c=card(BG);
        c.addView(text("A remote for your exit node",23,WHITE,true)); space(c,14);
        c.addView(text("Connect the official Tailscale app, then import the pairing file from your VM. Your VPN stays in Tailscale.",16,0xFFD9E3E4,false));
        space(c,22); Button pair=button("Import pairing file",GREEN,WHITE); c.addView(pair);
        pair.setOnClickListener(v -> importPairing());
        space(root,22); root.addView(text("Only your paired phone can change the gateway. No Mullvad account number or WireGuard key is stored in this app.",14,MUTED,false));
    }
    private void importPairing() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent,100);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=100 || result!=RESULT_OK || data==null || data.getData()==null) return;
        try(InputStream in=getContentResolver().openInputStream(data.getData())) {
            byte[] bytes=in.readNBytes(16385); if(bytes.length>16384) throw new IOException();
            JSONObject p=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            GatewayClient next=new GatewayClient(this,p);
            handler.removeCallbacks(poll);pairingGeneration++;client=next;pairing=p;
            state=null;locations=new JSONArray();online=false;loading=false;busy=false;pendingMutation=false;error="";announcedJob="";lastChecked=0;
            getPreferences(0).edit().remove("monitor_state").remove("monitor_locations").remove("monitor_online").remove("monitor_checked").remove("last_dns_mask").apply();
            showMain(); refresh(true); ensureNotification(false);
            Toast.makeText(this,"Paired. You can delete the imported file.",Toast.LENGTH_LONG).show();
        } catch(Exception e) { new AlertDialog.Builder(this).setMessage("Could not import this pairing file. Choose the JSON file generated for this exit node.").setPositiveButton("OK",null).show(); }
    }
    private void loadPairing() throws Exception { client=new GatewayClient(this); pairing=client.pairing; }
    private String gatewayName(){return pairing==null?"Exit node":pairing.optString("name","Exit node");}
    private String failure(Exception e) {
        if(e instanceof SSLException) return "Gateway identity could not be verified. Re-import its pairing file if its certificate changed.";
        if(e instanceof IOException && e.getMessage()!=null && (e.getMessage().startsWith("Connect ") || e.getMessage().startsWith("Pairing ") || e.getMessage().startsWith("This Tailscale") || e.getMessage().startsWith("Another ") || e.getMessage().startsWith("Gateway request"))) return e.getMessage();
        return "Cannot reach the gateway. Check that Tailscale is connected and this app is included in its VPN.";
    }
    private void showMain() {
        wide=getResources().getConfiguration().screenWidthDp>=700;
        FrameLayout canvas=new FrameLayout(this);canvas.setBackgroundColor(BG);
        FrameLayout stage=new FrameLayout(this);canvas.addView(stage,new FrameLayout.LayoutParams(-1,-1));
        canvas.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        globe=new one.hbx.exitcontroller.map.GlobeView(this);
        stage.addView(globe,new FrameLayout.LayoutParams(-1,-1));
        globe.setOnCitySelected(code->{if(!online||busy)return;new AlertDialog.Builder(this).setTitle("Switch exit location?").setMessage(cityTitle(code)).setPositiveButton("Use this city",(d,w)->change("location",code)).setNegativeButton("Cancel",null).show();});

        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPadding(dp(20),dp(8),dp(20),dp(8));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.logo_icon);logo.setContentDescription("Mullvad logo");heading.addView(logo,new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout titles=column();LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(0,-2,1);titleParams.leftMargin=dp(12);heading.addView(titles,titleParams);
        titles.addView(text("Mullvad Exit Node",wide?25:22,WHITE,true));
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-1,dp(72),Gravity.TOP);stage.addView(heading,hp);

        ImageButton recenter=new ImageButton(this);recenter.setImageResource(R.drawable.ic_recenter);recenter.setScaleType(ImageView.ScaleType.CENTER_INSIDE);recenter.setPadding(dp(12),dp(12),dp(12),dp(12));
        GradientDrawable recenterShape=shape(0xE61C3047,12);recenterShape.setStroke(dp(1),0xFF3E5570);recenter.setBackground(recenterShape);
        recenter.setContentDescription("Recenter globe");recenter.setTooltipText("Recenter globe");recenter.setOnClickListener(v->globe.recenter());
        FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.LEFT|Gravity.BOTTOM);rp.leftMargin=dp(24);stage.addView(recenter,rp);

        LinearLayout controls=column();GradientDrawable cardShape=shape(0xFF1C3047,26);cardShape.setStroke(dp(1),0xFF3E5570);controls.setBackground(cardShape);controls.setClipToOutline(true);controls.setElevation(dp(16));
        LinearLayout navigation=new LinearLayout(this);navigation.setPadding(dp(12),dp(12),dp(12),dp(10));navigation.setGravity(Gravity.CENTER);controls.addView(navigation,new LinearLayout.LayoutParams(-1,dp(66)));
        String[] names={"Location","DNS","Tools"};
        for(int i=0;i<3;i++){final int page=i;tabs[i]=button(names[i],0x001C3047,MUTED);tabs[i].setTextSize(14);tabs[i].setMinHeight(0);tabs[i].setPadding(dp(4),dp(8),dp(4),dp(8));tabs[i].setElevation(0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(3),0,dp(3),0);navigation.addView(tabs[i],p);tabs[i].setOnClickListener(v->pager.select(page));}
        pager=new ControlPager(this);controls.addView(pager,new LinearLayout.LayoutParams(-1,0,1));pager.onPageChanged(page->{selectedPage=page;for(int i=0;i<3;i++){tabs[i].setBackground(shape(i==page?0xFF385570:0x001C3047,12));tabs[i].setTextColor(i==page?WHITE:MUTED);tabs[i].setSelected(i==page);}if(arrangeMain!=null)pager.post(arrangeMain);});

        LinearLayout location=pageContent();
        connection=text("●  CONNECTING",11,0xFF95DBAA,true);location.addView(connection);space(location,12);
        place=text("Your exit node",wide?29:27,WHITE,true);location.addView(place);space(location,5);
        profile=text(gatewayName(),12,MUTED,false);location.addView(profile);
        status=text("Reading gateway status…",14,0xFFD4DFEA,false);status.setPadding(0,dp(12),0,0);location.addView(status);space(location,18);
        chooseButton=button("Switch location",GREEN,WHITE);location.addView(chooseButton,new LinearLayout.LayoutParams(-1,dp(52)));chooseButton.setOnClickListener(v->showLocations());
        pager.addPage(scrollPage(location));

        LinearLayout dns=pageContent();
        dnsMaster=toggle("Content blocking");dns.addView(dnsMaster);dnsMaster.setOnCheckedChangeListener((v,on)->{if(!updating){int remembered=getPreferences(0).getInt("last_dns_mask",7);change("dns",on?remembered:0);}});
        View divider=new View(this);divider.setBackgroundColor(0xFF3A5068);LinearLayout.LayoutParams dividerParams=new LinearLayout.LayoutParams(-1,dp(1));dividerParams.setMargins(0,dp(8),0,dp(8));dns.addView(divider,dividerParams);
        String[] labels={"Ads","Trackers","Malware"};
        for(int i=0;i<3;i++){Switch sw=toggle(labels[i]);filters[i]=sw;dns.addView(sw);sw.setOnCheckedChangeListener((v,on)->{if(!updating){int mask=0;for(int j=0;j<3;j++)if(filters[j].isChecked())mask|=1<<j;change("dns",mask);}});}
        pager.addPage(scrollPage(dns));

        LinearLayout tools=pageContent();
        refreshButton=button("Refresh status",SURFACE,WHITE);tools.addView(refreshButton,new LinearLayout.LayoutParams(-1,dp(52)));refreshButton.setOnClickListener(v->refresh(true));space(tools,10);
        Button check=button("Check connection on Vanadium",GREEN,WHITE);check.setTextSize(14);tools.addView(check,new LinearLayout.LayoutParams(-1,dp(56)));check.setOnClickListener(v->openConnectionCheck());space(tools,10);
        Button pair=button("Pairing settings",SURFACE,WHITE);tools.addView(pair,new LinearLayout.LayoutParams(-1,dp(52)));pair.setOnClickListener(v->showPairingSettings());space(tools,16);
        notificationSwitch=toggle("Location notification");notificationSwitch.setChecked(LocationNotification.enabled(this));tools.addView(notificationSwitch);
        notificationSwitch.setOnCheckedChangeListener((v,on)->{if(updating)return;getPreferences(0).edit().putBoolean("monitor_enabled",on).apply();if(on)ensureNotification(true);else{getSystemService(NotificationManager.class).cancel(41);}});
        pager.addPage(scrollPage(tools));

        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(370),Gravity.BOTTOM);cp.setMargins(dp(14),0,dp(14),dp(14));stage.addView(controls,cp);
        arrangeMain=()->{
            int w=stage.getWidth(),h=stage.getHeight();if(w==0||h==0)return;
            FrameLayout.LayoutParams cardParams=(FrameLayout.LayoutParams)controls.getLayoutParams();
            FrameLayout.LayoutParams mapParams=(FrameLayout.LayoutParams)globe.getLayoutParams();
            FrameLayout.LayoutParams recenterParams=(FrameLayout.LayoutParams)recenter.getLayoutParams();
            if(wide){int cw=Math.min(dp(420),Math.round(w*.43f));cardParams.width=cw;cardParams.height=Math.min(dp(66)+pager.contentHeight(selectedPage,cw),h-dp(104));cardParams.gravity=Gravity.RIGHT|Gravity.BOTTOM;cardParams.setMargins(0,dp(76),dp(22),dp(24));mapParams.width=Math.round(w*.78f);mapParams.height=h-dp(72);mapParams.topMargin=dp(72);}
            else{cardParams.width=w-dp(28);cardParams.height=Math.min(dp(66)+pager.contentHeight(selectedPage,cardParams.width),Math.min(dp(430),Math.round(h*.46f)));cardParams.gravity=Gravity.BOTTOM;cardParams.setMargins(dp(14),0,dp(14),dp(12));mapParams.width=w;mapParams.height=Math.round(h*.74f);mapParams.topMargin=dp(38);}
            recenterParams.bottomMargin=wide?dp(32):cardParams.height+dp(26);
            controls.setLayoutParams(cardParams);globe.setLayoutParams(mapParams);recenter.setLayoutParams(recenterParams);
        };
        stage.addOnLayoutChangeListener((v,left,top,right,bottom,ol,ot,or,ob)->{if(right-left!=or-ol || bottom-top!=ob-ot)arrangeMain.run();});
        setContentView(canvas);pager.post(()->pager.select(selectedPage));updateState();
    }
    private LinearLayout pageContent(){LinearLayout page=column();page.setPadding(dp(22),dp(12),dp(22),dp(22));return page;}
    private ScrollView scrollPage(LinearLayout page){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.addView(page);return scroll;}
    private Switch toggle(String title){Switch s=new Switch(this);s.setText(title);s.setTextSize(16);s.setTextColor(WHITE);s.setMinHeight(dp(50));s.setShowText(false);return s;}
    private void openConnectionCheck(){
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse("https://mullvad.net/check")).setPackage("app.vanadium.browser");
        try{startActivity(intent);}catch(ActivityNotFoundException e){new AlertDialog.Builder(this).setMessage("Vanadium is not available in this Android profile.").setPositiveButton("OK",null).show();}
    }
    private void showPairingSettings(){new AlertDialog.Builder(this).setTitle("Gateway pairing").setMessage(gatewayName()).setPositiveButton("Import new file",(d,w)->importPairing()).setNeutralButton("Licenses",(d,w)->showLicenses()).setNegativeButton("Close",null).show();}
    private void ensureNotification(boolean fromToggle){
        if(pairing==null || !getPreferences(0).getBoolean("monitor_enabled",true))return;
        if(checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            if(fromToggle || !getPreferences(0).getBoolean("notification_asked",false)){getPreferences(0).edit().putBoolean("notification_asked",true).apply();requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},42);}
            return;
        }
        if(state!=null)LocationNotification.publish(this,state,locations,online);
        else{LocationNotification.channel(this);getSystemService(NotificationManager.class).notify(41,LocationNotification.notification(this,true));}
        if(notificationSwitch!=null){updating=true;notificationSwitch.setChecked(true);updating=false;}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==42){if(grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)ensureNotification(false);else if(notificationSwitch!=null){updating=true;notificationSwitch.setChecked(false);updating=false;}}}
    private void showLicenses() {
        new AlertDialog.Builder(this).setTitle("Open-source licenses")
            .setItems(new String[]{"Mullvad renderer & controller · GPLv3","Kotlin runtime · Apache 2.0"},(dialog,index)->{
                try(InputStream in=getResources().openRawResource(index==0?R.raw.gpl_license:R.raw.kotlin_license)){
                    TextView content=text(new String(in.readAllBytes(),StandardCharsets.UTF_8),13,WHITE,false);content.setPadding(dp(20),dp(12),dp(20),dp(12));
                    ScrollView scroll=new ScrollView(this);scroll.addView(content);
                    new AlertDialog.Builder(this).setTitle(index==0?"GNU GPL version 3":"Apache License 2.0").setView(scroll).setPositiveButton("Close",null).show();
                }catch(IOException ignored){}
            }).setNegativeButton("Close",null).show();
    }
    private void refresh(boolean manual) {
        if(pairing==null || loading || !foreground) return;
        loading=true; if(refreshButton!=null) refreshButton.setEnabled(false);
        final GatewayClient requestClient=client;final long generation=pairingGeneration;final JSONArray cachedLocations=locations;
        executor.execute(() -> {
            try {
                JSONArray list=cachedLocations.length()==0?requestClient.request("/v1/locations",null).getJSONArray("locations"):cachedLocations;
                JSONObject next=requestClient.request("/v1/state",null);
                runOnUiThread(() -> { if(isDestroyed() || generation!=pairingGeneration)return;locations=list; state=next; lastChecked=System.currentTimeMillis(); online=true; error=""; loading=false; updateState(); LocationNotification.publish(this,state,locations,online); schedule(); });
            } catch(Exception e) {
                String message=failure(e);
                runOnUiThread(() -> { if(isDestroyed() || generation!=pairingGeneration)return;online=false; error=message; loading=false; updateState(); LocationNotification.publish(this,state,locations,online); schedule(); });
            }
        });
    }
    private void schedule() { handler.removeCallbacks(poll); if(foreground) handler.postDelayed(poll,busy?1800:15000); }
    private void change(String kind,Object value) {
        if(busy || !online) { updateState(); return; }
        pendingMutation=true; busy=true; handler.removeCallbacks(poll); error=""; connection.setText("●  Applying change"); status.setText(kind.equals("location")?"Switching location…":"Applying DNS filters…"); status.setVisibility(View.VISIBLE);if(arrangeMain!=null)pager.post(arrangeMain);setEnabled(false);
        final GatewayClient requestClient=client;final long generation=pairingGeneration;
        executor.execute(() -> {
            try {
                JSONObject data=new JSONObject().put("request_id",UUID.randomUUID().toString()).put("kind",kind).put("value",value);
                requestClient.request("/v1/change",data);
                runOnUiThread(() -> { if(isDestroyed() || generation!=pairingGeneration)return;pendingMutation=false;busy=false;if(foreground)handler.postDelayed(poll,300); });
            } catch(Exception e) {
                String message=failure(e)+" The change may still be running; refresh to check.";
                runOnUiThread(() -> { if(isDestroyed() || generation!=pairingGeneration)return;pendingMutation=false; busy=false; online=false; error=message; updateState(); schedule(); });
            }
        });
    }
    private void setEnabled(boolean enabled) {
        for(Switch s:filters) if(s!=null) s.setEnabled(enabled);
        if(countryRows!=null) for(int i=0;i<countryRows.getChildCount();i++) countryRows.getChildAt(i).setEnabled(enabled);
        if(dnsMaster!=null) dnsMaster.setEnabled(enabled);
        if(chooseButton!=null) chooseButton.setEnabled(enabled);
        if(refreshButton!=null) refreshButton.setEnabled(!loading);
    }
    private void updateState() {
        if(connection==null) return;
        JSONObject job=state==null?null:state.optJSONObject("job"); busy=pendingMutation || (job!=null && "running".equals(job.optString("status")));
        String selected=state==null?"":state.optString("profile","");
        String country="",city="";
        for(int i=0;i<locations.length();i++) { JSONObject row=locations.optJSONObject(i); if(selected.equals(row.optString("hostname"))) {country=row.optString("country_name"); city=row.optString("city_name"); break;} }
        place.setText(city.isEmpty()?"Your exit node":city+", "+country);
        profile.setText(selected.isEmpty()?gatewayName():selected+"  ·  "+gatewayName());
        boolean tunnel=state!=null && state.optBoolean("tunnel_active");
        long age=state==null || state.isNull("handshake_age")?-1:state.optLong("handshake_age");
        String connectionLabel=!online?(state==null && loading?"Connecting…":"Gateway unreachable"):busy?"Applying change":!tunnel?"Tunnel stopped":age<0 || age>180?"Checking tunnel":"Gateway connected";
        String updated=lastChecked==0?"":" · Updated "+android.text.format.DateFormat.getTimeFormat(this).format(new Date(lastChecked));
        connection.setText("●  "+connectionLabel+updated);
        if(!error.isEmpty()) status.setText(error);
        else if(busy) status.setText(job==null || job.optString("kind").equals("location")?"Switching location…":"Applying DNS filters…");
        else if(job!=null && "failed".equals(job.optString("status"))) status.setText(job.optString("message"));
        else if(state==null) status.setText("Waiting to connect through Tailscale…");
        else if(!tunnel) status.setText("Internet forwarding is blocked until the tunnel recovers.");
        else if(age<0 || age>180) status.setText("No recent WireGuard handshake. Refresh to check recovery.");
        else status.setText("");
        status.setVisibility(status.getText().length()==0?View.GONE:View.VISIBLE);
        updating=true; int mask=state==null?0:state.optInt("dns_mask");
        for(int i=0;i<3;i++) filters[i].setChecked((mask & (1<<i))!=0);
        if(dnsMaster!=null)dnsMaster.setChecked(mask!=0);
        if(mask!=0)getPreferences(0).edit().putInt("last_dns_mask",mask).apply();
        updating=false;
        if(job!=null && "done".equals(job.optString("status")) && !announcedJob.equals(job.optString("request_id"))) {
            announcedJob=job.optString("request_id"); Toast.makeText(this,job.optString("message"),Toast.LENGTH_SHORT).show();
        }
        if(globe!=null && foreground) globe.updateLocations(locations,selected);
        renderCountries(); setEnabled(online && !busy);
        if(arrangeMain!=null)pager.post(arrangeMain);
    }
    private String cityTitle(String code) {
        for(int i=0;i<locations.length();i++){JSONObject r=locations.optJSONObject(i);if(code.equals(r.optString("country_code")+"-"+r.optString("city_code")))return r.optString("city_name")+", "+r.optString("country_name");}
        return code;
    }
    private void showLocations() {
        pickerDialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar);
        LinearLayout page=column();page.setBackgroundColor(BG);
        page.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,Math.max(b.bottom,insets.getInsets(WindowInsets.Type.ime()).bottom));return insets;});
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPadding(dp(12),dp(12),dp(20),dp(12));
        Button back=button("‹",BG,WHITE);back.setContentDescription("Back to map");heading.addView(back,new LinearLayout.LayoutParams(dp(52),dp(52)));back.setOnClickListener(v->pickerDialog.dismiss());
        TextView title=text("Select location",24,WHITE,true);heading.addView(title);page.addView(heading);
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(16);search.setTextColor(WHITE);search.setHint("Search for a country, city or server");search.setHintTextColor(MUTED);search.setBackground(shape(SURFACE,6));search.setPadding(dp(16),0,dp(16),0);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(52));sp.setMargins(dp(20),0,dp(20),dp(16));page.addView(search,sp);
        ScrollView scroll=new ScrollView(this);countryRows=column();scroll.addView(countryRows);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        String selected=state==null?"":state.optString("profile","");
        relayPicker=new RelayPicker(this,countryRows,code->{pickerDialog.dismiss();change("location",code);},selected);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){} public void onTextChanged(CharSequence s,int a,int b,int c){renderCountries();}public void afterTextChanged(Editable e){}});
        pickerDialog.setContentView(page);pickerDialog.setOnDismissListener(d->{countryRows=null;relayPicker=null;search=null;});
        pickerDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        pickerDialog.show();pickerDialog.getWindow().setLayout(wide?dp(520):-1,wide?Math.min(dp(720),getResources().getDisplayMetrics().heightPixels-dp(100)):-1);renderCountries();
    }
    private void renderCountries() {
        if(relayPicker!=null && search!=null)relayPicker.update(locations,state==null?"":state.optString("profile",""),search.getText().toString(),online&&!busy);
    }
}
