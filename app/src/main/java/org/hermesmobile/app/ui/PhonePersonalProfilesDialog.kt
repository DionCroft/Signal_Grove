package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.local.*
import java.util.UUID

private sealed interface ProfilePage {
    data object Library : ProfilePage
    data class Edit(val profile: PhonePersonalProfile?) : ProfilePage
    data class Choose(val profile: PhonePersonalProfile) : ProfilePage
    data class Remove(val profile: PhonePersonalProfile) : ProfilePage
}

@Composable
internal fun PhonePersonalProfilesDialog(vm: LocalAiViewModel, state: LocalAiState, draft: String) {
    val library by vm.personalProfiles.collectAsStateWithLifecycle()
    val review by vm.profileReview.collectAsStateWithLifecycle()
    val saving by vm.profileSaving.collectAsStateWithLifecycle()
    val error by vm.profileStorageError.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf<ProfilePage>(ProfilePage.Library) }
    val back = {
        when {
            review != null -> vm.discardProfileReview()
            page != ProfilePage.Library -> page = ProfilePage.Library
            else -> vm.closePersonalProfiles()
        }
    }

    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val editingWithKeyboard = page is ProfilePage.Edit && WindowInsets.ime.getBottom(LocalDensity.current) > 0
                if (!editingWithKeyboard) Text(label(Words.PhonePersonalProfiles), style = MaterialTheme.typography.titleLarge)
                if (error) Text(label(Words.PhoneProfileStorageError), color = MaterialTheme.colorScheme.error)
                val request = review
                if (request != null) {
                    ProfileSendReview(vm, request, state, draft, request.profile in library?.entries.orEmpty())
                } else when (val current = page) {
                    ProfilePage.Library -> {
                        val entries = library?.entries.orEmpty()
                        val canChoose = state.loaded != null && !state.busy && validProfileText(draft.trim(), 8192) &&
                            state.chat?.let { it.modelSha256 != state.loaded.sha256 ||
                                it.exchanges.size >= LocalConversationStore.MAX_TURNS } != true
                        ProfileBody {
                            Text(label(Words.PhoneProfileIntro))
                            if (!canChoose) Text(label(Words.PhoneProfileDraftNeeded))
                            if (library == null && !error) Text(label(Words.Loading))
                            if (library != null && entries.isEmpty()) Text(label(Words.PhoneProfileEmpty))
                            if (entries.size >= PHONE_PROFILE_MAX) Text(label(Words.PhoneProfileLimit))
                            if (entries.isNotEmpty()) {
                                TextButton(vm::backUpPersonalProfiles, enabled = !saving,
                                    modifier = Modifier.testTag("phone-profile-backup-export")) {
                                    Text(label(Words.PhoneProfileBackup))
                                }
                            }
                            if (library != null) {
                                TextButton(vm::restorePersonalProfiles, enabled = !saving,
                                    modifier = Modifier.testTag("phone-profile-backup-import")) {
                                    Text(label(Words.PhoneProfileBackupRestore))
                                }
                            }
                            entries.forEach { profile ->
                                HermesCard {
                                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                                    if (profile.style.isNotBlank() || profile.notes.isNotEmpty()) {
                                        TextButton({ page = ProfilePage.Choose(profile) }, enabled = canChoose && !saving,
                                            modifier = Modifier.testTag("phone-profile-choose-${profile.id}")) {
                                            Text(label(Words.PhoneProfileChoose))
                                        }
                                    }
                                    Row {
                                        TextButton({ page = ProfilePage.Edit(profile) }, enabled = !saving,
                                            modifier = Modifier.testTag("phone-profile-edit-${profile.id}")) { Text(label(Words.Edit)) }
                                        TextButton({ page = ProfilePage.Remove(profile) }, enabled = !saving,
                                            modifier = Modifier.testTag("phone-profile-delete-${profile.id}")) { Text(label(Words.Delete)) }
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ page = ProfilePage.Edit(null) }, modifier = Modifier.weight(1f).testTag("phone-profile-add"),
                                enabled = library != null && !saving && entries.size < PHONE_PROFILE_MAX) {
                                Text(label(Words.PhoneProfileAdd))
                            }
                            TextButton(vm::closePersonalProfiles, Modifier.weight(1f).testTag("phone-profile-close")) {
                                Text(label(Words.Close))
                            }
                        }
                    }
                    is ProfilePage.Edit -> key(current) {
                        ProfileEdit(current.profile, saving, vm) { page = ProfilePage.Library }
                    }
                    is ProfilePage.Choose -> key(current) {
                        ProfileChoose(current.profile, vm, current.profile in library?.entries.orEmpty(), back)
                    }
                    is ProfilePage.Remove -> {
                        ProfileBody {
                            Text(label(Words.PhoneProfileRemove))
                            Text(current.profile.name)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ vm.removePersonalProfile(current.profile) { page = ProfilePage.Library } },
                                enabled = !saving && current.profile in library?.entries.orEmpty(),
                                modifier = Modifier.weight(1f).testTag("phone-profile-delete-confirm")) { Text(label(Words.Delete)) }
                            TextButton(back, Modifier.weight(1f)) { Text(label(Words.Cancel)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ProfileBody(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun ColumnScope.ProfileEdit(previous: PhonePersonalProfile?, saving: Boolean, vm: LocalAiViewModel, done: () -> Unit) {
    var name by remember(previous) { mutableStateOf(previous?.name.orEmpty()) }
    var style by remember(previous) { mutableStateOf(previous?.style.orEmpty()) }
    var notes by remember(previous) { mutableStateOf(previous?.notes.orEmpty().map { it.id to it.text }) }
    val valid = validTitle(name.trim()) &&
        (style.isBlank() || validProfileText(style, PHONE_PROFILE_STYLE_BYTES)) &&
        notes.all { (_, text) -> validProfileText(text, PHONE_PROFILE_NOTE_BYTES) }
    ProfileBody {
        OutlinedTextField(name, { if (it.length <= 64) name = it },
            Modifier.fillMaxWidth().testTag("phone-profile-name"), singleLine = true,
            label = { Text(label(Words.PhoneProfileName)) }, enabled = !saving)
        OutlinedTextField(style, { if (it.length <= PHONE_PROFILE_STYLE_BYTES) style = it },
            Modifier.fillMaxWidth().testTag("phone-profile-style"), maxLines = 4,
            label = { Text(label(Words.PhoneProfileStyle)) }, enabled = !saving)
        notes.forEachIndexed { index, (id, text) -> key(id) {
            OutlinedTextField(text, { value ->
                if (value.length <= PHONE_PROFILE_NOTE_BYTES) {
                    notes = notes.map { if (it.first == id) id to value else it }
                }
            }, Modifier.fillMaxWidth().testTag("phone-profile-note-$index"), maxLines = 5,
                label = { Text(label(Words.PhoneProfileNote)) }, enabled = !saving)
            TextButton({ notes = notes.filterNot { it.first == id } }, enabled = !saving) {
                Text(label(Words.PhoneProfileRemoveNote))
            }
        } }
        if (notes.size < PHONE_PROFILE_NOTES_MAX) {
            TextButton({ notes = notes + (UUID.randomUUID().toString() to "") }, enabled = !saving,
                modifier = Modifier.testTag("phone-profile-add-note")) { Text(label(Words.PhoneProfileAddNote)) }
        }
        if (!valid) Text(label(Words.PhoneProfileLimits), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label(Words.PhoneProfilePrivate), style = MaterialTheme.typography.bodySmall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({
            val updated = PhonePersonalProfile(previous?.id ?: UUID.randomUUID().toString(), name.trim(),
                style.takeUnless(String::isBlank).orEmpty(), notes.map { (id, text) -> PhonePersonalNote(id, text) })
            vm.savePersonalProfile(previous, updated, done)
        }, Modifier.weight(1f).testTag("phone-profile-save"), enabled = valid && !saving) {
            Text(label(Words.Save))
        }
        TextButton(done, Modifier.weight(1f), enabled = !saving) { Text(label(Words.Cancel)) }
    }
}

@Composable
private fun ColumnScope.ProfileChoose(profile: PhonePersonalProfile, vm: LocalAiViewModel, current: Boolean, back: () -> Unit) {
    var style by remember(profile) { mutableStateOf(false) }
    var notes by remember(profile) { mutableStateOf(emptySet<String>()) }
    val context = PhoneProfileContext.select(profile, style, notes)
    ProfileBody {
        Text(profile.name, style = MaterialTheme.typography.titleMedium)
        Text(label(Words.PhoneProfileChooseHelp))
        if (profile.style.isNotBlank()) ProfileOption(label(Words.PhoneProfileStyle), profile.style, style, { style = it },
            Modifier.testTag("phone-profile-include-style"))
        profile.notes.forEachIndexed { index, note ->
            ProfileOption(label(Words.PhoneProfileNote), note.text, note.id in notes,
                { checked -> notes = if (checked) notes + note.id else notes - note.id },
                Modifier.testTag("phone-profile-include-note-$index"))
        }
        if (context?.fits == false) Text(label(Words.PhoneProfileContextLimit), color = MaterialTheme.colorScheme.error)
        if (!current) Text(label(Words.PhoneProfileChanged), color = MaterialTheme.colorScheme.error)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ context?.let { vm.prepareProfileReview(profile, it) } },
            Modifier.weight(1f).testTag("phone-profile-request-review"), enabled = current && context?.fits == true) {
            Text(label(Words.Review))
        }
        TextButton(back, Modifier.weight(1f)) { Text(label(Words.Cancel)) }
    }
}

@Composable
private fun ProfileOption(name: String, text: String, checked: Boolean, change: (Boolean) -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(text)
        }
    }
}

@Composable
private fun ColumnScope.ProfileSendReview(vm: LocalAiViewModel, review: PhoneProfileReview, state: LocalAiState,
    draft: String, profileCurrent: Boolean) {
    val current = profileCurrent && !state.busy && review.matches(state, draft)
    var use by remember(review, current) { mutableStateOf<LocalPromptPreview?>(null) }
    LaunchedEffect(review, current) {
        if (current) use = vm.controller.previewProfilePrompt(review)
    }
    ProfileBody {
        Text(label(Words.PhoneProfileRequestReview), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("phone-profile-request-detail"))
        Text(review.profile.name)
        Text(label(Words.PhoneLoaded).replace("{model}", review.model.metadata.name))
        Text(label(Words.PhoneProfileRequestBoundary))
        if (!current) Text(label(Words.PhoneProfileChanged), color = MaterialTheme.colorScheme.error)
        review.context.style?.let {
            Text(label(Words.PhoneProfileStyle), style = MaterialTheme.typography.titleSmall)
            SelectionContainer { Text(it) }
        }
        review.context.notes.forEach {
            Text(label(Words.PhoneProfileNote), style = MaterialTheme.typography.titleSmall)
            SelectionContainer { Text(it.text) }
        }
        Text(label(Words.PhoneMessage), style = MaterialTheme.typography.titleSmall)
        SelectionContainer { Text(review.question) }
        if (current && use == null) Text(label(Words.PhoneProfileChecking))
        if (current && use == LocalPromptPreview.Unavailable) Text(label(Words.PhoneProfileEstimateUnavailable))
        PhonePromptUse(use)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ vm.sendProfileQuestion(review) }, Modifier.weight(1f).testTag("phone-profile-send"),
            enabled = current && (use as? LocalPromptPreview.Exact)?.budget?.fits == true) { Text(label(Words.Send)) }
        TextButton(vm::discardProfileReview, Modifier.weight(1f).testTag("phone-profile-review-cancel")) {
            Text(label(Words.Cancel))
        }
    }
}
