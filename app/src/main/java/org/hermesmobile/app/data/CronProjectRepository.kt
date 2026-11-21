package org.hermesmobile.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.hermesmobile.app.network.CanonicalGatewayChannel
import org.hermesmobile.app.network.ManagementClient
import org.hermesmobile.app.network.ManagementTokens
import java.util.UUID

internal class CronProjectRepository(private val dao: HermesDao, private val ready: Deferred<Unit>) {
    suspend fun decide(review: CronProjectReview, decision: CronRunOnceDecision, channel: CanonicalGatewayChannel) {
        if (decision == CronRunOnceDecision.Defer) {
            channel.decideCronProject(review, decision)
            return
        }
        ready.await()
        val owner = review.inbox.owner
        val selected = review.entry.occurrence
        // This is only a local audit key. Every server read uses the original occurrence.
        val row = ManagementOperation(UUID.randomUUID().toString().replace("-", ""), owner.resourceScope,
            owner.apiScope, owner.url, owner.profile, owner.actor, CronProjectProtocol.Action, selected.job,
            selected.summary(), "sending", "", System.currentTimeMillis(), review.inbox.home)
        channel.requireProjectReview(review)
        try {
            dao.claimManagementOperation(row)
            currentCoroutineContext().ensureActive()
            val accepted = checkNotNull(channel.decideCronProject(review, decision))
            withContext(NonCancellable) { dao.finishManagementOperation(row.key, "unknown", accepted.summary()) }
        } catch (error: Exception) {
            val refused = error is CronProjectRefused && !error.submitted
            val unresolved = withContext(NonCancellable) {
                if (dao.managementOperation(row.key) != row) false
                else {
                    dao.finishManagementOperation(row.key, if (refused) "refused" else "unknown", "")
                    !refused
                }
            }
            if (unresolved && error !is CancellationException) throw ManagementOutcomePending()
            throw error
        }
    }

    suspend fun observe(owner: ManagementTarget, home: String, key: String, api: ManagementClient,
                        tokens: ManagementTokens): CronProjectOccurrence? {
        ready.await()
        val row = dao.managementOperation(key) ?: throw CronProjectInvalid()
        require(row.action == CronProjectProtocol.Action && row.apiScope == owner.apiScope &&
            row.resourceScope == owner.resourceScope && row.url == owner.url && row.profile == owner.profile &&
            row.actor == owner.actor && row.projectHome == home && row.state in setOf("unknown", "acknowledged"))
        val selected = CronProjectProtocol.storedOccurrence(row)
        val result = try {
            api.withScheduler(tokens, owner, home) { it.observeCronProject(selected) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw ManagementOutcomePending()
        }
        withContext(NonCancellable) {
            val summary = result.summary()
            dao.finishManagementOperation(row.key, if (result.settled) "acknowledged" else "unknown", summary)
            if (result.settled) dao.confirmManagementOperation(row.key, summary)
        }
        return selected.takeIf { result.settled }
    }
}
