package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun metricWord(key:String)=when(key) {
    "input_tokens"->Words.InputTokens;"output_tokens"->Words.OutputTokens;"total_tokens"->Words.TotalTokens
    "cache_read_tokens"->Words.CacheReadTokens;"cache_write_tokens"->Words.CacheWriteTokens;"reasoning_tokens"->Words.ReasoningTokens
    "provider"->Words.Provider;"model"->Words.ModelId;"route_source"->Words.RouteSource;"model_lock"->Words.LastLockAck
    "child_session_id"->Words.ChildSession;"subagent_id"->Words.AgentIdentity;"delegation_id"->Words.DelegationIdentity;"parent_id"->Words.ParentIdentity
    "status"->Words.RunStatus;"cost_usd"->Words.ReportedCost;"duration_seconds"->Words.DurationSeconds
    "api_calls"->Words.ApiCalls
    "tool_count"->Words.ToolCount;"depth"->Words.AgentDepth;"task_index"->Words.TaskIndex;"task_count"->Words.TaskCount
    else->Words.ServerValue
}
@Composable private fun ServerField(key:String,value:JsonElement) {
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {Text(label(metricWord(key)),style=MaterialTheme.typography.labelMedium);SelectionContainer {Text((value as? JsonPrimitive)?.content ?: value.toString(),style=MaterialTheme.typography.bodySmall)}}
}
@Composable fun RuntimeUsageDialog(raw:String,dismiss:()->Unit) {
    val telemetry=remember(raw) {RunTelemetry.parse(raw)}
    AlertDialog(onDismissRequest=dismiss,title={Text(label(Words.RuntimeUsage))},text={
        LazyColumn(Modifier.testTag("runtime-usage-list").heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(label(Words.RuntimeUsageInfo),style=MaterialTheme.typography.bodySmall)}
            val runtime=telemetry["runtime"] as? JsonObject
            if(runtime!=null) {
                item {Text(label(Words.ServedRuntime),style=MaterialTheme.typography.titleSmall)}
                runtime.filterKeys {it!="requested"}.forEach {(key,value)->item {ServerField(key,value)}}
                val requested=runtime["requested"] as? JsonObject
                if(requested!=null) {item {Text(label(Words.RequestedModel),style=MaterialTheme.typography.titleSmall)};requested.forEach {(key,value)->item {ServerField(key,value)}}}
            }
            (telemetry["usage"] as? JsonObject)?.forEach {(key,value)->item {ServerField(key,value)}}
        }
    },confirmButton={TextButton(dismiss) {Text(label(Words.Close))}})
}
private val publicActivityDetails = setOf("child_session_id", "subagent_id", "delegation_id", "parent_id",
    "model", "duration_seconds", "tool_count", "api_calls", "depth", "task_index", "task_count")

@Composable internal fun activityName(event: ToolEvent): String = when {
    event.category == "subagent" -> label(Words.Subagent)
    event.loadedSkill != null -> label(Words.ActivitySkillLoaded)
    else -> event.tool.takeIf { it.isNotBlank() && it.length <= 256 && it.none(Char::isISOControl) }
        ?: label(Words.ToolActivity)
}

@Composable internal fun currentActivityName(
    conversation: Conversation, timeline: List<ActivityEntry>, progress: ReportedRunProgress?,
): String? = if (progress?.matches(conversation) == true) progress.tool
    else activeActivity(conversation, timeline)?.let { activityName(it) }

@Composable private fun ActivityEventTimes(event: ToolEvent, earlierStart: Boolean, displayTime: DateTimeFormatter) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        event.serverEventAt?.let { time ->
            val word = if (earlierStart) Words.ActivityHermesStartTime else Words.ActivityHermesEventTime
            Text(label(word).replace("{time}", displayTime.format(Instant.ofEpochMilli(time))),
                style = MaterialTheme.typography.labelSmall)
        }
        val receipt = if (earlierStart) Words.ActivityStartReceived else Words.ActivityUpdateReceived
        Text(label(receipt).replace("{time}", displayTime.format(Instant.ofEpochMilli(event.receivedAt))),
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable internal fun ActivityDialog(
    status: String, events: List<ToolEvent>, report: RunReport?, waits: List<ActivityWait>,
    liveConversation: Conversation? = null, progress: ReportedRunProgress? = null,
    savedRunId: String? = null, dismiss: () -> Unit,
) {
    val timeline = remember(events) { activityTimeline(events) }
    val currentTool = liveConversation?.takeIf { waits.isEmpty() }?.let { currentActivityName(it, timeline, progress) }
    val runtime = remember(report?.telemetryJson) {
        RunTelemetry.parse(report?.telemetryJson.orEmpty())["runtime"] as? JsonObject
    }
    val displayTime = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    }
    AlertDialog(onDismissRequest=dismiss,title={
        Text(label(if (savedRunId != null) Words.TodayRunReceipt else Words.HermesActivity))
    },text={
        LazyColumn(Modifier.testTag("activity-timeline").heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if (savedRunId != null) item {
                Text(label(Words.RunIdentity), style = MaterialTheme.typography.labelMedium)
                SelectionContainer { Text(savedRunId, Modifier.testTag("saved-receipt-identity")) }
            }
            if (status != "idle") item {
                Text(label(if (liveConversation != null) Words.RunStatus else Words.ActivityLastRunStatus), style = MaterialTheme.typography.titleSmall)
                Text(label(statusWord(status)), style = MaterialTheme.typography.bodySmall)
                currentTool?.let {
                    Text(label(Words.ActivityInProgress).replace("{name}", it),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            items(waits) { wait ->
                Column(Modifier.testTag("activity-wait"), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(label(if (wait.approval) Words.ActivityApprovalWait else Words.WaitingInput),
                        style = MaterialTheme.typography.titleSmall)
                    when (wait.state) {
                        "responding" -> Text(label(Words.ResponseSending), style = MaterialTheme.typography.bodySmall)
                        "unknown" -> Text(label(Words.DecisionUnknown), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(label(Words.ActivityUpdateReceived).replace("{time}", displayTime.format(Instant.ofEpochMilli(wait.receivedAt))),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            item {
                Text(label(Words.ServedRuntime), style = MaterialTheme.typography.titleSmall)
                val served = runtime?.filterKeys { it == "provider" || it == "model" }.orEmpty()
                if (served.isEmpty()) Text(label(Words.ActivityRuntimeUnavailable), style = MaterialTheme.typography.bodySmall)
                else served.forEach { (key, value) -> ServerField(key, value) }
                (runtime?.get("requested") as? JsonObject)?.let { requested ->
                    Text(label(Words.RequestedModel), style = MaterialTheme.typography.titleSmall)
                    requested.forEach { (key, value) -> ServerField(key, value) }
                }
            }
            if (timeline.isNotEmpty() || waits.isEmpty()) {
                item {Text(label(if (timeline.isEmpty()) Words.ActivityUnavailable else Words.RecentActivityInfo),
                    style=MaterialTheme.typography.bodySmall)}
            }
            items(timeline,key={it.key}) {entry ->
                val e=entry.latest
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(activityName(e), style=MaterialTheme.typography.titleSmall)
                    val mcpServer = e.mcpServer
                    val identity = mcpServer ?: e.loadedSkill
                    if (identity != null) {
                        if (mcpServer != null) Text(label(Words.ActivityMcpServer), style = MaterialTheme.typography.labelMedium)
                        SelectionContainer { Text(identity, style = MaterialTheme.typography.bodySmall) }
                    } else Text(label(when(e.kind) {
                        "started" -> Words.Started
                        "failed" -> Words.Failed
                        "cancelled" -> Words.Cancelled
                        "interrupted" -> Words.Interrupted
                        "incomplete" -> Words.Incomplete
                        "timeout" -> Words.ActivityTimedOut
                        "stalled" -> Words.ActivityStalled
                        "completed" -> Words.Completed
                        else -> Words.ActivityOutcomeUnknown
                    }), style=MaterialTheme.typography.labelMedium)
                    entry.startReceipt?.takeIf { it != e }?.let { start ->
                        ActivityEventTimes(start, earlierStart = true, displayTime = displayTime)
                    }
                    ActivityEventTimes(e, earlierStart = false, displayTime = displayTime)
                    if(e.callId.isNotBlank()) {Text(label(Words.ActivityIdentity));SelectionContainer {Text(e.callId,style=MaterialTheme.typography.bodySmall)}}
                    if(e.runId.isNotBlank()) {Text(label(Words.RunIdentity));Text(e.runId,style=MaterialTheme.typography.bodySmall)}
                    e.durationMs?.let {Text("${it} ms",style=MaterialTheme.typography.labelSmall)}
                    entry.details.filterKeys { it in publicActivityDetails }.forEach { (key, value) ->
                        if (value is JsonPrimitive && value !is JsonNull && value.content.length <= 1000 &&
                            value.content.none { it.isISOControl() && it !in "\n\r\t" }) ServerField(key, value)
                    }
                    HorizontalDivider()
                }
            }
        }
    },confirmButton={TextButton(dismiss) {Text(label(Words.Close))}})
}

@Composable fun AccountingHistoryDialog(reports:List<RunReport>,dismiss:()->Unit) {
    var selected by remember {mutableStateOf<RunReport?>(null)}
    selected?.let {RuntimeUsageDialog(it.telemetryJson) {selected=null};return}
    AlertDialog(onDismissRequest=dismiss,title={Text(label(Words.RuntimeUsage))},text={
        LazyColumn(Modifier.testTag("accounting-history-list").heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(label(Words.AccountingHistoryInfo),style=MaterialTheme.typography.bodySmall)}
            items(reports,key={it.key}) {report ->
                TextButton({selected=report}) {Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(if(report.runId.isNotBlank()) report.runId else report.pendingKey)
                    Text(label(statusWord(report.status)),style=MaterialTheme.typography.labelSmall)
                    Text(java.time.Instant.ofEpochMilli(report.updatedAt).toString(),style=MaterialTheme.typography.labelSmall)
                }}
            }
        }
    },confirmButton={TextButton(dismiss) {Text(label(Words.Close))}})
}
