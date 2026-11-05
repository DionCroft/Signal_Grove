package org.hermesmobile.app.data

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.*
import java.util.UUID

class HostProjectChangeRefused(val receipt:ManagementOperation?=null):Exception("Project change was not sent or the target was missing.")
data class HostProjectChangeOutcome(val inventory:HostProjectSnapshot,val acknowledgement:HostProjectAcknowledgement,val receipt:ManagementOperation) {
    override fun toString()="HostProjectChangeOutcome(redacted)"
}

/** One durable claim and at most one mutation. Readback never replays the write. */
class HostProjectOperationsRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    private fun scope(target:ManagementTarget,home:String,api:ManagementClient,tokens:ManagementTokens) {
        GatewayCronInventory.requireProfile(target.profile)
        require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
        require(home.matches(Regex("[a-f0-9]{64}")))
    }
    private suspend fun home(target:ManagementTarget,home:String,api:ManagementClient,tokens:ManagementTokens) {
        if(api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome?.identity()!=home) throw HostProjectChangeChanged()
        currentCoroutineContext().ensureActive()
    }
    suspend fun execute(target:ManagementTarget,request:HostProjectChange,api:ManagementClient,tokens:ManagementTokens):HostProjectChangeOutcome {
        ready.await();val expected=request.expected;scope(target,expected.homeId,api,tokens);require(expected.owner==target)
        if(dao.unresolvedManagementOperations(target.resourceScope,target.url,expected.homeId).isNotEmpty()) throw ManagementOutcomePending()
        api.identity(tokens);home(target,expected.homeId,api,tokens)
        return api.gateway(tokens).use {socket->
            val before=withContext(Dispatchers.Default) {HostProjectProtocol.inventory(socket.projects(target.profile),target,expected.homeId)}
            if(!request.same(before)) throw HostProjectChangeChanged()
            home(target,expected.homeId,api,tokens)
            val record=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,
                request.action.receiptAction,request.receiptTarget,request.summary().toString(),"sending","",System.currentTimeMillis(),expected.homeId)
            var sent=false
            try {
                dao.claimManagementOperation(record);currentCoroutineContext().ensureActive()
                sent=true
                val ack=request.acknowledge(socket.changeProject(target.profile,request))
                val summary=request.acknowledgementSummary(ack)
                withContext(NonCancellable) {dao.finishManagementOperation(record.key,"acknowledged",summary.toString())}
                home(target,expected.homeId,api,tokens)
                val after=withContext(Dispatchers.Default) {HostProjectProtocol.inventory(socket.projects(target.profile),target,expected.homeId)}
                home(target,expected.homeId,api,tokens)
                if(request.observed(ack,after)) withContext(NonCancellable) {
                    dao.confirmManagementOperation(record.key,JsonObject(summary+mapOf("configuration_observed" to JsonPrimitive(true))).toString())
                }
                HostProjectChangeOutcome(after,ack,dao.managementOperation(record.key)!!)
            } catch(e:Exception) {
                val retained=withContext(NonCancellable) {
                    val row=dao.managementOperation(record.key)
                    if(row?.state in setOf("sending","unknown")) {
                        val missing=sent && e is GatewayRpcException && e.code==5062 && request.action !in setOf(HostProjectAction.Create,HostProjectAction.ClearActive)
                        if(!sent || missing) dao.finishManagementOperation(record.key,"refused",buildJsonObject {put("reason",if(missing) "missing_project" else "local_not_sent")}.toString())
                        else dao.finishManagementOperation(record.key,"unknown","")
                    }
                    dao.managementOperation(record.key)
                }
                if(e is CancellationException) throw e
                when(retained?.state) {
                    "unknown"->throw HostProjectChangeUncertain(retained)
                    "acknowledged","confirmed"->throw HostProjectChangeReadbackPending(retained)
                    "refused"->throw HostProjectChangeRefused(retained)
                    else->throw e
                }
            }
        }
    }
    /** A verified same-store review changes only the local unknown barrier, never server state. */
    suspend fun review(target:ManagementTarget,expectedHome:String,key:String,api:ManagementClient,tokens:ManagementTokens) {
        ready.await();scope(target,expectedHome,api,tokens)
        val row=dao.managementOperation(key) ?: throw HostProjectChangeChanged()
        if(row.url!=target.url || row.projectHome!=expectedHome || !HostProjectAction.receipt(row.action) || row.state!="unknown") throw HostProjectChangeChanged()
        api.identity(tokens);home(target,expectedHome,api,tokens)
        dao.reviewManagementOperation(key,target.resourceScope,target.url,expectedHome)
    }
}