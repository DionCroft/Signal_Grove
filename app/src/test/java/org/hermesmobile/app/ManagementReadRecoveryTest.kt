package org.hermesmobile.app

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class ManagementReadRecoveryTest {
    private val tokens=ManagementTokens("read-access-fixture","read-refresh-fixture","password","fixture-user",4102444800)
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    private fun profiles(name:String)=json("""{"profiles":[{"name":"default","display_name":"$name","path":"/fixture/home"}]}""")
    private fun identity(name:String,user:String="fixture-user")=json("""{"user_id":"$user","provider":"password","display_name":"$name"}""")
    private fun broken()=MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
    private fun api(server:MockWebServer)=ManagementClient(server.url("/dashboard/").toString(),true,true)
    private fun paths(server:MockWebServer,count:Int,path:String,authorized:Boolean=true) {
        repeat(count) {
            val request=checkNotNull(server.takeRequest(2,TimeUnit.SECONDS))
            assertEquals(path,request.path)
            if(authorized) assertEquals("Bearer read-access-fixture",request.getHeader("Authorization"))
        }
        assertNull("No extra request may be replayed",server.takeRequest(200,TimeUnit.MILLISECONDS))
    }
    @Test fun profileReadRecoversABrokenPooledConnectionWithTheSamePrefixAndCredential()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(profiles("before"));server.enqueue(broken());server.enqueue(profiles("fresh"))
            val client=api(server);assertEquals("before",client.profiles(tokens).single().displayName)
            assertEquals("fresh",client.profiles(tokens).single().displayName)
            assertEquals(3,server.requestCount);paths(server,3,"/dashboard/api/profiles")
        }
    }
    @Test fun identityReadRecoversWithoutReplacingTheAuthenticatedActor()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(identity("before"));server.enqueue(broken());server.enqueue(identity("fresh"))
            val client=api(server);client.identity(tokens)
            assertEquals("fresh",client.identity(tokens).displayName)
            assertEquals(3,server.requestCount);paths(server,3,"/dashboard/api/auth/me")
        }
    }
    @Test fun recoveredIdentityStillRejectsAnotherActor()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(identity("before"));server.enqueue(broken());server.enqueue(identity("other","other-user"))
            val client=api(server);client.identity(tokens)
            val error=runCatching {client.identity(tokens)}.exceptionOrNull()
            assertTrue(error.toString(),error is IllegalArgumentException)
            assertEquals("Management identity verification failed.",error!!.message)
            assertEquals(3,server.requestCount);paths(server,3,"/dashboard/api/auth/me")
        }
    }
    @Test fun rotatingRefreshPostIsNeverAutomaticallyReplayedAfterItsRequestArrives()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(profiles("warm"));server.enqueue(broken())
            server.enqueue(json("""{"access_token":"next-access-fixture","refresh_token":"next-refresh-fixture","provider":"password","user_id":"fixture-user","expires_at":4102444800,"token_type":"Bearer"}"""))
            val client=api(server);client.profiles(tokens)
            assertTrue(runCatching {client.refresh(tokens)}.exceptionOrNull() is IOException)
            val warm=checkNotNull(server.takeRequest(2,TimeUnit.SECONDS));assertEquals("GET",warm.method)
            val post=checkNotNull(server.takeRequest(2,TimeUnit.SECONDS));assertEquals("POST",post.method)
            assertEquals("/dashboard/auth/native/refresh",post.path);assertNull(post.getHeader("Authorization"))
            assertEquals(2,server.requestCount);assertNull(server.takeRequest(200,TimeUnit.MILLISECONDS))
        }
    }
    @Test fun otherManagementReadsDoNotGainAnUnreviewedRetryPolicy()=runBlocking {
        MockWebServer().use {server->
            server.enqueue(profiles("warm"));server.enqueue(broken());server.enqueue(json("{}"))
            val client=api(server);client.profiles(tokens)
            assertTrue(runCatching {client.mcp(tokens,"default")}.exceptionOrNull() is IOException)
            assertEquals("/dashboard/api/profiles",server.takeRequest(2,TimeUnit.SECONDS)!!.path)
            val read=server.takeRequest(2,TimeUnit.SECONDS)!!;assertEquals("GET",read.method)
            assertEquals("/dashboard/api/mcp/servers?profile=default",read.path)
            assertEquals(2,server.requestCount);assertNull(server.takeRequest(200,TimeUnit.MILLISECONDS))
        }
    }
    @Test fun metadataHttpErrorsAndRedirectsAreSurfacedWithoutCredentialForwarding()=runBlocking {
        for(status in listOf(500,302)) MockWebServer().use {server->MockWebServer().use {other->
            server.enqueue(profiles("warm"));server.enqueue(json("{}").setResponseCode(status).setHeader("Location",other.url("/elsewhere")))
            val client=api(server);client.profiles(tokens)
            assertTrue(runCatching {client.profiles(tokens)}.exceptionOrNull() is HermesHttpException)
            assertEquals(2,server.requestCount);assertEquals(0,other.requestCount);paths(server,2,"/dashboard/api/profiles")
        }}
    }
}
