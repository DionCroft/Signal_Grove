package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*
import java.util.UUID

class HostMoaOutcome(val snapshot:HostMoaSnapshot,val receipt:ManagementOperation,val normalized:Boolean)
class HostMoaRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    private val models=HostModelsRepository()
    suspend fun read(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens,expectedHome:String?=null):HostMoaSnapshot {
        val catalog=models.read(target,api,tokens,expectedHome=expectedHome)
        val config=HostMoaConfig.parse(api.moa(tokens,target.profile))
        val after=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome
        if(after?.identity()!=catalog.homeId) throw HostModelsChanged()
        return HostMoaSnapshot(catalog,config)
    }
    suspend fun execute(target:ManagementTarget,request:HostMoaRequest,api:ManagementClient,tokens:ManagementTokens):HostMoaOutcome {
        ready.await();require(target==request.expected.owner)
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        val before=read(target,api,tokens,request.expected.homeId)
        if(!request.same(before)) throw HostMoaChanged(before)
        request.body(target.profile) // Enforce the request bound before the durable claim.
        val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"MoaConfig","",request.summary().toString(),"sending","",System.currentTimeMillis())
        try {
            dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
            val reply=request.reply(api.saveMoa(tokens,target.profile,request))
            withContext(NonCancellable) {dao.finishManagementOperation(receipt.key,"acknowledged",reply.summary.toString())}
            val after=read(target,api,tokens,request.expected.homeId)
            if(after.config!=reply.config) throw HostMoaReadbackPending()
            return HostMoaOutcome(after,dao.managementOperation(receipt.key)!!,reply.config!=request.desired)
        } catch(e:Exception) {
            val state=withContext(NonCancellable) {dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")};dao.managementOperation(receipt.key)?.state}
            if(e is CancellationException) throw e
            if(state=="unknown") throw HostMoaUncertain()
            if(state=="acknowledged") throw HostMoaReadbackPending()
            throw e
        }
    }
}
