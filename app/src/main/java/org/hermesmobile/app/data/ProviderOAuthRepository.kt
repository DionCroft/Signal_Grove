package org.hermesmobile.app.data

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.*
import java.util.UUID

class ProviderOAuthRepository(private val dao:HermesDao,private val ready:Deferred<Unit>,private val codes:ProviderCodeStore) {
    private fun scope(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens) {GatewayCronInventory.requireProfile(target.profile);require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)}
    private suspend fun home(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens,expected:String?=null):String {
        scope(target,api,tokens);api.identity(tokens)
        val id=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome?.identity() ?: throw ProviderOAuthUnavailable()
        if(expected!=null && id!=expected) throw ProviderOAuthChanged()
        return id
    }
    suspend fun read(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens,expectedHome:String?=null):ProviderOAuthInventory {
        val id=home(target,api,tokens,expectedHome)
        val result=ProviderOAuthInventory.parse(api.providerOAuth(tokens,target.profile),target,id)
        home(target,api,tokens,id)
        return result
    }
    private fun record(target:ManagementTarget,action:ProviderOAuthAction,provider:String,summary:JsonObject)=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,action.receiptAction,provider,summary.toString(),"sending","",System.currentTimeMillis())
    private suspend fun failure(key:String,e:Exception):Nothing {
        val state=withContext(NonCancellable) {dao.finishManagementOperation(key,"unknown","");dao.managementOperation(key)?.state}
        if(e is CancellationException) throw e
        if(state=="unknown") throw ProviderOAuthUncertain()
        if(state=="acknowledged") throw ProviderOAuthReadbackPending()
        throw e
    }
    suspend fun execute(target:ManagementTarget,request:ProviderOAuthRequest,api:ManagementClient,tokens:ManagementTokens):ManagementOperation {
        ready.await();require(target==request.expected.owner);scope(target,api,tokens)
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        val before=read(target,api,tokens,request.expected.homeId)
        if(before.rows.singleOrNull {it.id==request.provider.id}!=request.provider) throw ProviderOAuthChanged()
        val receipt=record(target,request.action,request.provider.id,request.summary())
        try {
            dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
            if(request.action==ProviderOAuthAction.Start) {
                val response=api.startProviderOAuth(tokens,target.profile,request.provider.id)
                val summary=ProviderOAuthProtocol.start(response,before.homeId)
                // Capture the mandatory ACK before optional display parsing or storage, including cancellation.
                withContext(NonCancellable) {dao.finishManagementOperation(receipt.key,"acknowledged",summary.toString())}
                val original=dao.managementOperation(receipt.key)!!;val binding=ProviderOAuthBinding.parse(original,target)
                val display=runCatching {ProviderDeviceCode.parse(response,System.currentTimeMillis())}.getOrNull()
                if(display!=null) withContext(NonCancellable+Dispatchers.IO) {runCatching {codes.save(binding,display)}}
                return original
            }
            val summary=ProviderOAuthProtocol.disconnect(api.disconnectProviderOAuth(tokens,target.profile,request.provider.id),request.provider.id)
            withContext(NonCancellable) {dao.finishManagementOperation(receipt.key,"acknowledged",summary.toString())}
            // A separate catalog observation cannot prove revocation, credential health or running-agent adoption.
            read(target,api,tokens,before.homeId)
            return dao.managementOperation(receipt.key)!!
        } catch(e:Exception) {failure(receipt.key,e)}
    }
    private suspend fun original(target:ManagementTarget,key:String):ProviderOAuthBinding {
        ready.await();return ProviderOAuthBinding.parse(dao.managementOperation(key) ?: error("Missing provider flow receipt."),target)
    }
    suspend fun poll(target:ManagementTarget,key:String,api:ManagementClient,tokens:ManagementTokens):ProviderOAuthObservation {
        val binding=original(target,key);home(target,api,tokens,binding.homeId)
        val response=try {api.pollProviderOAuth(tokens,target.profile,binding.provider,binding.sessionId)} catch(e:HermesHttpException) {
            if(e.code==404) {withContext(NonCancellable+Dispatchers.IO) {codes.remove(binding)};throw ProviderOAuthUnavailable()} else throw e
        }
        val summary=ProviderOAuthProtocol.observation(response,binding)
        home(target,api,tokens,binding.homeId)
        val status=summary.text("status")
        withContext(NonCancellable) {dao.updateProviderOAuthObservation(key,summary.toString())}
        val code=withContext(Dispatchers.IO) {if(status!="pending") {codes.remove(binding);null} else codes.read(binding,System.currentTimeMillis())}
        return ProviderOAuthObservation(binding,status,code)
    }
    suspend fun cancel(target:ManagementTarget,key:String,api:ManagementClient,tokens:ManagementTokens):ManagementOperation {
        val binding=original(target,key)
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        // The DELETE route only checks profile; first verify the provider on its scoped poll route.
        poll(target,key,api,tokens)
        val receipt=record(target,ProviderOAuthAction.Cancel,binding.provider,buildJsonObject {put("start_key",key);put("session_id",binding.sessionId);put("home_id",binding.homeId)})
        try {
            dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
            val summary=ProviderOAuthProtocol.cancellation(api.cancelProviderOAuth(tokens,target.profile,binding.sessionId),binding)
            withContext(NonCancellable) {dao.finishManagementOperation(receipt.key,"acknowledged",summary.toString())}
            withContext(NonCancellable+Dispatchers.IO) {codes.remove(binding)}
            return dao.managementOperation(receipt.key)!!
        } catch(e:Exception) {failure(receipt.key,e)}
    }
}
