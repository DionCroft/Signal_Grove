package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class SessionOperationStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun uncertainOperationPersistsBlocksMutationsAndReviewNeverRepeatsIt()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build()
        try {
            val dao=db.dao();val c=Conversation("c","scope","api",status="completed");dao.save(c)
            var sends=0;val api=object:FakeHermesApiClient() {override suspend fun sessionAction(action:SessionAction,target:String,body:JsonObject):JsonObject {sends++;throw java.io.IOException("Lost acknowledgement")}}
            val repo=SessionOperationsRepository(dao);val body=RuntimeChoice("m","p").fields()
            try {repo.perform(api,c,SessionAction.Lock,c.remoteId,body);fail()} catch(_:java.io.IOException) {}
            val pending=dao.unresolvedServerOperations(c.scope).single();assertEquals("unknown",pending.state)
            try {repo.perform(api,c,SessionAction.Lock,c.remoteId,body);fail()} catch(_:IllegalArgumentException) {}
            assertEquals(1,sends);dao.reviewServerOperation(pending.key,"wrong-scope");assertEquals(1,dao.unresolvedServerOperations(c.scope).size)
            dao.reviewServerOperation(pending.key,c.scope);assertTrue(dao.unresolvedServerOperations(c.scope).isEmpty());assertEquals(1,sends)
            dao.finishServerOperation(pending.key,"unknown","");assertEquals("reviewed",dao.serverOperations(c.scope).first().single().state)
        } finally {db.close()}
    }
    @Test fun concurrentOperationsClaimOnlyOneDurableReceipt()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build()
        try {
            val dao=db.dao();val c=Conversation("c","scope","api",status="completed");dao.save(c)
            val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var sends=0
            val api=object:FakeHermesApiClient() {override suspend fun sessionAction(action:SessionAction,target:String,body:JsonObject):JsonObject {sends++;entered.complete(Unit);release.await();return super.sessionAction(action,target,body)}}
            val repo=SessionOperationsRepository(dao);val body=RuntimeChoice("m","p").fields()
            coroutineScope {
                val first=async {repo.perform(api,c,SessionAction.Lock,c.remoteId,body)};entered.await()
                try {repo.perform(api,c,SessionAction.Lock,c.remoteId,body);fail()} catch(_:IllegalArgumentException) {}
                release.complete(Unit);first.await()
            }
            assertEquals(1,sends);assertEquals("confirmed",dao.serverOperations(c.scope).first().single().state)
            dao.recoverServerOperations();assertEquals("confirmed",dao.serverOperations(c.scope).first().single().state)
        } finally {db.close()}
    }
    @Test fun stalePreferenceCopiesCannotRestoreConnectionOrSpeechPrivacyFlags()=runBlocking {
        val app=context.applicationContext as org.hermesmobile.app.HermesApplication
        val store=app.preferences;val previous=store.flow.first()
        try {
            val old=previous.copy(demo=true,url="",allowNetworkSpeech=true,onDeviceSpeechOnly=false)
            store.update(old)
            store.connection(urlValue="https://fixture.invalid/p/work/",demoValue=false,httpValue=false)
            val connected=store.flow.first()
            store.patchUserSettings(connected.copy(allowNetworkSpeech=false,onDeviceSpeechOnly=true),connected)
            store.patchUserSettings(old.copy(theme="JADE"),old)
            val saved=store.flow.first()
            assertEquals("JADE",saved.theme);assertFalse(saved.demo);assertEquals("https://fixture.invalid/p/work/",saved.url)
            assertFalse(saved.allowNetworkSpeech);assertTrue(saved.onDeviceSpeechOnly)
        } finally {store.update(previous)}
    }
    @Test fun schemaFivePreservesFrozenRequestsAndRecoversInterruptedServerOperations()=runBlocking {
        val name="session-upgrade5.db";context.deleteDatabase(name)
        val schema=Json.parseToJsonElement(InstrumentationRegistry.getInstrumentation().context.assets.open("org.hermesmobile.app.data.HermesDatabase/5.json").bufferedReader().use {it.readText()}).jsonObject["database"]!!.jsonObject
        context.getDatabasePath(name).parentFile!!.mkdirs();val placeholder=36.toChar().toString()+"{TABLE_NAME}"
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use {old ->
            schema["entities"]!!.jsonArray.forEach {entry -> val entity=entry.jsonObject;val table=entity["tableName"]!!.jsonPrimitive.content
                old.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace(placeholder,table));entity["indices"]?.jsonArray.orEmpty().forEach {old.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace(placeholder,table))}
            }
            old.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema["identityHash"]!!.jsonPrimitive.content))
            old.execSQL("INSERT INTO sessions (`key`,scope,remoteId,title,updatedAt,pendingInput,pendingKey,pendingAt,transport,runId,status,channelKey,pendingRequestHash) VALUES('c','scope','api','Saved',1,'Work','key',1,'runs','r','unknown','mobile-channel',?)",arrayOf("a".repeat(64)))
            old.version=5
        }
        val db=Room.databaseBuilder(context,HermesDatabase::class.java,name).addMigrations(*ALL_HERMES_MIGRATIONS).build()
        try {
            val c=db.dao().conversation("c")!!;assertEquals("mobile-channel",c.channelKey);assertEquals("a".repeat(64),c.pendingRequestHash);assertEquals("",c.detailsJson)
            db.dao().claimServerOperation(ServerOperation("op",c.scope,c.key,"Lock",c.remoteId,"{}","sending","",1))
            db.dao().recoverServerOperations();assertEquals("unknown",db.dao().unresolvedServerOperations(c.scope).single().state)
        } finally {db.close();context.deleteDatabase(name)}
    }
}
