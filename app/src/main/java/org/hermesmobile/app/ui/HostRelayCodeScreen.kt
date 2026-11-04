package org.hermesmobile.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.hermesmobile.app.data.*

fun relayCodeStatusWord(status:String)=when(status) {"unverified"->Words.RelayCodeUnchecked;"pending"->Words.RelayCodePending;"inactive"->Words.RelayCodeInactive;"refused"->Words.RelayCodeRefused;else->Words.RelayCodeNotIssued}
@Composable fun RelayCodeCreateReview(review:ReviewedRelayLink,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    var grant by remember(review) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.RelayCodeCreate))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("relay-code-create-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);Text(review.request.platform)};item {Text(label(Words.RelayCodeCreateEffect))};item {Text(label(Words.RelayCodeRetention))}
        item {Row {Checkbox(grant,{grant=it},Modifier.testTag("relay-code-create-approve"));Text(label(Words.RelayCodeCreateGrant),Modifier.weight(1f))}}
    }},confirmButton={TextButton(confirm,enabled=enabled && grant,modifier=Modifier.testTag("relay-code-create-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("relay-code-create-cancel")) {Text(label(Words.Cancel))}})
}
@Composable fun RelayCodesClearReview(review:ReviewedRelayCodesClear,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    var grant by remember(review) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.RelayCodesClear))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("relay-codes-clear-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor)};item {Text(label(Words.RelayCodesClearEffect))};item {Row {Checkbox(grant,{grant=it},Modifier.testTag("relay-codes-clear-approve"));Text(label(Words.RelayCodesClearGrant),Modifier.weight(1f))}}
    }},confirmButton={TextButton(confirm,enabled=enabled && grant,modifier=Modifier.testTag("relay-codes-clear-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close) {Text(label(Words.Cancel))}})
}
@Composable fun RelayCodeDialog(vm:ManagementViewModel,s:ManagementState,flow:HostRelayLinkObservation,openBot:((String)->Unit)?=null) {
    var reveal by remember(flow.binding.receipt.key) {mutableStateOf(false)}
    var browser by remember(flow.code) {mutableStateOf<HostRelayLinkObservation?>(null)}
    val context=LocalContext.current;val uri=LocalUriHandler.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    val clipboardLabel=label(Words.RelayCodeCommand)
    LaunchedEffect(flow.binding.receipt.key,flow.code?.expiresAt) {flow.code?.let {delay((it.expiresAt-System.currentTimeMillis()).coerceAtLeast(0));vm.expireRelayCode(flow.binding.receipt.key)}}
    val code=flow.code?.takeIf {flow.scopeVerified && flow.binding.status in setOf("unverified","pending") && it.expiresAt>System.currentTimeMillis()}
    val ready=!s.busy && code!=null && reveal
    AlertDialog(onDismissRequest=vm::closeRelayCode,title={Text(label(Words.RelayCodeCreate))},text={LazyColumn(Modifier.heightIn(max=520.dp).testTag("relay-code-flow"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(flow.binding.receipt.url);Text(flow.binding.receipt.profile);Text(flow.binding.receipt.actor);Text(flow.binding.receipt.target)}
        item {Text(label(if(flow.binding.issued) Words.RelayCodeIssued else Words.RelayCodeNotIssued));Text(label(relayCodeStatusWord(flow.binding.status)))}
        item {Text(label(Words.RelayCodeRetention))}
        if(flow.binding.issued && flow.binding.status in setOf("unverified","pending") && code==null) item {Text(label(if(flow.available) Words.RelayCodeVerifyFirst else Words.RelayCodeUnavailable))}
        if(flow.binding.issued && flow.binding.status in setOf("unverified","pending")) item {TextButton({vm.refreshRelayCode(flow)},enabled=!s.busy && flow.available,modifier=Modifier.testTag("relay-code-refresh")) {Text(label(Words.RelayCodeRefresh))}}
        if(code!=null || flow.reason!=null) item {TextButton({reveal=!reveal},enabled=!s.busy,modifier=Modifier.testTag("relay-code-reveal")) {Text(label(if(reveal) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
        if(reveal && code!=null) {item {Text(label(Words.RelayCodeCommand));Text(code.command,Modifier.testTag("relay-code-command"))};item {Text(label(Words.RelayCodeLocalExpiry));Text(java.time.Instant.ofEpochMilli(code.expiresAt).toString())};code.botUrl?.let {item {Text(it)}}}
        if(reveal) flow.reason?.let {item {Text(it)}}
        if(code!=null) {
            item {Text(label(Words.RelayCodeShareEffect))}
            item {TextButton({vm.shareRelayCode(flow,false,{lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}) {value->val clip=ClipData.newPlainText(clipboardLabel,value);clip.description.extras=PersistableBundle().apply {putBoolean("android.content.extra.IS_SENSITIVE",true)};requireNotNull(context.getSystemService(ClipboardManager::class.java)).setPrimaryClip(clip)}},enabled=ready,modifier=Modifier.testTag("relay-code-copy")) {Text(label(Words.RelayCodeCopy))}}
            item {TextButton({browser=flow},enabled=ready && code.botUrl!=null,modifier=Modifier.testTag("relay-code-browser-review")) {Text(label(Words.RelayCodeOpenBot))};if(code.botUrl==null) Text(label(Words.RelayCodeNoBot))}
        }
        if(s.busy) item {QuietProgress();TextButton(vm::cancel) {Text(label(Words.Cancel))}}
        s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
    }},confirmButton={TextButton(vm::closeRelayCode,Modifier.testTag("relay-code-close")) {Text(label(Words.Close))}})
    browser?.let {frozen->
        var grant by remember(frozen) {mutableStateOf(false)}
        AlertDialog(onDismissRequest={browser=null},title={Text(label(Words.RelayCodeOpenBot))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("relay-code-browser-review-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(frozen.binding.receipt.url);Text(frozen.binding.receipt.profile);Text(frozen.binding.receipt.target)};item {Text(label(Words.RelayCodeShareEffect))};item {Text(frozen.code!!.botUrl!!,Modifier.testTag("relay-code-reviewed-bot"))};item {Row {Checkbox(grant,{grant=it},Modifier.testTag("relay-code-browser-approve"));Text(label(Words.RelayCodeBrowserGrant),Modifier.weight(1f))}}
        }},confirmButton={TextButton({browser=null;vm.shareRelayCode(frozen,true,{lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}) {if(openBot==null) uri.openUri(it) else openBot(it)}},enabled=!s.busy && grant,modifier=Modifier.testTag("relay-code-browser-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({browser=null}) {Text(label(Words.Cancel))}})
    }
}
