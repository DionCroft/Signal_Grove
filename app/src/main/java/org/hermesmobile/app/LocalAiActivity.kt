package org.hermesmobile.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.local.LocalAiViewModel
import org.hermesmobile.app.ui.HermesAppTheme
import org.hermesmobile.app.ui.LocalAiRoute
import org.hermesmobile.app.ui.PhoneDownloadNetworkSettings
import org.hermesmobile.app.ui.PhoneStartupSetting

/** Private, explicit phone inference destination. No remote drafts or requests are imported. */
class LocalAiActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[LocalAiViewModel::class.java] }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        model.receiveDraft(intent.getStringExtra(DRAFT_TOKEN))
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
                    downloadNetworkSettings = { close -> PhoneDownloadNetworkSettings(store, close) }
                )
            }
        }
    }

    private fun openHermes() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        const val DRAFT_TOKEN = "phone_draft_token"
        internal const val FROM_LAUNCHER = "phone_from_launcher"
    }
    override fun onStart() { super.onStart(); model.controller.refreshLibrary() }
    override fun onStop() {
        if (!isChangingConfigurations) model.controller.leave()
        super.onStop()
    }
}
