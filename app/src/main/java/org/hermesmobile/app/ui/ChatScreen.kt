package org.hermesmobile.app.ui

import android.widget.TextView
import androidx.compose.foundation.layout.*
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
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val connecting by vm.connecting.collectAsStateWithLifecycle()
    val historyLoading by vm.historyLoading.collectAsStateWithLifecycle()
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val status by vm.runStatus.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val approval by vm.approval.collectAsStateWithLifecycle()
    var history by rememberSaveable { mutableStateOf(false) }
    val list=rememberLazyListState()
    LaunchedEffect(messages.size,messages.lastOrNull()?.content?.length) { if(messages.isNotEmpty()) list.scrollToItem(messages.lastIndex) }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Word(Words.Chat,Modifier.weight(1f))
            IconButton({history=true},enabled=!busy) { Icon(Icons.Outlined.History,label(Words.History)) }
            IconButton(vm::refreshHistory,enabled=!busy && !historyLoading && !LocalPreferences.current.demo && caps?.sessions==true) {Icon(Icons.Outlined.Refresh,label(Words.RefreshHistory))}
            IconButton(vm::newChat,enabled=!busy) { Icon(Icons.Outlined.Add,label(Words.NewChat)) }
        }
        if(busy || historyLoading) QuietProgress()
        if(messages.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) { Word(Words.NoChats) }
        else LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=list,verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=8.dp)) {
            items(messages,key={it.id}) { MessageCard(it) }
        }
        if(activity.isNotBlank()) Text(activity,style=MaterialTheme.typography.labelSmall,maxLines=3)
        if(status!="idle") {
            Text(label(statusWord(status)),style=MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(status !in HermesViewModel.terminal) TextButton(vm::stop) { Text(label(Words.Stop)) }
                if(!busy && status !in setOf("completed","idle","response_finished")) TextButton(vm::retryOrRefresh) { Text(label(Words.CheckRun)) }
                if(!busy && status in setOf("failed","cancelled","interrupted")) TextButton(vm::prepareRetry) { Text(label(Words.Retry)) }
            }
        }
        OutlinedTextField(draft,{vm.draft.value=it},Modifier.fillMaxWidth(),placeholder={Text(label(Words.Message))},maxLines=5,
            trailingIcon={IconButton({vm.send()},enabled=draft.isNotBlank() && !busy && !connecting) { Icon(Icons.AutoMirrored.Outlined.Send,label(Words.Send)) }})
        Spacer(Modifier.height(4.dp))
    }
    if(history) ConversationBrowser(vm) {history=false}
    approval?.let { a -> AlertDialog(onDismissRequest={},title={Text(label(Words.Approval))},text={SelectionContainer {Text(a.description.ifBlank { label(Words.Approval) })}},
        confirmButton={if("once" in a.choices) TextButton({vm.approve("once")}) {Text(label(Words.AllowOnce))}},
        dismissButton={if("deny" in a.choices) TextButton({vm.approve("deny")}) {Text(label(Words.Deny))}}) }
}
@Suppress("DEPRECATION")
@Composable fun MessageCard(message: ChatMessage) {
    val clipboard=LocalClipboardManager.current
    var copied by remember {mutableStateOf(false)}
    val ink=MaterialTheme.colorScheme.onSurface.toArgb()
    val primary=MaterialTheme.colorScheme.primary.toArgb()
    val density=androidx.compose.ui.platform.LocalDensity.current
    HermesCard(Modifier.padding(start=if(message.role=="user") 24.dp else 0.dp,end=if(message.role=="assistant") 8.dp else 0.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(if(message.role=="user") label(Words.You) else "Hermes",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
            Text(DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(message.timestamp)),style=MaterialTheme.typography.labelSmall)
            IconButton({clipboard.setText(AnnotatedString(message.content));copied=true}) { Icon(Icons.Outlined.ContentCopy,label(if(copied) Words.Copied else Words.Copy),Modifier.size(18.dp)) }
        }
        if(message.content.isNotEmpty()) AndroidView(factory={context -> TextView(context).apply {setTextIsSelectable(true);setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,16f);tag=Markwon.create(context)}},
            modifier=Modifier.fillMaxWidth(),update={ view ->
                view.setTextColor(ink);view.setLinkTextColor(primary)
                view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,16f*density.density*density.fontScale)
                (view.tag as Markwon).setMarkdown(view,message.content)
            })
        if(message.role=="assistant" && message.status!="completed") Text(label(statusWord(message.status)),style=MaterialTheme.typography.labelSmall)
    }
}
