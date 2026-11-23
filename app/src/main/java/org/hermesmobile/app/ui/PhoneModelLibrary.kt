package org.hermesmobile.app.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.hermesmobile.app.local.CuratedPhoneModel
import org.hermesmobile.app.local.InstalledLocalModel
import org.hermesmobile.app.local.LocalAiState
import org.hermesmobile.app.local.LocalPerformancePreset

@Composable
internal fun ColumnScope.PhoneModelLibrary(
    state: LocalAiState,
    supported: Boolean,
    actions: LocalAiActions,
    openCatalogue: () -> Unit,
    openSetup: () -> Unit,
    openDownloads: (() -> Unit)?,
    reviewUpdate: (CuratedPhoneModel) -> Unit,
    remove: (InstalledLocalModel) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val search = query.trim().takeIf { state.models.size > 1 }.orEmpty()
    val context = LocalContext.current
    val models = state.models.filter { model ->
        model.metadata.name.contains(search, ignoreCase = true) ||
            model.metadata.architecture.contains(search, ignoreCase = true) ||
            model.sha256.contains(search, ignoreCase = true)
    }
    if (state.models.size > 1) {
        OutlinedTextField(
            query,
            { query = it.take(128) },
            Modifier.fillMaxWidth().testTag("phone-model-search"),
            label = { Text(label(Words.PhoneModelSearch)) },
            singleLine = true
        )
        if (models.isEmpty()) {
            Text(
                label(Words.PhoneModelNoMatches),
                Modifier.testTag("phone-model-no-matches").semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("phone-model-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(actions.chooseFile, enabled = supported && !state.busy, modifier = Modifier.testTag("phone-import")) {
            Text(label(Words.PhoneImport))
        }
        OutlinedButton(openCatalogue, Modifier.testTag("phone-catalogue-open")) { Text(label(Words.PhoneCatalogue)) }
        state.modelStorageAvailableBytes?.let { available ->
            val weights = state.models.sumOf { it.metadata.bytes }
            val partials = state.downloadPartials.values.sum()
            Text(label(Words.PhoneModelStorage)
                .replace("{installed}", Formatter.formatFileSize(context, weights))
                .replace("{available}", Formatter.formatFileSize(context, available)),
                Modifier.testTag("phone-model-storage"), style = MaterialTheme.typography.bodySmall)
            if (partials > 0) Text(label(Words.PhoneModelSavedDownloads)
                .replace("{size}", Formatter.formatFileSize(context, partials)),
                Modifier.testTag("phone-model-saved-downloads"), style = MaterialTheme.typography.bodySmall)
        }
        if (openDownloads != null) TextButton(openDownloads, Modifier.testTag("phone-downloads-open")) {
            Text(label(Words.PhoneDownloads))
        }
        TextButton(openSetup, Modifier.testTag("phone-offline-setup-models-entry")) { Text(label(Words.PhoneOfflineSetup)) }
        TextButton(actions.refresh, enabled = !state.busy) { Text(label(Words.Refresh)) }
        for (model in models) {
            key(model.sha256) { PhoneModel(model, state, supported, actions, reviewUpdate) { remove(model) } }
        }
        Text(label(Words.PhoneLibraryHelp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PhoneModel(model: InstalledLocalModel, state: LocalAiState, supported: Boolean, actions: LocalAiActions,
    reviewUpdate: (CuratedPhoneModel) -> Unit, remove: () -> Unit) {
    val next = state.modelPerformance[model.sha256] ?: LocalPerformancePreset.BALANCED.forModel(model.metadata.trainedContext)
    val canLoad = supported && next.allowedBy(model.metadata.trainedContext)
    val active = state.loadedSettings.takeIf { state.loaded?.sha256 == model.sha256 }
    var editing by rememberSaveable(model.sha256) { mutableStateOf(false) }
    val save = actions.saveModelPerformance
    val benchmark = actions.benchmark
    var diagnosticsOpen by remember(model.sha256, active != null) { mutableStateOf(false) }
    HermesCard {
        Text(model.metadata.name, Modifier.testTag("phone-installed-${model.sha256}"), style = MaterialTheme.typography.titleMedium)
        Button({ actions.load(model, next.contextTokens) }, enabled = canLoad && !state.busy,
            modifier = Modifier.testTag("phone-load-${model.sha256.take(8)}")) { Text(label(Words.PhoneLoad)) }
        Text("${model.metadata.architecture} · ${model.metadata.bytes / 1048576} MiB", style = MaterialTheme.typography.bodySmall)
        Text("SHA-256: ${model.sha256}", style = MaterialTheme.typography.labelSmall)
        val source = if (state.modelCatalog.known.any { it.sha256 == model.sha256 }) Words.PhonePublisher else Words.PhoneImported
        Text(label(source), style = MaterialTheme.typography.bodySmall)
        val minimumMiB = (model.minimumLoadMemoryBytes + 1048575) / 1048576
        Text(label(Words.PhoneModelFreeMemory).replace("{number}", minimumMiB.toString()), style = MaterialTheme.typography.bodySmall)
        if (active != null) {
            Text(label(Words.PhoneLoaded).replace("{model}", model.metadata.name))
            Text(modelPerformanceLabel(Words.PhoneModelActiveLimits, active.contextTokens, active.threads, active.maxOutputTokens),
                Modifier.testTag("phone-model-active-limits"), style = MaterialTheme.typography.bodySmall)
            if (benchmark != null) {
                TextButton({ diagnosticsOpen = true }, Modifier.testTag("phone-diagnostics-${model.sha256.take(8)}")) {
                    Text(label(Words.PhoneDiagnostics))
                }
            }
        }
        if (canLoad) {
            Text(modelPerformanceLabel(Words.PhoneModelNextLimits, next.contextTokens, next.threadCap, next.outputTokens),
                Modifier.testTag("phone-model-next-limits"), style = MaterialTheme.typography.bodySmall)
            if (save != null) TextButton({ editing = true }, enabled = !state.busy,
                modifier = Modifier.testTag("phone-model-settings-${model.sha256.take(8)}")) { Text(label(Words.PhoneModelPerformance)) }
        } else if (supported) {
            Text(label(Words.PhoneModelContextTooSmall), style = MaterialTheme.typography.bodySmall)
        }
        if (state.loaded?.sha256 == model.sha256) TextButton(actions.unload, enabled = !state.busy) { Text(label(Words.PhoneUnload)) }
        state.modelCatalog.updateFor(model.sha256)
            ?.takeUnless { update -> state.models.any { it.sha256 == update.sha256 } }
            ?.let { update ->
                TextButton({ reviewUpdate(update) }, enabled = supported && !state.busy,
                    modifier = Modifier.testTag("phone-catalogue-update-${model.sha256.take(8)}")) {
                    Text(label(Words.PhoneCatalogueUpdateReview))
                }
            }
        actions.replacements?.let { replacements ->
            TextButton({ replacements.choose(model) }, enabled = supported && !state.busy,
                modifier = Modifier.testTag("phone-replace-${model.sha256.take(8)}")) {
                Text(label(Words.PhoneChooseReplacement))
            }
        }
        TextButton(remove, enabled = !state.busy) { Text(label(Words.Delete)) }
    }
    if (diagnosticsOpen && active != null && benchmark != null) {
        PhonePerformanceDiagnosticsDialog(state, { benchmark(model.sha256) }, actions.stop, { diagnosticsOpen = false })
    }
    if (editing && save != null && canLoad) {
        PhonePerformanceSettings(model, next, state.busy, { editing = false }) { performance ->
            save(model, performance)
            editing = false
        }
    }
}
