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
import org.hermesmobile.app.data.*

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
    var gateway by remember(s.apiScope,s.url,s.profile) {mutableStateOf(false)}
    var hostSkills by remember(s.apiScope,s.url,s.profile) {mutableStateOf(false)}
    var hostCron by remember(s.apiScope,s.url,s.profile) {mutableStateOf(false)}
    var oauth by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedMcpOAuth?>(null)}
    var catalog by remember {mutableStateOf(false)}
    var create by remember {mutableStateOf(false)}
    val allReceipts by vm.receipts.collectAsStateWithLifecycle()
    val receipts=allReceipts.filter {it.resourceScope==vm.resourceScope(s)}
    val pending=receipts.any {it.state in setOf("sending","unknown")}
    var review by remember {mutableStateOf<ReviewedMcpAction?>(null)}
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
                    if(s.profile.isNotBlank()) item {TextButton({create=true},enabled=!s.busy && !pending && s.identity!=null && s.loaded,modifier=Modifier.testTag("mcp-add")) {Text(label(Words.McpAdd))}}
                    if(s.profile.isNotBlank()) item {TextButton({catalog=true;vm.loadCatalog()},enabled=!s.busy && s.identity!=null,modifier=Modifier.testTag("mcp-catalog-open")) {Text(label(Words.McpCatalog))}}
                    if(s.profile.isNotBlank()) item {TextButton({gateway=true},enabled=!s.busy && s.identity!=null,modifier=Modifier.testTag("gateway-cron-open")) {Text(label(Words.GatewayAutomation))}}
                    if(s.profile.isNotBlank()) item {TextButton({hostSkills=true;vm.loadHostSkills()},enabled=!s.busy && s.identity!=null,modifier=Modifier.testTag("host-skills-open")) {Text(label(Words.HostSkills))}}
                    if(s.profile.isNotBlank()) item {TextButton({hostCron=true;vm.loadHostCron()},enabled=!s.busy && s.identity!=null,modifier=Modifier.testTag("host-cron-open")) {Text(label(Words.HostCron))}}
                    if(s.loaded && s.servers.isEmpty()) item {Text(label(Words.EmptyInventory))}
                    if(pending) item {Text(label(Words.ManagementOutcomePending),color=MaterialTheme.colorScheme.error)}
                    s.notice?.let {word->item {Text(label(word))}}
                    s.observation?.let {observation->item {
                        Text(observation.name)
                        Text(label(if(!observation.exists) Words.McpAbsent else if(observation.enabled==true) Words.Enabled else Words.Disabled))
                        Text(label(Words.ManagementObservationOnly),style=MaterialTheme.typography.bodySmall)
                    }}
                    items(s.servers,key={it.name}) {server ->Column {
                        Text(server.name,style=MaterialTheme.typography.titleMedium)
                        Text(listOf(server.transport,server.auth,server.source).filter {it.isNotBlank()}.joinToString(" · "))
                        Text(label(if(server.enabled) Words.Enabled else Words.Disabled))
                        if(server.tools.isNotEmpty()) Text(server.tools.joinToString("\n"),style=MaterialTheme.typography.bodySmall)
                        val addressable=McpProtocol.addressable(server.name)
                        val ready=!s.busy && !pending && s.identity!=null && s.loaded && addressable
                        if(!addressable) Text(label(Words.McpUnsupportedName),style=MaterialTheme.typography.bodySmall)
                        if(server.source!="config") Text(label(Words.McpPluginManaged),style=MaterialTheme.typography.bodySmall)
                        if(McpOAuthSnapshot.eligible(server)) TextButton({oauth=vm.prepareOAuth(server)},enabled=ready,modifier=Modifier.testTag("mcp-oauth-start-${server.name}")) {Text(label(Words.McpOAuthStart))}
                        TextButton({review=vm.prepareMcp(server,McpAction.Probe)},enabled=ready,modifier=Modifier.testTag("mcp-probe-${server.name}")) {Text(label(Words.McpProbe))}
                        TextButton({review=vm.prepareMcp(server,McpAction.Toggle)},enabled=ready && server.source=="config",modifier=Modifier.testTag("mcp-toggle-${server.name}")) {Text(label(if(server.enabled) Words.McpDisable else Words.McpEnable))}
                        TextButton({review=vm.prepareMcp(server,McpAction.Delete)},enabled=ready && server.source=="config",modifier=Modifier.testTag("mcp-delete-${server.name}")) {Text(label(Words.McpRemove))}
                    }}
                }
                if(receipts.isNotEmpty()) item {Text(label(Words.ManagementReceipts),style=MaterialTheme.typography.titleMedium)}
                items(receipts,key={it.key}) {receipt ->ManagementReceiptCard(receipt,vm,s)}
                s.error?.let {message->item {Text(label(message),color=MaterialTheme.colorScheme.error)}}
            }
        }
    },confirmButton={TextButton(close) {Text(label(Words.Close))}})
    if(gateway) GatewayAutomationDialog(vm,s) {vm.cancel();gateway=false}
    if(hostSkills) HostSkillsDialog(vm,s) {vm.cancel();vm.closeSkillContent();hostSkills=false}
    if(hostCron) HostCronDialog(vm,s) {vm.cancel();vm.closeHostCronDetail();hostCron=false}
    oauth?.let {frozen ->McpOAuthStartReview(frozen,!s.busy,{oauth=null}) {vm.startOAuth(frozen);oauth=null}}
    if(catalog) McpCatalogDialog(vm,s,pending) {catalog=false}
    if(create) McpCreationForm(vm,!s.busy) {create=false}
    review?.let {frozen ->McpActionReview(frozen,!s.busy,{review=null}) {vm.performMcp(frozen);review=null}}
    if(profiles) AlertDialog(onDismissRequest={profiles=false},title={Text(label(Words.ManagementProfile))},text={
        LazyColumn(Modifier.heightIn(max=400.dp)) {items(s.profiles,key={it.name}) {row ->TextButton({vm.chooseProfile(row.name);profiles=false},enabled=!s.busy) {Text(row.displayName.takeIf {it.isNotBlank()}?.let {"$it\n${row.name}"} ?: row.name)}}}
    },confirmButton={TextButton({profiles=false}) {Text(label(Words.Cancel))}})
}
