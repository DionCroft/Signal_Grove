package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*
import java.util.UUID

sealed class HostMainModelOutcome {
    class Warning(val inventory:HostModelInventory,val warning:HostMainModelWarning,val receipt:ManagementOperation):HostMainModelOutcome()
    class Saved(val inventory:HostModelInventory,val receipt:ManagementOperation,val notice:String?):HostMainModelOutcome()
}
class HostMainModelRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    private val inventory=HostModelsRepository()
    suspend fun execute(target:ManagementTarget,request:HostMainModelRequest,api:ManagementClient,tokens:ManagementTokens):HostMainModelOutcome {
        ready.await();require(target==request.expected.owner)
        if(dao.unresolvedManagementOperations(target.resourceScope,target.url).isNotEmpty()) throw ManagementOutcomePending()
        val before=inventory.read(target,api,tokens,expectedHome=request.expected.homeId)
        if(!request.same(before)) throw HostMainModelChanged(before)
        val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"MainModel",request.provider+" · "+request.model,request.summary().toString(),"sending","",System.currentTimeMillis())
        try {
            dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
            when(val reply=request.reply(api.setMainModel(tokens,target.profile,request))) {
                is HostMainModelReply.Warning->{
                    withContext(NonCancellable) {dao.finishManagementOperation(receipt.key,"acknowledged",reply.summary.toString())}
                    val refreshed=HostMainModelRequest(before,request.provider,request.model)
                    return HostMainModelOutcome.Warning(before,HostMainModelWarning(refreshed,reply.warning.message),dao.managementOperation(receipt.key)!!)
                }
                is HostMainModelReply.Saved->{
                    withContext(NonCancellable) {dao.finishManagementOperation(receipt.key,"acknowledged",reply.summary.toString())}
                    val after=inventory.read(target,api,tokens,expectedHome=request.expected.homeId)
                    if(after.homeId!=request.expected.homeId || after.main!=reply.route) throw HostMainModelReadbackPending()
                    // The reported pair does not identify endpoint/credentials, prove causality or hot-swap a session.
                    return HostMainModelOutcome.Saved(after,dao.managementOperation(receipt.key)!!,reply.notice)
                }
            }
        } catch(e:Exception) {
            val state=withContext(NonCancellable) {dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")};dao.managementOperation(receipt.key)?.state}
            if(e is CancellationException) throw e
            if(state=="unknown") throw HostMainModelUncertain()
            if(state=="acknowledged") throw HostMainModelReadbackPending()
            throw e
        }
    }
}
