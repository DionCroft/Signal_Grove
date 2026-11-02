package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.data.Conversation

@Composable fun ConversationBrowser(vm: HermesViewModel,dismiss: ()->Unit) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val loading by vm.sessionsLoading.collectAsStateWithLifecycle()
    val hasMore by vm.sessionsHasMore.collectAsStateWithLifecycle()
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val p by vm.preferences.collectAsStateWithLifecycle()
    var search by rememberSaveable {mutableStateOf("")}
    var hidden by rememberSaveable {mutableStateOf(false)}
    var renaming by remember {mutableStateOf<Conversation?>(null)}
    var deleting by remember {mutableStateOf<Conversation?>(null)}
    val rows=conversations.filter {(!it.hidden || hidden) && it.title.contains(search,true)}
    AlertDialog(onDismissRequest=dismiss,title={Text(label(Words.History))},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text(label(Words.Search))},singleLine=true)
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(hidden,{hidden=it});Text(label(Words.ShowHidden),Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)}
            if(!p.demo) TextButton({vm.refreshSessions()},enabled=!loading && caps?.sessions==true) {Text(label(Words.Refresh))}
            if(loading) QuietProgress()
            LazyColumn(Modifier.heightIn(max=350.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(rows,key={it.key}) {c ->
                    Column {
                        TextButton({vm.select(c.key);dismiss()},Modifier.fillMaxWidth()) {Text((if(c.pinned) "★ " else "")+c.title.ifBlank {Words.NewChat.en},Modifier.fillMaxWidth())}
                        Row {
                            IconButton({vm.pinConversation(c.key)}) {Icon(Icons.Outlined.PushPin,label(Words.Pin))}
                            IconButton({vm.hideConversation(c.key)}) {Icon(Icons.Outlined.VisibilityOff,label(Words.Hide))}
                            IconButton({renaming=c},enabled=!loading && (p.demo || caps?.sessions!=true || caps?.endpoints?.containsKey("session_update")==true)) {Icon(Icons.Outlined.Edit,label(Words.Rename))}
                            IconButton({deleting=c},enabled=!loading) {Icon(Icons.Outlined.DeleteOutline,label(Words.Delete))}
                        }
                        HorizontalDivider()
                    }
                }
                if(rows.isEmpty()) item {Text(label(Words.NoMatches))}
                if(hasMore) item {TextButton({vm.refreshSessions(more=true)},enabled=!loading) {Text(label(Words.LoadMore))}}
            }
        }
    },confirmButton={TextButton(dismiss) {Text(label(Words.Close))}})
    renaming?.let {c ->
        var title by remember(c.key) {mutableStateOf(c.title)}
        AlertDialog(onDismissRequest={renaming=null},title={Text(label(Words.Rename))},text={OutlinedTextField(title,{title=it},label={Text(label(Words.ConversationTitle))})},
            confirmButton={TextButton({vm.renameConversation(c.key,title);renaming=null},enabled=title.isNotBlank() && title.length<=200) {Text(label(Words.Save))}},dismissButton={TextButton({renaming=null}) {Text(label(Words.Cancel))}})
    }
    deleting?.let {c ->
        val remote=!p.demo && caps?.sessions==true
        val allowed=!remote || caps?.endpoints?.containsKey("session_delete")==true
        AlertDialog(onDismissRequest={deleting=null},title={Text(label(if(remote) Words.DeleteServer else Words.DeleteLocal))},text={Text(c.title+"\n\n"+label(Words.DeleteSessionConfirm))},
            confirmButton={TextButton({vm.deleteConversation(c.key,remote);deleting=null},enabled=allowed && (c.status in HermesViewModel.terminal || c.status=="idle")) {Text(label(Words.Delete))}},dismissButton={TextButton({deleting=null}) {Text(label(Words.Cancel))}})
    }
}
