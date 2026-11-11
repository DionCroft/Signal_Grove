package org.hermesmobile.app

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

/** Private, explicit phone inference destination. No remote drafts or requests are imported. */
class LocalAiActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[LocalAiViewModel::class.java] }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            val preferences by (application as HermesApplication).preferences.flow.collectAsStateWithLifecycle(AppPreferences())
            HermesAppTheme(preferences) { LocalAiRoute(model, ::finish) }
        }
    }
    override fun onStart() { super.onStart(); model.controller.refreshLibrary() }
    override fun onStop() {
        if (!isChangingConfigurations) model.controller.leave()
        super.onStop()
    }
}
