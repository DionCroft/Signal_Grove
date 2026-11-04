package org.hermesmobile.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.*

/** Original vector pixel art. Body text remains native and supports Chinese and pinyin. */
object PixelPanelShape:Shape {
    override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density):Outline {
        val s=with(density) {3.dp.toPx()}.coerceAtMost(min(size.width,size.height)/4)
        val w=size.width;val h=size.height
        return Outline.Generic(Path().apply {moveTo(2*s,0f);lineTo(w-2*s,0f);lineTo(w-2*s,s);lineTo(w-s,s);lineTo(w-s,2*s);lineTo(w,2*s);lineTo(w,h-2*s);lineTo(w-s,h-2*s);lineTo(w-s,h-s);lineTo(w-2*s,h-s);lineTo(w-2*s,h);lineTo(2*s,h);lineTo(2*s,h-s);lineTo(s,h-s);lineTo(s,h-2*s);lineTo(0f,h-2*s);lineTo(0f,2*s);lineTo(s,2*s);lineTo(s,s);lineTo(2*s,s);close()})
    }
}
fun Modifier.pixelPaper(ink:Color)=drawBehind {
    val step=24.dp.toPx();val dot=1.dp.toPx()
    for(y in 0..(size.height/step).toInt()) for(x in 0..(size.width/step).toInt()) {
        drawRect(ink.copy(alpha=.035f),Offset(x*step+(if(y%2==0) 0f else step/2),y*step),Size(dot,dot))
    }
}
@Composable fun PixelIcon(route:String,modifier:Modifier=Modifier,tint:Color=LocalContentColor.current) {
    val rows=when(route) {
        "Home"->listOf("00011000","00111100","01111110","11111111","01100110","01100110","01111110","00000000")
        "Chat"->listOf("01111110","11000011","10110101","10000001","10111101","11000011","01111110","00110000")
        "Tasks"->listOf("00111110","00100010","11111010","10001010","10101010","10011010","10001000","11111000")
        "Automation"->listOf("00111100","01100110","11011011","10011001","10011101","11000011","01100110","00111100")
        "Voice"->listOf("00011000","00111100","00111100","10111101","10011001","01000010","00111100","00011000")
        else->listOf("00100100","01111110","11011011","10100101","10100101","11011011","01111110","00100100")
    }
    Canvas(modifier.size(24.dp).clearAndSetSemantics {}) {val p=floor(min(size.width,size.height)/8).coerceAtLeast(1f);val dx=floor((size.width-p*8)/2);val dy=floor((size.height-p*8)/2);rows.forEachIndexed {y,row->row.forEachIndexed {x,c->if(c=='1') drawRect(tint,Offset(dx+x*p,dy+y*p),Size(p,p))}}}
}
@Composable fun PixelPrimaryButton(onClick:()->Unit,enabled:Boolean=true,modifier:Modifier=Modifier,content:@Composable RowScope.()->Unit) {
    val source=remember {MutableInteractionSource()};val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed && enabled) .97f else 1f,if(LocalReducedMotion.current) snap() else spring(dampingRatio=.78f,stiffness=900f),label="pixel-button-press")
    Button(onClick,modifier.graphicsLayer {scaleX=scale;scaleY=scale},enabled=enabled,shape=PixelPanelShape,interactionSource=source,content=content)
}
@Composable fun PixelHomestead(modifier:Modifier=Modifier) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animate=!LocalReducedMotion.current && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val phase=if(animate) {
        val transition=rememberInfiniteTransition(label="homestead-ambient")
        transition.animateFloat(0f,1f,infiniteRepeatable(tween(16000,easing=LinearEasing)),label="homestead-orbit").value
    } else 0f
    Canvas(modifier.fillMaxWidth().aspectRatio(3.6f).clipToBounds().clearAndSetSemantics {}) {
        // Quantised coordinates keep the art crisp; no smooth scaling of bitmap assets.
        val unit=size.width/240f
        fun rect(x:Float,y:Float,w:Float,h:Float,color:Color)=drawRect(color,Offset(floor(x)*unit,floor(y)*unit),Size(ceil(w)*unit,ceil(h)*unit))
        val sky=Color(0xFF223D45);val gold=Color(0xFFF4C67A);val mint=Color(0xFFB8D5AC);val forest=Color(0xFF3E6658);val earth=Color(0xFF73553E)
        drawRect(sky)
        for(i in 0..30) {val x=((i*67+13)%240).toFloat();val y=((i*17+5)%38).toFloat();val blink=((floor(phase*12)+i.toInt())%7).toInt()!=0;rect(x,y,1f,1f,if(blink) Color(0xFFD8E8CE) else sky)}
        // Ringed moon, tiny survey ship, stepped hills and garden cabin.
        rect(177f,11f,18f,2f,gold);rect(173f,13f,26f,9f,gold);rect(177f,22f,18f,3f,gold)
        rect(169f,18f,34f,2f,Color(0xFFB18D65));rect(182f,13f,3f,3f,Color(0xFFD2A46B));rect(189f,19f,4f,2f,Color(0xFFD2A46B))
        val bob=if(animate) floor(sin(phase*PI*2)*2).toFloat() else 0f
        rect(123f,19f+bob,10f,2f,mint);rect(120f,21f+bob,16f,3f,mint);rect(126f,17f+bob,4f,2f,gold);rect(126f,24f+bob,4f,1f,Color(0xFFE08F65))
        for(i in 0..15) {val x=i*16f;val top=35f+((i*7)%12);rect(x,top,18f,33f,forest.copy(alpha=.55f))}
        for(i in 0..12) {val x=i*21f;val top=47f+((i*5)%7);rect(x,top,23f,22f,forest)}
        rect(0f,58f,240f,12f,earth);rect(0f,58f,240f,2f,mint.copy(alpha=.5f))
        // Observatory cabin with copper roof, glowing windows and antenna.
        rect(30f,39f,47f,20f,Color(0xFFCEAF7E));rect(25f,37f,57f,4f,Color(0xFF8E5D48));rect(30f,33f,47f,4f,Color(0xFFB97756));rect(35f,29f,37f,4f,Color(0xFFD79968))
        rect(35f,44f,10f,8f,gold);rect(63f,44f,9f,8f,gold);rect(50f,45f,9f,14f,Color(0xFF37554D));rect(54f,49f,2f,2f,gold)
        rect(63f,20f,2f,10f,Color(0xFF9CB8A0));rect(59f,19f,10f,2f,mint);rect(61f,16f,6f,2f,gold)
        for(i in 0..6) {val x=99f+i*13;rect(x,61f,8f,2f,Color(0xFF4E3F35));rect(x+3,55f,2f,6f,mint);rect(x,55f,3f,2f,Color(0xFF92B980));rect(x+5,53f,3f,2f,Color(0xFF92B980))}
        for(i in 0..4) {val x=10f+i*55;rect(x,50f,2f,2f,gold.copy(alpha=if(animate && (floor(phase*8)+i)%3==0f) .25f else .8f))}
    }
}
