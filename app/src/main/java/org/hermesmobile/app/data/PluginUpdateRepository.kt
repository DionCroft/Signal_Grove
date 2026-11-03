package org.hermesmobile.app.data

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.*
import java.util.UUID

data class PluginUpdateOutcome(val inventory:GatewayPluginInventory,val receipt:ManagementOperation,val decision:PluginUpdateDecision?,val report:PluginInstallReport?,val kind:PluginUpdateKind)
class PluginUpdateRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    suspend fun execute(target:ManagementTarget,request:PluginUpdateRequest,api:ManagementClient,tokens:ManagementTokens):PluginUpdateOutcome {
        ready.await();GatewayCronInventory.requireProfile(target.profile)
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
        request.decision?.let {require(it.owner==target && it.eligible())}
        api.identity(tokens)
        val home=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome
        if(!request.directory.belongsTo(home)) throw PluginUpdateChanged()
        return api.gateway(tokens).use {socket ->
            val before=GatewayPluginInventory.parse(socket.plugins(target.profile),target.profile,home)
            val row=before.plugins.singleOrNull {it.key==request.expected.key}
            if(row==null || !request.same(row) || PluginRemovalRequest.collision(row,before)) throw PluginUpdateChanged(before)
            val catalog=if(row.catalog.isNotBlank()) PluginSourcesProtocol.lookup(socket.lookupPlugin(target.profile,row.catalog),row.catalog).entry?.also {
                if(it.sha!=row.catalogSha || request.decision?.catalog?.let {previous->!previous.same(it)}==true) throw PluginUpdateChanged(before)
            } ?: throw PluginUpdateChanged(before) else null
            request.decision?.let {if(!it.eligible()) throw PluginUpdateChanged(before)}
            val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"PluginUpdate",request.expected.key,request.summary().toString(),"sending","",System.currentTimeMillis())
            try {
                dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
                val raw=socket.updatePlugin(target.profile,request);val reply=request.acknowledge(raw)
                val decision=if(reply.kind!=PluginUpdateKind.Updated) runCatching {PluginUpdateDecision.parse(raw,reply,target,row,receipt.key,catalog)}.getOrNull() else null
                val report=if(reply.kind==PluginUpdateKind.Updated) runCatching {PluginInstallReport.parse(raw,reply.name,requireInstallDetails=false)}.getOrNull() else null
                val summary=JsonObject(request.summary()+reply.summary()+buildJsonObject {if(catalog!=null && reply.kind==PluginUpdateKind.Updated && reply.pin.isNotBlank()) put("pin_changed_during_update",reply.pin!=request.expected.catalogSha)}+(decision?.summary() ?: report?.summary() ?: buildJsonObject {put("details_available",false)}))
                withContext(NonCancellable) {dao.finishManagementOperation(receipt.key,"acknowledged",summary.toString())}
                if(reply.kind!=PluginUpdateKind.Updated) return@use PluginUpdateOutcome(before,dao.managementOperation(receipt.key)!!,decision,null,reply.kind)
                val afterHome=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome
                if(!request.directory.belongsTo(afterHome)) throw PluginUpdateReadbackPending()
                val after=GatewayPluginInventory.parse(socket.plugins(target.profile),target.profile,afterHome)
                val observed=request.observedRow(reply,after) ?: throw PluginUpdateReadbackPending()
                // Git replies carry no revision; only a reported catalog pin can be checked separately.
                if(catalog!=null) {
                    if(!request.reportedPinObserved(reply,observed)) throw PluginUpdateReadbackPending()
                    withContext(NonCancellable) {dao.confirmManagementOperation(receipt.key,summary.toString())}
                }
                PluginUpdateOutcome(after,dao.managementOperation(receipt.key)!!,null,report,reply.kind)
            } catch(e:Exception) {
                val retained=withContext(NonCancellable) {dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")};dao.managementOperation(receipt.key)?.state}
                if(e is CancellationException) throw e
                if(retained=="unknown") throw PluginUpdateUncertain()
                if(retained=="acknowledged") throw PluginUpdateReadbackPending()
                throw e
            }
        }
    }
}
