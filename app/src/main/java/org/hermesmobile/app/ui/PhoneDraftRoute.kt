package org.hermesmobile.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.hermesmobile.app.LocalAiActivity
import org.hermesmobile.app.local.UnsentDraft

@Composable fun PhoneDraftRoute(vm:HermesViewModel,available:Boolean) {
    val context=LocalContext.current
    val work=rememberCoroutineScope()
    val busy by vm.controlBusy.collectAsStateWithLifecycle()
    var review by remember {mutableStateOf<UnsentDraft.Review?>(null)}
    TextButton({review=vm.reviewPhoneDraft()},enabled=available,modifier=Modifier.testTag("phone-draft-open")) {Text(label(Words.PhoneDraftOpen))}
    if(!available) Text(label(Words.PhoneDraftUnavailable),style=MaterialTheme.typography.bodySmall)
    review?.let {snapshot ->
        var text by remember(snapshot) {mutableStateOf(snapshot.text)}
        Dialog(onDismissRequest={if(!busy) review=null},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
            val focus=LocalFocusManager.current
            val keyboard=LocalSoftwareKeyboardController.current
            Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("phone-draft-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(label(Words.PhoneDraftReview),style=MaterialTheme.typography.headlineSmall)
                        Text(label(Words.PhoneDraftHelp))
                        OutlinedTextField(text,{if(!busy) text=it},Modifier.fillMaxWidth().testTag("phone-draft-text"),maxLines=5)
                    }
                    TextButton({work.launch {
                        val token=vm.routePhoneDraft(snapshot,text)
                        if(token!=null) {
                            focus.clearFocus();keyboard?.hide()
                            try {context.startActivity(Intent(context,LocalAiActivity::class.java).putExtra(LocalAiActivity.DRAFT_TOKEN,token))}
                            catch(e:Exception) {vm.cancelPhoneDraft(token)}
                            review=null
                        }
                    }},enabled=!busy && UnsentDraft.valid(text),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("phone-draft-confirm")) {Text(label(Words.PhoneDraftConfirm))}
                    TextButton({focus.clearFocus();keyboard?.hide();review=null},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("phone-draft-cancel")) {Text(label(Words.Cancel))}
                }
            }
        }
    }
}
