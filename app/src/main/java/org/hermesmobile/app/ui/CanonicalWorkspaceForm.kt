package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.hermesmobile.app.data.*

@Composable internal fun CanonicalWorkspaceForm(
    snapshot:CanonicalSessionSnapshot,pending:Boolean,current:()->Boolean,
    cancel:()->Unit,confirm:(CanonicalWorkspaceReview)->Unit
) {
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val original=(snapshot.workspace as CanonicalWorkspaceDirectory.Reported).path
    var directory by remember(snapshot) {mutableStateOf(original)}
    var review by remember(snapshot) {mutableStateOf<CanonicalWorkspaceReview?>(null)}
    var consent by remember(review) {mutableStateOf(false)}
    val valid=CanonicalWorkspaceDirectory.absolute(directory) && directory!=original
    val allowed=current() && !pending
    Dialog(cancel,DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val view=LocalView.current
        val light=MaterialTheme.colorScheme.surface.luminance()>0.5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let {window->
                WindowCompat.getInsetsController(window,window.decorView).apply {
                    isAppearanceLightStatusBars=light;isAppearanceLightNavigationBars=light
                }
            }
        }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("workspace-form-body"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(label(if(review==null) Words.WorkspaceChange else Words.WorkspaceReview),style=MaterialTheme.typography.titleLarge)
                    SelectionContainer {Column {
                        Text(snapshot.owner.target.url);Text(snapshot.owner.target.profile);Text(snapshot.owner.target.actor)
                        Text(snapshot.owner.sessionId)
                    }}
                    Text(label(Words.WorkspaceFences).replace("{revision}",snapshot.revision.toString()).replace("{generation}",snapshot.executionGeneration.toString()))
                    Text(label(Words.WorkspaceEffect))
                    Text(label(Words.WorkspaceBefore));SelectionContainer {Text(original.ifEmpty {label(Words.ViewerWorkspaceUnset)})}
                    if(review==null) {
                        OutlinedTextField(directory,{directory=it},label={Text(label(Words.WorkspaceDirectory))},
                            modifier=Modifier.fillMaxWidth().testTag("workspace-directory"),minLines=2)
                        if(!valid) Text(label(Words.WorkspaceInvalid))
                    } else {
                        Text(label(Words.WorkspaceRequested));SelectionContainer {Text(checkNotNull(review).requestedDirectory,Modifier.testTag("workspace-reviewed-directory"))}
                        Row {
                            Checkbox(consent,{consent=it},Modifier.testTag("workspace-consent"))
                            Text(label(Words.WorkspaceConsent))
                        }
                    }
                    if(!allowed) Text(label(if(pending) Words.ManagementOutcomePending else Words.ManagementReviewChanged))
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    TextButton(cancel,Modifier.weight(1f).heightIn(min=48.dp).testTag("workspace-cancel"),shape=MaterialTheme.shapes.small) {Text(label(Words.Cancel))}
                    if(review==null) TextButton({focus.clearFocus();keyboard?.hide();review=CanonicalWorkspaceReview.create(snapshot,directory)},
                        Modifier.weight(1f).heightIn(min=48.dp).testTag("workspace-review"),enabled=allowed && valid,shape=MaterialTheme.shapes.small) {Text(label(Words.WorkspaceReview))}
                    else TextButton({val approved=checkNotNull(review);review=null;confirm(approved)},
                        Modifier.weight(1f).heightIn(min=48.dp).testTag("workspace-confirm"),enabled=allowed && consent,shape=MaterialTheme.shapes.small) {Text(label(Words.WorkspaceChange))}
                }
            }
        }
    }
}
