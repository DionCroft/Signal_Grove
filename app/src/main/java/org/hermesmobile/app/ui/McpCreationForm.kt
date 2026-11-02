package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.*

private data class McpEnvironmentInput(val name:String="",val value:String="")
@Composable fun McpCreationForm(vm:ManagementViewModel,enabled:Boolean,close:()->Unit) {
    var transport by remember {mutableStateOf(McpTransport.Http)}
    var auth by remember {mutableStateOf(McpAuth.None)}
    // Provisioning inputs deliberately stay out of saved instance state and the receipt ledger.
    var name by remember {mutableStateOf("")};var url by remember {mutableStateOf("")}
    var command by remember {mutableStateOf("")};var arguments by remember {mutableStateOf("")}
    var token by remember {mutableStateOf("")}
    val environment=remember {mutableStateListOf<McpEnvironmentInput>()}
    var problem by remember {mutableStateOf<Words?>(null)}
    var review by remember {mutableStateOf<ReviewedMcpCreation?>(null)}
    fun prepare() {
        problem=null
        try {
            val env=environment.map {it.copy(name=it.name.trim())}
            if(env.map {it.name}.distinct().size!=env.size) throw InvalidMcpSetup(McpSetupIssue.Environment)
            val spec=if(transport==McpTransport.Http) McpCreation(name.trim(),transport,url=url.trim(),auth=auth,bearerToken=if(auth==McpAuth.Header) token else "")
                else McpCreation(name.trim(),transport,command=command.trim(),args=if(arguments.isEmpty()) emptyList() else arguments.split('\n').map {it.removeSuffix("\r")},environment=env.associate {it.name to it.value})
            review=vm.prepareCreation(spec)
            if(review==null) problem=Words.ManagementReviewChanged
        } catch(e:InvalidMcpSetup) {problem=when(e.issue) {
            McpSetupIssue.Name->Words.McpBadName;McpSetupIssue.Url->Words.McpBadUrl;McpSetupIssue.Command->Words.McpBadCommand
            McpSetupIssue.Arguments->Words.McpBadArguments;McpSetupIssue.Environment->Words.McpBadEnvironment
            McpSetupIssue.Authentication->Words.McpBadAuthentication;McpSetupIssue.Token->Words.McpBadToken
        }}
    }
    AlertDialog(onDismissRequest=close,title={Text(label(Words.McpAdd),style=MaterialTheme.typography.titleMedium)},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("mcp-create-form"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {OutlinedTextField(name,{if(it.length<=256) name=it},Modifier.fillMaxWidth().testTag("mcp-create-name"),label={Text(label(Words.McpServerName))},singleLine=true)}
            item {Row(Modifier.fillMaxWidth().testTag("mcp-transport-Http").selectable(transport==McpTransport.Http,role=Role.RadioButton) {transport=McpTransport.Http}) {RadioButton(transport==McpTransport.Http,null);Text(label(Words.McpHttp))};Row(Modifier.fillMaxWidth().testTag("mcp-transport-Stdio").selectable(transport==McpTransport.Stdio,role=Role.RadioButton) {transport=McpTransport.Stdio}) {RadioButton(transport==McpTransport.Stdio,null);Text(label(Words.McpStdio))}}
            if(transport==McpTransport.Http) {
                item {OutlinedTextField(url,{if(it.length<=8192) url=it},Modifier.fillMaxWidth().testTag("mcp-create-url"),label={Text(label(Words.McpUrl))},singleLine=true)}
                item {McpAuth.entries.forEach {option->Row(Modifier.fillMaxWidth().testTag("mcp-auth-${option.name}").selectable(auth==option,role=Role.RadioButton) {auth=option}) {RadioButton(auth==option,null);Text(label(when(option) {McpAuth.None->Words.McpAuthNone;McpAuth.Header->Words.McpAuthHeader;McpAuth.OAuth->Words.McpAuthOAuth}))}}}
                if(auth==McpAuth.Header) item {OutlinedTextField(token,{if(it.length<=16384) token=it},Modifier.fillMaxWidth().testTag("mcp-create-token"),label={Text(label(Words.McpToken))},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true)}
            } else {
                item {OutlinedTextField(command,{if(it.length<=512) command=it},Modifier.fillMaxWidth().testTag("mcp-create-command"),label={Text(label(Words.McpCommand))},singleLine=true)}
                item {OutlinedTextField(arguments,{if(it.length<=131072) arguments=it},Modifier.fillMaxWidth().testTag("mcp-create-arguments"),label={Text(label(Words.McpArguments))},minLines=2,maxLines=5);Text(label(Words.McpArgumentsHelp),style=MaterialTheme.typography.bodySmall)}
                environment.forEachIndexed {index,row ->item(key="env-$index") {
                    OutlinedTextField(row.name,{if(it.length<=128) environment[index]=environment[index].copy(name=it)},Modifier.fillMaxWidth(),label={Text(label(Words.McpEnvName))},singleLine=true)
                    OutlinedTextField(row.value,{if(it.length<=8192) environment[index]=environment[index].copy(value=it)},Modifier.fillMaxWidth(),label={Text(label(Words.McpEnvValue))},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true)
                    TextButton({environment.removeAt(index)}) {Text(label(Words.Delete))}
                }}
                item {TextButton({environment.add(McpEnvironmentInput())},enabled=environment.size<32) {Text(label(Words.McpAddEnvironment))}}
            }
            item {Text(label(Words.McpHostFields),style=MaterialTheme.typography.bodySmall)}
            problem?.let {word->item {Text(label(word),color=MaterialTheme.colorScheme.error)}}
        }
    },confirmButton={TextButton(::prepare,enabled=enabled,modifier=Modifier.testTag("mcp-create-review")) {Text(label(Words.Review))}},dismissButton={TextButton(close) {Text(label(Words.Cancel))}})
    review?.let {frozen ->McpCreationReview(frozen,enabled,{review=null}) {vm.createMcp(frozen);review=null;close()}}
}
@Composable private fun McpCreationReview(review:ReviewedMcpCreation,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    var showArguments by remember {mutableStateOf(false)}
    var showQuery by remember {mutableStateOf(false)}
    val spec=review.creation
    AlertDialog(onDismissRequest=close,title={Text(label(Words.McpAdd),style=MaterialTheme.typography.titleMedium)},text={
        LazyColumn(Modifier.heightIn(max=480.dp).testTag("mcp-create-confirmation"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);Text(spec.name,style=MaterialTheme.typography.titleMedium)}
            if(spec.transport==McpTransport.Http) item {Text(spec.reviewUrl());if(spec.queryKeys().isNotEmpty()) {Text(label(Words.McpQueryKeys));Text(spec.queryKeys().joinToString(", "));TextButton({showQuery=!showQuery}) {Text(label(if(showQuery) Words.McpHideQuery else Words.McpShowQuery))};if(showQuery) Text(spec.url)};Text(label(when(spec.auth) {McpAuth.None->Words.McpAuthNone;McpAuth.Header->Words.McpAuthHeader;McpAuth.OAuth->Words.McpAuthOAuth}))}
            else item {Text(spec.command);Text(label(Words.McpArguments)+" "+spec.args.size);if(spec.args.isNotEmpty()) {TextButton({showArguments=!showArguments}) {Text(label(if(showArguments) Words.McpHideArguments else Words.McpShowArguments))};if(showArguments) Text(spec.args.joinToString("\n"))};if(spec.environmentKeys.isNotEmpty()) {Text(label(Words.McpEnvName));Text(spec.environmentKeys.joinToString("\n"))}}
            item {Text(label(Words.McpCreateEffect))}
        }
    },confirmButton={TextButton(confirm,enabled=enabled,modifier=Modifier.testTag("mcp-create-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close) {Text(label(Words.Edit))}})
}
