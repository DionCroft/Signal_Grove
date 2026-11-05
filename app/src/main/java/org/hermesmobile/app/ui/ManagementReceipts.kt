package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

fun mcpActionWord(action:String)=when(action) {"ProjectCreate"->Words.ProjectCreate;"ProjectUpdate"->Words.ProjectUpdate;"ProjectAddFolder"->Words.ProjectAddFolder;"ProjectRemoveFolder"->Words.ProjectRemoveFolder;"ProjectSetPrimary"->Words.ProjectSetPrimary;"ProjectArchive"->Words.ProjectArchive;"ProjectRestore"->Words.ProjectRestore;"ProjectDelete"->Words.ProjectDelete;"ProjectSetActive"->Words.ProjectSetActive;"ProjectClearActive"->Words.ProjectClearActive;"RelayLinkStart"->Words.RelayCodeCreate;"RelayUnlink"->Words.RelayUnlink;"ChannelSave"->Words.ChannelsSave;"ChannelPosture"->Words.ChannelsPosture;"CustomEndpointProbe"->Words.EndpointProbe;"CustomEndpointSave"->Words.CustomEndpointsSave;"CustomEndpointActivate"->Words.CustomEndpointsActivate;"CustomEndpointRemove"->Words.CustomEndpointsRemove;"FallbackConfig"->Words.HostFallback;"CredentialPoolAdd"->Words.CredentialPoolAdd;"CredentialPoolRemove"->Words.CredentialPoolRemove;"ProviderOAuthStart"->Words.ProviderOAuthStart;"ProviderOAuthCancel"->Words.ProviderOAuthCancel;"ProviderDisconnect"->Words.ProviderDisconnect;"MoaConfig"->Words.HostMoa;"AuxModel"->Words.HostAuxEdit;"MainModel"->Words.HostMainSet;"EnvEmpty"->Words.HostEnvEmpty;"EnvSave"->Words.HostEnvSave;"EnvRemove"->Words.HostEnvRemove;"PluginSettings"->Words.PluginSettingsEdit;"PluginUpdate"->Words.PluginUpdate;"PluginRemove"->Words.PluginRemove;"PluginEnable"->Words.PluginEnable;"PluginDisable"->Words.PluginDisable;"PluginInstall"->Words.PluginInstall;"HubRemove"->Words.SkillHubRemove;"HubUpdateAll"->Words.SkillHubUpdateAll;"HubInstall"->Words.SkillHubInstall;"GatewayCronPause"->Words.GatewayCronPause;"GatewayCronResume"->Words.GatewayCronResume;"GatewayCronDelete"->Words.GatewayCronDelete;"SkillToggle"->Words.SkillToggle;"SkillLock"->Words.SkillLockControl;"CronCreate"->Words.HostCronCreate;"OAuthStart"->Words.McpOAuthStart;"OAuthCancel"->Words.McpOAuthCancel;"CatalogInstall"->Words.McpCatalogInstall;"Create"->Words.McpAdd;"Toggle"->Words.McpToggle;"Delete"->Words.McpRemove;"Probe"->Words.McpProbe;else->Words.Management}
fun managementStateWord(state:String)=when(state) {"sending"->Words.ManagementSending;"unknown"->Words.ManagementUncertain;"acknowledged"->Words.ManagementAcknowledged;"confirmed"->Words.ManagementVerified;"reviewed"->Words.ManagementReviewed;else->Words.Management}

@Composable fun McpActionReview(review:ReviewedMcpAction,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    AlertDialog(onDismissRequest=close,title={Text(label(mcpActionWord(review.request.action.name)),style=MaterialTheme.typography.titleMedium)},text={
        LazyColumn(Modifier.heightIn(max=440.dp).testTag("mcp-action-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);Text(review.request.name,style=MaterialTheme.typography.titleMedium)}
            if(review.request.action==McpAction.Toggle) item {Text(label(if(review.request.enabled==true) Words.McpEnable else Words.McpDisable))}
            item {Text(label(if(review.request.action==McpAction.Probe) Words.McpProbeEffect else Words.McpChangeEffect))}
        }
    },confirmButton={TextButton(confirm,enabled=enabled,modifier=Modifier.testTag("mcp-action-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close) {Text(label(Words.Cancel))}})
}

@Composable private fun managementReceiptTarget(receipt:ManagementOperation):String=if(HostProjectAction.receipt(receipt.action)) label(Words.HostProjects) else if(receipt.action=="MoaConfig") label(Words.HostMoa) else if(receipt.action=="AuxModel") label(if(receipt.target=="__reset__") Words.HostAuxReset else if(receipt.target.isEmpty()) Words.HostAuxAll else Words.HostAuxEdit)+(if(receipt.target.isNotEmpty() && receipt.target!="__reset__") " · "+receipt.target else "") else receipt.target

data class ReviewedManagementUncertainty(val key:String,val target:ManagementTarget,val instance:Boolean=false,val projectHome:String="")

@Composable fun ManagementReceiptCard(receipt:ManagementOperation,vm:ManagementViewModel?=null,s:ManagementState?=null,openCredentialPool:(()->Unit)?=null,openFallback:(()->Unit)?=null,openCustomEndpoints:(()->Unit)?=null,openChannels:(()->Unit)?=null,openRelay:((String)->Unit)?=null,reviewShared:((String,ManagementTarget)->Unit)?=null,openProjects:(()->Unit)?=null,inspectProject:((String)->Unit)?=null) {
    if(vm!=null && s!=null) {
        // Re-project even a stale raw row: sharing an inference credential does not share profile details.
        val visible=vm.visibleReceipts(s,listOf(receipt)).singleOrNull() ?: return
        if(visible.apiScope!=s.apiScope) {
            SharedManagementPendingCard(visible,vm,s,reviewShared)
            return
        }
    } else if(s!=null && receipt.apiScope!=s.apiScope) return
    var projects by remember(receipt.key) {mutableStateOf(false)}
    var providers by remember(receipt.key) {mutableStateOf(false)}
    var moa by remember(receipt.key) {mutableStateOf(false)}
    var hostModels by remember(receipt.key) {mutableStateOf(false)}
    var environment by remember(receipt.key) {mutableStateOf(false)}
    var plugins by remember(receipt.key) {mutableStateOf(false)}
    var gateway by remember(receipt.key) {mutableStateOf(false)}
    var skills by remember(receipt.key) {mutableStateOf(false)}
    var cron by remember(receipt.key) {mutableStateOf(false)}
    var result by remember(receipt.key) {mutableStateOf(false)}
    var review by remember(receipt.key,s?.apiScope,s?.url,s?.profile,s?.identity,s?.let {vm?.projectReceiptHome(it)}) {mutableStateOf<ReviewedManagementUncertainty?>(null)}
    Column(Modifier.fillMaxWidth().testTag("management-receipt-${receipt.key}"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(if(receipt.action in setOf("AuxModel","MoaConfig")) managementReceiptTarget(receipt) else label(mcpActionWord(receipt.action))+" · "+managementReceiptTarget(receipt),style=MaterialTheme.typography.titleSmall)
        Text(label(if(receipt.state=="acknowledged" && receipt.action=="PluginInstall") {if(runCatching {Json.parseToJsonElement(receipt.resultSummary).jsonObject["source_changed"]==JsonPrimitive(true)}.getOrDefault(false)) Words.PluginInstallSourceChanged else Words.PluginInstallReported} else if(receipt.state=="acknowledged" && receipt.action in setOf("HubRemove","HubUpdateAll")) Words.SkillHubMaintenanceAccepted else if(receipt.action=="HubInstall" && receipt.state=="acknowledged") Words.SkillHubInstallAccepted else managementStateWord(receipt.state)))
        Text(receipt.url+"\n"+receipt.profile+"\n"+receipt.actor,style=MaterialTheme.typography.bodySmall)
        Text(label(Words.ManagementRequestedAt)+" "+java.time.Instant.ofEpochMilli(receipt.createdAt).toString(),style=MaterialTheme.typography.bodySmall)
        if(receipt.action in setOf("ProviderOAuthStart","ProviderOAuthCancel","ProviderDisconnect") && receipt.resultSummary.isNotBlank()) ProviderOAuthReceipt(receipt,vm,s)
        else if(receipt.action in setOf("OAuthStart","OAuthCancel") && receipt.resultSummary.isNotBlank()) McpOAuthReceipt(receipt,vm,s)
        else if(receipt.resultSummary.isNotBlank()) TextButton({if(HostProjectAction.receipt(receipt.action) && inspectProject!=null) inspectProject(receipt.key) else result=true},Modifier.testTag("management-result-${receipt.key}")) {Text(label(Words.ManagementResult))}
        if(vm!=null && s!=null) {
            val enabled=!s.busy && s.identity!=null && vm.reviewTarget()!=null
            if((receipt.action !in setOf("CredentialPoolAdd","CredentialPoolRemove") || openCredentialPool!=null) && (receipt.action!="FallbackConfig" || openFallback!=null) && (!receipt.action.startsWith("CustomEndpoint") || openCustomEndpoints!=null) && (!receipt.action.startsWith("Channel") || openChannels!=null) && (receipt.action !in setOf("RelayUnlink","RelayLinkStart") || openRelay!=null)) TextButton({if(HostProjectAction.receipt(receipt.action)) {if(openProjects!=null) openProjects() else projects=true} else if(receipt.action in setOf("RelayUnlink","RelayLinkStart")) {openRelay?.invoke(receipt.key)} else if(receipt.action.startsWith("Channel")) {openChannels?.invoke()} else if(receipt.action.startsWith("CustomEndpoint")) {openCustomEndpoints?.invoke()} else if(receipt.action=="FallbackConfig") {openFallback?.invoke()} else if(receipt.action in setOf("CredentialPoolAdd","CredentialPoolRemove")) {openCredentialPool?.invoke()} else if(receipt.action in setOf("ProviderOAuthStart","ProviderOAuthCancel","ProviderDisconnect")) {providers=true} else if(receipt.action=="MoaConfig") {moa=true} else if(receipt.action in setOf("MainModel","AuxModel")) {hostModels=true} else if(receipt.action in setOf("EnvSave","EnvEmpty","EnvRemove")) {environment=true} else if(receipt.action in setOf("PluginInstall","PluginEnable","PluginDisable","PluginRemove","PluginUpdate","PluginSettings")) {plugins=true} else if(receipt.action in setOf("GatewayCronPause","GatewayCronResume","GatewayCronDelete")) {gateway=true} else if(receipt.action in setOf("HubInstall","HubRemove","HubUpdateAll")) {skills=true;vm.observeHubReceipt(receipt.key)} else if(receipt.action in setOf("SkillToggle","SkillLock")) {skills=true;vm.observeSkillReceipt(receipt.key)} else if(receipt.action=="CronCreate") {cron=true;vm.observeCronReceipt(receipt.key)} else vm.observeReceipt(receipt.key)},enabled=enabled,modifier=Modifier.testTag("management-observe-${receipt.key}")) {Text(label(Words.ManagementReadCurrent))}
            if(receipt.state=="unknown" && HostProjectAction.receipt(receipt.action) && receipt.projectHome!=vm.projectReceiptHome(s)) Text(label(Words.ProjectReceiptLocationChanged))
            if(receipt.state=="unknown") TextButton({vm.prepareManagementUncertainty(receipt.key)?.let {frozen->if(frozen.projectHome.isNotBlank() && reviewShared!=null) reviewShared(receipt.key,frozen.target) else review=frozen}},enabled=enabled && vm.prepareManagementUncertainty(receipt.key)!=null,modifier=Modifier.testTag("management-review-${receipt.key}")) {Text(label(Words.ManagementReview))}
        }
    }
    if(projects && vm!=null && s!=null) HostProjectsDialog(vm,s) {vm.closeHostProjects();projects=false}
    if(providers && vm!=null && s!=null) ProviderAccountsDialog(vm,s) {vm.closeProviderAccounts();providers=false}
    if(moa && vm!=null && s!=null) HostMoaDialog(vm,s) {vm.closeHostMoa();moa=false}
    if(hostModels && vm!=null && s!=null) HostModelsDialog(vm,s) {vm.closeHostModels();hostModels=false}
    if(environment && vm!=null && s!=null) HostEnvironmentDialog(vm,s,receipt.target) {vm.closeHostEnvironment();environment=false}
    if(plugins && vm!=null && s!=null) PluginInventoryDialog(vm,s) {vm.cancel();plugins=false}
    if(gateway && vm!=null && s!=null) GatewayAutomationDialog(vm,s) {vm.cancel();gateway=false}
    if(skills && vm!=null && s!=null) HostSkillsDialog(vm,s) {vm.cancel();skills=false}
    if(cron && vm!=null && s!=null) HostCronDialog(vm,s) {vm.cancel();cron=false}
    review?.let {frozen ->
        val project=frozen.projectHome.isNotBlank()
        var approved by remember(receipt.key,frozen) {mutableStateOf(false)}
        AlertDialog(onDismissRequest={review=null},title={Text(label(Words.ManagementReview))},text={
            LazyColumn(Modifier.heightIn(max=440.dp).testTag("management-own-review"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                item {Text(frozen.target.url);Text(frozen.target.profile);Text(managementReceiptTarget(receipt));Text(label(if(project) Words.ProjectReviewEffect else if(frozen.instance) Words.ManagementInstanceReviewEffect else Words.ManagementReviewEffect))}
                if(frozen.instance || project) item {Row {Checkbox(approved,{approved=it},Modifier.testTag(if(project) "management-project-approve" else "management-instance-approve"));Text(label(if(project) Words.ProjectReviewGrant else Words.ManagementInstanceGrant),Modifier.weight(1f))}}
            }
        },confirmButton={TextButton({vm!!.reviewReceipt(receipt.key,frozen.target,frozen.projectHome);review=null},enabled=s?.busy!=true && (!(frozen.instance || project) || approved) && vm?.managementUncertaintyCurrent(frozen)==true,modifier=Modifier.heightIn(min=48.dp).testTag("management-review-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({review=null},Modifier.heightIn(min=48.dp)) {Text(label(Words.Cancel))}})
    }
    if(result) AlertDialog(onDismissRequest={result=false},title={Text(managementReceiptTarget(receipt))},text={
        val parsed=remember(receipt.resultSummary) {runCatching {Json.parseToJsonElement(receipt.resultSummary).jsonObject}.getOrDefault(JsonObject(emptyMap()))}
        LazyColumn(Modifier.heightIn(max=440.dp).testTag("management-result-list"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {Text(label(if(receipt.state=="acknowledged" && receipt.action in setOf("HubRemove","HubUpdateAll")) Words.SkillHubMaintenanceAccepted else if(receipt.action=="HubInstall" && receipt.state=="acknowledged") Words.SkillHubInstallAccepted else managementStateWord(receipt.state)))}
            if(HostProjectAction.receipt(receipt.action)) item {ProjectReceiptSummary(parsed)}
            else if(receipt.action=="RelayLinkStart") item {Text(label(if(parsed["code_issued"]==JsonPrimitive(false)) Words.RelayCodeNotIssued else Words.RelayCodeIssued));Text(label(relayCodeStatusWord(parsed.text("status"))));Text(label(Words.RelayCodeReceipt))}
            else if(receipt.action=="RelayUnlink") item {Text(label(Words.RelayUnlinkAcknowledged));Text(label(Words.RelayUnlinkReceipt))}
            else if(receipt.action.startsWith("Channel")) item {HostChannelReceipt(receipt)}
            else if(receipt.action.startsWith("CustomEndpoint")) item {if(receipt.action=="CustomEndpointProbe") EndpointProbeReceipt(receipt) else CustomEndpointReceipt()}
            else if(receipt.action=="FallbackConfig") item {HostFallbackReceipt()}
            else if(receipt.action in setOf("CredentialPoolAdd","CredentialPoolRemove")) item {CredentialPoolReceiptSummary(receipt)}
            else if(receipt.action=="MoaConfig") item {Text(label(if(parsed["normalized"]==JsonPrimitive(true)) Words.HostMoaNormalized else Words.HostMoaAcknowledged));Text(label(Words.HostMainRequested));Text(label(Words.HostMoaDefault)+" · "+parsed.text("requested_default"));Text(label(Words.HostMoaActive)+" · "+parsed.text("requested_active"));Text(label(Words.HostMoaRequestedHash));Text(parsed.text("requested_sha256"));Text(label(Words.HostMainReported));Text(label(Words.HostMoaDefault)+" · "+parsed.text("reported_default"));Text(label(Words.HostMoaActive)+" · "+parsed.text("reported_active"));Text(label(Words.HostMoaReportedHash));Text(parsed.text("reported_sha256"));for(key in listOf("added","changed","removed")) {(parsed[key] as? JsonArray)?.takeIf {it.isNotEmpty()}?.let {Text(label(when(key) {"added"->Words.HostMoaAdded;"removed"->Words.HostMoaRemoved;else->Words.HostMoaChanged}));it.forEach {v->Text(v.jsonPrimitive.content)}}};Text(label(Words.HostMoaReceipt))}
            else if(receipt.action=="AuxModel") item {Text(label(if(parsed["selection_warning_required"]==JsonPrimitive(true)) Words.HostMainWarningRequired else Words.HostAuxAcknowledged));Text(label(if(parsed["reset"]==JsonPrimitive(true)) Words.HostAuxReset else Words.HostAuxEdit));Text(parsed.text("requested_provider"));Text(parsed.text("requested_model"));HostAuxEffort.entries.singleOrNull {it.name==parsed.text("reasoning_choice")}?.let {Text(label(hostAuxEffortWord(it)))};(parsed["requested_tasks"] as? JsonArray)?.forEach {Text(it.jsonPrimitive.content)};if(parsed["reported_tasks"]!=null && parsed["reported_tasks"]!=parsed["requested_tasks"]) {Text(label(Words.HostAuxReportedTasks));(parsed["reported_tasks"] as? JsonArray)?.forEach {Text(it.jsonPrimitive.content)}};Text(label(Words.HostAuxReceipt))}
            else if(receipt.action=="MainModel") item {Text(label(if(parsed["selection_warning_required"]==JsonPrimitive(true)) Words.HostMainWarningRequired else if(parsed["reported_provider"]!=parsed["requested_provider"] || parsed["reported_model"]!=parsed["requested_model"]) Words.HostMainNormalized else Words.HostMainAcknowledged));Text(label(Words.HostMainRequested));Text(parsed.text("requested_provider"));Text(parsed.text("requested_model"));if(parsed["reported_assigned"]==JsonPrimitive(true)) {Text(label(Words.HostMainReported));Text(parsed.text("reported_provider"));Text(parsed.text("reported_model"))};Text(label(Words.HostMainReceipt))}
            else if(receipt.action in setOf("EnvSave","EnvEmpty","EnvRemove")) item {Text(label(Words.HostEnvAcknowledged));Text(parsed.text("env_key"));if(receipt.action=="EnvEmpty") Text(label(Words.HostEnvEmptyEffect));Text(label(Words.HostEnvReceipt))}
            else if(receipt.action=="PluginSettings") item {Text(label(Words.PluginSettingsSaved));Text(parsed.text("setting_key"));Text(parsed.text("setting_type"));Text(label(Words.PluginSettingsObservation));if(parsed["nested_readback_unavailable"]==JsonPrimitive(true)) Text(label(Words.PluginSettingsNested))}
            else if(receipt.action=="PluginUpdate") item {PluginUpdateReceipt(parsed)}
            else if(receipt.action=="PluginRemove") item {PluginRemovalReceipt(parsed)}
            else if(receipt.action in setOf("PluginEnable","PluginDisable")) item {Text(label(Words.PluginToggleReported));Text(label(if(parsed["requested_enable"]==JsonPrimitive(true)) Words.PluginEnable else Words.PluginDisable));Text(label(Words.PluginToggleObservation));Text(label(if(parsed["requested_enable"]==JsonPrimitive(true)) Words.PluginEnableEffect else Words.PluginDisableEffect))}
            else if(receipt.action=="PluginInstall") item {PluginInstallReceiptResult(parsed)}
            else if(receipt.action=="Probe") {
                item {Text(label(if(parsed["ok"]?.jsonPrimitive?.booleanOrNull==true) Words.McpProbeReady else Words.McpProbeFailed));Text(label(Words.McpToolCount)+" "+parsed.text("tool_count"))}
                items((parsed["tools"] as? JsonArray).orEmpty()) {Text((it as? JsonPrimitive)?.contentOrNull.orEmpty())}
            } else if(receipt.action in setOf("GatewayCronPause","GatewayCronResume","GatewayCronDelete")) item {Text(label(if(receipt.state=="confirmed") Words.GatewayCronChanged else Words.GatewayCronAccepted));Text(parsed.text("job_id"));Text(label(Words.GatewayReceiptEffect))}
            else if(receipt.action=="CronCreate") item {Text(label(Words.HostCronCreatedPaused));Text(parsed.text("job_id"))}
            else if(receipt.action in setOf("HubRemove","HubUpdateAll")) item {Text(label(Words.SkillHubMaintenanceAccepted))}
            else if(receipt.action=="HubInstall") item {Text(label(Words.SkillHubInstallAccepted))}
            else if(receipt.action=="CatalogInstall") item {Text(label(Words.McpCatalogAccepted));if(parsed["background"]?.jsonPrimitive?.booleanOrNull==true) Text(label(Words.McpCatalogBackground))}
            else if(receipt.action=="Create") item {Text(label(Words.McpCreated));Text(parsed.text("transport")+" · "+parsed.text("auth"))}
            else if(receipt.action=="SkillLock") item {Text(label(if(parsed["locked"]?.jsonPrimitive?.booleanOrNull==true) Words.SkillLocked else Words.SkillUnlocked))}
            else if(parsed.containsKey("enabled")) item {Text(label(if(parsed["enabled"]?.jsonPrimitive?.booleanOrNull==true) Words.Enabled else Words.Disabled))}
        }
    },confirmButton={TextButton({result=false},Modifier.testTag("management-result-close")) {Text(label(Words.Close))}})
}

/** Only generic state is rendered here. Confirmation is hosted above the disposable lazy row. */
@Composable private fun SharedManagementPendingCard(receipt:ManagementOperation,vm:ManagementViewModel,s:ManagementState,reviewShared:((String,ManagementTarget)->Unit)?) {
    Column(Modifier.fillMaxWidth().testTag("management-shared-"+receipt.key),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        val instance=receipt.action==ManagementReceiptVisibility.SharedInstancePending
        val project=receipt.projectHome.isNotBlank() || receipt.action==ManagementReceiptVisibility.SharedProjectPending
        Text(label(if(project) Words.ProjectSharedPending else if(instance) Words.ManagementInstancePending else Words.ManagementSharedPending),style=MaterialTheme.typography.titleSmall)
        Text(label(managementStateWord(receipt.state)))
        Text(label(if(project && receipt.projectHome!=vm.projectReceiptHome(s)) Words.ProjectReceiptLocationChanged else if(project) Words.ProjectSharedPrivate else if(instance) Words.ManagementInstancePrivate else Words.ManagementSharedPrivate),style=MaterialTheme.typography.bodySmall)
        if(receipt.state=="unknown") TextButton({vm.reviewTarget()?.let {reviewShared?.invoke(receipt.key,it)}},
            enabled=!s.busy && vm.prepareManagementUncertainty(receipt.key)!=null && reviewShared!=null,
            modifier=Modifier.testTag("management-shared-review-"+receipt.key)) {Text(label(Words.ManagementReview))}
    }
}

@Composable fun SharedManagementUncertaintyReview(review:ReviewedManagementUncertainty,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    var approved by remember(review) {mutableStateOf(false)}
    val project=review.projectHome.isNotBlank()
    AlertDialog(onDismissRequest=close,title={Text(label(Words.ManagementReview))},text={
        LazyColumn(Modifier.heightIn(max=440.dp).testTag("management-shared-review"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item {Text(review.target.url)}
            item {Text(review.target.profile)}
            item {Text(review.target.actor)}
            item {Text(label(if(project) Words.ProjectReviewEffect else if(review.instance) Words.ManagementInstanceReviewEffect else Words.ManagementSharedReviewEffect))}
            if(review.instance || project) item {Row {Checkbox(approved,{approved=it},Modifier.testTag(if(project) "management-project-approve" else "management-instance-approve"));Text(label(if(project) Words.ProjectReviewGrant else Words.ManagementInstanceGrant),Modifier.weight(1f))}}
        }
    },confirmButton={TextButton(confirm,enabled=enabled && (!(review.instance || project) || approved),modifier=Modifier.testTag("management-shared-review-confirm")) {Text(label(Words.Confirm))}},
        dismissButton={TextButton(close) {Text(label(Words.Cancel))}})
}

/** Durable project receipts contain no original names, paths, descriptions or replay arguments. */
@Composable private fun ProjectReceiptSummary(summary:JsonObject) {
    Text(label(Words.ProjectReceiptPrivate))
    if(summary["host_acknowledged"]==JsonPrimitive(true)) {
        Text(label(Words.ProjectAcknowledged))
        Text(label(if(summary["requested_effect_reported"]==JsonPrimitive(true)) Words.ProjectEffectReported else Words.ProjectEffectUnreported))
        Text(label(if(summary["configuration_observed"]==JsonPrimitive(true)) Words.ProjectConfigurationObserved else Words.ProjectConfigurationUnchecked))
        if(summary["path_mapping_unverified"]==JsonPrimitive(true)) Text(label(Words.ProjectPathMapping))
    }
}

/** Hosted above the lazy receipt row so Android window resizing cannot discard its state. */
@Composable fun ProjectReceiptDetailsDialog(receipt:ManagementOperation,close:()->Unit) {
    val parsed=remember(receipt.resultSummary) {runCatching {Json.parseToJsonElement(receipt.resultSummary).jsonObject}.getOrDefault(JsonObject(emptyMap()))}
    AlertDialog(onDismissRequest=close,title={Text(label(mcpActionWord(receipt.action)))},text={
        LazyColumn(Modifier.heightIn(max=440.dp).testTag("management-result-list"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {Text(receipt.url);Text(receipt.profile);Text(label(managementStateWord(receipt.state)));ProjectReceiptSummary(parsed)}
        }
    },confirmButton={TextButton(close,Modifier.heightIn(min=48.dp).testTag("management-result-close")) {Text(label(Words.Close))}})
}
