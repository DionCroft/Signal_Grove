package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*

data class JobChange(val operation: String,val id: String,val name: String,val body: JsonObject=buildJsonObject {})
@Composable fun JobEditor(job: HermesJob?,dismiss: ()->Unit,review: (JobChange)->Unit) {
    var name by remember {mutableStateOf(job?.name.orEmpty())}
    var prompt by remember {mutableStateOf(job?.prompt.orEmpty())}
    var schedule by remember {mutableStateOf("")}
    val valid=name.isNotBlank() && name.length<=200 && prompt.isNotBlank() && prompt.length<=32000 && schedule.length<=500 && (job!=null || schedule.isNotBlank())
    AlertDialog(onDismissRequest=dismiss,title={Text(label(if(job==null) Words.NewJob else Words.Edit))},text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(name,{name=it},label={Text(label(Words.JobName))},singleLine=true)
        OutlinedTextField(prompt,{prompt=it},label={Text(label(Words.Prompt))},minLines=3,maxLines=8)
        if(job!=null) Text(job.schedule)
        OutlinedTextField(schedule,{schedule=it},label={Text(label(if(job==null) Words.Schedule else Words.ReplaceSchedule))},singleLine=true)
        Text(label(Words.ScheduleHelp));Text(label(Words.ManageJobsInfo))
    }},confirmButton={TextButton({review(JobChange(if(job==null) "create" else "update",job?.id.orEmpty(),name,JobProtocol.body(name,prompt,schedule,job==null)))},enabled=valid) {Text(label(Words.ReviewJobChange))}},dismissButton={TextButton(dismiss) {Text(label(Words.Cancel))}})
}
fun jobOperationWord(operation: String)=when(operation) {"create"->Words.NewJob;"update"->Words.Edit;"pause"->Words.PauseJob;"resume"->Words.ResumeJob;"run"->Words.RunJob;else->Words.DeleteJob}
@Composable fun JobReview(change: JobChange,dismiss: ()->Unit,confirm: ()->Unit) {
    AlertDialog(onDismissRequest=dismiss,title={Text(label(Words.ReviewJobChange))},text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label(jobOperationWord(change.operation)));Text(change.name)
        change.body.forEach {(key,value)-> Text("$key: "+(value as? JsonPrimitive)?.content.orEmpty())}
        Text(label(Words.JobChangeWarning))
    }},confirmButton={TextButton(confirm) {Text(label(Words.ConfirmChange))}},dismissButton={TextButton(dismiss) {Text(label(Words.Cancel))}})
}
