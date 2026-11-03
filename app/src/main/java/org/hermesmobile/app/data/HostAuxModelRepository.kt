package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*
import java.util.UUID

sealed class HostAuxModelOutcome {
    class Warning(val inventory:HostModelInventory,val warning:HostAuxModelWarning,val receipt:ManagementOperation):HostAuxModelOutcome()
    class Saved(val inventory:HostModelInventory,val receipt:ManagementOperation):HostAuxModelOutcome()
}
class HostAuxModelRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    private val inventory=HostModelsRepository()
    suspend fun execute(target:ManagementTarget,request:HostAuxModelRequest,api:ManagementClient,tokens:ManagementTokens):HostAuxModelOutcome {
        ready.await();require(target==request.expected.owner)
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        val before=inventory.read(target,api,tokens,expectedHome=request.expected.homeId)
        if(!request.same(before)) throw HostAuxModelChanged(before)
        val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"AuxModel",request.task,request.summary().toString(),"sending","",System.currentTimeMillis())
        try {
            dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
            when(val reply=request.reply(api.setAuxModel(tokens,target.profile,request))) {
                is HostAuxModelReply.Warning->{
                    withContext(NonCancellable) {dao.finishManagementOperation(receipt.key,"acknowledged",reply.summary.toString())}
                    return HostAuxModelOutcome.Warning(before,HostAuxModelWarning(request.rebase(before),reply.warning.message),dao.managementOperation(receipt.key)!!)
                }
                is HostAuxModelReply.Saved->{
                    withContext(NonCancellable) {dao.finishManagementOperation(receipt.key,"acknowledged",reply.summary.toString())}
                    val after=inventory.read(target,api,tokens,expectedHome=request.expected.homeId)
                    if(!request.observed(after,reply)) throw HostAuxModelReadbackPending()
                    return HostAuxModelOutcome.Saved(after,dao.managementOperation(receipt.key)!!)
                }
            }
        } catch(e:Exception) {
            val state=withContext(NonCancellable) {dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")};dao.managementOperation(receipt.key)?.state}
            if(e is CancellationException) throw e
            if(state=="unknown") throw HostAuxModelUncertain()
            if(state=="acknowledged") throw HostAuxModelReadbackPending()
            throw e
        }
    }
}
