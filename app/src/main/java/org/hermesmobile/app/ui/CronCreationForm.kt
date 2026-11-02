package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.*

private data class CronDraft(val name:String="",val schedule:String="",val prompt:String="",val repeat:String="1",val unlimited:Boolean=false,
    val delivery:String="local",val skills:String="",val model:String="",val provider:String="",val baseUrl:String="",val script:String="",
    val context:List<String> = emptyList(),val restrictTools:Boolean=false,val tools:String="",val workdir:String="",val noAgent:Boolean=false,
    val timezone:String="",val grace:String="",val catchUp:Int=0,val reason:String="") {
    fun request(s:ManagementState):CronCreation {
        fun lines(value:String)=value.lines().map(String::trim).filter(String::isNotBlank).distinct()
        val target=s.cron.deliveries.singleOrNull {it.id==delivery && it.configured} ?: throw InvalidCronCreation()
        val count=if(unlimited) null else (repeat.trim().toIntOrNull() ?: throw InvalidCronCreation())
        val graceMinutes=if(grace.isBlank()) null else (grace.trim().toIntOrNull() ?: throw InvalidCronCreation())
        return CronCreation(name.trim(),schedule.trim(),if(noAgent) "" else prompt.trim(),count,target,if(noAgent) emptyList() else lines(skills),
            if(noAgent) "" else model.trim(),if(noAgent) "" else provider.trim(),if(noAgent) "" else baseUrl.trim(),script.trim(),if(noAgent) emptyList() else context,
            if(noAgent || !restrictTools) null else lines(tools),workdir.trim(),noAgent,timezone.trim(),graceMinutes,when(catchUp) {1->true;2->false;else->null},reason.trim())
    }
    override fun toString()="CronDraft(redacted)"
}

@Composable fun CronCreationForm(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var draft by remember(s.apiScope,s.url,s.profile) {mutableStateOf(CronDraft())}
    var advanced by remember {mutableStateOf(false)}
    var attempted by remember {mutableStateOf(false)}
    var context by remember {mutableStateOf(false)}
    var review by remember {mutableStateOf<ReviewedCronCreation?>(null)}
    val request=runCatching {draft.request(s)}.getOrNull()
    val supported=request?.let {s.cron.contract?.accepts(it)}==true
    val enabled=!s.busy
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostCronCreate))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("cron-create-form"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(label(Words.HostCronCreateHelp));Text(s.profile)}
            item {OutlinedTextField(draft.name,{draft=draft.copy(name=it)},Modifier.fillMaxWidth().testTag("cron-create-name"),label={Text(label(Words.JobName))},singleLine=true,enabled=enabled)}
            item {OutlinedTextField(draft.schedule,{draft=draft.copy(schedule=it)},Modifier.fillMaxWidth().testTag("cron-create-schedule"),label={Text(label(Words.Schedule))},supportingText={Text(label(Words.HostCronScheduleHelp))},enabled=enabled)}
            item {Row {Checkbox(draft.noAgent,{draft=draft.copy(noAgent=it)},enabled=enabled,modifier=Modifier.testTag("cron-create-script-only"));Text(label(Words.HostCronNoAgent))}}
            if(draft.noAgent) item {OutlinedTextField(draft.script,{draft=draft.copy(script=it)},Modifier.fillMaxWidth().testTag("cron-create-script"),label={Text(label(Words.HostCronScript))},supportingText={Text(label(Words.HostCronScriptHelp))},enabled=enabled)}
            else item {OutlinedTextField(draft.prompt,{draft=draft.copy(prompt=it)},Modifier.fillMaxWidth().testTag("cron-create-prompt"),label={Text(label(Words.Prompt))},minLines=3,enabled=enabled)}
            item {Row {Checkbox(draft.unlimited,{draft=draft.copy(unlimited=it)},enabled=enabled,modifier=Modifier.testTag("cron-create-unlimited"));Text(label(Words.HostCronUnlimited))}}
            if(!draft.unlimited) item {OutlinedTextField(draft.repeat,{draft=draft.copy(repeat=it)},Modifier.fillMaxWidth().testTag("cron-create-repeat"),label={Text(label(Words.HostCronRepeat))},singleLine=true,enabled=enabled)}
            item {Text(label(Words.HostCronDelivery),style=MaterialTheme.typography.titleSmall)}
            items(s.cron.deliveries,key={it.id}) {target ->Column {
                Row {RadioButton(draft.delivery==target.id,{draft=draft.copy(delivery=target.id)},enabled=enabled && target.configured,modifier=Modifier.testTag("cron-create-delivery-${target.id}"));Text(if(target.id=="local") label(Words.HostCronLocal) else target.name.ifBlank {target.id})}
                if(!target.configured) Text(label(Words.HostCronDeliveryUnavailable),style=MaterialTheme.typography.bodySmall)
            }}
            item {TextButton({advanced=!advanced},modifier=Modifier.testTag("cron-create-advanced")) {Text(label(Words.HostCronAdvanced))}}
            if(advanced) {
                if(!draft.noAgent) {
                    item {OutlinedTextField(draft.skills,{draft=draft.copy(skills=it)},Modifier.fillMaxWidth().testTag("cron-create-skills"),label={Text(label(Words.HostCronSkills))},supportingText={Text(label(Words.HostCronIdsHelp))},enabled=enabled)}
                    item {OutlinedTextField(draft.model,{draft=draft.copy(model=it)},Modifier.fillMaxWidth(),label={Text(label(Words.HostCronModel))},enabled=enabled)}
                    item {OutlinedTextField(draft.provider,{draft=draft.copy(provider=it)},Modifier.fillMaxWidth(),label={Text(label(Words.HostCronProvider))},enabled=enabled)}
                    item {OutlinedTextField(draft.baseUrl,{draft=draft.copy(baseUrl=it)},Modifier.fillMaxWidth(),label={Text(label(Words.HostCronProviderUrl))},supportingText={Text(label(Words.HostCronProviderUrlEffect))},enabled=enabled)}
                    item {OutlinedTextField(draft.script,{draft=draft.copy(script=it)},Modifier.fillMaxWidth().testTag("cron-create-script"),label={Text(label(Words.HostCronScript))},supportingText={Text(label(Words.HostCronScriptHelp))},enabled=enabled)}
                    item {TextButton({context=true},enabled=enabled,modifier=Modifier.testTag("cron-create-context")) {Text(label(Words.HostCronContext)+" · "+draft.context.size)}}
                    item {Row {Checkbox(draft.restrictTools,{draft=draft.copy(restrictTools=it)},enabled=enabled,modifier=Modifier.testTag("cron-create-restrict-tools"));Text(label(Words.HostCronRestrictTools))}}
                    if(draft.restrictTools) item {OutlinedTextField(draft.tools,{draft=draft.copy(tools=it)},Modifier.fillMaxWidth().testTag("cron-create-tools"),label={Text(label(Words.HostCronToolsets))},supportingText={Text(label(if(draft.tools.isBlank()) Words.HostCronNoTools else Words.HostCronIdsHelp))},enabled=enabled)}
                    else item {Text(label(Words.HostCronInheritTools),style=MaterialTheme.typography.bodySmall)}
                }
                item {OutlinedTextField(draft.workdir,{draft=draft.copy(workdir=it)},Modifier.fillMaxWidth(),label={Text(label(Words.HostCronWorkdir))},supportingText={Text(label(Words.HostCronWorkdirHelp))},enabled=enabled)}
                item {OutlinedTextField(draft.timezone,{draft=draft.copy(timezone=it)},Modifier.fillMaxWidth().testTag("cron-create-timezone"),label={Text(label(Words.HostCronTimezone))},supportingText={Text(label(Words.HostCronTimezoneHelp))},enabled=enabled)}
                item {OutlinedTextField(draft.grace,{draft=draft.copy(grace=it)},Modifier.fillMaxWidth().testTag("cron-create-grace"),label={Text(label(Words.HostCronGrace))},supportingText={Text(label(Words.HostCronGraceHelp))},enabled=enabled)}
                item {Text(label(Words.HostCronCatchUp));Row {RadioButton(draft.catchUp==0,{draft=draft.copy(catchUp=0)},enabled=enabled);Text(label(Words.HostCronInherit))};Row {RadioButton(draft.catchUp==1,{draft=draft.copy(catchUp=1)},enabled=enabled);Text(label(Words.Enabled))};Row {RadioButton(draft.catchUp==2,{draft=draft.copy(catchUp=2)},enabled=enabled);Text(label(Words.Disabled))}}
                item {OutlinedTextField(draft.reason,{draft=draft.copy(reason=it)},Modifier.fillMaxWidth(),label={Text(label(Words.HostCronPauseReason))},enabled=enabled)}
            }
            if(attempted && request==null) item {Text(label(Words.HostCronCreateInvalid),color=MaterialTheme.colorScheme.error)}
            if(attempted && request!=null && !supported) item {Text(label(Words.HostCronCreateUnsupported),color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton({attempted=true;if(request!=null && supported) review=vm.prepareCronCreation(request)},enabled=enabled,modifier=Modifier.testTag("cron-create-review")) {Text(label(Words.Review))}},dismissButton={TextButton(close) {Text(label(Words.Cancel))}})
    if(context) AlertDialog(onDismissRequest={context=false},title={Text(label(Words.HostCronContext))},text={
        LazyColumn(Modifier.heightIn(max=400.dp)) {
            item {Row {Checkbox("self" in draft.context,{checked->draft=draft.copy(context=if(checked) draft.context+"self" else draft.context-"self")});Text(label(Words.HostCronSelfContext))}}
            items(s.cron.jobs,key={it.id}) {job ->Row {Checkbox(job.id in draft.context,{checked->draft=draft.copy(context=if(checked) draft.context+job.id else draft.context-job.id)},enabled=job.addressable);Text(job.name+"\n"+job.id)}}
        }
    },confirmButton={TextButton({context=false}) {Text(label(Words.Close))}})
    review?.let {frozen ->CronCreationReview(frozen,enabled,{review=null}) {vm.createCron(frozen);close()}}
}

@Composable private fun CronCreationReview(review:ReviewedCronCreation,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    val request=review.request
    var query by remember(review) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostCronCreate))},text={
        LazyColumn(Modifier.heightIn(max=500.dp).testTag("cron-create-confirmation"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor);Text(request.name,style=MaterialTheme.typography.titleMedium)}
            item {Text(label(Words.Schedule));Text(request.schedule);Text(label(Words.HostCronRepeat));Text(request.repeat?.toString() ?: label(Words.HostCronUnlimited));Text(label(Words.HostCronDelivery));Text(if(request.delivery.id=="local") label(Words.HostCronLocal) else request.delivery.name+"\n"+label(Words.HostCronDeliveryEffect))}
            if(request.prompt.isNotBlank()) item {Text(label(Words.Prompt));Text(request.prompt)}
            if(request.script.isNotBlank()) item {Text(label(Words.HostCronScript));Text(request.script);Text(label(if(request.noAgent) Words.HostCronNoAgent else Words.HostCronScriptContext))}
            if(request.skills.isNotEmpty()) item {Text(label(Words.HostCronSkills));Text(request.skills.joinToString("\n"))}
            if(request.model.isNotBlank() || request.provider.isNotBlank()) item {Text(label(Words.HostCronModel)+": "+request.model);Text(label(Words.HostCronProvider)+": "+request.provider)}
            if(request.baseUrl.isNotBlank()) item {Text(label(Words.HostCronProviderUrl));Text(if(query) request.baseUrl else request.reviewBaseUrl());Text(label(Words.HostCronProviderUrlEffect));if(request.baseUrl!=request.reviewBaseUrl()) TextButton({query=!query}) {Text(label(if(query) Words.McpHideQuery else Words.McpShowQuery))}}
            if(!request.noAgent) item {Text(label(Words.HostCronToolsets));Text(request.toolsets?.let {if(it.isEmpty()) label(Words.HostCronNoTools) else it.joinToString("\n")} ?: label(Words.HostCronInheritTools))}
            if(request.context.isNotEmpty()) item {Text(label(Words.HostCronContext));Text(request.context.joinToString("\n"))}
            if(request.workdir.isNotBlank()) item {Text(label(Words.HostCronWorkdir));Text(request.workdir)}
            if(request.timezone.isNotBlank()) item {Text(label(Words.HostCronTimezone));Text(request.timezone)}
            request.graceMinutes?.let {value->item {Text(label(Words.HostCronGrace));Text(value.toString())}}
            request.catchUp?.let {value->item {Text(label(Words.HostCronCatchUp));Text(label(if(value) Words.Enabled else Words.Disabled))}}
            if(request.pausedReason.isNotBlank()) item {Text(label(Words.HostCronPauseReason));Text(request.pausedReason)}
            item {Text(label(Words.HostCronCreateEffect))}
        }
    },confirmButton={TextButton(confirm,enabled=enabled,modifier=Modifier.testTag("cron-create-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close) {Text(label(Words.Edit))}})
}
