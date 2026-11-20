package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.hermesmobile.app.data.PreferenceStore

@Composable
internal fun PhoneDownloadNetworkSettings(store: PreferenceStore, close: () -> Unit) {
    val choices = remember(store) {
        store.phoneDownloadUnmetered.map { Result.success(it) }.catch { error ->
            if (error !is IOException) throw error
            emit(Result.failure(error))
        }
    }
    val saved by choices.collectAsStateWithLifecycle(initialValue = null)
    val value = saved?.getOrNull()
    val scope = LocalLifecycleOwner.current.lifecycleScope
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!saving) close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label(Words.PhoneDownloadNetworks), style = MaterialTheme.typography.titleLarge)
                    Text(label(Words.PhoneDownloadNetworksHelp), style = MaterialTheme.typography.bodyMedium)
                    Column(Modifier.selectableGroup()) {
                        for ((option, word) in listOf(false to Words.PhoneDownloadAnyNetwork, true to Words.PhoneDownloadUnmetered)) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .selectable(selected = value == option, enabled = value != null && !saving, role = Role.RadioButton,
                                        onClick = {
                                            if (!saving && value != option) {
                                                saving = true
                                                saveFailed = false
                                                scope.launch {
                                                    try {
                                                        store.setPhoneDownloadUnmetered(option)
                                                    } catch (_: IOException) {
                                                        saveFailed = true
                                                    } finally {
                                                        saving = false
                                                    }
                                                }
                                            }
                                        })
                                    .testTag(if (option) "phone-network-unmetered" else "phone-network-any"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = value == option, onClick = null, enabled = value != null && !saving)
                                Text(label(word), Modifier.weight(1f).padding(start = 8.dp))
                            }
                        }
                    }
                    if (saved?.isFailure == true) Text(label(Words.PhoneDownloadNetworkReadFailed), color = MaterialTheme.colorScheme.error)
                    if (saveFailed) Text(label(Words.PhoneDownloadNetworkSaveFailed), color = MaterialTheme.colorScheme.error)
                }
                TextButton(close, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-network-close"), enabled = !saving) {
                    Text(label(Words.Close))
                }
            }
        }
    }
}
