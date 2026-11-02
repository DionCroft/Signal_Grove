package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class McpCatalogStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val tokens=ManagementTokens("catalog-management-fixture","refresh-fixture","password","fixture",4102444800)
    private val catalog="""{"entries":[{"name":"catalog-fixture","description":"Fixture","source":"public source","transport":"http","auth_type":"header","url":"https://tools.example/","installed":false,"enabled":false,"needs_install":false,"required_env":[{"name":"FIXTURE_KEY","prompt":"Credential","required":true}]}]}"""
    private fun json(text:String)=MockResponse().setHeader("Content-Type","application/json").setBody(text)
    @Test fun lostInstallReplyPersistsOnlyRedactedMetadataAndCannotBeRetriedAfterRestart()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build();val writes=AtomicInteger()
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when(request.path) {
                    "/dashboard/api/auth/me"->json("""{"provider":"password","user_id":"fixture"}""")
                    "/dashboard/api/mcp/catalog?profile=worker"->json(catalog)
                    "/dashboard/api/mcp/servers?profile=worker"->json("""{"servers":[]}""")
                    "/dashboard/api/mcp/catalog/install?profile=worker"->{
                        writes.incrementAndGet();assertEquals("Bearer catalog-management-fixture",request.getHeader("Authorization"))
                        val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject;assertEquals("catalog-secret-fixture",body["env"]!!.jsonObject.text("FIXTURE_KEY"));assertEquals("worker",body.text("profile"))
                        MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                    }
                    else->json("{}").setResponseCode(404)
                }
            }
            try {
                val api=ManagementClient(server.url("dashboard/").toString(),true,true);val target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val install=McpCatalogInstall(McpCatalogEntry.inventory(Json.parseToJsonElement(catalog).jsonObject).single(),mapOf("FIXTURE_KEY" to "catalog-secret-fixture"),false)
                val repo=McpCatalogRepository(db.dao(),CompletableDeferred(Unit))
                assertTrue(runCatching {repo.install(target,install,api,tokens)}.isFailure)
                val receipt=db.dao().unresolvedManagementOperations(target.resourceScope).single();assertEquals("unknown",receipt.state);assertFalse(receipt.toString().contains("catalog-secret-fixture"));assertTrue(receipt.requestSummary.contains("FIXTURE_KEY"))
                db.dao().recoverManagementOperations();db.dao().clearChats()
                assertTrue(runCatching {McpCatalogRepository(db.dao(),CompletableDeferred(Unit)).install(target,install,api,tokens)}.exceptionOrNull() is ManagementOutcomePending);assertEquals(1,writes.get())
            } finally {db.close()}
        }
    }
    @Test fun changedManifestRefusesBeforeMutationAndAcceptedBackgroundWorkIsNeverMarkedCompleted()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build();val writes=AtomicInteger()
        val original=catalog.replace("\"needs_install\":false","\"needs_install\":true").replace("\"required_env\":[{\"name\":\"FIXTURE_KEY\",\"prompt\":\"Credential\",\"required\":true}]","\"required_env\":[]")
        var current=original.replace("public source","changed source")
        MockWebServer().use {server ->
            server.dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse=when(request.path) {
                    "/api/auth/me"->json("""{"provider":"password","user_id":"fixture"}""")
                    "/api/mcp/catalog?profile=worker"->json(current)
                    "/api/mcp/servers?profile=worker"->json("""{"servers":[]}""")
                    "/api/mcp/catalog/install?profile=worker"->{writes.incrementAndGet();json("""{"ok":true,"name":"catalog-fixture","background":true,"action":"mcp-install-fixture"}""")}
                    else->json("{}").setResponseCode(404)
                }
            }
            try {
                val api=ManagementClient(server.url("/").toString(),true,true);val target=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
                val install=McpCatalogInstall(McpCatalogEntry.inventory(Json.parseToJsonElement(original).jsonObject).single(),emptyMap(),true);val repo=McpCatalogRepository(db.dao(),CompletableDeferred(Unit))
                assertTrue(runCatching {repo.install(target,install,api,tokens)}.exceptionOrNull() is McpCatalogChanged);assertEquals(0,writes.get());assertTrue(db.dao().unresolvedManagementOperations(target.resourceScope).isEmpty())
                current=original;val receipt=repo.install(target,install,api,tokens);assertEquals(1,writes.get());assertEquals("acknowledged",receipt.state);assertTrue(receipt.resultSummary.contains("\"background\":true"));assertFalse(receipt.resultSummary.contains("mcp-install-fixture"))
            } finally {db.close()}
        }
    }
}
