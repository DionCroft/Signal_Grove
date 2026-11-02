package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class SessionProtocolTest {
    @Test fun sessionOperationsUseExactPrefixedRoutesAndBearerWithoutRetries()=runBlocking {
        MockWebServer().use {server ->
            val api=OkHttpHermesApiClient(ServerConfig(server.url("p/work/").toString(),"fixture",true),true)
            for((action,body,suffix) in listOf(
                Triple(SessionAction.Lock,RuntimeChoice("model","provider",false).fields(),"api/sessions/api-parent/model"),
                Triple(SessionAction.Fork,buildJsonObject {put("id","api-child");put("title","Branch")},"api/sessions/api-parent/fork"),
                Triple(SessionAction.Steer,buildJsonObject {put("input","Review the tests first")},"v1/runs/api-parent/steer"))) {
                server.enqueue(MockResponse().setBody("{}"));api.sessionAction(action,"api-parent",body)
                val request=server.takeRequest();assertEquals("/p/work/$suffix",request.path);assertEquals("POST",request.method)
                assertEquals("Bearer fixture",request.getHeader("Authorization"));assertEquals(body,Json.parseToJsonElement(request.body.readUtf8()).jsonObject)
            }
            assertEquals(3,server.requestCount)
        }
    }
    @Test fun acknowledgementsMustMatchTheExactSessionBranchOrRun() {
        val lock=RuntimeChoice("m","p").fields()
        val ack=Json.parseToJsonElement("""{"object":"hermes.session.model_lock","session_id":"s","runtime":{"model":"m","provider":"p","model_lock":"accepted"}}""").jsonObject
        assertEquals("s",SessionProtocol.acknowledged(SessionAction.Lock,"s",lock,ack).text("session_id"))
        try {SessionProtocol.acknowledged(SessionAction.Lock,"different",lock,ack);fail()} catch(_:IllegalArgumentException) {}
        val fork=buildJsonObject {put("id","child");put("title","Branch")}
        try {SessionProtocol.acknowledged(SessionAction.Fork,"parent",fork,Json.parseToJsonElement("""{"session":{"id":"child","parent_session_id":"other"}}""").jsonObject);fail()} catch(_:IllegalArgumentException) {}
        val steer=buildJsonObject {put("input","Review")}
        val accepted=SessionProtocol.acknowledged(SessionAction.Steer,"r",steer,Json.parseToJsonElement("""{"object":"hermes.run.steer","run_id":"r","accepted":true}""").jsonObject)
        assertFalse(accepted.containsKey("status"))
    }
    @Test fun lateForkValidationAndUnknownErrorsStayAmbiguous() {
        assertFalse(SessionProtocol.definiteRefusal(SessionAction.Fork,HermesHttpException(400,"invalid_title")))
        assertTrue(SessionProtocol.definiteRefusal(SessionAction.Fork,HermesHttpException(409,"session_exists")))
        assertTrue(SessionProtocol.definiteRefusal(SessionAction.Steer,HermesHttpException(409,"run_not_accepting_steer")))
        assertFalse(SessionProtocol.definiteRefusal(SessionAction.Steer,HermesHttpException(500,"steer_failed")))
        try {SessionProtocol.validate(SessionAction.Steer,"r",buildJsonObject {put("input","x".repeat(16385))});fail()} catch(_:IllegalArgumentException) {}
    }
    @Test fun metadataProjectionKeepsOnlyBoundedClientSafeFields() {
        val raw=Json.parseToJsonElement("""{"id":"s","model":"m","input_tokens":42,"system_prompt":"private","model_config":{"api_key":"fixture-private"},"preview":"private preview"}""").jsonObject
        val safe=SessionProtocol.sessionSnapshot(raw,"s")
        assertEquals(setOf("id","model","input_tokens"),safe.keys)
    }
}
