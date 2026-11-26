package org.hermesmobile.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import kotlin.math.floor

@Composable
internal fun PixelHomestead(modifier: Modifier = Modifier, latitude: Double? = null, sky: GroveSky? = null) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val animate = visible && !LocalReducedMotion.current
    val now by produceState(ZonedDateTime.now(), visible) {
        value = ZonedDateTime.now()
        while (visible) {
            delay(60_000L - Math.floorMod(System.currentTimeMillis(), 60_000L))
            value = ZonedDateTime.now()
        }
    }
    val frame = rememberPixelFrame(animate)
    Crossfade(
        targetState = groveScene(now, latitude, sky),
        modifier = modifier.fillMaxWidth().aspectRatio(3.6f).clipToBounds().clearAndSetSemantics {},
        animationSpec = if (animate) tween(440) else snap(),
        label = "grove-scene"
    ) { scene ->
        Canvas(Modifier.fillMaxWidth().aspectRatio(3.6f)) {
            drawGrove(scene, if (animate) frame.value else 0)
        }
    }
}

private data class GrovePalette(val sky: Color, val horizon: Color, val ridge: Color, val trees: Color, val earth: Color)

private fun palette(light: GroveLight) = when (light) {
    GroveLight.DAWN -> GrovePalette(Color(0xFF60577F), Color(0xFFE9AE91), Color(0xFF83738A), Color(0xFF5E676D), Color(0xFF765B57))
    GroveLight.DAY -> GrovePalette(Color(0xFF5F9DB0), Color(0xFFB5D5C3), Color(0xFF6B9C91), Color(0xFF46786A), Color(0xFF725A48))
    GroveLight.DUSK -> GrovePalette(Color(0xFF474269), Color(0xFFD08985), Color(0xFF79617F), Color(0xFF51586C), Color(0xFF634D50))
    GroveLight.NIGHT -> GrovePalette(Color(0xFF162839), Color(0xFF304D59), Color(0xFF34495B), Color(0xFF28474A), Color(0xFF433C42))
}

private class GrovePixels(private val scope: DrawScope) {
    private val unit = scope.size.width / 240f

    fun block(x: Int, y: Int, w: Int, h: Int, color: Color) {
        val left = floor(x * unit)
        val top = floor(y * unit)
        scope.drawRect(color, Offset(left, top), Size(floor((x + w) * unit) - left, floor((y + h) * unit) - top))
    }

    fun orb(x: Int, y: Int, width: Int, color: Color) {
        block(x + 3, y, width - 6, 2, color)
        block(x, y + 2, width, width - 4, color)
        block(x + 3, y + width - 2, width - 6, 2, color)
    }
}

private fun DrawScope.drawGrove(scene: GroveScene, frame: Int) {
    val p = palette(scene.light)
    val canvas = GrovePixels(this)
    val ice = Color(0xFFD6E6E2)
    val gold = Color(0xFFF3C87E)
    val night = scene.light == GroveLight.NIGHT
    val gust = frame / 8
    drawRect(p.sky)

    repeat(7) { strip ->
        canvas.block(0, 17 + strip * 5, 240, 5, p.horizon.copy(alpha = (strip + 1) * .12f))
    }
    if (night) {
        repeat(31) { i ->
            val shimmer = if ((i + frame / 12) % 9 == 0) .46f else .9f
            canvas.block((i * 67 + 13) % 240, (i * 17 + 5) % 34, 1, 1, ice.copy(alpha = shimmer))
        }
    }
    val sunY = when (scene.light) {
        GroveLight.DAY -> 7
        GroveLight.NIGHT -> 9
        else -> 20
    }
    canvas.orb(181, sunY, 19, if (night) gold else Color(0xFFFFDEAA))
    if (night) {
        canvas.block(184 + scene.moonPhase * 2, sunY + 4, 2, 8, Color(0xFFAB805E))
        canvas.block(176, sunY + 10, 29, 1, Color(0xFFD8A780))
    }
    if (scene.sky != GroveSky.CLEAR) {
        repeat(if (scene.sky == GroveSky.CLOUDY) 4 else 6) { i ->
            val x = (i * 57 + gust) % 272 - 20
            val y = 8 + (i * 9) % 21
            val cloud = (if (scene.sky == GroveSky.STORM) p.ridge else ice).copy(alpha = if (night) .38f else .76f)
            canvas.block(x + 4, y, 23, 3, cloud)
            canvas.block(x, y + 3, 38, 3, cloud)
            canvas.block(x + 7, y + 6, 22, 1, cloud)
        }
    }
    repeat(14) { i ->
        val x = i * 19
        val y = 35 + (i * 7) % 11
        canvas.block(x + 4, y, 12, 2, p.ridge)
        canvas.block(x, y + 2, 23, 27, p.ridge)
    }
    repeat(16) { i ->
        val x = i * 16
        val y = 39 + (i * 9) % 13
        canvas.block(x + 4, y, 3, 17, p.trees)
        canvas.block(x + 1, y + 5, 9, 15, p.trees)
        canvas.block(x - 2, y + 12, 15, 9, p.trees)
    }
    canvas.block(0, 57, 240, 12, p.earth)
    val foliage = when (scene.season) {
        GroveSeason.SPRING -> Color(0xFFB4C889)
        GroveSeason.SUMMER -> Color(0xFF89BE88)
        GroveSeason.AUTUMN -> Color(0xFFD59867)
        GroveSeason.WINTER -> ice
    }
    canvas.block(0, 57, 240, if (scene.season == GroveSeason.WINTER) 3 else 1, foliage.copy(alpha = if (night) .66f else .9f))

    canvas.block(26, 40, 58, 17, if (night) Color(0xFF8D7565) else Color(0xFFD8B58F))
    canvas.block(23, 37, 64, 4, Color(0xFF6E5358))
    canvas.block(29, 33, 52, 4, Color(0xFFAA715E))
    canvas.block(35, 30, 40, 3, Color(0xFFCC936E))
    if (scene.season == GroveSeason.WINTER) canvas.block(35, 30, 40, 1, ice)
    canvas.block(33, 45, 11, 8, if (scene.light == GroveLight.DAY) Color(0xFFA6CDD0) else gold)
    canvas.block(66, 45, 11, 8, if (scene.light == GroveLight.DAY) Color(0xFFA6CDD0) else gold)
    canvas.block(38, 45, 1, 8, p.earth)
    canvas.block(71, 45, 1, 8, p.earth)
    canvas.block(51, 45, 8, 12, p.trees)
    canvas.block(56, 50, 1, 2, gold)
    canvas.block(68, 21, 2, 9, p.ridge)
    canvas.block(64, 21, 10, 2, ice)
    canvas.block(68, 19, 2, 2, gold.copy(alpha = if ((frame / 12) % 4 == 3) .45f else 1f))
    repeat(3) { i ->
        val drift = (frame / 4 + i * 7) % 19
        canvas.block(77 + drift / 3, 30 - drift, 2 + i % 2, 1, ice.copy(alpha = .40f - drift * .014f))
    }
    canvas.block(89, 59, 32, 2, gold.copy(alpha = .33f))

    repeat(7) { i ->
        val x = 121 + i * 16
        canvas.block(x, 62, 11, 2, p.trees.copy(alpha = .76f))
        if (scene.season == GroveSeason.WINTER) {
            canvas.block(x + 2, 60, 7, 1, ice)
        } else {
            val sway = (gust + i) % 3 - 1
            canvas.block(x + 5, 55, 2, 7, foliage)
            canvas.block(x + 2 + sway, 55, 8, 2, foliage)
            canvas.block(x + 4 + sway, 53, 4, 2, if (scene.season == GroveSeason.SPRING) Color(0xFFF4A8AE) else gold)
        }
    }
    when (scene.sky) {
        GroveSky.RAIN, GroveSky.STORM -> repeat(23) { i ->
            val y = (i * 11 + frame * 2) % 65
            canvas.block((i * 61 + 245 - y / 3) % 240, y, 1, if (scene.sky == GroveSky.STORM) 4 else 3, ice.copy(alpha = .48f))
        }
        GroveSky.SNOW -> repeat(27) { i ->
            canvas.block((i * 69 + gust * 2) % 240, (i * 11 + frame / 2) % 65, 1, 1 + i % 2, ice.copy(alpha = .89f))
        }
        GroveSky.FOG -> repeat(6) { i ->
            canvas.block((i * 48 + gust * 2) % 300 - 38, 39 + i * 3, 65, 3, ice.copy(alpha = .26f))
        }
        else -> Unit
    }
    when (scene.event) {
        GroveEvent.VISITOR -> {
            val x = 101 + frame / 2
            val y = 25 + gust % 3
            canvas.block(x, y, 16, 2, ice)
            canvas.block(x + 3, y - 2, 10, 2, foliage)
            canvas.block(x + 5, y + 2, 6, 1, gold)
        }
        GroveEvent.METEOR -> {
            val x = 83 + frame / 2
            val y = 6 + frame / 7
            repeat(9) { i -> canvas.block(x - i, y - i / 2, 1, 1, gold.copy(alpha = 1f - i * .10f)) }
        }
        GroveEvent.FIREFLIES -> repeat(7) { i ->
            canvas.block(104 + i * 16 + (gust + i) % 5, 46 + (i * 3 + gust) % 8, 2, 1, gold.copy(alpha = .56f + (i + gust) % 4 * .12f))
        }
        GroveEvent.RAINBOW -> {
            listOf(Color(0xFFF3B6A9), gold, foliage).forEachIndexed { i, color ->
                val arc = color.copy(alpha = .8f)
                canvas.block(138 + i, 31 + i, 21 - 2 * i, 1, arc)
                canvas.block(134 + i, 32 + i, 4, 2, arc)
                canvas.block(159 - i, 32 + i, 4, 2, arc)
                canvas.block(132 + i, 34 + i, 2, 4, arc)
                canvas.block(163 - i, 34 + i, 2, 4, arc)
            }
        }
        GroveEvent.NONE -> Unit
    }
}
