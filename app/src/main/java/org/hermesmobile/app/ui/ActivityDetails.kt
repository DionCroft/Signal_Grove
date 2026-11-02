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

private fun metricWord(key:String)=when(key) {
    "input_tokens"->Words.InputTokens;"output_tokens"->Words.OutputTokens;"total_tokens"->Words.TotalTokens
    "cache_read_tokens"->Words.CacheReadTokens;"cache_write_tokens"->Words.CacheWriteTokens;"reasoning_tokens"->Words.ReasoningTokens
    "provider"->Words.Provider;"model"->Words.ModelId;"route_source"->Words.RouteSource;"model_lock"->Words.LastLockAck
    "child_session_id"->Words.ChildSession;"subagent_id"->Words.AgentIdentity;"delegation_id"->Words.DelegationIdentity;"parent_id"->Words.ParentIdentity
    "goal"->Words.AgentGoal;"summary"->Words.AgentSummary;"output_tail"->Words.AgentOutput
    "status"->Words.RunStatus;"cost_usd"->Words.ReportedCost;"duration_seconds"->Words.DurationSeconds
    "files_read"->Words.FilesRead;"files_written"->Words.FilesWritten;"api_calls"->Words.ApiCalls
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
@Composable fun ActivityDialog(events:List<ToolEvent>,dismiss:()->Unit) {
    val timeline=remember(events) {activityTimeline(events)}
    AlertDialog(onDismissRequest=dismiss,title={Text(label(Words.ToolActivity))},text={
        LazyColumn(Modifier.testTag("activity-timeline").heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(label(Words.RecentActivityInfo),style=MaterialTheme.typography.bodySmall)}
            items(timeline,key={it.key}) {entry ->
                val e=entry.latest
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(if(e.category=="subagent") label(Words.Subagent) else e.tool,style=MaterialTheme.typography.titleSmall)
                    Text(label(when(e.kind) {"started"->Words.Started;"failed"->Words.Failed;"cancelled"->Words.Cancelled;else->Words.Completed}),style=MaterialTheme.typography.labelMedium)
                    if(e.callId.isNotBlank()) {Text(label(Words.ActivityIdentity));SelectionContainer {Text(e.callId,style=MaterialTheme.typography.bodySmall)}}
                    if(e.runId.isNotBlank()) {Text(label(Words.RunIdentity));Text(e.runId,style=MaterialTheme.typography.bodySmall)}
                    e.durationMs?.let {Text("${it} ms",style=MaterialTheme.typography.labelSmall)}
                    entry.previews.forEach {SelectionContainer {Text(it,style=MaterialTheme.typography.bodySmall)}}
                    entry.details.filterKeys {it!="error"}.forEach {(key,value)->ServerField(key,value)}
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
