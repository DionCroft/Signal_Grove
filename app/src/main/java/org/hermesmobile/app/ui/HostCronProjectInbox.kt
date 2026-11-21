package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.*

@Composable
internal fun HostCronProjectInboxDialog(
    vm: ManagementViewModel,
    state: ManagementState,
    receipts: List<ManagementOperation>,
    pending: Boolean,
    close: () -> Unit
) {
    val home = vm.hostCronLedgerHome(state)
    val inbox = state.cron.projectInbox?.takeIf { it.owner == vm.reviewTarget() && it.home == home }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(label(Words.HostCronProjectInbox)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp).testTag("host-cron-project-list"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(state.profile)
                    TextButton(vm::loadCronProjectInbox, enabled = !state.busy && home != null,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("host-cron-project-refresh")) {
                        Text(label(Words.Refresh))
                    }
                    if (home == null) Text(label(Words.HostCronLedgerProfile))
                }
                if (pending) item { Text(label(Words.ManagementOutcomePending)) }
                state.error?.let { word -> item { Text(label(word), color = MaterialTheme.colorScheme.error) } }
                state.notice?.let { word -> item { Text(label(word)) } }
                if (state.busy) item { QuietProgress() }
                items(receipts, key = { it.key }) { ManagementReceiptCard(it, vm, state) }
                if (inbox != null && inbox.entries.isEmpty()) item { Text(label(Words.HostCronProjectEmpty)) }
                if (inbox != null) items(inbox.entries, key = { "occurrence-" + it.occurrence.id }) { entry ->
                    Column {
                        Text(entry.project, style = MaterialTheme.typography.titleMedium)
                        Text(entry.occurrence.job)
                        Text(label(Words.HostCronProjectDue) + "\n" + entry.occurrence.scheduledAt)
                        TextButton({ vm.prepareCronProject(inbox, entry) }, enabled = !state.busy && !pending,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("project-review-" + entry.occurrence.id)) {
                            Text(label(Words.HostCronProjectReview))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(close, Modifier.heightIn(min = 48.dp).testTag("host-cron-project-close")) { Text(label(Words.Close)) }
        }
    )
    state.cron.projectReview?.takeIf { it.inbox === inbox }?.let { review ->
        val selected = review.entry.occurrence
        SchedulerApprovalDialog(review.inbox.owner, selected.job, review.scope, review.expiresAt,
            Words.HostCronProjectReview, Words.HostCronProjectEffect, "host-cron-project",
            state.busy, pending, denyBlocked = pending, close = vm::closeSchedulerReview,
            expire = { vm.expireCronProject(review) },
            decide = { vm.decideCronProject(review, it) },
            detail = { Text(label(Words.HostCronProjectDue) + "\n" + selected.scheduledAt) })
    }
}

@Composable
internal fun CronProjectReceiptCard(receipt: ManagementOperation, vm: ManagementViewModel?, state: ManagementState?) {
    val selected = runCatching { CronProjectProtocol.storedOccurrence(receipt) }.getOrNull()
    val result = CronProjectProtocol.savedSummary(receipt)
    Column(Modifier.fillMaxWidth().testTag("management-receipt-" + receipt.key),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label(Words.HostCronProjectReview), style = MaterialTheme.typography.titleSmall)
        selected?.let { Text(it.job + "\n" + it.scheduledAt) }
        Text(label(when {
            result?.denied == true -> Words.HostCronProjectDenied
            result != null -> result.status.word()
            receipt.state == "refused" -> Words.HostCronProjectRefused
            else -> managementStateWord(receipt.state)
        }))
        result?.let { Text(it.execution, style = MaterialTheme.typography.bodySmall) }
        if (vm != null && state != null && receipt.state in setOf("unknown", "acknowledged")) {
            TextButton({ vm.observeCronProjectReceipt(receipt.key) },
                enabled = selected != null && !state.busy && vm.reviewTarget() != null,
                modifier = Modifier.heightIn(min = 48.dp).testTag("management-observe-" + receipt.key)) {
                Text(label(Words.HostCronProjectObserve))
            }
            if (selected == null) Text(label(Words.HostCronProjectInvalid))
        }
    }
}
