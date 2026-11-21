package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.CronExecutionHistory
import org.hermesmobile.app.data.CronExecutionStatus
import org.hermesmobile.app.data.ManagedCronRuns
import java.text.DateFormat
import java.util.Date

private fun cronRunTime(seconds:Double)=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date((seconds*1000).toLong()))

private fun CronExecutionStatus.word(): Words = when (this) {
    CronExecutionStatus.Claimed -> Words.HostCronAttemptClaimed
    CronExecutionStatus.Running -> Words.HostCronAttemptRunning
    CronExecutionStatus.Completed -> Words.Completed
    CronExecutionStatus.Failed -> Words.Failed
    CronExecutionStatus.Unknown -> Words.HostCronAttemptUnknown
    CronExecutionStatus.Cancelled -> Words.Cancelled
    CronExecutionStatus.Missed -> Words.HostCronAttemptMissed
}

internal fun LazyListScope.hostCronExecutionHistory(history: CronExecutionHistory?) {
    if (history == null) return
    item {
        when {
            !history.ledgerAvailable -> Text(label(Words.HostCronLedgerAbsent))
            history.attempts.isEmpty() -> Text(label(Words.HostCronLedgerEmpty))
            history.limited -> Text(label(Words.HostCronLedgerLimited))
        }
    }
    itemsIndexed(history.attempts, key = { _, attempt -> "cron-attempt-" + attempt.id }) { index, attempt ->
        var reveal by remember(history, attempt.id) { mutableStateOf(false) }
        OutlinedCard(Modifier.fillMaxWidth().testTag("host-cron-attempt-$index")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label(attempt.status.word()), style = MaterialTheme.typography.titleSmall)
                Text(label(Words.HostCronAttemptClaimedAt) + "\n" + attempt.claimedAt)
                attempt.scheduledAt?.let { Text(label(Words.HostCronAttemptScheduledAt) + "\n" + it) }
                attempt.startedAt?.let { Text(label(Words.HostCronAttemptStartedAt) + "\n" + it) }
                attempt.finishedAt?.let { Text(label(Words.HostCronAttemptFinishedAt) + "\n" + it) }
                attempt.deliveryOutcome?.let { Text(label(Words.HostCronDelivery) + "\n" + it) }
                if (attempt.reportedError) Text(label(Words.HostCronRunError))
                TextButton({ reveal = !reveal }, Modifier.fillMaxWidth().testTag("host-cron-attempt-id-$index")) {
                    Text(label(if (reveal) Words.HostCronHideReceipt else Words.HostCronShowReceipt), Modifier.fillMaxWidth())
                }
                if (reveal) SelectionContainer { Text(attempt.id) }
            }
        }
    }
}

/** Private display-only history; no run row becomes a writable chat or worker handle. */
fun LazyListScope.hostCronRuns(history:ManagedCronRuns?,busy:Boolean,error:Words?,load:()->Unit) {
    item {
        TextButton(load,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("host-cron-runs-load"),shape=MaterialTheme.shapes.small) {Text(label(Words.HostCronRecentRuns),Modifier.fillMaxWidth())}
        if(busy) QuietProgress()
        error?.let {Text(label(it),color=MaterialTheme.colorScheme.error)}
    }
    history?.let {snapshot ->
        item {Text(label(Words.HostCronRunHistoryNote));if(snapshot.rows.isEmpty()) Text(label(Words.HostCronNoRuns))}
        itemsIndexed(snapshot.rows,key={index,_->"cron-run-$index"}) {index,row ->
            var reveal by remember(snapshot,index) {mutableStateOf(false)}
            OutlinedCard(Modifier.fillMaxWidth().testTag("host-cron-run-$index")) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(label(when {row.scriptOutput->Words.HostCronScriptOutput;row.conversation->Words.HostCronConversationRun;else->Words.HostCronRunRecord}),style=MaterialTheme.typography.titleSmall)
                    if(row.hasPrivateText) TextButton({reveal=!reveal},Modifier.fillMaxWidth().testTag("host-cron-run-reveal-$index"),shape=MaterialTheme.shapes.small) {Text(label(if(reveal) Words.HostCronRunHide else Words.HostCronRunReveal),Modifier.fillMaxWidth())}
                    if(reveal) SelectionContainer {Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        row.title?.let {Text(it)}
                        row.preview?.let {Text(it)}
                        row.model?.let {Text(label(Words.HostCronRunModel)+"\n"+it)}
                        row.id?.let {Text(it)}
                    }}
                    row.startedAt?.let {Text(label(Words.HostCronRunRecordedAt)+"\n"+cronRunTime(it))}
                    Text(label(when(row.active) {true->Words.HostCronRunActive;false->Words.HostCronRunInactive;null->Words.HostCronRunActivityUnknown}))
                    row.lastActive?.let {Text(label(Words.HostCronRunLastActive)+"\n"+cronRunTime(it))}
                    row.messageCount?.let {Text(label(Words.HostCronRunMessages)+": "+it)}
                    row.toolCallCount?.let {Text(label(Words.HostCronRunTools)+": "+it)}
                }
            }
        }
    }
}
