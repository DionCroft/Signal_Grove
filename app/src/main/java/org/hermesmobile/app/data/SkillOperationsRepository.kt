package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*
import java.util.UUID

class SkillReviewChanged:Exception()
class SkillReadbackPending:Exception()
data class SkillOutcome(val rows:List<ManagedSkill>,val receipt:ManagementOperation)

class SkillOperationsRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    suspend fun execute(target:ManagementTarget,request:SkillRequest,api:ManagementClient,tokens:ManagementTokens):SkillOutcome {
        ready.await();SkillProtocol.profile(target.profile)
        if(dao.unresolvedManagementOperations(target.resourceScope,target.url).isNotEmpty()) throw ManagementOutcomePending()
        require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
        api.identity(tokens)
        val before=SkillProtocol.inventory(api.skills(tokens,target.profile))
        if(before.singleOrNull {it.name==request.expected.name}!=request.expected) throw SkillReviewChanged()
        val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,request.receiptAction,request.expected.name,request.summary(),"sending","",System.currentTimeMillis())
        try {
            dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
            val result=request.acknowledge(api.skillControl(tokens,target.profile,request))
            dao.finishManagementOperation(receipt.key,"acknowledged",result)
            val after=SkillProtocol.inventory(api.skills(tokens,target.profile))
            if(!request.observed(after)) throw SkillReadbackPending()
            dao.confirmManagementOperation(receipt.key,result)
            return SkillOutcome(after,dao.managementOperation(receipt.key)!!)
        } catch(e:Exception) {
            withContext(NonCancellable) {dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")}}
            throw e
        }
    }
}
