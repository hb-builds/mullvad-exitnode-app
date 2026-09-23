// SPDX-License-Identifier: GPL-3.0-only
// Android Views adaptation of Mullvad's SelectableRelayListItem / RelayListContent.
// Preserves country/city/server hierarchy, independent select/expand click targets,
// selection indication and highlighted search. See THIRD_PARTY_NOTICES.md.
package one.hbx.exitcontroller;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.function.Consumer;

final class RelayPicker {
    private final Context context;
    private final LinearLayout parent;
    private final Consumer<String> select;
    private final Set<String> expanded=new HashSet<>();
    private JSONArray relays=new JSONArray();
    private String selected="",query="";
    private boolean enabled;
    RelayPicker(Context context,LinearLayout parent,Consumer<String> select,String selected) {
        this.context=context;this.parent=parent;this.select=select;
        String[] p=selected.split("-");if(p.length>=2){expanded.add(p[0]);expanded.add(p[0]+"-"+p[1]);}
    }
    private int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    void update(JSONArray relays,String selected,String query,boolean enabled){
        String normalized=query.toLowerCase(Locale.ROOT).trim();
        if(this.relays==relays && this.selected.equals(selected) && this.query.equals(normalized) && this.enabled==enabled)return;
        this.relays=relays;this.selected=selected;this.query=normalized;this.enabled=enabled;render();
    }
    private boolean matches(String value){return value.toLowerCase(Locale.ROOT).contains(query);}
    private void render(){
        parent.removeAllViews();
        TreeMap<String,List<JSONObject>> countries=new TreeMap<>();
        for(int i=0;i<relays.length();i++){JSONObject r=relays.optJSONObject(i);if(r.optBoolean("active",true))countries.computeIfAbsent(r.optString("country_name"),k->new ArrayList<>()).add(r);}
        for(Map.Entry<String,List<JSONObject>> entry:countries.entrySet()){
            List<JSONObject> rows=entry.getValue();String cc=rows.get(0).optString("country_code");
            boolean countryMatches=matches(entry.getKey())||matches(cc);
            boolean any=countryMatches||rows.stream().anyMatch(r->matches(r.optString("city_name"))||matches(r.optString("hostname")));
            if(!any)continue;
            row(entry.getKey(),cc,0,true);
            if(!expanded.contains(cc)&&query.isEmpty())continue;
            TreeMap<String,List<JSONObject>> cities=new TreeMap<>();
            for(JSONObject r:rows)cities.computeIfAbsent(r.optString("city_name"),k->new ArrayList<>()).add(r);
            for(Map.Entry<String,List<JSONObject>> city:cities.entrySet()){
                String code=cc+"-"+city.getValue().get(0).optString("city_code");
                boolean cityMatches=countryMatches||matches(city.getKey())||city.getValue().stream().anyMatch(r->matches(r.optString("hostname")));
                if(!cityMatches)continue;
                row(city.getKey(),code,1,true);
                if(!expanded.contains(code)&&query.isEmpty())continue;
                city.getValue().sort(Comparator.comparing(r->r.optString("hostname")));
                for(JSONObject r:city.getValue())if(query.isEmpty()||countryMatches||matches(city.getKey())||matches(r.optString("hostname")))row(r.optString("hostname"),r.optString("hostname"),2,false);
            }
        }
        if(parent.getChildCount()==0){TextView t=new TextView(context);t.setText(relays.length()==0?"Connect Tailscale to load locations.":"No matching locations");t.setTextColor(0xFFCDD8E4);t.setPadding(dp(16),dp(24),dp(16),dp(24));parent.addView(t);}
    }
    private void row(String title,String code,int level,boolean canExpand){
        boolean active=selected.equals(code)||selected.startsWith(code+"-");
        LinearLayout row=new LinearLayout(context);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(active?0xFF326B43:level==0?0xFF294D73:level==1?0xFF244565:0xFF203B57);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(56));rp.topMargin=dp(1);parent.addView(row,rp);
        TextView name=new TextView(context);name.setTextSize(16);name.setTextColor(0xFFFFFFFF);name.setGravity(Gravity.CENTER_VERTICAL);
        String label=(active?"✓  ":"    ")+title;
        SpannableString styled=new SpannableString(label);int start=query.isEmpty()?-1:label.toLowerCase(Locale.ROOT).indexOf(query);
        if(start>=0){styled.setSpan(new ForegroundColorSpan(0xFFFFD524),start,start+query.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);styled.setSpan(new StyleSpan(Typeface.BOLD),start,start+query.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        name.setText(styled);name.setPadding(dp(12+level*16),0,dp(8),0);name.setContentDescription("Use "+title);name.setEnabled(enabled);name.setAlpha(enabled?1f:.5f);name.setBackgroundResource(android.R.drawable.list_selector_background);
        row.addView(name,new LinearLayout.LayoutParams(0,-1,1));name.setOnClickListener(v->select.accept(code));
        if(canExpand){
            TextView arrow=new TextView(context);boolean open=expanded.contains(code)||!query.isEmpty();arrow.setText(open?"⌃":"⌄");arrow.setTextSize(24);arrow.setTextColor(0xFFFFFFFF);arrow.setGravity(Gravity.CENTER);arrow.setContentDescription((open?"Collapse ":"Expand ")+title);arrow.setBackgroundResource(android.R.drawable.list_selector_background);
            row.addView(arrow,new LinearLayout.LayoutParams(dp(56),-1));arrow.setOnClickListener(v->{if(expanded.contains(code))expanded.remove(code);else expanded.add(code);render();});
        }
    }
}
