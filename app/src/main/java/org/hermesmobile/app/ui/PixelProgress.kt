package org.hermesmobile.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.floor

/** A native accessible progress bar with the same crisp blocks as the surrounding artwork. */
@Composable fun PixelProgress(progress:Float?=null,modifier:Modifier=Modifier) {
    val value=progress?.let {if(it.isFinite()) it.coerceIn(0f,1f) else 0f}
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animate=value==null && !LocalReducedMotion.current && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val frame=rememberPixelFrame(animate,20)
    val ink=MaterialTheme.colorScheme.primary
    val track=MaterialTheme.colorScheme.onSurface.copy(alpha=.13f)
    Canvas(modifier.fillMaxWidth().height(8.dp).semantics {
        progressBarRangeInfo=value?.let {ProgressBarRangeInfo(it,0f..1f)} ?: ProgressBarRangeInfo.Indeterminate
    }) {
        val count=minOf(20,(size.width/3.dp.toPx()).toInt().coerceAtLeast(1))
        val gap=1.dp.toPx().coerceAtMost(size.width/(count*3))
        val cell=(size.width-gap*(count-1))/count
        val head=if(animate) frame.value%count else count/2-2
        for(index in 0 until count) {
            val left=floor(index*(cell+gap));val right=floor(index*(cell+gap)+cell)
            drawRect(track,Offset(left,0f),Size(right-left,size.height))
            val fill=value?.let {(it*count-index).coerceIn(0f,1f)}
                ?: if((index-head+count)%count<4) 1f else 0f
            if(fill>0f) drawRect(ink,Offset(left,0f),Size(floor((right-left)*fill),size.height))
        }
    }
}
