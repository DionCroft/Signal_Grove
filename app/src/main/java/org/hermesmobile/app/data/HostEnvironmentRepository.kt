package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*
import java.util.UUID

data class HostEnvOutcome(val inventory:HostEnvInventory,val receipt:ManagementOperation)
class HostEnvironmentRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    private fun scope(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens) {GatewayCronInventory.requireProfile(target.profile);require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)}
    suspend fun read(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens):HostEnvInventory {
        scope(target,api,tokens);api.identity(tokens);val home=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome
        if(home==null) throw HostEnvUnavailable()
        return HostEnvInventory.parse(api.environment(tokens,target.profile),target,home)
    }
    suspend fun execute(target:ManagementTarget,request:HostEnvRequest,api:ManagementClient,tokens:ManagementTokens):HostEnvOutcome {
        ready.await();scope(target,api,tokens);require(target==request.expected.owner)
        if(dao.unresolvedManagementOperations(target.resourceScope,target.url).isNotEmpty()) throw ManagementOutcomePending()
        api.identity(tokens);val beforeHome=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome
        if(beforeHome?.identity()!=request.expected.homeId) throw HostEnvChanged()
        val before=HostEnvInventory.parse(api.environment(tokens,target.profile),target,beforeHome);if(!request.same(before)) throw HostEnvChanged()
        val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,request.receiptAction,request.key,request.summary().toString(),"sending","",System.currentTimeMillis())
        return try {
            dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
            val summary=request.acknowledge(api.environmentControl(tokens,target.profile,request)).toString()
            withContext(NonCancellable) {dao.finishManagementOperation(receipt.key,"acknowledged",summary)}
            val home=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome
            if(home?.identity()!=request.expected.homeId) throw HostEnvReadbackPending()
            val after=HostEnvInventory.parse(api.environment(tokens,target.profile),target,home)
            if(!request.presenceMatches(after)) throw HostEnvReadbackPending()
            // Presence cannot identify the saved value, mirror cleanup, provider validity or revocation.
            HostEnvOutcome(after,dao.managementOperation(receipt.key)!!)
        } catch(e:Exception) {
            val state=withContext(NonCancellable) {dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")};dao.managementOperation(receipt.key)?.state}
            if(e is CancellationException) throw e
            if(state=="unknown") throw HostEnvUncertain()
            if(state=="acknowledged") throw HostEnvReadbackPending()
            throw e
        }
    }
}
