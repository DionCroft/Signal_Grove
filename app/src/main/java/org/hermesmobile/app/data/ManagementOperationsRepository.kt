package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*
import java.util.UUID

class McpReviewChanged(val servers:List<ManagedServer>):Exception()
class McpReadbackPending:Exception()
class ManagementOutcomePending:Exception()
data class McpOutcome(val receipt:ManagementOperation?,val servers:List<ManagedServer>,val observedOnly:Boolean=false)

class ManagementOperationsRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    suspend fun execute(target:ManagementTarget,request:McpRequest,api:ManagementClient,tokens:ManagementTokens):McpOutcome {
        ready.await()
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId) {"The management target changed."}
        api.identity(tokens)
        val before=McpProtocol.inventory(api.mcp(tokens,target.profile))
        val current=before.singleOrNull {it.name==request.name}
        if(request.action==McpAction.Delete && current==null) return McpOutcome(null,before,true)
        if(current==null || current.source!=request.expectedSource) throw McpReviewChanged(before)
        if(request.action==McpAction.Toggle && current.enabled==request.enabled) return McpOutcome(null,before,true)
        if(current.enabled!=request.expectedEnabled) throw McpReviewChanged(before)
        val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,
            request.action.name,request.name,request.summary(),"sending","",System.currentTimeMillis())
        try {
            dao.claimManagementOperation(receipt)
            currentCoroutineContext().ensureActive()
            val result=McpProtocol.acknowledge(request,api.mcpControl(tokens,target.profile,request))
            dao.finishManagementOperation(receipt.key,"acknowledged",result)
            val after=McpProtocol.inventory(api.mcp(tokens,target.profile))
            if(request.action==McpAction.Probe || McpProtocol.observed(request,after)) dao.confirmManagementOperation(receipt.key,result)
            else throw McpReadbackPending()
            return McpOutcome(dao.managementOperation(receipt.key),after)
        } catch(e:Exception) {
            withContext(NonCancellable) {
                // A suspended claim can commit before cancellation is delivered; check its exact key.
                dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")}
            }
            throw e
        }
    }
    suspend fun review(target:ManagementTarget,key:String) {
        ready.await()
        dao.reviewManagementOperation(key,target.resourceScope)
    }
}
