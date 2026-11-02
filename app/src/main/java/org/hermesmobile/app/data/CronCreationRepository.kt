package org.hermesmobile.app.data

import kotlinx.coroutines.*
import org.hermesmobile.app.network.*
import java.util.UUID

data class CronCreationOutcome(val receipt:ManagementOperation,val job:ManagedCronJob)

class CronCreationRepository(private val dao:HermesDao,private val ready:Deferred<Unit>) {
    suspend fun create(target:ManagementTarget,request:CronCreation,contract:CronCreateContract,api:ManagementClient,tokens:ManagementTokens):CronCreationOutcome {
        ready.await()
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
        api.identity(tokens)
        val fresh=CronCreateContract.parse(api.cronSchema(tokens));if(fresh.signature!=contract.signature || !fresh.accepts(request)) throw CronCreationChanged()
        val jobs=ManagedCronJob.inventory(api.cronJobs(tokens,target.profile),target.profile)
        if(jobs.any {it.name==request.name} || request.context.any {id->id!="self" && jobs.none {it.id==id && it.addressable}}) throw CronCreationChanged()
        if(request.delivery.id!="local" && CronDeliveryTarget.inventory(api.cronDeliveries(tokens,target.profile)).none {it==request.delivery && it.configured}) throw CronCreationChanged()
        val receipt=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"CronCreate",request.name,request.summary(),"sending","",System.currentTimeMillis())
        try {
            dao.claimManagementOperation(receipt);currentCoroutineContext().ensureActive()
            val acknowledgement=api.createCron(tokens,target.profile,request)
            val result=request.acknowledge(acknowledgement,target.profile)
            dao.finishManagementOperation(receipt.key,"acknowledged",result)
            val id=ManagedCronJob.parse(acknowledgement,target.profile).id
            val after=api.cronJob(tokens,target.profile,id)
            val verified=runCatching {request.acknowledge(after,target.profile);request.matches(acknowledgement) && request.matches(after) && request.sameConfiguration(acknowledgement,after)}.getOrDefault(false)
            if(!verified) throw CronCreationReadbackPending()
            dao.confirmManagementOperation(receipt.key,result)
            return CronCreationOutcome(dao.managementOperation(receipt.key)!!,ManagedCronJob.parse(after,target.profile))
        } catch(e:Exception) {
            withContext(NonCancellable) {dao.managementOperation(receipt.key)?.let {dao.finishManagementOperation(it.key,"unknown","")}}
            throw e
        }
    }
}
