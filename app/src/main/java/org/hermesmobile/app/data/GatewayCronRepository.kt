package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*
import java.util.UUID

class GatewayCronReviewChanged(val inventory:GatewayCronInventory):Exception()
class GatewayCronReadbackPending:Exception()
class GatewayCronOutcomeUncertain:Exception()
data class GatewayCronOutcome(val inventory:GatewayCronInventory,val receipt:ManagementOperation?,val observedOnly:Boolean=false)

class GatewayCronRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    suspend fun execute(target:ManagementTarget,request:GatewayCronRequest,api:ManagementClient,tokens:ManagementTokens):GatewayCronOutcome {
        ready.await();GatewayCronInventory.requireProfile(target.profile)
        if(dao.unresolvedManagementOperations(target.resourceScope,target.url).isNotEmpty()) throw ManagementOutcomePending()
        require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
        api.identity(tokens)
        return api.gateway(tokens).use {connection ->
            val before=GatewayCronInventory.parse(connection.cronList(target.profile),target.profile)
            val current=before.jobs.singleOrNull {it.id==request.expected.id}
            if(current==null && request.action==GatewayCronAction.Delete) return@use GatewayCronOutcome(before,null,true)
            if(current==null || !current.sameReview(request.expected) || GatewayCronRequest.collision(current,before.jobs)) throw GatewayCronReviewChanged(before)
            val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,request.receiptAction,request.expected.id,request.summary(),"sending","",System.currentTimeMillis())
            try {
                dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
                val acknowledgement=connection.cronControl(target.profile,request)
                val result=try {request.acknowledge(acknowledgement)} catch(e:Exception) {throw GatewayCronOutcomeUncertain()}
                dao.finishManagementOperation(receipt.key,"acknowledged",result)
                val after=GatewayCronInventory.parse(connection.cronList(target.profile),target.profile)
                if(!request.observed(after)) throw GatewayCronReadbackPending()
                dao.confirmManagementOperation(receipt.key,result)
                GatewayCronOutcome(after,dao.managementOperation(receipt.key))
            } catch(e:Exception) {
                withContext(NonCancellable) {dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")}}
                throw e
            }
        }
    }
}
