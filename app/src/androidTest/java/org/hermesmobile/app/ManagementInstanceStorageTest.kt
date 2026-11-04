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
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class ManagementInstanceStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val first=ManagementTarget("a".repeat(64),"https://fixture.example/dashboard/","private-old-profile","password · private-old-actor")
    private val second=first.copy(apiScope="b".repeat(64),profile="worker",actor="password · fixture")
    private fun row(target:ManagementTarget,key:String,action:String="RelayLinkStart",state:String="unknown")=ManagementOperation(key,target.resourceScope,target.apiScope,target.url,target.profile,target.actor,action,"private-target","private-request",state,"private-result",1)
    private suspend fun database(block:suspend (HermesDatabase)->Unit) {val db=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build();try {block(db)} finally {db.close()}}
    @Test fun foreignProfileInstanceWorkBlocksNewClaimsAcrossProfiles()=runBlocking {
        for(action in listOf("RelayLinkStart","RelayUnlink","CustomEndpointProbe")) for(state in listOf("sending","unknown")) database {db->
            db.dao().insertManagementOperation(row(first,"prior",action,state))
            for(next in listOf("RelayLinkStart","RelayUnlink","CustomEndpointProbe","Probe")) assertTrue(runCatching {db.dao().claimManagementOperation(row(second,"next",next,"sending"))}.exceptionOrNull() is ManagementOutcomePending)
            assertEquals("prior",db.dao().unresolvedManagementOperations(second.resourceScope,second.url).single().key)
        }
    }
    @Test fun sameCredentialCannotBypassPendingRelayBySwitchingProfiles()=runBlocking {database {db->
        db.dao().insertManagementOperation(row(first,"prior"))
        assertTrue(runCatching {db.dao().claimManagementOperation(row(second.copy(apiScope=first.apiScope),"next",state="sending"))}.exceptionOrNull() is ManagementOutcomePending)
    }}
    @Test fun sendingAcrossProfilesCannotBeClearedByLocalReview()=runBlocking {database {db->
        db.dao().insertManagementOperation(row(first,"prior",state="sending"));ManagementOperationsRepository(db.dao(),CompletableDeferred(Unit)).review(second,"prior")
        assertEquals("sending",db.dao().managementOperation("prior")!!.state)
        assertTrue(runCatching {db.dao().claimManagementOperation(row(second,"next",state="sending"))}.exceptionOrNull() is ManagementOutcomePending)
    }}
    @Test fun simultaneousInstanceClaimsAcrossProfilesAndCredentialsHaveOneWinner()=runBlocking {
        repeat(12) {index->database {db->
            val start=CompletableDeferred<Unit>()
            val outcomes=coroutineScope {
                val jobs=listOf(first,second).mapIndexed {i,target->async(Dispatchers.IO) {start.await();runCatching {db.dao().claimManagementOperation(row(target,"$i",listOf("RelayLinkStart","RelayUnlink","CustomEndpointProbe")[(index+i)%3],"sending"))}}}
                start.complete(Unit);jobs.awaitAll()
            }
            assertEquals(1,outcomes.count {it.isSuccess});assertTrue(outcomes.single {it.isFailure}.exceptionOrNull() is ManagementOutcomePending)
            assertEquals(1,db.dao().unresolvedManagementOperations(second.resourceScope,second.url).size)
        }}
    }
    @Test fun exactDashboardPrefixPortAndHostRemainIndependent()=runBlocking {database {db->
        db.dao().insertManagementOperation(row(first,"prior"))
        for((index,url) in listOf("https://other.example/dashboard/","https://fixture.example/other/","https://fixture.example:8443/dashboard/").withIndex()) db.dao().claimManagementOperation(row(second.copy(url=url),"next-$index",state="sending"))
        assertEquals(1,db.dao().unresolvedManagementOperations(second.resourceScope,second.url).size)
    }}
    @Test fun ordinaryOtherProfileUncertaintyStillAllowsIndependentWork()=runBlocking {
        for(action in listOf("Probe","RelayLinkStart","CustomEndpointProbe")) database {db->
            db.dao().insertManagementOperation(row(first,"prior","Probe"));db.dao().claimManagementOperation(row(second,"next",action,"sending"))
            assertEquals("sending",db.dao().managementOperation("next")!!.state)
        }
    }
    @Test fun settledRelayOutcomesDoNotLeaveAFalseInstanceBarrier()=runBlocking {
        for(state in listOf("acknowledged","confirmed","reviewed")) database {db->
            db.dao().insertManagementOperation(row(first,"prior",state=state));db.dao().claimManagementOperation(row(second,"next",state="sending"))
            assertEquals("next",db.dao().unresolvedManagementOperations(second.resourceScope,second.url).single().key)
        }
    }
    @Test fun instancePendingRemainsVisibleAheadOfFullOwnHistoryAndForeignSettledRows()=runBlocking {database {db->
        repeat(160) {i->db.dao().insertManagementOperation(row(second,"own-$i","Probe","confirmed").copy(createdAt=1000L+i));db.dao().insertManagementOperation(row(second.copy(apiScope="c".repeat(64)),"foreign-$i","Probe","confirmed").copy(createdAt=2000L+i))}
        db.dao().insertManagementOperation(row(first,"prior"))
        val rows=db.dao().managementVisibleResourceOperations(second.resourceScope,second.apiScope,second.url).first()
        assertEquals(100,rows.size);assertEquals("prior",rows.first().key);assertTrue(rows.drop(1).all {it.apiScope==second.apiScope})
        val shown=ManagementReceiptVisibility.project(rows,second.apiScope,second.resourceScope,second.url)
        assertEquals(ManagementReceiptVisibility.SharedInstancePending,shown.first().action);assertFalse(shown.first().toString().contains("private-"))
    }}
    @Test fun moreThanOnePageOfCrossProfileUnknownRequestsCanAllBeReviewedWithoutLosingHistory()=runBlocking {database {db->
        repeat(105) {db.dao().insertManagementOperation(row(first,"prior-$it").copy(createdAt=it.toLong()))}
        val repo=ManagementOperationsRepository(db.dao(),CompletableDeferred(Unit));val original=db.dao().managementOperation("prior-0")!!
        val page=db.dao().managementVisibleResourceOperations(second.resourceScope,second.apiScope,second.url).first();assertEquals(100,page.size)
        for(record in page) repo.review(second,record.key)
        val remaining=db.dao().managementVisibleResourceOperations(second.resourceScope,second.apiScope,second.url).first();assertEquals(5,remaining.size)
        assertTrue(runCatching {db.dao().claimManagementOperation(row(second,"blocked",state="sending"))}.exceptionOrNull() is ManagementOutcomePending)
        for(record in remaining) repo.review(second,record.key)
        assertEquals(original.copy(state="reviewed"),db.dao().managementOperation("prior-0"));db.dao().claimManagementOperation(row(second,"next",state="sending"))
    }}
    @Test fun reviewRequiresExactDashboardAndNeverClearsOrdinaryOtherProfileWork()=runBlocking {database {db->
        db.dao().insertManagementOperation(row(first,"relay"));db.dao().insertManagementOperation(row(first,"ordinary","Probe"))
        val repo=ManagementOperationsRepository(db.dao(),CompletableDeferred(Unit))
        for(target in listOf(second.copy(url="https://other.example/dashboard/"),second.copy(url="https://fixture.example/other/"))) repo.review(target,"relay")
        assertEquals("unknown",db.dao().managementOperation("relay")!!.state)
        repo.review(second,"ordinary");assertEquals("unknown",db.dao().managementOperation("ordinary")!!.state)
        repo.review(second,"relay");assertEquals("reviewed",db.dao().managementOperation("relay")!!.state)
        assertTrue(db.dao().unresolvedManagementOperations(second.resourceScope,second.url).isEmpty())
    }}
    private suspend fun repositories(race:Boolean,unlink:Boolean=false) {database {db->
        MockWebServer().use {server->
            val writes=AtomicInteger();val privateAccess=AtomicInteger();var savedTarget=false
            fun json(raw:JsonObject)=MockResponse().setHeader("Content-Type","application/json").setBody(raw.toString())
            server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {
                if(q.method!="GET") writes.incrementAndGet()
                return when(q.requestUrl!!.encodedPath) {
                    "/dashboard/api/auth/me"->json(buildJsonObject {put("provider","password");put("user_id","fixture")})
                    "/dashboard/api/profiles"->json(PluginUpdateFixture.profiles())
                    "/dashboard/api/messaging/platforms"->json(HostRelayLinkFixture.channels())
                    "/dashboard/api/messaging/relay"->json(HostRelayLinkFixture.report())
                    else->MockResponse().setResponseCode(404)
                }
            }}
            val api=ManagementClient(server.url("dashboard/").toString(),true,true)
            val target=second.copy(url=api.base.toString());val prior=row(first.copy(url=target.url),"prior")
            fun inject() {runBlocking {db.dao().claimManagementOperation(prior.copy(state="sending"))}}
            val codes=object:HostRelayCodeStore {
                override fun beforeStart(target:ManagementTarget,now:Long) {privateAccess.incrementAndGet();if(race) inject()}
                override fun save(binding:HostRelayLinkBinding,code:HostRelayCode,now:Long) {error("Must not save a new code")}
                override fun read(binding:HostRelayLinkBinding,now:Long):HostRelayCode?=null
                override fun remove(binding:HostRelayLinkBinding) {}
                override fun clear(target:ManagementTarget) {}
            }
            val targets=object:HostRelayTargetStore {
                override fun save(binding:HostRelayUnlinkBinding,account:HostRelayAccount) {privateAccess.incrementAndGet();savedTarget=true;if(race) inject()}
                override fun read(binding:HostRelayUnlinkBinding):HostRelayAccount?=null
                override fun remove(binding:HostRelayUnlinkBinding) {savedTarget=false}
                override fun clear(target:ManagementTarget) {}
            }
            val home=PluginHome.project(JsonPrimitive(PluginUpdateFixture.home))!!.identity()
            val inventory=HostRelayInventory.parse(HostRelayLinkFixture.report(),HostRelayScope.from(HostChannelInventory.parse(HostRelayLinkFixture.channels(),target,home)))
            val reader=HostRelayRepository(HostChannelsRepository(db.dao(),CompletableDeferred(Unit)))
            val tokens=ManagementTokens("instance-fixture","refresh-fixture","password","fixture",4102444800)
            if(!race) db.dao().insertManagementOperation(prior)
            suspend fun failure(unlinking:Boolean)=if(unlinking) runCatching {HostRelayUnlinkRepository(db.dao(),CompletableDeferred(Unit),reader,targets).execute(target,HostRelayUnlinkRequest(inventory,"telegram",inventory.platforms.single().accounts.single()),api,tokens)}.exceptionOrNull()
                else runCatching {HostRelayLinkRepository(db.dao(),CompletableDeferred(Unit),reader,codes).execute(target,HostRelayLinkRequest(inventory,"telegram"),api,tokens)}.exceptionOrNull()
            if(race) {assertTrue(failure(unlink) is ManagementOutcomePending);assertTrue(server.requestCount>0);assertEquals(1,privateAccess.get());assertFalse(savedTarget)}
            else {
                assertTrue(failure(false) is ManagementOutcomePending);assertTrue(failure(true) is ManagementOutcomePending)
                assertTrue(runCatching {ManagementOperationsRepository(db.dao(),CompletableDeferred(Unit)).execute(target,McpRequest(McpAction.Probe,"fixture",true,"config"),api,tokens)}.exceptionOrNull() is ManagementOutcomePending)
                assertEquals(0,privateAccess.get());assertEquals(0,server.requestCount)
            }
            assertEquals(0,writes.get());assertEquals("prior",db.dao().unresolvedManagementOperations(target.resourceScope,target.url).single().key)
        }
    }}
    @Test fun linkUnlinkAndOrdinaryPreflightRefuseBeforeNetworkOrPrivateCache()=runBlocking {repositories(false)}
    @Test fun foreignProfileClaimAfterLinkPreflightPreventsMint()=runBlocking {repositories(true)}
    @Test fun foreignProfileClaimDuringUnlinkTargetSavePreventsDeleteAndCleansOnlyUnclaimedTarget()=runBlocking {repositories(true,true)}
    @Test fun historicalSchemaTenReceiptSurvivesReopenAndBlocksProfileAliasWithoutRewriting()=runBlocking {
        val name="instance-history-"+UUID.randomUUID()+".db"
        fun open()=Room.databaseBuilder(context,HermesDatabase::class.java,name).addMigrations(*ALL_HERMES_MIGRATIONS).build()
        val original=row(first,"prior");val initial=open()
        try {initial.dao().insertManagementOperation(original)} finally {initial.close()}
        val reopened=open()
        try {assertEquals(original,reopened.dao().managementOperation("prior"));assertTrue(runCatching {reopened.dao().claimManagementOperation(row(second,"next",state="sending"))}.exceptionOrNull() is ManagementOutcomePending)}
        finally {reopened.close();context.deleteDatabase(name)}
    }
}
