package org.hermesmobile.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.local.*

@Composable
internal fun PhoneHistoryBackupFlow(backup: PhoneHistoryBackup, busy: Boolean) {
    val state by backup.state.collectAsStateWithLifecycle()
    when (val current = state) {
        PhoneHistoryBackupState.Idle -> Unit
        is PhoneHistoryBackupState.Selection -> BackupSelection(current, backup)
        is PhoneHistoryBackupState.ExportReview -> BackupReview(
            Words.PhoneBackupExportReview, Words.PhoneMarkdownChoose, backup::dismiss,
            enabled = !busy, tag = "phone-backup-choose-destination", confirm = { backup.chooseDestination(current) }
        ) {
            Text(label(Words.PhoneBackupSize).replace("{count}", current.count.toString()).replace("{bytes}", current.bytes.toString()))
            Text(label(backupFormat(current.format)))
            Text(label(if (current.format == LocalHistoryBackupFormat.PROTECTED) Words.PhoneBackupKeepPassword else Words.PhoneBackupPlainWarning))
            Text(label(Words.PhoneBackupDestination))
        }
        is PhoneHistoryBackupState.Picking -> BackupPicker(current, backup)
        is PhoneHistoryBackupState.Working -> AlertDialog(
            onDismissRequest = {},
            text = {
                Text(label(if (current.stopping) Words.PhoneBackupStopping else backupStep(current.step)), Modifier.testTag("phone-backup-working"))
            },
            confirmButton = {
                if (current.step != BackupStep.RESTORING) {
                    TextButton(backup::stop, enabled = !current.stopping, modifier = Modifier.testTag("phone-backup-stop")) {
                        Text(label(Words.Stop))
                    }
                }
            }
        )
        is PhoneHistoryBackupState.Unlock -> BackupUnlock(current, backup)
        is PhoneHistoryBackupState.RestoreReview -> RestoreReview(current, backup, busy)
        is PhoneHistoryBackupState.Finished -> AlertDialog(
            onDismissRequest = backup::dismiss,
            text = {
                Text(label(backupResult(current.result)).replace("{count}", current.restoredCount.toString()),
                    Modifier.testTag("phone-backup-result"))
            },
            confirmButton = { TextButton(backup::dismiss, Modifier.testTag("phone-backup-close")) { Text(label(Words.Close)) } }
        )
    }
}

@Composable
private fun BackupPicker(picking: PhoneHistoryBackupState.Picking, backup: PhoneHistoryBackup) {
    key(picking.id) {
        val filename = picking.filename
        if (filename == null) {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { backup.picked(picking.id, it) }
            LaunchedEffect(picking.id) { backup.launchPicker(picking.id) { launcher.launch(arrayOf("*/*")) } }
        } else {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
                backup.picked(picking.id, it)
            }
            LaunchedEffect(picking.id) { backup.launchPicker(picking.id) { launcher.launch(filename) } }
        }
    }
    AlertDialog(onDismissRequest = {}, text = { Text(label(Words.PhoneMarkdownChoosing)) }, confirmButton = {})
}

@Composable
private fun BackupSelection(selection: PhoneHistoryBackupState.Selection, backup: PhoneHistoryBackup) {
    var selected by remember(selection) { mutableStateOf(emptySet<String>()) }
    var choosingProtection by remember(selection) { mutableStateOf(false) }
    var protect by remember(selection) { mutableStateOf(true) }
    var passphrase by remember(selection) { mutableStateOf("") }
    var repeat by remember(selection) { mutableStateOf("") }
    val protectionStep = choosingProtection
    val ready = selected.isNotEmpty() &&
        (!protectionStep || !protect || (passphrase.isNotEmpty() && passphrase == repeat))
    BackupReview(
        if (protectionStep) Words.PhoneBackupProtection else Words.PhoneBackupSelect,
        if (protectionStep) Words.PhoneBackupPrepare else Words.PhoneBackupContinue,
        backup::dismiss, enabled = ready,
        tag = if (protectionStep) "phone-backup-prepare" else "phone-backup-continue",
        back = if (protectionStep) ({
            passphrase = ""
            repeat = ""
            choosingProtection = false
        }) else null,
        confirm = {
            // An input queued for the previous footer cannot submit this step.
            if (protectionStep != choosingProtection || selected.isEmpty()) return@BackupReview
            if (protectionStep) {
                if (protect && (passphrase.isEmpty() || passphrase != repeat)) return@BackupReview
                val password = if (protect) passphrase.toCharArray() else null
                passphrase = ""
                repeat = ""
                backup.prepareExport(selection, selected, password)
            } else {
                choosingProtection = true
            }
        }
    ) {
        if (!protectionStep) Text(label(Words.PhoneBackupScope))
        Text(label(Words.PhoneBackupSelected).replace("{count}", selected.size.toString()),
            Modifier.testTag("phone-backup-selection-count"))
        if (!protectionStep) {
            BackupChatChoices(selection.rows, selected) { id, checked ->
                selected = if (checked) selected + id else selected - id
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(protect, role = Role.Switch, onValueChange = {
                protect = it
                passphrase = ""
                repeat = ""
            }).testTag("phone-backup-protect"), verticalAlignment = Alignment.CenterVertically) {
                Text(label(Words.PhoneBackupProtect), Modifier.weight(1f))
                Switch(protect, onCheckedChange = null)
            }
            if (protect) {
                Text(label(Words.PhoneBackupKeepPassword))
                BackupPassword(passphrase, { passphrase = it }, Words.PhoneBackupPassword, "phone-backup-password")
                BackupPassword(repeat, { repeat = it }, Words.PhoneBackupRepeat, "phone-backup-repeat",
                    repeat.isNotEmpty() && repeat != passphrase)
            } else {
                Text(label(Words.PhoneBackupPlainWarning), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun BackupChatChoices(rows: List<LocalConversationSummary>, selected: Set<String>, change: (String, Boolean) -> Unit) {
    for (row in rows) {
        key(row.id) {
            val labels = row.organization?.preferences
            val checked = row.id in selected
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox,
                onValueChange = { change(row.id, it) }).testTag("phone-backup-select-" + row.id),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked, onCheckedChange = null)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(row.title, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (labels?.pinned == true) Text(label(Words.PhoneHistoryPinned), style = MaterialTheme.typography.labelSmall)
                    if (labels?.archived == true) Text(label(Words.PhoneHistoryArchived), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun BackupUnlock(unlock: PhoneHistoryBackupState.Unlock, backup: PhoneHistoryBackup) {
    var passphrase by remember(unlock) { mutableStateOf("") }
    BackupReview(Words.PhoneBackupProtected, Words.PhoneBackupUnlock, backup::dismiss,
        enabled = passphrase.isNotEmpty(), tag = "phone-backup-unlock", confirm = {
            val value = passphrase.toCharArray()
            passphrase = ""
            backup.unlock(value)
        }) {
        Text(label(Words.PhoneBackupReadPassword))
        if (unlock.rejected) Text(label(Words.PhoneBackupRejected), color = MaterialTheme.colorScheme.error)
        BackupPassword(passphrase, { passphrase = it }, Words.PhoneBackupPassword, "phone-backup-unlock-password", unlock.rejected)
    }
}

@Composable
private fun RestoreReview(review: PhoneHistoryBackupState.RestoreReview, backup: PhoneHistoryBackup, busy: Boolean) {
    val items = remember(review) { review.plan.items }
    BackupReview(Words.PhoneBackupRestoreReview, Words.PhoneBackupRestoreConfirm, backup::dismiss,
        enabled = !busy, tag = "phone-backup-restore-confirm", confirm = { backup.restore(review) }) {
        Text(label(backupFormat(review.format)))
        Text(label(Words.PhoneBackupRestoreScope).replace("{count}", items.size.toString()))
        for (item in items) {
            HermesCard {
                Text(item.source.title, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text(label(Words.PhoneBackupIdentity).replace("{chat}", item.source.id).replace("{model}", item.source.modelSha256.take(12)),
                    style = MaterialTheme.typography.bodySmall)
                if (item.remapped) Text(label(Words.PhoneBackupNewId).replace("{id}", item.targetId), Modifier.testTag("phone-backup-remapped-" + item.source.id))
                if (item.parentOutsideArchive) Text(label(Words.PhoneBackupDetachedParent))
                if (item.source.labels?.pinned == true) Text(label(Words.PhoneHistoryPinned), style = MaterialTheme.typography.labelSmall)
                if (item.source.labels?.archived == true) Text(label(Words.PhoneHistoryArchived), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun BackupPassword(value: String, change: (String) -> Unit, title: Words, tag: String, invalid: Boolean = false) {
    OutlinedTextField(value, { if (it.length <= 256) change(it) }, Modifier.fillMaxWidth().testTag(tag),
        label = { Text(label(title)) }, singleLine = true, isError = invalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
}

@Composable
private fun BackupReview(title: Words, action: Words, cancel: () -> Unit, enabled: Boolean, tag: String,
    confirm: () -> Unit, back: (() -> Unit)? = null, body: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = cancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                key(title) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label(title), style = MaterialTheme.typography.titleLarge)
                        if (back != null) {
                            val focus = LocalFocusManager.current
                            val keyboard = LocalSoftwareKeyboardController.current
                            TextButton({
                                focus.clearFocus()
                                keyboard?.hide()
                                back()
                            }, Modifier.testTag("phone-backup-change-selection")) { Text(label(Words.PhoneBackupChangeSelection)) }
                        }
                        body()
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(confirm, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(tag)) {
                        Text(label(action))
                    }
                    TextButton(cancel, Modifier.weight(1f).heightIn(min = 48.dp).testTag("phone-backup-cancel")) {
                        Text(label(Words.Cancel))
                    }
                }
            }
        }
    }
}

private fun backupFormat(format: LocalHistoryBackupFormat) = when (format) {
    LocalHistoryBackupFormat.PLAIN -> Words.PhoneBackupPlain
    LocalHistoryBackupFormat.PROTECTED -> Words.PhoneBackupProtected
}

private fun backupStep(step: BackupStep) = when (step) {
    BackupStep.PREPARING -> Words.PhoneBackupPreparing
    BackupStep.READING -> Words.PhoneBackupReading
    BackupStep.REVIEWING -> Words.PhoneBackupInspecting
    BackupStep.WRITING -> Words.PhoneBackupWriting
    BackupStep.RESTORING -> Words.PhoneBackupRestoring
}

private fun backupResult(result: BackupResult) = when (result) {
    BackupResult.SAVED -> Words.PhoneBackupSaved
    BackupResult.RESTORED -> Words.PhoneBackupRestored
    BackupResult.RESTORED_REFRESH_NEEDED -> Words.PhoneBackupRefresh
    BackupResult.CANCELLED -> Words.Cancel
    BackupResult.STOPPED -> Words.PhoneBackupStopped
    BackupResult.WRITE_STOPPED -> Words.PhoneBackupWriteStopped
    BackupResult.WRITE_UNCERTAIN -> Words.PhoneBackupWriteUncertain
    BackupResult.RESTORE_UNCERTAIN -> Words.PhoneBackupRestoreUncertain
    BackupResult.INTERRUPTED -> Words.PhoneBackupInterrupted
    BackupResult.PICKER_UNAVAILABLE -> Words.PhoneMarkdownPickerUnavailable
    BackupResult.FILE_UNAVAILABLE -> Words.PhoneBackupFileUnavailable
    BackupResult.BUSY -> Words.PhoneBackupBusy
    BackupResult.INVALID -> Words.PhoneBackupInvalid
    BackupResult.CHANGED -> Words.PhoneBackupChanged
    BackupResult.CAPACITY -> Words.PhoneBackupCapacity
    BackupResult.INVALID_PASSPHRASE -> Words.PhoneBackupInvalidPassword
    BackupResult.CRYPTO_UNAVAILABLE -> Words.PhoneBackupCryptoUnavailable
    BackupResult.FAILED -> Words.PhoneBackupFailed
}
