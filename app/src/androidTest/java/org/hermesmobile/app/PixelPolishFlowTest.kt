package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.compose.ui.unit.dp
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelProvider
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PixelPolishFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val updates=UpdateFixtureRule()
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private fun exercise(reduced:Boolean) {
        fun assertBrandFits() {
            val brand=compose.activity.getString(R.string.app_name)
            assertEquals("Signal Grove",brand)
            val title=compose.onNodeWithText(brand,useUnmergedTree=true).assertIsDisplayed().getUnclippedBoundsInRoot()
            val version=compose.onNodeWithText(BuildConfig.VERSION_NAME,useUnmergedTree=true).assertIsDisplayed().getUnclippedBoundsInRoot()
            val frame=compose.onRoot().getUnclippedBoundsInRoot()
            assertTrue(title.left>=frame.left && title.right<=frame.right && version.left>=frame.left && version.right<=frame.right)
            assertTrue(title.right<=version.left || version.right<=title.left || title.bottom<=version.top || version.bottom<=title.top)
        }
        val original=Settings.Global.getFloat(compose.activity.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        fun scale(v:Float) {androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("settings put global animator_duration_scale $v").use {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use {stream->stream.readBytes()}}}
        try {
            scale(1f)
            val vm=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
            compose.runOnUiThread {compose.activity.setContent {HermesAppTheme(AppPreferences(theme="STARHAVEN",chinese=false,pinyin=false,reducedMotion=reduced)) {HermesApp(vm)}}}
            assertBrandFits()
            compose.onNodeWithText(Words.Greeting.en).assertIsDisplayed()
            compose.onNodeWithTag("home-voice-action").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            val shot=File(compose.activity.getExternalFilesDir(null),"qa/pixel-home-${if(reduced) "still" else "motion"}.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            for(route in listOf("Tasks","Voice","Chat","Settings","Home")) {compose.onNodeWithContentDescription(route,useUnmergedTree=true).performClick();compose.waitForIdle()}
            assertBrandFits()
            compose.onNodeWithText(Words.Greeting.en).assertIsDisplayed()
            if(!reduced) {scale(0f);compose.waitForIdle();compose.onNodeWithContentDescription("Tasks",useUnmergedTree=true).performClick();compose.onNodeWithContentDescription("Home",useUnmergedTree=true).performClick();compose.onNodeWithText(Words.Greeting.en).assertIsDisplayed()}
        } finally {scale(original)}
    }
    @Test fun animatedPixelHomeAndNavigationKeepAllDestinationsReachable()=exercise(false)
    @Test fun reducedMotionRetainsTheSameReadablePixelInterface()=exercise(true)
}
