package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ImageShareTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun uri(name:String)=Uri.parse("content://org.hermesmobile.app.test.images/$name")
    private fun share(uri:Uri)=Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri)
    private fun launch(intent:Intent) {compose.activity.startActivity(intent.setClass(compose.activity,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))}
    private fun awaitShareReview() {
        // An activity handoff may briefly have no Compose root. Keep polling within the same bound.
        compose.waitUntil(5000) {compose.onAllNodesWithTag("share-image-review").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
        compose.onNodeWithTag("share-image-review").assertIsDisplayed()
    }
    @Before fun ready() {
        compose.waitUntil(10000) {!vm.connecting.value};compose.runOnUiThread {vm.setDemo(true)}
        compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="demo"}
        compose.runOnUiThread {vm.newChat()}
    }
    @After fun reset() {compose.runOnUiThread {vm.cancelImageImport();vm.clearDraftImages()}}
    @Test fun shareIsReviewedForTheCurrentTargetAndNeverSubmitsAutomatically() {
        val existing=vm;val before=runBlocking {app.database.dao().conversations("demo").first().size}
        compose.runOnUiThread {vm.draft.value="Local text";launch(share(uri("red")).putExtra(Intent.EXTRA_TEXT,"Shared note"))}
        awaitShareReview()
        assertSame(existing,vm);assertNull(vm.imageDraft.value);assertEquals("Local text",vm.draft.value)
        val screenshot=File(compose.activity.getExternalFilesDir(null),"qa/image-share-review.png");screenshot.parentFile!!.mkdirs()
        screenshot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        compose.activityRule.scenario.recreate();compose.waitForIdle()
        compose.onNodeWithTag("share-image-review").assertExists()
        compose.onNodeWithTag("use-shared-images").assertIsEnabled().performClick()
        compose.waitUntil(10000) {!vm.imageLoading.value && vm.imageDraft.value?.refs?.size==1}
        assertEquals("Local text\nShared note",vm.draft.value);assertEquals("idle",vm.runStatus.value)
        assertEquals(before,runBlocking {app.database.dao().conversations("demo").first().size})
        compose.onNodeWithTag("image-draft-strip").assertExists()
        compose.activityRule.scenario.recreate();compose.waitForIdle()
        compose.onNodeWithTag("share-image-review").assertDoesNotExist()
    }
    @Test fun cancelAndInvalidFileSharesLeaveTheDraftUnchanged() {
        compose.runOnUiThread {vm.draft.value="Keep this text";launch(share(uri("blue")))}
        awaitShareReview()
        compose.onNodeWithText(Words.Cancel.localized(vm.preferences.value)).performClick()
        assertNull(vm.imageDraft.value);assertEquals("Keep this text",vm.draft.value)
        compose.runOnUiThread {launch(share(Uri.parse("file:///unavailable.jpg")))}
        awaitShareReview()
        compose.onNodeWithTag("use-shared-images").assertDoesNotExist()
        compose.onNodeWithText(Words.Cancel.localized(vm.preferences.value)).performClick()
        assertNull(vm.imageDraft.value);assertEquals("Keep this text",vm.draft.value)
    }
    @Test fun parserDeduplicatesSourcesAndRefusesOversizeOrNonImageShares() {
        val same=uri("red")
        val intent=share(same).apply {clipData=ClipData.newRawUri("fixture",same)}
        assertEquals(listOf(same),SharedImages.parse(intent)!!.uris);assertFalse(SharedImages.parse(intent)!!.invalid)
        assertNull(SharedImages.parse(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Text")))
        val multiple=Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/png").putParcelableArrayListExtra(Intent.EXTRA_STREAM,arrayListOf(uri("one"),uri("two"),uri("three"),uri("four")))
        assertTrue(SharedImages.parse(multiple)!!.invalid)
        val repeated=share(same).apply {clipData=ClipData.newRawUri("fixture",same).also {clip ->repeat(4) {clip.addItem(ClipData.Item(same))}}}
        assertTrue(SharedImages.parse(repeated)!!.invalid)
        assertTrue(SharedImages.parse(share(same).putExtra(Intent.EXTRA_TEXT,"x".repeat(64001)))!!.invalid)
        assertTrue(SharedImages.parse(Intent(Intent.ACTION_SEND).setType("image/png"))!!.invalid)
    }
}
