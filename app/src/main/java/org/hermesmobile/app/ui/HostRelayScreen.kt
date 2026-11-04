package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.*

@Composable fun HostRelayDialog(vm:ManagementViewModel,s:ManagementState,openPortal:((String)->Unit)?=null,close:()->Unit) {
    var read by remember(s.apiScope,s.url,s.profile,s.identity) {mutableStateOf<ReviewedRelayRead?>(null)}
    var portal by remember(s.relay) {mutableStateOf<ReviewedRelayPortal?>(null)}
    var reveal by remember(s.relay) {mutableStateOf(false)}
    val inventory=s.relay?.takeIf {it.owner==vm.reviewTarget()}
    val uri=LocalUriHandler.current
    AlertDialog(onDismissRequest=close,title={Text(label(Words.RelayAccounts))},text={LazyColumn(Modifier.heightIn(max=520.dp).testTag("relay-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(s.url);Text(s.profile)}
        item {Text(label(Words.RelayPrivacy))}
        item {TextButton({read=vm.prepareRelayRead()},enabled=!s.busy && s.identity!=null && GatewayCronInventory.supportedProfile(s.profile),modifier=Modifier.testTag("relay-read")) {Text(label(Words.RelayRead))};if(!GatewayCronInventory.supportedProfile(s.profile)) Text(label(Words.PluginProfileUnsupported))}
        if(s.busy) item {QuietProgress();TextButton(vm::cancel) {Text(label(Words.Cancel))}}
        s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
        inventory?.let {current->
            item {Text(label(when(current.manager) {HostRelayManager.Portal->Words.RelayPortalManaged;HostRelayManager.Connector->Words.RelayConnectorManaged;HostRelayManager.Unknown->Words.RelayUnknownManaged}));Text(label(Words.RelayAccountCount)+": "+current.accountCount)}
            item {Text(label(Words.RelayReported))}
            item {TextButton({reveal=!reveal},enabled=!s.busy,modifier=Modifier.testTag("relay-reveal")) {Text(label(if(reveal) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
            if(reveal) {
                for((index,platform) in current.platforms.withIndex()) {
                    item(key="platform:"+index) {Text(platform.id,style=MaterialTheme.typography.titleMedium);if(platform.accounts.isEmpty()) Text(label(Words.EmptyInventory))}
                    for((accountIndex,account) in platform.accounts.withIndex()) item(key="account:$index:$accountIndex") {Text(label(Words.RelayAccountId)+": "+account.id);if(account.name.isNotEmpty()) Text(label(Words.RelayAccountName)+": "+account.name);if(account.handle.isNotEmpty()) Text(label(Words.RelayAccountHandle)+": "+account.handle)}
                    if(platform.botUrl.isNotEmpty()) item(key="bot:"+index) {Text(label(Words.RelayBotUrl));Text(platform.botUrl)}
                }
            }
            if(current.manager==HostRelayManager.Portal) {
                item {Text(label(Words.RelayPortalEffect))}
                item {TextButton({portal=vm.prepareRelayPortal(current)},enabled=!s.busy && HostRelayPolicy.portal(current.portalUrl),modifier=Modifier.testTag("relay-portal-review")) {Text(label(Words.RelayPortalReview))};if(!HostRelayPolicy.portal(current.portalUrl)) Text(label(Words.RelayPortalUnavailable))}
            } else item {Text(label(Words.RelayLocalControlsPending))}
        }
    }},confirmButton={TextButton(close,Modifier.testTag("relay-close")) {Text(label(Words.Close))}})
    read?.let {frozen->AlertDialog(onDismissRequest={read=null},title={Text(label(Words.RelayRead))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("relay-read-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor)};item {Text(label(Words.RelayPrivacy))}}},confirmButton={TextButton({read=null;vm.loadHostRelay(frozen)},enabled=!s.busy,modifier=Modifier.testTag("relay-read-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({read=null},Modifier.testTag("relay-read-cancel")) {Text(label(Words.Cancel))}})}
    portal?.let {frozen->
        var approved by remember(frozen) {mutableStateOf(false)}
        AlertDialog(onDismissRequest={portal=null},title={Text(label(Words.RelayPortalReview))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("relay-portal-review-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor)}
            item {Text(label(Words.RelayPortalEffect))}
            item {Text(frozen.inventory.portalUrl,Modifier.testTag("relay-reviewed-portal"))}
            item {Row {Checkbox(approved,{approved=it},Modifier.testTag("relay-portal-approve"));Text(label(Words.RelayPortalGrant),Modifier.weight(1f))}}
        }},confirmButton={TextButton({portal=null;vm.openRelayPortal(frozen) {if(openPortal==null) uri.openUri(it) else openPortal(it)}},enabled=!s.busy && approved,modifier=Modifier.testTag("relay-portal-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({portal=null},Modifier.testTag("relay-portal-cancel")) {Text(label(Words.Cancel))}})
    }
}
