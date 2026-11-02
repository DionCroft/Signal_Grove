package org.hermesmobile.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.HermesApplication
import org.hermesmobile.app.data.*

@Composable fun ConnectionProfiles(vm:HermesViewModel) {
    val profiles by vm.savedConnections.collectAsStateWithLifecycle()
    val scope by vm.profileScope.collectAsStateWithLifecycle()
    val connecting by vm.connecting.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val job by vm.jobBusy.collectAsStateWithLifecycle()
    val control by vm.controlBusy.collectAsStateWithLifecycle()
    val enabled=!connecting && !busy && !job && !control
    var show by remember {mutableStateOf(false)}
    var rename by remember {mutableStateOf<SavedConnection?>(null)}
    var remove by remember {mutableStateOf<SavedConnection?>(null)}
    var cache by remember {mutableStateOf<SavedConnection?>(null)}
    var name by remember {mutableStateOf("")}
    TextButton({show=true},Modifier.testTag("saved-connections")) {Text(label(Words.Connections))}
    if(show) AlertDialog(onDismissRequest={show=false},title={Text(label(Words.Connections))},text={
        LazyColumn(Modifier.heightIn(max=480.dp).testTag("connection-list"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(profiles.isEmpty()) item {Text(label(Words.NoSavedConnections))}
            items(profiles,key={it.id}) {row ->Column(Modifier.testTag("connection-${row.id}")) {
                Text(row.name,style=MaterialTheme.typography.titleMedium);Text(row.url,style=MaterialTheme.typography.bodySmall)
                if(row.id==scope) Text(label(Words.CurrentConnection))
                if(!row.hasCredential) Text(label(Words.CredentialRemoved))
                if(row.hasCredential && row.id!=scope) TextButton({vm.useSavedConnection(row.id)},enabled=enabled) {Text(label(Words.UseConnection))}
                TextButton({cache=row},Modifier.testTag("connection-cache-${row.id}")) {Text(label(Words.SavedCache))}
                TextButton({rename=row;name=row.name},enabled=enabled) {Text(label(Words.Rename))}
                if(row.hasCredential) TextButton({remove=row},enabled=enabled) {Text(label(Words.RemoveCredential))}
            }}
            item {Text(label(Words.RotationPolicy),style=MaterialTheme.typography.bodySmall)}
        }
    },confirmButton={TextButton({show=false}) {Text(label(Words.Close))}})
    rename?.let {row ->AlertDialog(onDismissRequest={rename=null},title={Text(label(Words.ConnectionName))},text={
        OutlinedTextField(name,{if(it.length<=80) name=it},Modifier.testTag("connection-name"),singleLine=true)
    },confirmButton={TextButton({vm.renameConnection(row.id,name);rename=null},enabled=enabled && name.isNotBlank() && name.none(Char::isISOControl)) {Text(label(Words.Save))}},dismissButton={TextButton({rename=null}) {Text(label(Words.Cancel))}})}
    remove?.let {row ->AlertDialog(onDismissRequest={remove=null},title={Text(row.name)},text={Text(label(Words.RemoveCredentialInfo))},confirmButton={TextButton({vm.removeConnectionCredential(row.id);remove=null},enabled=enabled) {Text(label(Words.RemoveCredential))}},dismissButton={TextButton({remove=null}) {Text(label(Words.Cancel))}})}
    cache?.let {CacheHistory(it) {cache=null}}
}

@Composable private fun CacheHistory(profile:SavedConnection,close:()->Unit) {
    val app=LocalContext.current.applicationContext as HermesApplication
    val dao=app.database.dao()
    val rows by remember(profile.id) {dao.conversations(profile.id)}.collectAsStateWithLifecycle(emptyList())
    var selected by remember(profile.id) {mutableStateOf<Conversation?>(null)}
    val current=selected
    val messages by remember(current?.key) {if(current==null) kotlinx.coroutines.flow.flowOf(emptyList()) else dao.messages(current.key)}.collectAsStateWithLifecycle(emptyList())
    AlertDialog(onDismissRequest=close,title={Text(current?.title ?: profile.name)},text={
        LazyColumn(Modifier.heightIn(max=480.dp).testTag("connection-cache"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(label(Words.ReadOnlyCache),style=MaterialTheme.typography.bodySmall)}
            if(current==null) items(rows,key={it.key}) {row ->Text(row.title.ifBlank {row.remoteId},Modifier.fillMaxWidth().clickable {selected=row}.padding(vertical=12.dp))}
            else items(messages,key={it.id}) {MessageCard(it,current)}
        }
    },confirmButton={TextButton(close,Modifier.testTag("close-connection-cache")) {Text(label(Words.Close))}},dismissButton={if(current!=null) TextButton({selected=null}) {Text(label(Words.History))}})
}
