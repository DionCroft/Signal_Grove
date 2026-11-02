package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class StableStorageTest {
    @Test fun reviewedTaskImportMergesAtomicallyWithoutDeletingUnlistedTasks()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
        try {
            val dao=db.dao();dao.save(LocalTask("keep","Keep this"));dao.save(LocalTask("replace","Old title"))
            val file=TaskTransfer.encode(listOf(LocalTask("replace","New title",notes="Saved notes"),LocalTask("new","新的任务",completed=true)))
            dao.importTasks(TaskTransfer.decode(file))
            val tasks=dao.tasks().first();assertEquals(3,tasks.size);assertTrue(tasks.any {it.id=="keep" && it.title=="Keep this"});assertEquals("Saved notes",tasks.single {it.id=="replace"}.notes)
            assertTrue(tasks.single {it.id=="new"}.completed)
            dao.importTasks(TaskTransfer.decode(file));assertEquals(3,dao.tasks().first().size)
        } finally {db.close()}
    }

    @Test fun legacyCredentialMigratesOnlyWithItsForegroundUrlAndIsRemovedAtomically() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=SecureCredentialStore(context);val previous=store.readConfig()
        try {
            // Populate the actual v1 format using the same Keystore alias; all values are fixtures.
            store.writeConfig(ServerConfig("https://temporary.example/","temporary-fixture"))
            val keyStore=java.security.KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
            val key=keyStore.getKey("hermes_token_v1",null) as javax.crypto.SecretKey
            val cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {init(javax.crypto.Cipher.ENCRYPT_MODE,key)}
            val encrypted=android.util.Base64.encodeToString(cipher.iv+cipher.doFinal("legacy-fixture".toByteArray()),android.util.Base64.NO_WRAP)
            val prefs=context.getSharedPreferences("credentials",0)
            assertTrue(prefs.edit().clear().putString("token",encrypted).commit())
            assertNull(store.readConfig())
            val expected=ServerConfig("https://legacy.example/p/work/","legacy-fixture",true)
            assertEquals(expected,store.readConfig(AppPreferences(url=expected.url,debugHttp=true)))
            assertFalse(prefs.contains("token"));assertTrue(prefs.contains("connection_v2"))
            assertEquals(expected,store.readConfig())
        } finally {if(previous==null) store.clear() else store.writeConfig(previous)}
    }
}
