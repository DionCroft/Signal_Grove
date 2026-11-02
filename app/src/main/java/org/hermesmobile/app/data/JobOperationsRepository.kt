package org.hermesmobile.app.data

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.*
import java.util.UUID

class JobOperationsRepository(private val dao: HermesDao) {
    suspend fun perform(api: HermesApiClient,scope: String,operation: String,id: String,body: JsonObject) {
        JobProtocol.validate(operation,id,body)
        require(dao.unresolvedOperations(scope).isEmpty()) {"Review the uncertain previous operation before making another change."}
        val receipt=JobOperation(UUID.randomUUID().toString(),scope,operation,id,"sending",System.currentTimeMillis())
        dao.save(receipt)
        try {
            api.mutateJob(operation,id,body)
            withContext(NonCancellable) {dao.jobOperationState(receipt.key,"confirmed")}
        } catch(e: Exception) {
            // Only contract-verified pre-mutation refusals are definite; never auto-repeat a mutation.
            val state=if(e is HermesHttpException && e.documentedJobRefusal) "rejected" else "unknown"
            withContext(NonCancellable) {dao.jobOperationState(receipt.key,state)}
            throw e
        }
    }
}
