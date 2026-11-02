package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Executors

class TurnRequestStoreTest {
    @get:Rule val temporary=TemporaryFolder()
    private fun request(input:String="Review")=TurnRequest("runs","v1/runs",buildJsonObject {
        put("session_id","api-original");put("input",buildJsonArray {add(buildJsonObject {put("type","text");put("text",input)});add(buildJsonObject {put("type","image_url");put("image_url",buildJsonObject {put("url","data:image/png;base64,fixture")})})})
        put("model","fixture-model");put("provider","fixture-provider");put("model_options",buildJsonObject {put("reasoning",buildJsonObject {put("enabled",false)})})
    },"mobile_fixture")
    @Test fun fullRequestSurvivesReopeningAndTheSameKeyCannotBeRebound() {
        val directory=temporary.newFolder();val original=request();val hash=TurnRequestStore(directory).freeze("scope","conversation","key",original)
        val reopened=TurnRequestStore(directory)
        assertEquals(original,reopened.read("scope","conversation","key",hash));assertEquals(hash,reopened.freeze("scope","conversation","key",original))
        try {reopened.freeze("scope","conversation","key",request("Changed"));fail("Rebinding a receipt")} catch(_:IllegalArgumentException) {}
        assertEquals(original,reopened.read("scope","conversation","key",hash))
    }
    @Test fun wrongScopeMissingOrCorruptPayloadCannotBeRebuilt() {
        val directory=temporary.newFolder();val store=TurnRequestStore(directory);val hash=store.freeze("a","conversation","key",request())
        try {store.read("b","conversation","key",hash);fail("Cross-scope read")} catch(_:IllegalArgumentException) {}
        val file=directory.walkTopDown().single {it.isFile};file.writeText("corrupt")
        try {store.read("a","conversation","key",hash);fail("Corrupt request")} catch(_:IllegalArgumentException) {}
        file.delete()
        try {store.read("a","conversation","key",hash);fail("Missing request")} catch(_:IllegalArgumentException) {}
    }
    @Test fun concurrentWritersChooseOneImmutableBodyAcrossStoreInstances() {
        val directory=temporary.newFolder();val executor=Executors.newFixedThreadPool(2)
        try {
            val results=listOf("One","Two").map {input -> executor.submit<Result<Pair<TurnRequest,String>>> {runCatching {val request=request(input);request to TurnRequestStore(directory).freeze("scope","conversation","key",request)}}}.map {it.get()}
            assertEquals(1,results.count {it.isSuccess})
            val (request,hash)=results.single {it.isSuccess}.getOrThrow()
            assertEquals(request,TurnRequestStore(directory).read("scope","conversation","key",hash));assertEquals(1,directory.walkTopDown().count {it.isFile})
        } finally {executor.shutdownNow()}
    }
    @Test fun oversizedRequestsFailBeforeAReceiptIsWritten() {
        val directory=temporary.newFolder()
        try {TurnRequestStore(directory,64).freeze("scope","conversation","key",request());fail("Oversized request")} catch(_:RequestTooLarge) {}
        assertTrue(directory.listFiles()!!.isEmpty())
    }
    @Test fun deletingAConversationAndClearingCachesRemoveAllItsRequestBodies() {
        val directory=temporary.newFolder();val store=TurnRequestStore(directory)
        store.freeze("scope","a","one",request());store.freeze("scope","a","two",request())
        val retained=store.freeze("scope","b","three",request())
        store.deleteConversation("scope","a")
        assertEquals(1,directory.walkTopDown().count {it.isFile})
        assertEquals(request(),store.read("scope","b","three",retained))
        store.clearAll();assertTrue(directory.listFiles()!!.isEmpty())
    }
    @Test fun compatibilityHistoryIsFrozenWithItsOriginalModelAndRoleOrder() {
        val c=Conversation("c","scope","api",pendingInput="Translate",transport="compat")
        val history=listOf(ChatMessage("1","c","user","Color"),ChatMessage("2","c","assistant","Teal",status="response_finished"),ChatMessage("3","c","user","Translate"))
        val request=TurnRequest.create(c,history,"original-model")
        assertEquals("original-model",request.body.text("model"));assertEquals(listOf("user","assistant","user"),request.body["messages"]!!.jsonArray.map {it.jsonObject.text("role")})
        assertEquals("",request.channelKey)
    }
}
