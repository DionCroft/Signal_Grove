package org.hermesmobile.app

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import org.hermesmobile.app.ui.*

class MainActivity : ComponentActivity() {
    private val destination=mutableStateOf<String?>(null)
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);setIntent(intent);destination.value=intent.getStringExtra("destination")?.takeIf {it in setOf("Tasks","Chat")}}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        destination.value=intent.getStringExtra("destination")?.takeIf {it in setOf("Tasks","Chat")}
        enableEdgeToEdge()
        setContent {
            val vm: HermesViewModel=viewModel()
            val p by vm.preferences.collectAsStateWithLifecycle()
            HermesAppTheme(p) { HermesApp(vm,destination.value) {destination.value=null} }
        }
    }
}
