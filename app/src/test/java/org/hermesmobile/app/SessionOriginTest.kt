package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class SessionOriginTest {
    private fun conversation(source:String?,transport:String="runs")=Conversation("c","scope","saved",pendingInput="hello",transport=transport,detailsJson=source?.let {SessionOrigin.retain("",it)}.orEmpty())
    private fun client(server:MockWebServer)=OkHttpHermesApiClient(ServerConfig(server.url("p/work/").toString(),"fixture",true),true)
    @Test fun allOriginsRemainVisibleWithoutLosingProfilePrefixOrPagination()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"api","title":"Phone","source":"api_server"},{"id":"desk","title":"Desktop","source":"desktop"},{"id":"disc","title":"Discord","source":"discord"}],"has_more":true}"""))
            val page=client(server).sessionPage(50,50)
            assertEquals(listOf("api_server","desktop","discord"),page.rows.map {it.source});assertEquals(100,page.nextOffset);assertTrue(page.hasMore)
            val request=server.takeRequest();assertEquals("/p/work/api/sessions?limit=50&offset=50",request.path);assertEquals("Bearer fixture",request.getHeader("Authorization"));assertNull(request.requestUrl!!.queryParameter("source"))
        }
    }
    @Test fun newlyMissingOriginIsUnknownAndCannotAcquireWriteAuthority()=runBlocking<Unit> {
        MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"older","title":"No origin"}],"has_more":false}"""))
            val remote=client(server).sessionPage().rows.single();assertEquals(SessionOrigin.Unknown,remote.source)
            assertThrows(ReadOnlySessionException::class.java) {TurnRequest.create(conversation(remote.source),emptyList(),"model")}
        }
    }
    @Test fun malformedWireSourcesRejectThePageInsteadOfBecomingApiOwned()=runBlocking {
        for(value in listOf("42","{}","[]","true","\"desktop\nunsafe\"")) MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("{\"data\":[{\"id\":\"bad\",\"source\":"+value+"}]}"))
            try {client(server).sessionPage();fail("Untrusted source accepted")} catch(_:IllegalArgumentException) {}
        }
    }
    @Test fun everyForeignOriginIsBlockedAcrossEveryRequestTransport() {
        for(source in listOf("desktop","discord","cli","oneshot","new_surface","unknown")) for(transport in listOf("runs","session","compat")) {
            assertThrows(ReadOnlySessionException::class.java) {TurnRequest.create(conversation(source,transport),emptyList(),"model")}
        }
    }
    @Test fun knownApiOriginKeepsTheExplicitRequestedRuntime() {
        val body=TurnRequest.create(conversation("api_server"),emptyList(),"default",RuntimeChoice("requested","provider")).body
        assertEquals("requested",body.text("model"));assertEquals("provider",body.text("provider"));assertEquals("saved",body.text("session_id"))
    }
    @Test fun legacyApiOnlyCachesKeepTheirPreviousBehavior() {
        assertFalse(SessionOrigin.readOnly(conversation(null)))
        assertEquals("saved",TurnRequest.create(conversation(null),emptyList(),"model").body.text("session_id"))
    }
    @Test fun inventoryOriginPreservesExistingPrivateMetadata() {
        val original="""{"id":"saved","model":"private-model","_fetched_at":123}"""
        val updated=Json.parseToJsonElement(SessionOrigin.retain(original,"desktop")).jsonObject
        assertEquals("private-model",updated.text("model"));assertEquals(JsonPrimitive(123),updated["_fetched_at"]);assertEquals("desktop",updated.text("source"))
    }
    @Test fun incompleteMetadataRefreshCannotDiscardAnEstablishedForeignOrigin() {
        val original=SessionOrigin.retain("","discord");val result=SessionOrigin.mergeDetails(original,buildJsonObject {put("id","saved");put("model","updated")})
        assertTrue(SessionOrigin.readOnly(conversation(null).copy(detailsJson=result)));assertEquals("discord",Json.parseToJsonElement(result).jsonObject.text("source"))
    }
    @Test fun explicitNullOrBlankMetadataSourceRemainsUnknown() {
        for(value in listOf(JsonNull,JsonPrimitive(""))) {
            val result=SessionOrigin.mergeDetails("",buildJsonObject {put("source",value)})
            assertEquals(SessionOrigin.Unknown,SessionOrigin.source(conversation(null).copy(detailsJson=result)))
        }
    }
    @Test fun malformedCachedMetadataDoesNotGainLegacyWriteAccess() {
        for(raw in listOf("broken","[]","{\"source\":null}","{\"source\":42}")) assertTrue(SessionOrigin.readOnly(conversation(null).copy(detailsJson=raw)))
    }
    @Test fun sourceBoundAndControlCharactersAreEnforced() {
        for(value in listOf("x".repeat(129),"desktop\nunsafe")) assertThrows(IllegalArgumentException::class.java) {SessionOrigin.wire(buildJsonObject {put("source",value)})}
    }
    @Test fun createAcknowledgementCannotRedirectTheNewTurnIntoAnotherSession()=runBlocking {
        for(body in listOf("""{"session":{"id":"foreign","source":"api_server"}}""","""{"session":{"id":"requested","source":"desktop"}}""")) MockWebServer().use {server->
            server.enqueue(MockResponse().setBody(body));try {client(server).createSession("requested","Title");fail("Foreign acknowledgement accepted")} catch(_:IllegalArgumentException) {}
            assertEquals(1,server.requestCount)
        }
    }
}
