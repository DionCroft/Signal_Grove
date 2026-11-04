package org.hermesmobile.app.data

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.*
import java.util.UUID

data class PluginInstallOutcome(val receipt:ManagementOperation,val report:PluginInstallReport?,val sourceChanged:Boolean)
class PluginInstallRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    suspend fun install(target:ManagementTarget,request:PluginInstallRequest,api:ManagementClient,tokens:ManagementTokens):PluginInstallOutcome {
        ready.await();GatewayCronInventory.requireProfile(target.profile)
        if(dao.unresolvedManagementOperations(target.resourceScope,target.url).isNotEmpty()) throw ManagementOutcomePending()
        require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
        api.identity(tokens)
        return api.gateway(tokens).use {socket ->
            if(request.duplicate(GatewayPluginInventory.parse(socket.plugins(target.profile),target.profile))) throw PluginSourceChanged()
            request.catalog?.let {PluginSourcesProtocol.requireCatalog(it,PluginSourcesProtocol.lookup(socket.lookupPlugin(target.profile,it.name),it.name))}
            val fresh=PluginSourcesProtocol.inspection(socket.inspectPlugin(target.profile,request.inspection.source),request.inspection.source,request.catalog)
            if(!request.same(fresh)) throw PluginSourceChanged()
            request.catalog?.let {PluginSourcesProtocol.requireCatalog(it,PluginSourcesProtocol.lookup(socket.lookupPlugin(target.profile,it.name),it.name))}
            val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"PluginInstall",request.name,request.summary().toString(),"sending","",System.currentTimeMillis())
            try {
                dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
                val result=try {socket.installPlugin(target.profile,request)} catch(e:GatewayRpcException) {
                    if(e.code!=5026 || !e.sourceMoved) throw e
                    val refused=JsonObject(request.summary()+mapOf("source_changed" to JsonPrimitive(true),"published" to JsonPrimitive(false)))
                    withContext(NonCancellable) {dao.finishManagementOperation(receipt.key,"acknowledged",refused.toString())}
                    return@use PluginInstallOutcome(dao.managementOperation(receipt.key)!!,null,true)
                }
                request.acknowledge(result)
                val report=runCatching {PluginInstallReport.parse(result,request.name)}.getOrNull()
                val summary=JsonObject(request.summary()+mapOf("reported_install" to JsonPrimitive(true))+ (report?.summary() ?: buildJsonObject {put("details_available",false)}))
                withContext(NonCancellable) {dao.finishManagementOperation(receipt.key,"acknowledged",summary.toString())}
                PluginInstallOutcome(dao.managementOperation(receipt.key)!!,report,false)
            } catch(e:Exception) {
                withContext(NonCancellable) {dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")}}
                throw e
            }
        }
    }
}
