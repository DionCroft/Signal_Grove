package org.hermesmobile.app.data

import java.util.UUID
import kotlinx.coroutines.*
import org.hermesmobile.app.network.ManagementClient
import org.hermesmobile.app.network.ManagementTokens

internal class CronTimingRepository(private val dao: HermesDao, private val ready: Deferred<Unit>) {
    suspend fun apply(review: CronTimingReview, api: ManagementClient, tokens: ManagementTokens) {
        ready.await()
        val base = review.baseline
        val owner = base.owner
        if (dao.unresolvedManagementOperations(owner.resourceScope, owner.url, base.homeId).isNotEmpty()) throw ManagementOutcomePending()
        val proof = CronTimingReceiptProof(base.version, review.digest, review.after.fingerprint())
        val record = ManagementOperation(UUID.randomUUID().toString().replace("-", ""), owner.resourceScope,
            owner.apiScope, owner.url, owner.profile, owner.actor, CronTimingProtocol.Action, base.jobId,
            proof.wire(), "sending", "", System.currentTimeMillis(), base.homeId)
        try {
            api.withScheduler(tokens, owner, base.homeId) { channel ->
                val fresh = channel.cronTimingReview(base, review.draft)
                if (!review.sameReview(fresh)) throw CronTimingRefused("CHANGED", false)
                dao.claimManagementOperation(record)
                currentCoroutineContext().ensureActive()
                channel.applyCronTiming(review, record.key, proof)
            }
        } catch (error: Exception) {
            val unresolved = withContext(NonCancellable) {
                if (dao.managementOperation(record.key) == null) false
                else {
                    val definite = error is CronTimingRefused && !error.submitted
                    dao.finishManagementOperation(record.key, if (definite) "refused" else "unknown", "")
                    !definite
                }
            }
            if (unresolved && error !is CancellationException) throw ManagementOutcomePending()
            throw error
        }
        // Keep the profile's write barrier until the original receipt is verified.
        withContext(NonCancellable) { dao.finishManagementOperation(record.key, "unknown", "") }
        observe(owner, base.homeId, record.key, api, tokens)
    }

    suspend fun observe(owner: ManagementTarget, home: String, key: String, api: ManagementClient, tokens: ManagementTokens) {
        ready.await()
        val record = dao.managementOperation(key) ?: throw CronTimingInvalid()
        require(record.action == CronTimingProtocol.Action && record.apiScope == owner.apiScope &&
            record.resourceScope == owner.resourceScope && record.url == owner.url &&
            record.profile == owner.profile && record.actor == owner.actor && record.projectHome == home &&
            record.state in setOf("unknown","acknowledged","confirmed"))
        val proof = CronTimingProtocol.proof(record.requestSummary)
        try {
            api.withScheduler(tokens, owner, home) { it.observeCronTiming(record.target, record.key, proof) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw ManagementOutcomePending()
        }
        withContext(NonCancellable) {
            dao.finishManagementOperation(record.key, "acknowledged", "")
            dao.confirmManagementOperation(record.key, "")
        }
    }
}
