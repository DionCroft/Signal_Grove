package org.hermesmobile.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.local.LocalAiViewModel
import org.hermesmobile.app.local.PhoneModelDownloadService
import org.hermesmobile.app.ui.HermesAppTheme
import org.hermesmobile.app.ui.LocalAiRoute
import org.hermesmobile.app.ui.PhoneDownloadNetworkSettings
import org.hermesmobile.app.ui.PhoneStartupSetting

/** Private, explicit phone inference destination. No remote drafts or requests are imported. */
class LocalAiActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[LocalAiViewModel::class.java] }
    private val showDownloads = mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        model.receiveDraft(intent.getStringExtra(DRAFT_TOKEN))
        if (savedInstanceState == null) receiveWriting(intent)
        showDownloads.value = savedInstanceState == null && intent.getBooleanExtra(OPEN_DOWNLOADS, false)
        val store = (application as HermesApplication).preferences
        val standalone = intent.getBooleanExtra(FROM_LAUNCHER, false)
        setContent {
            val preferences by store.flow.collectAsStateWithLifecycle(AppPreferences())
            HermesAppTheme(preferences) {
                LocalAiRoute(
                    model,
                    close = ::finish,
                    openHermes = if (standalone) ::openHermes else null,
                    startupSetting = { PhoneStartupSetting(store) },
                    downloadNetworkSettings = { close -> PhoneDownloadNetworkSettings(store, close) },
                    backgroundDownload = { PhoneModelDownloadService.start(this, model.controller, it, preferences) },
                    downloadNotificationSettings = { startActivity(PhoneModelDownloadService.notificationSettings(this)) },
                    requestedDownloads = showDownloads.value,
                    downloadsOpened = { showDownloads.value = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveWriting(intent)
        if (intent.getBooleanExtra(OPEN_DOWNLOADS, false)) showDownloads.value = true
    }

    private fun receiveWriting(intent: Intent) {
        model.receiveWriting(intent.getStringExtra(WRITING_TOKEN), intent.getBooleanExtra(WRITING_REJECTED, false))
    }

    private fun openHermes() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        const val DRAFT_TOKEN = "phone_draft_token"
        internal const val WRITING_TOKEN = "phone_writing_token"
        internal const val WRITING_REJECTED = "phone_writing_rejected"
        internal const val FROM_LAUNCHER = "phone_from_launcher"
        internal const val OPEN_DOWNLOADS = "open_phone_model_downloads"
    }
    override fun onStart() { super.onStart(); model.controller.refreshLibrary() }
    override fun onStop() {
        if (!isChangingConfigurations) model.controller.leave()
        super.onStop()
    }
}
