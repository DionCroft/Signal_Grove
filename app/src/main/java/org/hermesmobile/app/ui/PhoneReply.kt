package org.hermesmobile.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.method.ArrowKeyMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.image.AsyncDrawableLoader
import org.hermesmobile.app.local.LocalReplyContent
import org.hermesmobile.app.local.LocalReplyState

/** Local Markdown cannot start activities or load image/HTML/network plugins. */
internal fun phoneReplyMarkwon(context: Context): Markwon = Markwon.builder(context).usePlugin(object : AbstractMarkwonPlugin() {
    override fun configureSpansFactory(builder: io.noties.markwon.MarkwonSpansFactory.Builder) {
        builder.setFactory(org.commonmark.node.Link::class.java) { _, _ -> emptyArray<Any>() }
    }
    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
        builder.linkResolver { _, _ -> }
        builder.asyncDrawableLoader(AsyncDrawableLoader.noOp())
    }
}).build()

@Composable internal fun PhoneReply(text: String, state: LocalReplyState, index: Int) {
    var reader by remember {mutableStateOf(false)}
    var code by remember {mutableStateOf(false)}
    if (state == LocalReplyState.GENERATING) {
        SelectionContainer {Text(LocalReplyContent.streamingTail(text))}
        if (text.length > 512) Text(label(Words.PhoneStreamTail), style = MaterialTheme.typography.labelSmall)
    }
    else if (text.isNotBlank()) {
        val content = remember(text) {LocalReplyContent.read(text)}
        PhoneMarkdown(content.preview, "phone-reply-$index")
        if (content.hasMore) TextButton({reader = true}, Modifier.testTag("phone-reply-expand-$index")) {Text(label(Words.PhoneFullReply))}
        if (content.code.isNotEmpty()) TextButton({code = true}, Modifier.testTag("phone-code-open-$index")) {
            Text(label(Words.PhoneCodeBlocks).replace("{number}", content.codeCount.toString()))
        }
        if (reader) PhoneTextReader(text, true) {reader = false}
        if (code) PhoneCodeReader(content) {code = false}
    }
}

@Composable private fun PhoneMarkdown(text: String, tag: String) {
    val ink = MaterialTheme.colorScheme.onSurface.toArgb()
    val density = LocalDensity.current
    AndroidView(factory = {context -> TextView(context).apply {
        setTextIsSelectable(true); movementMethod = ArrowKeyMovementMethod.getInstance()
        setTextClassifier(android.view.textclassifier.TextClassifier.NO_OP)
        this.tag = phoneReplyMarkwon(context)
    }}, modifier = Modifier.fillMaxWidth().testTag(tag), update = {view ->
        view.setTextColor(ink); view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 16f * density.density * density.fontScale)
        (view.tag as Markwon).setMarkdown(view, text)
        // Keep selection, but links remain passive even through accessibility span actions.
        view.linksClickable = false
    })
}

@Composable private fun PhoneTextReader(text: String, markdown: Boolean, close: () -> Unit) {
    val context = LocalContext.current
    val rendered = markdown && text.toByteArray().size <= LocalReplyContent.MAX_MARKDOWN_BYTES && text.length <= 4096 && text.count {it == '\n'} <= 128
    val chunks = remember(text) {LocalReplyContent.chunks(text)}
    var copied by remember {mutableStateOf(false)}
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label(if(markdown) Words.PhoneFullReply else Words.PhoneFullCode), style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("phone-full-reply"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {Text(label(Words.PhonePassiveReply), style = MaterialTheme.typography.bodySmall)}
                    if (rendered) item {PhoneMarkdown(text, "phone-full-markdown")}
                    else {
                        if (markdown) item {Text(label(Words.PhoneLargeReplyPlain), style = MaterialTheme.typography.bodySmall)}
                        itemsIndexed(chunks) {i, part -> SelectionContainer {Text(part, Modifier.testTag("phone-full-part-$i"), fontFamily = if(markdown) null else FontFamily.Monospace)} }
                    }
                }
                TextButton({copyPhoneText(context, text); copied = true}, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-full-copy")) {
                    Text(label(if(copied) Words.Copied else Words.PhoneCopyFullReply))
                }
                TextButton(close, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-full-close")) {Text(label(Words.Close))}
            }
        }
    }
}

@Composable private fun PhoneCodeReader(content: LocalReplyContent, close: () -> Unit) {
    val context = LocalContext.current
    var copied by remember {mutableIntStateOf(-1)}
    var read by remember {mutableStateOf<String?>(null)}
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label(Words.PhoneCodeBlocks).replace("{number}", content.codeCount.toString()), style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("phone-code-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {Text(label(Words.PhonePassiveReply), style = MaterialTheme.typography.bodySmall)}
                    if (content.codeCount > content.code.size) item {Text(label(Words.PhoneCodeLimit).replace("{number}", content.code.size.toString()))}
                    itemsIndexed(content.code) {index, block ->
                        HermesCard {
                            Text("${index + 1}" + block.language.takeIf {it.isNotBlank()}?.let {" · $it"}.orEmpty())
                            SelectionContainer {Text(LocalReplyContent.chunks(block.text).firstOrNull().orEmpty(), fontFamily = FontFamily.Monospace)}
                            if (block.text.length > 512) TextButton({read = block.text}) {Text(label(Words.PhoneFullCode))}
                            TextButton({copyPhoneText(context, block.text); copied = index}, Modifier.testTag("phone-code-copy-$index")) {
                                Text(label(if(copied == index) Words.Copied else Words.PhoneCopyCode))
                            }
                        }
                    }
                }
                TextButton(close, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-code-close")) {Text(label(Words.Close))}
            }
        }
    }
    read?.let {PhoneTextReader(it, false) {read = null}}
}

private fun copyPhoneText(context: Context, text: String) {
    val clip = ClipData.newPlainText("Signal Grove", text)
    // The compat key suppresses content previews on Android versions/IMEs that honor it.
    clip.description.extras = android.os.PersistableBundle().apply {putBoolean("android.content.extra.IS_SENSITIVE", true)}
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}