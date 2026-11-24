package org.hermesmobile.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable internal fun RunControls(
    vm: HermesViewModel,
    leading: @Composable RowScope.() -> Unit,
    trailing: @Composable RowScope.() -> Unit,
) {
    val rows by vm.controls.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val events by vm.toolEvents.collectAsStateWithLifecycle()
    val reports by vm.runReports.collectAsStateWithLifecycle()
    val current by vm.selectedConversation.collectAsStateWithLifecycle()
    val scope by vm.profileScope.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val sending by vm.controlBusy.collectAsStateWithLifecycle()
    val feedback by vm.activity.collectAsStateWithLifecycle()
    val conversation = current?.takeIf { it.key == selected && it.scope == scope }
    val ownEvents = remember(conversation?.key, events) {
        events.filter { it.sessionKey == conversation?.key }
    }
    var dismissed by rememberSaveable {mutableStateOf("")}
    var timeline by remember(conversation?.key, conversation?.remoteId, scope) {mutableStateOf(false)}
    val control = rows.firstOrNull { it.sessionKey == selected }
    val waits = if (conversation == null) emptyList() else activityWaits(conversation, rows)
    val live = conversation != null && busy && !SessionOrigin.readOnly(conversation)
    val currentTool = if (live && conversation.status in setOf("running", "queued")) {
        val entries = remember(ownEvents) { activityTimeline(ownEvents) }
        activeActivity(conversation, entries)
    } else null
    val latestWait = waits.maxByOrNull { it.receivedAt }
    val statusText = when {
        latestWait?.state == "unknown" -> label(Words.DecisionUnknown)
        latestWait?.state == "responding" -> label(Words.ResponseSending)
        latestWait != null -> label(if (latestWait.approval) Words.ActivityApprovalWait else Words.WaitingInput)
        currentTool != null -> label(Words.ActivityInProgress).replace("{name}", activityName(currentTool))
        busy -> feedback
        else -> ""
    }

    Column(Modifier.fillMaxWidth()) {
        if (statusText.isNotBlank()) {
            Text(
                statusText.replace("\n", " · "),
                Modifier.testTag("chat-current-activity").semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Row(Modifier.weight(1f)) {
                if (conversation != null) {
                    IconButton({ timeline = true }) {
                        BadgedBox(badge = { if (ownEvents.isNotEmpty()) Badge { Text(ownEvents.size.toString()) } }) {
                            Icon(Icons.Outlined.Build, label(Words.ToolActivity))
                        }
                    }
                }
                if (control != null) {
                    IconButton({ dismissed = "" }) {
                        BadgedBox(badge = { Badge { Text(rows.size.toString()) } }) {
                            Icon(Icons.Outlined.HelpOutline, label(Words.ReviewRequest) + " (${rows.size})")
                        }
                    }
                }
            }
            trailing()
        }
    }
    if (timeline && conversation != null) {
        ActivityDialog(conversation, ownEvents, activityReport(conversation, reports), waits, live = live) {
            timeline = false
        }
    }
    if(control!=null && dismissed!=control.key) PendingControlDialog(control,sending,{dismissed=control.key},vm::respondControl,vm::retryOrRefresh)
}
@Composable private fun PendingControlDialog(c:PendingControl,sending:Boolean,dismiss:()->Unit,respond:(PendingControl,String)->Unit,refresh:()->Unit) {
    var answer by remember(c.key,c.generation) {mutableStateOf("")}
    val identity=c.runId.isNotBlank() && c.requestId.isNotBlank() && (c.kind=="approval" || c.generation!=null)
    val enabled=identity && c.state=="pending" && !sending
    AlertDialog(onDismissRequest=dismiss,title={Text(label(if(c.kind=="approval") Words.Approval else Words.WaitingInput))},text={
        Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            SelectionContainer {Text(c.prompt)}
            if(c.limited) Text(label(Words.LimitedConsent),color=MaterialTheme.colorScheme.error)
            if(!identity) Text(label(Words.MissingIdentity),color=MaterialTheme.colorScheme.error)
            if(c.state=="responding" || sending) {Text(label(Words.ResponseSending));QuietProgress()}
            if(c.state=="unknown") {Text(label(Words.DecisionUnknown));TextButton(refresh,enabled=!sending) {Text(label(Words.CheckRun))}}
            if(c.kind=="clarify") {
                c.choices().forEach {choice -> TextButton({answer=choice},enabled=enabled) {Text(choice)}}
                OutlinedTextField(answer,{answer=it},Modifier.fillMaxWidth(),label={Text(label(Words.Answer))},enabled=enabled,maxLines=5)
            }
        }
    },confirmButton={
        if(c.kind=="approval") {if("once" in c.choices()) TextButton({respond(c,"once")},enabled=enabled && !c.limited && c.prompt.isNotBlank()) {Text(label(Words.AllowOnce))}}
        else TextButton({respond(c,answer)},enabled=enabled && !c.limited && answer.isNotBlank() && answer.length<=16384) {Text(label(Words.SubmitAnswer))}
    },dismissButton={Row {if(c.kind=="approval" && "deny" in c.choices()) TextButton({respond(c,"deny")},enabled=enabled) {Text(label(Words.Deny))};TextButton(dismiss) {Text(label(Words.Later))}}})
}
