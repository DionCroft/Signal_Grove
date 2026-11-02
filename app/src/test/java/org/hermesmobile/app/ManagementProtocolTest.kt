package org.hermesmobile.app

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ManagementProtocolTest {
    private val payload="""{"access_token":"management-access-fixture","refresh_token":"management-refresh-fixture","token_type":"Bearer","expires_at":4102444800,"provider":"password","user_id":"fixture-user"}"""
    private fun json(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    @Test fun pkceUsesTheRfcVectorAndCallbackRejectsAmbiguousOrMismatchedState() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",NativeLogin.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val code="a".repeat(43)
        assertEquals(code,NativeLogin.callback("/callback?code=$code&state=fixture-state","fixture-state"))
        listOf("/other?code=$code&state=fixture-state","/callback?code=$code&state=wrong","/callback?code=$code&state=fixture-state&state=fixture-state","/callback?code=$code&code=$code&state=fixture-state","/callback?code=$code&state=fixture-state#fragment","/callback?code=short&state=fixture-state","/callback?code=$code&state=fixture-state&unknown=1").forEach {target->assertTrue(target,runCatching {NativeLogin.callback(target,"fixture-state")}.isFailure)}
    }
    @Test fun malformedAccessTokensAreRejectedBeforeTheyCanAppearInHeaderErrors() {
        listOf("bad token", "bad令牌").forEach {secret ->
            val failure=runCatching {ManagementTokens.parse(Json.parseToJsonElement(payload.replace("management-access-fixture",secret)).jsonObject)}.exceptionOrNull()
            assertNotNull(failure);assertEquals("Invalid management sign-in response.",failure!!.message)
            assertFalse(failure.message.orEmpty().contains(secret))
        }
    }
    @Test fun loopbackReceivesOnlyMatchingCallbackAndDoesNotReflectCode()=runBlocking {
        NativeLogin().use {pending ->
            val authorize=pending.authorize("https://fixture.example/dashboard/".toHttpUrl())
            assertEquals("/dashboard/auth/native/authorize",authorize.encodedPath)
            assertEquals(NativeLogin.challenge(pending.verifier),authorize.queryParameter("code_challenge"))
            assertFalse(authorize.toString().contains(pending.verifier))
            val waiting=async {pending.awaitCode(5)}
            val client=OkHttpClient.Builder().callTimeout(3,TimeUnit.SECONDS).build()
            val code="b".repeat(43);val callback=authorize.queryParameter("redirect_uri")!!.toHttpUrl()
            withContext(Dispatchers.IO) {
                val wrong=callback.newBuilder().addQueryParameter("code",code).addQueryParameter("state","wrong").build()
                client.newCall(Request.Builder().url(wrong).build()).execute().use {assertEquals(400,it.code)}
                val right=callback.newBuilder().addQueryParameter("code",code).addQueryParameter("state",authorize.queryParameter("state")).build()
                client.newCall(Request.Builder().url(right).build()).execute().use {assertEquals(200,it.code);assertEquals("no-store",it.header("Cache-Control"));assertFalse(it.body.string().contains(code))}
            }
            assertEquals(code,waiting.await())
        }
    }
    @Test fun managementContractsPreservePrefixAndUseOnlyTheirOwnBearerToken()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(json(payload));server.enqueue(json("""{"user_id":"fixture-user","provider":"password","display_name":"Fixture"}"""))
            server.enqueue(json("""{"profiles":[{"name":"worker","display_name":"Worker"},{"name":"default"}]}"""))
            server.enqueue(json("""{"servers":[]}"""));server.enqueue(json(payload.replace("management-access-fixture","renewed-access-fixture")))
            val api=ManagementClient(server.url("dashboard/").toString(),true,true)
            val tokens=api.exchange("one-time-code","verifier")
            assertEquals("Fixture",api.identity(tokens).displayName)
            assertEquals(listOf("worker","default"),api.profiles(tokens).map {it.name})
            api.mcp(tokens,"worker");val refreshed=api.refresh(tokens);assertEquals("renewed-access-fixture",refreshed.access)
            val exchange=server.takeRequest();assertEquals("/dashboard/auth/native/token",exchange.path);assertNull(exchange.getHeader("Authorization"));assertEquals(setOf("code","code_verifier"),Json.parseToJsonElement(exchange.body.readUtf8()).jsonObject.keys)
            listOf("/dashboard/api/auth/me","/dashboard/api/profiles","/dashboard/api/mcp/servers?profile=worker").forEach {path ->val request=server.takeRequest();assertEquals(path,request.path);assertEquals("Bearer management-access-fixture",request.getHeader("Authorization"));assertNull(request.getHeader("Cookie"))}
            val renewal=server.takeRequest();assertEquals("/dashboard/auth/native/refresh",renewal.path);assertNull(renewal.getHeader("Authorization"));assertEquals(setOf("provider","refresh_token"),Json.parseToJsonElement(renewal.body.readUtf8()).jsonObject.keys)
        }
    }
    @Test fun managementRejectsRedirectsHtmlAndChangedIdentityWithoutRetry()=runBlocking {
        MockWebServer().use {server ->
            val api=ManagementClient(server.url("/").toString(),true,true)
            val tokens=ManagementTokens.parse(Json.parseToJsonElement(payload).jsonObject)
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","https://other.example/"))
            assertTrue(runCatching {api.identity(tokens)}.exceptionOrNull() is HermesHttpException)
            server.enqueue(MockResponse().setHeader("Content-Type","text/html").setBody("<html>login</html>"))
            assertTrue(runCatching {api.profiles(tokens)}.isFailure)
            server.enqueue(json("""{"user_id":"other-user","provider":"password"}"""))
            assertTrue(runCatching {api.identity(tokens)}.isFailure)
            server.enqueue(json(payload.replace("fixture-user","other-user")))
            assertTrue(runCatching {api.refresh(tokens)}.isFailure)
            assertEquals(4,server.requestCount)
        }
    }
}
