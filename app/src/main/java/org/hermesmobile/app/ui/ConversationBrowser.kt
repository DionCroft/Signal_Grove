package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.data.Conversation
import org.hermesmobile.app.data.SessionOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

@Composable fun ConversationBrowser(vm: HermesViewModel,dismiss: ()->Unit) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val loading by vm.sessionsLoading.collectAsStateWithLifecycle()
    val hasMore by vm.sessionsHasMore.collectAsStateWithLifecycle()
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val p by vm.preferences.collectAsStateWithLifecycle()
    var search by rememberSaveable {mutableStateOf("")}
    var hidden by rememberSaveable {mutableStateOf(false)}
    var renaming by remember {mutableStateOf<Conversation?>(null)}
    var deleting by remember {mutableStateOf<Conversation?>(null)}
    val scope by vm.profileScope.collectAsStateWithLifecycle()
    var source by remember(scope) {mutableStateOf("")}
    var sourceMenu by remember(scope) {mutableStateOf(false)}
    fun origin(c:Conversation)=SessionOrigin.source(c).ifBlank {SessionOrigin.Api}
    val sources=conversations.map(::origin).distinct().sorted()
    val rows=conversations.filter {(!it.hidden || hidden) && it.title.contains(search,true) && (source.isBlank() || origin(it)==source)}
    // API31+ opts into platform fitting; older dialogs retain their resize/inset path.
    val platformFits=android.os.Build.VERSION.SDK_INT>=android.os.Build.VERSION_CODES.S
    var visibleDialogHeightPx by remember {mutableIntStateOf(0)}
    val density=LocalDensity.current
    if(platformFits) ObserveHistoryHostViewport {visibleDialogHeightPx=it}
    // Platform fitting can leave a wrap-content dialog measured for its pre-keyboard height.
    // Bound the whole surface; Material3 measures its actions before the weighted scrolling body.
    val fittedBounds=if(platformFits && visibleDialogHeightPx>0) {
        Modifier.heightIn(max=(with(density) {visibleDialogHeightPx.toDp()}-48.dp).coerceAtLeast(48.dp))
    } else Modifier
    AlertDialog(onDismissRequest=dismiss,modifier=if(platformFits) fittedBounds else Modifier.imePadding(),properties=DialogProperties(decorFitsSystemWindows=platformFits),text={
        val focus=LocalFocusManager.current
        val keyboard=LocalSoftwareKeyboardController.current
        // Filters and rows share one viewport so large multilingual controls cannot hide the list.
        LazyColumn(Modifier.heightIn(max=350.dp).testTag("conversation-browser"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item(key="history-heading") {Text(label(Words.History),style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.onSurface)}
            item(key="history-search") {
                val caption=label(Words.Search)
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(caption,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().testTag("history-search-input").semantics {contentDescription=caption},singleLine=true,
                        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus();keyboard?.hide()}))
                }
            }
            item(key="history-hidden") {Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(hidden,{hidden=it});Text(label(Words.ShowHidden),Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)}}
            if(!p.demo) item(key="history-refresh") {TextButton({vm.refreshSessions()},enabled=!loading && caps?.sessions==true) {Text(label(Words.Refresh))}}
            item(key="history-source") {Box {
                OutlinedButton({sourceMenu=true},Modifier.testTag("history-source-filter")) {Text(if(source.isBlank()) label(Words.AllConversationSources) else source)}
                DropdownMenu(sourceMenu,{sourceMenu=false}) {
                    DropdownMenuItem(text={Text(label(Words.AllConversationSources))},onClick={source="";sourceMenu=false})
                    sources.forEach {value->DropdownMenuItem(text={Text(if(value==SessionOrigin.Unknown) label(Words.UnknownConversationSource) else value)},onClick={source=value;sourceMenu=false},modifier=Modifier.testTag("history-source-"+value))}
                }
            }}
            item(key="history-filter-note") {Text(label(Words.HistoryLoadedFilter),style=MaterialTheme.typography.bodySmall)}
            if(loading) item(key="history-loading") {QuietProgress()}
            items(rows,key={"conversation:"+it.key}) {c ->
                Column {
                    TextButton({focus.clearFocus();keyboard?.hide();vm.select(c.key);dismiss()},Modifier.fillMaxWidth().testTag("history-open-"+c.remoteId)) {Text((if(c.pinned) "★ " else "")+c.title.ifBlank {Words.NewChat.en},Modifier.fillMaxWidth())}
                    Text(label(Words.ConversationSource)+": "+if(origin(c)==SessionOrigin.Unknown) label(Words.UnknownConversationSource) else origin(c),style=MaterialTheme.typography.bodySmall)
                    if(SessionOrigin.readOnly(c)) Text(label(Words.ReadOnlyHistory),style=MaterialTheme.typography.bodySmall)
                    Row {
                        IconButton({vm.pinConversation(c.key)}) {Icon(Icons.Outlined.PushPin,label(Words.Pin))}
                        IconButton({vm.hideConversation(c.key)}) {Icon(Icons.Outlined.VisibilityOff,label(Words.Hide))}
                        IconButton({renaming=c},enabled=!loading && !SessionOrigin.readOnly(c) && (p.demo || c.transport=="compat" || caps?.sessions!=true || caps?.endpoints?.containsKey("session_update")==true)) {Icon(Icons.Outlined.Edit,label(Words.Rename))}
                        IconButton({deleting=c},enabled=!loading) {Icon(Icons.Outlined.DeleteOutline,label(Words.Delete))}
                    }
                    HorizontalDivider()
                }
            }
            if(rows.isEmpty()) item(key="history-empty") {Text(label(Words.NoMatches))}
        }
    },confirmButton={
        val focus=LocalFocusManager.current
        val keyboard=LocalSoftwareKeyboardController.current
        val initialFocus=remember {FocusRequester()}
        var initialFocusSet by remember {mutableStateOf(false)}
        val windowFocused=LocalWindowInfo.current.isWindowFocused
        TextButton({focus.clearFocus();keyboard?.hide();dismiss()},Modifier.heightIn(min=48.dp).focusRequester(initialFocus).focusProperties {canFocus=true}.testTag("history-close")) {Text(label(Words.Close))}
        LaunchedEffect(windowFocused) {if(windowFocused && !initialFocusSet) {initialFocusSet=initialFocus.requestFocus();keyboard?.hide()}}
    },dismissButton={if(hasMore) {
        val description=label(Words.LoadMore)
        TextButton({vm.refreshSessions(more=true)},enabled=!loading,modifier=Modifier.heightIn(min=48.dp).testTag("history-load-more").semantics {contentDescription=description}) {Text(label(Words.MoreHistory))}
    }})
    renaming?.let {c ->
        var title by remember(c.key) {mutableStateOf(c.title)}
        AlertDialog(onDismissRequest={renaming=null},title={Text(label(Words.Rename))},text={OutlinedTextField(title,{title=it},label={Text(label(Words.ConversationTitle))})},
            confirmButton={TextButton({vm.renameConversation(c.key,title);renaming=null},enabled=title.isNotBlank() && title.length<=200) {Text(label(Words.Save))}},dismissButton={TextButton({renaming=null}) {Text(label(Words.Cancel))}})
    }
    deleting?.let {c ->
        val remote=!p.demo && c.transport!="compat" && !SessionOrigin.readOnly(c) && caps?.sessions==true
        val allowed=!remote || caps?.endpoints?.containsKey("session_delete")==true
        AlertDialog(onDismissRequest={deleting=null},title={Text(label(if(remote) Words.DeleteServer else Words.DeleteLocal))},text={Text(c.title+"\n\n"+label(Words.DeleteSessionConfirm))},
            confirmButton={TextButton({vm.deleteConversation(c.key,remote);deleting=null},enabled=allowed && (c.status in HermesViewModel.terminal || c.status=="idle")) {Text(label(Words.Delete))}},dismissButton={TextButton({deleting=null}) {Text(label(Words.Cancel))}})
    }
}


/** The hosting activity reports the full IME-affected viewport; dialog-local insets may be consumed. */
@Composable private fun ObserveHistoryHostViewport(heightChanged:(Int)->Unit) {
    val view=LocalView.current
    val currentHeightChanged by rememberUpdatedState(heightChanged)
    val imeBottom=WindowInsets.ime.getBottom(LocalDensity.current)
    fun update() {
        val frame=android.graphics.Rect()
        view.getWindowVisibleDisplayFrame(frame)
        if(frame.height()>0) currentHeightChanged(frame.height())
    }
    DisposableEffect(view) {
        val listener=android.view.ViewTreeObserver.OnGlobalLayoutListener {update()}
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        update()
        onDispose {
            val observer=view.viewTreeObserver
            if(observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
        }
    }
    LaunchedEffect(view,imeBottom) {update()}
}
