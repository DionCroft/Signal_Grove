package org.hermesmobile.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.hermesmobile.app.data.*

@Composable fun ImageAttachments(media:ImageMedia,scope:String,conversation:String,raw:String) {
    val refs=remember(raw) {runCatching {ImageContent.refs(raw)}.getOrDefault(listOf(ImageRef(unavailable=true)))}
    val uri=LocalUriHandler.current
    var openFailed by remember(raw) {mutableStateOf(false)}
    if(openFailed) Text(label(Words.ImageOpenFailed),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    refs.forEachIndexed {index,ref ->
        if(ref.hash.isNotBlank()) {
            var expanded by remember(scope,conversation,ref.hash) {mutableStateOf(false)}
            val loaded by produceState<ImageLoad?>(null,scope,conversation,ref.hash) {
                value=withContext(Dispatchers.IO) {ImageLoad(runCatching {
                    val bytes=media.blobs.read(scope,conversation,ref)
                    BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=2})
                }.getOrNull())}
            }
            val bitmap=loaded?.bitmap
            when {
                loaded==null -> QuietProgress()
                bitmap==null -> Text(label(Words.ImageCacheMissing),style=MaterialTheme.typography.bodySmall)
                else -> {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val height=(maxWidth*(bitmap.height.toFloat()/bitmap.width)).coerceIn(80.dp,240.dp)
                        Image(bitmap.asImageBitmap(),label(Words.ImagePreview)+" ${index+1}",Modifier.fillMaxWidth().height(height).testTag("image-preview-$index").clickable(onClickLabel=label(Words.EnlargeImage)) {expanded=true},contentScale=ContentScale.Fit)
                    }
                    if(expanded) AlertDialog(onDismissRequest={expanded=false},title={Text(label(Words.ImagePreview))},text={
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val height=(maxWidth*(bitmap.height.toFloat()/bitmap.width)).coerceIn(80.dp,350.dp)
                            Image(bitmap.asImageBitmap(),label(Words.ImagePreview),Modifier.fillMaxWidth().height(height).testTag("expanded-image-preview"),contentScale=ContentScale.Fit)
                        }
                    },confirmButton={TextButton({expanded=false}) {Text(label(Words.Close))}})
                }
            }
        } else if(ref.url.isNotBlank()) {
            Text(label(Words.RemoteImage),style=MaterialTheme.typography.labelMedium)
            Text(ref.url.toHttpUrlOrNull()?.host.orEmpty(),style=MaterialTheme.typography.bodySmall)
            Text(label(Words.RemoteImageInfo),style=MaterialTheme.typography.bodySmall)
            TextButton({openFailed=runCatching {uri.openUri(ref.url)}.isFailure}) {Text(label(Words.OpenImageBrowser))}
        } else Text(label(Words.ImageUnavailable),style=MaterialTheme.typography.bodySmall)
    }
}

private data class ImageLoad(val bitmap:android.graphics.Bitmap?)
