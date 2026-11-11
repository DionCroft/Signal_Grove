package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.data.*
import java.text.DateFormat
import java.util.Date

@Composable internal fun CanonicalSessionsDialog(
    controller:CanonicalViewerController,available:Boolean,pending:Boolean,close:()->Unit
) {
    val s by controller.state.collectAsStateWithLifecycle()
    var workspaceDetails by remember(s.sessionId,s.workspaceObservedAt) {mutableStateOf(false)}
    var workspaceEdit by remember(s.sessionId,s.workspaceObservedAt) {mutableStateOf(false)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(controller,lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) controller.close()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);controller.close()}
    }
    LaunchedEffect(controller) {controller.load(CanonicalSessionScope.Live)}
    AlertDialog(onDismissRequest=close,title={Text(label(Words.ViewerTitle))},text={
        LazyColumn(Modifier.heightIn(max=480.dp).testTag("canonical-viewer-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(label(Words.ViewerBoundary))}
            if(s.phase!=CanonicalViewerPhase.Following) {
                item {TextButton({controller.load(CanonicalSessionScope.Live)},enabled=available && !s.busy,modifier=Modifier.heightIn(min=48.dp).testTag("canonical-live")) {Text(label(Words.ViewerLive))}}
                item {TextButton({controller.load(CanonicalSessionScope.Stored)},enabled=available && !s.busy,modifier=Modifier.heightIn(min=48.dp).testTag("canonical-stored")) {Text(label(Words.ViewerStored))}}
                if(!available) item {Text(label(Words.ManagementVerify))}
            }
            if(s.busy) item {QuietProgress();Text(label(Words.Loading))}
            if(s.phase==CanonicalViewerPhase.Closed) item {Text(label(Words.ViewerClosed))}
            if(pending) item {
                Text(label(Words.ViewerReceiptNote))
                TextButton(close,modifier=Modifier.heightIn(min=48.dp).testTag("canonical-return-receipts")) {Text(label(Words.ViewerReturnReceipts))}
            }
            s.inventory?.takeIf {s.phase==CanonicalViewerPhase.Inventory}?.let {inventory->
                item {Text(label(if(inventory.scope==CanonicalSessionScope.Live) Words.ViewerLive else Words.ViewerStored));Text(label(Words.ViewerLimited))}
                if(inventory.rows.isEmpty()) item {Text(label(Words.ViewerEmpty))}
                items(inventory.rows,key={it.sessionId}) {row->
                    OutlinedCard(Modifier.fillMaxWidth().testTag("canonical-session-"+row.sessionId)) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(row.title.ifBlank {row.sessionId},style=MaterialTheme.typography.titleMedium,maxLines=3,overflow=TextOverflow.Ellipsis)
                            Text(row.sessionId,style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                            TextButton({controller.review(row.sessionId)},enabled=!s.busy && !pending,modifier=Modifier.heightIn(min=48.dp).testTag("canonical-review-"+row.sessionId)) {Text(label(Words.ViewerReview))}
                        }
                    }
                }
            }
            if(s.sessionId.isNotBlank() && s.phase in setOf(CanonicalViewerPhase.Following,CanonicalViewerPhase.Held)) {
                item {Text(s.title.ifBlank {s.sessionId},style=MaterialTheme.typography.titleMedium);SelectionContainer {Text(s.sessionId)};Text(label(Words.ViewerSnapshotNote))}
                if(s.phase==CanonicalViewerPhase.Following) item {Text(label(Words.ViewerFollowing),modifier=Modifier.testTag("canonical-following"))}
                s.workspace?.let {directory->item {
                    TextButton({workspaceDetails=!workspaceDetails},Modifier.heightIn(min=48.dp).testTag("canonical-workspace-open")) {Text(label(Words.ViewerWorkspace))}
                    if(workspaceDetails) Column(Modifier.testTag("canonical-workspace-details"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(label(Words.ViewerWorkspaceSnapshot))
                        if(directory is CanonicalWorkspaceDirectory.Reported) {
                            if(directory.path.isEmpty()) Text(label(Words.ViewerWorkspaceUnset))
                            else SelectionContainer {Text(directory.path,Modifier.testTag("canonical-workspace-path"))}
                        } else Text(label(Words.ViewerWorkspaceUnavailable))
                        s.workspaceObservedAt?.let {time->Text(DateFormat.getDateTimeInstance().format(Date(time)))}
                        Text(label(Words.ViewerWorkspaceBoundary),style=MaterialTheme.typography.bodySmall)
                        val ready=s.workspaceSnapshot?.let(controller::workspaceCurrent)==true
                        TextButton({workspaceEdit=true},Modifier.heightIn(min=48.dp).testTag("workspace-edit"),enabled=ready && !pending,shape=MaterialTheme.shapes.small) {Text(label(Words.WorkspaceChange))}
                        if(!ready) Text(label(Words.WorkspaceUnavailable))
                        if(pending) Text(label(Words.ManagementOutcomePending))
                    }
                }}
                item {Text(label(Words.ViewerActivityLimit))}
                item {Text(label(Words.ViewerEventCount).replace("{count}",s.observed.toString()))}
                s.heartbeatAt?.let {time->item {Text(label(Words.ViewerHeartbeat).replace("{time}",DateFormat.getTimeInstance().format(Date(time))))}}
                items(s.activities,key={it.sequence}) {activity->OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {Text(label(activity.kind));if(activity.text.isNotBlank()) SelectionContainer {Text(activity.text)};if(activity.truncated) Text(label(Words.ViewerPreviewLimited),style=MaterialTheme.typography.labelSmall)}
                }}
            }
            if(s.workspaceChanging) item {QuietProgress();Text(label(Words.Loading))}
            s.workspaceNotice?.let {message->item {Text(label(message),Modifier.testTag("workspace-result"))}}
            s.error?.let {message->item {Text(label(message),color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("canonical-error"))}}
        }
    },confirmButton={TextButton(close,modifier=Modifier.heightIn(min=48.dp).testTag("canonical-close")) {Text(label(Words.Close))}})
    if(workspaceEdit) s.workspaceSnapshot?.takeIf(controller::workspaceCurrent)?.let {snapshot->
        CanonicalWorkspaceForm(snapshot,pending,{controller.workspaceCurrent(snapshot)},{workspaceEdit=false}) {review->
            workspaceEdit=false;controller.changeWorkspace(review)
        }
    }
    s.review?.takeIf(controller::reviewCurrent)?.let {review->
        var consent by remember(review) {mutableStateOf(false)}
        var fresh by remember(review) {mutableStateOf(false)}
        val inventory=review.approval.expected
        AlertDialog(onDismissRequest=controller::cancelReview,title={Text(label(Words.ViewerReview))},text={
            LazyColumn(Modifier.heightIn(max=360.dp).testTag("canonical-open-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {Text(inventory.binding.target.url);Text(inventory.binding.target.profile);Text(inventory.binding.target.actor)}
                item {SelectionContainer {Column {Text(review.approval.row.title.ifBlank {review.approval.row.sessionId});Text(review.approval.row.sessionId)}}}
                item {Text(label(Words.ViewerResumeEffect))}
                item {Row {Checkbox(consent,{consent=it},modifier=Modifier.testTag("canonical-consent"));Text(label(Words.ViewerConsent))}}
                item {Row {Checkbox(fresh,{fresh=it},modifier=Modifier.testTag("canonical-fresh"));Text(label(Words.ViewerFresh))};Text(label(Words.ViewerFreshNote))}
            }
        },confirmButton={TextButton({controller.approve(review,fresh)},enabled=consent && !pending && controller.reviewCurrent(review),modifier=Modifier.heightIn(min=48.dp).testTag("canonical-open-confirm")) {Text(label(Words.ViewerOpen))}},
            dismissButton={TextButton(controller::cancelReview,modifier=Modifier.heightIn(min=48.dp).testTag("canonical-open-cancel")) {Text(label(Words.Cancel))}})
    }
}

/** Never routes a canonical session receipt into the MCP-name observer. */
@Composable internal fun CanonicalViewerReceiptCard(receipt:ManagementOperation,vm:ManagementViewModel?,s:ManagementState?) {
    var review by remember(receipt.key,s?.apiScope,s?.url,s?.profile,s?.identity) {mutableStateOf<ReviewedManagementUncertainty?>(null)}
    Column(Modifier.fillMaxWidth().testTag("canonical-receipt-"+receipt.key),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label(if(receipt.action==CanonicalWorkspaceAction.Id) Words.WorkspaceChange else Words.ViewerOpen));Text(receipt.target)
        val workspace=receipt.action==CanonicalWorkspaceAction.Id
        Text(label(if(workspace && receipt.state=="confirmed") Words.ManagementAcknowledged else managementStateWord(receipt.state)))
        if(workspace) {
            Text(label(Words.WorkspaceRequestId))
            SelectionContainer {Text(receipt.key,Modifier.testTag("workspace-receipt-request-id"))}
        }
        Text(label(Words.ViewerSnapshotNote))
        if(receipt.state=="acknowledged") Text(label(if(receipt.action==CanonicalWorkspaceAction.Id) Words.WorkspaceAcknowledged else Words.ViewerAdoptionKnown))
        if(receipt.state=="unknown" && vm!=null && s!=null) {
            Text(label(Words.ViewerReceiptNote))
            TextButton({review=vm.prepareManagementUncertainty(receipt.key)},enabled=!s.busy && vm.prepareManagementUncertainty(receipt.key)!=null,modifier=Modifier.heightIn(min=48.dp).testTag("canonical-receipt-review")) {Text(label(Words.ManagementReview))}
        }
    }
    review?.takeIf {vm?.managementUncertaintyCurrent(it)==true}?.let {frozen->
        var consent by remember(frozen) {mutableStateOf(false)}
        AlertDialog(onDismissRequest={review=null},title={Text(label(Words.ManagementReview))},text={
            LazyColumn(Modifier.heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {Text(frozen.target.url);Text(frozen.target.profile);Text(receipt.target);Text(label(if(receipt.action==CanonicalWorkspaceAction.Id) Words.WorkspaceLocalReviewEffect else Words.ViewerLocalReviewEffect))}
                item {Row {Checkbox(consent,{consent=it},modifier=Modifier.testTag("canonical-local-review-consent"));Text(label(if(receipt.action==CanonicalWorkspaceAction.Id) Words.WorkspaceLocalReviewConsent else Words.ViewerLocalReviewConsent))}}
            }
        },confirmButton={TextButton({vm!!.reviewReceipt(receipt.key,frozen.target,frozen.projectHome);review=null},enabled=consent && s?.busy!=true && vm?.managementUncertaintyCurrent(frozen)==true,modifier=Modifier.heightIn(min=48.dp).testTag("canonical-local-review-confirm")) {Text(label(Words.Confirm))}},
            dismissButton={TextButton({review=null},modifier=Modifier.heightIn(min=48.dp)) {Text(label(Words.Cancel))}})
    }
}
