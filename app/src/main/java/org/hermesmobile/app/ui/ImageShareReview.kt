package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.data.SharedImages

@Composable fun ImageShareReview(vm:HermesViewModel,request:SharedImages,done:()->Unit,openChat:()->Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val current by vm.selectedConversation.collectAsStateWithLifecycle()
    val connecting by vm.connecting.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val control by vm.controlBusy.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest=done,title={Text(label(Words.ReviewSharedImages),style=MaterialTheme.typography.titleMedium)},text={
        LazyColumn(Modifier.testTag("share-image-review").heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(request.invalid) item {Text(label(Words.InvalidImageShare),color=MaterialTheme.colorScheme.error)}
            else {
                item {Text(label(Words.Server));Text(if(prefs.demo) label(Words.Demo) else prefs.url)}
                item {Text(current?.title ?: label(Words.NewChat))}
                item {Text(request.uris.size.toString()+" / "+org.hermesmobile.app.data.ImageContent.MAX_IMAGES);Text(label(Words.SharedImageInfo))}
                if(request.caption.isNotBlank()) item {Text(label(Words.SharedCaption));Text(request.caption)}
                if(connecting || busy || control) item {Text(label(Words.WaitForImageTarget))}
            }
        }
    },confirmButton={if(!request.invalid) TextButton({
        if(vm.importImages(request.uris,vm.profileScope.value,vm.selected.value,request.caption)) {done();openChat()}
    },enabled=!connecting && !busy && !control,modifier=Modifier.testTag("use-shared-images")) {Text(label(Words.UseSharedImages))}},dismissButton={TextButton(done) {Text(label(Words.Cancel))}})
}
