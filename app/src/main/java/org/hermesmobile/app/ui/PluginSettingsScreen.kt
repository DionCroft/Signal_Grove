package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

@Composable fun PluginSettingsDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var environmentKey by remember(s.apiScope,s.url,s.profile) {mutableStateOf<String?>(null)}
    val snapshot=s.plugins.settings
    var revealed by remember(snapshot) {mutableStateOf(false)}
    var field by remember(snapshot) {mutableStateOf<PluginSettingField?>(null)}
    var review by remember(snapshot) {mutableStateOf<ReviewedPluginSettings?>(null)}
    val receipts by vm.receipts.collectAsStateWithLifecycle();val pending=receipts.any {it.state in setOf("sending","unknown")}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.PluginSettings))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("plugin-settings-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(s.url);Text(s.profile);Text(label(Words.PluginSettingsPrivacy))}
            if(s.busy) item {QuietProgress();TextButton(vm::cancel) {Text(label(Words.Cancel))}}
            s.error?.let {item {Text(label(it),color=MaterialTheme.colorScheme.error)}}
            s.notice?.let {item {Text(label(it))}}
            snapshot?.let {snap->
                item {Text(snap.plugin.name);Text(snap.plugin.key);TextButton({revealed=!revealed},Modifier.testTag("plugin-settings-show")) {Text(label(if(revealed) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))};if(snap.fields.isEmpty()) Text(label(Words.PluginSettingsEmpty))}
                items(snap.fields,key={it.key}) {f->Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(f.label,style=MaterialTheme.typography.titleMedium);Text(f.key+" · "+f.type)
                    if(revealed) {Text(f.description);if(f.type=="secret") {Text(f.env);Text(label(when(f.hasValue) {true->Words.PluginReportedYes;false->Words.PluginReportedNo;else->Words.Unknown}))} else {Text(label(Words.PluginSettingsReported));Text(f.value.toString());if(f.default !is JsonNull) {Text(label(Words.PluginSettingsDefault));Text(f.default.toString())}}}
                    if(f.type=="secret") {Text(label(Words.PluginSettingsSecret));TextButton({environmentKey=f.env},enabled=!s.busy,modifier=Modifier.testTag("plugin-secret-${f.key}")) {Text(label(Words.HostEnvironment))}} else {
                        if(f.key.contains('.')) Text(label(Words.PluginSettingsNested))
                        TextButton({field=f},enabled=!s.busy && !pending,modifier=Modifier.testTag("plugin-setting-edit-${f.key}")) {Text(label(Words.PluginSettingsEdit))}
                    }
                }}
                if(pending) item {Text(label(Words.ManagementOutcomePending))}
            }
        }
    },confirmButton={TextButton(close,Modifier.testTag("plugin-settings-close")) {Text(label(Words.Close))}})
    environmentKey?.let {key->HostEnvironmentDialog(vm,s,key) {vm.closeHostEnvironment();environmentKey=null}}
    if(snapshot!=null) field?.let {f->PluginSettingEditor(f,!s.busy,{field=null}) {v->review=vm.preparePluginSetting(snapshot,f,v);if(review!=null) field=null}}
    review?.let {frozen->PluginSettingReview(frozen,!s.busy,{review=null}) {review=null;vm.savePluginSetting(frozen)}}
}
@Composable private fun PluginSettingEditor(field:PluginSettingField,enabled:Boolean,close:()->Unit,review:(JsonElement)->Unit) {
    var text by remember(field) {mutableStateOf(when {field.value is JsonNull->"";field.value is JsonPrimitive && field.value.isString->field.value.content;else->field.value.toString()})}
    var show by remember(field) {mutableStateOf(false)}
    val value=field.input(text)
    AlertDialog(onDismissRequest=close,title={Text(label(Words.PluginSettingsEdit))},text={
        LazyColumn(Modifier.heightIn(max=480.dp).testTag("plugin-setting-editor"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(field.label);Text(field.key+" · "+field.type);Text(label(Words.PluginSettingsPrivacy))}
            item {OutlinedTextField(text,{if(it.length<=32768) text=it},label={Text(label(Words.PluginSettingsValue))},visualTransformation=if(show) VisualTransformation.None else PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth().testTag("plugin-setting-value"),maxLines=6)}
            item {TextButton({show=!show},Modifier.testTag("plugin-setting-editor-show")) {Text(label(if(show) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
            if(show) {
                item {Text(field.description);if(field.required) Text(label(Words.PluginSettingsRequired))}
                if(field.type=="enum") items(field.choices) {choice->TextButton({text=choice},Modifier.testTag("plugin-setting-choice-$choice")) {Text(choice)}}
                if(field.type=="boolean") item {Row {TextButton({text="true"}) {Text(label(Words.PluginReportedYes))};TextButton({text="false"}) {Text(label(Words.PluginReportedNo))}}}
            }
            if(value==null) item {Text(label(Words.PluginSettingsInvalid))}
        }
    },confirmButton={TextButton({value?.let(review)},enabled=enabled && value!=null,modifier=Modifier.testTag("plugin-setting-review")) {Text(label(Words.PluginSettingsReview))}},dismissButton={TextButton(close,Modifier.testTag("plugin-setting-editor-cancel")) {Text(label(Words.Cancel))}})
}
@Composable private fun PluginSettingReview(review:ReviewedPluginSettings,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    var shown by remember(review) {mutableStateOf(false)};var approved by remember(review) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.PluginSettingsReview))},text={
        LazyColumn(Modifier.heightIn(max=480.dp).testTag("plugin-setting-confirm-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);Text(review.request.expected.plugin.key);Text(review.request.field.label);Text(review.request.field.key)}
            item {Text(label(Words.PluginSettingsEffect));Text(label(Words.PluginSettingsLimit))}
            if(review.request.field.key.contains('.')) item {Text(label(Words.PluginSettingsNested))}
            item {TextButton({shown=!shown},Modifier.testTag("plugin-setting-review-show")) {Text(label(if(shown) Words.PluginUpdateHideDetails else Words.PluginUpdateShowDetails))}}
            if(shown) item {Text(label(Words.PluginSettingsValue));Text(review.request.value.toString())}
            item {Row {Checkbox(approved,{approved=it},modifier=Modifier.testTag("plugin-setting-approve"));Text(label(Words.PluginSettingsApprove),Modifier.weight(1f))}}
        }
    },confirmButton={TextButton(confirm,enabled=enabled && shown && approved,modifier=Modifier.testTag("plugin-setting-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.testTag("plugin-setting-cancel")) {Text(label(Words.Cancel))}})
}
