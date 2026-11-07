package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.data.*
import java.text.DateFormat
import java.util.Date

private fun projectTime(ms:Long)=DateFormat.getDateTimeInstance().format(Date(ms))
@Composable fun HostProjectsDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    val visible=vm.visibleHostProjects(s)
    val receiptRows by vm.receipts.collectAsStateWithLifecycle()
    val pending=vm.visibleReceipts(s,receiptRows).any {it.state in setOf("sending","unknown")}
    val generation by vm.reviewGeneration.collectAsStateWithLifecycle()
    val home=vm.projectReceiptHome(s)
    var readReview by remember(s.apiScope,s.url,s.profile,s.identity,home,generation) {mutableStateOf<ReviewedHostProjectsRead?>(null)}
    var editor by remember(s.apiScope,s.url,s.profile,s.identity,home,generation,visible.inventory,pending) {mutableStateOf<ReviewedHostProjectEditor?>(null)}
    var change by remember(s.apiScope,s.url,s.profile,s.identity,home,generation,visible.inventory,pending) {mutableStateOf<ReviewedHostProjectChange?>(null)}
    val read=if(vm.hostProjectsContextCurrent(s)) vm.prepareHostProjectsRead() else null
    fun offer(request:HostProjectChange) {val next=vm.prepareHostProjectChange(request);if(next==null) vm.hostProjectReviewChanged() else {editor=null;change=next}}
    fun dismiss() {editor=null;change=null;readReview=null;close()}
    AlertDialog(onDismissRequest=::dismiss,title={Text(label(Words.HostProjects))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("host-projects-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {if(vm.hostProjectsContextCurrent(s)) {Text(s.url);Text(s.profile)};Text(label(Words.HostProjectsBoundary))}
            item {
                TextButton({readReview=read},enabled=read!=null,modifier=Modifier.heightIn(min=48.dp).testTag("host-projects-read")) {Text(label(Words.HostProjectsRead))}
                if(read==null && !s.busy) Text(label(Words.HostProjectsReload))
            }
            item {
                val create=vm.prepareHostProjectEditor(HostProjectAction.Create)
                TextButton({editor=create},enabled=create!=null,modifier=Modifier.heightIn(min=48.dp).testTag("project-create")) {Text(label(Words.ProjectCreate))}
                vm.hostProjectBlockReason(s)?.let {Text(label(it))}
            }
            if(pending) item {Text(label(Words.ManagementOutcomePending));TextButton(::dismiss,Modifier.heightIn(min=48.dp).testTag("project-return-receipts")) {Text(label(Words.ProjectReturnReceipts))}}
            if(s.busy) item {QuietProgress();Text(label(Words.Loading));TextButton(vm::cancel,modifier=Modifier.heightIn(min=48.dp).testTag("host-projects-cancel-work")) {Text(label(Words.Cancel))}}
            visible.report?.let {report->item {HostProjectChangeStatus(report)}}
            visible.inventory?.let {snapshot->
                item {Text(label(Words.HostProjectsSnapshot).replace("{time}",projectTime(snapshot.observedAt)))}
                item {
                    TextButton({offer(HostProjectChange.action(snapshot,null,HostProjectAction.ClearActive))},enabled=vm.hostProjectBlockReason(s)==null && snapshot.activeId!=null,modifier=Modifier.heightIn(min=48.dp).testTag("project-clear-active")) {Text(label(Words.ProjectClearActive))}
                    if(snapshot.activeId==null) Text(label(Words.ProjectNoActive))
                }
                if(snapshot.activeId!=null && snapshot.activeProject==null) item {Text(label(Words.HostProjectMissingActive));SelectionContainer {Text(snapshot.activeId)}}
                if(snapshot.projects.isEmpty()) item {Text(label(Words.EmptyInventory))}
                items(snapshot.projects,key={"project:"+it.id}) {project->
                    val details=vm.prepareHostProjectDetail(project)
                    OutlinedCard(Modifier.fillMaxWidth().testTag("host-project-"+project.id)) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(project.name,style=MaterialTheme.typography.titleMedium)
                        Text(project.slug);Text(project.id,style=MaterialTheme.typography.labelSmall)
                        if(project.id==snapshot.activeId) Text(label(Words.HostProjectActive))
                        if(project.archived) Text(label(Words.HostProjectArchived))
                        TextButton({details?.let(vm::loadHostProjectDetail)},enabled=details!=null,modifier=Modifier.heightIn(min=48.dp).testTag("host-project-details-"+project.id)) {Text(label(Words.HostProjectDetails))}
                    }}
                }
            }
            s.error?.let {message->item {Text(label(message),color=MaterialTheme.colorScheme.error)}}
        }
    },confirmButton={TextButton(::dismiss,Modifier.heightIn(min=48.dp)) {Text(label(Words.Close))}})
    readReview?.takeIf(vm::hostProjectReadCurrent)?.let {frozen->AlertDialog(onDismissRequest={readReview=null},title={Text(label(Words.HostProjectsRead))},text={
        LazyColumn(Modifier.heightIn(max=480.dp).testTag("host-projects-read-review")) {
            item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor);Text(label(Words.HostProjectsReadEffect));Text(label(Words.HostProjectsBoundary))}
        }
    },confirmButton={TextButton({readReview=null;vm.loadHostProjects(frozen)},enabled=!s.busy,modifier=Modifier.heightIn(min=48.dp).testTag("host-projects-read-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({readReview=null},modifier=Modifier.heightIn(min=48.dp).testTag("host-projects-read-cancel")) {Text(label(Words.Cancel))}})}
    visible.detail?.let {detail->HostProjectDetailsDialog(detail,vm.hostProjectBlockReason(s,detail.project),visible.inventory?.activeId==detail.project.id,vm::closeHostProjectDetail,
        edit={action->editor=vm.prepareHostProjectEditor(action,detail.project.id)},
        action={action->visible.inventory?.let {offer(HostProjectChange.action(it,detail.project.id,action))}},
        folder={path,primary->visible.inventory?.let {offer(HostProjectChange.folder(it,detail.project.id,path,primary))}})}
    editor?.takeIf(vm::hostProjectEditorCurrent)?.let {draft->HostProjectForm(draft,{editor=null},::offer)}
    change?.takeIf(vm::hostProjectChangeCurrent)?.let {frozen->HostProjectChangeReview(frozen,!s.busy,{change=null}) {change=null;vm.performHostProjectChange(frozen)}}
}

@Composable private fun HostProjectDetailsDialog(detail:HostProjectDetail,blocked:Words?,active:Boolean,close:()->Unit,edit:(HostProjectAction)->Unit,action:(HostProjectAction)->Unit,folder:(String,Boolean)->Unit) {
    val project=detail.project
    AlertDialog(onDismissRequest=close,title={Text(project.name)},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("host-project-detail"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(detail.owner.url);Text(detail.owner.profile);Text(label(Words.HostProjectsSnapshot).replace("{time}",projectTime(detail.observedAt)))}
            item {Text(label(Words.HostProjectId));SelectionContainer {Text(project.id)};Text(label(Words.HostProjectSlug));SelectionContainer {Text(project.slug)}}
            item {Text(label(Words.HostProjectCreated).replace("{time}",projectTime(project.createdAtSeconds*1000)))}
            if(project.archived) item {Text(label(Words.HostProjectArchived))}
            if(active) item {Text(label(Words.HostProjectActive))}
            item {
                blocked?.let {Text(label(it))}
                for(kind in listOf(HostProjectAction.Update,HostProjectAction.AddFolder)) TextButton({edit(kind)},enabled=blocked==null,modifier=Modifier.heightIn(min=48.dp).testTag("project-action-"+kind.name)) {Text(label(mcpActionWord(kind.receiptAction)))}
                for(kind in listOf(if(project.archived) HostProjectAction.Restore else HostProjectAction.Archive,HostProjectAction.Delete,HostProjectAction.SetActive)) TextButton({action(kind)},enabled=blocked==null && (kind!=HostProjectAction.SetActive || !active),modifier=Modifier.heightIn(min=48.dp).testTag("project-action-"+kind.name)) {Text(label(mcpActionWord(kind.receiptAction)))}
                if(active) Text(label(Words.ProjectAlreadyActive))
            }
            project.icon?.let {value->item {Text(label(Words.HostProjectIcon));SelectionContainer {Text(value)}}}
            project.color?.let {value->item {Text(label(Words.HostProjectColor));SelectionContainer {Text(value)}}}
            project.boardSlug?.let {value->item {Text(label(Words.HostProjectBoard));SelectionContainer {Text(value)}}}
            project.description?.let {text->item {Text(label(Words.HostProjectDescription));SelectionContainer {Text(text)}}}
            project.primaryPath?.let {path->item {Text(label(Words.HostProjectPrimaryPath));SelectionContainer {Text(path)}}}
            item {Text(label(Words.HostProjectFolders),style=MaterialTheme.typography.titleMedium);if(project.folders.isEmpty()) Text(label(Words.EmptyInventory))}
            itemsIndexed(project.folders,key={_,it->it.path}) {index,attached->OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                attached.label?.let {Text(it)};SelectionContainer {Text(attached.path)}
                if(attached.primary) Text(label(Words.HostProjectPrimary))
                attached.addedAtSeconds?.let {Text(label(Words.HostProjectAdded).replace("{time}",projectTime(it*1000)))}
                TextButton({folder(attached.path,true)},enabled=blocked==null && !(attached.primary && project.primaryPath==attached.path),modifier=Modifier.heightIn(min=48.dp).testTag("project-folder-primary-$index")) {Text(label(Words.ProjectSetPrimary))}
                TextButton({folder(attached.path,false)},enabled=blocked==null,modifier=Modifier.heightIn(min=48.dp).testTag("project-folder-remove-$index")) {Text(label(Words.ProjectRemoveFolder))}
            }}}
        }
    },confirmButton={TextButton(close,modifier=Modifier.heightIn(min=48.dp).testTag("host-project-detail-close")) {Text(label(Words.Close))}})
}
