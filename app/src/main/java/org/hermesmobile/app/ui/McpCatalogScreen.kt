package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.*

@Composable fun McpCatalogDialog(vm:ManagementViewModel,s:ManagementState,pending:Boolean,close:()->Unit) {
    var selected by remember(s.apiScope,s.url,s.profile) {mutableStateOf<McpCatalogEntry?>(null)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.McpCatalog))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("mcp-catalog-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(s.url);Text(s.profile);TextButton(vm::loadCatalog,enabled=!s.busy,modifier=Modifier.testTag("mcp-catalog-refresh")) {Text(label(Words.Refresh))}}
            if(s.busy) item {QuietProgress()}
            s.notice?.let {word->item {Text(label(word))}}
            s.error?.let {word->item {Text(label(word),color=MaterialTheme.colorScheme.error)}}
            if(s.catalogLoaded && s.catalog.isEmpty()) item {Text(label(Words.EmptyInventory))}
            items(s.catalog,key={it.name}) {entry ->Column {
                Text(entry.name,style=MaterialTheme.typography.titleMedium);Text(entry.description)
                if(entry.installed) Text(label(Words.McpCatalogInstalled))
                if(!entry.supported) Text(label(Words.McpCatalogHostSetup))
                TextButton({selected=entry},enabled=!s.busy && !pending && entry.supported && !entry.installed,modifier=Modifier.testTag("mcp-catalog-select-${entry.name}")) {Text(label(Words.Review))}
            }}
            if(pending) item {Text(label(Words.ManagementOutcomePending))}
        }
    },confirmButton={TextButton(close) {Text(label(Words.Close))}})
    selected?.let {entry ->McpCatalogForm(vm,entry,!s.busy && !pending) {selected=null}}
}

@Composable private fun McpCatalogForm(vm:ManagementViewModel,entry:McpCatalogEntry,enabled:Boolean,close:()->Unit) {
    var values by remember(entry) {mutableStateOf<Map<String,String>>(emptyMap())}
    var enable by remember(entry) {mutableStateOf(entry.needsInstall)}
    var review by remember(entry) {mutableStateOf<ReviewedCatalogInstall?>(null)}
    AlertDialog(onDismissRequest=close,title={Text(entry.name)},text={
        LazyColumn(Modifier.heightIn(max=500.dp).testTag("mcp-catalog-form"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(entry.description);Text(entry.details);Text(label(Words.McpCatalogEffect))}
            if(entry.needsInstall) item {Text(label(Words.McpCatalogBackground))}
            else item {Row {Checkbox(enable,{enable=it},enabled=enabled,modifier=Modifier.testTag("mcp-catalog-enable"));Text(label(Words.Enabled))}}
            items(entry.environment,key={it.name}) {spec ->OutlinedTextField(values[spec.name].orEmpty(),{value->values=values+(spec.name to value)},Modifier.fillMaxWidth().testTag("mcp-catalog-env-${spec.name}"),label={Text(spec.name+(if(spec.required) " *" else ""))},supportingText={Text(spec.prompt)},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),enabled=enabled)}
        }
    },confirmButton={TextButton({review=vm.prepareCatalog(McpCatalogInstall(entry,values.filterValues {it.isNotEmpty()},enable))},enabled=enabled && runCatching {McpCatalogInstall(entry,values.filterValues {it.isNotEmpty()},enable)}.isSuccess,modifier=Modifier.testTag("mcp-catalog-review")) {Text(label(Words.Review))}},dismissButton={TextButton(close) {Text(label(Words.Cancel))}})
    review?.let {frozen ->AlertDialog(onDismissRequest={review=null},title={Text(label(Words.McpCatalogInstall))},text={
        LazyColumn(Modifier.heightIn(max=460.dp).testTag("mcp-catalog-confirmation"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor);Text(entry.name,style=MaterialTheme.typography.titleMedium)}
            item {Text(entry.details);Text(label(if(enable) Words.Enabled else Words.Disabled));Text(entry.environment.joinToString("\n") {it.name});Text(label(Words.McpCatalogEffect));if(entry.needsInstall) Text(label(Words.McpCatalogBackground))}
        }
    },confirmButton={TextButton({vm.installCatalog(frozen);close()},enabled=enabled,modifier=Modifier.testTag("mcp-catalog-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({review=null}) {Text(label(Words.Edit))}})}
}
