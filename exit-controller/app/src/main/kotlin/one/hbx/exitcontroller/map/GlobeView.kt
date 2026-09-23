// SPDX-License-Identifier: GPL-3.0-only
// Android View adapter around Mullvad's actual OpenGL globe renderer.
package one.hbx.exitcontroller.map

import android.animation.ValueAnimator
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.AccelerateDecelerateInterpolator
import net.mullvad.mullvadvpn.lib.map.data.*
import net.mullvad.mullvadvpn.lib.map.internal.MapRenderer
import net.mullvad.mullvadvpn.lib.model.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class GlobeView(context:Context):GLSurfaceView(context) {
    fun interface OnCitySelected { fun onSelect(code:String) }
    var onCitySelected:OnCitySelected?=null
    private val renderer=MapRenderer(resources)
    private var latitude=25f
    private var longitude=0f
    private var zoom=2.05f
    private var homeZoom=2.05f
    private var selected=""
    private var animation:ValueAnimator?=null
    private val coordinates=resources.openRawResource(one.hbx.exitcontroller.R.raw.city_coordinates).use { JSONObject(it.bufferedReader().readText()) }
    private var available=listOf<String>()
    private var markers=listOf<Marker>()
    private var positions=mapOf<LatLong,String>()
    private val colors=GlobeColors(Color(0xFF527F9A.toInt()),Color(0xFF234C68.toInt()),Color(0xFF15344B.toInt()),Color(0xFF0B1728.toInt()))
    private fun viewState()=GlobeViewState(CameraPosition(LatLong(latitude,longitude),zoom),markers=markers,globeColors=colors)
    private fun render() { val s=viewState(); queueEvent { renderer.setViewState(s) }; requestRender() }
    private fun coordinate(code:String):LatLong? {
        val p=coordinates.optJSONObject(code)?:return null
        return LatLong(p.getDouble("latitude").toFloat(),p.getDouble("longitude").toFloat())
    }
    fun updateLocations(locations:JSONArray, profile:String) {
        val codes=linkedSetOf<String>()
        for(i in 0 until locations.length()) {
            val r=locations.getJSONObject(i)
            if(r.optBoolean("active",true)) codes.add(r.getString("country_code")+"-"+r.getString("city_code"))
        }
        val city=profile.substringBefore("-wg-")
        val changed=city!=selected
        if(available==codes.toList() && !changed)return
        available=codes.toList();selected=city
        val markerList=mutableListOf<Marker>();val lookup=mutableMapOf<LatLong,String>()
        for(code in available) coordinate(code)?.let { p ->
            lookup[p]=code
            markerList.add(Marker(p,if(code==city) .15f else .07f,if(code==city) LocationMarkerColors.hop() else LocationMarkerColors.default()))
        }
        markers=markerList;positions=lookup
        if(changed) coordinate(city)?.let { focus(it) } ?: render() else render()
        contentDescription="Mullvad globe, selected exit $city. Drag to rotate, pinch to zoom, double-tap to recenter, or tap a city marker. The location list provides the same choices."
    }
    private fun focus(target:LatLong) {
        animation?.cancel()
        val fromLat=latitude;val fromLon=longitude
        val delta=((target.longitude.value-fromLon+540f)%360f)-180f
        animation=ValueAnimator.ofFloat(0f,1f).apply {
            duration=1400;interpolator=AccelerateDecelerateInterpolator()
            addUpdateListener { latitude=fromLat+(target.latitude.value-fromLat)*(it.animatedValue as Float);longitude=Longitude.unwind(fromLon+delta*(it.animatedValue as Float));render() }
            start()
        }
    }
    fun recenter() { coordinate(selected)?.let { zoom=homeZoom;focus(it) } }
    private val scale=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(detector:ScaleGestureDetector):Boolean { animation?.cancel();zoom=(zoom+(1-detector.scaleFactor)*.5f).coerceIn(1.3f,5.5f);render();return true }
    })
    private val gestures=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){
        override fun onDown(e:MotionEvent):Boolean { animation?.cancel();return true }
        override fun onScroll(e1:MotionEvent?,e2:MotionEvent,dx:Float,dy:Float):Boolean {
            if(!scale.isInProgress) {
                // Adapted from Mullvad InteractiveMap's angular pan: cast both positions onto the globe.
                val from=renderer.calculateIntersection(Offset(e2.x+dx,e2.y+dy),width,height)?.toLatLong()
                val to=renderer.calculateIntersection(Offset(e2.x,e2.y),width,height)?.toLatLong()
                if(from!=null && to!=null) {
                    latitude=(latitude+from.latitude.value-to.latitude.value).coerceIn(-85f,85f)
                    longitude=Longitude.unwind(longitude+from.longitude.value-to.longitude.value)
                } else { latitude=(latitude+dy/height*90f).coerceIn(-85f,85f);longitude=Longitude.unwind(longitude+dx/width*180f) }
                render()
            }
            return true
        }
        override fun onSingleTapConfirmed(e:MotionEvent):Boolean {
            val hit=renderer.closestMarker(Offset(e.x,e.y),width,height)
            if(hit!=null && hit.second<.07f) hit.first?.let { positions[it.latLong]?.let { code->onCitySelected?.onSelect(code) } }
            performClick();return true
        }
        override fun onDoubleTap(e:MotionEvent):Boolean { recenter();return true }
    })
    init {
        setEGLContextClientVersion(2);setEGLConfigChooser(8,8,8,8,24,0)
        renderer.setViewState(viewState());setRenderer(renderer);renderMode=RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause=true;isClickable=true
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        parent?.requestDisallowInterceptTouchEvent(event.actionMasked!=MotionEvent.ACTION_UP && event.actionMasked!=MotionEvent.ACTION_CANCEL)
        scale.onTouchEvent(event);gestures.onTouchEvent(event);return true
    }
    override fun performClick():Boolean { super.performClick();return true }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if(w>0 && h>0){
            val radiusRatio=h.toFloat()/(w*.90f*kotlin.math.tan(Math.toRadians(35.0)).toFloat())
            homeZoom=kotlin.math.sqrt(1f+radiusRatio*radiusRatio).coerceIn(1.75f,5f)
            zoom=homeZoom;render()
        }
    }
    override fun onResume() { super.onResume();coordinate(selected)?.let { zoom=homeZoom;focus(it) } }
    override fun onPause() { animation?.cancel();super.onPause() }
}
