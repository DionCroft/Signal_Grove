package org.hermesmobile.app.data

import kotlinx.serialization.json.JsonObject
import org.hermesmobile.app.network.*

class InteractionRepository(private val dao: HermesDao) {
    suspend fun synchronize(c: Conversation,snapshot: JsonObject,resetUnknown: Boolean = false) {
        dao.replaceControls(c.key,c.runId,ControlProtocol.snapshot(snapshot,c.runId).map {it.stored(c.key)},resetUnknown)
    }
    suspend fun respond(api: HermesApiClient,c: Conversation,expected: PendingControl,response: String) {
        val key=expected.key
        val control=dao.control(key) ?: throw IllegalStateException("This prompt has already settled.")
        require(control.sessionKey==c.key && control.runId==c.runId && control.state=="pending" && control==expected) {"Refresh the current run before responding."}
        ControlProtocol.responseBody(control,response)
        val current=ControlProtocol.snapshot(api.run(c.runId),c.runId).firstOrNull {ControlProtocol.matches(control,it)}
            ?: throw IllegalArgumentException("The prompt changed or expired. Refresh the run and review it again.")
        val fresh=current.stored(c.key)
        ControlProtocol.responseBody(fresh,response)
        dao.controlState(key,"responding")
        try {
            api.respondControl(fresh,response)
            dao.controlState(key,"resolved")
        } catch(e: Exception) {kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {dao.controlState(key,"unknown")};throw e}
    }
}
