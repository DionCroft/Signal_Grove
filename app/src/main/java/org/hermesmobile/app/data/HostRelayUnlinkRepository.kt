package org.hermesmobile.app.data

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.*
import java.util.UUID

class HostRelayUnlinkRepository(private val dao:HermesDao,private val ready:Deferred<Unit>,private val relay:HostRelayRepository,private val targets:HostRelayTargetStore) {
    suspend fun execute(target:ManagementTarget,request:HostRelayUnlinkRequest,api:ManagementClient,tokens:ManagementTokens):HostRelayUnlinkOutcome {
        ready.await();require(target==request.expected.owner)
        if(dao.unresolvedManagementOperations(target.resourceScope,target.url).isNotEmpty()) throw ManagementOutcomePending()
        val before=relay.read(target,api,tokens,request.expected.scope.homeId)
        if(!request.same(before)) throw HostRelayChanged()
        val record=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"RelayUnlink",request.platform,request.summary().toString(),"sending","",System.currentTimeMillis())
        val binding=HostRelayUnlinkBinding.parse(record,target)
        try {targets.save(binding,request.account)} catch(e:CancellationException) {throw e} catch(e:HostRelayPrivateFull) {throw e} catch(e:Exception) {throw HostRelayPrivateUnavailable()}
        try {
            dao.claimManagementOperation(record);currentCoroutineContext().ensureActive()
            request.acknowledge(api.unlinkHostRelay(tokens,request))
            withContext(NonCancellable) {dao.finishManagementOperation(record.key,"acknowledged",buildJsonObject {put("acknowledged",true);put("unlinked",true)}.toString())}
            val receipt=dao.managementOperation(record.key)!!
            val after=relay.read(target,api,tokens,request.expected.scope.homeId)
            return HostRelayUnlinkOutcome(after,receipt,request.observedAbsent(after))
        } catch(e:Exception) {
            val state=withContext(NonCancellable) {dao.finishManagementOperation(record.key,"unknown","");dao.managementOperation(record.key)?.state}
            if(state==null) runCatching {targets.remove(binding)}
            if(e is CancellationException) throw e
            if(state=="unknown") throw HostRelayUnlinkUncertain()
            if(state=="acknowledged") throw HostRelayUnlinkReadbackPending()
            throw e
        }
    }
    suspend fun local(target:ManagementTarget,key:String):HostRelayLocalTarget {
        ready.await();val record=dao.managementOperation(key) ?: throw HostRelayPrivateUnavailable()
        val binding=HostRelayUnlinkBinding.parse(record,target)
        val value=try {targets.read(binding)} catch(e:CancellationException) {throw e} catch(e:Exception) {throw HostRelayPrivateUnavailable()}
        return HostRelayLocalTarget(binding,value)
    }
}
