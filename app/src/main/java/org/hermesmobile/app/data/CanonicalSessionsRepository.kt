package org.hermesmobile.app.data

import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import org.hermesmobile.app.network.*

internal class CanonicalAdoptionAcknowledged(val receipt: ManagementOperation, cause: Exception) :
    Exception("Session adoption was acknowledged, but the live view is unavailable.", cause) {
    override fun toString() = "CanonicalAdoptionAcknowledged(redacted)"
}

internal class CanonicalSessionConnection(
    private val channel: CanonicalGatewayChannel,
    override val attachment: CanonicalGatewayChannel.Attachment,
    override val receipt: ManagementOperation,
    private val verifyScope:suspend ()->Unit
) : CanonicalViewerLink {
    override fun observations(): Flow<CanonicalFeedDelivery> = channel.observations()
    override suspend fun heartbeat() {channel.heartbeat();verifyScope()}
    override fun close() = channel.close()
    override fun toString() = "CanonicalSessionConnection(redacted)"
}

/** Explicit reviewed viewer adoption. Receipts never contain transcript/prompt content or credentials. */
internal class CanonicalSessionsRepository(private val dao: HermesDao, private val ready: Deferred<Unit>) : CanonicalViewerRepository {
    private fun scope(target: ManagementTarget, homeId: String, api: ManagementClient, tokens: ManagementTokens) {
        GatewayCronInventory.requireProfile(target.profile)
        require(target.url == api.base.toString() && target.actor == tokens.provider + " · " + tokens.userId)
        require(homeId.matches(Regex("[a-f0-9]{64}")))
    }
    private suspend fun home(target: ManagementTarget, homeId: String, api: ManagementClient, tokens: ManagementTokens) {
        if (api.profiles(tokens).singleOrNull { it.name == target.profile }?.pluginHome?.identity() != homeId) throw CanonicalScopeChanged()
        currentCoroutineContext().ensureActive()
    }
    override suspend fun prepare(target: ManagementTarget, homeId: String, api: ManagementClient, tokens: ManagementTokens, scope: CanonicalSessionScope): CanonicalSessionInventory {
        ready.await(); scope(target,homeId,api,tokens)
        val inventory = api.canonicalGateway(tokens,target,homeId).use { it.sessionInventory(scope) }
        home(target,homeId,api,tokens)
        return inventory
    }
    override suspend fun adopt(review: CanonicalResumeReview, api: ManagementClient, tokens: ManagementTokens, previous: CanonicalReplayCheckpoint?, requireHeartbeat:Boolean): CanonicalSessionConnection {
        ready.await()
        val target = review.expected.binding.target
        val homeId = review.expected.binding.homeId
        scope(target,homeId,api,tokens)
        if (dao.unresolvedManagementOperations(target.resourceScope,target.url,homeId).isNotEmpty()) throw ManagementOutcomePending()
        val record = ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,
            CanonicalResumeAction.Id,review.row.sessionId,review.summary(),"sending","",System.currentTimeMillis(),homeId)
        var channel: CanonicalGatewayChannel? = null
        try {
            channel = api.canonicalGateway(tokens,target,homeId)
            if(requireHeartbeat) channel.heartbeat()
            val attachment = channel.resumeReviewed(review, previous,
                beforeSend = {
                    dao.claimManagementOperation(record)
                    currentCoroutineContext().ensureActive()
                },
                acknowledged = { snapshot ->
                    // This records the valid resume ACK before the separate replay read.
                    withContext(NonCancellable) { dao.finishManagementOperation(record.key,"acknowledged",snapshot.receiptSummary()) }
                })
            home(target,homeId,api,tokens)
            withContext(NonCancellable) { dao.confirmManagementOperation(record.key,attachment.snapshot.receiptSummary()) }
            return CanonicalSessionConnection(channel,attachment,checkNotNull(dao.managementOperation(record.key))) {api.identity(tokens);home(target,homeId,api,tokens)}
        } catch (error: Exception) {
            channel?.close()
            val receipt = withContext(NonCancellable) {
                // A cancelled claim may have committed. Only sending/unknown states can change here;
                // an acknowledged adoption is never rewritten as an unknown mutation by replay failure.
                dao.managementOperation(record.key)?.let { dao.finishManagementOperation(it.key,"unknown","") }
                dao.managementOperation(record.key)
            }
            if (receipt?.state in setOf("acknowledged","confirmed") && error !is CancellationException)
                throw CanonicalAdoptionAcknowledged(checkNotNull(receipt),error)
            throw error
        }
    }
    override suspend fun changeWorkspace(review:CanonicalWorkspaceReview,api:ManagementClient,tokens:ManagementTokens):CanonicalWorkspaceAcknowledgement {
        ready.await()
        val target=review.expected.owner.target;val homeId=review.expected.owner.homeId
        scope(target,homeId,api,tokens)
        if(dao.unresolvedManagementOperations(target.resourceScope,target.url,homeId).isNotEmpty()) throw ManagementOutcomePending()
        api.identity(tokens);home(target,homeId,api,tokens)
        val record=ManagementOperation(review.requestId,target.resourceScope,target.apiScope,target.url,target.profile,target.actor,
            CanonicalWorkspaceAction.Id,review.expected.owner.sessionId,review.summary(),"sending","",System.currentTimeMillis(),homeId)
        try {
            val result=api.canonicalGateway(tokens,target,homeId).use {channel->
                channel.changeWorkspaceReviewed(review,beforeSend={
                    // Revalidate scope before the durable claim. Every later failure preserves uncertainty.
                    api.identity(tokens);home(target,homeId,api,tokens)
                    dao.claimManagementOperation(record);currentCoroutineContext().ensureActive()
                },acknowledged={ack->withContext(NonCancellable) {dao.finishManagementOperation(record.key,"acknowledged",ack.summary())}})
            }
            api.identity(tokens);home(target,homeId,api,tokens)
            // Identity/home readback does not independently verify the directory.
            // Keep the durable result acknowledged until a separately reviewed fresh snapshot.
            return result
        } catch(error:Exception) {
            val saved=withContext(NonCancellable) {
                dao.managementOperation(record.key)?.let {dao.finishManagementOperation(record.key,"unknown","")}
                dao.managementOperation(record.key)
            }
            if(saved?.state in setOf("acknowledged","confirmed") && error !is CancellationException)
                throw CanonicalWorkspaceAcknowledged(checkNotNull(saved),error)
            throw error
        }
    }
    override suspend fun lastApplied(target:ManagementTarget,homeId:String,requested:String):CanonicalReplayCheckpoint? {
        ready.await()
        val rows=dao.canonicalViewerReceipts(target.apiScope,target.url,target.profile,target.actor,homeId,requested)
        for(row in rows) CanonicalViewerCheckpoint.read(row,target,homeId,requested)?.let {return it}
        if(rows.size==50) throw CanonicalReviewChanged()
        return null
    }
    override suspend fun recordApplied(key:String,owner:CanonicalFeedOwner,cursor:CanonicalReplayCursor) {
        ready.await()
        val row=dao.managementOperation(key) ?: throw CanonicalReviewChanged()
        val result=CanonicalViewerCheckpoint.updated(row,owner,cursor)
        if(dao.updateCanonicalViewerCheckpoint(key,row.resultSummary,result)!=1) throw CanonicalReviewChanged()
    }
    /** Local acknowledgement after an explicit backend inspection; never retries a server operation. */
    suspend fun reviewUnknown(target: ManagementTarget, homeId: String, key: String, api: ManagementClient, tokens: ManagementTokens) {
        ready.await(); scope(target,homeId,api,tokens); api.identity(tokens); home(target,homeId,api,tokens)
        val row = dao.managementOperation(key) ?: throw CanonicalReviewChanged()
        require(CanonicalWorkspaceAction.receipt(row.action) && row.url == target.url && row.projectHome == homeId && row.state == "unknown")
        dao.reviewManagementOperation(key,target.resourceScope,target.url,homeId)
    }
}
