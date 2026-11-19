package org.hermesmobile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.hermesmobile.app.data.PreferenceStore

@Composable
internal fun PhoneStartupSetting(store: PreferenceStore) {
    val state = remember(store) {
        store.phoneStartup.map { Result.success(it) }.catch { error ->
            if (error !is IOException) throw error
            emit(Result.failure(error))
        }
    }
    val saved by state.collectAsStateWithLifecycle(initialValue = null)
    val scope = LocalLifecycleOwner.current.lifecycleScope
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val enabledLabel = label(Words.PhoneStartupEnabled)

    HermesCard {
        Text(label(Words.PhoneStartup), style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(enabledLabel, Modifier.weight(1f))
            Switch(
                checked = saved?.getOrNull() == true,
                onCheckedChange = { enabled ->
                    if (saving) return@Switch
                    saving = true
                    saveFailed = false
                    scope.launch {
                        try {
                            store.setPhoneStartup(enabled)
                        } catch (_: IOException) {
                            saveFailed = true
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = saved?.isSuccess == true && !saving,
                modifier = Modifier.testTag("phone-startup-toggle").semantics { contentDescription = enabledLabel }
            )
        }
        Text(label(Words.PhoneStartupHelp), style = MaterialTheme.typography.bodySmall)
        if (saved?.isFailure == true) Text(label(Words.PhoneStartupReadFailed), color = MaterialTheme.colorScheme.error)
        if (saveFailed) Text(label(Words.PhoneStartupSaveFailed), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun PhoneStartupUnavailable(openPhone: () -> Unit, openHermes: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(label(Words.PhoneStartupReadFailed), style = MaterialTheme.typography.titleLarge)
            TextButton(openPhone) { Text(label(Words.PhoneStartupOpenLocal)) }
            TextButton(openHermes) { Text(label(Words.PhoneStartupOpenHermes)) }
        }
    }
}
