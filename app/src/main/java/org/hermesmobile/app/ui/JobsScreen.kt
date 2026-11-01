package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.hermesmobile.app.data.HermesJob
import org.hermesmobile.app.network.connectionError

@Composable fun JobsScreen(vm: HermesViewModel) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val state by vm.jobsStatus.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var detail by remember {mutableStateOf<HermesJob?>(null)}
    var detailError by remember {mutableStateOf<String?>(null)}
    var loading by remember {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(vertical=16.dp)) {
        item {Word(Words.Automation,heading=true)}
        item {Text(label(Words.JobsInfo))}
        item {TextButton(vm::refreshJobs,enabled=state!="loading") {Text(label(Words.Refresh))}}
        if(state=="loading" || loading) item {QuietProgress()}
        if(detailError!=null) item {Text(detailError.orEmpty(),color=MaterialTheme.colorScheme.error)}
        if(jobs.isEmpty() && state!="loading") item {HermesCard {Text(label(when(state) {"unsupported"->Words.JobsUnsupported;"error"->Words.JobsError;else->Words.NoJobs}))}}
        items(jobs,key={it.id}) { job -> HermesCard {
            Text(job.name,style=MaterialTheme.typography.titleLarge)
            Text(job.schedule)
            Text(job.state,style=MaterialTheme.typography.labelMedium)
            TextButton({scope.launch {loading=true;detailError=null;try {detail=vm.jobDetail(job.id)} catch(e:Exception) {detailError=connectionError(e)} finally {loading=false}}},enabled=!loading) {Text(label(Words.Jobs))}
        }}
    }
    detail?.let {job -> AlertDialog(onDismissRequest={detail=null},title={Text(job.name)},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(label(Words.Schedule));Text(job.schedule)}
        item {Text(label(Words.JobState));Text(job.state)}
        item {Text(label(Words.Prompt));Text(job.prompt)}
    }},confirmButton={TextButton({detail=null}) {Text(label(Words.Close))}}) }
}
