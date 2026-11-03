package org.hermesmobile.app.data

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.*
import java.util.UUID

data class PluginToggleOutcome(val inventory:GatewayPluginInventory,val receipt:ManagementOperation,val report:PluginInstallReport?)
class PluginToggleRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    suspend fun execute(target:ManagementTarget,request:PluginToggleRequest,api:ManagementClient,tokens:ManagementTokens,home:PluginHome?=null):PluginToggleOutcome {
        ready.await();GatewayCronInventory.requireProfile(target.profile)
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
        api.identity(tokens)
        return api.gateway(tokens).use {socket ->
            val before=GatewayPluginInventory.parse(socket.plugins(target.profile),target.profile,home)
            val row=before.plugins.singleOrNull {it.key==request.expected.key}
            if(row==null || !request.same(row) || PluginToggleRequest.collision(row,before)) throw PluginToggleChanged(before)
            val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,request.receiptAction,request.expected.key,request.summary().toString(),"sending","",System.currentTimeMillis())
            try {
                dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
                val result=socket.togglePlugin(target.profile,request);request.acknowledge(result)
                val report=runCatching {PluginInstallReport.parse(result,request.expected.key,requireInstallDetails=false)}.getOrNull()
                val summary=JsonObject(request.summary()+mapOf("reported_change" to JsonPrimitive(true))+(report?.summary() ?: buildJsonObject {put("details_available",false)}))
                withContext(NonCancellable) {dao.finishManagementOperation(receipt.key,"acknowledged",summary.toString())}
                val after=GatewayPluginInventory.parse(socket.plugins(target.profile),target.profile,home)
                if(!request.observed(after)) throw PluginToggleReadbackPending()
                withContext(NonCancellable) {dao.confirmManagementOperation(receipt.key,summary.toString())}
                PluginToggleOutcome(after,dao.managementOperation(receipt.key)!!,report)
            } catch(e:Exception) {
                val retained=withContext(NonCancellable) {
                    dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")}
                    dao.managementOperation(receipt.key)?.state
                }
                if(e is CancellationException) throw e
                if(retained=="unknown") throw PluginToggleOutcomeUncertain()
                if(retained=="acknowledged") throw PluginToggleReadbackPending()
                throw e
            }
        }
    }
}
