package org.hermesmobile.app.data

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.*
import java.util.UUID

class McpCreationRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    suspend fun create(target:ManagementTarget,creation:McpCreation,api:ManagementClient,tokens:ManagementTokens):McpOutcome {
        ready.await()
        if(dao.unresolvedManagementOperations(target.resourceScope,target.url).isNotEmpty()) throw ManagementOutcomePending()
        require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
        api.identity(tokens)
        val before=McpProtocol.inventory(api.mcp(tokens,target.profile))
        if(before.any {it.name==creation.name}) throw McpReviewChanged(before)
        val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"Create",creation.name,creation.summary(),"sending","",System.currentTimeMillis())
        try {
            dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
            val acknowledgement=api.createMcp(tokens,target.profile,creation)
            require(creation.matches(acknowledgement))
            val result=buildJsonObject {put("accepted",true);put("transport",creation.transport.name);put("auth",creation.auth.name)}.toString()
            dao.finishManagementOperation(receipt.key,"acknowledged",result)
            val readback=api.mcp(tokens,target.profile)
            val servers=McpProtocol.inventory(readback)
            if(readback.items("servers").singleOrNull {it.text("name")==creation.name}?.let(creation::matches)!=true) throw McpReadbackPending()
            dao.confirmManagementOperation(receipt.key,result)
            return McpOutcome(dao.managementOperation(receipt.key),servers)
        } catch(e:Exception) {
            withContext(NonCancellable) {dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")}}
            throw e
        }
    }
}
