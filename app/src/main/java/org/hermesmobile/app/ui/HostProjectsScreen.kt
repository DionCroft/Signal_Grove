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
import org.hermesmobile.app.data.*
import java.text.DateFormat
import java.util.Date

private fun projectTime(ms:Long)=DateFormat.getDateTimeInstance().format(Date(ms))
@Composable fun HostProjectsDialog(vm:ManagementViewModel,s:ManagementState,close:()->Unit) {
    var review by remember(s.apiScope,s.url,s.profile,s.identity?.provider,s.identity?.userId) {mutableStateOf<ReviewedHostProjectsRead?>(null)}
    val visible=vm.visibleHostProjects(s)
    val read=if(vm.hostProjectsContextCurrent(s)) vm.prepareHostProjectsRead() else null
    AlertDialog(onDismissRequest=close,title={Text(label(Words.HostProjects))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("host-projects-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {if(vm.hostProjectsContextCurrent(s)) {Text(s.url);Text(s.profile)};Text(label(Words.HostProjectsBoundary))}
            item {
                TextButton({review=read},enabled=read!=null,modifier=Modifier.heightIn(min=48.dp).testTag("host-projects-read")) {Text(label(Words.HostProjectsRead))}
                if(read==null && !s.busy) Text(label(Words.HostProjectsReload))
            }
            if(s.busy) item {QuietProgress();Text(label(Words.Loading));TextButton(vm::cancel,modifier=Modifier.heightIn(min=48.dp).testTag("host-projects-cancel-work")) {Text(label(Words.Cancel))}}
            visible.inventory?.let {snapshot->
                item {Text(label(Words.HostProjectsSnapshot).replace("{time}",projectTime(snapshot.observedAt)))}
                if(snapshot.activeId!=null && snapshot.activeProject==null) item {Text(label(Words.HostProjectMissingActive));Text(snapshot.activeId)}
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
    },confirmButton={TextButton(close,modifier=Modifier.heightIn(min=48.dp)) {Text(label(Words.Close))}})
    review?.takeIf(vm::hostProjectReadCurrent)?.let {frozen->AlertDialog(onDismissRequest={review=null},title={Text(label(Words.HostProjectsRead))},text={
        LazyColumn(Modifier.heightIn(max=480.dp).testTag("host-projects-read-review")) {
            item {Text(frozen.target.url);Text(frozen.target.profile);Text(frozen.target.actor);Text(label(Words.HostProjectsReadEffect));Text(label(Words.HostProjectsBoundary))}
        }
    },confirmButton={TextButton({review=null;vm.loadHostProjects(frozen)},enabled=!s.busy,modifier=Modifier.heightIn(min=48.dp).testTag("host-projects-read-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton({review=null},modifier=Modifier.heightIn(min=48.dp).testTag("host-projects-read-cancel")) {Text(label(Words.Cancel))}})}
    visible.detail?.let {detail->HostProjectDetailsDialog(detail,vm::closeHostProjectDetail)}
}

@Composable private fun HostProjectDetailsDialog(detail:HostProjectDetail,close:()->Unit) {
    val project=detail.project
    AlertDialog(onDismissRequest=close,title={Text(project.name)},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("host-project-detail"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(detail.owner.url);Text(detail.owner.profile);Text(label(Words.HostProjectsSnapshot).replace("{time}",projectTime(detail.observedAt)))}
            item {Text(label(Words.HostProjectId));SelectionContainer {Text(project.id)};Text(label(Words.HostProjectSlug));SelectionContainer {Text(project.slug)}}
            item {Text(label(Words.HostProjectCreated).replace("{time}",projectTime(project.createdAtSeconds*1000)))}
            if(project.archived) item {Text(label(Words.HostProjectArchived))}
            project.icon?.let {value->item {Text(label(Words.HostProjectIcon));SelectionContainer {Text(value)}}}
            project.color?.let {value->item {Text(label(Words.HostProjectColor));SelectionContainer {Text(value)}}}
            project.boardSlug?.let {value->item {Text(label(Words.HostProjectBoard));SelectionContainer {Text(value)}}}
            project.description?.let {text->item {Text(label(Words.HostProjectDescription));SelectionContainer {Text(text)}}}
            project.primaryPath?.let {path->item {Text(label(Words.HostProjectPrimaryPath));SelectionContainer {Text(path)}}}
            item {Text(label(Words.HostProjectFolders),style=MaterialTheme.typography.titleMedium);if(project.folders.isEmpty()) Text(label(Words.EmptyInventory))}
            items(project.folders,key={it.path}) {folder->OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                folder.label?.let {Text(it)};SelectionContainer {Text(folder.path)}
                if(folder.primary) Text(label(Words.HostProjectPrimary))
                folder.addedAtSeconds?.let {Text(label(Words.HostProjectAdded).replace("{time}",projectTime(it*1000)))}
            }}}
        }
    },confirmButton={TextButton(close,modifier=Modifier.heightIn(min=48.dp).testTag("host-project-detail-close")) {Text(label(Words.Close))}})
}
