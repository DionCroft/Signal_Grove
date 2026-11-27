package org.hermesmobile.app.data

import org.hermesmobile.app.network.CanonicalRuntimeBinding
import org.hermesmobile.app.network.SharedApproval
import org.hermesmobile.app.network.SharedApprovalNotice

internal class CanonicalApprovalInbox(
    val binding: CanonicalRuntimeBinding,
    val notices: List<CanonicalApprovalNotice>,
    val checkedSessions: Int,
    val listedSessions: Int,
    val incomplete: Boolean
) {
    override fun toString() = "CanonicalApprovalInbox(redacted)"
}

internal class CanonicalApprovalNotice(
    val binding: CanonicalRuntimeBinding,
    val session: CanonicalStoredSession,
    val request: SharedApprovalNotice
) {
    fun matches(inventory: CanonicalSessionInventory): Boolean {
        val current = inventory.binding
        return request.reviewable && inventory.scope == CanonicalSessionScope.Live &&
            current.target == binding.target && current.homeId == binding.homeId && current.profileId == binding.profileId &&
            current.instanceId == binding.instanceId && current.authorityEpoch == binding.authorityEpoch &&
            inventory.rows.singleOrNull { it.sessionId == session.sessionId }?.fingerprint == session.fingerprint
    }

    fun matches(snapshot: CanonicalSessionSnapshot, approval: SharedApproval): Boolean {
        val owner = snapshot.owner
        return owner.target == binding.target && owner.homeId == binding.homeId && owner.profileId == binding.profileId &&
            owner.instanceId == binding.instanceId && owner.authorityEpoch == binding.authorityEpoch &&
            snapshot.requestedSessionId == session.sessionId && owner.sessionId == session.sessionId &&
            snapshot.executionGeneration == approval.generation && request.matches(approval)
    }

    override fun toString() = "CanonicalApprovalNotice(redacted)"
}
