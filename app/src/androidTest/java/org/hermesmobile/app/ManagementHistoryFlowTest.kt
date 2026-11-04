package org.hermesmobile.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.ManagementTokens
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class ManagementHistoryFlowTest {
    @get:Rule(order=0) val credentials=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val api get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val management get()=ViewModelProvider(compose.activity)[ManagementViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    @Test fun otherConnectionsSettledHistoryCannotHideOwnOlderReceipt() {
        val writes=AtomicInteger()
        credentials.managementServer().use {server ->
            fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method!="GET") writes.incrementAndGet()
                if(request.path.orEmpty().startsWith("/dashboard/")) assertEquals("Bearer history-native-fixture",request.getHeader("Authorization"))
                return when {
                    request.path=="/inference/v1/capabilities"->json("""{"features":{},"endpoints":{}}""")
                    request.path.orEmpty().contains("/inference/api/jobs?")->json("""{"jobs":[]}""")
                    request.path=="/dashboard/api/auth/me"->json("""{"user_id":"fixture-user","provider":"password"}""")
                    request.path=="/dashboard/api/profiles"->json("""{"profiles":[{"name":"worker"}]}""")
                    request.path=="/dashboard/api/mcp/servers?profile=worker"->json("""{"servers":[]}""")
                    else->MockResponse().setResponseCode(404)
                }
            }}
            val inference=server.url("inference/").toString();val dashboard=server.url("dashboard/").toString()
            compose.waitUntil(10000) {!api.connecting.value}
            compose.runOnUiThread {api.configure(inference,credentials.uniqueToken("history-inference-fixture"),true);api.updatePreferences(api.preferences.value.copy(chinese=false,pinyin=false))}
            compose.waitUntil(10000) {!api.connecting.value && api.connectionStatus.value=="connected" && !api.preferences.value.chinese && !api.preferences.value.pinyin}
            val scope=api.profileScope.value
            app.credentials.saveManagement(scope,buildJsonObject {put("url",dashboard);put("profile","worker");put("debugHttp",true);put("tokens",ManagementTokens("history-native-fixture","refresh-fixture","password","fixture-user",4102444800).json())})
            compose.runOnUiThread {management.bind(scope)};compose.waitUntil(5000) {management.state.value.signedIn}
            compose.runOnUiThread {management.refresh()};compose.waitUntil(10000) {!management.state.value.busy && management.state.value.loaded}
            val target=ManagementTarget(scope,dashboard,"worker","password · fixture-user")
            val own=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,scope,dashboard,"worker",target.actor,"Probe","saved-own-history-fixture","{}","confirmed","",1)
            runBlocking {app.database.withTransaction {
                app.database.dao().insertManagementOperation(own)
                repeat(105) {i ->app.database.dao().insertManagementOperation(own.copy(key=UUID.randomUUID().toString(),apiScope="b".repeat(64),actor="password · foreign-history-fixture",target="foreign-history-fixture",state="acknowledged",createdAt=100L+i))}
            }}
            try {
                compose.waitUntil(5000) {management.receipts.value.any {it.key==own.key}}
            } catch(error:Throwable) {
                val raw=runBlocking {app.database.dao().managementResourceOperations(target.resourceScope).first()}
                val stored=runBlocking {app.database.dao().managementOperations(scope).first()}
                throw AssertionError("Own history missing; raw page="+raw.size+", visible="+management.receipts.value.size+", stored own="+stored.count {it.key==own.key},error)
            }
            compose.runOnUiThread {compose.activity.setContent {val preferences by api.preferences.collectAsStateWithLifecycle();HermesAppTheme(preferences) {ManagementEntry(api,management)}}}
            compose.onNodeWithTag("management-open").performClick()
            compose.onNodeWithTag("management-list").performScrollToNode(hasTestTag("management-receipt-"+own.key))
            compose.onNodeWithTag("management-receipt-"+own.key).assertIsDisplayed()
            compose.onNodeWithText("foreign-history-fixture",substring=true,useUnmergedTree=true).assertDoesNotExist()
            assertEquals(0,writes.get());assertEquals(own,runBlocking {app.database.dao().managementOperation(own.key)})
        }
    }
}
