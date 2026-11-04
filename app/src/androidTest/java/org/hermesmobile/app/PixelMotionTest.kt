package org.hermesmobile.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PixelMotionTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val updates=UpdateFixtureRule()
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private class Owner:LifecycleOwner {
        val registry=LifecycleRegistry(this)
        override val lifecycle:Lifecycle get()=registry
    }
    private fun scene(reduced:Boolean=false,progress:Float?=null):Owner {
        compose.mainClock.autoAdvance=false
        lateinit var owner:Owner
        compose.runOnUiThread {
            owner=Owner();owner.registry.currentState=Lifecycle.State.RESUMED
            compose.activity.setContent {
                HermesAppTheme(AppPreferences(theme="STARHAVEN",chinese=false,pinyin=false,reducedMotion=reduced)) {
                    CompositionLocalProvider(LocalLifecycleOwner provides owner,LocalReducedMotion provides reduced) {
                        Column {
                            PixelHomestead(Modifier.testTag("scene"))
                            PixelProgress(progress,Modifier.testTag("progress"))
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(64);compose.waitForIdle()
        return owner
    }
    private fun pixels(tag:String):IntArray {
        val bitmap=compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        return IntArray(bitmap.width*bitmap.height).also {bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height)}
    }
    @Test fun foregroundAmbientArtAndBusyIndicatorActuallyAnimate() {
        scene();val art=pixels("scene");val progress=pixels("progress")
        compose.mainClock.advanceTimeBy(2250);compose.waitForIdle()
        assertFalse(art.contentEquals(pixels("scene")))
        assertFalse(progress.contentEquals(pixels("progress")))
    }
    @Test fun reducedMotionKeepsArtAndBusyIndicatorStill() {
        scene(reduced=true);val art=pixels("scene");val progress=pixels("progress")
        compose.mainClock.advanceTimeBy(2250);compose.waitForIdle()
        assertArrayEquals(art,pixels("scene"));assertArrayEquals(progress,pixels("progress"))
    }
    @Test fun backgroundSceneStopsAndResumingRestartsMotion() {
        val owner=scene();compose.mainClock.advanceTimeBy(1000)
        compose.runOnUiThread {owner.registry.currentState=Lifecycle.State.STARTED}
        compose.mainClock.advanceTimeBy(64);compose.waitForIdle()
        val art=pixels("scene");val progress=pixels("progress")
        compose.mainClock.advanceTimeBy(2250);compose.waitForIdle()
        assertArrayEquals(art,pixels("scene"));assertArrayEquals(progress,pixels("progress"))
        compose.runOnUiThread {owner.registry.currentState=Lifecycle.State.RESUMED}
        compose.mainClock.advanceTimeBy(64);compose.waitForIdle()
        val resumed=pixels("scene")
        compose.mainClock.advanceTimeBy(2250);compose.waitForIdle()
        assertFalse(resumed.contentEquals(pixels("scene")))
    }
    @Test fun downloadProgressHasExactAccessibleValueWithoutAmbientMotion() {
        scene(progress=.42f)
        compose.onNodeWithTag("progress").assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo(.42f,0f..1f)))
        val progress=pixels("progress");compose.mainClock.advanceTimeBy(2250);compose.waitForIdle()
        assertArrayEquals(progress,pixels("progress"))
    }
    @Test fun loadingIndicatorReportsIndeterminateProgress() {
        scene(reduced=true)
        compose.onNodeWithTag("progress").assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo.Indeterminate))
    }
}
