package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*
import java.util.UUID

class McpCatalogRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    suspend fun install(target:ManagementTarget,request:McpCatalogInstall,api:ManagementClient,tokens:ManagementTokens):ManagementOperation {
        ready.await()
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
        api.identity(tokens)
        val fresh=McpCatalogEntry.inventory(api.catalog(tokens,target.profile)).singleOrNull {it.name==request.entry.name}
        if(fresh==null || !request.entry.same(fresh) || fresh.installed) throw McpCatalogChanged()
        val servers=McpProtocol.inventory(api.mcp(tokens,target.profile))
        if(servers.any {it.name==request.entry.name}) throw McpReviewChanged(servers)
        val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"CatalogInstall",request.entry.name,request.summary(),"sending","",System.currentTimeMillis())
        try {
            dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
            val result=request.acknowledge(api.installCatalog(tokens,target.profile,request))
            // An accepted action name is not a unique process handle. Never claim completion.
            dao.finishManagementOperation(receipt.key,"acknowledged",result)
            return dao.managementOperation(receipt.key)!!
        } catch(e:Exception) {
            withContext(NonCancellable) {dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")}}
            throw e
        }
    }
}
