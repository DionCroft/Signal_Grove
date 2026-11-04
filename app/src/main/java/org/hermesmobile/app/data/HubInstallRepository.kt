package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*
import java.util.UUID

class HubInstallRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    suspend fun install(target:ManagementTarget,request:HubInstallRequest,api:ManagementClient,tokens:ManagementTokens):ManagementOperation {
        ready.await();SkillProtocol.profile(target.profile)
        if(dao.unresolvedManagementOperations(target.resourceScope,target.url).isNotEmpty()) throw ManagementOutcomePending()
        require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
        api.identity(tokens)
        val sources=SkillHubProtocol.sources(api.hubSources(tokens,target.profile))
        val installed=SkillProtocol.inventory(api.skills(tokens,target.profile))
        val duplicate=sources.installed.orEmpty().any {(key,value)->key in setOf(request.skill.identifier,request.skill.canonicalId) || value.name.equals(request.name,true)} ||
            installed.any {it.name.equals(request.name,true) || it.name.equals(request.skill.name,true)}
        if(duplicate) throw SkillHubChanged()
        val preview=SkillHubProtocol.preview(api.hubPreview(tokens,target.profile,request.skill),request.skill)
        val scan=SkillHubProtocol.scan(api.hubScan(tokens,target.profile,preview.skill),preview.skill)
        if(!request.same(preview,scan)) throw SkillHubChanged()
        val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"HubInstall",request.name,request.summary(),"sending","",System.currentTimeMillis())
        try {
            dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
            val result=request.acknowledge(api.installHubSkill(tokens,target.profile,request))
            // Alias/PID are not a unique durable process receipt. Acceptance is not completion.
            dao.finishManagementOperation(receipt.key,"acknowledged",result)
            return dao.managementOperation(receipt.key)!!
        } catch(e:Exception) {
            withContext(NonCancellable) {dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")}}
            throw e
        }
    }
}
