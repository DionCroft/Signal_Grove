package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HostRelayStorageTest {
    private fun exercise(mode:String)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,HermesDatabase::class.java).build()
        val relayReads=AtomicInteger();val channelReads=AtomicInteger();val profiles=AtomicInteger();val other=AtomicInteger();val opened=mutableListOf<String>();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        try {MockWebServer().use {server->
            fun json(raw:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(raw.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                assertEquals("Bearer relay-fixture",q.getHeader("Authorization"));assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"));assertEquals("GET",q.method);assertEquals(0L,q.bodySize)
                val url=q.requestUrl!!
                return when(url.encodedPath) {
                    "/prefix/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id",if(mode=="identity") "other" else "fixture")})
                    "/prefix/api/profiles"->{val n=profiles.incrementAndGet();json(PluginUpdateFixture.profiles(mode=="home" && n>=3))}
                    "/prefix/api/messaging/platforms"->{assertEquals("worker",url.queryParameter("profile"));val n=channelReads.incrementAndGet();json(HostRelayFixture.channels(fronted=mode!="unproven" && !(mode=="marker" && n>=2),exclusive=mode!="mode" || n==1))}
                    "/prefix/api/messaging/relay"->{
                        assertNull(url.query);val n=relayReads.incrementAndGet()
                        if(mode=="cancel") {entered.countDown();release.await(10,TimeUnit.SECONDS)}
                        when(mode) {
                            "redirect"->MockResponse().setResponseCode(307).setHeader("Location",server.url("must-not-follow"))
                            "lost"->MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                            "not-ready"->MockResponse().setResponseCode(503).setHeader("Content-Type","application/json").setBody("{\"detail\":{\"code\":\"gateway_not_answering\",\"message\":\"private error\"}}")
                            "malformed"->json(buildJsonObject {put("platforms",17)})
                            else->json(HostRelayFixture.report(manager=if(mode=="authority" && n>=2) "connector" else if(mode=="unknown") "unknown" else "portal",url=if(mode=="destination" && n>=2) "https://changed.example.test/agents" else HostRelayFixture.portal))
                        }
                    }
                    else->{other.incrementAndGet();MockResponse().setResponseCode(404)}
                }
            }}
            val api=ManagementClient(server.url("prefix/").toString(),true,true);val tokens=ManagementTokens("relay-fixture","refresh-fixture","password","fixture",4102444800);val owner=ManagementTarget("a".repeat(64),api.base.toString(),"worker","password · fixture")
            val repo=HostRelayRepository(HostChannelsRepository(db.dao(),CompletableDeferred(Unit)))
            if(mode=="cancel") {val task=async {repo.read(owner,api,tokens)};assertTrue(withContext(Dispatchers.IO) {entered.await(10,TimeUnit.SECONDS)});task.cancelAndJoin();release.countDown();assertEquals(1,relayReads.get());return@use}
            val result=runCatching {repo.read(owner,api,tokens)}
            when(mode) {
                "unproven","identity"->{assertTrue(result.isFailure);assertEquals(0,relayReads.get())}
                "home","marker","mode","redirect","lost","not-ready","malformed"->{assertTrue(result.isFailure);assertEquals(1,relayReads.get())}
                else->{
                    val inventory=result.getOrThrow();assertEquals(HostRelayFixture.account,inventory.platforms.single().accounts.single().id);assertEquals(1,relayReads.get());assertEquals(2,channelReads.get())
                    if(mode in setOf("destination","authority","portal","foreign","unknown")) {
                        val portal=runCatching {repo.portal(if(mode=="foreign") owner.copy(actor="other") else owner,inventory,api,tokens)}
                        if(mode=="portal") {opened.add(portal.getOrThrow().portalUrl);assertEquals(listOf(HostRelayFixture.portal),opened)} else assertTrue(portal.isFailure)
                        assertEquals(if(mode in setOf("foreign","unknown")) 1 else 2,relayReads.get())
                    }
                }
            }
            assertTrue(db.dao().managementOperations(owner.apiScope).first().isEmpty());assertEquals(0,other.get())
        }} finally {release.countDown();db.close()}
    }
    @Test fun prefixBearerAndExplicitProfileProofSurroundUnscopedRelayRead()=exercise("read")
    @Test fun missingLaunchMarkerRefusesBeforeAnyPrivateAccountRequest()=exercise("unproven")
    @Test fun nativeIdentityMismatchRefusesBeforeRelayRead()=exercise("identity")
    @Test fun changedHomeDuringAccountReadDiscardsTheReport()=exercise("home")
    @Test fun disappearingRelayMarkerOrChangedModeDiscardsTheReport() {exercise("marker");exercise("mode")}
    @Test fun redirectsAndLostReadsAreNotFollowedOrRetried() {exercise("redirect");exercise("lost")}
    @Test fun notReadyAndMalformedReportsDoNotBecomeEmptyInventories() {exercise("not-ready");exercise("malformed")}
    @Test fun portalHandoffRechecksScopeAndSameDestination()=exercise("portal")
    @Test fun changedPortalOrAuthorityCannotOpenTheFrozenDestination() {exercise("destination");exercise("authority")}
    @Test fun foreignOwnerAndUnknownAuthorityCannotOpenPortal() {exercise("foreign");exercise("unknown")}
    @Test fun cancellationOfAReadCannotCreateWorkOrFollowupRequests()=exercise("cancel")
}
