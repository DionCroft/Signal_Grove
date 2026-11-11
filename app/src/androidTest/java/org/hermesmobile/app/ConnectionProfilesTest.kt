package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ConnectionProfilesTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    @Test fun rotatedConnectionsKeepTheirOwnHistoryAndNeverReplayPendingWork() {
        val posts=AtomicInteger()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    if(request.method=="POST") posts.incrementAndGet()
                    return when {
                        request.path=="/profile/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                        request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                        else->json("{}").setResponseCode(404)
                    }
                }
            }
            fun connect(token:String) {
                compose.waitUntil(10000) {!vm.connecting.value && !vm.jobBusy.value}
                val url=server.url("profile/").toString()
                compose.runOnUiThread {vm.configure(url,token,true)}
                compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="connected"}
            }
            connect("original-profile-fixture");val original=vm.profileScope.value
            val saved=Conversation("profiles:$original",original,"api_previous","Original uncertain conversation",pendingKey="original-request",status="unknown",runId="original-run")
            runBlocking {app.database.dao().save(saved);app.database.dao().save(ChatMessage("profiles-answer",saved.key,"assistant","Only the original connection can recover this."))}
            try {
                compose.runOnUiThread {vm.renameConnection(original,"Original fixture")}
                compose.waitUntil(5000) {!vm.controlBusy.value && vm.savedConnections.value.any {it.name=="Original fixture"}}
                compose.runOnUiThread {vm.changeDraft("Unsent original text")}
                connect("rotated-profile-fixture");val rotated=vm.profileScope.value
                assertNotEquals(original,rotated);assertEquals("",vm.draft.value)
                compose.waitUntil(5000) {vm.savedConnections.value.size==2 && vm.conversations.value.none {it.key==saved.key}}
                compose.runOnUiThread {vm.controlBusy.value=true;vm.useSavedConnection(original)}
                assertEquals(rotated,vm.profileScope.value)
                compose.runOnUiThread {vm.controlBusy.value=false;vm.useSavedConnection(original)}
                compose.waitUntil(10000) {!vm.connecting.value && vm.profileScope.value==original && vm.conversations.value.any {it.key==saved.key}}
                assertEquals("original-profile-fixture",app.credentials.readConfig()!!.token);assertEquals("",vm.selected.value)
                assertEquals("unknown",runBlocking {app.database.dao().conversation(saved.key)!!.status});assertEquals(0,posts.get())
                compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(chinese=false,pinyin=false))}
                compose.waitUntil(5000) {!vm.preferences.value.chinese}
                compose.onAllNodesWithContentDescription(Words.Settings.en,useUnmergedTree=true).onLast().performClick()
                compose.onNodeWithTag("saved-connections").performScrollTo().performClick()
                compose.onNodeWithTag("connection-list").performScrollToNode(hasText("Original fixture"))
                compose.onNodeWithText("Original fixture").assertIsDisplayed()
                val file=File(compose.activity.getExternalFilesDir(null),"qa/named-connections.png");file.parentFile!!.mkdirs()
                file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                compose.onNodeWithTag("connection-list").performScrollToNode(hasTestTag("connection-cache-$original"))
                compose.onNodeWithTag("connection-cache-$original").performClick()
                compose.onNodeWithText(saved.title).performScrollTo().performClick()
                compose.onNodeWithTag("message-body-profiles-answer").assertExists()
                compose.onNodeWithTag("close-connection-cache").performClick()
                compose.runOnUiThread {vm.removeConnectionCredential(original)}
                compose.waitUntil(10000) {!vm.connecting.value && vm.preferences.value.demo && vm.savedConnections.value.single {it.id==original}.hasCredential==false}
                assertNotNull(app.credentials.profile(rotated));assertNull(app.credentials.profile(original))
                assertNotNull(runBlocking {app.database.dao().conversation(saved.key)})
                assertEquals(0,posts.get())
            } finally {runBlocking {app.database.dao().deleteConversation(saved.key)}}
        }
    }
}
