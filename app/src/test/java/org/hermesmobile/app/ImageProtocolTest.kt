package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImageProtocolTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun structuredHistoryKeepsImagesWhileLiteralJsonTextStaysLiteral() {
        val raw="""[{"type":"text","text":"Look"},{"type":"image_url","image_url":{"url":"https://images.invalid/a.png"}},{"type":"input_image","image_url":"data:image/png;base64,AQID"}]"""
        val content=ImageContent.parse(Json.parseToJsonElement(raw));assertEquals("Look",content.text);assertEquals(2,content.sources.size)
        assertEquals(raw,ImageContent.parse(JsonPrimitive(raw)).text)
        assertNull(ImageContent.remoteUrl("file:///tmp/image.png"));assertNull(ImageContent.remoteUrl("https://user:password@example.invalid/a"))
        assertThrows(IllegalArgumentException::class.java) {ImageContent.parse(buildJsonArray {repeat(9) {add(buildJsonObject {put("type","image_url");put("image_url","https://images.invalid/a")})}})}
    }
    @Test fun immutablePrivateBlobsAreScopeBoundAndCorruptionIsDetected() {
        val directory=temporary.newFolder();val store=ImageBlobStore(directory);val data=byteArrayOf(1,2,3)
        val ref=store.write("scope-a","conversation",data,2,2);assertArrayEquals(data,store.read("scope-a","conversation",ref))
        assertEquals(ref,ImageBlobStore(directory).write("scope-a","conversation",data,2,2))
        assertThrows(IllegalArgumentException::class.java) {store.read("scope-b","conversation",ref)}
        val copied=store.copy("scope-a","conversation","branch",ref);assertArrayEquals(data,store.read("scope-a","branch",copied))
        store.deleteConversation("scope-a","conversation");assertArrayEquals(data,store.read("scope-a","branch",copied))
        directory.walkTopDown().first {it.extension=="image"}.writeBytes(byteArrayOf(9));assertThrows(IllegalArgumentException::class.java) {store.read("scope-a","branch",copied)}
        store.clearAll();assertTrue(directory.listFiles()!!.isEmpty())
    }
    @Test fun metadataAndSourceLimitsNeverPermitInlineBytesInsideReferenceRows() {
        val ref=ImageRef("a".repeat(64),width=10,height=20)
        assertEquals(listOf(ref),ImageContent.refs(ImageContent.encode(listOf(ref))))
        assertTrue(ImageContent.encode(listOf(ref)).length<300)
        assertThrows(IllegalArgumentException::class.java) {ImageContent.refs("x".repeat(100001))}
        assertThrows(IllegalArgumentException::class.java) {ImageContent.parts("Look",listOf(ImageRef(unavailable=true))) {byteArrayOf()}}
        assertThrows(IllegalArgumentException::class.java) {ImageBlobStore(temporary.newFolder()).write("a","b",ByteArray(ImageContent.MAX_IMAGE_BYTES+1),1,1)}
    }
    @Test fun eachTransportUsesItsVerifiedImageEnvelopeAndFrozenReplayKeepsTheBytes() {
        val bytes=byteArrayOf(1,2,3);val ref=ImageRef("a".repeat(64),width=1,height=1)
        val user=ChatMessage("key_user","c","user","Look",imagesJson=ImageContent.encode(listOf(ref)))
        val folder=temporary.newFolder();val store=TurnRequestStore(folder)
        listOf("runs","session","compat").forEach {transport ->
            val c=Conversation("c","scope","api",pendingKey="key",pendingInput="Look",transport=transport)
            val request=TurnRequest.create(c,listOf(user),"default",RuntimeChoice("vision","provider")) {m->ImageContent.parts(m.content,ImageContent.refs(m.imagesJson)) {bytes}}
            val parts=when(transport) {"runs"->request.body["input"]!!.jsonArray.single().jsonObject["content"]!!;"session"->request.body["input"]!!;else->request.body["messages"]!!.jsonArray.single().jsonObject["content"]!!}.jsonArray
            assertEquals("data:image/jpeg;base64,AQID",parts.last().jsonObject["image_url"]!!.jsonObject.text("url"))
            val hash=store.freeze(c.scope,c.key,transport,request)
            assertEquals(request,TurnRequestStore(folder).read(c.scope,c.key,transport,hash))
        }
    }
    @Test fun largeHistoryPageIsRetriedOnlyAsSmallerReadOnlyGets()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setHeader("Content-Length","11000000").setBody("too large").setHeader("Content-Length","11000000"))
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"session_id":"api","data":[{"id":"m","role":"user","content":[{"type":"text","text":"Look"},{"type":"image_url","image_url":{"url":"https://images.invalid/a"}}]}]}"""))
            val client=OkHttpHermesApiClient(ServerConfig(server.url("/p/work/").toString(),"image-fixture",true),true)
            val rows=client.history("api").rows;assertEquals(1,rows.size);assertEquals("Look",rows.single().content);assertEquals(listOf("https://images.invalid/a"),rows.single().images)
            val first=server.takeRequest();val second=server.takeRequest();assertEquals("GET",first.method);assertEquals("GET",second.method)
            assertTrue(first.path!!.startsWith("/p/work/api/sessions/api/messages?"));assertTrue(first.path!!.contains("limit=500"));assertTrue(second.path!!.contains("limit=50"))
            assertEquals("Bearer image-fixture",second.getHeader("Authorization"));assertEquals(2,server.requestCount)
        }
    }
}
