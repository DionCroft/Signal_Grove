package org.hermesmobile.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.ManagementTokens
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class HostFallbackFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    private fun exercise(mode:String) {
        val current=AtomicReference(HostFallbackFixture.config());val sends=AtomicInteger();val reads=AtomicInteger();val mcp=AtomicInteger();val other=AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        if(mode=="automatic") {val config=current.get();val rows=config["fallback_providers"]!!.jsonArray;current.set(JsonObject(config+mapOf("fallback_providers" to JsonArray(listOf(JsonObject(rows.first().jsonObject+mapOf("api_mode" to JsonPrimitive(""),"transport" to JsonPrimitive("anthropic"))))+rows.drop(1)))))}
        credentials.managementServer().use {server->
            fun json(j:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(j.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                val u=q.requestUrl!!
                if(u.encodedPath.startsWith("/dashboard/")) {assertEquals("Bearer fallback-ui-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"))}
                return when {
                    q.path=="/inference/v1/capabilities"->json(buildJsonObject {put("features",JsonObject(emptyMap()));put("endpoints",JsonObject(emptyMap()))})
                    q.path.orEmpty().contains("/inference/api/jobs?")->json(buildJsonObject {put("jobs",JsonArray(emptyList()))})
                    q.path=="/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture-user")})
                    q.path=="/dashboard/api/profiles"->json(PluginUpdateFixture.profiles())
                    u.encodedPath=="/dashboard/api/mcp/servers"->{mcp.incrementAndGet();json(buildJsonObject {put("servers",JsonArray(emptyList()))})}
                    u.encodedPath=="/dashboard/api/config" && q.method=="GET"->{assertEquals("worker",u.queryParameter("profile"));assertEquals("false",u.queryParameter("include_defaults"));reads.incrementAndGet();if(mode=="held") {entered.countDown();release.await(10,TimeUnit.SECONDS);returned.countDown()};json(current.get())}
                    u.encodedPath=="/dashboard/api/config" && q.method=="PUT"->{
                        assertEquals("worker",u.queryParameter("profile"));sends.incrementAndGet();val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject;assertEquals(setOf("config","profile"),body.keys);assertEquals("worker",body.text("profile"));val config=body["config"]!!.jsonObject
                        if(mode=="clear") {assertEquals(setOf("fallback_providers","fallback_model"),config.keys);assertTrue(config.values.all {it==JsonArray(emptyList())})}
                        else {assertEquals(setOf("fallback_providers"),config.keys);val rows=config["fallback_providers"]!!.jsonArray
                            when(mode) {
                                "fresh"->{assertEquals(4,rows.size);assertEquals(HostFallbackFixture.config()["fallback_providers"]!!.jsonArray,JsonArray(rows.take(3)));val row=rows.last().jsonObject;assertEquals(setOf("provider","model","base_url","api_key"),row.keys);assertEquals("plugin-provider",row.text("provider"));assertEquals("new-fallback-model",row.text("model"));assertEquals(HostFallbackFixture.endpoint,row.text("base_url"));assertEquals(HostFallbackFixture.fresh,row.text("api_key"))}
                                "automatic"->{val row=rows.first().jsonObject;assertEquals(HostFallbackFixture.row().keys,row.keys);assertEquals("edited-model",row.text("model"));assertEquals(HostFallbackFixture.inline,row.text("api_key"));assertEquals(HostFallbackFixture.env,row.text("key_env"));assertFalse(row.containsKey("api_mode"));assertFalse(row.containsKey("transport"))}
                                "environment"->{val row=rows.first().jsonObject;assertEquals(HostFallbackFixture.row().keys-setOf("api_key"),row.keys);assertEquals("NEW_FALLBACK_ENV",row.text("key_env"));assertEquals("edited-model",row.text("model"));assertEquals(HostFallbackFixture.row()["extension"],row["extension"])}
                                else->{assertEquals(listOf(HostFallbackFixture.row(),JsonPrimitive(17),HostFallbackFixture.config()["fallback_providers"]!!.jsonArray[1]),rows)}
                            }
                        }
                        current.set(JsonObject(current.get()+config));if(mode=="unknown") MockResponse().setResponseCode(400).setBody("private-fallback-host-error") else json(HostFallbackFixture.ack())
                    }
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            try {
                val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
                compose.waitUntil(10000) {!api.connecting.value};compose.runOnUiThread {api.configure(inference,credentials.uniqueToken("fallback-inference-fixture"),true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))};compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese}
                val scope=api.profileScope.value;app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("fallback-ui-fixture","refresh-fixture","password","fixture-user",4102444800).json())});compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn};compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
                compose.runOnUiThread {compose.activity.setContent {val p by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(p) {ManagementEntry(api,management)}}};compose.onNodeWithTag("management-open").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("fallback-open"));compose.onNodeWithTag("fallback-open").performClick();assertEquals(0,reads.get())
                fun scroll(tag:String)=compose.onNodeWithTag("fallback-list").performScrollToNode(hasTestTag(tag))
                fun read() {scroll("fallback-read");compose.onNodeWithTag("fallback-read").performClick();compose.onNodeWithTag("fallback-read-confirm").performClick()}
                read()
                if(mode=="held") {compose.waitUntil(10000) {entered.count==0L};compose.runOnUiThread {management.bind("unconfigured")};release.countDown();compose.waitUntil(10000) {returned.count==0L};compose.waitForIdle();assertNull(management.state.value.fallback);assertEquals(0,sends.get());return@use}
                compose.waitUntil(10000) {!management.state.value.busy && management.state.value.fallback!=null}
                for(secret in listOf(HostFallbackFixture.inline,HostFallbackFixture.endpoint,HostFallbackFixture.env,HostFallbackFixture.extra,"unrelated-private-fixture")) compose.onNodeWithText(secret,substring=true).assertDoesNotExist()
                fun stage() {
                    when(mode) {
                        "clear"->{scroll("fallback-clear");compose.onNodeWithTag("fallback-clear").performClick()}
                        "fresh","environment","automatic"->{
                            val tag=if(mode=="fresh") "fallback-add-Primary" else "fallback-edit-Primary-0";scroll(tag);compose.onNodeWithTag(tag).performClick();compose.onNodeWithTag("fallback-stage-row").assertIsNotEnabled();compose.onNodeWithTag("fallback-editor").performScrollToNode(hasTestTag("fallback-editor-reveal"));compose.onNodeWithTag("fallback-editor-reveal").performClick()
                            if(mode=="fresh") {compose.onNodeWithTag("fallback-editor").performScrollToNode(hasTestTag("fallback-provider"));compose.onNodeWithTag("fallback-provider").performTextReplacement("plugin-provider")}
                            compose.onNodeWithTag("fallback-editor").performScrollToNode(hasTestTag("fallback-model"));compose.onNodeWithTag("fallback-model").performTextReplacement(if(mode=="fresh") "new-fallback-model" else "edited-model")
                            if(mode=="fresh") {compose.onNodeWithTag("fallback-editor").performScrollToNode(hasTestTag("fallback-url"));compose.onNodeWithTag("fallback-url").performTextReplacement(HostFallbackFixture.endpoint)}
                            closeFocusedKeyboard()
                            if(mode=="automatic") {compose.onNodeWithTag("fallback-editor").performScrollToNode(hasTestTag("fallback-mode-"));compose.onNodeWithTag("fallback-mode-").performClick()}
                            else {compose.onNodeWithTag("fallback-editor").performScrollToNode(hasTestTag("fallback-auth-${if(mode=="fresh") "Fresh" else "Environment"}"));compose.onNodeWithTag("fallback-auth-${if(mode=="fresh") "Fresh" else "Environment"}").performClick();compose.onNodeWithTag("fallback-editor").performScrollToNode(hasTestTag("fallback-credential"));compose.onNodeWithTag("fallback-credential").performTextReplacement(if(mode=="fresh") HostFallbackFixture.fresh else "NEW_FALLBACK_ENV");closeFocusedKeyboard()}
                            compose.onNodeWithTag("fallback-stage-row").performClick()
                        }
                        else->{scroll("fallback-up-Primary-2");compose.onNodeWithTag("fallback-up-Primary-2").performClick()}
                    }
                    scroll("fallback-review-open");compose.onNodeWithTag("fallback-review-open").performClick()
                }
                fun approve() {
                    compose.onNodeWithTag("fallback-confirm").assertIsNotEnabled();compose.onNodeWithTag("fallback-review").performScrollToNode(hasTestTag("fallback-approve"));compose.onNodeWithTag("fallback-approve").assertIsOff().performClick();compose.onNodeWithTag("fallback-confirm").assertIsNotEnabled();compose.onNodeWithTag("fallback-review").performScrollToNode(hasTestTag("fallback-review-reveal"));compose.onNodeWithTag("fallback-review-reveal").performClick();compose.onNodeWithText(HostFallbackFixture.inline,substring=true).assertDoesNotExist();if(mode=="fresh") {compose.onNodeWithTag("fallback-review").performScrollToNode(hasText(HostFallbackFixture.fresh));compose.onNode(hasText(HostFallbackFixture.fresh) and hasAnyAncestor(hasTestTag("fallback-review"))).assertIsDisplayed()};compose.onNodeWithTag("fallback-review").performScrollToNode(hasTestTag("fallback-approve"));compose.onNodeWithTag("fallback-confirm").assertIsEnabled()
                }
                stage();approve()
                if(mode=="cancel") {compose.onNodeWithTag("fallback-review-cancel").performClick();assertEquals(0,sends.get());scroll("fallback-review-open");compose.onNodeWithTag("fallback-review-open").performClick();approve();compose.onNodeWithTag("fallback-review-cancel").performClick();compose.onNodeWithTag("fallback-close").performClick();assertNull(management.state.value.fallback);compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("fallback-open"));compose.onNodeWithTag("fallback-open").performClick();compose.onNodeWithTag("fallback-review-open").assertDoesNotExist();assertEquals(1,reads.get());return@use}
                val shot=File(compose.activity.getExternalFilesDir(null),"qa/fallback-$mode-review.png");shot.parentFile!!.mkdirs();shot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
                if(mode=="stale") current.set(HostFallbackFixture.config("secret"));compose.onNodeWithTag("fallback-confirm").performClick()
                if(mode=="stale") {compose.waitUntil(10000) {!management.state.value.busy && management.state.value.error==Words.ManagementReviewChanged};assertEquals(0,sends.get());assertTrue(management.receipts.value.none {it.action=="FallbackConfig"});return@use}
                compose.waitUntil(10000) {!management.state.value.busy && management.receipts.value.any {it.action=="FallbackConfig" && it.state==(if(mode=="unknown") "unknown" else "acknowledged")}}
                val receipt=management.receipts.value.first {it.action=="FallbackConfig"};for(secret in listOf(HostFallbackFixture.inline,HostFallbackFixture.fresh,HostFallbackFixture.endpoint,HostFallbackFixture.env,HostFallbackFixture.extra,"private-fallback-host-error","primary-model")) assertFalse(secret,receipt.toString().contains(secret));compose.onNodeWithTag("fallback-list").assertExists()
                if(mode=="unknown") {read();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.fallback!=null};scroll("fallback-add-Primary");compose.onNodeWithTag("fallback-add-Primary").assertIsNotEnabled()}
                if(mode=="observe") {val before=reads.get();compose.onNodeWithTag("fallback-close").performClick();compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-observe-${receipt.key}"));compose.onNodeWithTag("management-observe-${receipt.key}").performClick();assertEquals(before,reads.get());assertEquals(1,mcp.get());read();compose.waitUntil(10000) {!management.state.value.busy && management.state.value.fallback!=null};scroll("fallback-row-Primary-1");compose.onNodeWithTag("fallback-row-Primary-1").assertExists()}
                assertEquals(1,sends.get());assertEquals(1,mcp.get());assertEquals(0,other.get())
            } finally {release.countDown()}
        }
    }
    @Test fun automaticModeClearsLegacyTransportAndKeepsStoredCredentials()=exercise("automatic")
    @Test fun clearingBothChainsRequiresRevealAndFreshConsent()=exercise("clear")
    @Test fun freshKeyAndEndpointAreReviewedWithoutPersistingPrivateValues()=exercise("fresh")
    @Test fun replacingInlineCredentialWithEnvironmentReferencePreservesExtensions()=exercise("environment")
    @Test fun cancelRequiresNewConsentAndClosingDiscardsPrivateDraft()=exercise("cancel")
    @Test fun movedMalformedRowAndReceiptObservationNeverReplayOrDismissDialog()=exercise("observe")
    @Test fun unknownSaveBlocksAfterAnotherRead()=exercise("unknown")
    @Test fun changedPrivateCredentialRefusesBeforeSave()=exercise("stale")
    @Test fun lateReadCannotEnterAnotherConnection()=exercise("held")
}
