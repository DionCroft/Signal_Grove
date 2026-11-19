package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.hermesmobile.app.local.*

@Composable internal fun PhoneHistoryOrganizationDialog(expected: LocalConversationSummary, busy: Boolean,
    save: (LocalHistoryPreferences) -> Unit, close: () -> Unit) {
    var title by remember(expected) {mutableStateOf(expected.organization?.preferences?.title.orEmpty())}
    var pinned by remember(expected) {mutableStateOf(expected.organization?.preferences?.pinned == true)}
    var archived by remember(expected) {mutableStateOf(expected.organization?.preferences?.archived == true)}
    val proposed = runCatching {LocalHistoryPreferences(title.trim().takeIf {it.isNotEmpty()}, pinned, archived)}.getOrNull()
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label(Words.PhoneHistoryOrganize), style = MaterialTheme.typography.titleLarge)
                    Text(label(Words.PhoneHistoryOrganizeHelp), style = MaterialTheme.typography.bodySmall)
                    Text(expected.title, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("SHA-256: ${expected.modelSha256}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(title, {if(it.toByteArray(Charsets.UTF_8).size <= 4096) title = it},
                        Modifier.fillMaxWidth().testTag("phone-history-title"), label = {Text(label(Words.PhoneHistoryTitle))},
                        supportingText = {Text(label(Words.PhoneHistoryTitleHelp))}, singleLine = true, isError = proposed == null, enabled = !busy)
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(pinned, enabled = !busy, role = Role.Checkbox,
                        onValueChange = {pinned = it}).testTag("phone-history-pin"), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(pinned, null, enabled = !busy); Text(label(Words.PhoneHistoryPinned))
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(archived, enabled = !busy, role = Role.Checkbox,
                        onValueChange = {archived = it}).testTag("phone-history-archive"), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(archived, null, enabled = !busy); Text(label(Words.PhoneHistoryArchived))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({proposed?.let(save)}, enabled = !busy && proposed != null,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("phone-history-organization-save")) {Text(label(Words.PhoneHistorySaveOrganization))}
                    TextButton(close, Modifier.weight(1f).heightIn(min = 48.dp).testTag("phone-history-organization-cancel")) {Text(label(Words.Cancel))}
                }
            }
        }
    }
}
