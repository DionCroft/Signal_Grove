package org.hermesmobile.app

import androidx.room.Room
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher as MockDispatcher
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Separate canonical negotiation over its real HTTP/ticket/WebSocket path. No saved credentials. */
class CanonicalSessionAttachmentContractTest {
    private class Cookies:CookieJar {
        private val values=mutableListOf<Cookie>()
        @Synchronized override fun saveFromResponse(url:HttpUrl,cookies:List<Cookie>) {
            cookies.forEach {cookie->values.removeAll {it.name==cookie.name && it.domain==cookie.domain && it.path==cookie.path};values+=cookie}
        }
        @Synchronized override fun loadForRequest(url:HttpUrl)=values.filter {it.matches(url) && it.expiresAt>System.currentTimeMillis()}
    }
    private data class Pending(val challenge:String,val redirect:HttpUrl,val state:String)
    private class Fixture(val mode:String="normal"):AutoCloseable {
        val resumes=AtomicInteger();val replays=AtomicInteger();val sockets=AtomicInteger()
        val server=MockWebServer()
        val failures=AtomicInteger()
        private val pending=AtomicReference<Pending>()
        private val tickets=ConcurrentHashMap.newKeySet<String>()
        private val code="project-code-"+UUID.randomUUID().toString().replace("-","")
        private val envelope="""{"access_token":"project-access-fixture","refresh_token":"project-refresh-fixture","provider":"stub","user_id":"stub-user-1","expires_at":4102444800,"token_type":"Bearer"}"""
        private fun json(body:String,status:Int=200)=MockResponse().setHeader("Content-Type","application/json").setBody(body).setResponseCode(status)
        private fun listener()=object:WebSocketListener() {
            override fun onOpen(ws:WebSocket,response:Response) {
                sockets.incrementAndGet()
                ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"skin":{},"change_events":true,"replay_epoch":"fixture-ring","session_authority":true}}}""")
            }
            override fun onMessage(ws:WebSocket,text:String) {
                try {
                    val frame=Json.parseToJsonElement(text).jsonObject;val method=frame["method"]!!.jsonPrimitive.content
                    val id=frame["id"]!!;val params=frame["params"]!!.jsonObject
                    fun reply(result:JsonObject)=ws.send(buildJsonObject {put("jsonrpc","2.0");put("id",id);put("result",result)}.toString())
                    when(method) {
                        "runtime.describe"->{
                            check(params.isEmpty())
                            reply(Json.parseToJsonElement("""{"instance_id":"fixture-native-default","profile_id":"/fixture/homes/default","authority_epoch":1,"capabilities":["durable-admission-v1","event-replay-v1"],"session_create":{"sources":["cli"],"parameters":["title"]}}""").jsonObject)
                        }
                        "session.list"->{
                            check(params.keys==setOf("scope","limit") && params["limit"]==JsonPrimitive(200))
                            val scope=params["scope"]!!.jsonPrimitive.content;check(scope in setOf("stored","live"))
                            val row=Json.parseToJsonElement("""{"id":"shared-fixture","session_id":"shared-fixture","resolved_id":"shared-fixture","title":"Fixture legacy history","source":"cli","started_at":1.0,"message_count":2,"running":false,"model":null}""")
                            reply(buildJsonObject {put("scope",scope);put("sessions",JsonArray(listOf(row)))})
                        }
                        "session.resume"->{
                            check(params==Json.parseToJsonElement("""{"session_id":"shared-fixture","omit_messages":true}"""))
                            resumes.incrementAndGet()
                            if(mode=="drop-resume") {ws.close(1000,"fixture-drop");return}
                            ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"sessions.changed","session_id":"","payload":{}}}""")
                            reply(Json.parseToJsonElement("""{"session_id":"shared-fixture","stored_session_id":"shared-fixture","messages":[],"messages_omitted":true,"message_count":2,"running":false,"authority_epoch":1,"replay_epoch":"fixture-ring","last_sequence":0,"subscription_id":"fixture-subscription","revision":0,"execution_generation":0,"pending":[],"prompts":[],"info":{"desktop_protocol":"hermes-gateway-v1","profile_id":"/fixture/homes/default"}}""").jsonObject)
                        }
                        "session.events.since"->{
                            check(params==Json.parseToJsonElement("""{"session_id":"shared-fixture","last_sequence":0,"replay_epoch":"fixture-ring"}"""))
                            replays.incrementAndGet()
                            if(mode=="drop-replay") {ws.close(1000,"fixture-drop");return}
                            reply(Json.parseToJsonElement("""{"events":[],"latest_seq":0,"last_sequence":0,"epoch":"fixture-ring","replay_epoch":"fixture-ring","truncated":false,"snapshot_required":false,"count":0}""").jsonObject)
                        }
                        else->error("Unreviewed fixture RPC.")
                    }
                } catch(_:Exception) {failures.incrementAndGet();ws.close(1011,"fixture-error")}
            }
        }
        init {
            server.start(InetAddress.getByName("127.0.0.1"),0)
            server.dispatcher=object:MockDispatcher() {
                override fun dispatch(q:RecordedRequest):MockResponse=try {
                    val url=checkNotNull(q.requestUrl)
                    when(url.encodedPath) {
                        "/dashboard/auth/native/authorize"->{
                            check(q.method=="GET" && q.getHeader("Authorization")==null && url.queryParameter("code_challenge_method")=="S256")
                            val redirect=checkNotNull(url.queryParameter("redirect_uri")).toHttpUrl()
                            check(redirect.scheme=="http" && redirect.host=="127.0.0.1" && redirect.encodedPath=="/callback")
                            pending.set(Pending(checkNotNull(url.queryParameter("code_challenge")),redirect,checkNotNull(url.queryParameter("state"))))
                            MockResponse().setResponseCode(302).setHeader("Location",server.url("/dashboard/auth/callback").newBuilder().host("127.0.0.1").addQueryParameter("code","stub_code").build())
                        }
                        "/dashboard/auth/callback"->{
                            check(q.method=="GET" && url.queryParameter("code")=="stub_code")
                            val p=checkNotNull(pending.get())
                            MockResponse().setResponseCode(302).setHeader("Location",p.redirect.newBuilder().addQueryParameter("code",code).addQueryParameter("state",p.state).build())
                        }
                        "/dashboard/auth/native/token"->{
                            check(q.method=="POST" && q.getHeader("Authorization")==null && q.getHeader("Cookie")==null)
                            val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject
                            check(body["code"]==JsonPrimitive(code))
                            val challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(body["code_verifier"]!!.jsonPrimitive.content.toByteArray(Charsets.US_ASCII)))
                            check(challenge==pending.get().challenge);json(envelope)
                        }
                        "/dashboard/api/auth/me","/dashboard/api/profiles","/dashboard/api/auth/ws-ticket"->{
                            check(q.getHeader("Authorization")=="Bearer project-access-fixture" && q.getHeader("Cookie")==null)
                            when(url.encodedPath) {
                                "/dashboard/api/auth/me"->{check(q.method=="GET");json("""{"user_id":"stub-user-1","provider":"stub","display_name":"Project fixture"}""")}
                                "/dashboard/api/profiles"->{check(q.method=="GET");json("""{"profiles":[{"name":"default","path":"/fixture/homes/default"},{"name":"worker","path":"/fixture/homes/worker"},{"name":"alias","path":"/fixture/homes/default"}]}""")}
                                else->{
                                    check(q.method=="POST" && Json.parseToJsonElement(q.body.readUtf8())==JsonObject(emptyMap()))
                                    val ticket="project-ticket-"+UUID.randomUUID().toString().replace("-","");tickets+=ticket
                                    json(buildJsonObject {put("ticket",ticket);put("ttl_seconds",30)}.toString())
                                }
                            }
                        }
                        "/dashboard/api/ws"->{
                            check(q.method=="GET" && q.getHeader("Authorization")==null && q.getHeader("Cookie")==null && q.getHeader("Origin")==null)
                            val protocols=checkNotNull(q.getHeader("Sec-WebSocket-Protocol")).split(',').map(String::trim)
                            check(protocols.size==2 && protocols[0]=="hermes-gateway-v1" && protocols[1].startsWith("hermes-gateway-ticket."))
                            check(tickets.remove(protocols[1].removePrefix("hermes-gateway-ticket.")))
                            MockResponse().withWebSocketUpgrade(listener()).setHeader("Sec-WebSocket-Protocol","hermes-gateway-v1")
                        }
                        else->{failures.incrementAndGet();json("{}",404)}
                    }
                } catch(_:Exception) {failures.incrementAndGet();json("""{"error":"fixture_expectation_failed"}""",500)}
            }
        }
        override fun close()=server.close()
    }

    private suspend fun signIn(api:ManagementClient):ManagementTokens {
        val browser=OkHttpClient.Builder().cookieJar(Cookies()).followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).callTimeout(10,TimeUnit.SECONDS).build()
        fun redirect(url:HttpUrl)=browser.newCall(Request.Builder().url(url).build()).execute().use {
            assertEquals(302,it.code);checkNotNull(url.resolve(checkNotNull(it.header("Location"))))
        }
        try {
            return coroutineScope {
                NativeLogin().use {login->
                    val waiting=async(Dispatchers.IO) {login.awaitCode(20)}
                    try {
                        val callback=redirect(login.authorize(api.base))
                        assertEquals(api.base.scheme,callback.scheme);assertEquals(api.base.host,callback.host);assertEquals(api.base.port,callback.port)
                        assertEquals("/dashboard/auth/callback",callback.encodedPath)
                        val destination=redirect(callback);val expected=login.redirect.toHttpUrl()
                        assertEquals(expected.scheme,destination.scheme);assertEquals(expected.host,destination.host);assertEquals(expected.port,destination.port);assertEquals(expected.encodedPath,destination.encodedPath)
                        browser.newCall(Request.Builder().url(destination).build()).execute().use {assertEquals(200,it.code)}
                        api.exchange(waiting.await(),login.verifier)
                    } finally {login.close();waiting.cancelAndJoin()}
                }
            }
        } finally {browser.connectionPool.evictAll();browser.dispatcher.executorService.shutdown()}
    }
    private fun database()=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
    private inline fun <T> withDatabase(block:(HermesDatabase)->T):T {
        val db=database()
        return try {block(db)} finally {db.close()}
    }
    private fun base(f:Fixture)=f.server.url("/dashboard/").newBuilder().host("127.0.0.1").build().toString()
    private fun target(api:ManagementClient,tokens:ManagementTokens,profile:String="default")=ManagementTarget("b".repeat(64),api.base.toString(),profile,tokens.provider+" · "+tokens.userId)
    private suspend fun home(api:ManagementClient,tokens:ManagementTokens)=checkNotNull(api.profiles(tokens).single {it.name=="default"}.pluginHome).identity()

    @Test fun reviewedResumeUsesRealAuthHomeAndDurableReceiptWithoutSubmittingWork() {
        val args=InstrumentationRegistry.getArguments();val external=args.getString("signalGroveCanonicalSessionBase")
        val label=args.getString("signalGroveCanonicalSessionLabel") ?: "mock";require(label.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,80}")))
        val revision=args.getString("signalGroveCanonicalSessionRevision") ?: "mock";require(revision in setOf("mock","stable","prior"))
        val fixture=if(external==null) Fixture() else null
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val credentials=context.getSharedPreferences("credentials",0);val before=credentials.all.toMap()
        val checks=mutableListOf<String>()
        withDatabase {db->
            try {
                val url=(external ?: base(fixture!!)).toHttpUrl()
                require(url.scheme=="http" && url.host=="127.0.0.1" && url.port in 1024..65535 && url.encodedPath=="/dashboard/")
                require(url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null)
                val api=ManagementClient(url.toString(),true,true)
                runBlocking {withTimeout(180_000) {
                    val tokens=signIn(api);val owner=target(api,tokens);val profileHome=home(api,tokens)
                    checks+="native_pkce_login_and_verified_home"
                    val repo=CanonicalSessionsRepository(db.dao(),CompletableDeferred(Unit))
                    val inventory=repo.prepare(owner,profileHome,api,tokens)
                    assertEquals(owner,inventory.binding.target);assertEquals(profileHome,inventory.binding.homeId)
                    checks+="stored_eligibility_keeps_exact_authenticated_owner"
                    if(revision=="prior") {
                        assertTrue(inventory.rows.isEmpty())
                        checks+="prior_remote_login_has_no_legacy_adoption_eligibility"
                    } else {
                        assertTrue(inventory.rows.any {it.sessionId=="shared-fixture"})
                        val review=inventory.review("shared-fixture")
                        assertNull(db.dao().unresolvedManagementOperations(owner.resourceScope,owner.url,profileHome).firstOrNull())
                        checks+="selection_prepares_review_without_a_resume_receipt"
                        val connection=repo.adopt(review,api,tokens)
                        try {
                            val snapshot=connection.attachment.snapshot;val receipt=connection.receipt
                            assertEquals("shared-fixture",snapshot.requestedSessionId);assertEquals("shared-fixture",snapshot.owner.sessionId)
                            assertEquals(owner,snapshot.owner.target);assertEquals(profileHome,snapshot.owner.homeId)
                            assertEquals(2L,snapshot.messageCount)
                            checks+="server_resolved_snapshot_and_message_count_are_bound"
                            assertEquals("confirmed",receipt.state);assertEquals(CanonicalResumeAction.Id,receipt.action);assertEquals(profileHome,receipt.projectHome)
                            checks+="sending_and_acknowledged_adoption_have_a_durable_confirmed_receipt"
                            assertEquals(CanonicalFeedPhase.Following,connection.attachment.initial.status.phase)
                            assertTrue(connection.attachment.initial.events.isEmpty())
                            checks+="attach_first_empty_replay_is_contiguous_without_work_admission"
                            val summary=Json.parseToJsonElement(receipt.resultSummary).jsonObject
                            assertEquals(JsonPrimitive("unproven"),summary["execution_outcome"]);assertFalse(summary.containsKey("execution_state"))
                            assertFalse(receipt.requestSummary.contains("private user fixture"));assertFalse(receipt.resultSummary.contains("private reply fixture"))
                            checks+="receipts_do_not_infer_execution_state_or_store_transcript"
                        } finally {connection.close()}
                        fixture?.let {assertEquals(1,it.resumes.get());assertEquals(1,it.replays.get())}
                        checks+="connection_closes_without_an_interrupt_or_explicit_end_reason"
                    }
                }}
                fixture?.let {assertEquals(0,it.failures.get())}
                assertEquals(before,credentials.all);checks+="saved_credentials_unchanged"
                assertEquals(if(revision=="prior") 4 else 9,checks.size)
                val qa=File(checkNotNull(context.getExternalFilesDir(null)),"qa");check(qa.isDirectory || qa.mkdirs())
                File(qa,"canonical-session-contract-"+label+".json").writeText(buildJsonObject {
                    put("state","PASS_CANONICAL_SESSION_ATTACHMENT");put("revision",revision);put("api",Build.VERSION.SDK_INT)
                    put("checks",JsonArray(checks.map(::JsonPrimitive)));put("savedCredentialsUnchanged",true)
                    put("workerOrExecutionClaim",false);put("completedAtMillis",System.currentTimeMillis())
                }.toString())
            } finally {fixture?.close()}
        }
    }

    @Test fun lostResumeReplySurvivesRepositoryRecreationAndBlocksAliasReplay() {
        Fixture("drop-resume").use {f->withDatabase {db->runBlocking {withTimeout(60_000) {
            val api=ManagementClient(base(f),true,true);val tokens=signIn(api);val owner=target(api,tokens);val profileHome=home(api,tokens)
            val repo=CanonicalSessionsRepository(db.dao(),CompletableDeferred(Unit));val review=repo.prepare(owner,profileHome,api,tokens).review("shared-fixture")
            assertTrue(runCatching {repo.adopt(review,api,tokens)}.isFailure)
            val record=db.dao().unresolvedManagementOperations(owner.resourceScope,owner.url,profileHome).single()
            assertEquals("unknown",record.state);assertEquals(CanonicalResumeAction.Id,record.action);assertEquals(1,f.resumes.get())
            db.dao().recoverManagementOperations()
            val recreated=CanonicalSessionsRepository(db.dao(),CompletableDeferred(Unit))
            assertTrue(runCatching {recreated.adopt(review,api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
            assertEquals(1,f.resumes.get())
            val alias=target(api,tokens,"alias")
            assertTrue(db.dao().unresolvedManagementOperations(alias.resourceScope,alias.url,profileHome).any {it.key==record.key})
            assertEquals(0,f.failures.get())
        }}}}
    }

    @Test fun replayFailurePreservesKnownAdoptionAckAndCannotReuseApproval() {
        Fixture("drop-replay").use {f->withDatabase {db->runBlocking {withTimeout(60_000) {
            val api=ManagementClient(base(f),true,true);val tokens=signIn(api);val owner=target(api,tokens);val profileHome=home(api,tokens)
            val repo=CanonicalSessionsRepository(db.dao(),CompletableDeferred(Unit));val review=repo.prepare(owner,profileHome,api,tokens).review("shared-fixture")
            val error=runCatching {repo.adopt(review,api,tokens)}.exceptionOrNull()
            assertTrue(error is CanonicalAdoptionAcknowledged)
            val receipt=(error as CanonicalAdoptionAcknowledged).receipt;assertEquals("acknowledged",receipt.state)
            assertEquals("acknowledged",db.dao().managementOperation(receipt.key)!!.state)
            assertTrue(runCatching {repo.adopt(review,api,tokens)}.exceptionOrNull() is CanonicalApprovalUsed)
            assertEquals(1,f.resumes.get());assertEquals(1,f.replays.get());assertEquals(0,f.failures.get())
        }}}}
    }

    @Test fun cancelledDurableClaimIsRecoveredWithoutReplayingAnySessionOperation() {
        withDatabase {db->runBlocking {
            val owner=ManagementTarget("b".repeat(64),"https://fixture.test/","default","fixture")
            val record=ManagementOperation("canonical-claim-fixture",owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,CanonicalResumeAction.Id,"history","{}","sending","",1,"a".repeat(64))
            db.dao().claimManagementOperation(record);db.dao().recoverManagementOperations()
            assertEquals("unknown",db.dao().managementOperation(record.key)!!.state)
            assertTrue(db.dao().unresolvedManagementOperations(owner.resourceScope,owner.url,"a".repeat(64)).any {it.key==record.key})
            assertTrue(runCatching {db.dao().claimManagementOperation(record.copy(key="second"))}.exceptionOrNull() is ManagementOutcomePending)
        }}
    }
}
