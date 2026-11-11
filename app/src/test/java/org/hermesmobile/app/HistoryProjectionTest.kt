package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.hermesmobile.app.ui.*
import org.junit.Assert.*
import org.junit.Test

class HistoryProjectionTest {
    private fun client(server:MockWebServer)=OkHttpHermesApiClient(ServerConfig(server.url("p/work/").toString(),"history-fixture-token",true),true)
    private fun row(id:Int,hidden:Boolean=false)=buildJsonObject {put("id",id);put("role","assistant");put("content",if(hidden) "private-hidden-fixture" else "Reply "+id);if(hidden) put("display_kind","hidden");put("reasoning_content","private-reasoning-fixture")}
    private fun page(id:String,rows:List<JsonObject>,offset:Int=0)=buildJsonObject {put("session_id",id);put("data",JsonArray(rows));put("pagination",buildJsonObject {put("limit",500);put("offset",offset);put("order","oldest");put("returned",rows.size)})}
    private fun MockWebServer.enqueueJson(value:JsonObject)=enqueue(MockResponse().setHeader("Content-Type","application/json").setBody(value.toString()))
    @Test fun hiddenHumanAndAssistantRowsAndReasoningNeverBecomeChatMessages()=runBlocking {
        MockWebServer().use {s->
            val user=JsonObject(row(2,true)+("role" to JsonPrimitive("user")))
            s.enqueueJson(page("saved",listOf(row(1,true),user,row(3))))
            val result=client(s).history("saved")
            assertEquals(listOf("3"),result.rows.map {it.id});assertEquals("Reply 3",result.rows.single().content)
            assertFalse(result.rows.toString().contains("private-hidden-fixture"));assertFalse(result.rows.toString().contains("private-reasoning-fixture"))
            assertEquals(1,s.requestCount)
        }
    }
    @Test fun hiddenRowsCountTowardWireOffsetsAndDoNotEndTheReadEarly()=runBlocking {
        MockWebServer().use {s->
            s.enqueueJson(page("saved",(1..500).map {row(it,true)}));s.enqueueJson(page("saved",listOf(row(501)),500))
            assertEquals(listOf("501"),client(s).history("saved").rows.map {it.id})
            assertTrue(s.takeRequest().path!!.contains("offset=0"));val last=s.takeRequest()
            assertEquals("/p/work/api/sessions/saved/messages?inline_images=false&limit=500&offset=500&order=oldest",last.path)
            assertEquals("Bearer history-fixture-token",last.getHeader("Authorization"));assertNull(last.getHeader("Cookie"));assertEquals("GET",last.method)
        }
    }
    @Test fun firstResolvedContinuationRemainsSupportedAcrossLaterPages()=runBlocking {
        MockWebServer().use {s->
            s.enqueueJson(page("saved-tip",(1..500).map {row(it)}));s.enqueueJson(page("saved-tip",listOf(row(501)),500))
            val result=client(s).history("saved-root");assertEquals("saved-tip",result.sessionId);assertEquals(501,result.rows.size)
            assertTrue(s.takeRequest().path!!.contains("/saved-root/"));assertTrue(s.takeRequest().path!!.contains("/saved-tip/"))
        }
    }
    @Test fun changingContinuationDuringPagingFailsWithoutReturningAMixedTranscript()=runBlocking {
        MockWebServer().use {s->
            s.enqueueJson(page("saved-tip",(1..500).map {row(it)}));s.enqueueJson(page("other-tip",listOf(row(501)),500))
            val failure=runCatching {client(s).history("saved-root")}.exceptionOrNull();assertTrue(failure is HistoryReadChanged)
            assertEquals(Words.HistoryChanged.en,historyReadError(failure!!,AppPreferences(chinese=false,pinyin=false)));assertEquals(2,s.requestCount)
        }
    }
    @Test fun missingOrMalformedDataCannotMasqueradeAsAnEmptySuccessfulHistory()=runBlocking {
        for(data in listOf(null,JsonNull,JsonPrimitive("private-fixture"),JsonArray(listOf(JsonPrimitive(1))))) MockWebServer().use {s->
            s.enqueueJson(buildJsonObject {put("session_id","saved");data?.let {put("data",it)}})
            val failure=runCatching {client(s).history("saved")}.exceptionOrNull();assertTrue(failure is HistoryReadInvalid);assertEquals(1,s.requestCount)
        }
    }
    @Test fun contradictoryPaginationIsRefusedWithoutRetrying()=runBlocking {
        for((key,value) in listOf("offset" to JsonPrimitive(1),"limit" to JsonPrimitive(50),"order" to JsonPrimitive("latest"),"returned" to JsonPrimitive(2))) MockWebServer().use {s->
            val raw=page("saved",listOf(row(1)));s.enqueueJson(JsonObject(raw+("pagination" to JsonObject(raw.getValue("pagination").jsonObject+(key to value)))))
            assertTrue(runCatching {client(s).history("saved")}.exceptionOrNull() is HistoryReadInvalid);assertEquals(1,s.requestCount)
        }
    }
    @Test fun malformedJsonDoesNotExposeResponseTextOrRetry()=runBlocking {
        MockWebServer().use {s->
            s.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("{\"data\":[{\"content\":\"private-malformed-history-fixture"))
            val error=runCatching {client(s).history("saved")}.exceptionOrNull()
            assertTrue(error is HistoryReadInvalid);assertFalse(error.toString().contains("private-malformed-history-fixture"));assertNull(error!!.cause)
            assertEquals(Words.HistoryInvalid.en,historyReadError(error,AppPreferences(chinese=false,pinyin=false)));assertEquals(1,s.requestCount)
        }
    }
    @Test fun malformedDisplayKindIsNotCoercedOrRendered()=runBlocking {
        for(kind in listOf(JsonPrimitive(true),JsonObject(emptyMap()),JsonPrimitive("bad\nkind"))) MockWebServer().use {s->
            s.enqueueJson(page("saved",listOf(JsonObject(row(1)+("display_kind" to kind)))))
            val failure=runCatching {client(s).history("saved")}.exceptionOrNull();assertTrue(failure is HistoryReadInvalid)
            assertFalse(failure.toString().contains("private-fixture"));assertEquals(1,s.requestCount)
        }
    }
    @Test fun legacyOptionalMetadataAndIndependentLanguageChoicesArePreserved()=runBlocking {
        MockWebServer().use {s->
            s.enqueueJson(buildJsonObject {put("data",JsonArray(listOf(JsonObject(row(1)+("display_kind" to JsonNull)))))})
            assertEquals("saved",client(s).history("saved").sessionId)
        }
        for((failure,word) in listOf(HistoryReadChanged() to Words.HistoryChanged,HistoryReadInvalid() to Words.HistoryInvalid)) {
            val result=historyReadError(failure,AppPreferences(chinese=true,pinyin=true));assertTrue(result.contains(word.en));assertTrue(result.contains(word.zh));assertTrue(result.contains(word.pin))
        }
    }
}
