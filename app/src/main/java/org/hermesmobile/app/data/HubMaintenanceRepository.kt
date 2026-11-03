package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*
import java.util.UUID

class HubMaintenanceRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    suspend fun execute(target:ManagementTarget,request:HubMaintenanceRequest,api:ManagementClient,tokens:ManagementTokens):ManagementOperation {
        ready.await();SkillProtocol.profile(target.profile)
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
        api.identity(tokens)
        val inventory=HubInstalledInventory.from(SkillHubProtocol.sources(api.hubSources(tokens,target.profile)))
        if(!request.same(inventory)) throw SkillHubChanged()
        val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,request.receiptAction,if(request.action==HubMaintenanceAction.Remove) request.name else target.profile,request.summary(),"sending","",System.currentTimeMillis())
        try {
            dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
            val result=request.acknowledge(api.maintainHubSkills(tokens,target.profile,request))
            dao.finishManagementOperation(receipt.key,"acknowledged",result)
            return dao.managementOperation(receipt.key)!!
        } catch(e:Exception) {
            withContext(NonCancellable) {dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")}}
            throw e
        }
    }
}
