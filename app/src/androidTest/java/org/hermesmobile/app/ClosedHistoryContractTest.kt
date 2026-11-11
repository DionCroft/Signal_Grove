package org.hermesmobile.app

import android.os.Build
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class ClosedHistoryContractTest {
    private class Fixture:Closeable {
        val server=MockWebServer()
        val keys=mapOf("default" to "fixture-default-key","field" to "fixture-field-key")
        init {
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    check(request.method=="GET" && request.getHeader("Cookie")==null)
                    val url=request.requestUrl!!;val parts=url.pathSegments
                    check(parts.take(2)==listOf("history","p"));val profile=parts[2];check(profile in keys)
                    fun json(body:JsonObject,status:Int=200)=MockResponse().setResponseCode(status).setHeader("Content-Type","application/json").setBody(body.toString())
                    if(request.getHeader("Authorization")!="Bearer "+keys.getValue(profile)) return json(buildJsonObject {put("error",buildJsonObject {put("code","gateway_auth_failed")})},401)
                    check(parts.take(5)==listOf("history","p",profile,"api","sessions"))
                    if(parts.size==5) {
                        val offset=url.queryParameter("offset")!!.toInt();check(url.queryParameter("limit")=="1" && offset in 0..2)
                        val id=if(offset==0) "shared-closed-fixture" else profile+"-only-fixture"
                        return json(buildJsonObject {put("data",JsonArray(if(offset==2) emptyList() else listOf(buildJsonObject {put("id",id);put("title","Fixture");put("source","desktop")})));put("has_more",offset<2)})
                    }
                    check(parts.size==7 && parts.last()=="messages")
                    if(parts[5]!="shared-closed-fixture") return json(buildJsonObject {put("error",buildJsonObject {put("code","session_not_found")})},404)
                    val offset=url.queryParameter("offset")!!.toInt();check(offset in listOf(0,500) && url.queryParameter("limit")=="500" && url.queryParameter("order")=="oldest" && url.queryParameter("inline_images")=="false")
                    val range=if(offset==0) 1..500 else 501..501
                    val rows=range.map {i->buildJsonObject {put("id",i);put("role",if(i==1) "user" else "assistant");put("timestamp",100+i)
                        put("content",when(i) {1->profile+" saved user";501->profile+" saved reply";else->"private-hidden-fixture-"+profile})
                        if(i in 2..500) put("display_kind","hidden")}}
                    return json(buildJsonObject {put("session_id","shared-closed-fixture");put("data",JsonArray(rows));put("pagination",buildJsonObject {put("limit",500);put("offset",offset);put("order","oldest");put("returned",rows.size)})})
                }
            };server.start()
        }
        override fun close() {server.close()}
    }
    @Test fun closedSavedHistoryUsesRealApiPagingAndRetainsProfileReadOnlyCaches() {
        val args=InstrumentationRegistry.getArguments();val external=args.getString("signalGroveOwnerHistoryBase")
        val label=args.getString("signalGroveOwnerHistoryLabel") ?: "mock";require(label.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,80}")))
        val commit=args.getString("signalGroveOwnerHistoryCommit") ?: "mock"
        if(external!=null) require(commit in setOf("2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19","3b421578588243f920886e6fe79a6faa87ea026e"))
        val fixture=if(external==null) Fixture() else null
        val base=(external ?: fixture!!.server.url("/history/").newBuilder().host("127.0.0.1").build().toString()).toHttpUrl()
        require(base.scheme=="http" && base.host=="127.0.0.1" && base.port in 1024..65535 && base.encodedPath=="/history/" && base.query==null && base.fragment==null && base.username.isEmpty() && base.password.isEmpty())
        val keys=if(fixture!=null) fixture.keys else mapOf("default" to checkNotNull(args.getString("signalGroveOwnerHistoryDefaultKey")),"field" to checkNotNull(args.getString("signalGroveOwnerHistoryFieldKey")))
        assertNotEquals(keys.getValue("default"),keys.getValue("field"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val credentials=context.getSharedPreferences("credentials",0);val originalCredentials=credentials.all.toMap()
        val databaseName="closed-history-fixture-"+UUID.randomUUID()+".db";check(!context.getDatabasePath(databaseName).exists())
        var db=Room.databaseBuilder(context,HermesDatabase::class.java,databaseName).build()
        val checks=mutableListOf<String>();val configs=keys.mapValues {(name,key)->ServerConfig(base.newBuilder().addPathSegments("p/"+name+"/").build().toString(),key,true)}
        val conversations=mutableMapOf<String,Conversation>();var complete=false;var errorType:String?=null
        try {
            runBlocking {withTimeout(180_000) {
                for(profile in listOf("default","field")) {
                    val config=configs.getValue(profile);val api=OkHttpHermesApiClient(config,true)
                    val first=api.sessionPage(0,1);val second=api.sessionPage(1,1);val last=api.sessionPage(2,1)
                    // The real API's full-page flag is optimistic, not an exact total.
                    assertTrue(first.hasMore);assertTrue(second.hasMore);assertFalse(last.hasMore);assertTrue(last.rows.isEmpty())
                    assertEquals(setOf("shared-closed-fixture",profile+"-only-fixture"),(first.rows+second.rows).map {it.id}.toSet())
                    assertTrue((first.rows+second.rows).all {it.source=="desktop"})
                    val c=Conversation(config.scope+":shared-closed-fixture",config.scope,"shared-closed-fixture",detailsJson=SessionOrigin.retain("{}","desktop"))
                    conversations[profile]=c;db.dao().save(c)
                    db.dao().saveMessages(listOf(ChatMessage("old-hidden-"+profile,c.key,"assistant","private-hidden-fixture-"+profile,1)))
                    HermesSessionRepository(db.dao()).importMessages(api,c)
                    val rows=db.dao().history(c.key)
                    assertEquals(listOf(profile+" saved user",profile+" saved reply"),rows.map {it.content})
                    assertEquals(listOf("user","assistant"),rows.map {it.role});assertTrue(rows.none {it.content.contains("private-hidden-fixture") || it.content.contains("private-reasoning")})
                }
                checks+="bounded_inventory_and_closed_origin_pages_use_each_profile_prefix"
                checks+="complete_visible_history_replaces_cache_without_hidden_rows"
                assertNotEquals(configs.getValue("default").scope,configs.getValue("field").scope)
                for((name,c) in conversations) assertEquals(listOf(name+" saved user",name+" saved reply"),db.dao().history(c.key).map {it.content})
                checks+="same_remote_id_stays_in_two_separate_profile_caches"
                val field=configs.getValue("field")
                val wrong=OkHttpHermesApiClient(field.copy(token=keys.getValue("default")),true)
                assertEquals(401,(runCatching {wrong.history("shared-closed-fixture")}.exceptionOrNull() as HermesHttpException).code)
                val api=OkHttpHermesApiClient(field,true)
                assertEquals(404,(runCatching {api.history("default-only-fixture")}.exceptionOrNull() as HermesHttpException).code)
                for((name,c) in conversations) assertEquals(listOf(name+" saved user",name+" saved reply"),db.dao().history(c.key).map {it.content})
                checks+="wrong_key_and_foreign_id_refusals_leave_both_caches_unchanged"
                db.close();db=Room.databaseBuilder(context,HermesDatabase::class.java,databaseName).build()
                for((name,c) in conversations) {
                    assertEquals("shared-closed-fixture",db.dao().conversation(c.key)!!.remoteId)
                    assertEquals(listOf(name+" saved user",name+" saved reply"),db.dao().history(c.key).map {it.content})
                }
                checks+="real_room_reopen_retains_exact_profile_history_and_identity"
                for(c in conversations.values) assertTrue(runCatching {SessionOrigin.requireWritable(db.dao().conversation(c.key)!!)}.exceptionOrNull() is ReadOnlySessionException)
                checks+="foreign_surface_conversations_stay_read_only_without_work"
                assertEquals(originalCredentials,credentials.all);checks+="saved_credentials_unchanged_and_no_model_or_worker_execution_claim"
            }};complete=true
        } catch(error:Throwable) {errorType=error.javaClass.simpleName;throw error}
        finally {
            db.close();fixture?.close()
            val dir=File(checkNotNull(context.getExternalFilesDir(null)),"qa");check(dir.isDirectory || dir.mkdirs())
            File(dir,"owner-closed-history-"+label+".json").writeText(buildJsonObject {
                put("state",if(complete) "PASS_REAL_OWNER_API_CLOSED_HISTORY" else "FAILED_REAL_OWNER_API_CLOSED_HISTORY")
                put("backend",if(external==null) "mock" else "actual-api-owner");put("sourceCommit",commit);put("api",Build.VERSION.SDK_INT)
                put("transport","bearer-http");put("checks",JsonArray(checks.map(::JsonPrimitive)));put("databaseName",databaseName)
                put("savedCredentialsUnchanged",originalCredentials==credentials.all);put("modelOrWorkerExecutionClaim",false)
                errorType?.let {put("errorType",it)}
            }.toString())
        }
    }
    @Test fun incompleteHistoryReadPreservesExactExistingRoomCache()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build()
        try {MockWebServer().use {server->
            server.enqueue(MockResponse().setBody("{}"))
            val c=Conversation("cached","fixture","saved");val old=ChatMessage("old",c.key,"assistant","retained fixture reply",1)
            db.dao().save(c);db.dao().saveMessages(listOf(old))
            val api=OkHttpHermesApiClient(ServerConfig(server.url("p/default/").toString(),"fixture",true),true)
            assertTrue(runCatching {HermesSessionRepository(db.dao()).importMessages(api,c)}.exceptionOrNull() is HistoryReadInvalid)
            assertEquals(listOf(old),db.dao().history(c.key));assertEquals(c,db.dao().conversation(c.key));assertEquals(1,server.requestCount)
        }} finally {db.close()}
    }
}
