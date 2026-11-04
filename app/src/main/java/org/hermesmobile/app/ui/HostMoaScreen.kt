package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.data.*

@Composable fun HostMoaDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var read by remember(s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedHostMoaRead?>(null)}
    var review by remember(s.moa,s.apiScope,s.url,s.profile) {mutableStateOf<ReviewedHostMoa?>(null)}
    var draft by remember(s.moa) {mutableStateOf(s.moa?.config)}
    var selected by remember(s.moa) {mutableStateOf(s.moa?.config?.defaultPreset.orEmpty())}
    var newName by remember(s.moa) {mutableStateOf("")};var editing by remember(s.moa) {mutableStateOf(false)}
    val receiptRows by vm.receipts.collectAsStateWithLifecycle();val receipts=vm.visibleReceipts(s,receiptRows);val pending=receipts.any {it.state in setOf("sending","unknown")}
    val editable=!s.busy && !pending && s.moa?.config?.writable==true
    val candidate=remember(s.moa,draft) {s.moa?.let {snapshot->draft?.let {runCatching {HostMoaRequest(snapshot,it)}.getOrNull()}}}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostMoa))},text={LazyColumn(Modifier.heightIn(max=520.dp).testTag("host-moa-list"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {Text(s.url);Text(s.profile);Text(label(Words.HostMoaDraft));TextButton({read=vm.prepareHostMoaRead()},enabled=!s.busy && s.identity!=null && GatewayCronInventory.supportedProfile(s.profile),modifier=Modifier.testTag("host-moa-read")) {Text(label(Words.HostMoaRead))}}
        if(!GatewayCronInventory.supportedProfile(s.profile)) item {Text(label(Words.PluginProfileUnsupported))}
        if(draft?.writable==true && candidate==null) item {Text(label(Words.ManagementInvalid))}
        if(s.busy) item {QuietProgress();TextButton(vm::cancel) {Text(label(Words.Cancel))}}
        s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}};s.notice?.let {item {Text(label(it))}}
        if(pending) item {Text(label(Words.ManagementOutcomePending))}
        draft?.let {cfg->
            item {Text(label(Words.HostMoaDefault)+" · "+cfg.defaultPreset);Text(label(Words.HostMoaActive)+" · "+cfg.activePreset.ifEmpty {label(Words.HostMoaInactive)});Text(label(Words.HostMoaPrivacy)+" · "+cfg.privacy);Text(label(Words.HostMoaPrivacyEffect));if(!cfg.writable) Text(label(Words.HostMoaUnrepresentable))}
            items(cfg.presets.keys.toList(),key={"preset:$it"}) {name->TextButton({selected=name},Modifier.testTag("host-moa-select-$name")) {Text((if(selected==name) "✓ " else "")+name)}}
            val preset=cfg.presets[selected]
            if(preset!=null) {
                item {Text(selected,style=MaterialTheme.typography.titleMedium)
                    TextButton({editing=true},enabled=editable,modifier=Modifier.testTag("host-moa-edit")) {Text(label(Words.HostMoaEdit))}
                    TextButton({draft=cfg.copy(defaultPreset=selected)},enabled=editable,modifier=Modifier.testTag("host-moa-default")) {Text(label(Words.HostMoaDefault))}
                    TextButton({draft=cfg.copy(activePreset=selected)},enabled=editable,modifier=Modifier.testTag("host-moa-active")) {Text(label(Words.HostMoaActive))}
                    TextButton({draft=cfg.copy(activePreset="")},enabled=editable,modifier=Modifier.testTag("host-moa-inactive")) {Text(label(Words.HostMoaInactive))}
                    if(cfg.presets.size>1) TextButton({val next=cfg.without(selected);draft=next;selected=next.defaultPreset},enabled=editable,modifier=Modifier.testTag("host-moa-remove")) {Text(label(Words.HostMoaRemove))} else Text(label(Words.HostMoaAtLeastOne))
                    OutlinedTextField(newName,{if(it.length<=256) newName=it},Modifier.fillMaxWidth().testTag("host-moa-new-name"),label={Text(label(Words.HostMoaNewName))},singleLine=true)
                    TextButton({draft=cfg.copy(presets=cfg.presets+(newName to preset));selected=newName;newName=""},enabled=editable && cfg.presets.size<128 && cfg.presets.values.sumOf {it.references.size}+preset.references.size<=4096 && HostMoaLimits.id(newName) && newName !in cfg.presets,modifier=Modifier.testTag("host-moa-duplicate")) {Text(label(Words.HostMoaDuplicate))}
                    if(cfg.presets.size>=128 || cfg.presets.values.sumOf {it.references.size}+preset.references.size>4096) Text(label(Words.HostMoaLimit)) else if(newName.isNotEmpty() && (!HostMoaLimits.id(newName) || newName in cfg.presets)) Text(label(Words.ManagementInvalid))
                }
                item {HostMoaPresetDetails(preset)}
            }
        }
    }},confirmButton={TextButton({draft?.let {review=vm.prepareHostMoa(it)}},enabled=editable && candidate!=null,modifier=Modifier.testTag("host-moa-review-open")) {Text(label(Words.HostMoaReview))}},dismissButton={TextButton(close,Modifier.testTag("host-moa-close")) {Text(label(Words.Close))}})
    read?.let {frozen->AlertDialog(onDismissRequest={read=null},title={Text(label(Words.HostMoaRead))},text={LazyColumn(Modifier.heightIn(max=480.dp)) {item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor);Text(label(Words.HostMoaReadEffect));Text(label(Words.HostModelsReadEffect))}}},confirmButton={TextButton({read=null;vm.loadHostMoa(frozen)},enabled=!s.busy,modifier=Modifier.testTag("host-moa-read-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({read=null},Modifier.testTag("host-moa-read-cancel")) {Text(label(Words.Cancel))}})}
    val cfg=draft;val snapshot=s.moa
    if(editing && cfg!=null && snapshot!=null) cfg.presets[selected]?.let {preset->HostMoaPresetEditor(selected,preset,snapshot.catalog,minOf(64,4096-cfg.presets.filterKeys {it!=selected}.values.sumOf {it.references.size}),snapshot.config.presets[selected]?.aggregator?.reasoning==null,{editing=false}) {next->draft=cfg.copy(presets=cfg.presets+(selected to next));editing=false}}
    review?.let {frozen->HostMoaReview(frozen,!s.busy,{review=null}) {review=null;vm.saveHostMoa(frozen)}}
}
@Composable private fun HostMoaSlotDetails(slot:HostMoaSlot) {Text(slot.route.provider);Text(slot.route.model);Text(label(Words.HostModelsReasoning)+" · "+(slot.reasoning ?: label(Words.HostMoaReasoningDefault)))}
@Composable private fun HostMoaPresetDetails(p:HostMoaPreset) {
    Text(label(if(p.enabled) Words.Enabled else Words.Disabled));Text(label(Words.HostMoaReferences))
    p.references.forEachIndexed {index,slot->Text("${index+1} · "+label(if(slot.enabled) Words.Enabled else Words.Disabled));HostMoaSlotDetails(slot)}
    Text(label(Words.HostMoaAggregator));HostMoaSlotDetails(p.aggregator)
    Text(label(Words.HostMoaReferenceTemperature)+" · "+p.referenceTemperature.orDefault());Text(label(Words.HostMoaAggregatorTemperature)+" · "+p.aggregatorTemperature.orDefault());Text(label(Words.HostMoaTimeout)+" · "+p.referenceTimeout.orDefault());Text(label(Words.HostMoaCadence)+" · "+p.fanout);Text(label(if(p.policy=="loud") Words.HostMoaLoud else Words.HostMoaSilent))
}
@Composable private fun Double?.orDefault():String=this?.toString() ?: label(Words.HostModelsUnspecified)

@Composable private fun HostMoaPresetEditor(name:String,initial:HostMoaPreset,catalog:HostModelInventory,maxReferences:Int,allowAggregatorDefault:Boolean,close:()->Unit,apply:(HostMoaPreset)->Unit) {
    var refs by remember(initial) {mutableStateOf(initial.references)};var aggregator by remember(initial) {mutableStateOf(initial.aggregator)}
    var enabled by remember(initial) {mutableStateOf(initial.enabled)};var rt by remember(initial) {mutableStateOf(initial.referenceTemperature?.toString().orEmpty())};var at by remember(initial) {mutableStateOf(initial.aggregatorTemperature?.toString().orEmpty())};var timeout by remember(initial) {mutableStateOf(initial.referenceTimeout?.toString().orEmpty())}
    var cadence by remember(initial) {mutableStateOf(if(initial.fanout.startsWith("every_n:")) "every_n" else initial.fanout)};var n by remember(initial) {mutableStateOf(initial.fanout.substringAfter(":","2"))};var policy by remember(initial) {mutableStateOf(initial.policy)}
    var slot by remember {mutableStateOf<Int?>(null)}
    val fanout=if(cadence=="every_n") "every_n:$n" else cadence
    val valid=listOf(rt,at).all {it.isBlank() || HostMoaLimits.number(it)!=null} && (timeout.isBlank() || HostMoaLimits.number(timeout,true)!=null) && HostMoaLimits.cadence(fanout)
    AlertDialog(onDismissRequest=close,title={Text(name)},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("host-moa-editor"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {Row {Switch(enabled,{enabled=it},Modifier.testTag("host-moa-preset-enabled"));Text(label(Words.Enabled))};Text(label(Words.HostMoaReferences))}
        itemsIndexed(refs,key={index,_->index}) {index,row->Column {
            HostMoaSlotDetails(row);Row {Switch(row.enabled,{on->refs=refs.mapIndexed {i,v->if(i==index) v.copy(enabled=on) else v}},Modifier.testTag("host-moa-ref-enabled-$index"));Text(label(Words.Enabled))}
            TextButton({slot=index},Modifier.testTag("host-moa-ref-edit-$index")) {Text(label(Words.HostMoaEdit))}
            if(index>0) TextButton({refs=refs.toMutableList().also {val previous=it[index-1];it[index-1]=it[index];it[index]=previous}},Modifier.testTag("host-moa-ref-up-$index")) {Text(label(Words.HostMoaMoveUp))}
            if(index<refs.lastIndex) TextButton({refs=refs.toMutableList().also {val next=it[index+1];it[index+1]=it[index];it[index]=next}},Modifier.testTag("host-moa-ref-down-$index")) {Text(label(Words.HostMoaMoveDown))}
            if(refs.size>1) TextButton({refs=refs.filterIndexed {i,_->i!=index}},Modifier.testTag("host-moa-ref-remove-$index")) {Text(label(Words.HostMoaRemoveReference))}
            if(refs.size<maxReferences) TextButton({refs=refs+row},Modifier.testTag("host-moa-ref-copy-$index")) {Text(label(Words.HostMoaCopyReference))}
        }}
        item {if(refs.size==1) Text(label(Words.HostMoaAtLeastOne));if(refs.size<maxReferences) TextButton({slot=-2},Modifier.testTag("host-moa-add-reference")) {Text(label(Words.HostMoaAddReference))} else Text(label(Words.HostMoaLimit));Text(label(Words.HostMoaAggregator));HostMoaSlotDetails(aggregator);TextButton({slot=-1},Modifier.testTag("host-moa-aggregator-edit")) {Text(label(Words.HostMoaEdit))}}
        item {OutlinedTextField(rt,{if(it.length<=64) rt=it},Modifier.fillMaxWidth().testTag("host-moa-reference-temperature"),label={Text(label(Words.HostMoaReferenceTemperature))},singleLine=true);OutlinedTextField(at,{if(it.length<=64) at=it},Modifier.fillMaxWidth().testTag("host-moa-aggregator-temperature"),label={Text(label(Words.HostMoaAggregatorTemperature))},singleLine=true);OutlinedTextField(timeout,{if(it.length<=64) timeout=it},Modifier.fillMaxWidth().testTag("host-moa-timeout"),label={Text(label(Words.HostMoaTimeout))},singleLine=true)}
        item {Text(label(Words.HostMoaCadence));listOf("user_turn" to Words.HostMoaPerTurn,"per_iteration" to Words.HostMoaPerIteration,"every_n" to Words.HostMoaEveryN).forEach {(v,w)->Row {RadioButton(cadence==v,{cadence=v},Modifier.testTag("host-moa-cadence-$v"));Text(label(w),Modifier.weight(1f))}};if(cadence=="every_n") OutlinedTextField(n,{if(it.length<=128) n=it},Modifier.fillMaxWidth().testTag("host-moa-n"),label={Text(label(Words.HostMoaEveryN))},singleLine=true)}
        item {Text(label(Words.HostMoaFailurePolicy));listOf("loud" to Words.HostMoaLoud,"silent" to Words.HostMoaSilent).forEach {(v,w)->Row {RadioButton(policy==v,{policy=v},Modifier.testTag("host-moa-policy-$v"));Text(label(w),Modifier.weight(1f))}};if(!valid) Text(label(Words.ManagementInvalid))}
    }},confirmButton={TextButton({apply(HostMoaPreset(refs,aggregator,enabled,HostMoaLimits.number(rt),HostMoaLimits.number(at),HostMoaLimits.number(timeout,true),policy,fanout))},enabled=valid,modifier=Modifier.testTag("host-moa-editor-apply")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("host-moa-editor-cancel")) {Text(label(Words.Cancel))}})
    slot?.let {index->HostMoaSlotEditor(if(index==-1) aggregator else refs.getOrNull(index),catalog,index!=-1 || allowAggregatorDefault,{slot=null}) {value->if(index==-1) aggregator=value.copy(enabled=true) else if(index==-2) refs=refs+value else refs=refs.mapIndexed {i,v->if(i==index) value.copy(enabled=v.enabled) else v};slot=null}}
}
@Composable private fun HostMoaSlotEditor(initial:HostMoaSlot?,catalog:HostModelInventory,allowDefault:Boolean,close:()->Unit,apply:(HostMoaSlot)->Unit) {
    var route by remember(initial) {mutableStateOf(initial?.route)};var effort by remember(initial) {mutableStateOf(initial?.reasoning)};var picking by remember {mutableStateOf(initial==null)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostMoaEdit))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("host-moa-slot"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {Text(route?.provider.orEmpty());Text(route?.model.orEmpty());if(route==null) Text(label(Words.ManagementInvalid));TextButton({picking=true},Modifier.testTag("host-moa-slot-pick")) {Text(label(Words.HostModelsCatalog))};Text(label(Words.HostModelsReasoning));Row {RadioButton(effort==null,{effort=null},enabled=allowDefault,modifier=Modifier.testTag("host-moa-effort-default"));Text(label(Words.HostMoaReasoningDefault),Modifier.weight(1f))};if(!allowDefault) Text(label(Words.HostMoaAggClearUnsupported))}
        items(HostAuxEffort.entries.filter {it.wire!=null},key={it.name}) {e->Row {RadioButton(effort==e.wire,{effort=e.wire},Modifier.testTag("host-moa-effort-${e.name}"));Text(label(hostAuxEffortWord(e)),Modifier.weight(1f))}}
    }},confirmButton={TextButton({route?.let {apply(HostMoaSlot(it,effort))}},enabled=route!=null && (effort!=null || allowDefault),modifier=Modifier.testTag("host-moa-slot-apply")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("host-moa-slot-cancel")) {Text(label(Words.Cancel))}})
    if(picking) HostMoaRoutePicker(catalog,{picking=false}) {value->route=value;picking=false}
}
@Composable private fun HostMoaRoutePicker(catalog:HostModelInventory,close:()->Unit,choose:(HostModelRoute)->Unit) {
    var provider by remember {mutableStateOf<HostModelProvider?>(null)};var query by remember {mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostModelsCatalog))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("host-moa-catalog"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {OutlinedTextField(query,{if(it.length<=256) query=it},Modifier.fillMaxWidth().testTag("host-moa-catalog-filter"),label={Text(label(Words.HostModelsFilter))},singleLine=true)}
        val row=provider
        if(row==null) {items(catalog.providers.filter {query.isBlank() || it.slug.contains(query,true) || it.name.contains(query,true)},key={it.slug}) {p->TextButton({provider=p;query=""},Modifier.testTag("host-moa-provider-${p.slug}")) {Text(p.name.ifBlank {p.slug})}};if(catalog.providers.isEmpty()) item {Text(label(Words.EmptyInventory))}}
        else {item {Text(row.name);TextButton({provider=null;query=""}) {Text(label(Words.HostModelsProviders))}};items(row.models.filter {query.isBlank() || it.contains(query,true)},key={it}) {model->Column {TextButton({choose(HostModelRoute(row.slug,model))},enabled=!row.slug.equals("moa",true) && HostMainModelRequest.eligible(row,model),modifier=Modifier.testTag("host-moa-model-${row.slug}-$model")) {Text(model)};if(row.slug.equals("moa",true)) Text(label(Words.HostMoaRecursive)) else if(model in row.unavailableModels || row.entitlementPending==true) Text(label(Words.ModelUnavailable)) else if(row.authenticated!=true) Text(label(Words.HostMainProviderUnready)) else if(!HostMainModelRequest.eligible(row,model)) Text(label(Words.ManagementInvalid))}};if(row.models.isEmpty()) item {Text(label(Words.EmptyInventory))}}
    }},confirmButton={TextButton(close,Modifier.testTag("host-moa-catalog-close")) {Text(label(Words.Close))}})
}
@Composable private fun HostMoaReview(review:ReviewedHostMoa,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    var approved by remember(review) {mutableStateOf(false)};val r=review.request
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostMoaReview))},text={LazyColumn(Modifier.heightIn(max=480.dp).testTag("host-moa-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);Text(label(Words.HostMoaEffect));Text(label(Words.HostMoaConcurrency));Text(label(Words.HostMoaRead));Text(label(Words.HostModelsReadEffect))}
        item {Text(label(Words.HostMoaDefault)+" · "+r.expected.config.defaultPreset+" → "+r.desired.defaultPreset);Text(label(Words.HostMoaActive)+" · "+r.expected.config.activePreset+" → "+r.desired.activePreset);Text(label(Words.HostMoaPrivacy)+" · "+r.desired.privacy);Text(label(Words.HostMoaRemoved));(r.expected.config.presets.keys-r.desired.presets.keys).forEach {Text(it)};Text(label(Words.HostMoaAdded));(r.desired.presets.keys-r.expected.config.presets.keys).forEach {Text(it)}}
        items(r.desired.presets.keys.sorted(),key={it}) {name->Column {Text(name,style=MaterialTheme.typography.titleMedium);HostMoaPresetDetails(r.desired.presets.getValue(name))}}
        item {Row {Checkbox(approved,{approved=it},modifier=Modifier.testTag("host-moa-approve"));Text(label(Words.HostMoaApprove),Modifier.weight(1f))}}
    }},confirmButton={TextButton(confirm,enabled=enabled && approved,modifier=Modifier.testTag("host-moa-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("host-moa-review-cancel")) {Text(label(Words.Cancel))}})
}
