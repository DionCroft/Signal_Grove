package org.hermesmobile.app

import android.graphics.*
import android.media.ExifInterface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
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
import java.io.*

class ImageHistoryTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=compose.activity.application as HermesApplication
    private val vm get()=ViewModelProvider(compose.activity)[HermesViewModel::class.java]
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    @After fun reset() {compose.waitUntil(10000) {!vm.connecting.value && !vm.busy.value};compose.runOnUiThread {vm.forgetServer()};compose.waitUntil(10000) {!vm.connecting.value && vm.preferences.value.demo}}
    private fun png():ByteArray {
        val bitmap=Bitmap.createBitmap(32,64,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {drawColor(Color.BLUE);drawRect(0f,0f,16f,64f,Paint().apply {color=Color.RED})}
        return ByteArrayOutputStream().use {output ->bitmap.compress(Bitmap.CompressFormat.PNG,100,output);bitmap.recycle();output.toByteArray()}
    }
    @Test fun normalizationResizesOrientsAndRemovesMetadataAndTransparency() {
        val source=Bitmap.createBitmap(3200,1600,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.RED)}
        val file=File.createTempFile("image-fixture-",".jpg",app.cacheDir)
        try {
            file.outputStream().use {source.compress(Bitmap.CompressFormat.JPEG,90,it)};source.recycle()
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString())
                setAttribute(ExifInterface.TAG_GPS_LATITUDE,"1/1,2/1,3/1");setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF,"N");saveAttributes()
            }
            val normalized=ImageCodec.normalize(file.readBytes());assertEquals(400,normalized.width);assertEquals(800,normalized.height);assertTrue(normalized.bytes.size<=ImageContent.MAX_IMAGE_BYTES)
            assertNull(ExifInterface(ByteArrayInputStream(normalized.bytes)).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            val transparent=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
            val input=ByteArrayOutputStream().also {transparent.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();transparent.recycle()
            val opaque=ImageCodec.normalize(input).bytes;val decoded=BitmapFactory.decodeByteArray(opaque,0,opaque.size)
            assertTrue(Color.red(decoded.getPixel(0,0))>245);decoded.recycle()
            assertThrows(IllegalArgumentException::class.java) {ImageCodec.normalize(byteArrayOf(1,2,3))}
            assertThrows(IllegalArgumentException::class.java) {ImageCodec.readBounded(ByteArrayInputStream(ByteArray(ImageContent.MAX_SOURCE_BYTES+1)))}
        } finally {file.delete()}
    }
    @Test fun inlineHistoryIsCachedOutsideRowsAndRemoteImagesAreNotFetchedAutomatically() {
        val data="data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(png())
        MockWebServer().use {external -> MockWebServer().use {server ->
            val remote=external.url("/image").toString()
            server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse=when {
                request.path=="/v1/capabilities"->json("""{"features":{"session_resources":true,"session_chat":true},"endpoints":{}}""")
                request.path.orEmpty().contains("/api/jobs?")->json("""{"jobs":[]}""")
                request.path.orEmpty().startsWith("/api/sessions?")->json("""{"data":[{"id":"api_image","title":"Image fixture"}],"has_more":false}""")
                request.path.orEmpty().startsWith("/api/sessions/api_image/messages?")->json(buildJsonObject {
                    put("session_id","api_image");put("data",buildJsonArray {add(buildJsonObject {put("id","image-message");put("role","user");put("content",buildJsonArray {
                        add(buildJsonObject {put("type","text");put("text","Synthetic image history")})
                        listOf(data,remote).forEach {url->add(buildJsonObject {put("type","image_url");put("image_url",buildJsonObject {put("url",url)})})}
                    })})})
                }.toString())
                else->json("{}").setResponseCode(404)
            }}
            compose.waitUntil(10000) {!vm.connecting.value};val url=server.url("/").toString()
            compose.runOnUiThread {vm.updatePreferences(vm.preferences.value.copy(chinese=true,pinyin=true));vm.configure(url,"image-history-fixture",true)}
            compose.waitUntil(10000) {!vm.connecting.value && vm.preferences.value.chinese && vm.preferences.value.pinyin && vm.conversations.value.any {it.remoteId=="api_image"}}
            val c=vm.conversations.value.first {it.remoteId=="api_image"}
            compose.runOnUiThread {vm.select(c.key)}
            compose.waitUntil(10000) {!vm.historyLoading.value && vm.messages.value.any {it.imagesJson.contains("hash")}}
            val message=vm.messages.value.single();assertEquals("Synthetic image history",message.content);assertTrue(message.imagesJson.length<1000);assertFalse(message.imagesJson.contains("base64"))
            val refs=ImageContent.refs(message.imagesJson);assertEquals(2,refs.size);assertTrue(refs.first().hash.isNotBlank());assertEquals(remote,refs.last().url)
            compose.onAllNodesWithContentDescription(Words.Chat.localized(vm.preferences.value),useUnmergedTree=true).onLast().performClick()
            compose.waitUntil(5000) {compose.onAllNodesWithTag("image-preview-0").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("chat-messages").performScrollToNode(hasTestTag("image-preview-0"))
            compose.onNodeWithTag("image-preview-0").assertIsDisplayed();assertEquals(0,external.requestCount)
            compose.onNodeWithTag("image-preview-0").performClick()
            compose.onNodeWithTag("expanded-image-preview").assertIsDisplayed()
            val expanded=File(compose.activity.getExternalFilesDir(null),"qa/inline-image-expanded.png");expanded.parentFile!!.mkdirs()
            expanded.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithText(Words.Close.localized(vm.preferences.value)).performClick()
            val screenshot=File(compose.activity.getExternalFilesDir(null),"qa/inline-image-history.png");screenshot.parentFile!!.mkdirs()
            screenshot.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            val archived=TurnRequestStore(File(app.cacheDir,"image-request-fixture"))
            val receipt=c.copy(transport="runs",pendingKey="image-key",pendingInput=message.content)
            val user=message.copy(id="image-key_user",imagesJson=ImageContent.encode(listOf(refs.first())))
            val request=TurnRequest.create(receipt,listOf(user),"vision") {m -> app.images.content(c.scope,c.key,m)}
            val hash=archived.freeze(c.scope,c.key,receipt.pendingKey,request)
            app.images.blobs.deleteConversation(c.scope,c.key)
            assertEquals(request,archived.read(c.scope,c.key,receipt.pendingKey,hash));archived.clearAll()
        } }
    }
    @Test fun schemaSevenPreservesAccountingAndAddsEmptyImageReferences()=runBlocking {
        val name="image-upgrade7.db";app.deleteDatabase(name)
        val schema=Json.parseToJsonElement(InstrumentationRegistry.getInstrumentation().context.assets.open("org.hermesmobile.app.data.HermesDatabase/7.json").bufferedReader().use {it.readText()}).jsonObject["database"]!!.jsonObject
        app.getDatabasePath(name).parentFile!!.mkdirs();val placeholder=36.toChar().toString()+"{TABLE_NAME}"
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(app.getDatabasePath(name),null).use {old ->
            schema["entities"]!!.jsonArray.forEach {entry -> val e=entry.jsonObject;val table=e["tableName"]!!.jsonPrimitive.content
                old.execSQL(e["createSql"]!!.jsonPrimitive.content.replace(placeholder,table));e["indices"]?.jsonArray.orEmpty().forEach {old.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace(placeholder,table))}}
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");old.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema["identityHash"]!!.jsonPrimitive.content))
            old.execSQL("INSERT INTO messages VALUES('m','c','assistant','Saved',1,'completed','{\"usage\":{\"input_tokens\":12}}')")
            old.execSQL("INSERT INTO run_reports VALUES('r','c','run','pending','runs','{\"usage\":{\"input_tokens\":12}}','completed',1)");old.version=7
        }
        val db=Room.databaseBuilder(app,HermesDatabase::class.java,name).addMigrations(*ALL_HERMES_MIGRATIONS).build()
        try {assertEquals("Saved",db.dao().message("m")!!.content);assertEquals("",db.dao().message("m")!!.imagesJson);assertTrue(db.dao().report("r")!!.telemetryJson.contains("12"))}
        finally {db.close();app.deleteDatabase(name)}
    }
}
