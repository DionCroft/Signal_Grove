package org.hermesmobile.app

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.FakeHermesApiClient
import org.junit.Assert.*
import org.junit.Test

class StorageUpgradeTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun alphaDatabaseMigratesWithoutLosingTasksMessagesOrPendingReceipts()=runBlocking {
        val name="migration-qa.db";context.deleteDatabase(name)
        val placeholder=36.toChar().toString()+"{TABLE_NAME}"
        val schema=Json.parseToJsonElement(InstrumentationRegistry.getInstrumentation().context.assets.open("org.hermesmobile.app.data.HermesDatabase/1.json").bufferedReader().use {it.readText()}).jsonObject["database"]!!.jsonObject
        context.getDatabasePath(name).parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use {old ->
            schema["entities"]!!.jsonArray.forEach {entry ->
                val entity=entry.jsonObject;val table=entity["tableName"]!!.jsonPrimitive.content
                old.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace(placeholder,table))
                entity["indices"]?.jsonArray.orEmpty().forEach {old.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace(placeholder,table))}
            }
            old.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema["identityHash"]!!.jsonPrimitive.content))
            old.execSQL("INSERT INTO tasks VALUES('old-task','Keep this','2026-11-30',2,0)")
            old.execSQL("INSERT INTO sessions VALUES('old-chat','scope','api_1','Saved',123,'pending input','turn-key',456,'runs','run_1','running')")
            old.execSQL("INSERT INTO messages VALUES('old-message','old-chat','assistant','Saved text',123,'unknown')")
            old.version=1
        }
        val db=Room.databaseBuilder(context,HermesDatabase::class.java,name).addMigrations(DatabaseMigrations.FROM_1_TO_2).build()
        try {
            val task=db.dao().tasks().first().single();assertEquals("Keep this",task.title);assertEquals("",task.notes)
            val chat=db.dao().conversation("old-chat")!!;assertEquals("turn-key",chat.pendingKey);assertEquals("run_1",chat.runId);assertFalse(chat.pinned)
            assertEquals("Saved text",db.dao().history("old-chat").single().content)
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun historyRefreshReplacesSettledRowsWithoutDuplicatesAndProtectsAnInFlightTurn()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build()
        try {
            val dao=db.dao();val c=Conversation("key","scope","api_root",status="completed");dao.save(c)
            dao.save(ChatMessage("local","key","assistant","Outdated"))
            var rows=listOf(RemoteMessage("assistant","Updated",10,"m1"))
            val api=object:FakeHermesApiClient() {override suspend fun history(id:String)=MessageHistory("api_tip",rows)}
            val repo=HermesSessionRepository(dao)
            repeat(2) {repo.importMessages(api,dao.conversation("key")!!)}
            assertEquals(1,dao.history("key").size);assertEquals("Updated",dao.history("key").single().content)
            assertEquals("api_tip",dao.conversation("key")!!.remoteId)
            val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            val delayed=object:FakeHermesApiClient() {override suspend fun history(id:String):MessageHistory {started.complete(Unit);release.await();return MessageHistory(id,emptyList())}}
            val sync=launch {repo.importMessages(delayed,dao.conversation("key")!!)}
            started.await();dao.save(dao.conversation("key")!!.copy(status="running",pendingKey="new-turn"));release.complete(Unit);sync.join()
            assertEquals("Updated",dao.history("key").single().content)
            dao.mergeRemoteSession("scope",RemoteSession("api_tip","Metadata refresh",20),20)
            assertEquals("new-turn",dao.conversation("key")!!.pendingKey)
            assertEquals("running",dao.conversation("key")!!.status)
        } finally {db.close()}
    }
    @Test fun jobSnapshotsArePartitionedAndNetworkFailurePreservesTheLastSuccessfulSnapshot()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build()
        try {
            val repo=HermesJobsRepository(db.dao());val api=FakeHermesApiClient()
            repo.refresh(api,"one");assertTrue(db.dao().cachedJobs("two").first().isEmpty())
            val failing=object:FakeHermesApiClient() {override suspend fun jobs():List<HermesJob> {throw java.io.IOException("fixture outage")}}
            try {repo.refresh(failing,"one");fail("Expected outage")} catch(_:java.io.IOException) {}
            assertEquals("demo-job",repo.detail(failing,"one","demo-job",false).id)
        } finally {db.close()}
    }
}
