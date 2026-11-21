package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.CronTimingDraft
import org.hermesmobile.app.data.CronTimingEffect

@Composable
internal fun HostCronTimingDialog(vm: ManagementViewModel, editor: HostCronTimingEditor, busy: Boolean, pending: Boolean, error: Words?) {
    val base = editor.baseline
    val current = base.effect
    val review = editor.review
    var schedule by remember(base) { mutableStateOf(current.display) }
    var zone by remember(base) { mutableStateOf(current.timezone.orEmpty()) }
    var catchUp by remember(base) { mutableStateOf(current.catchUp) }
    var grace by remember(base) { mutableStateOf(current.grace?.toString().orEmpty()) }
    val focus = LocalFocusManager.current
    val draft = CronTimingDraft.create(current, schedule, zone, catchUp, grace)
    val title = if (review == null) Words.HostCronTimingEdit else Words.HostCronTimingReview
    AlertDialog(onDismissRequest = vm::closeHostCronTiming, title = { Text(label(title)) }, text = {
        LazyColumn(Modifier.heightIn(max = 480.dp).testTag("host-cron-timing"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(editor.job.name, style = MaterialTheme.typography.titleSmall)
                Text(base.owner.profile + " · " + base.jobId)
            }
            error?.let { item { Text(label(it), color = MaterialTheme.colorScheme.error) } }
            if (pending) item { Text(label(Words.ManagementOutcomePending), color = MaterialTheme.colorScheme.error) }
            if (busy) item { QuietProgress() }
            if (review == null) {
                item {
                    OutlinedTextField(schedule, { if (it.length <= 512) schedule = it }, Modifier.fillMaxWidth().testTag("cron-timing-schedule"),
                        label = { Text(label(Words.Schedule)) }, singleLine = true, enabled = !busy)
                    if (draft == null) Text(label(Words.HostCronTimingDraftHelp), style = MaterialTheme.typography.bodySmall)
                }
                item {
                    OutlinedTextField(zone, { if (it.length <= 128) zone = it }, Modifier.fillMaxWidth().testTag("cron-timing-zone"),
                        label = { Text(label(Words.HostCronTimezone)) }, singleLine = true, enabled = !busy)
                    Text(label(Words.HostCronTimezoneHelp), style = MaterialTheme.typography.bodySmall)
                }
                item {
                    Column(Modifier.selectableGroup()) {
                        Text(label(Words.HostCronCatchUp), style = MaterialTheme.typography.titleSmall)
                        for ((value, word) in listOf(null to Words.HostCronTimingInherit, true to Words.Enabled, false to Words.Disabled)) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(catchUp == value,
                                enabled = !busy, role = Role.RadioButton, onClick = { catchUp = value }),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(catchUp == value, onClick = null, enabled = !busy)
                                Text(label(word), Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(grace, { if (it.length <= 4) grace = it }, Modifier.fillMaxWidth().testTag("cron-timing-grace"),
                        label = { Text(label(Words.HostCronGrace)) }, singleLine = true, enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Text(label(Words.HostCronGraceHelp), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                item { CronTimingSummary(Words.HostCronTimingBefore, current, review.profileTimezone) }
                item { CronTimingSummary(Words.HostCronTimingAfter, review.after, review.profileTimezone) }
                item { Text(label(Words.HostCronTimingStaysPaused)) }
            }
        }
    }, confirmButton = {
        if (review == null) TextButton({
            draft?.let { focus.clearFocus(); vm.reviewHostCronTiming(editor, it) }
        }, enabled = draft != null && !busy && !pending, modifier = Modifier.heightIn(min = 48.dp).testTag("cron-timing-review")) {
            Text(label(Words.HostCronTimingReview))
        } else TextButton({ vm.applyHostCronTiming(editor) }, enabled = !busy && !pending,
            modifier = Modifier.heightIn(min = 48.dp).testTag("cron-timing-apply")) {
            Text(label(Words.HostCronTimingApply))
        }
    }, dismissButton = {
        TextButton({
            if (review == null) vm.closeHostCronTiming() else vm.reviseHostCronTiming(editor)
        }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("cron-timing-back")) {
            Text(label(if (review == null) Words.Cancel else Words.HostCronTimingBack))
        }
    })
}

@Composable
private fun CronTimingSummary(title: Words, effect: CronTimingEffect, profileZone: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label(title), style = MaterialTheme.typography.titleSmall)
        Text(label(Words.Schedule) + " · " + effect.display)
        val zone = effect.timezone ?: listOfNotNull(label(Words.HostCronTimingInherit), profileZone).joinToString(" · ")
        Text(label(Words.HostCronTimezone) + " · " + zone)
        Text(label(when (effect.catchUp) {
            true -> Words.HostCronPreviewCatchUpOnce
            false -> Words.HostCronPreviewCatchUpSkip
            null -> Words.HostCronPreviewCatchUpInherited
        }))
        Text(effect.grace?.let { label(Words.HostCronGrace) + " · " + it } ?: label(Words.HostCronPreviewGraceInherited))
        val repeat = effect.repeat
        val limit = if (repeat == null) label(Words.Unknown) else repeat.times?.toString() ?: label(Words.HostCronUnlimited)
        Text(label(Words.HostCronRepeat) + " · " + limit)
        if (repeat != null) Text(label(Words.HostCronRepeatCompleted) + " · " + repeat.completed)
    }
}
