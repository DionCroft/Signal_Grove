package org.hermesmobile.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun RunControls(vm:HermesViewModel) {
    val rows by vm.controls.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val events by vm.toolEvents.collectAsStateWithLifecycle()
    val sending by vm.controlBusy.collectAsStateWithLifecycle()
    var dismissed by rememberSaveable {mutableStateOf("")}
    var timeline by rememberSaveable {mutableStateOf(false)}
    val control=rows.firstOrNull {it.sessionKey==selected}
    Row(Modifier.fillMaxWidth()) {
        if(events.any {it.sessionKey==selected}) TextButton({timeline=true},Modifier.weight(1f)) {Text(label(Words.ToolActivity)+" (${events.size})")}
        if(control!=null) TextButton({dismissed=""},Modifier.weight(1f)) {Text(label(Words.ReviewRequest)+" (${rows.size})")}
    }
    if(timeline) AlertDialog(onDismissRequest={timeline=false},title={Text(label(Words.ToolActivity))},text={
        LazyColumn(Modifier.heightIn(max=450.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(events.filter {it.sessionKey==selected},key={it.key}) {e ->
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(e.tool,style=MaterialTheme.typography.titleSmall)
                    Text(label(when(e.kind) {"started"->Words.Started;"failed"->Words.Failed;else->Words.Completed}),style=MaterialTheme.typography.labelSmall)
                    Text(DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(e.receivedAt))+(e.durationMs?.let {" · ${it}ms"} ?: ""),style=MaterialTheme.typography.labelSmall)
                    if(e.preview.isNotBlank()) SelectionContainer {Text(e.preview,style=MaterialTheme.typography.bodySmall)}
                    HorizontalDivider()
                }
            }
        }
    },confirmButton={TextButton({timeline=false}) {Text(label(Words.Close))}})
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
        if(c.kind=="approval") {if("once" in c.choices()) TextButton({respond(c,"once")},enabled=enabled && !c.limited) {Text(label(Words.AllowOnce))}}
        else TextButton({respond(c,answer)},enabled=enabled && !c.limited && answer.isNotBlank() && answer.length<=16384) {Text(label(Words.SubmitAnswer))}
    },dismissButton={Row {if(c.kind=="approval" && "deny" in c.choices()) TextButton({respond(c,"deny")},enabled=enabled) {Text(label(Words.Deny))};TextButton(dismiss) {Text(label(Words.Later))}}})
}
