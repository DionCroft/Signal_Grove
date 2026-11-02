package org.hermesmobile.app.ui

import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.noties.markwon.Markwon
import org.hermesmobile.app.data.ChatMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun ChatScreen(vm: HermesViewModel) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val reports by vm.runReports.collectAsStateWithLifecycle()
    var accountingHistory by remember {mutableStateOf(false)}
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val pictureDraft by vm.imageDraft.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val connecting by vm.connecting.collectAsStateWithLifecycle()
    val controlling by vm.controlBusy.collectAsStateWithLifecycle()
    var runtimePicker by rememberSaveable {mutableStateOf(false)}
    val historyLoading by vm.historyLoading.collectAsStateWithLifecycle()
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val status by vm.runStatus.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val current by vm.selectedConversation.collectAsStateWithLifecycle()
    var statusDetails by rememberSaveable {mutableStateOf(false)}
    var resolve by rememberSaveable {mutableStateOf(false)}
    var history by rememberSaveable { mutableStateOf(false) }
    val list=rememberLazyListState()
    val dragging by list.interactionSource.collectIsDraggedAsState()
    var followReply by remember {mutableStateOf(true)}
    LaunchedEffect(current?.key,current?.pendingKey) {followReply=true}
    LaunchedEffect(dragging) {if(dragging) followReply=false}
    LaunchedEffect(list) {snapshotFlow {!list.isScrollInProgress && !list.canScrollForward}.distinctUntilChanged().collect {if(it) followReply=true}}
    LaunchedEffect(messages.size,messages.lastOrNull()?.content?.length,messages.lastOrNull()?.status,followReply) {
        if(messages.isNotEmpty() && followReply && !dragging) {
            list.scrollToItem(messages.lastIndex)
            val last=list.layoutInfo.visibleItemsInfo.lastOrNull {it.index==messages.lastIndex}
            if(last!=null) list.scrollBy((last.offset+last.size-list.layoutInfo.viewportEndOffset).coerceAtLeast(0).toFloat())
        }
    }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Word(Words.Chat,Modifier.weight(1f))
            IconButton({runtimePicker=true},enabled=!busy && !controlling && !connecting) {Icon(Icons.Outlined.Tune,label(Words.Models))}
            IconButton({history=true},enabled=!busy && !controlling) { Icon(Icons.Outlined.History,label(Words.History)) }
            if(current?.transport!="compat") IconButton(vm::refreshHistory,enabled=!busy && !controlling && !historyLoading && !LocalPreferences.current.demo && caps?.sessions==true) {Icon(Icons.Outlined.Refresh,label(Words.RefreshHistory))}
            IconButton(vm::newChat,enabled=!busy && !controlling) { Icon(Icons.Outlined.Add,label(Words.NewChat)) }
        }
        if(busy || historyLoading || controlling) QuietProgress()
        if(messages.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            Word(Words.NoChats)
            if(LocalPreferences.current.demo) {TextButton({vm.sendDemoExercise("approval")},enabled=!busy) {Text(label(Words.DemoApproval))};TextButton({vm.sendDemoExercise("clarify")},enabled=!busy) {Text(label(Words.DemoClarification))}}
        }
        else LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("chat-messages"),state=list,verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=8.dp)) {
            items(messages,key={it.id}) { MessageCard(it,current) }
        }
        if(activity.isNotBlank()) Text(activity,style=MaterialTheme.typography.labelSmall,maxLines=1)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {RunControls(vm,compact=true)}
            SessionControls(vm)
            if(status!="idle") IconButton({statusDetails=true}) {Icon(if(status=="completed") Icons.Outlined.CheckCircle else Icons.Outlined.Info,label(statusWord(status)))}
            if(status !in HermesViewModel.terminal && status!="idle" && (LocalPreferences.current.demo || (current?.runId?.isNotBlank()==true && caps?.has("run_stop")==true))) IconButton(vm::stop,enabled=!controlling) {Icon(Icons.Outlined.StopCircle,label(Words.Stop))}
            if(!busy && status !in setOf("completed","idle","response_finished")) IconButton(vm::retryOrRefresh,enabled=!controlling) {Icon(Icons.Outlined.Refresh,label(Words.CheckRun))}
            if(!busy && status in setOf("failed","cancelled","interrupted","incomplete","response_failed","response_cancelled","response_incomplete")) IconButton(vm::prepareRetry,enabled=!controlling) {Icon(Icons.Outlined.Replay,label(Words.Retry))}
        }
        if(status=="unknown" && caps?.has("run_unknown_resolution")==true && caps?.endpoints?.containsKey("run_unknown_resolution")==true && current?.admissionId?.isNotBlank()==true && current?.executionGeneration!=null) TextButton({resolve=true},enabled=!busy && !controlling) {Text(label(Words.ResolveUnknown))}
        DraftImageStrip(vm)
        OutlinedTextField(draft,{vm.draft.value=it},Modifier.fillMaxWidth(),placeholder={Text(label(Words.Message))},maxLines=5,
            leadingIcon={ImagePickerButton(vm)},
            trailingIcon={IconButton({vm.send()},enabled=(draft.isNotBlank() || pictureDraft?.refs?.isNotEmpty()==true) && !busy && !controlling && !connecting) { Icon(Icons.AutoMirrored.Outlined.Send,label(Words.Send)) }})
        Spacer(Modifier.height(4.dp))
    }
    if(accountingHistory) AccountingHistoryDialog(reports) {accountingHistory=false}
    if(statusDetails) AlertDialog(onDismissRequest={statusDetails=false},title={Text(label(Words.RunStatus))},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label(statusWord(status)))
        current?.runId?.takeIf {it.isNotBlank()}?.let {Text(label(Words.RunIdentity));Text(it)}
        if(reports.isNotEmpty()) TextButton({statusDetails=false;accountingHistory=true},Modifier.testTag("accounting-history")) {Text(label(Words.RuntimeUsage)+" (${reports.size})")}
    }},confirmButton={TextButton({statusDetails=false}) {Text(label(Words.Close))}})
    if(runtimePicker) RuntimePicker(vm) {runtimePicker=false}
    if(history) ConversationBrowser(vm) {history=false}
    if(resolve) AlertDialog(onDismissRequest={resolve=false},title={Text(label(Words.ResolveUnknown))},text={Text(label(Words.ResolveInfo))},confirmButton={TextButton({resolve=false;vm.acknowledgeUnknown()}) {Text(label(Words.ResolveUnknown))}},dismissButton={TextButton({resolve=false}) {Text(label(Words.Cancel))}})
}
@Suppress("DEPRECATION")
@Composable fun MessageCard(message: ChatMessage,conversation:org.hermesmobile.app.data.Conversation?=null) {
    val clipboard=LocalClipboardManager.current
    var copied by remember {mutableStateOf(false)}
    var runtimeUsage by remember(message.id) {mutableStateOf(false)}
    val ink=MaterialTheme.colorScheme.onSurface.toArgb()
    val primary=MaterialTheme.colorScheme.primary.toArgb()
    if(runtimeUsage) RuntimeUsageDialog(message.telemetryJson) {runtimeUsage=false}
    val density=androidx.compose.ui.platform.LocalDensity.current
    HermesCard(Modifier.padding(start=if(message.role=="user") 24.dp else 0.dp,end=if(message.role=="assistant") 8.dp else 0.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(if(message.role=="user") label(Words.You) else "Hermes",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
            Text(DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(message.timestamp)),style=MaterialTheme.typography.labelSmall)
            if(message.role=="assistant" && message.telemetryJson.isNotBlank()) IconButton({runtimeUsage=true},Modifier.testTag("runtime-usage-${message.id}")) {Icon(Icons.Outlined.Assessment,label(Words.RuntimeUsage),Modifier.size(18.dp))}
            if(message.content.isNotBlank()) IconButton({clipboard.setText(AnnotatedString(message.content));copied=true}) { Icon(Icons.Outlined.ContentCopy,label(if(copied) Words.Copied else Words.Copy),Modifier.size(18.dp)) }
        }
        if(message.content.isNotEmpty()) AndroidView(factory={context -> TextView(context).apply {setTextIsSelectable(true);setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,16f);tag=Markwon.create(context)}},
            modifier=Modifier.fillMaxWidth().testTag("message-body-${message.id}"),update={ view ->
                view.setTextColor(ink);view.setLinkTextColor(primary)
                view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,16f*density.density*density.fontScale)
                (view.tag as Markwon).setMarkdown(view,message.content)
            })
        if(message.imagesJson.isNotBlank() && conversation?.key==message.sessionKey) {
            val app=androidx.compose.ui.platform.LocalContext.current.applicationContext as org.hermesmobile.app.HermesApplication
            ImageAttachments(app.images,conversation.scope,conversation.key,message.imagesJson)
        }
        if(message.role=="assistant" && message.status!="completed") Text(label(statusWord(message.status)),style=MaterialTheme.typography.labelSmall)
    }
}
