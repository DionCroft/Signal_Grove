package org.hermesmobile.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SavedConnectionsTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun isolated():Context=object:ContextWrapper(context) {
        private val file="qa-vault-"+UUID.randomUUID()
        override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences(file,mode)
    }
    @Test fun namesCredentialsAndRotationAreEncryptedAndIndependentlyRecoverable() {
        val sandbox=isolated();val store=SecureCredentialStore(sandbox)
        val a=ServerConfig("https://fixture.example/p/alpha/","original-fixture")
        val b=a.copy(token="rotated-fixture")
        try {
            store.writeConfig(a);store.rename(a.scope,"Original profile")
            store.writeConfig(b);store.rename(b.scope,"Rotated profile")
            assertNotEquals(a.scope,b.scope);assertEquals(2,store.profiles().size)
            assertEquals(b,store.readConfig());assertEquals(a,store.profile(a.scope))
            assertEquals(a,store.activate(a.scope));assertEquals(a,store.readConfig())
            store.forget(b.scope);assertEquals(a,store.readConfig());assertNull(store.profile(b.scope))
            assertFalse(store.profiles().single {it.id==b.scope}.hasCredential)
            store.forgetCurrent();assertNull(store.readConfig());assertEquals(2,store.profiles().size)
            store.writeConfig(b);assertEquals("Rotated profile",store.profiles().single {it.id==b.scope}.name)
            val raw=sandbox.getSharedPreferences("credentials",0).all.values.joinToString()
            listOf(a.url,a.token,b.token,"Original profile","Rotated profile").forEach {assertFalse(raw.contains(it))}
        } finally {store.clear()}
    }
    @Test fun existingVersionTwoCredentialIsAvailableWithoutDestructiveMigration() {
        val sandbox=isolated();val store=SecureCredentialStore(sandbox)
        val a=ServerConfig("https://fixture.example/p/existing/","v2-fixture",true)
        try {
            store.writeConfig(a)
            assertTrue(sandbox.getSharedPreferences("credentials",0).edit().remove("profiles_v1").commit())
            val migrated=SecureCredentialStore(sandbox)
            assertEquals(a,migrated.readConfig());assertEquals(a,migrated.profile(a.scope))
            assertEquals(a.scope,migrated.profiles().single().id)
            migrated.rename(a.scope,"Existing connection")
            assertEquals(a,store.readConfig());assertEquals("Existing connection",store.profiles().single().name)
        } finally {store.clear()}
    }
    @Test fun multipleStoreInstancesCannotLoseProfilesOrMixActiveCredentialPairs()=runBlocking {
        val sandbox=isolated();val first=SecureCredentialStore(sandbox);val second=SecureCredentialStore(sandbox)
        val rows=(0..7).map {ServerConfig("https://fixture.example/p/$it/","parallel-fixture-$it")}
        try {
            first.writeConfig(rows.first())
            coroutineScope {rows.mapIndexed {i,row ->launch(Dispatchers.IO) {repeat(3) {
                val store=if(i%2==0) first else second;store.writeConfig(row)
                assertTrue(first.readConfig() in rows)
            }}}.joinAll()}
            assertEquals(rows.map {it.scope}.toSet(),first.profiles().map {it.id}.toSet())
            rows.forEach {row ->assertEquals(row,second.activate(row.scope));assertEquals(row,first.readConfig())}
            assertTrue(runCatching {first.rename(rows.first().scope,"bad\nname")}.isFailure)
            assertTrue(runCatching {first.activate("unavailable")}.isFailure)
        } finally {first.clear()}
    }
    @Test fun managementSecretsStayBoundToTheirApiCredentialAndRemovalCannotBeUndone() {
        val sandbox=isolated();val store=SecureCredentialStore(sandbox)
        val a=ServerConfig("https://api.example/first/","api-first-fixture")
        val b=ServerConfig("https://api.example/second/","api-second-fixture")
        val secret=buildJsonObject {put("url","https://management.example/private/");put("token","management-secret-fixture")}
        try {
            store.writeConfig(a);store.writeConfig(b)
            store.saveManagement(a.scope,secret);store.saveManagement(b.scope,secret)
            assertEquals(secret,store.management(a.scope));assertEquals(b,store.readConfig())
            val raw=sandbox.getSharedPreferences("credentials",0).all.values.joinToString()
            assertFalse(raw.contains("management-secret-fixture"));assertFalse(raw.contains("management.example"))
            store.forget(a.scope)
            assertNull(store.management(a.scope));assertEquals(secret,store.management(b.scope));assertEquals(b,store.readConfig())
            assertTrue(runCatching {store.saveManagement(a.scope,secret)}.isFailure)
            assertNull(store.management(a.scope))
            store.saveManagement(b.scope,null);assertNull(store.management(b.scope));assertEquals(b,store.readConfig())
        } finally {store.clear()}
    }

}
