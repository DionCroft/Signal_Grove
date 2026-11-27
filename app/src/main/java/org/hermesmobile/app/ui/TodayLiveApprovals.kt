package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import org.hermesmobile.app.data.CanonicalApprovalInbox
import org.hermesmobile.app.network.CanonicalNotSupported
import org.hermesmobile.app.network.CanonicalProfileTransportUnavailable
import org.hermesmobile.app.network.CanonicalScopeChanged
import org.hermesmobile.app.network.HermesHttpException

private data class ApprovalCheck(
    val generation: Long,
    val request: Long,
    val attemptedAt: Long,
    val observedAt: Long,
    val inbox: CanonicalApprovalInbox?,
    val failure: Words? = null
)

@Composable
internal fun TodayLiveApprovals(
    vm: HermesViewModel, scope: String, generation: Long, connected: Boolean, busy: Boolean,
    now: Instant, formatted: DateTimeFormatter, management: ManagementViewModel
) {
    LaunchedEffect(management, scope) { management.bind(scope) }
    val state by management.state.collectAsStateWithLifecycle()
    val managementGeneration by management.reviewGeneration.collectAsStateWithLifecycle()
    val reason = when {
        busy -> Words.TodayRefreshBusy
        !connected -> Words.TodayRefreshOffline
        else -> management.todayApprovalBlockReason(scope)
    }
    var lookup by remember(scope, managementGeneration) { mutableStateOf<ApprovalCheck?>(null) }
    var request by remember(scope, managementGeneration) { mutableLongStateOf(0L) }
    var loading by remember(scope, managementGeneration, generation) { mutableStateOf(false) }
    var showList by remember(scope, managementGeneration) { mutableStateOf(false) }
    var selection by remember(scope, managementGeneration, generation) { mutableStateOf<CanonicalViewerSelection?>(null) }

    LaunchedEffect(scope, managementGeneration, generation, reason, request) {
        if (reason != null) return@LaunchedEffect
        val previous = lookup
        val time = System.currentTimeMillis()
        if (previous != null && previous.generation == generation && previous.request == request &&
            time - previous.attemptedAt in 0..120_000L) return@LaunchedEffect
        loading = true
        try {
            val inbox = management.discoverTodayApprovals(scope)
            lookup = ApprovalCheck(generation, request, time, System.currentTimeMillis(), inbox)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val word = when (error) {
                is CanonicalNotSupported -> Words.ViewerUnsupported
                is CanonicalProfileTransportUnavailable -> Words.ViewerProfileTransportUnavailable
                is CanonicalScopeChanged -> Words.ManagementReviewChanged
                is HermesHttpException -> if (error.code == 401) Words.ManagementExpired else Words.TodayApprovalReadFailed
                else -> Words.TodayApprovalReadFailed
            }
            lookup = ApprovalCheck(generation, request, time, previous?.observedAt ?: 0L, previous?.inbox, word)
        } finally {
            loading = false
        }
    }

    val cached = lookup
    val inbox = cached?.inbox
    val currentTime = remember(now, cached) { System.currentTimeMillis() }
    val fresh = reason == null && !loading && cached != null && cached.failure == null &&
        cached.generation == generation && cached.request == request &&
        currentTime - cached.observedAt in 0..120_000L

    LaunchedEffect(reason) { if (reason != null) selection = null }

    Word(Words.TodayLiveApprovals)
    if (state.apiScope == scope && state.profile.isNotBlank()) Text(state.profile, style = MaterialTheme.typography.labelMedium)
    TextButton({ request++ }, Modifier.testTag("today-live-approval-refresh"), enabled = reason == null && !loading) {
        Text(label(if (loading) Words.TodayChecking else Words.TodayApprovalRead))
    }
    (reason ?: cached?.failure)?.let {
        Text(label(it), Modifier.testTag("today-live-approval-state"), style = MaterialTheme.typography.bodySmall)
    }
    if (reason == Words.ManagementVerify && state.signedIn && state.apiScope == scope && state.profile.isNotBlank()) {
        TextButton({ management.refreshSharedChatAccess(scope) },
            Modifier.testTag("today-live-approval-verify")) { Text(label(Words.Refresh)) }
    }
    if (inbox != null) {
        Text(label(Words.TodayChecked).replace("{time}", formatted.format(Instant.ofEpochMilli(cached.observedAt))),
            style = MaterialTheme.typography.bodySmall)
        if (!fresh) Text(label(Words.TodayApprovalCached), style = MaterialTheme.typography.bodySmall)
        Text(label(Words.TodayApprovalChecked).replace("{checked}", inbox.checkedSessions.toString())
            .replace("{listed}", inbox.listedSessions.toString()), style = MaterialTheme.typography.bodySmall)
        if (inbox.incomplete) Text(label(Words.TodayApprovalPartial), style = MaterialTheme.typography.bodySmall)
        if (inbox.notices.isNotEmpty()) {
            TextButton({ showList = true }, Modifier.testTag("today-live-approval-list")) {
                Text(label(Words.TodayApprovalCount).replace("{count}", inbox.notices.size.toString()))
            }
        } else if (fresh && !inbox.incomplete) {
            Text(label(if (inbox.listedSessions == 0) Words.TodayApprovalNoLive else Words.TodayApprovalEmpty))
        }
    }

    if (showList && inbox != null) AlertDialog(
        onDismissRequest = { showList = false },
        title = { Text(label(Words.TodayLiveApprovals)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!fresh) item { Text(label(Words.TodayApprovalCached)) }
                items(inbox.notices) { notice ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(notice.session.title.ifBlank { notice.session.sessionId }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(notice.session.sessionId, style = MaterialTheme.typography.labelSmall)
                        Text(notice.request.id, style = MaterialTheme.typography.labelSmall)
                        TextButton({
                            if (vm.profileScope.value == scope && vm.todayConnectionGeneration.value == generation &&
                                vm.connectionStatus.value == "connected") {
                                selection = management.reviewTodayApproval(scope, notice)
                                if (selection != null) showList = false
                            }
                        }, Modifier.heightIn(min = 48.dp), enabled = fresh && notice.request.reviewable) {
                            Text(label(Words.SharedApprovalReview))
                        }
                        if (!notice.request.reviewable) Text(label(Words.TodayApprovalUnreviewable))
                    }
                }
            }
        },
        confirmButton = { TextButton({ showList = false }) { Text(label(Words.Close)) } }
    )

    selection?.takeIf { reason == null }?.let { selected ->
        CanonicalSessionsDialog(management.canonicalViewer, available = true, pending = false, selection = selected,
            continuing = true) {
            selection = null
            request++
        }
    }
}
