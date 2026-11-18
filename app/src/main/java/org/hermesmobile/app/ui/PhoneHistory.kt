package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.hermesmobile.app.local.*

internal enum class PhoneHistoryPeriod(val days: Long?, val word: Words) {
    ALL(null, Words.PhoneHistoryAnyDate), DAY(1, Words.PhoneHistoryLastDay),
    WEEK(7, Words.PhoneHistoryLastWeek), MONTH(30, Words.PhoneHistoryLastMonth)
}

@Composable internal fun ColumnScope.PhoneHistory(state: LocalAiState, actions: LocalAiActions,
    open: (String) -> Unit, remove: (String) -> Unit) {
    var review by rememberSaveable {mutableStateOf(false)}
    var text by rememberSaveable {mutableStateOf(state.historyQuery?.text.orEmpty())}
    var model by rememberSaveable {mutableStateOf(state.historyQuery?.modelSha256)}
    var period by rememberSaveable {mutableStateOf(PhoneHistoryPeriod.entries.firstOrNull {value ->
        val q = state.historyQuery
        value.days != null && q?.fromInclusive != null && q.untilExclusive != null &&
            q.untilExclusive - q.fromInclusive == value.days * 24 * 60 * 60 * 1000L + 1
    }?.name ?: PhoneHistoryPeriod.ALL.name)}
    val results = if(state.historyQuery == null) state.history else state.historyMatches
    Row(Modifier.fillMaxWidth()) {
        TextButton({review = true}, enabled = !state.busy, modifier = Modifier.weight(1f).testTag("phone-history-search-open")) {
            Text(label(Words.PhoneHistorySearch))
        }
        if(state.historyQuery != null) TextButton(actions.clearHistorySearch, enabled = !state.busy,
            modifier = Modifier.testTag("phone-history-search-clear")) {Text(label(Words.PhoneHistoryClear))}
    }
    LazyColumn(Modifier.weight(1f).testTag("phone-history"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      state.historyQuery?.let {query -> item {
        Text(label(Words.PhoneHistoryMatches).replace("{matches}", results.size.toString()).replace("{total}", state.history.size.toString()),
            Modifier.testTag("phone-history-search-summary"), style = MaterialTheme.typography.bodySmall)
        if(query.text.isNotBlank()) Text(query.text, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall)
        query.modelSha256?.let {Text(label(Words.PhoneHistoryModel) + ": " + it.take(12), style = MaterialTheme.typography.bodySmall)}
        query.fromInclusive?.let {from ->
            val format = remember {java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)}
            Text(label(Words.PhoneHistoryFrom).replace("{time}", format.format(java.util.Date(from))) +
                query.untilExclusive?.let {"\n" + label(Words.PhoneHistoryUntil).replace("{time}", format.format(java.util.Date(it - 1)))}.orEmpty(),
                style = MaterialTheme.typography.bodySmall)
        }
      } }
        if(results.isEmpty()) item {Text(label(if(state.historyQuery == null) Words.PhoneNoHistory else Words.PhoneHistoryNoMatches), Modifier.testTag("phone-history-empty"))}
        items(results, key = {it.id}) {item -> HermesCard {
            Text(item.title)
            Text("SHA-256: ${item.modelSha256}", style = MaterialTheme.typography.labelSmall)
            item.lastState?.let {Text(label(phoneReplyState(it)), style = MaterialTheme.typography.labelSmall)}
            TextButton({open(item.id)}, enabled = !state.busy, modifier = Modifier.testTag("phone-history-open-${item.id}")) {Text(label(Words.Chat))}
            TextButton({remove(item.id)}, enabled = !state.busy) {Text(label(Words.Delete))}
        } }
    }
    if(review) PhoneHistorySearchDialog(state, text, {text = it}, model, {model = it}, PhoneHistoryPeriod.valueOf(period),
        {period = it.name}, {query -> actions.searchHistory(query); review = false}, {review = false})
}

@Composable private fun PhoneHistorySearchDialog(state: LocalAiState, text: String, change: (String) -> Unit,
    model: String?, chooseModel: (String?) -> Unit, period: PhoneHistoryPeriod, choosePeriod: (PhoneHistoryPeriod) -> Unit,
    search: (LocalHistoryQuery) -> Unit, close: () -> Unit) {
    var menu by remember {mutableStateOf(false)}
    val models = (state.history.map {it.modelSha256} + state.models.map {it.sha256} + listOfNotNull(model)).distinct().sorted()
    fun modelLabel(hash: String) = state.models.firstOrNull {it.sha256 == hash}?.metadata?.name
        ?.filter { !it.isISOControl() }?.take(80)?.let {"$it · ${hash.take(12)}"} ?: hash.take(12)
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label(Words.PhoneHistorySearch), style = MaterialTheme.typography.titleLarge)
                    Text(label(Words.PhoneHistorySearchHelp), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(text, {candidate ->
                        if(candidate.toByteArray(Charsets.UTF_8).size <= 1024 && !candidate.contains('\u0000') && Charsets.UTF_8.newEncoder().canEncode(candidate)) change(candidate)
                    }, Modifier.fillMaxWidth().testTag("phone-history-search-text"), label = {Text(label(Words.PhoneHistoryQuery))},
                        supportingText = {Text(label(Words.PhoneHistoryQueryLimit))}, maxLines = 3)
                    Text(label(Words.PhoneHistoryModel), style = MaterialTheme.typography.titleSmall)
                    Box {
                        OutlinedButton({menu = true}, enabled = !state.busy, modifier = Modifier.testTag("phone-history-model-menu")) {
                            Text(model?.let(::modelLabel) ?: label(Words.PhoneHistoryAllModels))
                        }
                        DropdownMenu(menu, {menu = false}) {
                            DropdownMenuItem(text = {Text(label(Words.PhoneHistoryAllModels))}, onClick = {chooseModel(null); menu = false},
                                modifier = Modifier.testTag("phone-history-model-all"))
                            models.forEach {hash -> DropdownMenuItem(text = {Text(modelLabel(hash))}, onClick = {chooseModel(hash); menu = false},
                                modifier = Modifier.testTag("phone-history-model-$hash")) }
                        }
                    }
                    Text(label(Words.PhoneHistoryDate), style = MaterialTheme.typography.titleSmall)
                    PhoneHistoryPeriod.entries.forEach {value -> FilterChip(value == period, {choosePeriod(value)},
                        label = {Text(label(value.word))}, enabled = !state.busy, modifier = Modifier.testTag("phone-history-period-${value.name}")) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({
                        val now = System.currentTimeMillis().coerceIn(0, Long.MAX_VALUE - 1)
                        val from = period.days?.let {(now - it * 24 * 60 * 60 * 1000L).coerceAtLeast(0)}
                        search(LocalHistoryQuery(text, model, from, if(from == null) null else now + 1))
                    }, enabled = !state.busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("phone-history-search-apply")) {Text(label(Words.PhoneHistoryApply))}
                    TextButton(close, Modifier.weight(1f).heightIn(min = 48.dp).testTag("phone-history-search-cancel")) {Text(label(Words.Cancel))}
                }
            }
        }
    }
}
