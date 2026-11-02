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

class AutomationStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun lostJobAcknowledgementIsDurableAndBlocksFurtherMutationsWithoutRetry()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build()
        try {
            var calls=0
            val api=object:FakeHermesApiClient() {override suspend fun mutateJob(operation:String,id:String,body:JsonObject):HermesJob? {calls++;throw java.io.IOException("Fixture lost acknowledgement")}}
            val repo=JobOperationsRepository(db.dao())
            try {repo.perform(api,"profile","run","j1",buildJsonObject {});fail()} catch(_:java.io.IOException) {}
            val receipt=db.dao().unresolvedOperations("profile").single();assertEquals("unknown",receipt.state)
            try {repo.perform(api,"profile","run","j1",buildJsonObject {});fail()} catch(_:IllegalArgumentException) {}
            assertEquals(1,calls);assertTrue(db.dao().unresolvedOperations("other").isEmpty())
            db.dao().jobOperationState(receipt.key,"reviewed")
            assertTrue(db.dao().unresolvedOperations("profile").isEmpty());assertEquals(1,calls)
            db.dao().save(receipt.copy(key="interrupted",state="sending"));db.dao().recoverJobOperations()
            assertEquals("unknown",db.dao().unresolvedOperations("profile").single().state)
        } finally {db.close()}
    }
    @Test fun encryptedConnectionNeverPairsAnOldUrlWithANewToken()=runBlocking {
        val store=SecureCredentialStore(context)
        val previous=store.readConfig()
        try {
            val a=ServerConfig("https://one.example/p/a/","token-one")
            val b=ServerConfig("https://two.example/p/b/","token-two")
            store.writeConfig(a)
            coroutineScope {
                val writers=launch(Dispatchers.IO) {repeat(15) {store.writeConfig(if(it%2==0) b else a)}}
                val readers=launch(Dispatchers.IO) {repeat(30) {assertTrue(store.readConfig() in listOf(a,b))}}
                joinAll(writers,readers)
            }
            // Legacy UI preferences cannot override a complete vault pair.
            assertTrue(store.readConfig(AppPreferences(url="https://old.example")) in listOf(a,b))
            val raw=context.getSharedPreferences("credentials",0).all.values.joinToString()
            assertFalse(raw.contains("token-one"));assertFalse(raw.contains("two.example"))
        } finally {if(previous==null) store.clear() else store.writeConfig(previous)}
    }
    @Test fun schemaThreePreservesPendingControlsToolsAndRunCursor()=runBlocking {
        val name="upgrade3-qa.db";context.deleteDatabase(name)
        val schema=Json.parseToJsonElement(InstrumentationRegistry.getInstrumentation().context.assets.open("org.hermesmobile.app.data.HermesDatabase/3.json").bufferedReader().use {it.readText()}).jsonObject["database"]!!.jsonObject
        val placeholder=36.toChar().toString()+"{TABLE_NAME}"
        context.getDatabasePath(name).parentFile!!.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use {old ->
            schema["entities"]!!.jsonArray.forEach {entry ->val e=entry.jsonObject;val table=e["tableName"]!!.jsonPrimitive.content
                old.execSQL(e["createSql"]!!.jsonPrimitive.content.replace(placeholder,table))
                e["indices"]?.jsonArray.orEmpty().forEach {old.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace(placeholder,table))}}
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema["identityHash"]!!.jsonPrimitive.content))
            old.execSQL("INSERT INTO sessions VALUES('c','scope','s','Saved',1,'input','key',2,'runs','r','waiting_for_approval',1,0,1,42,'admission',7)")
            old.execSQL("INSERT INTO pending_controls VALUES('p','c','r','request',7,'approval','Review','[\"once\",\"deny\"]',0,'unknown',3)")
            old.execSQL("INSERT INTO tool_events VALUES('e','c','r','terminal','started','Visible',NULL,4)");old.version=3
        }
        val db=Room.databaseBuilder(context,HermesDatabase::class.java,name).addMigrations(*ALL_HERMES_MIGRATIONS).build()
        try {assertEquals(42L,db.dao().conversation("c")!!.lastEventSeq);assertEquals("unknown",db.dao().controls("c").first().single().state);assertEquals("Visible",db.dao().events("c").first().single().preview)}
        finally {db.close();context.deleteDatabase(name)}
    }
}
