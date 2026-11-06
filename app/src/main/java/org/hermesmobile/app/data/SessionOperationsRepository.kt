package org.hermesmobile.app.data

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.*
import java.util.UUID

class SessionOperationsRepository(private val dao: HermesDao) {
    suspend fun perform(api: HermesApiClient,c: Conversation,action: SessionAction,target: String,body: JsonObject): JsonObject {
        SessionOrigin.requireWritable(c)
        SessionProtocol.validate(action,target,body)
        val receipt=ServerOperation(UUID.randomUUID().toString(),c.scope,c.key,action.name,target,body.toString(),"sending","",System.currentTimeMillis())
        dao.claimServerOperation(receipt)
        try {
            val ack=SessionProtocol.acknowledged(action,target,body,api.sessionAction(action,target,body))
            withContext(NonCancellable) {dao.finishServerOperation(receipt.key,"confirmed",ack.toString())}
            return ack
        } catch(e: Exception) {
            val state=if(e is HermesHttpException && SessionProtocol.definiteRefusal(action,e)) "rejected" else "unknown"
            withContext(NonCancellable) {dao.finishServerOperation(receipt.key,state,"")}
            throw e
        }
    }
}
