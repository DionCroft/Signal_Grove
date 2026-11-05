package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

fun kanbanBoardActionWord(action:HostKanbanBoardAction)=when(action) {HostKanbanBoardAction.Create->Words.KanbanBoardCreate;HostKanbanBoardAction.Update->Words.KanbanBoardUpdate;HostKanbanBoardAction.Archive->Words.KanbanBoardArchive;HostKanbanBoardAction.Delete->Words.KanbanBoardDelete;HostKanbanBoardAction.Switch->Words.KanbanBoardSwitch}
fun kanbanBoardStateWord(state:String)=when(state) {"acknowledged"->Words.KanbanBoardAcknowledged;"confirmed"->Words.KanbanBoardObserved;else->managementStateWord(state)}
private fun boardFieldWord(key:String)=when(key) {"slug"->Words.KanbanBoardSlug;"name"->Words.KanbanBoardName;"description"->Words.HostProjectDescription;"icon"->Words.ProjectIconInput;"color"->Words.ProjectColorInput;"default_workdir"->Words.KanbanBoardWorkdir;else->Words.HostProjectId}
@Composable private fun BoardInput(key:String,value:String,changed:(String)->Unit,multiline:Boolean=false) {OutlinedTextField(value,changed,Modifier.fillMaxWidth().testTag("board-field-"+key),label={Text(label(boardFieldWord(key)))},singleLine=!multiline,minLines=if(multiline) 2 else 1,maxLines=if(multiline) 6 else 1)}
@Composable private fun BoardChoice(key:String,word:Words,value:Boolean,changed:(Boolean)->Unit) {val text=label(word);Row(Modifier.fillMaxWidth()) {Checkbox(value,changed,Modifier.heightIn(min=48.dp).testTag("board-set-"+key).semantics {contentDescription=text});Text(text,Modifier.weight(1f))}}

@Composable fun KanbanBoardForm(editor:ReviewedKanbanBoardEditor,close:()->Unit,review:(HostKanbanBoardChange)->Unit) {
    val row=editor.expected.boards.singleOrNull {it.slug==editor.slug}
    val editing=editor.action==HostKanbanBoardAction.Update
    var slug by remember(editor) {mutableStateOf(editor.slug.orEmpty())}
    var name by remember(editor) {mutableStateOf(row?.name.orEmpty())}
    var description by remember(editor) {mutableStateOf(row?.description.orEmpty())}
    var icon by remember(editor) {mutableStateOf(row?.icon.orEmpty())}
    var color by remember(editor) {mutableStateOf(row?.color.orEmpty())}
    var directory by remember(editor) {mutableStateOf(row?.workdir.orEmpty())}
    var projectId by remember(editor) {mutableStateOf(row?.projectId.orEmpty())}
    val checked=remember(editor) {mutableStateMapOf<String,Boolean>()}
    val fields=buildMap {for((k,v) in listOf("name" to name,"description" to description,"icon" to icon,"color" to color,"default_workdir" to directory,"project_id" to projectId)) if(!editing || checked[k]==true) put(k,v)}
    val request=runCatching {if(editing) HostKanbanBoardChange.update(editor.expected,editor.slug!!,fields,editor.projects) else HostKanbanBoardChange.create(editor.expected,HostKanbanBoardDraft(slug,fields),editor.projects)}.getOrNull()
    AlertDialog(onDismissRequest=close,title={Text(label(kanbanBoardActionWord(editor.action)))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("board-form"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(editor.expected.owner.url);Text(editor.expected.owner.profile);Text(label(Words.ProjectDraftPrivate));Text(label(Words.KanbanBoardFields));if(row?.archived==true) Text(label(Words.KanbanBoardArchivedControls))}
            item {if(editing) SelectionContainer {Text(editor.slug.orEmpty())} else BoardInput("slug",slug,{slug=it})}
            item {if(editing) BoardChoice("name",Words.ProjectChangeName,checked["name"]==true,{checked["name"]=it});if(!editing || checked["name"]==true) BoardInput("name",name,{name=it})}
            item {if(editing) BoardChoice("description",Words.ProjectChangeDescription,checked["description"]==true,{checked["description"]=it});if(!editing || checked["description"]==true) BoardInput("description",description,{description=it},true)}
            item {if(editing) BoardChoice("icon",Words.ProjectChangeIcon,checked["icon"]==true,{checked["icon"]=it});if(!editing || checked["icon"]==true) BoardInput("icon",icon,{icon=it})}
            item {if(editing) BoardChoice("color",Words.ProjectChangeColor,checked["color"]==true,{checked["color"]=it});if(!editing || checked["color"]==true) BoardInput("color",color,{color=it})}
            item {if(editing) BoardChoice("default_workdir",Words.KanbanBoardSetWorkdir,checked["default_workdir"]==true,{checked["default_workdir"]=it});if(!editing || checked["default_workdir"]==true) BoardInput("default_workdir",directory,{directory=it})}
            item {if(editing) BoardChoice("project_id",Words.KanbanBoardSetProject,checked["project_id"]==true,{checked["project_id"]=it});Text(label(Words.KanbanBoardBindingEffect))}
            if(!editing || checked["project_id"]==true) {
                item {SelectionContainer {Text(projectId)};TextButton({projectId=""},Modifier.heightIn(min=48.dp).testTag("board-project-none")) {Text(label(Words.KanbanBoardNoBinding))};if(editor.projects==null) Text(label(Words.KanbanBoardProjectsFirst))}
                editor.projects?.let {projects->items(projects.projects.filter {!it.archived && !HostProjectChange.collision(projects,it.id)},key={it.id}) {project->TextButton({projectId=project.id},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("board-project-"+project.id)) {Column {Text(project.name);Text(project.id)}}}}
            }
            if(request==null) item {Text(label(Words.KanbanBoardInvalid))}
        }
    },confirmButton={TextButton({request?.let(review)},enabled=request!=null,modifier=Modifier.heightIn(min=48.dp).testTag("board-form-review")) {Text(label(Words.Review))}},dismissButton={TextButton(close,Modifier.heightIn(min=48.dp).testTag("board-form-cancel")) {Text(label(Words.Cancel))}})
}

@Composable fun KanbanBoardChangeReview(review:ReviewedKanbanBoardChange,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    val request=review.request
    var granted by remember(review) {mutableStateOf(false)}
    var deleteSlug by remember(review) {mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text(label(kanbanBoardActionWord(request.action)))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("board-change-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(request.expected.owner.url);Text(request.expected.owner.profile);Text(request.expected.owner.actor);Text(label(Words.KanbanBoardSlug));SelectionContainer {Text(request.slug)}}
            request.selected?.let {b->item {Text(b.name);Text(label(Words.KanbanDbPath));SelectionContainer {Text(b.dbPath)};if(b.archived) Text(label(Words.KanbanBoardArchivedControls))}}
            items(request.values.entries.filter {it.key !in setOf("slug","switch")}.toList(),key={it.key}) {(key,value)->Column {Text(label(boardFieldWord(key)));SelectionContainer {Text(value.jsonPrimitive.content.ifEmpty {label(Words.ProjectEmptyValue)})}}}
            request.project?.let {p->item {Text(p.name);Text(label(Words.HostProjectId));SelectionContainer {Text(p.id)};p.primaryPath?.let {Text(label(Words.HostProjectPrimaryPath));SelectionContainer {Text(it)}}}}
            item {Text(label(when(request.action) {HostKanbanBoardAction.Create->Words.KanbanBoardCreateEffect;HostKanbanBoardAction.Update->Words.KanbanBoardEditEffect;HostKanbanBoardAction.Archive->Words.KanbanBoardArchiveEffect;HostKanbanBoardAction.Delete->Words.KanbanBoardDeleteEffect;HostKanbanBoardAction.Switch->Words.KanbanBoardSwitchEffect}));if(request.action in setOf(HostKanbanBoardAction.Create,HostKanbanBoardAction.Update)) Text(label(Words.KanbanBoardBindingEffect));Text(label(Words.KanbanBoardConcurrency));Text(label(Words.KanbanBoundary))}
            if(request.action==HostKanbanBoardAction.Delete) item {OutlinedTextField(deleteSlug,{deleteSlug=it},Modifier.fillMaxWidth().testTag("board-delete-slug"),label={Text(label(Words.KanbanBoardDeleteSlug))},singleLine=true)}
            item {BoardChoice("grant",Words.KanbanBoardGrant,granted,{granted=it})}
        }
    },confirmButton={TextButton(confirm,enabled=enabled && granted && (request.action!=HostKanbanBoardAction.Delete || deleteSlug==request.slug),modifier=Modifier.heightIn(min=48.dp).testTag("board-change-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.heightIn(min=48.dp).testTag("board-change-cancel")) {Text(label(Words.Cancel))}})
}

@Composable fun KanbanBoardReceiptSummary(summary:JsonObject,state:String) {
    Text(label(kanbanBoardStateWord(state)))
    if(summary["host_acknowledged"]==JsonPrimitive(true) && summary["effect_reported"]!=JsonPrimitive(true)) Text(label(Words.KanbanBoardEffectNotReported))
    if(state!="confirmed" && summary["configuration_observed"]==JsonPrimitive(true)) Text(label(Words.KanbanBoardObserved))
    (summary["fields"] as? JsonArray)?.let {fields->
        val names=mutableListOf<String>()
        for(field in fields) (field as? JsonPrimitive)?.content?.takeIf {it!="switch"}?.let {names.add(label(boardFieldWord(it)))}
        if(names.isNotEmpty()) Text(names.joinToString(" · "))
    }
    Text(label(Words.KanbanBoardReceiptBoundary))
}
@Composable fun KanbanBoardReport(report:HostKanbanBoardReport) {
    val summary=remember(report.receipt) {runCatching {Json.parseToJsonElement(report.receipt.resultSummary).jsonObject}.getOrDefault(JsonObject(emptyMap()))}
    OutlinedCard(Modifier.fillMaxWidth().testTag("board-change-report")) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label(mcpActionWord(report.receipt.action)),style=MaterialTheme.typography.titleMedium);KanbanBoardReceiptSummary(summary,report.receipt.state)
        report.archivePath?.let {Text(label(Words.KanbanBoardArchivePath));SelectionContainer {Text(it)}}
    }}
}


@Composable fun KanbanBoardControls(board:HostKanbanBoard,url:String,profile:String,blocked:Words?,close:()->Unit,edit:()->Unit,action:(HostKanbanBoardAction)->Unit) {
    AlertDialog(onDismissRequest=close,title={Text(board.name.ifBlank {board.slug})},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("board-controls"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(url);Text(profile);SelectionContainer {Text(board.slug)};Text(label(Words.KanbanDbPath));SelectionContainer {Text(board.dbPath)};Text(label(Words.KanbanBoundary));blocked?.let {Text(label(it))}}
            if(board.archived) item {Text(label(Words.KanbanBoardArchivedControls))}
            item {TextButton(edit,enabled=blocked==null,modifier=Modifier.heightIn(min=48.dp).testTag("board-action-"+board.slug+"-Update")) {Text(label(Words.KanbanBoardUpdate))}}
            for(kind in listOf(HostKanbanBoardAction.Switch,HostKanbanBoardAction.Archive,HostKanbanBoardAction.Delete)) item {
                val allowed=blocked==null && (kind!=HostKanbanBoardAction.Archive || !board.archived && board.slug!="default") && (kind!=HostKanbanBoardAction.Delete || board.slug!="default") && (kind!=HostKanbanBoardAction.Switch || !board.archived)
                TextButton({action(kind)},enabled=allowed,modifier=Modifier.heightIn(min=48.dp).testTag("board-action-"+board.slug+"-"+kind.name)) {Text(label(kanbanBoardActionWord(kind)))}
                if(kind in setOf(HostKanbanBoardAction.Archive,HostKanbanBoardAction.Delete) && board.slug=="default") Text(label(Words.KanbanBoardDefaultProtected))
                if(kind in setOf(HostKanbanBoardAction.Archive,HostKanbanBoardAction.Switch) && board.archived) Text(label(Words.KanbanBoardArchivedControls))
            }
        }
    },confirmButton={TextButton(close,Modifier.heightIn(min=48.dp).testTag("board-controls-close")) {Text(label(Words.Close))}})
}
