package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.data.*

@Composable fun KanbanTaskCreateForm(editor:ReviewedKanbanTaskCreate,close:()->Unit,review:(HostKanbanTaskChange)->Unit) {
    var title by remember(editor) {mutableStateOf("")}
    var body by remember(editor) {mutableStateOf("")}
    var assignee by remember(editor) {mutableStateOf("")}
    var tenant by remember(editor) {mutableStateOf("")}
    var priority by remember(editor) {mutableStateOf("0")}
    var workspace by remember(editor) {mutableStateOf<String?>(null)}
    var path by remember(editor) {mutableStateOf("")}
    var triage by remember(editor) {mutableStateOf(false)}
    var runtime by remember(editor) {mutableStateOf("")}
    var skills by remember(editor) {mutableStateOf("")}
    var goal by remember(editor) {mutableStateOf(false)}
    var turns by remember(editor) {mutableStateOf("")}
    var model by remember(editor) {mutableStateOf("")}
    var provider by remember(editor) {mutableStateOf("")}
    var reasoning by remember(editor) {mutableStateOf<String?>(null)}
    var projectId by remember(editor) {mutableStateOf<String?>(null)}
    var advanced by remember(editor) {mutableStateOf(false)}
    var parentPicker by remember(editor) {mutableStateOf(false)}
    var parentQuery by remember(editor) {mutableStateOf("")}
    val selected=remember(editor) {mutableStateListOf<String>()}
    val flags=remember(editor) {mutableStateMapOf<String,Boolean>()}
    fun set(key:String)=flags[key]==true
    val draft=runCatching {HostKanbanTaskDraft(title,body.takeIf {set("body")},assignee.takeIf {set("assignee")},tenant.takeIf {set("tenant")},priority.toInt(),workspace,path.takeIf {set("path")},selected.toList(),triage,if(set("runtime")) runtime.toInt() else null,if(set("skills")) skills.lines().map {it.trim()}.filter {it.isNotEmpty()} else null,goal,if(set("turns")) turns.toInt() else null,model.takeIf {set("model")},provider.takeIf {set("provider")},reasoning,projectId)}.getOrNull()
    // A private nonce is generated once for an unchanged draft, then frozen with its review.
    val request=remember(editor,draft) {draft?.let {runCatching {HostKanbanTaskChange.create(editor.inventory,editor.snapshot,it,editor.projects)}.getOrNull()}}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.KanbanTaskCreate))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("task-create-form"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(editor.inventory.owner.url);Text(editor.inventory.owner.profile);Text(editor.snapshot.board.slug);Text(label(Words.ProjectDraftPrivate))}
            item {TaskInput("title",Words.KanbanTaskTitle,title,{title=it})}
            item {TaskChoice("body",Words.KanbanTaskSetBodyCreate,set("body"),{flags["body"]=it});if(set("body")) TaskInput("body",Words.KanbanBody,body,{body=it},true)}
            item {TaskInput("priority",Words.KanbanPriority,priority,{priority=it})}
            item {TaskChoice("triage",Words.KanbanTaskTriageCreate,triage,{triage=it});Text(label(Words.KanbanTaskCreateEffect))}
            item {TextButton({parentPicker=!parentPicker},Modifier.heightIn(min=48.dp).testTag("task-create-parents")) {Text(label(Words.KanbanTaskPickParents))};Text(selected.size.toString());Text(label(Words.KanbanTaskParentLimit))}
            if(parentPicker) {
                item {TaskInput("parent-search",Words.Search,parentQuery,{parentQuery=it})}
                items(editor.snapshot.columns.flatMap {it.tasks}.filter {parentQuery.isBlank() || it.id.contains(parentQuery,true) || it.title.contains(parentQuery,true)},key={"parent:"+it.id}) {parent->
                    val description=label(Words.KanbanParents)+" · "+parent.title+" · "+parent.id
                    Row(Modifier.fillMaxWidth()) {Checkbox(parent.id in selected,{checked->if(checked) {if(parent.id !in selected && selected.size<128) selected.add(parent.id)} else selected.remove(parent.id)},enabled=parent.id in selected || selected.size<128,modifier=Modifier.heightIn(min=48.dp).testTag("task-create-parent-"+parent.id).semantics {contentDescription=description});Column(Modifier.weight(1f)) {Text(parent.title);Text(parent.id);KanbanStatus(parent.status)}}
                }
            }
            item {TextButton({advanced=!advanced},Modifier.heightIn(min=48.dp).testTag("task-create-advanced")) {Text(label(Words.KanbanTaskAdvanced))}}
            if(advanced) {
                item {TaskChoice("assignee",Words.KanbanTaskSetAssignee,set("assignee"),{flags["assignee"]=it});if(set("assignee")) TaskInput("assignee",Words.KanbanAssignee,assignee,{assignee=it})}
                item {TaskChoice("tenant",Words.KanbanTaskSetTenant,set("tenant"),{flags["tenant"]=it});if(set("tenant")) TaskInput("tenant",Words.KanbanTenant,tenant,{tenant=it})}
                item {Text(label(Words.KanbanTaskWorkspace))}
                for((wire,word) in listOf(null to Words.KanbanTaskWorkspaceDefault,"scratch" to Words.KanbanTaskScratch,"dir" to Words.KanbanTaskDirectory,"worktree" to Words.KanbanTaskWorktree)) item {Row {RadioButton(workspace==wire,{workspace=wire},Modifier.heightIn(min=48.dp).testTag("task-create-workspace-"+(wire ?: "default")));Text(label(word),Modifier.weight(1f))}}
                item {TaskChoice("path",Words.KanbanTaskSetPath,set("path"),{flags["path"]=it});if(set("path")) TaskInput("path",Words.KanbanTaskPath,path,{path=it})}
                item {Text(label(Words.KanbanProject));Text(label(Words.KanbanTaskProjectEffect))}
                item {Row {RadioButton(projectId==null,{projectId=null},Modifier.heightIn(min=48.dp).testTag("task-create-project-inherit"));Text(label(Words.KanbanTaskProjectInherit),Modifier.weight(1f))}}
                item {Row {RadioButton(projectId=="",{projectId=""},Modifier.heightIn(min=48.dp).testTag("task-create-project-none"));Text(label(Words.KanbanTaskProjectOptOut),Modifier.weight(1f))}}
                if(editor.projects==null) item {Text(label(Words.KanbanTaskProjectRead))}
                editor.projects?.let {projects->items(projects.projects.filter {!it.archived && !HostProjectChange.collision(projects,it.id)},key={"project:"+it.id}) {project->Row {RadioButton(projectId==project.id,{projectId=project.id},Modifier.heightIn(min=48.dp).testTag("task-create-project-"+project.id));Column(Modifier.weight(1f)) {Text(project.name);Text(project.id);project.primaryPath?.let {Text(it)}}}}}
                item {TaskChoice("runtime",Words.KanbanTaskSetRuntime,set("runtime"),{flags["runtime"]=it});if(set("runtime")) TaskInput("runtime",Words.KanbanRuntime,runtime,{runtime=it})}
                item {TaskChoice("skills",Words.KanbanTaskSetSkills,set("skills"),{flags["skills"]=it});if(set("skills")) {TaskInput("skills",Words.KanbanSkills,skills,{skills=it},true);Text(label(Words.KanbanTaskSkillLines))}}
                item {TaskChoice("goal",Words.KanbanGoal,goal,{goal=it})}
                item {TaskChoice("turns",Words.KanbanTaskSetTurns,set("turns"),{flags["turns"]=it});if(set("turns")) TaskInput("turns",Words.KanbanGoalTurns,turns,{turns=it})}
                item {TaskChoice("model",Words.KanbanTaskSetModel,set("model"),{flags["model"]=it});if(set("model")) TaskInput("model",Words.KanbanModel,model,{model=it})}
                item {TaskChoice("provider",Words.KanbanTaskSetProvider,set("provider"),{flags["provider"]=it});if(set("provider")) TaskInput("provider",Words.KanbanProvider,provider,{provider=it})}
                item {Text(label(Words.KanbanReasoning));Row {RadioButton(reasoning==null,{reasoning=null},Modifier.heightIn(min=48.dp).testTag("task-create-effort-default"));Text(label(Words.ServerDefault),Modifier.weight(1f))}}
                items(HostAuxEffort.entries.filter {it.wire!=null},key={"effort:"+it.name}) {effort->Row {RadioButton(reasoning==effort.wire,{reasoning=effort.wire},Modifier.heightIn(min=48.dp).testTag("task-create-effort-"+effort.wire));Text(label(hostAuxEffortWord(effort)),Modifier.weight(1f))}}
            }
            if(request==null) item {Text(label(Words.KanbanTaskCreateInvalid));if(editor.projects==null && projectId!="" && (projectId!=null || editor.snapshot.board.projectId!=null && workspace!="scratch")) Text(label(Words.KanbanTaskProjectRead))}
        }
    },confirmButton={TextButton({request?.let(review)},enabled=request!=null,modifier=Modifier.heightIn(min=48.dp).testTag("task-create-review")) {Text(label(Words.Review))}},dismissButton={TextButton(close,Modifier.heightIn(min=48.dp).testTag("task-create-cancel")) {Text(label(Words.Cancel))}})
}
