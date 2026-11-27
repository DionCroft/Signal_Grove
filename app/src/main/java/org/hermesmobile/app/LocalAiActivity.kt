package org.hermesmobile.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
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
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        model.receiveDraft(intent.getStringExtra(DRAFT_TOKEN))
        if (savedInstanceState == null) receiveEntry(intent)
        showDownloads.value = savedInstanceState == null && intent.getBooleanExtra(OPEN_DOWNLOADS, false)
        val store = (application as HermesApplication).preferences
        val standalone = intent.getBooleanExtra(FROM_LAUNCHER, false)
        setContent {
            val preferences by store.flow.collectAsStateWithLifecycle(AppPreferences())
            HermesAppTheme(preferences, window) {
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
        receiveEntry(intent)
        if (intent.getBooleanExtra(OPEN_DOWNLOADS, false)) showDownloads.value = true
    }

    private fun receiveEntry(intent: Intent) {
        if (intent.getBooleanExtra(OPEN_CAPTURE, false)) {
            model.openCapture(intent.getStringExtra(CAPTURE_ID), intent.getBooleanExtra(WIDGET_NOTE, false))
            return
        }
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
        private const val OPEN_CAPTURE = "open_phone_capture"
        private const val CAPTURE_ID = "open_phone_capture_id"
        private const val WIDGET_NOTE = "open_phone_widget_note"

        internal fun captureIntent(context: Context, captureId: String? = null, fromWidget: Boolean = false) =
            Intent(context, LocalAiActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(OPEN_CAPTURE, true).putExtra(CAPTURE_ID, captureId).putExtra(WIDGET_NOTE, fromWidget)
    }
    override fun onStart() { super.onStart(); model.controller.refreshLibrary() }
    override fun onStop() {
        if (!isChangingConfigurations) model.leavePhone()
        super.onStop()
    }
}
