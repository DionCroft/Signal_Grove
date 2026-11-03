package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.data.*

@Composable fun HubMaintenanceDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var review by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedHubMaintenance?>(null)}
    var query by remember(s.apiScope,s.url,s.profile) {mutableStateOf(false)}
    val inventory=s.hub.sources?.let {runCatching {HubInstalledInventory.from(it)}.getOrNull()}
    val receipts by vm.receipts.collectAsStateWithLifecycle()
    val pending=receipts.any {it.state in setOf("sending","unknown")}
    val enabled=!s.busy && !pending && s.identity!=null && vm.reviewTarget()!=null
    AlertDialog(onDismissRequest={vm.cancel();close()},title={Text(label(Words.SkillHubInstalledManagement))},text={
        LazyColumn(Modifier.heightIn(max=500.dp).testTag("hub-maintenance-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(s.url);Text(s.profile);TextButton(vm::loadSkillHub,enabled=!s.busy && s.identity!=null,modifier=Modifier.testTag("hub-maintenance-refresh")) {Text(label(Words.Refresh))}}
            if(s.busy) item {QuietProgress();TextButton(vm::cancel) {Text(label(Words.Cancel))}}
            if(pending) item {Text(label(Words.ManagementOutcomePending))}
            if(inventory==null) item {Text(label(Words.SkillHubInstalledUnavailable))}
            else {
                if(inventory.entries.isEmpty()) item {Text(label(Words.EmptyInventory))}
                item {TextButton({review=vm.prepareHubMaintenance(HubMaintenanceAction.UpdateAll)},enabled=enabled && inventory.entries.isNotEmpty(),modifier=Modifier.testTag("hub-update-all-review")) {Text(label(Words.SkillHubUpdateAll))};TextButton({query=!query}) {Text(label(if(query) Words.McpHideQuery else Words.McpShowQuery))}}
                items(inventory.entries,key={it.name}) {entry ->Column {
                    Text(entry.name.ifBlank {label(Words.Unknown)},style=MaterialTheme.typography.titleSmall);Text(entry.trust+" · "+entry.verdict)
                    entry.identifiers.forEach {Text(entry.displayIdentifier(it,query))}
                    TextButton({review=vm.prepareHubMaintenance(HubMaintenanceAction.Remove,entry.name)},enabled=enabled && entry.addressable,modifier=Modifier.testTag("hub-remove-${entry.name}")) {Text(label(Words.SkillHubRemove))}
                    if(!entry.addressable) Text(label(Words.SkillHubInstalledUnavailable))
                }}
            }
            s.notice?.let {item {Text(label(it))}};s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
        }
    },confirmButton={TextButton({vm.cancel();close()},Modifier.testTag("hub-maintenance-close")) {Text(label(Words.Close))}})
    review?.let {frozen ->
        val all=frozen.request.action==HubMaintenanceAction.UpdateAll
        var reveal by remember(frozen) {mutableStateOf(false)}
        AlertDialog(onDismissRequest={review=null},title={Text(label(if(all) Words.SkillHubUpdateAll else Words.SkillHubRemove))},text={
            LazyColumn(Modifier.heightIn(max=480.dp).testTag("hub-maintenance-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor);Text(label(if(all) Words.SkillHubUpdateAllEffect else Words.SkillHubRemoveEffect));if(all) Text(label(Words.SkillHubUpdatePolicy));Text(label(Words.SkillHubMaintenanceRace))}
                item {TextButton({reveal=!reveal}) {Text(label(if(reveal) Words.McpHideQuery else Words.McpShowQuery))}}
                items(if(all) frozen.request.inventory.entries else listOf(frozen.request.selected!!),key={it.name}) {entry ->Column {Text(entry.name);Text(entry.trust+" · "+entry.verdict);entry.identifiers.forEach {Text(entry.displayIdentifier(it,reveal))}}}
            }
        },confirmButton={TextButton({review=null;vm.maintainHubSkills(frozen)},enabled=enabled,modifier=Modifier.testTag("hub-maintenance-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({review=null},Modifier.testTag("hub-maintenance-review-cancel")) {Text(label(Words.Cancel))}})
    }
}
