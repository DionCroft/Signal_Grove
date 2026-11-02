package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.hermesmobile.app.data.HermesJob
import org.hermesmobile.app.network.connectionError
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun JobsScreen(vm: HermesViewModel) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val profile by vm.profileScope.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val jobBusy by vm.jobBusy.collectAsStateWithLifecycle()
    val receipts by vm.jobOperations.collectAsStateWithLifecycle()
    val enabled=prefs.automationScope==profile
    val state by vm.jobsStatus.collectAsStateWithLifecycle()
    val fetched by vm.jobsFetchedAt.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var detail by remember {mutableStateOf<HermesJob?>(null)}
    var detailError by remember {mutableStateOf<String?>(null)}
    var loading by remember {mutableStateOf(false)}
    var editor by remember(profile) {mutableStateOf(false)}
    var editing by remember(profile) {mutableStateOf<HermesJob?>(null)}
    var change by remember(profile) {mutableStateOf<JobChange?>(null)}
    var acknowledge by remember(profile) {mutableStateOf<String?>(null)}
    LaunchedEffect(profile) {detail=null;detailError=null;loading=false}
    var query by rememberSaveable {mutableStateOf("")}
    val visible=jobs.filter {it.name.contains(query,true) || it.prompt.contains(query,true)}
    LazyColumn(Modifier.testTag("job-list").fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(vertical=16.dp)) {
        item {Word(Words.Automation,heading=true)}
        item {Text(label(Words.ManageJobsInfo))}
        item {Row {Text(label(Words.ManageJobs),Modifier.weight(1f));Switch(enabled,{vm.updatePreferences(prefs.copy(automationScope=if(it) profile else ""))},enabled=!jobBusy)}}
        if(enabled) item {Button({editing=null;editor=true},enabled=state=="ready" && !jobBusy && receipts.isEmpty()) {Text(label(Words.NewJob))}}
        if(jobBusy) item {QuietProgress()}
        receipts.forEach {receipt -> item(key=receipt.key) {HermesCard {
            Text(label(Words.JobOutcomeUnknown));Text(label(jobOperationWord(receipt.operation))+" · "+receipt.jobId)
            if(receipt.state=="unknown") TextButton({acknowledge=receipt.key},enabled=!jobBusy) {Text(label(Words.ReviewedOutcome))}
        }}}

        item {OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text(label(Words.Search))},singleLine=true)}
        item {TextButton(vm::refreshJobs,enabled=state!="loading") {Text(label(Words.Refresh))}}
        if(fetched>0) item {Text(label(Words.LastUpdated)+": "+DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(fetched)),style=MaterialTheme.typography.labelSmall)}
        if(state=="loading" || loading) item {QuietProgress()}
        if(state in listOf("unsupported","error")) item {Text(label(if(state=="unsupported") Words.JobsUnsupported else Words.JobsError))}
        if(state!="ready" && jobs.isNotEmpty()) item {Text(label(Words.Cached))}
        if(detailError!=null) item {Text(detailError.orEmpty(),color=MaterialTheme.colorScheme.error)}
        if(visible.isEmpty() && state!="loading") item {HermesCard {Text(label(if(query.isBlank()) Words.NoJobs else Words.NoMatches))}}
        items(visible,key={it.id}) {job -> HermesCard {
            Text(job.name,style=MaterialTheme.typography.titleLarge)
            Text(job.schedule)
            Text(label(if(job.enabled) Words.Enabled else Words.Paused),style=MaterialTheme.typography.labelMedium)
            if(job.nextRun.isNotBlank()) Text(label(Words.NextRun)+": "+job.nextRun,style=MaterialTheme.typography.bodySmall)
            TextButton({scope.launch {loading=true;detailError=null;try {val result=vm.jobDetail(job.id);if(vm.profileScope.value==profile) detail=result} catch(e:Exception) {if(vm.profileScope.value==profile) detailError=connectionError(e)} finally {if(vm.profileScope.value==profile) loading=false}}},enabled=!loading) {Text(label(Words.JobDetails))}
        }}
    }
    detail?.let {job -> AlertDialog(onDismissRequest={detail=null},title={Text(job.name)},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(state!="ready") item {Text(label(Words.Cached))}
        item {Text(label(Words.Schedule));Text(job.schedule)}
        item {Text(label(Words.JobState));Text(job.state)}
        if(job.nextRun.isNotBlank()) item {Text(label(Words.NextRun));Text(job.nextRun)}
        if(job.lastRun.isNotBlank()) item {Text(label(Words.LastRun));Text(job.lastRun)}
        item {Text(label(Words.Prompt));Text(job.prompt)}
        if(enabled && state=="ready") item {
            TextButton({editing=job;editor=true;detail=null},enabled=!jobBusy && receipts.isEmpty()) {Text(label(Words.Edit))}
            listOf(if(job.enabled) "pause" else "resume","run","delete").forEach {operation ->
                TextButton({change=JobChange(operation,job.id,job.name);detail=null},enabled=!jobBusy && receipts.isEmpty()) {Text(label(jobOperationWord(operation)))}
            }
        }
    }},confirmButton={TextButton({detail=null}) {Text(label(Words.Close))}}) }
    if(editor) JobEditor(editing,{editor=false}) {change=it;editor=false}
    change?.let {review -> JobReview(review,{change=null}) {vm.mutateJob(profile,review.operation,review.id,review.body);change=null}}
    acknowledge?.let {key -> AlertDialog(onDismissRequest={acknowledge=null},title={Text(label(Words.ReviewedOutcome))},text={Text(label(Words.AcknowledgeOutcome))},confirmButton={TextButton({vm.acknowledgeJobOperation(key);acknowledge=null}) {Text(label(Words.ConfirmChange))}},dismissButton={TextButton({acknowledge=null}) {Text(label(Words.Cancel))}})}
}
