package org.hermesmobile.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.data.*
import java.time.LocalDate
import java.util.UUID

@Composable fun TasksScreen(vm: HermesViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    var search by rememberSaveable {mutableStateOf("")}
    var filter by rememberSaveable {mutableStateOf(TaskFilter.ALL)}
    val visible=filteredTasks(tasks,search,filter)
    var editing by remember {mutableStateOf<LocalTask?>(null)}
    var deleting by remember {mutableStateOf<LocalTask?>(null)}
    LazyColumn(Modifier.testTag("task-list").fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(vertical=16.dp)) {
        item {Word(Words.Tasks,heading=true)}
        item {Text(label(Words.TaskInfo))}
        item {HermesCard {
            Text("${tasks.count {it.completed}} / ${tasks.size}",style=MaterialTheme.typography.displaySmall)
            Text(label(Words.Completed))
            LinearProgressIndicator(progress={if(tasks.isEmpty()) 0f else tasks.count {it.completed}.toFloat()/tasks.size},modifier=Modifier.fillMaxWidth())
            Button({editing=LocalTask(UUID.randomUUID().toString(),"")}) {Icon(Icons.Outlined.Add,null);Text(label(Words.AddTask))}
        }}
        item {OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text(label(Words.Search))},singleLine=true)}
        item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {TaskFilter.entries.forEach {f ->
            FilterChip(filter==f,{filter=f},label={Text(label(when(f) {TaskFilter.ALL->Words.All;TaskFilter.OPEN->Words.Open;TaskFilter.COMPLETED->Words.Completed;TaskFilter.OVERDUE->Words.Overdue}))})
        }}}
        if(visible.isEmpty()) item {HermesCard {Text(label(if(tasks.isEmpty()) Words.NoTasks else Words.NoMatches))}}
        items(visible,key={it.id}) { task -> HermesCard {
            Row(verticalAlignment=Alignment.CenterVertically) {
                val completeLabel=label(Words.CompleteTask)
                Checkbox(task.completed,{vm.saveTask(task.copy(completed=it))},Modifier.semantics {contentDescription=completeLabel})
                Column(Modifier.weight(1f)) {
                    Text(task.title,style=MaterialTheme.typography.titleMedium)
                    if(task.notes.isNotBlank()) Text(task.notes,style=MaterialTheme.typography.bodySmall,maxLines=3)
                    if(task.dueDate.isNotBlank()) Text(task.dueDate,style=MaterialTheme.typography.labelMedium)
                    Text(label(listOf(Words.Low,Words.Normal,Words.High)[task.priority.coerceIn(0,2)]),style=MaterialTheme.typography.labelSmall)
                }
                IconButton({editing=task}) {Icon(Icons.Outlined.Edit,label(Words.Edit))}
                IconButton({deleting=task}) {Icon(Icons.Outlined.DeleteOutline,label(Words.Delete))}
            }
        }}
    }
    editing?.let { task -> TaskEditor(task,{editing=null}) {vm.saveTask(it);editing=null} }
    deleting?.let { task -> AlertDialog(onDismissRequest={deleting=null},title={Text(label(Words.Delete))},text={Text(task.title)},confirmButton={TextButton({vm.deleteTask(task);deleting=null}) {Text(label(Words.Delete))}},dismissButton={TextButton({deleting=null}) {Text(label(Words.Cancel))}}) }
}
@Composable fun TaskEditor(task: LocalTask, dismiss: ()->Unit, save: (LocalTask)->Unit) {
    var title by rememberSaveable(task.id) {mutableStateOf(task.title)}
    var notes by rememberSaveable(task.id) {mutableStateOf(task.notes)}
    var due by rememberSaveable(task.id) {mutableStateOf(task.dueDate)}
    var priority by rememberSaveable(task.id) {mutableIntStateOf(task.priority)}
    val valid=due.isBlank() || runCatching {LocalDate.parse(due)}.isSuccess
    AlertDialog(onDismissRequest=dismiss,title={Text(label(Words.Edit))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title,{title=it},label={Text(label(Words.TaskTitle))},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(due,{due=it},label={Text(label(Words.DueDate))},isError=!valid,supportingText={if(!valid) Text(label(Words.InvalidDate))},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(notes,{notes=it},label={Text(label(Words.Notes))},modifier=Modifier.fillMaxWidth(),maxLines=5)
            Text(label(Words.Priority))
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { listOf(Words.Low,Words.Normal,Words.High).forEachIndexed { i,w -> FilterChip(priority==i,{priority=i},label={Text(w.en)}) } }
        }
    },confirmButton={TextButton({save(task.copy(title=title,dueDate=due,priority=priority,notes=notes))},enabled=title.isNotBlank() && valid) {Text(label(Words.Save))}},dismissButton={TextButton(dismiss) {Text(label(Words.Cancel))}})
}
