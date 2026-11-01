package org.hermesmobile.app.data

import org.hermesmobile.app.BuildConfig
import org.hermesmobile.app.network.*

class HermesConnectionRepository {
    fun client(config: ServerConfig): HermesApiClient = OkHttpHermesApiClient(config,BuildConfig.DEBUG)
    suspend fun test(api: HermesApiClient) = api.capabilities()
}
class HermesSessionRepository(val dao: HermesDao) {
    suspend fun importSessions(api: HermesApiClient, scope: String) {
        api.sessions().forEachIndexed { index, session ->
            val key="$scope:${session.id}"
            if(dao.conversation(key) == null) dao.save(Conversation(key,scope,session.id,session.title, System.currentTimeMillis()-index))
        }
    }
    suspend fun importMessages(api: HermesApiClient, session: Conversation) {
        val messages=api.messages(session.remoteId)
        // Keep local pending receipts. Do not infer success from transcript text.
        if (session.pendingKey.isNotBlank() && session.status !in listOf("completed","idle")) return
        val local=dao.history(session.key)
        // Initial import only. Later local turns are maintained by their durable receipts.
        if(local.isEmpty()) dao.saveMessages(messages.mapIndexed { i,m -> ChatMessage("remote_${session.key}_$i",session.key,m.role,m.content,m.timestamp.takeIf { it > 0 } ?: i.toLong()) })
    }
}
class HermesJobsRepository {
    suspend fun list(api: HermesApiClient) = api.jobs()
    suspend fun detail(api: HermesApiClient, id: String) = api.job(id)
}
