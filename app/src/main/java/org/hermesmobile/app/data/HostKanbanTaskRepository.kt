package org.hermesmobile.app.data

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.*
import java.util.UUID

data class HostKanbanTaskOutcome(val detail:HostKanbanDetail?,val board:HostKanbanSnapshot?,val acknowledgement:HostKanbanTaskAck,val receipt:ManagementOperation) {override fun toString()="HostKanbanTaskOutcome(redacted)"}

/** Exact reviewed phase, shared atomic claim, one send, and durable ACK before cancellable observation. */
class HostKanbanTaskRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    private val reads=HostKanbanRepository()
    private suspend fun home(request:HostKanbanTaskChange,api:ManagementClient,tokens:ManagementTokens) {
        if(api.profiles(tokens).singleOrNull {it.name==request.owner.profile}?.pluginHome?.identity()!=request.home) throw HostKanbanTaskChanged()
        currentCoroutineContext().ensureActive()
    }
    suspend fun execute(owner:ManagementTarget,request:HostKanbanTaskChange,api:ManagementClient,tokens:ManagementTokens):HostKanbanTaskOutcome {
        ready.await();require(request.owner==owner && owner.url==api.base.toString() && owner.actor==tokens.provider+" · "+tokens.userId)
        if(dao.unresolvedManagementOperations(owner.resourceScope,owner.url,request.home).isNotEmpty()) throw ManagementOutcomePending()
        suspend fun inventory() {if(!request.sameInventory(reads.inventory(owner,request.home,api,tokens))) throw HostKanbanTaskChanged()}
        inventory()
        for(expected in listOfNotNull(request.subject,request.related)) {
            if(!request.sameDetail(expected,reads.detail(owner,request.home,request.board,expected.task.id,api,tokens))) throw HostKanbanTaskChanged()
        }
        for(parent in request.parents) if(reads.detail(owner,request.home,request.board,parent.id,api,tokens).task!=parent) throw HostKanbanTaskChanged()
        if(request.project!=null && !request.projectCurrent(HostProjectsRepository().read(owner,request.home,api,tokens))) throw HostKanbanTaskChanged()
        inventory();home(request,api,tokens)
        val record=ManagementOperation(UUID.randomUUID().toString(),owner.resourceScope,owner.apiScope,owner.url,owner.profile,owner.actor,request.action.receiptAction,request.receiptTarget,request.summary().toString(),"sending","",System.currentTimeMillis())
        var attempted=false
        try {
            dao.claimManagementOperation(record);currentCoroutineContext().ensureActive();attempted=true
            val ack=request.acknowledge(api.changeKanbanTask(tokens,owner.profile,request));val summary=request.acknowledgedSummary(ack)
            withContext(NonCancellable) {dao.finishManagementOperation(record.key,"acknowledged",summary.toString())}
            home(request,api,tokens);inventory()
            val after=if(request.action==HostKanbanTaskAction.Delete) reads.board(owner,request.home,request.board,api,tokens) else null
            val id=ack.task?.id ?: request.id
            val detail=if(request.action==HostKanbanTaskAction.Delete) null else reads.detail(owner,request.home,request.board,id!!,api,tokens)
            home(request,api,tokens);inventory()
            if(request.observed(ack,detail,after)) withContext(NonCancellable) {dao.confirmManagementOperation(record.key,JsonObject(summary+mapOf("configuration_observed" to JsonPrimitive(true))).toString())}
            return HostKanbanTaskOutcome(detail,after,ack,dao.managementOperation(record.key)!!)
        } catch(e:Exception) {
            val retained=withContext(NonCancellable) {
                val row=dao.managementOperation(record.key)
                if(row?.state in setOf("sending","unknown")) dao.finishManagementOperation(record.key,if(attempted) "unknown" else "refused",if(attempted) "" else buildJsonObject {put("local_not_attempted",true)}.toString())
                dao.managementOperation(record.key)
            }
            if(e is CancellationException) throw e
            when(retained?.state) {"unknown"->throw HostKanbanTaskUncertain(retained);"acknowledged","confirmed"->throw HostKanbanTaskReadbackPending(retained);"refused"->throw HostKanbanTaskRefused(retained);else->throw e}
        }
    }
}
