package org.hermesmobile.app

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

/** Production project repository over its real HTTP/ticket/WebSocket path. No saved credentials. */
class ProjectGatewayContractTest {
    private class Cookies:CookieJar {
        private val values=mutableListOf<Cookie>()
        @Synchronized override fun saveFromResponse(url:HttpUrl,cookies:List<Cookie>) {
            cookies.forEach {cookie->values.removeAll {it.name==cookie.name && it.domain==cookie.domain && it.path==cookie.path};values+=cookie}
        }
        @Synchronized override fun loadForRequest(url:HttpUrl)=values.filter {it.matches(url) && it.expiresAt>System.currentTimeMillis()}
    }
    private data class Pending(val challenge:String,val redirect:HttpUrl,val state:String)
    private class Fixture:AutoCloseable {
        val server=MockWebServer()
        val failures=AtomicInteger()
        private val pending=AtomicReference<Pending>()
        private val tickets=ConcurrentHashMap.newKeySet<String>()
        private val code="project-code-"+UUID.randomUUID().toString().replace("-","")
        private val envelope="""{"access_token":"project-access-fixture","refresh_token":"project-refresh-fixture","provider":"stub","user_id":"stub-user-1","expires_at":4102444800,"token_type":"Bearer"}"""
        private fun json(body:String,status:Int=200)=MockResponse().setHeader("Content-Type","application/json").setBody(body).setResponseCode(status)
        private fun project(profile:String,id:String)=buildJsonObject {
            val shared=id=="fixture-shared-project";val part=if(shared) "shared" else "only"
            val title=if(profile=="default") "Default" else "Worker"
            val primary="/fixture/${profile}-project-root/$part/primary"
            put("id",id);put("slug",if(shared) "shared-fixture" else "$profile-only")
            put("name",if(shared) "$title fixture project" else "$title only project")
            put("description","$profile synthetic description");put("primary_path",primary)
            put("created_at",1700000000L);put("archived",false)
            put("folders",buildJsonArray {
                add(buildJsonObject {put("path",primary);put("label","$title primary folder");put("is_primary",true);put("added_at",1700000000L)})
                if(shared) add(buildJsonObject {put("path","/fixture/${profile}-project-root/shared/secondary");put("label","$title secondary folder");put("is_primary",false);put("added_at",1700000001L)})
            })
        }
        private fun listener()=object:WebSocketListener() {
            override fun onOpen(ws:WebSocket,response:Response) {
                ws.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"skin":{}}}}""")
            }
            override fun onMessage(ws:WebSocket,text:String) {
                val id=runCatching {Json.parseToJsonElement(text).jsonObject["id"]}.getOrNull() ?: JsonNull
                try {
                    val frame=Json.parseToJsonElement(text).jsonObject
                    check(frame["jsonrpc"]==JsonPrimitive("2.0") && (id as JsonPrimitive).isString)
                    val method=frame["method"]!!.jsonPrimitive.content
                    val params=frame["params"]!!.jsonObject;val profile=params["profile"]!!.jsonPrimitive.content
                    check(method in setOf("projects.list","projects.get","projects.project_sessions"))
                    check(params.keys==when(method) {"projects.list"->setOf("profile");"projects.project_sessions"->setOf("profile","project_id","session_limit");else->setOf("profile","id")})
                    val error=if(profile=="missing-fixture") 4064 else {
                        check(profile in setOf("default","worker"))
                        if(method=="projects.get" && params["id"]!!.jsonPrimitive.content !in setOf("fixture-shared-project","$profile-only-project")) 5062 else null
                    }
                    ws.send(buildJsonObject {
                        put("jsonrpc","2.0");put("id",id)
                        if(error!=null) put("error",buildJsonObject {put("code",error);put("message","Fixture metadata unavailable")})
                        else put("result",buildJsonObject {
                            if(method=="projects.list") {
                                put("active_id","fixture-shared-project")
                                put("projects",JsonArray(listOf(project(profile,"fixture-shared-project"),project(profile,"$profile-only-project"))))
                            } else if(method=="projects.project_sessions") {
                                check(params["project_id"]==JsonPrimitive("fixture-shared-project") && params["session_limit"]==JsonPrimitive(500))
                                val stored=project(profile,"fixture-shared-project");val path=stored.getValue("primary_path")
                                put("project",buildJsonObject {
                                    put("id","fixture-shared-project");put("label",stored.getValue("name"));put("path",path);put("isAuto",false);put("isNoProject",false);put("sessionCount",1)
                                    put("sessionIds",buildJsonArray {add("fixture-project-session")});put("repos",buildJsonArray {add(buildJsonObject {
                                        put("id","fixture-repository");put("path",path);put("sessionCount",1);put("groups",buildJsonArray {add(buildJsonObject {
                                            put("id","fixture-main");put("label","Main");put("sessions",buildJsonArray {add(buildJsonObject {
                                                put("id","fixture-project-session");put("title",profile+" fixture-project-session");put("profile",profile);put("source","cli");put("cwd",path);put("message_count",2);put("last_active",101);put("archived",false);put("preview","Private fixture preview must not become a DTO field")
                                            })})
                                        })})
                                    })})
                                })
                            } else put("project",project(profile,params["id"]!!.jsonPrimitive.content))
                        })
                    }.toString())
                } catch(_:Exception) {
                    failures.incrementAndGet()
                    ws.send(buildJsonObject {put("jsonrpc","2.0");put("id",id);put("error",buildJsonObject {put("code",-32600);put("message","Fixture expectation failed")})}.toString())
                }
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
                                "/dashboard/api/profiles"->{check(q.method=="GET");json("""{"profiles":[{"name":"default","path":"/fixture/homes/default"},{"name":"worker","path":"/fixture/homes/worker"}]}""")}
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
    private fun detail(detail:HostProjectDetail,profile:String) {
        val title=if(profile=="default") "Default" else "Worker";val project=detail.project
        assertEquals("fixture-shared-project",project.id);assertEquals("$title fixture project",project.name)
        assertFalse(project.archived);assertEquals(2,project.folders.size)
        val primary=project.folders.single {it.primary};assertEquals(project.primaryPath,primary.path)
        assertEquals("$title primary folder",primary.label)
        assertTrue(primary.path.replace('\\','/').endsWith("/${profile}-project-root/shared/primary"))
        assertTrue(project.folders.all {it.label.orEmpty().startsWith(title)})
    }

    @Test fun projectReadsKeepVerifiedActorProfileHomeAndExactIdentity() {
        val args=InstrumentationRegistry.getArguments();val external=args.getString("signalGroveProjectBase")
        val label=args.getString("signalGroveProjectLabel") ?: "mock";require(label.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,80}")))
        val fixture=if(external==null) Fixture() else null
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val credentials=context.getSharedPreferences("credentials",0)
        val before=credentials.all.mapValues {(_,value)->if(value is Set<*>) value.toSet() else value}
        val checks=mutableListOf<String>()
        try {
            val base=(external ?: fixture!!.server.url("/dashboard/").newBuilder().host("127.0.0.1").build().toString()).toHttpUrl()
            require(base.scheme=="http" && base.host=="127.0.0.1" && base.port in 1024..65535 && base.encodedPath=="/dashboard/")
            require(base.username.isEmpty() && base.password.isEmpty() && base.query==null && base.fragment==null)
            val api=ManagementClient(base.toString(),true,true)
            runBlocking {
                withTimeout(180_000) {
                    val tokens=signIn(api);assertEquals("stub",tokens.provider);assertEquals("stub-user-1",tokens.userId)
                    checks+="real_native_signin_for_project_client"
                    val profiles=api.profiles(tokens)
                    fun home(profile:String)=checkNotNull(profiles.single {it.name==profile}.pluginHome).identity()
                    assertNotEquals(home("default"),home("worker"));checks+="verified_profile_homes_are_distinct"
                    fun target(profile:String)=ManagementTarget("b".repeat(64),base.toString(),profile,tokens.provider+" · "+tokens.userId)
                    val repo=HostProjectsRepository();val default=target("default");val worker=target("worker")
                    val first=repo.read(default,home("default"),api,tokens)
                    assertEquals(default,first.owner);assertEquals(home("default"),first.homeId)
                    assertEquals(setOf("fixture-shared-project","default-only-project"),first.projects.map {it.id}.toSet())
                    assertEquals("Default fixture project",first.activeProject?.name);checks+="default_inventory_keeps_owner_home_and_active_metadata"
                    detail(repo.detail(default,home("default"),"fixture-shared-project",api,tokens),"default")
                    checks+="default_detail_keeps_exact_id_and_folder_metadata"
                    val second=repo.read(worker,home("worker"),api,tokens)
                    assertEquals(worker,second.owner);assertEquals(home("worker"),second.homeId)
                    assertEquals(setOf("fixture-shared-project","worker-only-project"),second.projects.map {it.id}.toSet())
                    assertEquals("Worker fixture project",second.activeProject?.name);checks+="secondary_inventory_keeps_its_own_owner_and_home"
                    detail(repo.detail(worker,home("worker"),"fixture-shared-project",api,tokens),"worker")
                    checks+="same_id_secondary_detail_is_isolated"
                    val foreign=runCatching {repo.detail(worker,home("worker"),"default-only-project",api,tokens)}.exceptionOrNull()
                    assertTrue(foreign is GatewayRpcException);assertEquals(5062,(foreign as GatewayRpcException).code)
                    checks+="foreign_only_project_id_is_refused"
                    val missing=runCatching {repo.read(target("missing-fixture"),home("default"),api,tokens)}.exceptionOrNull()
                    assertTrue(missing is HostProjectsScopeChanged);checks+="unregistered_profile_is_refused_by_home_fence"
                    val actor=runCatching {repo.read(default.copy(actor="other-fixture-actor"),home("default"),api,tokens)}.exceptionOrNull()
                    assertTrue(actor is IllegalArgumentException);checks+="actor_mismatch_is_refused_before_transport"
                    api.gateway(tokens).use {socket->
                        assertEquals(GatewayAuthorityAdvertisement.NotAdvertised,socket.sessionAuthorityAdvertisement)
                        val absent=runCatching {socket.projects("missing-fixture")}.exceptionOrNull()
                        assertTrue(absent is GatewayRpcException);assertEquals(4064,(absent as GatewayRpcException).code)
                    }
                    checks+="missing_server_profile_is_typed_without_invented_authority"
                }
            }
            fixture?.let {assertEquals(0,it.failures.get())}
            assertTrue("Project conformance changed the saved credential store.",before==credentials.all)
            checks+="saved_credentials_unchanged";assertEquals(11,checks.size)
            val qa=File(checkNotNull(context.getExternalFilesDir(null)),"qa");check(qa.isDirectory || qa.mkdirs())
            File(qa,"project-gateway-contract-$label.json").writeText(buildJsonObject {
                put("state","PASS_PROJECT_GATEWAY_CLIENT_CONTRACT");put("backend",if(external==null) "local-mock" else "owned-real-hermes-loopback")
                put("api",Build.VERSION.SDK_INT);put("checks",JsonArray(checks.map(::JsonPrimitive)))
                put("savedCredentialsUnchanged",true);put("fileOrWorkspaceAdoptionClaim",false);put("completedAtMillis",System.currentTimeMillis())
            }.toString())
        } finally {fixture?.close()}
    }
    @Test fun projectConversationsRemainProfileBoundAndDoNotAdoptAWorkspace() {
        val encoded=InstrumentationRegistry.getArguments().getString("signalGroveOwnerProjectCase")
        val case=encoded?.let {raw->
            require(raw.matches(Regex("[A-Za-z0-9_-]{16,32768}")))
            Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(raw),Charsets.UTF_8)).jsonObject.also {
                require(it.keys==setOf("caseId","surface","sourceCommit","api","baseUrl","profileHomes","projects"))
                require(it.getValue("sourceCommit").jsonPrimitive.content in setOf("2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19","3b421578588243f920886e6fe79a6faa87ea026e"))
                require(it.getValue("api").jsonPrimitive.int==Build.VERSION.SDK_INT)
            }
        }
        val caseId=case?.getValue("caseId")?.jsonPrimitive?.content ?: UUID.randomUUID().toString();require(UUID.fromString(caseId).toString()==caseId)
        val surface=case?.getValue("surface")?.jsonPrimitive?.content ?: "legacy-mock";require(surface in setOf("gateway","webapp","legacy-mock"))
        val fixture=if(case==null) Fixture() else null
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val credentials=context.getSharedPreferences("credentials",0)
        val before=credentials.all.mapValues {(_,value)->if(value is Set<*>) value.toSet() else value};val checks=mutableListOf<String>();val supported=mutableListOf<String>()
        try {
            val base=(case?.getValue("baseUrl")?.jsonPrimitive?.content ?: fixture!!.server.url("/dashboard/").newBuilder().host("127.0.0.1").build().toString()).toHttpUrl()
            require(base.scheme=="http" && base.host=="127.0.0.1" && base.port in 1024..65535 && base.encodedPath=="/dashboard/" && base.query==null && base.fragment==null && base.username.isEmpty() && base.password.isEmpty())
            val api=ManagementClient(base.toString(),true,true)
            runBlocking {withTimeout(180_000) {
                val issued=signIn(api);val tokens=if(case==null) issued else api.refresh(issued);api.identity(tokens)
                checks+="native_pkce_and_verified_identity";val profiles=api.profiles(tokens)
                val names=listOf("default",if(case==null) "worker" else "field")
                val homes=names.associateWith {name->checkNotNull(profiles.single {it.name==name}.pluginHome).identity()};assertNotEquals(homes.getValue(names[0]),homes.getValue(names[1]))
                if(case!=null) for(name in names) assertEquals(PluginHome.project(case.getValue("profileHomes").jsonObject.getValue(name))!!.identity(),homes.getValue(name))
                checks+="authenticated_distinct_profile_homes";val repo=HostProjectsRepository()
                for(name in names) {
                    val target=ManagementTarget("b".repeat(64),base.toString(),name,tokens.provider+" · "+tokens.userId);val home=homes.getValue(name)
                    if(surface=="gateway" && name=="field") {
                        val refusal=runCatching {repo.read(target,home,api,tokens)}.exceptionOrNull();assertTrue(refusal is CanonicalProfileTransportUnavailable);checks+="named_profile_unavailable_without_launch_profile_fallback";continue
                    }
                    val inventory=repo.read(target,home,api,tokens);val expected=case?.getValue("projects")?.jsonObject?.getValue(name)?.jsonObject
                    val projectId=expected?.getValue("projectId")?.jsonPrimitive?.content ?: "fixture-shared-project"
                    assertTrue(inventory.projects.any {it.id==projectId});assertEquals(projectId,inventory.activeId)
                    val detail=repo.detail(target,home,projectId,api,tokens)
                    if(expected!=null) {assertEquals(expected.getValue("name").jsonPrimitive.content,detail.project.name);assertEquals(expected.getValue("directory").jsonPrimitive.content,detail.project.primaryPath)}
                    val conversations=repo.conversations(target,home,detail.project,api,tokens)
                    assertEquals(target,conversations.owner);assertEquals(home,conversations.homeId);assertTrue(conversations.listed)
                    val ids=expected?.getValue("sessionIds")?.jsonArray?.map {it.jsonPrimitive.content}?.toSet() ?: setOf("fixture-project-session")
                    assertEquals(ids,conversations.rows.map {it.id}.toSet());assertEquals(ids.size,conversations.reportedCount)
                    assertTrue(conversations.rows.all {it.title==name+" "+it.id && it.messageCount==2L && it.directory==detail.project.primaryPath})
                    checks+=name+"_exact_project_and_eligible_conversations"
                    if(case!=null) {
                        api.canonicalGateway(tokens,target,home).use {channel->
                            val stored=channel.sessionInventory(CanonicalSessionScope.Stored)
                            for(id in ids) {val review=stored.review(id);assertEquals(id,review.row.sessionId);assertEquals(target,review.expected.binding.target);assertEquals(home,review.expected.binding.homeId)}
                        }
                        val foreign=case.getValue("projects").jsonObject.getValue(names.first {it!=name}).jsonObject.getValue("projectId").jsonPrimitive.content
                        val refused=runCatching {repo.detail(target,home,foreign,api,tokens)}.exceptionOrNull();assertTrue(refused is GatewayRpcException);assertEquals(5062,(refused as GatewayRpcException).code)
                        checks+=name+"_stored_review_only_and_foreign_project_refusal"
                    }
                    val wrongHome=runCatching {repo.conversations(target,homes.getValue(names.first {it!=name}),detail.project,api,tokens)}.exceptionOrNull();assertTrue(wrongHome is HostProjectsScopeChanged)
                    checks+=name+"_wrong_home_refused";supported+=name
                }
            }}
            fixture?.let {assertEquals(0,it.failures.get())};assertEquals(before,credentials.all)
            val qa=File(checkNotNull(context.getExternalFilesDir(null)),"qa");check(qa.isDirectory || qa.mkdirs())
            File(qa,"owner-project-"+caseId+".json").writeText(buildJsonObject {
                put("state","PASS_ANDROID_PROJECT_CONVERSATIONS");put("backend",if(case==null) "local-mock" else "actual-owner");put("caseId",caseId);put("surface",surface)
                put("sourceCommit",case?.getValue("sourceCommit") ?: JsonPrimitive("mock"));put("api",Build.VERSION.SDK_INT)
                put("checks",JsonArray(checks.map(::JsonPrimitive)));put("supportedProfiles",JsonArray(supported.map(::JsonPrimitive)))
                put("savedCredentialsUnchanged",true);put("sessionAdoptionCount",0);put("workerExecutionCount",0)
            }.toString())
        } finally {fixture?.close()}
    }

}
