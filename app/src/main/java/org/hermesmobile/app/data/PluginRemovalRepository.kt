package org.hermesmobile.app.data

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.*
import java.util.UUID

data class PluginRemovalOutcome(val inventory:GatewayPluginInventory,val receipt:ManagementOperation?,val observedOnly:Boolean=false)
class PluginRemovalRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    suspend fun execute(target:ManagementTarget,request:PluginRemovalRequest,api:ManagementClient,tokens:ManagementTokens):PluginRemovalOutcome {
        ready.await();GatewayCronInventory.requireProfile(target.profile)
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
        api.identity(tokens)
        val home=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome
        if(!request.directory.belongsTo(home)) throw PluginRemovalChanged()
        return api.gateway(tokens).use {socket ->
            val before=GatewayPluginInventory.parse(socket.plugins(target.profile),target.profile,home)
            val row=before.plugins.singleOrNull {it.key==request.expected.key}
            if(request.observed(before)) return@use PluginRemovalOutcome(before,null,true)
            if(row==null || !request.same(row) || PluginRemovalRequest.collision(row,before)) throw PluginRemovalChanged(before)
            val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"PluginRemove",request.expected.key,request.summary().toString(),"sending","",System.currentTimeMillis())
            try {
                dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
                val result=socket.removePlugin(target.profile,request)
                val summary=JsonObject(request.summary()+request.acknowledge(result))
                withContext(NonCancellable) {dao.finishManagementOperation(receipt.key,"acknowledged",summary.toString())}
                val afterHome=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome
                if(!request.directory.belongsTo(afterHome)) throw PluginRemovalReadbackPending()
                val after=GatewayPluginInventory.parse(socket.plugins(target.profile),target.profile,afterHome)
                if(!request.observed(after)) throw PluginRemovalReadbackPending()
                withContext(NonCancellable) {dao.confirmManagementOperation(receipt.key,summary.toString())}
                PluginRemovalOutcome(after,dao.managementOperation(receipt.key))
            } catch(e:Exception) {
                val retained=withContext(NonCancellable) {dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")};dao.managementOperation(receipt.key)?.state}
                if(e is CancellationException) throw e
                if(retained=="unknown") throw PluginRemovalUncertain()
                if(retained=="acknowledged") throw PluginRemovalReadbackPending()
                throw e
            }
        }
    }
}
