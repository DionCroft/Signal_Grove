package org.hermesmobile.app.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.hermesmobile.app.local.LocalAiState

@Composable
internal fun PhoneOfflineSetup(
    state: LocalAiState,
    supported: Boolean,
    openCatalogue: () -> Unit,
    chooseFile: () -> Unit,
    openModels: () -> Unit,
    openChat: () -> Unit,
    openHistory: () -> Unit,
    troubleshoot: () -> Unit,
    close: () -> Unit,
    startupSetting: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val readyToLoad = supported && !state.busy && state.models.isNotEmpty()
    val readyToChat = supported && !state.busy && state.loaded != null
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("phone-offline-setup"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(label(Words.PhoneOfflineSetup), style = MaterialTheme.typography.titleLarge)
                    if (!supported) Text(label(Words.PhoneUnsupported), style = MaterialTheme.typography.bodySmall)
                    if (state.busy) Text(label(Words.PhoneOfflineSetupBusy), style = MaterialTheme.typography.bodySmall)
                    HermesCard {
                        Text(label(Words.PhoneOfflineSetupGet), style = MaterialTheme.typography.titleMedium)
                        Text(label(Words.PhoneOfflineSetupGetHelp), style = MaterialTheme.typography.bodySmall)
                        Text(label(Words.PhoneOfflineSetupInstalled).replace("{count}", state.models.size.toString()),
                            Modifier.testTag("phone-offline-setup-installed"))
                        state.modelStorageAvailableBytes?.let { available ->
                            Text(label(Words.PhoneCatalogueUpdateFree).replace("{available}", Formatter.formatFileSize(context, available)),
                                Modifier.testTag("phone-offline-setup-space"), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(openCatalogue, Modifier.testTag("phone-offline-setup-catalogue")) {
                            Text(label(Words.PhoneCatalogue))
                        }
                        TextButton(chooseFile, enabled = supported && !state.busy, modifier = Modifier.testTag("phone-offline-setup-import")) {
                            Text(label(Words.PhoneImport))
                        }
                    }
                    HermesCard {
                        Text(label(Words.PhoneOfflineSetupLoad), style = MaterialTheme.typography.titleMedium)
                        Text(label(Words.PhoneOfflineSetupLoadHelp), style = MaterialTheme.typography.bodySmall)
                        if (supported && state.models.isEmpty()) Text(label(Words.PhoneOfflineSetupNoModel))
                        state.loaded?.let { model ->
                            Text(label(Words.PhoneLoaded).replace("{model}", model.metadata.name),
                                Modifier.testTag("phone-offline-setup-loaded"))
                        }
                        TextButton(openModels, enabled = readyToLoad, modifier = Modifier.testTag("phone-offline-setup-models")) {
                            Text(label(Words.PhoneModels))
                        }
                    }
                    HermesCard {
                        Text(label(Words.PhoneOfflineSetupTry), style = MaterialTheme.typography.titleMedium)
                        Text(label(Words.PhoneOfflineSetupTryHelp), style = MaterialTheme.typography.bodySmall)
                        if (state.loaded == null) Text(label(Words.PhoneOfflineSetupNeedsLoad), style = MaterialTheme.typography.bodySmall)
                        if (state.history.isNotEmpty()) {
                            Text(label(Words.PhoneOfflineSetupHistory).replace("{count}", state.history.size.toString()),
                                Modifier.testTag("phone-offline-setup-saved"))
                        }
                        TextButton(openChat, enabled = readyToChat, modifier = Modifier.testTag("phone-offline-setup-chat")) {
                            Text(label(Words.PhoneOfflineSetupOpenChat))
                        }
                        if (state.history.isNotEmpty()) {
                            TextButton(openHistory, Modifier.testTag("phone-offline-setup-history")) { Text(label(Words.PhoneHistory)) }
                        }
                    }
                    TextButton(troubleshoot, Modifier.testTag("phone-offline-setup-troubleshoot")) {
                        Text(label(Words.PhoneOfflineSetupTroubleshoot))
                    }
                    startupSetting?.invoke()
                }
                TextButton(close, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-offline-setup-close")) {
                    Text(label(Words.Close))
                }
            }
        }
    }
}
