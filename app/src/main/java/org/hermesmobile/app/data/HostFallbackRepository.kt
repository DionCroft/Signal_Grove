package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*
import java.util.UUID

class HostFallbackOutcome(val snapshot:HostFallbackSnapshot,val receipt:ManagementOperation,val matches:Boolean)
class HostFallbackRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    private fun scope(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens) {GatewayCronInventory.requireProfile(target.profile);require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)}
    private suspend fun home(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens,expected:String?=null):String {
        scope(target,api,tokens);api.identity(tokens)
        val id=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome?.identity() ?: throw HostFallbackUnavailable()
        if(expected!=null && expected!=id) throw HostFallbackChanged()
        return id
    }
    suspend fun read(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens,expectedHome:String?=null):HostFallbackSnapshot {
        val id=home(target,api,tokens,expectedHome)
        val snapshot=HostFallbackSnapshot.parse(api.fallbackConfig(tokens,target.profile),target,id)
        home(target,api,tokens,id)
        return snapshot
    }
    suspend fun execute(target:ManagementTarget,request:HostFallbackRequest,api:ManagementClient,tokens:ManagementTokens):HostFallbackOutcome {
        ready.await();scope(target,api,tokens);require(target==request.expected.owner)
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        val before=read(target,api,tokens,request.expected.homeId)
        if(!request.expected.sameConfig(before)) throw HostFallbackChanged()
        val record=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"FallbackConfig","fallback",request.summary().toString(),"sending","",System.currentTimeMillis())
        try {
            dao.claimManagementOperation(record);currentCoroutineContext().ensureActive()
            val response=api.changeFallbackConfig(tokens,target.profile,request)
            val summary=request.acknowledge(response)
            withContext(NonCancellable) {dao.finishManagementOperation(record.key,"acknowledged",summary.toString())}
            val after=read(target,api,tokens,request.expected.homeId)
            return HostFallbackOutcome(after,dao.managementOperation(record.key)!!,request.matches(after))
        } catch(e:Exception) {
            val state=withContext(NonCancellable) {dao.finishManagementOperation(record.key,"unknown","");dao.managementOperation(record.key)?.state}
            if(e is CancellationException) throw e
            if(state=="unknown") throw HostFallbackUncertain()
            if(state=="acknowledged") throw HostFallbackReadbackPending()
            throw e
        }
    }
}
