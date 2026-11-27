package org.hermesmobile.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import org.hermesmobile.app.LocalAiActivity
import org.hermesmobile.app.data.LocalTask
import org.hermesmobile.app.data.TodayApproval
import org.hermesmobile.app.data.TodayRun
import org.hermesmobile.app.data.TodayRunDetails
import org.hermesmobile.app.data.TodayRunIdentity
import org.hermesmobile.app.data.duePhoneTasks

@Composable
internal fun TodayOverview(
    vm: HermesViewModel, management: ManagementViewModel, tasks: List<LocalTask>, go: (String) -> Unit
) {
    val context = LocalContext.current
    val date by todayClock()
    val due = remember(tasks, date.first.atZone(date.second).toLocalDate()) {
        duePhoneTasks(tasks, date.first, date.second)
    }
    val unprocessed by vm.todayUnprocessed.collectAsStateWithLifecycle()
    val notebook by vm.todayNotebook.collectAsStateWithLifecycle()
    val scope by vm.profileScope.collectAsStateWithLifecycle()
    val connectionGeneration by vm.todayConnectionGeneration.collectAsStateWithLifecycle()
    var openedRun by remember(scope, connectionGeneration) { mutableStateOf<TodayRun?>(null) }
    var openedApproval by remember(scope, connectionGeneration) { mutableStateOf<TodayApproval?>(null) }
    val connection by vm.connectionStatus.collectAsStateWithLifecycle()
    val profiles by vm.savedConnections.collectAsStateWithLifecycle()
    val approvals by vm.todayApprovals.collectAsStateWithLifecycle()
    val runs by vm.todayRuns.collectAsStateWithLifecycle()
    val snapshot by vm.todayRemoteSnapshot.collectAsStateWithLifecycle()
    val refreshing by vm.todayRefreshing.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val controlBusy by vm.controlBusy.collectAsStateWithLifecycle()
    val capabilities by vm.capabilities.collectAsStateWithLifecycle()
    val connected = connection == "connected"
    val connecting by vm.connecting.collectAsStateWithLifecycle()
    val refreshReason = when {
        connecting -> Words.TodayChecking
        !connected -> Words.TodayRefreshOffline
        capabilities?.runs != true -> Words.TodayRefreshUnsupported
        busy || controlBusy -> Words.TodayRefreshBusy
        else -> null
    }
    LaunchedEffect(scope, connectionGeneration, refreshReason) {
        if (refreshReason == null) vm.refreshTodayIfStale()
    }
    val current = snapshot?.takeIf { it.scope == scope && it.connectionGeneration == connectionGeneration }
    val rowTime = remember(date.first, current) { Instant.now() }
    val formatted = remember(date.second) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(date.second) }

    HermesCard(Modifier.testTag("today-overview")) {
        Word(Words.Today)
        TextButton({ go("Tasks") }, Modifier.testTag("today-due-tasks")) {
            Text(label(Words.TodayDueTasks).replace("{count}", due.size.toString()))
        }
        due.take(4).forEach { task ->
            Text(task.title + " · " + task.dueDate, style = MaterialTheme.typography.bodySmall)
        }
        TextButton({ context.startActivity(LocalAiActivity.captureIntent(context)) }, Modifier.testTag("home-capture-open")) {
            Text(label(Words.TodayCaptures).replace("{count}", unprocessed.toString()))
        }
        notebook?.let { project ->
            Text(label(Words.TodayLastNotebook), style = MaterialTheme.typography.labelMedium)
            TextButton({ context.startActivity(LocalAiActivity.notebookIntent(context, project.id)) },
                Modifier.testTag("today-notebook")) { Text(project.name) }
        }
        if (scope == "demo") return@HermesCard
        Word(Words.TodayHermes)
        profiles.firstOrNull { it.id == scope }?.let { profile ->
            Text(profile.name, style = MaterialTheme.typography.titleSmall)
        }
        Text(label(if (connected) Words.TodayOnline else Words.TodayCached), style = MaterialTheme.typography.labelMedium)
        if (current != null) {
            Text(label(Words.TodayChecked).replace("{time}", formatted.format(Instant.ofEpochMilli(current.endedAt))),
                style = MaterialTheme.typography.bodySmall)
            if (current.failed > 0) Text(label(Words.TodaySomeUnavailable), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(vm::refreshToday, Modifier.testTag("today-refresh"), enabled = refreshReason == null && !refreshing) {
            Text(label(if (refreshing || connecting) Words.TodayChecking else Words.Refresh))
        }
        refreshReason?.takeIf { it != Words.TodayChecking }?.let { reason ->
            Text(label(reason), Modifier.testTag("today-refresh-reason"), style = MaterialTheme.typography.bodySmall)
        }
        val activeApprovals = approvals.filter { it.scope == scope }
        val activeRuns = runs.filter { it.scope == scope }
        if (activeApprovals.isNotEmpty()) {
            Word(Words.TodayApprovals)
            activeApprovals.forEach { approval ->
                TodayRunRow(approval.sessionTitle, approval.runId, approval.lastConfirmedAt, formatted,
                    current?.confirmed?.get(TodayRunIdentity(approval.sessionKey, approval.runId)),
                    connected, rowTime, !connecting,
                    button = Modifier.testTag("today-approval-${approval.requestId}"), requestId = approval.requestId) {
                    openedApproval = approval
                }
            }
        }
        TodayLiveApprovals(vm, scope, connectionGeneration, connected, connecting || busy || controlBusy,
            date.first, formatted, management)
        if (activeRuns.isNotEmpty()) {
            Word(Words.TodayCompleted)
            activeRuns.forEach { run ->
                TodayRunRow(run.sessionTitle, run.runId, run.lastConfirmedAt, formatted,
                    current?.confirmed?.get(TodayRunIdentity(run.sessionKey, run.runId)),
                    connected, rowTime, !connecting,
                    button = Modifier.testTag("today-receipt-${run.runId}")) {
                    openedRun = run
                }
            }
        }
        if (activeApprovals.isEmpty() && activeRuns.isEmpty()) Text(label(Words.TodayNoReceipts))
    }
    openedRun?.let { row -> TodaySavedRunDialog(vm, row) { openedRun = null } }
    openedApproval?.let { row ->
        val details = remember(row) { vm.todayApprovalDetails(row) }
        val control by details.collectAsStateWithLifecycle(initialValue = null)
        val cachedReason = when {
            refreshReason != null -> refreshReason
            !vm.todayApprovalIsCurrent(row, connectionGeneration, rowTime.toEpochMilli()) -> Words.TodayApprovalCached
            else -> null
        }
        control?.let { selected ->
            PendingControlDialog(selected, controlBusy, { openedApproval = null },
                { shown, choice -> vm.respondTodayApproval(row, connectionGeneration, shown, choice) },
                vm::refreshToday, cachedReason, fromToday = true)
        } ?: AlertDialog(
            onDismissRequest = { openedApproval = null },
            title = { Text(label(Words.SharedApprovalReview)) },
            text = { Text(label(if (approvals.any { it.controlKey == row.controlKey })
                Words.Loading else Words.TodayApprovalUnreviewable)) },
            confirmButton = { TextButton({ openedApproval = null }) { Text(label(Words.Close)) } }
        )
    }
}

@Composable
private fun TodaySavedRunDialog(vm: HermesViewModel, row: TodayRun, dismiss: () -> Unit) {
    var receipt by remember(row) { mutableStateOf<TodayRunDetails?>(null) }
    var loading by remember(row) { mutableStateOf(true) }
    LaunchedEffect(row) {
        receipt = vm.todayRunDetails(row)
        loading = false
    }
    val saved = receipt
    if (saved != null) {
        ActivityDialog("completed", saved.events, saved.report, emptyList(), savedRunId = row.runId, dismiss = dismiss)
    } else {
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(label(Words.TodayRunReceipt)) },
            text = { Text(label(if (loading) Words.TodayRunLoading else Words.TodayRunUnavailable)) },
            confirmButton = { TextButton(dismiss) { Text(label(Words.Close)) } }
        )
    }
}

@Composable
private fun TodayRunRow(sessionTitle: String, runId: String, stored: Long, formatted: DateTimeFormatter, checked: Long?,
                        connected: Boolean, now: Instant, enabled: Boolean, button: Modifier = Modifier,
                        requestId: String? = null, open: () -> Unit) {
    val fresh = connected && checked != null && now.toEpochMilli() - checked in 0..120_000L
    val confirmedAt = maxOf(stored, checked ?: 0)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextButton(open, modifier = button, enabled = enabled) { Text(sessionTitle.ifBlank { runId }) }
        Text(runId, style = MaterialTheme.typography.labelSmall)
        requestId?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        if (!fresh) Text(label(Words.TodayCached), style = MaterialTheme.typography.labelSmall)
        val confirmation = if (confirmedAt > 0) {
            label(Words.TodayConfirmed).replace("{time}", formatted.format(Instant.ofEpochMilli(confirmedAt)))
        } else label(Words.TodayConfirmationUnknown)
        Text(confirmation, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun todayClock(): State<Pair<Instant, ZoneId>> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now = remember { mutableStateOf(Instant.now() to ZoneId.systemDefault()) }
    DisposableEffect(context, lifecycle) {
        val refresh = { now.value = Instant.now() to ZoneId.systemDefault() }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { refresh() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val resume = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(resume)
        onDispose {
            lifecycle.removeObserver(resume)
            context.unregisterReceiver(receiver)
        }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now.value = Instant.now() to ZoneId.systemDefault()
                delay(30_000)
            }
        }
    }
    return now
}
