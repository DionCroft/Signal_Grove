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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.*
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
/** Eight visual frames per second; lifecycle/reduced-motion callers stop the clock entirely. */
@Composable internal fun rememberPixelFrame(active:Boolean,frames:Int=128):State<Int> = produceState(0,active,frames) {
    value=0
    if(active) {
        val origin=withInfiniteAnimationFrameNanos {it}
        while(true) withInfiniteAnimationFrameNanos {now->value=(((now-origin)/125_000_000L)%frames).toInt()}
    }
}
