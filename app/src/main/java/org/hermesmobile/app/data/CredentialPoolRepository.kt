package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*
import java.util.UUID

class CredentialPoolOutcome(val inventory:CredentialPoolInventory,val receipt:ManagementOperation,val reviewedIdPresent:Boolean?,val report:PoolCleanupReport?)
class CredentialPoolRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    private fun scope(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens) {GatewayCronInventory.requireProfile(target.profile);require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)}
    private suspend fun home(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens,expected:String?=null):String {
        scope(target,api,tokens);api.identity(tokens)
        val id=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome?.identity() ?: throw CredentialPoolUnavailable()
        if(expected!=null && expected!=id) throw CredentialPoolChanged()
        return id
    }
    suspend fun read(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens,expectedHome:String?=null):CredentialPoolInventory {
        val id=home(target,api,tokens,expectedHome)
        val inventory=CredentialPoolInventory.parse(api.credentialPool(tokens,target.profile),target,id)
        home(target,api,tokens,id)
        return inventory
    }
    suspend fun execute(target:ManagementTarget,request:CredentialPoolRequest,api:ManagementClient,tokens:ManagementTokens):CredentialPoolOutcome {
        ready.await();scope(target,api,tokens);require(target==request.expected.owner)
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        val before=read(target,api,tokens,request.expected.homeId)
        if(!request.expected.sameProvider(before,request.provider)) throw CredentialPoolChanged()
        val record=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,request.action.receiptAction,request.provider,request.summary().toString(),"sending","",System.currentTimeMillis())
        var report:PoolCleanupReport?=null
        try {
            dao.claimManagementOperation(record);currentCoroutineContext().ensureActive()
            val response=api.changeCredentialPool(tokens,target.profile,request)
            val summary=request.acknowledge(response)
            withContext(NonCancellable) {dao.finishManagementOperation(record.key,"acknowledged",summary.toString())}
            if(request.action==CredentialPoolAction.Remove) report=PoolCleanupReport.parse(response,target,request.expected.homeId,record.key,request.provider)
            val after=read(target,api,tokens,request.expected.homeId)
            // Readback is an observation only: count/absence cannot identify the added or removed secret.
            val present=request.entry?.let {entry->after.providers.singleOrNull {it.provider==request.provider}?.entries?.any {it.id==entry.id} ?: false}
            return CredentialPoolOutcome(after,dao.managementOperation(record.key)!!,present,report)
        } catch(e:Exception) {
            val state=withContext(NonCancellable) {dao.finishManagementOperation(record.key,"unknown","");dao.managementOperation(record.key)?.state}
            if(e is CancellationException) throw e
            if(state=="unknown") throw CredentialPoolUncertain()
            if(state=="acknowledged") throw CredentialPoolReadbackPending(report)
            throw e
        }
    }
}
