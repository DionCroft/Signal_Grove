package org.hermesmobile.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hermesmobile.app.HermesApplication
import org.hermesmobile.app.data.*

@Composable fun ImagePickerButton(vm:HermesViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val control by vm.controlBusy.collectAsStateWithLifecycle()
    val connecting by vm.connecting.collectAsStateWithLifecycle()
    var expectedScope by rememberSaveable {mutableStateOf("")}
    var expectedConversation by rememberSaveable {mutableStateOf("")}
    var unavailable by remember {mutableStateOf(false)}
    var pending by rememberSaveable {mutableStateOf<ArrayList<android.net.Uri>?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ImageContent.MAX_IMAGES)) {uris -> pending=ArrayList(uris)}
    LaunchedEffect(pending,connecting,busy,control) {
        val uris=pending
        if(uris!=null && !vm.connecting.value && !vm.busy.value && !vm.controlBusy.value) {pending=null;vm.importImages(uris,expectedScope,expectedConversation)}
    }
    IconButton({
        expectedScope=vm.profileScope.value;expectedConversation=vm.selected.value
        try {picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))} catch(_:Exception) {unavailable=true}
    },enabled=!busy && !control && !connecting,modifier=Modifier.testTag("choose-images")) {Icon(Icons.Outlined.AddPhotoAlternate,label(Words.ChooseImages))}
    if(unavailable) AlertDialog(onDismissRequest={unavailable=false},title={Text(label(Words.ChooseImages))},text={Text(label(Words.ImagePickerUnavailable))},confirmButton={TextButton({unavailable=false}) {Text(label(Words.Close))}})
}

@Composable fun DraftImageStrip(vm:HermesViewModel) {
    val draft by vm.imageDraft.collectAsStateWithLifecycle()
    val loading by vm.imageLoading.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val control by vm.controlBusy.collectAsStateWithLifecycle()
    val app=LocalContext.current.applicationContext as HermesApplication
    var expanded by remember(draft?.namespace) {mutableStateOf<Bitmap?>(null)}
    var info by remember {mutableStateOf(false)}
    if(loading) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label(Words.LoadingImages),Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,maxLines=2)
        TextButton(vm::cancelImageImport) {Text(label(Words.Cancel))}
    }
    draft?.let {current ->
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("image-draft-strip"),verticalAlignment=Alignment.CenterVertically) {
            current.refs.forEachIndexed {index,ref ->
                val bitmap by produceState<Bitmap?>(null,current.namespace,ref.hash) {
                    value=withContext(Dispatchers.IO) {runCatching {
                        val bytes=app.imageDrafts.read(current.scope,current.namespace,ref)
                        BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=4})
                    }.getOrNull()}
                }
                Box(Modifier.size(64.dp).clickable(enabled=bitmap!=null) {expanded=bitmap}.testTag("draft-image-$index"),contentAlignment=Alignment.Center) {
                    bitmap?.let {Image(it.asImageBitmap(),label(Words.ImagePreview)+" ${index+1}",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
                        ?: Icon(Icons.Outlined.ImageNotSupported,label(Words.ImageCacheMissing))
                }
                IconButton({vm.removeDraftImage(ref.hash.ifBlank {ref.url})},enabled=!busy && !control) {Icon(Icons.Outlined.Close,label(Words.RemoveImage)+" ${index+1}")}
            }
            IconButton({info=true}) {Icon(Icons.Outlined.Info,label(Words.ImageInputInfo))}
        }
    }
    expanded?.let {bitmap -> AlertDialog(onDismissRequest={expanded=null},title={Text(label(Words.ImagePreview))},text={Image(bitmap.asImageBitmap(),label(Words.ImagePreview),Modifier.fillMaxWidth().height(280.dp),contentScale=ContentScale.Fit)},confirmButton={TextButton({expanded=null}) {Text(label(Words.Close))}}) }
    if(info) AlertDialog(onDismissRequest={info=false},title={Text(label(Words.ChooseImages))},text={Text(label(Words.ImageInputInfo),Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()))},confirmButton={TextButton({info=false}) {Text(label(Words.Close))}})
}
