package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*
import java.util.UUID

data class PluginSettingsOutcome(val snapshot:PluginSettingsSnapshot,val receipt:ManagementOperation)
class PluginSettingsRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    private fun scope(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens) {GatewayCronInventory.requireProfile(target.profile);require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)}
    suspend fun read(target:ManagementTarget,expected:ManagedPlugin,api:ManagementClient,tokens:ManagementTokens):PluginSettingsSnapshot {
        scope(target,api,tokens);api.identity(tokens);val home=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome
        return api.gateway(tokens).use {socket->PluginSettingsSnapshot.parse(socket.plugins(target.profile),target,home,expected.key).also {if(!it.sameRow(expected)) throw PluginSettingsChanged()}}
    }
    suspend fun execute(target:ManagementTarget,request:PluginSettingsRequest,api:ManagementClient,tokens:ManagementTokens):PluginSettingsOutcome {
        ready.await();scope(target,api,tokens);require(request.expected.owner==target)
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        api.identity(tokens);val home=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome
        if(home?.identity()!=request.expected.homeId) throw PluginSettingsChanged()
        return api.gateway(tokens).use {socket->
            val before=PluginSettingsSnapshot.parse(socket.plugins(target.profile),target,home,request.expected.plugin.key)
            if(!request.expected.same(before)) throw PluginSettingsChanged()
            val summary=request.summary().toString()
            val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"PluginSettings",request.expected.plugin.key,summary,"sending","",System.currentTimeMillis())
            try {
                dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
                request.acknowledge(socket.savePluginSetting(target.profile,request))
                withContext(NonCancellable) {dao.finishManagementOperation(receipt.key,"acknowledged",summary)}
                val afterHome=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome
                if(afterHome?.identity()!=request.expected.homeId) throw PluginSettingsReadbackPending()
                val after=PluginSettingsSnapshot.parse(socket.plugins(target.profile),target,afterHome,request.expected.plugin.key)
                if(request.observed(after)) withContext(NonCancellable) {dao.confirmManagementOperation(receipt.key,summary)}
                else if(!request.field.key.contains('.')) throw PluginSettingsReadbackPending()
                PluginSettingsOutcome(after,dao.managementOperation(receipt.key)!!)
            } catch(e:Exception) {
                val retained=withContext(NonCancellable) {dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")};dao.managementOperation(receipt.key)?.state}
                if(e is CancellationException) throw e
                if(retained=="unknown") throw PluginSettingsUncertain()
                if(retained=="acknowledged") throw PluginSettingsReadbackPending()
                throw e
            }
        }
    }
}
