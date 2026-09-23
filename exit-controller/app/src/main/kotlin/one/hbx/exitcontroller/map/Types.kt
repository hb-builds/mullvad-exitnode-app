// SPDX-License-Identifier: GPL-3.0-only
package one.hbx.exitcontroller.map
import kotlin.math.sqrt

data class Color(val red:Float,val green:Float,val blue:Float,val alpha:Float=1f) {
    constructor(argb:Int):this(((argb ushr 16) and 255)/255f,((argb ushr 8) and 255)/255f,(argb and 255)/255f,((argb ushr 24) and 255)/255f)
    fun copy(alpha:Float)=Color(red,green,blue,alpha)
    companion object { val White=Color(-1); val Black=Color(0xFF000000.toInt()); val Transparent=Color(0) }
}
data class Offset(val x:Float,val y:Float)
val Offset.isUnspecified:Boolean get()=!x.isFinite() || !y.isFinite()
data class Size(val width:Float,val height:Float)
object EaseInCirc { fun transform(x:Float)=1f-sqrt(1f-x*x) }
object EaseOutQuad { fun transform(x:Float)=1f-(1f-x)*(1f-x) }
