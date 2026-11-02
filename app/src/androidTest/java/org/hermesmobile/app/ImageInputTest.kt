package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ImageInputTest {
    @get:Rule(order=0) val credentialFixture=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun uri(name:String)=Uri.parse("content://org.hermesmobile.app.test.images/$name")
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    private fun dispatcher(posts:MutableList<Triple<String,String?,String?>>,loseFirst:Boolean=false)=object:Dispatcher() {
        override fun dispatch(request:RecordedRequest):MockResponse=when {
            request.path=="/v1/capabilities"->json("""{"features":{"session_resources":true,"session_chat":true,"run_events_sse":true,"model_options":true,"session_key_header":"X-Hermes-Session-Key"},"endpoints":{"runs":{}}}""")
            request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
            request.path.orEmpty().startsWith("/api/sessions?")->json("""{"data":[],"has_more":false}""")
            request.path=="/api/sessions"->{val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject;json(buildJsonObject {put("session",buildJsonObject {put("id",body.text("id"));put("title",body.text("title"))})}.toString())}
            request.path=="/v1/runs"->{val count=synchronized(posts) {posts+=Triple(request.body.readUtf8(),request.getHeader("Idempotency-Key"),request.getHeader("X-Hermes-Session-Key"));posts.size}
                if(loseFirst && count==1) MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) else json("""{"run_id":"image-run"}""")}
            request.path=="/v1/runs/image-run/events"->MockResponse().setHeader("Content-Type","text/event-stream").setBody(if(loseFirst) "data: {\"event\":\"run.failed\",\"run_id\":\"image-run\",\"seq\":0}\n\n" else "data: {\"event\":\"run.completed\",\"run_id\":\"image-run\",\"output\":\"Image fixture complete\",\"seq\":0}\n\n")
            else->json("{}").setResponseCode(404)
        }
    }
    private fun connect(server:MockWebServer) {
        compose.waitUntil(10000) {!vm.connecting.value && !vm.controlBusy.value}
        val url=server.url("/").toString();compose.runOnUiThread {vm.configure(url,"image-input-fixture",true)}
        compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="connected"}
    }
    private fun load(name:String) {
        compose.runOnUiThread {vm.importImages(listOf(uri(name)),vm.profileScope.value,vm.selected.value)}
        compose.waitUntil(10000) {!vm.imageLoading.value && !vm.controlBusy.value && vm.imageDraft.value?.refs?.isNotEmpty()==true}
    }
    @After fun reset() {
        compose.runOnUiThread {vm.cancelImageImport()}
        compose.waitUntil(10000) {!vm.busy.value && !vm.controlBusy.value && !vm.connecting.value}
        compose.runOnUiThread {vm.forgetServer()};compose.waitUntil(10000) {!vm.connecting.value && vm.preferences.value.demo}
    }
    @Test fun pickerDispatchStagesAReviewableImageWithoutSubmittingUntilSend() {
        val posts=mutableListOf<Triple<String,String?,String?>>()
        MockWebServer().use {server ->
            server.dispatcher=dispatcher(posts);connect(server);compose.runOnUiThread {vm.newChat()}
            compose.onAllNodesWithContentDescription(Words.Chat.localized(vm.preferences.value),useUnmergedTree=true).onLast().performClick()
            val instrumentation=InstrumentationRegistry.getInstrumentation();val dispatched=AtomicInteger()
            val monitor=object:Instrumentation.ActivityMonitor() {override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult? {
                if(intent.action in setOf("android.provider.action.PICK_IMAGES","android.intent.action.OPEN_DOCUMENT","android.intent.action.GET_CONTENT","androidx.activity.result.contract.action.SYSTEM_FALLBACK_PICK_IMAGES")) {
                    dispatched.incrementAndGet();return Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(uri("red")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
                return null
            }}
            instrumentation.addMonitor(monitor)
            try {compose.onNodeWithTag("choose-images").assertIsEnabled().performClick()
                compose.waitUntil(10000) {!vm.imageLoading.value && vm.imageDraft.value?.refs?.size==1}
            } finally {instrumentation.removeMonitor(monitor)}
            assertEquals(1,dispatched.get());assertTrue(posts.isEmpty());assertEquals("",vm.draft.value)
            compose.onNodeWithTag("image-draft-strip").assertExists()
            val screenshot=File(compose.activity.getExternalFilesDir(null),"qa/image-draft-review.png");screenshot.parentFile!!.mkdirs()
            screenshot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithContentDescription(Words.Send.localized(vm.preferences.value)).assertIsEnabled().performClick()
            compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="completed"}
            assertEquals(1,posts.size);val body=Json.parseToJsonElement(posts.single().first).jsonObject
            val parts=body["input"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray
            assertEquals(1,parts.size);assertTrue(parts.single().jsonObject["image_url"]!!.jsonObject.text("url").startsWith("data:image/jpeg;base64,"))
            assertNull(vm.imageDraft.value);assertTrue(runBlocking {app.database.dao().history(vm.selected.value).first {it.role=="user"}.imagesJson}.contains("hash"))
        }
    }
    @Test fun changedDraftCannotAlterLostAdmissionAndExplicitRetryRestoresOriginalPixels() {
        val posts=mutableListOf<Triple<String,String?,String?>>()
        MockWebServer().use {server ->
            server.dispatcher=dispatcher(posts,true);connect(server);compose.runOnUiThread {vm.newChat()};load("red")
            val original=vm.imageDraft.value!!.refs.single().hash
            compose.runOnUiThread {vm.send("Original image")}
            compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="unknown"}
            val c=runBlocking {app.database.dao().conversation(vm.selected.value)!!}
            load("blue");val replacement=vm.imageDraft.value!!.refs.single().hash;assertNotEquals(original,replacement)
            compose.runOnUiThread {vm.select(c.key)}
            assertEquals(replacement,vm.imageDraft.value!!.refs.single().hash)
            compose.runOnUiThread {vm.chooseRuntime(vm.profileScope.value,RuntimeChoice("different-model","different-provider"))}
            compose.waitUntil(5000) {!vm.controlBusy.value}
            compose.runOnUiThread {vm.retryOrRefresh()}
            compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="failed"}
            assertEquals(2,posts.size);assertEquals(posts.first(),posts.last());assertEquals(replacement,vm.imageDraft.value!!.refs.single().hash)
            app.images.blobs.deleteConversation(c.scope,c.key)
            compose.runOnUiThread {vm.prepareRetry()}
            compose.waitUntil(10000) {!vm.imageLoading.value && !vm.controlBusy.value && vm.imageDraft.value?.refs?.singleOrNull()?.hash==original}
            assertEquals("Original image",vm.draft.value);assertEquals(2,posts.size)
        }
    }
    @Test fun cancelAndConnectionChangesDiscardLateImagesAndInvalidSourcesNeverSubmit() {
        val first=mutableListOf<Triple<String,String?,String?>>();val second=mutableListOf<Triple<String,String?,String?>>()
        MockWebServer().use {a -> MockWebServer().use {b ->
            a.dispatcher=dispatcher(first);b.dispatcher=dispatcher(second);connect(a)
            val oldScope=vm.profileScope.value;val oldOwner=vm.selected.value;val url=b.url("/").toString()
            compose.runOnUiThread {vm.importImages(listOf(uri("slow")),oldScope,oldOwner)}
            compose.waitUntil(5000) {vm.imageLoading.value}
            compose.runOnUiThread {vm.configure(url,"image-input-fixture",true)}
            assertEquals(oldScope,vm.profileScope.value);assertEquals(0,b.requestCount)
            compose.runOnUiThread {vm.cancelImageImport();vm.configure(url,"image-input-fixture",true)}
            compose.waitUntil(10000) {!vm.connecting.value && vm.profileScope.value!=oldScope}
            compose.waitUntil(5000) {(app.contentResolver.call(uri("slow"),"state",null,null)?.getInt("active") ?: 0)==0}
            assertNull(vm.imageDraft.value)
            compose.runOnUiThread {vm.importImages(listOf(uri("red")),oldScope,oldOwner)}
            assertNull(vm.imageDraft.value)
            compose.runOnUiThread {vm.importImages(listOf(Uri.parse("file:///unavailable.jpg")),vm.profileScope.value,vm.selected.value)}
            compose.waitUntil(5000) {!vm.imageLoading.value && !vm.controlBusy.value}
            assertNull(vm.imageDraft.value);assertTrue(first.isEmpty());assertTrue(second.isEmpty())
        } }
    }
}
