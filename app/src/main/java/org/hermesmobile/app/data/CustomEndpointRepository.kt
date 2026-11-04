package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*
import java.util.UUID

class CustomEndpointOutcome(val snapshot:CustomEndpointSnapshot,val receipt:ManagementOperation,val different:Boolean)
class CustomEndpointRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    private fun scope(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens) {GatewayCronInventory.requireProfile(target.profile);require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)}
    private suspend fun home(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens,expected:String?=null):String {
        scope(target,api,tokens);api.identity(tokens)
        val id=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome?.identity() ?: throw CustomEndpointUnavailable()
        if(expected!=null && expected!=id) throw CustomEndpointChanged()
        return id
    }
    suspend fun read(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens,expectedHome:String?=null):CustomEndpointSnapshot {
        val id=home(target,api,tokens,expectedHome)
        val raw=api.customEndpointConfig(tokens,target.profile)
        val snapshot=CustomEndpointSnapshot.parse(api.customEndpoints(tokens,target.profile),raw,target,id)
        home(target,api,tokens,id)
        return snapshot
    }
    suspend fun probe(target:ManagementTarget,request:CustomEndpointProbeRequest,api:ManagementClient,tokens:ManagementTokens):CustomEndpointProbeReport {
        ready.await();scope(target,api,tokens);require(target==request.expected.owner)
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        if(!request.expected.same(read(target,api,tokens,request.expected.homeId))) throw CustomEndpointChanged()
        val record=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"CustomEndpointProbe","custom-endpoints",request.summary().toString(),"sending","",System.currentTimeMillis())
        try {
            dao.claimManagementOperation(record);currentCoroutineContext().ensureActive()
            // The official validate route has no profile argument. It uses listener/process networking.
            val response=api.probeCustomEndpoint(tokens,request);val ack=request.acknowledge(response)
            withContext(NonCancellable) {dao.finishManagementOperation(record.key,"acknowledged",ack.summary(request).toString())}
            return CustomEndpointProbeReport(request,ack,CustomEndpointProbeDetails.parse(response),dao.managementOperation(record.key)!!)
        } catch(e:Exception) {
            val state=withContext(NonCancellable) {dao.finishManagementOperation(record.key,"unknown","");dao.managementOperation(record.key)?.state}
            if(e is CancellationException) throw e
            if(state=="unknown") throw CustomEndpointUncertain()
            if(state=="acknowledged") throw CustomEndpointReadbackPending()
            throw e
        }
    }
    suspend fun execute(target:ManagementTarget,request:CustomEndpointRequest,api:ManagementClient,tokens:ManagementTokens):CustomEndpointOutcome {
        ready.await();scope(target,api,tokens);require(target==request.expected.owner)
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        val before=read(target,api,tokens,request.expected.homeId)
        if(!request.expected.same(before,includeReasoning=request.draft?.aliasEffect?.globalEffort!=null)) throw CustomEndpointChanged()
        val record=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,request.action.receiptAction,"custom-endpoints",request.summary().toString(),"sending","",System.currentTimeMillis())
        try {
            dao.claimManagementOperation(record);currentCoroutineContext().ensureActive()
            val response=api.changeCustomEndpoint(tokens,target.profile,request)
            val ack=request.acknowledge(response)
            withContext(NonCancellable) {dao.finishManagementOperation(record.key,"acknowledged",ack.summary.toString())}
            val after=read(target,api,tokens,request.expected.homeId)
            // Observation only; the endpoint has no revision guard or exact credential/cleanup identity.
            val different=when(request.action) {
                CustomEndpointAction.Save->{
                    val row=after.rows.singleOrNull {it.id==ack.id};val draft=request.draft!!
                    ack.id!=request.id || row==null || row.name!=draft.name || row.baseUrl!=draft.baseUrl || row.model!=draft.aliasEffect.effectiveModel || !after.metadataObserved(draft) || row.discoverModels!=draft.discoverModels || draft.apiMode?.let {it!=row.apiMode}==true || draft.contextLength?.let {it!=row.contextLength}==true || !row.models.containsAll(draft.models) || draft.key==CustomEndpointKey.Fresh && !row.hasKey || draft.key==CustomEndpointKey.Clear && row.hasKey || draft.makeDefault && !row.current
                }
                CustomEndpointAction.Activate->ack.provider!=request.id || ack.model!=request.row?.model || after.current.provider!=ack.provider || after.current.model!=ack.model
                CustomEndpointAction.Remove->after.rows.any {it.id==request.id}
            }
            return CustomEndpointOutcome(after,dao.managementOperation(record.key)!!,different)
        } catch(e:Exception) {
            val state=withContext(NonCancellable) {dao.finishManagementOperation(record.key,"unknown","");dao.managementOperation(record.key)?.state}
            if(e is CancellationException) throw e
            if(state=="unknown") throw CustomEndpointUncertain()
            if(state=="acknowledged") throw CustomEndpointReadbackPending()
            throw e
        }
    }
}
