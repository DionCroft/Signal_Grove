package org.hermesmobile.app

import androidx.room.Room
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher as MockDispatcher
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.hermesmobile.app.ui.*
import org.hermesmobile.app.updates.UpdateWorkGuard
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
        val mutations=AtomicInteger();var mutationReceived:(JsonObject)->Unit={}
        val resumes=AtomicInteger();val replays=AtomicInteger();val sockets=AtomicInteger();val pings=AtomicInteger()
        val profileReadAttempts=AtomicInteger()
        val server=MockWebServer()
        val failures=AtomicInteger()
        private val pending=AtomicReference<Pending>()
        private val tickets=ConcurrentHashMap.newKeySet<String>()
        private val code="project-code-"+UUID.randomUUID().toString().replace("-","")
        private val envelope="""{"access_token":"project-access-fixture","refresh_token":"project-refresh-fixture","provider":"stub","user_id":"stub-user-1","expires_at":4102444800,"token_type":"Bearer"}"""
        private fun json(body:String,status:Int=200)=MockResponse().setHeader("Content-Type","application/json").setBody(body).setResponseCode(status)
        private fun activity(sequence:Int):JsonObject=buildJsonObject {
            val type=when(sequence) {1,4->"status.update";2->"reasoning.delta";3->"message.delta";else->error("Unreviewed fixture sequence.")}
            val text=when(sequence) {1->"Fixture public status one";2->"fixture-private-reasoning-sentinel";3->"Fixture public reply three";4->"Fixture public live status four";else->error("Unreviewed fixture sequence.")}
            put("type",type);put("session_id","shared-fixture");put("replay_epoch","fixture-ring");put("seq",sequence)
            put("payload",buildJsonObject {put("text",text);if(type=="status.update") put("kind","status")})
        }
        private fun publish(ws:WebSocket,sequence:Int) {
            ws.send(buildJsonObject {put("jsonrpc","2.0");put("method","event");put("params",activity(sequence))}.toString())
        }
        private fun listener()=object:WebSocketListener() {
            override fun onOpen(ws:WebSocket,response:Response) {
                sockets.incrementAndGet()
                ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"skin":{},"change_events":true,"heartbeat":${mode in setOf("viewer","viewer-activity")},"replay_epoch":"fixture-ring","session_authority":true}}}""")
            }
            override fun onMessage(ws:WebSocket,text:String) {
                try {
                    val frame=Json.parseToJsonElement(text).jsonObject;val method=frame["method"]!!.jsonPrimitive.content
                    val id=frame["id"]!!;val params=frame["params"]!!.jsonObject
                    fun reply(result:JsonObject)=ws.send(buildJsonObject {put("jsonrpc","2.0");put("id",id);put("result",result)}.toString())
                    when(method) {
                        "gateway.ping"->{
                            check(mode in setOf("viewer","viewer-activity") && params.isEmpty());pings.incrementAndGet()
                            if(mode=="viewer-activity" && pings.get()==3) publish(ws,4)
                            reply(buildJsonObject {put("ok",true)})
                        }
                        "runtime.describe"->{
                            check(params.isEmpty())
                            reply(Json.parseToJsonElement("""{"instance_id":"fixture-native-default","profile_id":"/fixture/homes/default","authority_epoch":1,"capabilities":["durable-admission-v1","event-replay-v1"],"session_create":{"sources":["cli"],"parameters":["title"]}}""").jsonObject)
                        }
                        "session.mutate"->{
                            check(mode.startsWith("workspace"))
                            check(params.keys==setOf("session_id","request_id","expected_revision","expected_generation","operation","payload"))
                            check(params["session_id"]==JsonPrimitive("shared-fixture") && params["operation"]==JsonPrimitive("workspace"))
                            check(params["expected_revision"]==JsonPrimitive(2) && params["expected_generation"]==JsonPrimitive(3))
                            check(params["payload"]==buildJsonObject {put("cwd","/fixture/new project")})
                            mutations.incrementAndGet();mutationReceived(params)
                            if(mode.startsWith("workspace-refusal-")) {
                                ws.send(buildJsonObject {put("jsonrpc","2.0");put("id",id);put("error",buildJsonObject {
                                    put("code",4001);put("message","session_busy");put("data",buildJsonObject {put("reason","session_busy");if(mode.endsWith("compression-lock")) put("field","compression")})
                                })}.toString());return
                            }
                            if(mode in setOf("workspace-drop","workspace-drop-roundtrip")) {ws.close(1000,"fixture");return}
                            reply(buildJsonObject {put("session_id","shared-fixture");put("operation","workspace");put("revision",3);put("execution_generation",4);put("cwd","/fixture/new project")})
                        }
                        "session.list"->{
                            check(params.keys==setOf("scope","limit") && params["limit"]==JsonPrimitive(200))
                            val scope=params["scope"]!!.jsonPrimitive.content;check(scope in setOf("stored","live"))
                            val row=Json.parseToJsonElement("""{"id":"shared-fixture","session_id":"shared-fixture","resolved_id":"shared-fixture","title":"Fixture legacy history","source":"cli","started_at":1.0,"message_count":2,"running":false,"model":null}""")
                            reply(buildJsonObject {put("scope",scope);put("sessions",if(mode in setOf("workspace-roundtrip","workspace-drop-roundtrip","workspace-refusal-turn-lease","workspace-refusal-compression-lock") && scope=="stored") JsonArray(emptyList()) else JsonArray(listOf(if(mode in setOf("workspace-roundtrip","workspace-drop-roundtrip","workspace-refusal-turn-lease","workspace-refusal-compression-lock")) JsonObject(row.jsonObject+mapOf("message_count" to JsonPrimitive(0))) else row)))})
                        }
                        "session.resume"->{
                            check(params==Json.parseToJsonElement("""{"session_id":"shared-fixture","omit_messages":true}"""))
                            resumes.incrementAndGet()
                            if(mode=="drop-resume") {ws.close(1000,"fixture-drop");return}
                            ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"sessions.changed","session_id":"","payload":{}}}""")
                            val raw=Json.parseToJsonElement("""{"session_id":"shared-fixture","stored_session_id":"shared-fixture","messages":[],"messages_omitted":true,"message_count":2,"running":false,"authority_epoch":1,"replay_epoch":"fixture-ring","last_sequence":0,"subscription_id":"fixture-subscription","revision":0,"execution_generation":0,"pending":[],"prompts":[],"info":{"desktop_protocol":"hermes-gateway-v1","profile_id":"/fixture/homes/default"}}""").jsonObject
                            val roundTrip=mode in setOf("workspace-roundtrip","workspace-drop-roundtrip","workspace-refusal-turn-lease","workspace-refusal-compression-lock");val changed=mutations.get()>0 && !mode.startsWith("workspace-refusal-")
                            reply(if(roundTrip) JsonObject(raw+mapOf(
                                "message_count" to JsonPrimitive(0),"revision" to JsonPrimitive(if(changed) 3 else 2),
                                "execution_generation" to JsonPrimitive(if(changed) 4 else 3),
                                "info" to JsonObject(raw.getValue("info").jsonObject+mapOf("cwd" to JsonPrimitive(if(changed) "/fixture/new project" else "/fixture/old"))))) else raw)
                            if(mode=="viewer-activity") (1..3).forEach {publish(ws,it)}
                        }
                        "session.events.since"->{
                            check(params==Json.parseToJsonElement("""{"session_id":"shared-fixture","last_sequence":0,"replay_epoch":"fixture-ring"}"""))
                            replays.incrementAndGet()
                            if(mode=="drop-replay") {ws.close(1000,"fixture-drop");return}
                            val result=Json.parseToJsonElement("""{"events":[],"latest_seq":0,"last_sequence":0,"epoch":"fixture-ring","replay_epoch":"fixture-ring","truncated":false,"snapshot_required":false,"count":0}""").jsonObject
                            reply(if(mode=="viewer-activity") JsonObject(result+mapOf("events" to JsonArray((1..3).map(::activity)),"latest_seq" to JsonPrimitive(3),"last_sequence" to JsonPrimitive(3),"count" to JsonPrimitive(3))) else result)
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
                        "/dashboard/auth/native/refresh"->{
                            check(mode in setOf("workspace-roundtrip","workspace-drop-roundtrip","workspace-refusal-turn-lease","workspace-refusal-compression-lock") && q.method=="POST" && q.getHeader("Authorization")==null && q.getHeader("Cookie")==null)
                            val body=Json.parseToJsonElement(q.body.readUtf8()).jsonObject
                            check(body.keys==setOf("provider","refresh_token") && body["provider"]==JsonPrimitive("stub"))
                            check(body["refresh_token"]==Json.parseToJsonElement(envelope).jsonObject.getValue("refresh_token"))
                            json(envelope)
                        }
                        "/dashboard/api/auth/me","/dashboard/api/profiles","/dashboard/api/auth/ws-ticket"->{
                            check(q.getHeader("Authorization")=="Bearer project-access-fixture" && q.getHeader("Cookie")==null)
                            when(url.encodedPath) {
                                "/dashboard/api/auth/me"->{check(q.method=="GET");json("""{"user_id":"stub-user-1","provider":"stub","display_name":"Project fixture"}""")}
                                "/dashboard/api/profiles"->{check(q.method=="GET");if(mode=="workspace-remap" && mutations.get()>0) json("""{"profiles":[{"name":"default","path":"/fixture/homes/changed"}]}""") else if(mode=="drop-profile-once" && replays.get()>0 && profileReadAttempts.incrementAndGet()==1) MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) else json("""{"profiles":[{"name":"default","path":"/fixture/homes/default"},{"name":"worker","path":"/fixture/homes/worker"},{"name":"alias","path":"/fixture/homes/default"}]}""")}
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

    @Test fun viewerHeartbeatUsesRealRouterAndPersistsOnlyAppliedCursor()=viewerContract(false)
    @Test fun advancingEventsHidePrivateTextAndPersistEachAppliedCursorOnce()=viewerContract(true)
    private fun viewerContract(activity:Boolean) {
        val args=InstrumentationRegistry.getArguments();val external=args.getString("signalGroveCanonicalSessionBase")
        val label=args.getString("signalGroveCanonicalSessionLabel") ?: "mock"
        require(label.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,80}")))
        val revision=args.getString("signalGroveCanonicalSessionRevision") ?: "mock"
        require(revision in setOf("mock","stable","prior","main"))
        val fixture=if(external==null) Fixture(if(activity) "viewer-activity" else "viewer") else null
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val credentials=context.getSharedPreferences("credentials",0);val before=credentials.all.toMap()
        val checks=mutableListOf<String>()
        withDatabase {db->
            try {
                val url=(external ?: base(fixture!!)).toHttpUrl()
                require(url.scheme=="http" && url.host=="127.0.0.1" && url.port in 1024..65535 && url.encodedPath=="/dashboard/")
                require(url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null)
                val api=ManagementClient(url.toString(),true,true)
                runBlocking {withTimeout(60_000) {
                    val tokens=signIn(api);val owner=target(api,tokens);val profileHome=home(api,tokens)
                    checks+="native_pkce_and_verified_profile_home"
                    val repo=CanonicalSessionsRepository(db.dao(),CompletableDeferred(Unit));val guard=UpdateWorkGuard()
                    val controllerScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
                    val appliedSequences=MutableStateFlow<List<Long>>(emptyList())
                    val observedRepository=object:CanonicalViewerRepository by repo {
                        override suspend fun recordApplied(key:String,eventOwner:CanonicalFeedOwner,cursor:CanonicalReplayCursor) {
                            repo.recordApplied(key,eventOwner,cursor);appliedSequences.value=appliedSequences.value+cursor.sequence
                        }
                    }
                    val access=CanonicalViewerAccess(owner,profileHome,1,api,tokens)
                    val viewer=CanonicalViewerController(controllerScope,observedRepository,{access},guard::set,heartbeatDelay=2500,heartbeatDeadline=5000)
                    try {
                        withContext(Dispatchers.Main.immediate) {viewer.load(CanonicalSessionScope.Stored)}
                        val loaded=viewer.state.first {it.phase in setOf(CanonicalViewerPhase.Inventory,CanonicalViewerPhase.Held)}
                        assertEquals(CanonicalViewerPhase.Inventory,loaded.phase)
                        val inventory=checkNotNull(loaded.inventory);assertEquals(owner,inventory.binding.target)
                        assertEquals(profileHome,inventory.binding.homeId)
                        assertNull(db.dao().unresolvedManagementOperations(owner.resourceScope,owner.url,profileHome).firstOrNull())
                        assertTrue(db.dao().canonicalViewerReceipts(owner.apiScope,owner.url,owner.profile,owner.actor,profileHome,"shared-fixture").isEmpty())
                        checks+="stored_inventory_has_no_adoption_receipt"
                        if(revision=="prior") {
                            assertTrue(inventory.rows.isEmpty());checks+="prior_remote_identity_has_no_legacy_eligibility"
                        } else {
                            assertTrue(inventory.rows.any {it.sessionId=="shared-fixture"})
                            withContext(Dispatchers.Main.immediate) {viewer.review("shared-fixture")}
                            val review=checkNotNull(viewer.state.value.review)
                            assertTrue(viewer.reviewCurrent(review));assertFalse(guard.active.value)
                            withContext(Dispatchers.Main.immediate) {viewer.approve(review,false)}
                            val opened=viewer.state.first {it.phase in setOf(CanonicalViewerPhase.Following,CanonicalViewerPhase.Held)}
                            assertEquals(CanonicalViewerPhase.Following,opened.phase);assertTrue(guard.active.value)
                            assertNull(opened.review);assertEquals("shared-fixture",opened.sessionId)
                            checks+="one_use_review_opens_a_guarded_foreground_viewer"
                            val firstHeartbeat=checkNotNull(opened.heartbeatAt)
                            val periodic=viewer.state.first {it.phase==CanonicalViewerPhase.Held || (it.heartbeatAt ?: 0)>firstHeartbeat}
                            assertEquals(CanonicalViewerPhase.Following,periodic.phase)
                            assertTrue(checkNotNull(periodic.heartbeatAt)>firstHeartbeat)
                            checks+="initial_and_periodic_real_outer_heartbeat_verify_owner_home"
                            if(activity) {
                                val live=viewer.state.first {it.phase==CanonicalViewerPhase.Held || it.observed>=4}
                                assertEquals(CanonicalViewerPhase.Following,live.phase)
                                val saved=appliedSequences.first {it.lastOrNull()==4L}
                                assertEquals(listOf(3L,4L),saved)
                                assertEquals(listOf(1L,2L,3L,4L),live.activities.map {it.sequence})
                                assertEquals(listOf("Fixture public status one","","Fixture public reply three","Fixture public live status four"),live.activities.map {it.text})
                                assertEquals(Words.ViewerPrivateActivity,live.activities[1].kind)
                                assertEquals(Words.ViewerReplyActivity,live.activities[2].kind)
                                assertEquals(4L,live.observed);assertEquals(4L,live.cursor!!.sequence)
                                checks+="actual_replay_and_live_activity_apply_once_without_private_text"
                                checks+="confirmed_room_cursor_advances_from_three_to_four"
                            }
                            val current=viewer.state.value
                            val receipt=checkNotNull(db.dao().managementOperation(current.receiptKey))
                            assertEquals("confirmed",receipt.state);assertEquals(CanonicalResumeAction.Id,receipt.action)
                            val applied=checkNotNull(repo.lastApplied(owner,profileHome,"shared-fixture"))
                            assertEquals(current.cursor,applied.cursor);assertEquals(owner,applied.owner.target)
                            assertEquals(profileHome,applied.owner.homeId);assertEquals(current.sessionId,applied.owner.sessionId)
                            checks+="applied_cursor_is_durable_and_exactly_owner_bound"
                            val summary=Json.parseToJsonElement(receipt.resultSummary).jsonObject
                            assertEquals(JsonPrimitive("unproven"),summary["execution_outcome"])
                            assertFalse(summary.containsKey("execution_state"))
                            assertFalse(receipt.requestSummary.contains("private user fixture"));assertFalse(receipt.resultSummary.contains("private reply fixture"))
                            checks+="receipt_contains_no_transcript_or_execution_inference"
                        }
                    } finally {
                        withContext(Dispatchers.Main.immediate) {viewer.close()}
                        controllerScope.coroutineContext[Job]!!.cancelAndJoin()
                    }
                    assertFalse(guard.active.value);assertEquals(CanonicalViewerPhase.Closed,viewer.state.value.phase)
                    checks+="close_releases_installation_guard_without_reopen"
                    fixture?.let {assertEquals(1,it.resumes.get());assertEquals(1,it.replays.get());assertEquals(2,it.sockets.get());assertEquals(3,it.pings.get());assertEquals(0,it.failures.get())}
                }}
                assertEquals(before,credentials.all);checks+="saved_credentials_unchanged"
                assertEquals(if(revision=="prior") 5 else if(activity) 10 else 8,checks.size)
                val qa=File(checkNotNull(context.getExternalFilesDir(null)),"qa");check(qa.isDirectory || qa.mkdirs())
                File(qa,(if(activity) "canonical-viewer-activity-contract-" else "canonical-viewer-contract-")+label+".json").writeText(buildJsonObject {
                    put("state",if(activity) "PASS_CANONICAL_VIEWER_ACTIVITY" else "PASS_CANONICAL_VIEWER_HEARTBEAT");put("revision",revision);put("api",Build.VERSION.SDK_INT)
                    put("checks",JsonArray(checks.map(::JsonPrimitive)));put("savedCredentialsUnchanged",true)
                    put("workerOrExecutionClaim",false);put("completedAtMillis",System.currentTimeMillis())
                }.toString())
            } finally {fixture?.close()}
        }
    }

    @Test fun reviewedResumeUsesRealAuthHomeAndDurableReceiptWithoutSubmittingWork() {
        val args=InstrumentationRegistry.getArguments();val external=args.getString("signalGroveCanonicalSessionBase")
        val label=args.getString("signalGroveCanonicalSessionLabel") ?: "mock";require(label.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,80}")))
        val revision=args.getString("signalGroveCanonicalSessionRevision") ?: "mock";require(revision in setOf("mock","stable","prior","main"))
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

    @Test fun postAckProfileRecoveryNeverRepeatsResumeOrOpensAnotherSocket() {
        Fixture("drop-profile-once").use {f->withDatabase {db->runBlocking {withTimeout(60_000) {
            val api=ManagementClient(base(f),true,true);val tokens=signIn(api);val owner=target(api,tokens);val profileHome=home(api,tokens)
            val repo=CanonicalSessionsRepository(db.dao(),CompletableDeferred(Unit))
            val review=repo.prepare(owner,profileHome,api,tokens).review("shared-fixture")
            val connection=repo.adopt(review,api,tokens)
            try {
                assertEquals("confirmed",connection.receipt.state)
                assertEquals("confirmed",db.dao().managementOperation(connection.receipt.key)!!.state)
                assertEquals(CanonicalFeedPhase.Following,connection.attachment.initial.status.phase)
                assertTrue(db.dao().unresolvedManagementOperations(owner.resourceScope,owner.url,profileHome).isEmpty())
                assertEquals(2,f.profileReadAttempts.get())
                assertEquals(1,f.resumes.get());assertEquals(1,f.replays.get());assertEquals(2,f.sockets.get())
                assertEquals(0,f.failures.get())
            } finally {connection.close()}
        }}}}
    }    private fun workspaceSnapshot(target:ManagementTarget,home:String)=CanonicalSessionSnapshot(
        CanonicalFeedOwner(target,home,"/fixture/homes/default","fixture-native-default","shared-fixture",1),
        "shared-fixture","shared-fixture","fixture-sub",CanonicalReplayCursor("fixture-ring",0),2,3,false,2,true,CanonicalWorkspaceDirectory.Reported("/fixture/old"))

    @Test fun workspaceUsesNativeTicketTransportAndDurableClaimBeforeOneAcknowledgedMutation()=runBlocking {
        Fixture("workspace").use {f->
            val api=ManagementClient(base(f),true,true);val tokens=signIn(api)
            val target=ManagementTarget("a".repeat(64),api.base.toString(),"default",tokens.provider+" · "+tokens.userId)
            val home=checkNotNull(PluginHome.project(JsonPrimitive("/fixture/homes/default"))).identity()
            val database=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
            try {
                val dao=database.dao();val repo=CanonicalSessionsRepository(dao,CompletableDeferred(Unit))
                val review=CanonicalWorkspaceReview.create(workspaceSnapshot(target,home),"/fixture/new project")
                f.mutationReceived={params->runBlocking {
                    val row=checkNotNull(dao.managementOperation(params["request_id"]!!.jsonPrimitive.content))
                    check(row.state=="sending" && row.action==CanonicalWorkspaceAction.Id && row.projectHome==home)
                }}
                val ack=repo.changeWorkspace(review,api,tokens)
                assertEquals("/fixture/new project",ack.directory.path)
                val row=checkNotNull(dao.managementOperation(review.requestId));assertEquals("acknowledged",row.state)
                assertFalse(row.requestSummary.contains("/fixture"));assertFalse(row.resultSummary.contains("/fixture"))
                assertEquals(1,f.mutations.get());assertEquals(0,f.resumes.get());assertEquals(0,f.replays.get())
                assertEquals(0,f.failures.get());assertTrue(dao.unresolvedManagementOperations(target.resourceScope,target.url,home).isEmpty())
                assertTrue(runCatching {repo.changeWorkspace(review,api,tokens)}.isFailure);assertEquals(1,f.mutations.get())
            } finally {database.close()}
        }
    }
    @Test fun lostWorkspaceAckPersistsUnknownAcrossRepositoryRecoveryAndBlocksAliasWrites()=runBlocking {
        Fixture("workspace-drop").use {f->
            val api=ManagementClient(base(f),true,true);val tokens=signIn(api)
            val target=ManagementTarget("b".repeat(64),api.base.toString(),"default",tokens.provider+" · "+tokens.userId)
            val home=checkNotNull(PluginHome.project(JsonPrimitive("/fixture/homes/default"))).identity()
            val database=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
            try {
                val dao=database.dao();val repo=CanonicalSessionsRepository(dao,CompletableDeferred(Unit))
                val review=CanonicalWorkspaceReview.create(workspaceSnapshot(target,home),"/fixture/new project")
                assertTrue(runCatching {repo.changeWorkspace(review,api,tokens)}.isFailure)
                assertEquals("unknown",dao.managementOperation(review.requestId)!!.state);dao.recoverManagementOperations()
                val alias=target.copy(apiScope="c".repeat(64),profile="alias")
                assertEquals(1,dao.unresolvedManagementOperations(alias.resourceScope,alias.url,home).size)
                val next=CanonicalWorkspaceReview.create(workspaceSnapshot(alias,home),"/fixture/new project")
                val restored=CanonicalSessionsRepository(dao,CompletableDeferred(Unit))
                assertTrue(runCatching {restored.changeWorkspace(next,api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
                assertEquals(1,f.mutations.get());assertEquals(0,f.failures.get());assertNull(dao.managementOperation(next.requestId))
                restored.reviewUnknown(target,home,review.requestId,api,tokens)
                assertEquals("reviewed",dao.managementOperation(review.requestId)!!.state);assertEquals(1,f.mutations.get())
            } finally {database.close()}
        }
    }
    @Test fun workspaceAckRemainsKnownWhenProfileReadbackRemapsTheHome()=runBlocking {
        Fixture("workspace-remap").use {f->
            val api=ManagementClient(base(f),true,true);val tokens=signIn(api)
            val target=ManagementTarget("d".repeat(64),api.base.toString(),"default",tokens.provider+" · "+tokens.userId)
            val home=checkNotNull(PluginHome.project(JsonPrimitive("/fixture/homes/default"))).identity()
            val database=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
            try {
                val dao=database.dao();val repo=CanonicalSessionsRepository(dao,CompletableDeferred(Unit))
                val review=CanonicalWorkspaceReview.create(workspaceSnapshot(target,home),"/fixture/new project")
                assertTrue(runCatching {repo.changeWorkspace(review,api,tokens)}.exceptionOrNull() is CanonicalWorkspaceAcknowledged)
                assertEquals("acknowledged",dao.managementOperation(review.requestId)!!.state)
                assertEquals(1,f.mutations.get());assertEquals(0,f.failures.get())
            } finally {database.close()}
        }
    }


    /** The same assertions run against the bounded mock or an explicitly staged real owner. */
    @Test fun ownerWorkspaceRoundTripUsesAuthRoomAndIndependentFreshReadback() {
        val args=InstrumentationRegistry.getArguments();val external=args.getString("signalGroveOwnerWorkspaceBase")
        val label=args.getString("signalGroveOwnerWorkspaceLabel") ?: "mock"
        require(label.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,80}")))
        val commit=args.getString("signalGroveOwnerWorkspaceCommit") ?: "mock"
        if(external!=null) require(commit in setOf("2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19","3b421578588243f920886e6fe79a6faa87ea026e"))
        val fixture=if(external==null) Fixture("workspace-roundtrip") else null
        val session=if(external==null) "shared-fixture" else checkNotNull(args.getString("signalGroveOwnerWorkspaceSession"))
        val beforeDirectory=if(external==null) "/fixture/old" else checkNotNull(args.getString("signalGroveOwnerWorkspaceBefore"))
        val afterDirectory=if(external==null) "/fixture/new project" else checkNotNull(args.getString("signalGroveOwnerWorkspaceAfter"))
        require(CanonicalWorkspaceDirectory.absolute(beforeDirectory) && CanonicalWorkspaceDirectory.absolute(afterDirectory) && beforeDirectory!=afterDirectory)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val credentials=context.getSharedPreferences("credentials",0);val beforeCredentials=credentials.all.toMap()
        val databaseName="canonical-owner-fixture-"+UUID.randomUUID()+".db"
        check(!context.getDatabasePath(databaseName).exists())
        var db=Room.databaseBuilder(context,HermesDatabase::class.java,databaseName).build()
        val checks=mutableListOf<String>();var requestId:String?=null;var completed=false;var errorType:String?=null
        try {
            val url=(external ?: base(checkNotNull(fixture))).toHttpUrl()
            require(url.scheme=="http" && url.host=="127.0.0.1" && url.port in 1024..65535 && url.encodedPath=="/dashboard/")
            require(url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null)
            val api=ManagementClient(url.toString(),true,true)
            runBlocking {withTimeout(180_000) {
                val signed=signIn(api);val tokens=api.refresh(signed)
                assertEquals(signed.userId,tokens.userId);assertEquals(signed.provider,tokens.provider)
                val identity=api.identity(tokens);assertEquals(tokens.userId,identity.userId);assertEquals(tokens.provider,identity.provider)
                val owner=target(api,tokens);val profileHome=home(api,tokens)
                if(external!=null) assertEquals(PluginHome.project(JsonPrimitive(checkNotNull(args.getString("signalGroveOwnerWorkspaceHome"))))!!.identity(),profileHome)
                checks+="native_pkce_callback_refresh_identity_and_profile_home"
                var repo=CanonicalSessionsRepository(db.dao(),CompletableDeferred(Unit))
                val stored=repo.prepare(owner,profileHome,api,tokens,CanonicalSessionScope.Stored)
                assertTrue(stored.rows.none {it.sessionId==session})
                checks+="http_native_only_stored_scope_does_not_manufacture_eligibility"
                val inventory=repo.prepare(owner,profileHome,api,tokens,CanonicalSessionScope.Live)
                assertTrue(inventory.rows.any {it.sessionId==session})
                val first=repo.adopt(inventory.review(session),api,tokens)
                val snapshot=try {
                    first.attachment.snapshot.also {snap->
                        assertEquals(session,snap.owner.sessionId);assertEquals(owner,snap.owner.target);assertEquals(profileHome,snap.owner.homeId)
                        assertEquals(0L,snap.messageCount);assertFalse(snap.reportedRunning)
                        assertEquals(beforeDirectory,(snap.workspace as CanonicalWorkspaceDirectory.Reported).path)
                        assertEquals("confirmed",first.receipt.state)
                    }
                } finally {first.close()}
                checks+="reviewed_actual_session_attachment_preserves_empty_history_and_workspace"
                val review=CanonicalWorkspaceReview.create(snapshot,afterDirectory);requestId=review.requestId
                val ack=repo.changeWorkspace(review,api,tokens)
                assertEquals(session,ack.sessionId);assertEquals(afterDirectory,ack.directory.path)
                assertEquals(snapshot.revision+1,ack.revision);assertEquals(snapshot.executionGeneration+1,ack.generation)
                val saved=checkNotNull(db.dao().managementOperation(review.requestId))
                assertEquals("acknowledged",saved.state);assertEquals(CanonicalWorkspaceAction.Id,saved.action);assertEquals(profileHome,saved.projectHome)
                assertFalse(saved.requestSummary.contains(beforeDirectory));assertFalse(saved.requestSummary.contains(afterDirectory));assertFalse(saved.resultSummary.contains(afterDirectory))
                checks+="one_use_fenced_mutation_saves_redacted_acknowledgement"
                assertTrue(runCatching {repo.changeWorkspace(review,api,tokens)}.isFailure)
                checks+="consumed_review_cannot_send_another_mutation"
                db.close();db=Room.databaseBuilder(context,HermesDatabase::class.java,databaseName).build()
                assertEquals(saved,db.dao().managementOperation(review.requestId))
                repo=CanonicalSessionsRepository(db.dao(),CompletableDeferred(Unit))
                checks+="actual_room_file_reopen_retains_exact_workspace_receipt"
                val fresh=repo.prepare(owner,profileHome,api,tokens,CanonicalSessionScope.Live)
                val second=repo.adopt(fresh.review(session),api,tokens)
                try {
                    val observed=second.attachment.snapshot
                    assertEquals(afterDirectory,(observed.workspace as CanonicalWorkspaceDirectory.Reported).path)
                    assertEquals(ack.revision,observed.revision);assertEquals(ack.generation,observed.executionGeneration)
                    assertEquals(snapshot.owner,observed.owner);assertEquals(0L,observed.messageCount);assertFalse(observed.reportedRunning)
                    assertEquals("acknowledged",db.dao().managementOperation(review.requestId)!!.state)
                } finally {second.close()}
                checks+="independent_fresh_attachment_observes_new_directory_without_falsely_confirming_ack"
                assertTrue(db.dao().unresolvedManagementOperations(owner.resourceScope,owner.url,profileHome).isEmpty())
                fixture?.let {assertEquals(1,it.mutations.get());assertEquals(2,it.resumes.get());assertEquals(2,it.replays.get());assertEquals(0,it.failures.get())}
                assertEquals(beforeCredentials,credentials.all)
                checks+="saved_credentials_unchanged_and_no_unknown_write_barrier"
            }}
            completed=true
        } catch(error:Throwable) {errorType=error.javaClass.simpleName;throw error}
        finally {
            db.close();fixture?.close()
            val qa=File(checkNotNull(context.getExternalFilesDir(null)),"qa");check(qa.isDirectory || qa.mkdirs())
            File(qa,"owner-workspace-"+label+".json").writeText(buildJsonObject {
                put("state",if(completed) "PASS_OWNER_WORKSPACE_ANDROID_ROUNDTRIP" else "FAILED_OWNER_WORKSPACE_ANDROID_ROUNDTRIP")
                put("backend",if(external==null) "mock" else "actual-owner");put("sourceCommit",commit);put("api",Build.VERSION.SDK_INT);put("inventoryScope","live")
                put("checks",JsonArray(checks.map(::JsonPrimitive)));put("databaseName",databaseName)
                requestId?.let {put("requestId",it)};errorType?.let {put("errorType",it)}
                put("beforeDirectorySha256",directoryHash(beforeDirectory));put("afterDirectorySha256",directoryHash(afterDirectory))
                put("savedCredentialsUnchanged",beforeCredentials==credentials.all);put("modelOrWorkerExecutionClaim",false)
                put("completedAtMillis",System.currentTimeMillis())
            }.toString())
        }
    }


    @Test fun ownerWorkspaceLostAckSurvivesRoomReopenAndBlocksFurtherWrites()=ownerWorkspaceUnknown(null)

    @Test fun ownerWorkspaceBusyRefusalRetainsUnknownAndBlocksAfterHostGuardReleased() {
        val expected=InstrumentationRegistry.getArguments().getString("signalGroveOwnerWorkspaceRefusal") ?: "turn-lease"
        require(expected in setOf("turn-lease","compression-lock"));ownerWorkspaceUnknown(expected)
    }

    private fun ownerWorkspaceUnknown(expectedRefusal:String?) {
        val args=InstrumentationRegistry.getArguments();val external=args.getString("signalGroveOwnerWorkspaceBase")
        val label=args.getString("signalGroveOwnerWorkspaceLabel") ?: "mock"
        require(label.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,80}")))
        val commit=args.getString("signalGroveOwnerWorkspaceCommit") ?: "mock"
        if(external!=null) require(commit in setOf("2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19","3b421578588243f920886e6fe79a6faa87ea026e"))
        val fixture=if(external==null) Fixture(if(expectedRefusal==null) "workspace-drop-roundtrip" else "workspace-refusal-"+expectedRefusal) else null
        val session=if(external==null) "shared-fixture" else checkNotNull(args.getString("signalGroveOwnerWorkspaceSession"))
        val beforeDirectory=if(external==null) "/fixture/old" else checkNotNull(args.getString("signalGroveOwnerWorkspaceBefore"))
        val afterDirectory=if(external==null) "/fixture/new project" else checkNotNull(args.getString("signalGroveOwnerWorkspaceAfter"))
        require(CanonicalWorkspaceDirectory.absolute(beforeDirectory) && CanonicalWorkspaceDirectory.absolute(afterDirectory) && beforeDirectory!=afterDirectory)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val credentials=context.getSharedPreferences("credentials",0);val beforeCredentials=credentials.all.toMap()
        val databaseName="canonical-owner-unknown-fixture-"+UUID.randomUUID()+".db"
        check(!context.getDatabasePath(databaseName).exists())
        var db=Room.databaseBuilder(context,HermesDatabase::class.java,databaseName).build()
        val checks=mutableListOf<String>();var requestId:String?=null;var completed=false;var errorType:String?=null
        try {
            val url=(external ?: base(checkNotNull(fixture))).toHttpUrl()
            require(url.scheme=="http" && url.host=="127.0.0.1" && url.port in 1024..65535 && url.encodedPath=="/dashboard/")
            require(url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null)
            val api=ManagementClient(url.toString(),true,true)
            runBlocking {withTimeout(180_000) {
                val signed=signIn(api);val tokens=api.refresh(signed)
                assertEquals(signed.userId,tokens.userId);assertEquals(signed.provider,tokens.provider)
                val identity=api.identity(tokens);assertEquals(tokens.userId,identity.userId);assertEquals(tokens.provider,identity.provider)
                val owner=target(api,tokens);val profileHome=home(api,tokens)
                if(external!=null) assertEquals(PluginHome.project(JsonPrimitive(checkNotNull(args.getString("signalGroveOwnerWorkspaceHome"))))!!.identity(),profileHome)
                checks+="native_auth_refresh_identity_and_verified_home"
                var repo=CanonicalSessionsRepository(db.dao(),CompletableDeferred(Unit))
                val inventory=repo.prepare(owner,profileHome,api,tokens,CanonicalSessionScope.Live)
                val connection=repo.adopt(inventory.review(session),api,tokens)
                val snapshot=try {connection.attachment.snapshot.also {
                    assertEquals(owner,it.owner.target);assertEquals(session,it.owner.sessionId);assertEquals(profileHome,it.owner.homeId)
                    assertEquals(beforeDirectory,(it.workspace as CanonicalWorkspaceDirectory.Reported).path)
                    assertEquals(0L,it.messageCount);assertFalse(it.reportedRunning)
                }} finally {connection.close()}
                checks+="explicit_live_attachment_and_old_directory_verified"
                val review=CanonicalWorkspaceReview.create(snapshot,afterDirectory);requestId=review.requestId
                fixture?.mutationReceived={params->runBlocking {
                    val row=checkNotNull(db.dao().managementOperation(params.getValue("request_id").jsonPrimitive.content))
                    assertEquals("sending",row.state);assertEquals(CanonicalWorkspaceAction.Id,row.action)
                }}
                val failure=runCatching {repo.changeWorkspace(review,api,tokens)}.exceptionOrNull()
                assertNotNull(failure);assertFalse(failure is CanonicalWorkspaceAcknowledged)
                if(expectedRefusal!=null) {
                    assertTrue(failure is CanonicalWorkspaceRefused)
                    assertEquals(4001,(failure as CanonicalWorkspaceRefused).code)
                    assertEquals(if(expectedRefusal=="compression-lock") CanonicalWorkspaceRefusal.Compressing else CanonicalWorkspaceRefusal.Busy,failure.refusal)
                    checks+="actual_bound_busy_refusal_has_only_closed_advice_without_remote_details"
                }
                val unknown=checkNotNull(db.dao().managementOperation(review.requestId))
                assertEquals("unknown",unknown.state);assertEquals(CanonicalWorkspaceAction.Id,unknown.action)
                assertEquals(profileHome,unknown.projectHome);assertTrue(unknown.resultSummary.isEmpty())
                assertFalse(unknown.requestSummary.contains(beforeDirectory));assertFalse(unknown.requestSummary.contains(afterDirectory))
                checks+="lost_reply_preserves_unknown_without_inventing_an_ack"
                db.close();db=Room.databaseBuilder(context,HermesDatabase::class.java,databaseName).build()
                db.dao().recoverManagementOperations();assertEquals(unknown,db.dao().managementOperation(review.requestId))
                repo=CanonicalSessionsRepository(db.dao(),CompletableDeferred(Unit))
                checks+="real_room_reopen_and_recovery_retain_exact_unknown_receipt"
                val next=CanonicalWorkspaceReview.create(snapshot,afterDirectory)
                assertTrue(runCatching {repo.changeWorkspace(next,api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
                assertNull(db.dao().managementOperation(next.requestId))
                checks+="same_connection_new_review_cannot_send_or_claim_another_write"
                val alias=owner.copy(apiScope="e".repeat(64))
                val aliasSnapshot=CanonicalSessionSnapshot(snapshot.owner.copy(target=alias),snapshot.requestedSessionId,snapshot.reportedStoredId,
                    snapshot.subscriptionId,snapshot.cursor,snapshot.revision,snapshot.executionGeneration,snapshot.reportedRunning,snapshot.messageCount,snapshot.messagesOmitted,snapshot.workspace)
                val aliasReview=CanonicalWorkspaceReview.create(aliasSnapshot,afterDirectory)
                assertEquals(1,db.dao().unresolvedManagementOperations(alias.resourceScope,alias.url,profileHome).size)
                assertTrue(runCatching {repo.changeWorkspace(aliasReview,api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
                assertNull(db.dao().managementOperation(aliasReview.requestId))
                checks+="connection_alias_shares_unknown_home_barrier_without_a_second_claim"
                assertTrue(runCatching {repo.adopt(inventory.review(session),api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
                checks+="pending_write_blocks_new_adoption_without_replaying_or_resuming"
                assertTrue(repo.prepare(owner,profileHome,api,tokens,CanonicalSessionScope.Live).rows.any {it.sessionId==session})
                assertEquals("unknown",db.dao().managementOperation(review.requestId)!!.state)
                checks+="read_only_inventory_cannot_resolve_or_review_uncertainty"
                fixture?.let {assertEquals(1,it.mutations.get());assertEquals(1,it.resumes.get());assertEquals(1,it.replays.get());assertEquals(0,it.failures.get())}
                assertEquals(beforeCredentials,credentials.all)
                checks+="credentials_unchanged_and_unknown_left_for_independent_operator_inspection"
            }}
            completed=true
        } catch(error:Throwable) {errorType=error.javaClass.simpleName;throw error}
        finally {
            db.close();fixture?.close()
            val qa=File(checkNotNull(context.getExternalFilesDir(null)),"qa");check(qa.isDirectory || qa.mkdirs())
            File(qa,(if(expectedRefusal==null) "owner-workspace-unknown-" else "owner-workspace-refusal-")+label+".json").writeText(buildJsonObject {
                put("state",if(expectedRefusal==null) (if(completed) "PASS_OWNER_WORKSPACE_ANDROID_UNKNOWN_RECOVERY" else "FAILED_OWNER_WORKSPACE_ANDROID_UNKNOWN_RECOVERY") else (if(completed) "PASS_OWNER_WORKSPACE_ANDROID_REFUSAL" else "FAILED_OWNER_WORKSPACE_ANDROID_REFUSAL"))
                expectedRefusal?.let {put("expectedRefusal",it)}
                put("backend",if(external==null) "mock" else "actual-owner");put("sourceCommit",commit);put("api",Build.VERSION.SDK_INT);put("inventoryScope","live")
                put("checks",JsonArray(checks.map(::JsonPrimitive)));put("databaseName",databaseName);put("receiptState","unknown");put("localReviewApplied",false)
                requestId?.let {put("requestId",it)};errorType?.let {put("errorType",it)}
                put("beforeDirectorySha256",directoryHash(beforeDirectory));put("afterDirectorySha256",directoryHash(afterDirectory))
                put("savedCredentialsUnchanged",beforeCredentials==credentials.all);put("modelOrWorkerExecutionClaim",false)
                put("completedAtMillis",System.currentTimeMillis())
            }.toString())
        }
    }

}
