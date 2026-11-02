package org.hermesmobile.app.data

import org.hermesmobile.app.BuildConfig
import org.hermesmobile.app.network.*

class HermesConnectionRepository {
    fun client(config: ServerConfig): HermesApiClient = OkHttpHermesApiClient(config,BuildConfig.DEBUG)
    suspend fun test(api: HermesApiClient) = api.capabilities()
}
class HermesSessionRepository(val dao: HermesDao) {
    suspend fun importSessions(api: HermesApiClient, scope: String, offset: Int = 0): SessionPage {
        val page = api.sessionPage(offset)
        page.rows.forEachIndexed { index, remote ->
            dao.mergeRemoteSession(scope,remote,System.currentTimeMillis()-index)
        }
        return page
    }
    suspend fun importMessages(api: HermesApiClient, session: Conversation) {
        if(session.transport=="compat") return
        if(session.status !in (RunStates.terminal+"idle")) return
        val history=api.history(session.remoteId)
        val rows=history.rows.mapIndexed { index,m ->
            ChatMessage("remote_${session.key}_${m.id.ifBlank { index.toString() }}",session.key,m.role,m.content,
                m.timestamp.takeIf { it>0 } ?: index.toLong())
        }
        dao.replaceHistory(session,history.sessionId,rows)
    }
}
class HermesJobsRepository(private val dao: HermesDao) {
    suspend fun refresh(api: HermesApiClient,scope: String) {
        val now=System.currentTimeMillis()
        val rows=api.jobs().map {j -> CachedJob("$scope:${j.id}",scope,j.id,j.name,j.prompt,j.schedule,j.state,j.enabled,j.nextRun,j.lastRun,now) }
        dao.replaceJobs(scope,rows)
    }
    suspend fun detail(api: HermesApiClient, scope: String, id: String, online: Boolean): HermesJob {
        if(online) return api.job(id)
        return dao.cachedJob(scope,id)?.remote() ?: throw IllegalStateException("No saved job details are available.")
    }
}
