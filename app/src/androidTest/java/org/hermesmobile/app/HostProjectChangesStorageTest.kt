package org.hermesmobile.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class HostProjectChangesStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val home="b".repeat(64)
    private val owner=ManagementTarget("a".repeat(64),"https://fixture.example/dashboard/","worker","password · private-actor")
    private fun db()=Room.inMemoryDatabaseBuilder(context,HermesDatabase::class.java).build()
    private fun receipt(target:ManagementTarget=owner,key:String="prior",state:String="unknown",store:String=home)=ManagementOperation(key,target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"ProjectUpdate","private-target","private-request",state,"private-result",123,store)
    private fun repo(db:HermesDatabase)=HostProjectOperationsRepository(db.dao(),CompletableDeferred(Unit))
    private fun snapshot(f:HostProjectChangeFixture,j:JsonObject=HostProjectChangeFixture.listing())=HostProjectProtocol.inventory(j,f.target,f.home,100)
    private fun update(s:HostProjectSnapshot)=HostProjectChange.update(s,"p_00000001",mapOf("name" to "Changed name"))
    private fun wire(vararg fields:Pair<String,JsonElement>)=JsonObject(mapOf("profile" to JsonPrimitive("worker"))+mapOf(*fields))

    @Test fun everyStoredMutationUsesExactTypedWireOneClaimAndSeparateReadback()=runBlocking {
        val original=HostProjectChangeFixture.row();val changed=HostProjectChangeFixture.changed()
        val oldFolder=original["folders"]!!.jsonArray.single()
        val next=buildJsonObject {put("path","/srv/next");put("is_primary",false);put("added_at",101)}
        val both=JsonObject(original+mapOf("folders" to JsonArray(listOf(oldFolder,next))))
        val primary=JsonObject(original+mapOf("primary_path" to JsonPrimitive("/srv/next"),"folders" to JsonArray(listOf(JsonObject(oldFolder.jsonObject+mapOf("is_primary" to JsonPrimitive(false))),JsonObject(next+mapOf("is_primary" to JsonPrimitive(true)))))))
        val empty=HostProjectChangeFixture.row(folderPath=null,primary=false)
        val archived=HostProjectChangeFixture.row(archived=true)
        val created=HostProjectChangeFixture.row("p_00000002","new","New garden",folderPath=null,primary=false)
        fun list(rows:List<JsonObject> = listOf(original),active:String?="p_00000001")=HostProjectChangeFixture.listing(rows,active)
        fun answer(row:JsonObject)=HostProjectChangeFixture.answer(row)
        data class Case(val method:String,val before:JsonObject,val ack:JsonObject,val after:JsonObject,val expected:JsonObject,val request:(HostProjectSnapshot)->HostProjectChange)
        val id="id" to JsonPrimitive("p_00000001")
        val cases=listOf(
            Case("projects.create",list(),answer(created),list(listOf(original,created)),wire("name" to JsonPrimitive("New garden"),"use" to JsonPrimitive(false))) {HostProjectChange.create(it,HostProjectCreateDraft("New garden"))},
            Case("projects.update",list(),answer(changed),list(listOf(changed)),wire(id,"name" to JsonPrimitive("Changed name")),::update),
            Case("projects.add_folder",list(),answer(both),list(listOf(both)),wire(id,"path" to JsonPrimitive("/srv/next"),"is_primary" to JsonPrimitive(false))) {HostProjectChange.addFolder(it,"p_00000001","/srv/next")},
            Case("projects.remove_folder",list(),answer(empty),list(listOf(empty)),wire(id,"path" to JsonPrimitive("/srv/private/garden"))) {HostProjectChange.folder(it,"p_00000001","/srv/private/garden",false)},
            Case("projects.set_primary",list(listOf(both)),answer(primary),list(listOf(primary)),wire(id,"path" to JsonPrimitive("/srv/next"))) {HostProjectChange.folder(it,"p_00000001","/srv/next",true)},
            Case("projects.archive",list(),list(listOf(archived)),list(listOf(archived)),wire(id,"restore" to JsonPrimitive(false))) {HostProjectChange.action(it,"p_00000001",HostProjectAction.Archive)},
            Case("projects.archive",list(listOf(archived)),list(),list(),wire(id,"restore" to JsonPrimitive(true))) {HostProjectChange.action(it,"p_00000001",HostProjectAction.Restore)},
            Case("projects.delete",list(),list(emptyList()),list(emptyList()),wire(id)) {HostProjectChange.action(it,"p_00000001",HostProjectAction.Delete)},
            Case("projects.set_active",list(active=null),buildJsonObject {put("active_id","p_00000001")},list(),wire(id)) {HostProjectChange.action(it,"p_00000001",HostProjectAction.SetActive)},
            Case("projects.set_active",list(),buildJsonObject {put("active_id",JsonNull)},list(active=null),wire("id" to JsonNull)) {HostProjectChange.action(it,null,HostProjectAction.ClearActive)}
        )
        for(case in cases) {
            val db=db()
            try {HostProjectChangeFixture(before=case.before,ack=case.ack,after=case.after).use {f->
                val outcome=withTimeout(8000) {repo(db).execute(f.target,case.request(snapshot(f,case.before)),f.api,f.tokens)}
                assertEquals(case.method,"confirmed",outcome.receipt.state);assertEquals(f.home,outcome.receipt.projectHome)
                assertEquals(1,f.writes.get());assertEquals(2,f.lists.get());assertEquals(1,f.tickets.get());assertEquals(4,f.homes.get())
                assertEquals(listOf("projects.list",case.method,"projects.list"),f.calls.map {it["method"]!!.jsonPrimitive.content})
                assertEquals(case.expected,f.calls[1]["params"]);assertEquals(buildJsonObject {put("profile","worker")},f.calls[0]["params"])
                assertEquals(JsonPrimitive(true),Json.parseToJsonElement(outcome.receipt.resultSummary).jsonObject["configuration_observed"])
                val durable=outcome.receipt.requestSummary+outcome.receipt.resultSummary+outcome.receipt.target
                for(secret in listOf("Private garden","Private notes","/srv/private/","New garden","Changed name","p_00000001","p_00000002")) assertFalse(case.method+" leaked input",durable.contains(secret))
                for(q in f.requests) {assertNull(q.getHeader("Cookie"));assertNull(q.getHeader("Origin"));if(q.path!="/prefix/api/ws") assertEquals("Bearer project-change-fixture",q.getHeader("Authorization"))}
            }} finally {db.close()}
        }
    }
    @Test fun staleMetadataAndRemappedHomeNeverReachTheMutationOrCreateAReceipt()=runBlocking {
        for(mode in listOf("changed","before-home")) {
            val db=db()
            try {HostProjectChangeFixture(mode,before=if(mode=="changed") HostProjectChangeFixture.listing(listOf(HostProjectChangeFixture.changed())) else HostProjectChangeFixture.listing()).use {f->
                val failure=withTimeout(8000) {runCatching {repo(db).execute(f.target,update(snapshot(f)),f.api,f.tokens)}.exceptionOrNull()}
                assertTrue(failure is HostProjectChangeChanged);assertEquals(0,f.writes.get());assertTrue(db.dao().managementOperations(f.target.apiScope).first().isEmpty())
            }} finally {db.close()}
        }
    }
    @Test fun droppedMalformedAndPotentialPostWriteErrorsStayUnknownAndCannotReplayAcrossAliases()=runBlocking {
        for(mode in listOf("disconnect","malformed","late-error")) {
            val db=db()
            try {HostProjectChangeFixture(mode).use {f->
                val failure=withTimeout(8000) {runCatching {repo(db).execute(f.target,update(snapshot(f)),f.api,f.tokens)}.exceptionOrNull()}
                assertTrue(mode,failure is HostProjectChangeUncertain);assertEquals(1,f.writes.get())
                val prior=db.dao().unresolvedManagementOperations(f.target.resourceScope,f.target.url,f.home).single();assertEquals("unknown",prior.state);assertEquals(prior,(failure as HostProjectChangeUncertain).receipt)
                val alias=f.target.copy(profile="mirror",apiScope="c".repeat(64));val before=f.requests.size
                val blocked=runCatching {repo(db).execute(alias,update(snapshot(f).copy(owner=alias)),f.api,f.tokens)}.exceptionOrNull()
                assertTrue(blocked is ManagementOutcomePending);assertEquals(before,f.requests.size);assertEquals(1,f.writes.get())
            }} finally {db.close()}
        }
    }
    @Test fun onlyVerifiedMissingTargetRefusalsAreSafeAndCreateErrorsRemainUncertain()=runBlocking {
        for(create in listOf(false,true)) {
            val db=db()
            try {HostProjectChangeFixture("missing").use {f->
                val request=if(create) HostProjectChange.create(snapshot(f),HostProjectCreateDraft("New")) else update(snapshot(f))
                val failure=withTimeout(8000) {runCatching {repo(db).execute(f.target,request,f.api,f.tokens)}.exceptionOrNull()}
                val row=db.dao().managementOperations(f.target.apiScope).first().single()
                assertEquals(if(create) "unknown" else "refused",row.state)
                assertTrue(if(create) failure is HostProjectChangeUncertain else failure is HostProjectChangeRefused)
                assertEquals(1,f.writes.get());assertFalse(row.resultSummary.contains("private-server-error"))
            }} finally {db.close()}
        }
    }
    @Test fun postAcknowledgementHomeChangeKeepsTheAcknowledgementAndNeverReplays()=runBlocking {
        val db=db()
        try {HostProjectChangeFixture("after-home").use {f->
            val failure=withTimeout(8000) {runCatching {repo(db).execute(f.target,update(snapshot(f)),f.api,f.tokens)}.exceptionOrNull()}
            assertTrue(failure is HostProjectChangeReadbackPending);assertEquals(1,f.writes.get());assertEquals(1,f.lists.get())
            val row=db.dao().managementOperations(f.target.apiScope).first().single();assertEquals("acknowledged",row.state);assertEquals(row,(failure as HostProjectChangeReadbackPending).receipt)
            assertEquals(JsonPrimitive(true),Json.parseToJsonElement(row.resultSummary).jsonObject["host_acknowledged"])
        }} finally {db.close()}
    }
    @Test fun successfulRpcWithAnUnchangedPrimaryIsAcknowledgedWithoutConfigurationConfirmation()=runBlocking {
        val db=db();val row=HostProjectChangeFixture.row();val original=row["folders"]!!.jsonArray.single()
        val extra=buildJsonObject {put("path","/srv/next");put("is_primary",false)}
        val both=JsonObject(row+mapOf("folders" to JsonArray(listOf(original,extra))))
        val list=HostProjectChangeFixture.listing(listOf(both))
        try {HostProjectChangeFixture(before=list,ack=HostProjectChangeFixture.answer(both),after=list).use {f->
            val request=HostProjectChange.folder(snapshot(f,list),"p_00000001","/srv/next",true)
            val outcome=withTimeout(8000) {repo(db).execute(f.target,request,f.api,f.tokens)}
            assertEquals("acknowledged",outcome.receipt.state);assertFalse(outcome.acknowledgement.effectReported);assertEquals(1,f.writes.get())
        }} finally {db.close()}
    }
    @Test fun cancellationAfterSendingPreservesTheUnknownBarrierAndDropsTheLateReply()=runBlocking {
        val db=db()
        try {HostProjectChangeFixture("held").use {f->
            val job=async {repo(db).execute(f.target,update(snapshot(f)),f.api,f.tokens)}
            assertTrue(withContext(Dispatchers.IO) {f.entered.await(5,TimeUnit.SECONDS)})
            job.cancelAndJoin();f.release.countDown()
            val row=db.dao().managementOperations(f.target.apiScope).first().single();assertEquals("unknown",row.state);assertEquals(1,f.writes.get())
            assertEquals(1,db.dao().unresolvedManagementOperations(f.target.resourceScope,f.target.url,f.home).size)
        }} finally {db.close()}
    }
    @Test fun sameStoreConcurrentClaimsHaveOneWinnerWhileOtherHomesAndPrefixesRemainIndependent()=runBlocking {
        val db=db()
        try {
            val alias=owner.copy(profile="mirror",apiScope="c".repeat(64));val start=CompletableDeferred<Unit>()
            val attempts=listOf(owner,alias).mapIndexed {i,target->async(Dispatchers.IO) {start.await();runCatching {db.dao().claimManagementOperation(receipt(target,"claim-$i",state="sending"))}}}
            start.complete(Unit);val results=attempts.awaitAll();assertEquals(1,results.count {it.isSuccess});assertEquals(1,results.count {it.exceptionOrNull() is ManagementOutcomePending})
            db.dao().claimManagementOperation(receipt(owner.copy(profile="independent"),"other-home",store="d".repeat(64)))
            db.dao().claimManagementOperation(receipt(owner.copy(url="https://fixture.example/other/"),"other-prefix"))
            assertEquals(3,db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM management_operations").use {it.moveToFirst();it.getInt(0)})
            assertTrue(runCatching {db.dao().claimManagementOperation(receipt(key="missing-binding",store=""))}.exceptionOrNull() is IllegalArgumentException)
        } finally {db.close()}
    }
    @Test fun sameStoreForeignHistoryIsFilteredBeforePagingAndPendingProjectionRechecksTheHome()=runBlocking {
        val db=db();val alias=owner.copy(profile="mirror",apiScope="c".repeat(64))
        try {
            repeat(110) {db.dao().insertManagementOperation(receipt(owner,"foreign-$it","confirmed").copy(createdAt=1000L+it))}
            db.dao().insertManagementOperation(receipt(alias,"own-history","confirmed"))
            db.dao().claimManagementOperation(receipt(owner,"pending"))
            val rows=db.dao().managementVisibleResourceOperations(alias.resourceScope,alias.apiScope,alias.url,home).first()
            assertEquals(2,rows.size);assertEquals("pending",rows.first().key)
            val projected=ManagementReceiptVisibility.project(rows,alias.apiScope,alias.resourceScope,alias.url,home)
            val shared=projected.first();assertEquals(ManagementReceiptVisibility.SharedProjectPending,shared.action)
            for(secret in listOf("private-actor","private-target","private-request","private-result",owner.apiScope,owner.profile)) assertFalse(shared.toString().contains(secret))
            assertTrue(ManagementReceiptVisibility.project(listOf(shared),alias.apiScope,alias.resourceScope,alias.url,"d".repeat(64)).isEmpty())
            assertTrue(ManagementReceiptVisibility.project(listOf(shared),alias.apiScope,alias.resourceScope,"https://fixture.example/other/",home).isEmpty())
            assertEquals("own-history",projected.last().key)
        } finally {db.close()}
    }
    @Test fun restartRecoveryAndExplicitSameHomeReviewNeverInventAServerOutcome()=runBlocking {
        val db=db();val alias=owner.copy(profile="mirror",apiScope="c".repeat(64))
        try {
            db.dao().claimManagementOperation(receipt(owner,"prior","sending"));db.dao().reviewManagementOperation("prior",alias.resourceScope,alias.url,home)
            assertEquals("sending",db.dao().managementOperation("prior")!!.state)
            db.dao().recoverManagementOperations();assertEquals("unknown",db.dao().managementOperation("prior")!!.state)
            db.dao().reviewManagementOperation("prior",alias.resourceScope,alias.url)
            db.dao().reviewManagementOperation("prior",alias.resourceScope,alias.url,"d".repeat(64));assertEquals("unknown",db.dao().managementOperation("prior")!!.state)
            db.dao().reviewManagementOperation("prior",alias.resourceScope,alias.url,home)
            val row=db.dao().managementOperation("prior")!!;assertEquals("reviewed",row.state);assertEquals("private-result",row.resultSummary)
        } finally {db.close()}
    }
    @Test fun verifiedAliasReviewUsesOnlyIdentityAndProfileReadsWithNoGatewayOrMutation()=runBlocking {
        val db=db()
        try {HostProjectChangeFixture().use {f->
            db.dao().claimManagementOperation(receipt(f.target,"prior",store=f.home))
            val alias=f.target.copy(profile="mirror",apiScope="c".repeat(64))
            repo(db).review(alias,f.home,"prior",f.api,f.tokens)
            assertEquals("reviewed",db.dao().managementOperation("prior")!!.state);assertEquals(2,f.requests.size);assertEquals(0,f.tickets.get());assertEquals(0,f.writes.get())
        }} finally {db.close()}
    }
    @Test fun schemaTenMigrationAndReopenRetainAllHistoricalReceiptsAndPrivateAppData()=runBlocking {
        val name="project-home-migration10-qa.db";context.deleteDatabase(name)
        val schema=Json.parseToJsonElement(InstrumentationRegistry.getInstrumentation().context.assets.open("org.hermesmobile.app.data.HermesDatabase/10.json").bufferedReader().use {it.readText()}).jsonObject["database"]!!.jsonObject
        context.getDatabasePath(name).parentFile!!.mkdirs();val placeholder=36.toChar().toString()+"{TABLE_NAME}"
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use {old->
            schema["entities"]!!.jsonArray.forEach {entry->val entity=entry.jsonObject;val table=entity["tableName"]!!.jsonPrimitive.content;old.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace(placeholder,table));entity["indices"]?.jsonArray.orEmpty().forEach {old.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace(placeholder,table))}}
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");old.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema["identityHash"]!!.jsonPrimitive.content))
            for(state in listOf("sending","unknown","acknowledged","confirmed","reviewed")) old.execSQL("INSERT INTO management_operations ([key],resourceScope,apiScope,url,profile,actor,action,target,requestSummary,state,resultSummary,createdAt) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",arrayOf<Any>(state,owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,"Probe","private-target","private-request",state,"private-result",123L))
            old.execSQL("INSERT INTO tasks (id,title,dueDate,priority,completed,notes) VALUES ('saved','Retained private task','',1,0,'Retained private notes')")
            old.version=10
        }
        val db=Room.databaseBuilder(context,HermesDatabase::class.java,name).addMigrations(*ALL_HERMES_MIGRATIONS).build()
        try {
            val rows=db.dao().managementOperations(owner.apiScope).first();assertEquals(5,rows.size)
            for(row in rows) {assertEquals(row.key,row.state);assertEquals("",row.projectHome);assertEquals("private-request",row.requestSummary);assertEquals("private-result",row.resultSummary);assertEquals(owner.resourceScope,row.resourceScope)}
            assertEquals("Retained private task",db.openHelper.readableDatabase.query("SELECT title FROM tasks WHERE id='saved'").use {it.moveToFirst();it.getString(0)})
            assertEquals(11,db.openHelper.readableDatabase.version)
            db.dao().claimManagementOperation(receipt(owner.copy(profile="new-profile"),"new-home",state="sending"));assertEquals(home,db.dao().managementOperation("new-home")!!.projectHome)
            db.close()
            val reopened=Room.databaseBuilder(context,HermesDatabase::class.java,name).addMigrations(*ALL_HERMES_MIGRATIONS).build()
            try {
                val persisted=reopened.dao().managementOperation("new-home")!!;assertEquals(home,persisted.projectHome);assertEquals("sending",persisted.state)
                reopened.dao().recoverManagementOperations();val recovered=reopened.dao().managementOperation("new-home")!!
                assertEquals(home,recovered.projectHome);assertEquals("unknown",recovered.state);assertEquals("private-request",recovered.requestSummary)
                assertEquals("Retained private notes",reopened.openHelper.readableDatabase.query("SELECT notes FROM tasks WHERE id='saved'").use {it.moveToFirst();it.getString(0)})
            } finally {reopened.close()}
        } finally {db.close();context.deleteDatabase(name)}
    }
}