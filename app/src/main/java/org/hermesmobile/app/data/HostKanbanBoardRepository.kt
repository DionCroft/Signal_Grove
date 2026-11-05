package org.hermesmobile.app.data

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.*
import java.util.UUID

data class HostKanbanBoardOutcome(val inventory:HostKanbanInventory,val acknowledgement:HostKanbanBoardAck,val receipt:ManagementOperation) {override fun toString()="HostKanbanBoardOutcome(redacted)"}

/** One atomic shared claim, one mutation attempt, ACK persistence before independently cancellable readback. */
class HostKanbanBoardRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    private val reads=HostKanbanRepository()
    private val projects=HostProjectsRepository()
    private suspend fun verifyHome(owner:ManagementTarget,home:String,api:ManagementClient,tokens:ManagementTokens) {
        if(api.profiles(tokens).singleOrNull {it.name==owner.profile}?.pluginHome?.identity()!=home) throw HostKanbanBoardChanged()
        currentCoroutineContext().ensureActive()
    }
    suspend fun execute(owner:ManagementTarget,request:HostKanbanBoardChange,api:ManagementClient,tokens:ManagementTokens):HostKanbanBoardOutcome {
        ready.await();require(request.expected.owner==owner && owner.url==api.base.toString() && owner.actor==tokens.provider+" · "+tokens.userId)
        val home=request.expected.profileHomeId
        if(dao.unresolvedManagementOperations(owner.resourceScope,owner.url,home).isNotEmpty()) throw ManagementOutcomePending()
        val before=reads.inventory(owner,home,api,tokens);if(!request.same(before)) throw HostKanbanBoardChanged()
        if(request.project!=null && !request.projectCurrent(projects.read(owner,home,api,tokens))) throw HostKanbanBoardChanged()
        verifyHome(owner,home,api,tokens)
        val record=ManagementOperation(UUID.randomUUID().toString(),owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,request.action.receiptAction,request.receiptTarget,request.summary().toString(),"sending","",System.currentTimeMillis())
        var attempted=false
        try {
            dao.claimManagementOperation(record);currentCoroutineContext().ensureActive()
            attempted=true
            val ack=request.acknowledge(api.changeKanbanBoard(tokens,owner.profile,request))
            val summary=request.acknowledgedSummary(ack)
            withContext(NonCancellable) {dao.finishManagementOperation(record.key,"acknowledged",summary.toString())}
            verifyHome(owner,home,api,tokens)
            val after=reads.inventory(owner,home,api,tokens)
            if(!request.rootSame(after)) throw HostKanbanBoardChanged()
            val selectedAfter=after.boards.singleOrNull {it.slug==request.slug}
            if(request.selected!=null && selectedAfter!=null && selectedAfter.dbPath!=request.selected.dbPath) throw HostKanbanBoardChanged()
            if(request.observed(ack,after)) withContext(NonCancellable) {dao.confirmManagementOperation(record.key,JsonObject(summary+mapOf("configuration_observed" to JsonPrimitive(true))).toString())}
            return HostKanbanBoardOutcome(after,ack,dao.managementOperation(record.key)!!)
        } catch(e:Exception) {
            val retained=withContext(NonCancellable) {
                val row=dao.managementOperation(record.key)
                if(row?.state in setOf("sending","unknown")) dao.finishManagementOperation(record.key,if(attempted) "unknown" else "refused",if(attempted) "" else buildJsonObject {put("local_not_attempted",true)}.toString())
                dao.managementOperation(record.key)
            }
            if(e is CancellationException) throw e
            when(retained?.state) {"unknown"->throw HostKanbanBoardUncertain(retained);"acknowledged","confirmed"->throw HostKanbanBoardReadbackPending(retained);"refused"->throw HostKanbanBoardRefused(retained);else->throw e}
        }
    }
}
