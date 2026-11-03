package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.*

@Composable fun SkillHubDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var query by remember(s.apiScope,s.url,s.profile) {mutableStateOf("")}
    var source by remember(s.apiScope,s.url,s.profile) {mutableStateOf("all")}
    var picker by remember {mutableStateOf(false)}
    var mode by remember(s.apiScope,s.url,s.profile) {mutableStateOf("official")}
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val canRead=!s.busy && s.identity!=null && vm.reviewTarget()!=null
    val sources=s.hub.sources?.sources.orEmpty().filter {it.id!="all"}
    val selected=sources.singleOrNull {it.id==source}
    val sourceAllowed=source=="all" || selected!=null && selected.available!=false && selected.searchable!=false && selected.rateLimited!=true
    val queryValid=query.isNotBlank() && query.length<=256 && query.none(Char::isISOControl)
    val rows=when(mode) {"search"->s.hub.search?.results.orEmpty();"featured"->s.hub.sources?.featured.orEmpty();else->s.hub.official.orEmpty()}
    val listKnown=when(mode) {"search"->s.hub.search!=null;"featured"->s.hub.sources!=null;else->s.hub.official!=null}
    val installed=if(mode=="search") s.hub.search?.installed else s.hub.sources?.installed
    AlertDialog(onDismissRequest=close,title={Text(label(Words.SkillHub))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("skill-hub-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(s.url);Text(s.profile);Text(label(Words.SkillHubBoundary));TextButton(vm::loadSkillHub,enabled=canRead,modifier=Modifier.testTag("skill-hub-refresh")) {Text(label(Words.Refresh))}}
            if(s.identity==null) item {Text(label(Words.ManagementVerify))}
            if(s.busy) item {QuietProgress();TextButton(vm::cancel) {Text(label(Words.Cancel))}}
            item {Row {TextButton({mode="official"},enabled=canRead && s.hub.official!=null,modifier=Modifier.testTag("skill-hub-official")) {Text(label(Words.SkillHubOfficial))};TextButton({mode="featured"},enabled=canRead && s.hub.sources!=null,modifier=Modifier.testTag("skill-hub-featured")) {Text(label(Words.SkillHubFeatured))};TextButton({mode="search"},enabled=canRead,modifier=Modifier.testTag("skill-hub-search-tab")) {Text(label(Words.Search))}}}
            if(s.hub.loaded && !listKnown && mode!="search") item {Text(label(Words.ManagementUnsupported))}
            if(mode=="search") {
                item {OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().testTag("skill-hub-query"),label={Text(label(Words.Search))},singleLine=true,enabled=canRead);if(query.isNotEmpty() && !queryValid) Text(label(Words.SkillHubQueryInvalid))}
                item {TextButton({picker=true},enabled=canRead && sources.isNotEmpty(),modifier=Modifier.testTag("skill-hub-source")) {Text(if(source=="all") label(Words.SkillHubAllSources) else selected?.label?.ifBlank {source} ?: source)};if(sources.isEmpty()) Text(label(Words.SkillHubSourcesUnavailable));if(!sourceAllowed) Text(label(Words.SkillHubSourceUnavailable))}
                item {Text(label(Words.SkillHubSearchLimit))}
                item {Button({focus.clearFocus();keyboard?.hide();vm.searchSkillHub(query,source)},enabled=canRead && queryValid && sourceAllowed,modifier=Modifier.testTag("skill-hub-search")) {Text(label(Words.Search))}}
                if(s.hub.search?.timedOut?.isNotEmpty()==true) item {Text(label(Words.SkillHubTimedOut));Text(s.hub.search.timedOut.joinToString("\n"))}
            }
            if(s.hub.loaded && listKnown && rows.isEmpty()) item {Text(label(Words.EmptyInventory))}
            itemsIndexed(rows,key={index,_->index}) {index,skill ->Column {
                Text(skill.name,style=MaterialTheme.typography.titleMedium);Text(skill.description);Text(skill.source+" · "+skill.trust)
                if(skill.category.isNotBlank()) Text(skill.category)
                if(skill.tags.isNotEmpty()) Text(skill.tags.joinToString(" · "))
                if(skill.installed==true || installed?.containsKey(skill.identifier)==true || installed?.containsKey(skill.canonicalId)==true) Text(label(Words.SkillHubInstalled))
                TextButton({vm.previewHubSkill(skill)},enabled=canRead && skill.addressable,modifier=Modifier.testTag("skill-hub-preview-$index")) {Text(label(Words.SkillHubPreview))}
                if(!skill.addressable) Text(label(Words.SkillHubIdentifierUnsupported))
            }}
            s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
        }
    },confirmButton={TextButton(close,Modifier.testTag("skill-hub-close")) {Text(label(Words.Close))}})
    if(picker) AlertDialog(onDismissRequest={picker=false},title={Text(label(Words.SkillHubSources))},text={
        LazyColumn(Modifier.heightIn(max=440.dp)) {
            item {TextButton({source="all";picker=false},Modifier.testTag("skill-hub-source-all")) {Text(label(Words.SkillHubAllSources))}}
            items(sources,key={it.id}) {entry ->Column {
                TextButton({source=entry.id;picker=false},enabled=entry.available!=false && entry.searchable!=false && entry.rateLimited!=true,modifier=Modifier.testTag("skill-hub-source-${entry.id}")) {Text(entry.label.ifBlank {entry.id})}
                Text(entry.id);if(entry.available==false || entry.searchable==false || entry.rateLimited==true) Text(label(Words.SkillHubSourceUnavailable))
            }}
        }
    },confirmButton={TextButton({picker=false}) {Text(label(Words.Close))}})
    s.hub.preview?.let {preview ->SkillHubPreviewDialog(vm,s,preview)}
    s.hub.scan?.let {scan ->SkillHubScanDialog(scan,vm::closeHubScan)}
}

@Composable private fun SkillHubPreviewDialog(vm:ManagementViewModel,s:ManagementState,preview:HubPreview) {
    var query by remember(preview) {mutableStateOf(false)}
    val canRead=!s.busy && s.identity!=null
    AlertDialog(onDismissRequest=vm::closeHubPreview,title={Text(preview.skill.name)},text={
        LazyColumn(Modifier.heightIn(max=500.dp).testTag("skill-hub-preview-view"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(s.url);Text(s.profile);Text(preview.skill.source+" · "+preview.skill.trust);Text(preview.skill.displayIdentifier(query))
                if(preview.skill.displayIdentifier(true)!=preview.skill.displayIdentifier(false)) TextButton({query=!query}) {Text(label(if(query) Words.McpHideQuery else Words.McpShowQuery))}}
            if(preview.skill.repo.isNotBlank()) item {Text(preview.skill.displayRepo().ifBlank {label(Words.Unknown)})}
            item {Text(label(Words.SkillHubPreviewHelp));Text(preview.skill.description)}
            item {Text(label(Words.SkillHubScanEffect));TextButton(vm::scanHubSkill,enabled=canRead && preview.skill.addressable,modifier=Modifier.testTag("skill-hub-scan")) {Text(label(Words.SkillHubScan))}}
            if(s.busy) item {QuietProgress();TextButton(vm::closeHubPreview) {Text(label(Words.Cancel))}}
            item {Text(label(Words.SkillContent),style=MaterialTheme.typography.titleSmall);if(preview.content.isBlank()) Text(label(Words.SkillHubPreviewEmpty)) else Text(preview.content)}
            item {Text(label(Words.SkillHubFiles),style=MaterialTheme.typography.titleSmall)}
            items(preview.files) {Text(it)}
            s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
        }
    },confirmButton={TextButton(vm::closeHubPreview,Modifier.testTag("skill-hub-preview-close")) {Text(label(Words.Close))}})
}

@Composable private fun SkillHubScanDialog(scan:HubScan,close:()->Unit) {
    AlertDialog(onDismissRequest=close,title={Text(label(Words.SkillHubScan))},text={
        LazyColumn(Modifier.heightIn(max=500.dp).testTag("skill-hub-scan-view"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(scan.name);Text(label(Words.SkillHubHostEvidence));Text(label(Words.SkillHubTrust)+": "+scan.trust);Text(label(Words.SkillHubVerdict)+": "+scan.verdict);Text(label(Words.SkillHubPolicy)+": "+scan.policy);Text(scan.reason)}
            item {Text(label(Words.SkillHubFindings)+" "+scan.findings.size)}
            items(scan.findings) {HubFindingView(it)}
            item {Text(label(Words.SkillHubTier1));Text(label(when(scan.tier1Passed) {true->Words.SkillHubTier1Passed;false->Words.SkillHubTier1Findings;null->Words.SkillHubTier1Unavailable}))}
            if(scan.incomplete.isNotEmpty()) item {Text(label(Words.SkillHubIncomplete));Text(scan.incomplete.joinToString("\n"))}
            items(scan.tier1) {HubFindingView(it)}
        }
    },confirmButton={TextButton(close,Modifier.testTag("skill-hub-scan-close")) {Text(label(Words.Close))}})
}
@Composable private fun HubFindingView(finding:HubFinding) {Column {
    Text(finding.severity+" · "+finding.category)
    val file=finding.displayFile();if(file.isNotBlank()) Text(file+(finding.line?.let {":$it"} ?: ""))
    Text(if(finding.sensitive) label(Words.SkillHubSensitiveFinding) else finding.description)
}}
