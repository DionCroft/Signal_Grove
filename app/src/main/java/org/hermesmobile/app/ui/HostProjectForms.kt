package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*

@Composable private fun ProjectInput(word:Words,value:String,changed:(String)->Unit,tag:String,multiline:Boolean=false) {
    OutlinedTextField(value,changed,Modifier.fillMaxWidth().testTag(tag),label={Text(label(word))},singleLine=!multiline,minLines=if(multiline) 2 else 1,maxLines=if(multiline) 6 else 1)
}

@Composable private fun ProjectChoice(word:Words,checked:Boolean,changed:(Boolean)->Unit,tag:String) {
    Row(Modifier.fillMaxWidth()) {Checkbox(checked,changed,Modifier.heightIn(min=48.dp).testTag(tag));Text(label(word),Modifier.weight(1f))}
}

/** A private draft; no gateway or durable write occurs while this dialog is open. */
@Composable fun HostProjectForm(editor:ReviewedHostProjectEditor,close:()->Unit,review:(HostProjectChange)->Unit) {
    val row=editor.expected.projects.singleOrNull {it.id==editor.id}
    val editing=editor.action==HostProjectAction.Update
    val folder=editor.action==HostProjectAction.AddFolder
    var name by remember(editor) {mutableStateOf(if(editing) row!!.name else "")}
    var slug by remember(editor) {mutableStateOf("")}
    var description by remember(editor) {mutableStateOf(if(editing) row!!.description.orEmpty() else "")}
    var icon by remember(editor) {mutableStateOf(if(editing) row!!.icon.orEmpty() else "")}
    var color by remember(editor) {mutableStateOf(if(editing) row!!.color.orEmpty() else "")}
    var board by remember(editor) {mutableStateOf(if(editing) row!!.boardSlug.orEmpty() else "")}
    var paths by remember(editor) {mutableStateOf("")}
    var primaryPath by remember(editor) {mutableStateOf("")}
    var folderLabel by remember(editor) {mutableStateOf("")}
    var labelSet by remember(editor) {mutableStateOf(false)}
    var primary by remember(editor) {mutableStateOf(false)}
    val changed=remember(editor) {mutableStateMapOf<String,Boolean>()}
    fun edits()=buildMap<String,String> {
        for((key,value) in listOf("name" to name,"description" to description,"icon" to icon,"color" to color,"board_slug" to board)) if(changed[key]==true) put(key,value)
    }
    val request=runCatching {
        when(editor.action) {
            HostProjectAction.Create->HostProjectChange.create(editor.expected,HostProjectCreateDraft(name,slug.takeUnless {it.isEmpty()},description.takeIf {changed["description"]==true},icon.takeUnless {it.isEmpty()},color.takeUnless {it.isEmpty()},board.takeUnless {it.isEmpty()},if(paths.isEmpty()) emptyList() else paths.lines(),primaryPath.takeUnless {it.isEmpty()}))
            HostProjectAction.Update->HostProjectChange.update(editor.expected,editor.id!!,edits())
            HostProjectAction.AddFolder->HostProjectChange.addFolder(editor.expected,editor.id!!,paths,folderLabel.takeIf {labelSet},primary)
            else->error("Unsupported editor")
        }
    }.getOrNull()
    AlertDialog(onDismissRequest=close,title={Text(label(mcpActionWord(editor.action.receiptAction)))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("project-form"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(editor.expected.owner.url);Text(editor.expected.owner.profile);Text(label(Words.ProjectDraftPrivate));if(editing) Text(label(Words.ProjectUpdateFields))}
            row?.let {selected->item {SelectionContainer {Text(selected.id+"\n"+selected.slug)}}}
            if(folder) {
                item {ProjectInput(Words.ProjectPathInput,paths,{paths=it},"project-form-path");Text(label(Words.ProjectPathsEffect))}
                item {ProjectChoice(Words.ProjectLabelSet,labelSet,{labelSet=it},"project-form-label-set");if(labelSet) ProjectInput(Words.ProjectLabelInput,folderLabel,{folderLabel=it},"project-form-label",true)}
                item {ProjectChoice(Words.ProjectPrimaryInput,primary,{primary=it},"project-form-primary");Text(label(Words.ProjectFolderPrimaryEffect))}
            } else {
                item {if(editing) ProjectChoice(Words.ProjectChangeName,changed["name"]==true,{changed["name"]=it},"project-form-name-set");if(!editing || changed["name"]==true) ProjectInput(Words.ProjectNameInput,name,{name=it},"project-form-name")}
                if(!editing) item {ProjectInput(Words.ProjectSlugInput,slug,{slug=it},"project-form-slug");Text(label(Words.ProjectSlugEffect))}
                item {ProjectChoice(Words.ProjectChangeDescription,changed["description"]==true,{changed["description"]=it},"project-form-description-set");if(changed["description"]==true) ProjectInput(Words.HostProjectDescription,description,{description=it},"project-form-description",true)}
                item {if(editing) ProjectChoice(Words.ProjectChangeIcon,changed["icon"]==true,{changed["icon"]=it},"project-form-icon-set");if(!editing || changed["icon"]==true) ProjectInput(Words.ProjectIconInput,icon,{icon=it},"project-form-icon")}
                item {if(editing) ProjectChoice(Words.ProjectChangeColor,changed["color"]==true,{changed["color"]=it},"project-form-color-set");if(!editing || changed["color"]==true) ProjectInput(Words.ProjectColorInput,color,{color=it},"project-form-color")}
                item {if(editing) ProjectChoice(Words.ProjectChangeBoard,changed["board_slug"]==true,{changed["board_slug"]=it},"project-form-board-set");if(!editing || changed["board_slug"]==true) ProjectInput(Words.ProjectBoardInput,board,{board=it},"project-form-board")}
                if(!editing) {
                    item {ProjectInput(Words.ProjectPathsInput,paths,{paths=it},"project-form-paths",true);Text(label(Words.ProjectPathsEffect))}
                    item {ProjectInput(Words.ProjectPrimaryPathInput,primaryPath,{primaryPath=it},"project-form-primary-path")}
                }
            }
            if(request==null) item {Text(label(if(editing && edits().isEmpty()) Words.ProjectNoEdits else Words.ProjectDraftInvalid))}
        }
    },confirmButton={TextButton({request?.let(review)},enabled=request!=null,modifier=Modifier.heightIn(min=48.dp).testTag("project-form-review")) {Text(label(Words.Review))}},dismissButton={TextButton(close,Modifier.heightIn(min=48.dp).testTag("project-form-cancel")) {Text(label(Words.Cancel))}})
}

fun projectFieldWord(key:String)=when(key) {
    "name"->Words.ProjectNameInput;"slug"->Words.HostProjectSlug;"description"->Words.HostProjectDescription;"icon"->Words.ProjectIconInput;"color"->Words.ProjectColorInput;"board_slug"->Words.HostProjectBoard
    "folders"->Words.HostProjectFolders;"primary_path"->Words.HostProjectPrimaryPath;"path"->Words.ProjectPathInput;"label"->Words.ProjectLabelInput;"is_primary"->Words.ProjectPrimaryInput
    else->Words.HostProjects
}

/** The exact private values are shown here, never reconstructed from a durable receipt. */
@Composable fun HostProjectChangeReview(review:ReviewedHostProjectChange,enabled:Boolean,close:()->Unit,confirm:()->Unit) {
    val request=review.request
    var approved by remember(review) {mutableStateOf(false)}
    AlertDialog(onDismissRequest=close,title={Text(label(mcpActionWord(request.action.receiptAction)))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("project-change-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(review.target.url);Text(review.target.profile);Text(review.target.actor)}
            request.selected?.let {row->item {Text(row.name);Text(label(Words.HostProjectId));SelectionContainer {Text(row.id)};Text(label(Words.HostProjectSlug));SelectionContainer {Text(row.slug)};if(row.archived) Text(label(Words.HostProjectArchived))}}
            if(request.action==HostProjectAction.ClearActive) item {Text(label(Words.HostProjectActive));SelectionContainer {Text(request.expected.activeId.orEmpty())}}
            items(request.values.entries.filter {it.key !in setOf("id","use","restore")}.sortedBy {it.key},key={it.key}) {(key,value)->
                Column {Text(label(projectFieldWord(key)),style=MaterialTheme.typography.titleSmall)
                    when(value) {
                        is JsonArray->value.forEach {path->SelectionContainer {Text(path.jsonPrimitive.content)}}
                        else->if(value==JsonPrimitive(true) || value==JsonPrimitive(false)) Text(label(if(value==JsonPrimitive(true)) Words.Yes else Words.No)) else SelectionContainer {Text(value.jsonPrimitive.content.ifEmpty {label(Words.ProjectEmptyValue)})}
                    }
                }
            }
            item {Text(label(when(request.action) {
                HostProjectAction.Create->Words.ProjectCreateEffect
                HostProjectAction.Update->Words.ProjectUpdateEffect
                HostProjectAction.AddFolder->Words.ProjectAddFolderEffect
                HostProjectAction.RemoveFolder->Words.ProjectRemoveFolderEffect
                HostProjectAction.SetPrimary->Words.ProjectSetPrimaryEffect
                HostProjectAction.Archive,HostProjectAction.Restore->Words.ProjectArchiveEffect
                HostProjectAction.Delete->Words.ProjectDeleteEffect
                HostProjectAction.SetActive,HostProjectAction.ClearActive->Words.ProjectActiveEffect
            }))}
            item {Text(label(Words.ProjectConcurrency));Text(label(Words.HostProjectsBoundary))}
            item {ProjectChoice(Words.ProjectChangeGrant,approved,{approved=it},"project-change-approve")}
        }
    },confirmButton={TextButton(confirm,enabled=enabled && approved,modifier=Modifier.heightIn(min=48.dp).testTag("project-change-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.heightIn(min=48.dp).testTag("project-change-cancel")) {Text(label(Words.Cancel))}})
}

@Composable fun HostProjectChangeStatus(report:HostProjectChangeReport) {
    val receipt=report.receipt
    val summary=remember(receipt) {runCatching {Json.parseToJsonElement(receipt.resultSummary).jsonObject}.getOrDefault(JsonObject(emptyMap()))}
    OutlinedCard(Modifier.fillMaxWidth().testTag("project-change-report")) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label(mcpActionWord(receipt.action)),style=MaterialTheme.typography.titleMedium)
        Text(label(if(receipt.state=="refused") Words.ProjectChangeRefused else managementStateWord(receipt.state)))
        ProjectReceiptSummary(summary)
        if(receipt.action==HostProjectAction.Create.receiptAction && summary["host_acknowledged"]==JsonPrimitive(true) && summary.text("canonical_id_hash").isEmpty()) Text(label(Words.ProjectNoReturnedProject))
    }}
}
