package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*
import java.util.UUID

class HostChannelOutcome(val inventory:HostChannelInventory,val receipt:ManagementOperation,val different:Boolean=false,val hotServed:Boolean?=null,val posture:HostChannelPosture?=null)
class HostChannelsRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    private suspend fun home(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens,expected:String?=null):String {
        GatewayCronInventory.requireProfile(target.profile);require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
        api.identity(tokens);val id=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome?.identity() ?: throw HostChannelsUnavailable()
        if(expected!=null && expected!=id) throw HostChannelsChanged();return id
    }
    suspend fun read(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens,expectedHome:String?=null):HostChannelInventory {
        val id=home(target,api,tokens,expectedHome)
        val result=HostChannelInventory.parse(api.hostChannels(tokens,target.profile),target,id)
        home(target,api,tokens,id);return result
    }
    suspend fun execute(target:ManagementTarget,request:HostChannelRequest,api:ManagementClient,tokens:ManagementTokens):HostChannelOutcome {
        ready.await();require(target==request.expected.owner)
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        val before=read(target,api,tokens,request.expected.homeId);if(!request.same(before)) throw HostChannelsChanged()
        val record=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,request.action,request.id,request.summary().toString(),"sending","",System.currentTimeMillis())
        try {
            dao.claimManagementOperation(record);currentCoroutineContext().ensureActive()
            val raw=if(request.draft==null) api.hostChannelPosture(tokens,target.profile,request.id) else api.changeHostChannel(tokens,target.profile,request)
            val ack=request.acknowledge(raw)
            withContext(NonCancellable) {dao.finishManagementOperation(record.key,"acknowledged",ack.summary().toString())}
            val receipt=dao.managementOperation(record.key)!!
            if(request.draft==null) return HostChannelOutcome(before,receipt,posture=HostChannelPosture(before,request.id,ack,HostChannelPostureDetails.parse(raw),receipt))
            val after=read(target,api,tokens,request.expected.homeId)
            return HostChannelOutcome(after,receipt,!request.observed(after),ack.hotServed)
        } catch(e:Exception) {
            val state=withContext(NonCancellable) {dao.finishManagementOperation(record.key,"unknown","");dao.managementOperation(record.key)?.state}
            if(e is CancellationException) throw e
            if(state=="unknown") throw HostChannelsUncertain()
            if(state=="acknowledged") throw HostChannelsReadbackPending()
            throw e
        }
    }
}
