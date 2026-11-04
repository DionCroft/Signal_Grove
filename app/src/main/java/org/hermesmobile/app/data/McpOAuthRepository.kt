package org.hermesmobile.app.data

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.*
import java.util.UUID

class McpOAuthRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    private fun check(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens) {
        require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
    }
    private fun receipt(target:ManagementTarget,action:String,name:String,summary:String)=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,action,name,summary,"sending","",System.currentTimeMillis())
    private suspend fun unknown(key:String)=withContext(NonCancellable) {dao.managementOperation(key)?.let {dao.finishManagementOperation(it.key,"unknown","")}}
    suspend fun start(target:ManagementTarget,server:ManagedServer,api:ManagementClient,tokens:ManagementTokens,allowHttp:Boolean=false):Pair<ManagementOperation,McpOAuthSnapshot> {
        ready.await();check(target,api,tokens);require(McpOAuthSnapshot.eligible(server))
        if(dao.unresolvedManagementOperations(target.resourceScope,target.url).isNotEmpty()) throw ManagementOutcomePending()
        api.identity(tokens)
        val fresh=McpProtocol.inventory(api.mcp(tokens,target.profile))
        if(fresh.singleOrNull {it.name==server.name}!=server) throw McpReviewChanged(fresh)
        val record=receipt(target,"OAuthStart",server.name,"{}")
        try {
            dao.claimManagementOperation(record);currentCoroutineContext().ensureActive()
            val snapshot=McpOAuthSnapshot.parse(api.startMcpOAuth(tokens,target.profile,server.name),server.name,allowHttp=allowHttp)
            dao.finishManagementOperation(record.key,"acknowledged",snapshot.summary())
            return dao.managementOperation(record.key)!! to snapshot
        } catch(e:Exception) {unknown(record.key);throw e}
    }
    private suspend fun original(target:ManagementTarget,key:String):Pair<ManagementOperation,String> {
        ready.await()
        val receipt=dao.managementOperation(key) ?: error("Missing flow receipt.")
        require(receipt.resourceScope==target.resourceScope && receipt.url==target.url && receipt.apiScope==target.apiScope && receipt.profile==target.profile && receipt.actor==target.actor && receipt.action=="OAuthStart" && receipt.state=="acknowledged")
        val id=Json.parseToJsonElement(receipt.resultSummary).jsonObject.text("flow_id");require(McpOAuthSnapshot.validId(id))
        return receipt to id
    }
    suspend fun read(target:ManagementTarget,key:String,api:ManagementClient,tokens:ManagementTokens,allowHttp:Boolean=false):McpOAuthSnapshot {
        check(target,api,tokens);val (receipt,id)=original(target,key);api.identity(tokens)
        val response=try {api.mcpOAuthFlow(tokens,id)} catch(e:HermesHttpException) {if(e.code==404) throw McpOAuthUnavailable() else throw e}
        val snapshot=McpOAuthSnapshot.parse(response,receipt.target,id,allowHttp)
        // Reported flow status may reopen after approval/error; it is not a run terminal state.
        dao.updateMcpOAuthObservation(key,snapshot.summary())
        return snapshot
    }
    suspend fun cancel(target:ManagementTarget,key:String,api:ManagementClient,tokens:ManagementTokens):ManagementOperation {
        check(target,api,tokens);val (original,id)=original(target,key)
        if(dao.unresolvedManagementOperations(target.resourceScope,target.url).isNotEmpty()) throw ManagementOutcomePending()
        api.identity(tokens)
        val record=receipt(target,"OAuthCancel",original.target,buildJsonObject {put("start_key",key)}.toString())
        try {
            dao.claimManagementOperation(record);currentCoroutineContext().ensureActive()
            val result=McpOAuthSnapshot.cancellation(api.cancelMcpOAuth(tokens,id),id)
            dao.finishManagementOperation(record.key,"acknowledged",result)
            return dao.managementOperation(record.key)!!
        } catch(e:Exception) {unknown(record.key);throw e}
    }
}
