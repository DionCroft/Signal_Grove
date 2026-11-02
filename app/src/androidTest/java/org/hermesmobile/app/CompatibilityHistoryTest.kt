package org.hermesmobile.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.ui.HermesViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Compatibility responses remain useful history without asserting run success. */
class CompatibilityHistoryTest {
    @get:Rule(order=0) val credentialFixture=CredentialFixtureRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private val app get()=compose.activity.application as HermesApplication
    @After fun reset() {compose.runOnUiThread {vm.forgetServer()};compose.waitUntil(10000) {vm.preferences.value.demo && !vm.controlBusy.value && !vm.connecting.value}}
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    private fun connect(server:MockWebServer,path:String,token:String) {
        compose.waitUntil(10000) {!vm.connecting.value}
        val url=server.url(path).toString()
        compose.runOnUiThread {vm.configure(url,token,true)}
        compose.waitUntil(10000) {!vm.connecting.value && vm.connectionStatus.value=="connected" && vm.preferences.value.url==url}
    }
    @Test fun compatibilityFollowUpIncludesThePreviousAssistantAnswer() {
        val bodies=mutableListOf<JsonObject>()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when(request.path) {
                    "/compat/v1/capabilities"->json("""{"model":"fixture","features":{},"endpoints":{}}""")
                    "/compat/api/jobs?include_disabled=true"->json("""{"jobs":[]}""")
                    "/compat/v1/chat/completions"->{
                        synchronized(bodies) {bodies+=Json.parseToJsonElement(request.body.readUtf8()).jsonObject}
                        MockResponse().setHeader("Content-Type","text/event-stream").setBody("data: {\"choices\":[{\"delta\":{\"content\":\"The color is teal.\"},\"finish_reason\":null}]}\n\ndata: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n")
                    }
                    else->json("{}").setResponseCode(404)
                }
            }
            connect(server,"compat/","audit-compat-fixture")
            compose.runOnUiThread {vm.newChat();vm.send("Name one color")}
            compose.waitUntil(10000) {!vm.busy.value && vm.runStatus.value=="response_finished"}
            assertTrue(runBlocking {app.database.dao().history(vm.selected.value).any {it.role=="assistant" && it.content=="The color is teal."}})
            compose.runOnUiThread {vm.send("Translate your previous answer")}
            compose.waitUntil(10000) {!vm.busy.value && synchronized(bodies) {bodies.size==2}}
            val roles=bodies.last()["messages"]!!.jsonArray.map {it.jsonObject.text("role")}
            // Preserve the answer in exact role order on the next turn.
            assertEquals(listOf("user","assistant","user"),roles)
            assertEquals("The color is teal.",bodies.last()["messages"]!!.jsonArray[1].jsonObject.text("content"))
        }
    }
}
