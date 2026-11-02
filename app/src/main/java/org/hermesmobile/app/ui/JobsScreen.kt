package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
    val state by vm.jobsStatus.collectAsStateWithLifecycle()
    val fetched by vm.jobsFetchedAt.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var detail by remember {mutableStateOf<HermesJob?>(null)}
    var detailError by remember {mutableStateOf<String?>(null)}
    var loading by remember {mutableStateOf(false)}
    var query by rememberSaveable {mutableStateOf("")}
    val visible=jobs.filter {it.name.contains(query,true) || it.prompt.contains(query,true)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(vertical=16.dp)) {
        item {Word(Words.Automation,heading=true)}
        item {Text(label(Words.JobsInfo))}
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
            TextButton({scope.launch {loading=true;detailError=null;try {detail=vm.jobDetail(job.id)} catch(e:Exception) {detailError=connectionError(e)} finally {loading=false}}},enabled=!loading) {Text(label(Words.JobDetails))}
        }}
    }
    detail?.let {job -> AlertDialog(onDismissRequest={detail=null},title={Text(job.name)},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(state!="ready") item {Text(label(Words.Cached))}
        item {Text(label(Words.Schedule));Text(job.schedule)}
        item {Text(label(Words.JobState));Text(job.state)}
        if(job.nextRun.isNotBlank()) item {Text(label(Words.NextRun));Text(job.nextRun)}
        if(job.lastRun.isNotBlank()) item {Text(label(Words.LastRun));Text(job.lastRun)}
        item {Text(label(Words.Prompt));Text(job.prompt)}
    }},confirmButton={TextButton({detail=null}) {Text(label(Words.Close))}}) }
}
