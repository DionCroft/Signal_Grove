package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import java.security.MessageDigest
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ManagementResourceStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val tokens=ManagementTokens("shared-management-fixture","refresh-fixture","password","fixture",4102444800)
    private fun db()=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build()
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    private fun receipt(target:ManagementTarget,key:String)=ManagementOperation(key,target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"Probe","private-server-fixture","{}","sending","",1)

    @Test fun unknownOutcomeBlocksAnotherInferenceConnectionBeforeAnyNetworkRequest()=runBlocking {
        val database=db();val writes=AtomicInteger();var enabled=true
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when(request.path) {
                    "/dashboard/api/auth/me"->json("""{"user_id":"fixture","provider":"password"}""")
                    "/dashboard/api/mcp/servers?profile=worker"->json("""{"servers":[{"name":"fixture","transport":"http","source":"config","enabled":$enabled}]}""")
                    "/dashboard/api/mcp/servers/fixture/enabled?profile=worker"->{
                        enabled=!enabled
                        if(writes.incrementAndGet()==1) MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                        else json("""{"ok":true,"name":"fixture","enabled":$enabled}""")
                    }
                    else->json("{}").setResponseCode(404)
                }
            }
            try {
                val api=ManagementClient(server.url("dashboard/").toString(),true,true)
                val first=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val second=first.copy(apiScope="b".repeat(64))
                val repo=ManagementOperationsRepository(database.dao(),CompletableDeferred(Unit))
                assertTrue(runCatching {repo.execute(first,McpRequest(McpAction.Toggle,"fixture",true,"config",false),api,tokens)}.isFailure)
                assertEquals("unknown",database.dao().managementOperation(database.dao().unresolvedManagementOperations(first.resourceScope).single().key)!!.state)
                val before=server.requestCount
                val failure=runCatching {repo.execute(second,McpRequest(McpAction.Toggle,"fixture",false,"config",true),api,tokens)}.exceptionOrNull()
                assertTrue("Shared pending barrier bypassed: ${writes.get()} mutations",failure is ManagementOutcomePending)
                assertEquals(before,server.requestCount);assertEquals(1,writes.get())
            } finally {database.close()}
        }
    }

    @Test fun simultaneousClaimsFromDifferentInferenceConnectionsHaveOneWinner()=runBlocking {
        val database=db()
        try {
            val first=ManagementTarget("a".repeat(64),"https://fixture.example/dashboard/","worker","password · first")
            val second=first.copy(apiScope="b".repeat(64),actor="password · second")
            val results=coroutineScope {listOf(first,second).mapIndexed {i,target ->async(Dispatchers.IO) {runCatching {database.dao().claimManagementOperation(receipt(target,i.toString()))}}}.awaitAll()}
            assertEquals("A dashboard/profile must have only one pending owner",1,results.count {it.isSuccess})
            assertTrue(results.single {it.isFailure}.exceptionOrNull() is ManagementOutcomePending)
        } finally {database.close()}
    }

    @Test fun separateProfilesAndDashboardPrefixesDoNotBlockEachOtherAndInvalidClaimsAreRefused()=runBlocking {
        val database=db()
        try {
            val first=ManagementTarget("a".repeat(64),"https://fixture.example/dashboard/","worker","password · first")
            database.dao().claimManagementOperation(receipt(first,"first"))
            for((i,target) in listOf(first.copy(profile="other"),first.copy(url="https://fixture.example/other/"),first.copy(url="https://other.example/dashboard/")).withIndex()) database.dao().claimManagementOperation(receipt(target,"other-$i"))
            assertTrue(runCatching {database.dao().claimManagementOperation(receipt(first,"forged").copy(resourceScope="invented"))}.exceptionOrNull() is IllegalArgumentException)
            assertEquals(4,database.dao().managementOperations(first.apiScope).first().size)
        } finally {database.close()}
    }

    @Test fun schemaNineRetainsAllReceiptsAndDataButUnifiesHistoricalPendingResources()=runBlocking {
        val name="shared-management-migration9-qa.db";context.deleteDatabase(name)
        val schema=Json.parseToJsonElement(InstrumentationRegistry.getInstrumentation().context.assets.open("org.hermesmobile.app.data.HermesDatabase/9.json").bufferedReader().use {it.readText()}).jsonObject["database"]!!.jsonObject
        context.getDatabasePath(name).parentFile!!.mkdirs();val placeholder=36.toChar().toString()+"{TABLE_NAME}"
        val target=ManagementTarget("a".repeat(64),"https://fixture.example/dashboard/","worker","password · private-user")
        val states=listOf("sending","unknown","acknowledged","confirmed","reviewed")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use {old ->
            schema["entities"]!!.jsonArray.forEach {entry->val entity=entry.jsonObject;val table=entity["tableName"]!!.jsonPrimitive.content
                old.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace(placeholder,table));entity["indices"]?.jsonArray.orEmpty().forEach {old.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace(placeholder,table))}}
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");old.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema["identityHash"]!!.jsonPrimitive.content))
            for((i,state) in states.withIndex()) {
                val apiScope=('a'.code+i).toChar().toString().repeat(64)
                val legacy=MessageDigest.getInstance("SHA-256").digest((apiScope+"\u0000"+target.url+"\u0000"+target.profile).toByteArray()).joinToString("") {"%02x".format(it)}
                old.execSQL("INSERT INTO management_operations ([key],resourceScope,apiScope,url,profile,actor,action,target,requestSummary,state,resultSummary,createdAt) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",arrayOf<Any>("legacy-$state",legacy,apiScope,target.url,target.profile,target.actor,"CustomEndpointSave","private-target","private-request",state,"private-result",100L+i))
            }
            old.execSQL("""INSERT INTO messages VALUES('m','c','assistant','Saved',1,'completed','{"usage":{"input_tokens":12}}','["saved-image-reference"]')""")
            old.execSQL("INSERT INTO server_operations VALUES('native','api','c','Fork','child','{}','unknown','',1)")
            old.execSQL("INSERT INTO tasks VALUES('task','Retained local task','',1,0,'Retained notes')")
            old.version=9
        }
        val database=Room.databaseBuilder(context,HermesDatabase::class.java,name).addMigrations(*ALL_HERMES_MIGRATIONS).build()
        try {
            val rows=database.dao().managementResourceOperations(target.resourceScope).first();assertEquals(5,rows.size)
            for((i,state) in states.withIndex()) {val row=rows.single {it.key=="legacy-$state"};assertEquals(('a'.code+i).toChar().toString().repeat(64),row.apiScope);assertEquals(100L+i,row.createdAt);assertEquals(target.url,row.url);assertEquals(target.profile,row.profile);assertEquals(state,row.state);assertEquals("private-request",row.requestSummary);assertEquals("private-result",row.resultSummary);assertEquals("private-target",row.target);assertEquals(target.actor,row.actor)}
            assertTrue(database.dao().message("m")!!.imagesJson.contains("saved-image-reference"));assertEquals("native",database.dao().unresolvedServerOperations("api").single().key)
            val task=database.dao().tasks().first().single {it.id=="task"};assertEquals("Retained local task",task.title);assertEquals("Retained notes",task.notes)
            val replacement=target.copy(apiScope="f".repeat(64),actor="password · replacement")
            assertTrue(runCatching {database.dao().claimManagementOperation(receipt(replacement,"blocked"))}.exceptionOrNull() is ManagementOutcomePending)
            val visible=ManagementReceiptVisibility.project(rows,replacement.apiScope,replacement.resourceScope);assertEquals(2,visible.size);assertFalse(visible.toString().contains("private-"))
            val repo=ManagementOperationsRepository(database.dao(),CompletableDeferred(Unit))
            repo.review(replacement,"legacy-sending");assertEquals("sending",database.dao().managementOperation("legacy-sending")!!.state)
            database.dao().recoverManagementOperations();assertEquals(2,database.dao().unresolvedManagementOperations(replacement.resourceScope).size)
            // No original inference credential is needed for explicit LOCAL uncertainty review.
            repo.review(replacement.copy(profile="other"),"legacy-unknown");assertEquals("unknown",database.dao().managementOperation("legacy-unknown")!!.state)
            repo.review(replacement,"legacy-unknown");repo.review(replacement,"legacy-sending")
            database.dao().claimManagementOperation(receipt(replacement,"after-review"));assertEquals("sending",database.dao().managementOperation("after-review")!!.state)
            assertEquals("reviewed",database.dao().managementOperation("legacy-unknown")!!.state);assertEquals("private-result",database.dao().managementOperation("legacy-unknown")!!.resultSummary)
        } finally {database.close();context.deleteDatabase(name)}
    }

    @Test fun manyHistoricalUnknownReceiptsRemainBlockedAndReviewableBeyondTheVisiblePage()=runBlocking {
        val database=db();val target=ManagementTarget("a".repeat(64),"https://fixture.example/dashboard/","worker","password · fixture")
        try {
            repeat(105) {database.dao().insertManagementOperation(receipt(target,"legacy-$it").copy(state="unknown",apiScope="b".repeat(64),createdAt=it.toLong()))}
            val first=database.dao().managementResourceOperations(target.resourceScope).first();assertEquals(100,first.size)
            assertTrue(runCatching {database.dao().claimManagementOperation(receipt(target,"blocked"))}.exceptionOrNull() is ManagementOutcomePending)
            val repo=ManagementOperationsRepository(database.dao(),CompletableDeferred(Unit));for(row in first) repo.review(target,row.key)
            val next=ManagementReceiptVisibility.project(database.dao().managementResourceOperations(target.resourceScope).first(),target.apiScope,target.resourceScope)
            assertEquals(5,next.size);assertTrue(next.all {it.state=="unknown"});assertEquals(5,database.dao().unresolvedManagementOperations(target.resourceScope).size)
            for(row in next) repo.review(target,row.key)
            database.dao().claimManagementOperation(receipt(target,"allowed"));assertEquals(106,database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM management_operations").use {it.moveToFirst();it.getInt(0)})
        } finally {database.close()}
    }

    @Test fun sharedResourceDoesNotPermitAnotherConnectionsOAuthFlowReadOrCancel()=runBlocking {
        val database=db()
        MockWebServer().use {server ->
            val api=ManagementClient(server.url("dashboard/").toString(),true,true)
            val owner=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture");val other=owner.copy(apiScope="b".repeat(64))
            try {
                val row=receipt(owner,"flow").copy(action="OAuthStart",state="acknowledged",resultSummary="""{"flow_id":"fixture-flow-123456789","status":"starting"}""")
                database.dao().insertManagementOperation(row);val repo=McpOAuthRepository(database.dao(),CompletableDeferred(Unit))
                assertEquals(owner.resourceScope,other.resourceScope)
                assertTrue(runCatching {repo.read(other,row.key,api,tokens)}.exceptionOrNull() is IllegalArgumentException)
                assertTrue(runCatching {repo.cancel(other,row.key,api,tokens)}.exceptionOrNull() is IllegalArgumentException)
                assertEquals(0,server.requestCount);assertEquals("acknowledged",database.dao().managementOperation(row.key)!!.state)
            } finally {database.close()}
        }
    }
}
