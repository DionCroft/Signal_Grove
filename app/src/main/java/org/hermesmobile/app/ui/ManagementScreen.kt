package org.hermesmobile.app.ui

import android.content.Intent
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
import androidx.lifecycle.viewmodel.compose.viewModel
import org.hermesmobile.app.BuildConfig

@Composable fun ManagementEntry(api:HermesViewModel,vm:ManagementViewModel=viewModel()) {
    val scope by api.profileScope.collectAsStateWithLifecycle()
    val apiPreferences by api.preferences.collectAsStateWithLifecycle()
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(scope) {vm.bind(scope)}
    var show by remember {mutableStateOf(false)}
    TextButton({show=true},Modifier.testTag("management-open")) {Text(label(Words.Management))}
    if(show) ManagementDialog(vm,s,apiPreferences.url,scope=="demo" || scope=="unconfigured") {vm.cancel();show=false}
}
@Composable private fun ManagementDialog(vm:ManagementViewModel,s:ManagementState,apiUrl:String,unconfigured:Boolean,close:()->Unit) {
    val context=LocalContext.current.applicationContext
    var url by remember(s.url) {mutableStateOf(s.url)}
    var http by remember(s.debugHttp) {mutableStateOf(s.debugHttp)}
    var profiles by remember {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.Management),style=MaterialTheme.typography.titleMedium)},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("management-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(label(Words.ManagementBoundary),style=MaterialTheme.typography.bodySmall);Text(apiUrl,style=MaterialTheme.typography.labelSmall)}
            if(unconfigured) item {Text(label(Words.ManagementNeedsConnection))}
            else {
                item {
                    OutlinedTextField(url,{url=it},Modifier.fillMaxWidth().testTag("management-url"),label={Text(label(Words.ManagementUrl))},singleLine=true,enabled=!s.busy)
                    if(BuildConfig.DEBUG) Row {Checkbox(http,{http=it},enabled=!s.busy);Text(label(Words.DebugHttp))}
                    Button({vm.signIn(url,http) {link->context.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}},enabled=!s.busy && url.isNotBlank(),modifier=Modifier.testTag("management-sign-in")) {Text(label(Words.ManagementSignIn))}
                }
                if(s.busy) item {QuietProgress();Text(label(Words.Loading));TextButton(vm::cancel) {Text(label(Words.Cancel))}}
                if(s.signedIn) {
                    item {
                        if(s.identity!=null && s.profiles.isEmpty() && !s.busy) Text(label(Words.EmptyInventory))
                        Text(s.identity?.let {it.displayName.ifBlank {it.userId}+" · "+it.provider} ?: label(Words.ManagementVerify))
                        TextButton({vm.refresh()},enabled=!s.busy) {Text(label(Words.Refresh))}
                        TextButton({vm.refresh(renew=true)},enabled=!s.busy) {Text(label(Words.ManagementRenew))}
                        TextButton({profiles=true},enabled=!s.busy && s.identity!=null && s.profiles.isNotEmpty(),modifier=Modifier.testTag("management-profile")) {Text(label(Words.ManagementProfile)+s.profile.takeIf {it.isNotBlank()}?.let {"\n$it"}.orEmpty())}
                        TextButton(vm::disconnect,enabled=!s.busy) {Text(label(Words.ManagementForget))}
                    }
                    if(s.profile.isNotBlank()) item {Text(s.profile,style=MaterialTheme.typography.titleMedium);Text(label(Words.ManagementReadOnly),style=MaterialTheme.typography.bodySmall)}
                    if(s.loaded && s.servers.isEmpty()) item {Text(label(Words.EmptyInventory))}
                    items(s.servers,key={it.name}) {server ->Column {
                        Text(server.name,style=MaterialTheme.typography.titleMedium)
                        Text(listOf(server.transport,server.auth,server.source).filter {it.isNotBlank()}.joinToString(" · "))
                        Text(label(if(server.enabled) Words.Enabled else Words.Disabled))
                        if(server.tools.isNotEmpty()) Text(server.tools.joinToString("\n"),style=MaterialTheme.typography.bodySmall)
                    }}
                }
                s.error?.let {message->item {Text(label(message),color=MaterialTheme.colorScheme.error)}}
            }
        }
    },confirmButton={TextButton(close) {Text(label(Words.Close))}})
    if(profiles) AlertDialog(onDismissRequest={profiles=false},title={Text(label(Words.ManagementProfile))},text={
        LazyColumn(Modifier.heightIn(max=400.dp)) {items(s.profiles,key={it.name}) {row ->TextButton({vm.chooseProfile(row.name);profiles=false},enabled=!s.busy) {Text(row.displayName.takeIf {it.isNotBlank()}?.let {"$it\n${row.name}"} ?: row.name)}}}
    },confirmButton={TextButton({profiles=false}) {Text(label(Words.Cancel))}})
}
