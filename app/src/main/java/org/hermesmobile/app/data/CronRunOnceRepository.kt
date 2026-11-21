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

internal class CronRunOnceRepository(private val dao: HermesDao, private val ready: Deferred<Unit>) {
    suspend fun approve(review: CronRunOnceReview, channel: CanonicalGatewayChannel) {
        ready.await()
        val owner = review.owner
        val row = ManagementOperation(review.request, owner.resourceScope, owner.apiScope, owner.url, owner.profile,
            owner.actor, CronRunOnceProtocol.Action, review.job, "", "sending", "", System.currentTimeMillis(), review.home)
        channel.requireRunOnceReview(review)
        try {
            dao.claimManagementOperation(row)
            currentCoroutineContext().ensureActive()
            val claimed = channel.confirmCronRunOnce(review)
            withContext(NonCancellable) { dao.finishManagementOperation(row.key, "unknown", claimed.summary()) }
        } catch (error: Exception) {
            val refused = error is CronRunOnceRefused && !error.submitted
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

    suspend fun observe(owner: ManagementTarget, home: String, key: String, api: ManagementClient, tokens: ManagementTokens) {
        ready.await()
        val row = dao.managementOperation(key) ?: throw CronRunOnceInvalid()
        require(row.action == CronRunOnceProtocol.Action && row.apiScope == owner.apiScope &&
            row.resourceScope == owner.resourceScope && row.url == owner.url && row.profile == owner.profile &&
            row.actor == owner.actor && row.projectHome == home && row.state in setOf("unknown", "acknowledged"))
        val result = try {
            api.withScheduler(tokens, owner, home) { it.observeCronRunOnce(row.target, row.key) }
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
    }
}
