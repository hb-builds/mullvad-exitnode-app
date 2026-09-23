// SPDX-License-Identifier: GPL-3.0-only
package one.hbx.exitcontroller;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.*;
import android.os.*;
import org.json.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class LocationNotification {
    private static final String CHANNEL="exit_location";
    private static final int ID=41;
    static boolean enabled(Context c){return GatewayClient.preferences(c).getBoolean("monitor_enabled",true) && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;}
    static void channel(Context c){
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Exit node location",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Last checked exit location; updated only while the app is open");channel.setShowBadge(false);
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    static String locationName(JSONObject state,JSONArray locations){
        String selected=state==null?"":state.optString("profile","");
        for(int i=0;i<locations.length();i++){JSONObject r=locations.optJSONObject(i);if(selected.equals(r.optString("hostname")))return r.optString("city_name")+", "+r.optString("country_name");}
        return selected.isEmpty()?"Location not checked yet":selected;
    }
    static void publish(Context c,JSONObject state,JSONArray locations,boolean online){
        SharedPreferences.Editor editor=GatewayClient.preferences(c).edit();
        if(state!=null)editor.putString("monitor_state",state.toString());
        if(locations.length()>0)editor.putString("monitor_locations",locations.toString());
        editor.putBoolean("monitor_online",online);
        if(online)editor.putLong("monitor_checked",System.currentTimeMillis());
        editor.apply();
        if(enabled(c)){channel(c);c.getSystemService(NotificationManager.class).notify(ID,notification(c,false));}
    }
    static Notification notification(Context c,boolean checking){
        SharedPreferences prefs=GatewayClient.preferences(c);JSONObject state=new JSONObject();JSONArray locations=new JSONArray();
        try{state=new JSONObject(prefs.getString("monitor_state","{}"));locations=new JSONArray(prefs.getString("monitor_locations","[]"));}catch(JSONException ignored){}
        long checked=prefs.getLong("monitor_checked",0);
        boolean online=prefs.getBoolean("monitor_online",false) && System.currentTimeMillis()-checked<150000;
        String location=locationName(state,locations);
        String headline=location;
        JSONObject job=state.optJSONObject("job");
        String detail;
        if(checking)detail="Last known location · open app to refresh";
        else if(!online)detail="Last check: gateway unreachable · connect Tailscale";
        else if(job!=null && "running".equals(job.optString("status")))detail="Applying gateway change…";
        else if(!state.optBoolean("tunnel_active"))detail="Tunnel stopped · internet forwarding blocked";
        else if(state.isNull("handshake_age") || state.optLong("handshake_age",999)>180)detail="Gateway reachable · tunnel needs checking";
        else detail="Last checked · "+state.optString("profile")+" · DNS filters "+(state.optInt("dns_mask")==0?"off":"on");
        Intent open=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.small_logo_white)
            .setContentTitle(headline).setContentText(detail).setStyle(new Notification.BigTextStyle().bigText(detail))
            .setContentIntent(pending).setCategory(Notification.CATEGORY_STATUS).setOngoing(true)
            .setOnlyAlertOnce(true).setShowWhen(checked>0).setWhen(checked)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setColor(0xFF44AD4D).build();
    }
}
