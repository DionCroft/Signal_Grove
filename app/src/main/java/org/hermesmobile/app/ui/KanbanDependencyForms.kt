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
import org.hermesmobile.app.data.HostKanbanLinkedTask

@Composable fun KanbanDependencyPicker(picker:ReviewedKanbanDependencyPicker,close:()->Unit,select:(String)->Unit) {
    var query by remember(picker) {mutableStateOf("")}
    val child=picker.subject
    val cards=picker.snapshot.columns.flatMap {it.tasks}.map {HostKanbanLinkedTask(it.id,it.title,it.status)}
    val ids=if(picker.remove) child.parents else cards.filter {it.id!=child.task.id && it.id !in child.parents}.map {it.id}
    val candidates=ids.filter {id->query.isBlank() || id.contains(query,true) || (cards+child.linked).any {it.id==id && it.title.contains(query,true)}}
    AlertDialog(onDismissRequest=close,title={Text(label(if(picker.remove) Words.KanbanTaskUnlink else Words.KanbanTaskLink))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("dependency-picker"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(picker.inventory.owner.url);Text(picker.inventory.owner.profile);Text(picker.snapshot.board.slug);Text(label(Words.KanbanDependencyChild));SelectionContainer {Column {Text(child.task.id);Text(child.task.title)}};Text(label(Words.KanbanDependencyDirection));Text(label(Words.ProjectDraftPrivate))}
            item {TaskInput("dependency-search",Words.Search,query,{query=it})}
            if(candidates.isEmpty()) item {Text(label(Words.KanbanNoMatches))}
            items(candidates,key={it}) {id->val card=(cards+child.linked).firstOrNull {it.id==id};OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(label(Words.KanbanDependencyParent));SelectionContainer {Column {Text(id);card?.let {Text(it.title);KanbanStatus(it.status)}}}
                TextButton({select(id)},Modifier.heightIn(min=48.dp).testTag("dependency-select-"+id)) {Text(label(Words.KanbanDependencyRead))}
            }}}
        }
    },confirmButton={TextButton(close,Modifier.heightIn(min=48.dp).testTag("dependency-picker-cancel")) {Text(label(Words.Cancel))}})
}

@Composable fun KanbanDependencyReadReview(review:ReviewedKanbanDependencyRead,close:()->Unit,confirm:()->Unit) {
    val picker=review.picker;val board=picker.snapshot.board
    AlertDialog(onDismissRequest=close,title={Text(label(Words.KanbanDependencyRead))},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("dependency-read-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(picker.inventory.owner.url);Text(picker.inventory.owner.profile);Text(picker.inventory.owner.actor);Text(board.slug);Text(label(Words.KanbanDbPath));SelectionContainer {Text(board.dbPath)}}
            item {Text(label(Words.KanbanDependencyChild));SelectionContainer {Column {Text(picker.subject.task.id);Text(picker.subject.task.title)}};Text(label(Words.KanbanDependencyParent));SelectionContainer {Text(review.parentId)};Text(label(Words.KanbanDependencyDirection))}
            item {Text(label(Words.KanbanDependencyReadEffect));Text(label(Words.KanbanReadEffect));Text(label(Words.KanbanBoundary))}
        }
    },confirmButton={TextButton(confirm,Modifier.heightIn(min=48.dp).testTag("dependency-read-confirm")) {Text(label(Words.Confirm))}},dismissButton={TextButton(close,Modifier.heightIn(min=48.dp).testTag("dependency-read-cancel")) {Text(label(Words.Cancel))}})
}
