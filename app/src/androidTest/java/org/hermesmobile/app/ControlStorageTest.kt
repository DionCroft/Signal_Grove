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

class ControlStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun snapshot(generation:Int=1):JsonObject = buildJsonObject {
        put("status","waiting_for_approval")
        put("pending_controls",buildJsonArray {
            add(buildJsonObject {
                put("kind","approval");put("prompt_id","p");put("execution_generation",generation)
                put("description","Review harmless sample")
                put("choices",buildJsonArray {add("once");add("deny")})
            })
        })
    }
    @Test fun staleGenerationIsRefusedBeforeAnyDecisionIsSent()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build()
        try {
            val dao=db.dao();val c=Conversation("c","scope","api",runId="r",status="waiting_for_approval");dao.save(c)
            val stored=ControlProtocol.snapshot(snapshot(),"r").single().stored("c");dao.save(stored)
            var decisions=0
            val api=object:FakeHermesApiClient() {override suspend fun run(id:String)=snapshot(2);override suspend fun respondControl(control:PendingControl,response:String) {decisions++}}
            try {InteractionRepository(dao).respond(api,c,stored,"once");fail("Stale generation")} catch(_:IllegalArgumentException) {}
            assertEquals(0,decisions)
        } finally {db.close()}
    }
    @Test fun lostAcknowledgementStaysUnknownUntilExplicitRefreshAndNeverAutoRepeats()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build()
        try {
            val dao=db.dao();val c=Conversation("c","scope","api",runId="r",status="waiting_for_approval");dao.save(c)
            val stored=ControlProtocol.snapshot(snapshot(),"r").single().stored("c");dao.save(stored)
            var decisions=0
            val api=object:FakeHermesApiClient() {override suspend fun run(id:String)=snapshot();override suspend fun respondControl(control:PendingControl,response:String) {decisions++;throw java.io.IOException("lost acknowledgement")}}
            val repo=InteractionRepository(dao)
            try {repo.respond(api,c,stored,"once");fail("Expected disconnect")} catch(_:java.io.IOException) {}
            assertEquals("unknown",dao.control(stored.key)!!.state)
            repo.synchronize(c,snapshot());assertEquals("unknown",dao.control(stored.key)!!.state);assertEquals(1,decisions)
            repo.synchronize(c,snapshot(),true);assertEquals("pending",dao.control(stored.key)!!.state);assertEquals(1,decisions)
        } finally {db.close()}
    }
    @Test fun confirmedDecisionCannotBeResurrectedByAnOlderSnapshot()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build()
        try {
            val dao=db.dao();val c=Conversation("c","scope","api",runId="r",status="waiting_for_approval");dao.save(c)
            val stored=ControlProtocol.snapshot(snapshot(),"r").single().stored("c");dao.save(stored)
            val api=object:FakeHermesApiClient() {override suspend fun run(id:String)=snapshot();override suspend fun respondControl(control:PendingControl,response:String) {}}
            val repo=InteractionRepository(dao);repo.respond(api,c,stored,"deny");repo.synchronize(c,snapshot())
            assertTrue(dao.controls("c").first().isEmpty());assertEquals("resolved",dao.control(stored.key)!!.state)
        } finally {db.close()}
    }
    @Test fun cursorTextAndPromptsSurviveReopeningAndAnInterruptedResponseBecomesUnknown()=runBlocking {
        val name="cursor-qa.db";context.deleteDatabase(name)
        fun open()=Room.databaseBuilder(context,HermesDatabase::class.java,name).addMigrations(*ALL_HERMES_MIGRATIONS).build()
        var db=open()
        try {
            val c=Conversation("c","scope","api",runId="r",status="running",lastEventSeq=8)
            db.dao().save(c)
            val control=ControlProtocol.snapshot(snapshot(),"r").single().stored("c")
            db.dao().saveTurn(c,ChatMessage("m","c","assistant","Saved partial text",status="running"),control,ToolEvent("e","c","r","sample","started","safe",null,1),"")
            db.dao().controlState(control.key,"responding");db.close();db=open();db.dao().recoverControls()
            assertEquals(8L,db.dao().conversation("c")!!.lastEventSeq)
            assertEquals("Saved partial text",db.dao().history("c").single().content)
            assertEquals("unknown",db.dao().controls("c").first().single().state)
            assertEquals("sample",db.dao().events("c").first().single().tool)
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun versionTwoTasksAndCachedJobsSurviveTheVoiceAndControlUpgrade()=runBlocking {
        val name="upgrade2-qa.db";context.deleteDatabase(name)
        val schema=Json.parseToJsonElement(InstrumentationRegistry.getInstrumentation().context.assets.open("org.hermesmobile.app.data.HermesDatabase/2.json").bufferedReader().use {it.readText()}).jsonObject["database"]!!.jsonObject
        context.getDatabasePath(name).parentFile!!.mkdirs()
        val placeholder=36.toChar().toString()+"{TABLE_NAME}"
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use {old ->
            schema["entities"]!!.jsonArray.forEach {entry ->
                val entity=entry.jsonObject;val table=entity["tableName"]!!.jsonPrimitive.content
                old.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace(placeholder,table))
                entity["indices"]?.jsonArray.orEmpty().forEach {old.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace(placeholder,table))}
            }
            old.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema["identityHash"]!!.jsonPrimitive.content))
            old.execSQL("INSERT INTO tasks VALUES('t','Preserved task','',1,0,'Saved notes')")
            old.execSQL("INSERT INTO cached_jobs VALUES('cache','scope','j1','Saved job','Review','daily','scheduled',1,'','',123)")
            old.version=2
        }
        val db=Room.databaseBuilder(context,HermesDatabase::class.java,name).addMigrations(*ALL_HERMES_MIGRATIONS).build()
        try {assertEquals("Saved notes",db.dao().tasks().first().single().notes);assertEquals("Saved job",db.dao().cachedJobs("scope").first().single().name)}
        finally {db.close();context.deleteDatabase(name)}
    }

}
