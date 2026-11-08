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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.data.*
import java.text.DateFormat
import java.util.Date

private fun roomTime(millis:Long)=DateFormat.getDateTimeInstance().format(Date(millis))
@Composable internal fun HostedRoomsDialog(controller:HostedRoomBrowserController,available:Boolean,close:()->Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val lifecycle=LocalLifecycleOwner.current
    DisposableEffect(controller,lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) controller.close()}
        lifecycle.lifecycle.addObserver(observer)
        onDispose {lifecycle.lifecycle.removeObserver(observer);controller.close()}
    }
    LaunchedEffect(Unit) {if(available) controller.reload()}
    LaunchedEffect(available) {controller.recheckContext();if(!available) controller.close()}
    fun dismiss() {controller.close();close()}
    AlertDialog(onDismissRequest=::dismiss,title={Text(state.detail?.room?.name ?: label(Words.RoomsTitle),maxLines=3,overflow=TextOverflow.Ellipsis)},text={
        LazyColumn(Modifier.heightIn(max=520.dp).testTag("hosted-rooms-list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(label(Words.RoomsBoundary))}
            item {Text(label(Words.RoomsSharedScope))}
            if(!available) item {Text(label(Words.ManagementVerify))}
            if(state.phase==HostedRoomBrowserPhase.Closed && available) item {Text(label(Words.RoomsReadAgain))}
            if(state.busy) item {QuietProgress();Text(label(Words.Loading))}
            state.error?.let {word->item {Text(label(word),color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("hosted-rooms-error"))}}
            val detail=state.detail
            if(detail!=null && state.phase==HostedRoomBrowserPhase.Detail) {
                item {TextButton(controller::back,enabled=!state.busy && available,modifier=Modifier.heightIn(min=48.dp).testTag("hosted-rooms-back")) {Text(label(Words.RoomsBack))}}
                item {SelectionContainer {Text(detail.room.name)};Text(detail.scope.owner.url);Text(detail.scope.owner.profile);Text(label(Words.RoomsObserved).replace("{time}",roomTime(detail.observedAt)));Text(label(Words.RoomsUpdated).replace("{time}",roomTime((detail.room.updatedAtSeconds*1000).toLong())))}
                item {SelectionContainer {Text(detail.room.id)};Text(label(Words.RoomsAuthority));SelectionContainer {Text(detail.room.authorityId)};Text(label(Words.RoomsRevision).replace("{epoch}",detail.room.authorityEpoch.toString()).replace("{revision}",detail.room.revision.toString()))}
                if(detail.room.disbandedAtSeconds!=null) item {Text(label(Words.RoomsDisbanded))}
                detail.context?.let {context->
                    item {Text(label(Words.RoomsPurpose),style=MaterialTheme.typography.titleMedium);SelectionContainer {Text(context.purpose.ifBlank {"—"})}}
                    item {Text(label(Words.RoomsNotes),style=MaterialTheme.typography.titleMedium);SelectionContainer {Text(context.notes.ifBlank {"—"})}}
                    item {Text(label(Words.RoomsRouting));Text(label(when(context.routing) {"lead"->Words.RoomsLead;"auto"->Words.RoomsAuto;else->Words.RoomsEveryone}));if(context.routing=="lead") SelectionContainer {Text(context.leadMemberId)}}
                } ?: item {Text(label(Words.RoomsContextUnknown))}
                detail.driver?.let {driver->item {
                    if(!driver.running) Text(label(Words.RoomsDriverUnavailable))
                    if(driver.working) Text(label(Words.RoomsWorking))
                    if(driver.blocked) Text(label(Words.RoomsBlocked))
                    if(!driver.working && !driver.blocked) Text(label(Words.RoomsNoWorkReported))
                }} ?: item {Text(label(Words.RoomsStatusUnknown))}
                item {Text(label(Words.RoomsMembers),style=MaterialTheme.typography.titleMedium)}
                itemsIndexed(detail.room.members,key={index,_->"member-$index"}) {_,member->
                    OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(member.displayName ?: member.handle ?: member.profile ?: label(Words.RoomsLegacyMember),style=MaterialTheme.typography.titleSmall)
                        member.id?.let {Text(label(Words.RoomsMemberId));SelectionContainer {Text(it)}}
                        member.profile?.let {SelectionContainer {Text(it)}}
                        member.handle?.let {Text(label(Words.RoomsMemberHandle));SelectionContainer {Text(it)}}
                        member.targetKind?.let {Text(label(Words.RoomsTargetKind));Text(it)}
                    }}
                }
            } else {
                item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(label(Words.RoomsIncludeDisbanded),Modifier.weight(1f))
                    Switch(state.includeDisbanded,{controller.reload(it)},enabled=!state.busy && available,modifier=Modifier.testTag("hosted-rooms-filter"))
                }}
                item {TextButton({controller.reload()},enabled=!state.busy && available,modifier=Modifier.heightIn(min=48.dp).testTag("hosted-rooms-refresh")) {Text(label(Words.Refresh))}}
                state.listing?.let {listing->
                    item {Text(listing.scope.owner.url);Text(listing.scope.owner.profile);Text(label(Words.RoomsObserved).replace("{time}",roomTime(listing.page.observedAt)));Text(label(Words.RoomsOrder))}
                    if(!listing.capabilities.driver) item {Text(label(Words.RoomsDriverUnavailable))}
                    val canDetail="groups.state" in listing.capabilities.methods
                    if(!canDetail) item {Text(label(Words.RoomsStateUnavailable))}
                    if(listing.page.offset>0) item {TextButton({controller.reload()},enabled=!state.busy && available,modifier=Modifier.heightIn(min=48.dp).testTag("hosted-rooms-first")) {Text(label(Words.RoomsFirstPage))}}
                    if(listing.page.rooms.isEmpty()) item {Text(label(Words.RoomsEmpty))}
                    items(listing.page.rooms,key={it.id}) {room->OutlinedCard(Modifier.fillMaxWidth().testTag("hosted-room-"+room.id)) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(room.name,style=MaterialTheme.typography.titleMedium,maxLines=3,overflow=TextOverflow.Ellipsis)
                        Text(room.id,style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(label(Words.RoomsMemberCount).replace("{count}",room.members.size.toString()))
                        if(room.disbandedAtSeconds!=null) Text(label(Words.RoomsDisbanded))
                        TextButton({controller.detail(room)},enabled=!state.busy && available && canDetail,modifier=Modifier.heightIn(min=48.dp).testTag("hosted-room-view-"+room.id)) {Text(label(Words.RoomsView))}
                    }}}
                    if(listing.page.nextOffset!=null) item {TextButton(controller::more,enabled=!state.busy && available,modifier=Modifier.heightIn(min=48.dp).testTag("hosted-rooms-more")) {Text(label(Words.RoomsMore))}}
                }
            }
        }
    },confirmButton={TextButton(::dismiss,modifier=Modifier.heightIn(min=48.dp).testTag("hosted-rooms-close")) {Text(label(Words.Close))}})
}
