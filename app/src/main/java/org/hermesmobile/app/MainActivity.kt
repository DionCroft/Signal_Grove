package org.hermesmobile.app

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.withResumed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.ui.*

class MainActivity : ComponentActivity() {
    private val destination=mutableStateOf<String?>(null)
    private val notebookToken = mutableStateOf<String?>(null)

    companion object {
        internal const val NOTEBOOK_HANDOFF_TOKEN = "notebook_handoff_token"
    }
    private val sharedImages=mutableStateOf<org.hermesmobile.app.data.SharedImages?>(null)
    private var shareConsumed=false
    private var launcherRouting: Job? = null
    private var hermesOpened = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.isLauncherEntry()) return
        intent.getStringExtra(NOTEBOOK_HANDOFF_TOKEN)?.let { notebookToken.value = it }
        // A delivered request updates saved review state and leaves the activity launch identity intact.
        launcherRouting?.cancel()
        destination.value=intent.getStringExtra("destination")?.takeIf {it in setOf("Tasks","Chat")}
        org.hermesmobile.app.data.SharedImages.parse(intent)?.let {shareConsumed=false;sharedImages.value=it}
        openHermes()
    }

    override fun onSaveInstanceState(outState:Bundle) {
        outState.putBoolean("hermes_opened", hermesOpened)
        outState.putBoolean("image_share_consumed",shareConsumed)
        outState.putString("requested_destination",destination.value)
        sharedImages.value?.let {pending ->
            outState.putParcelableArrayList("pending_image_uris",ArrayList(pending.uris))
            outState.putString("pending_image_caption",pending.caption)
            outState.putBoolean("pending_image_invalid",pending.invalid)
        }
        super.onSaveInstanceState(outState)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) notebookToken.value = intent.getStringExtra(NOTEBOOK_HANDOFF_TOKEN)
        shareConsumed=savedInstanceState?.getBoolean("image_share_consumed") ?: false
        if(!shareConsumed) {
            if(savedInstanceState?.containsKey("pending_image_uris")==true) {
                @Suppress("DEPRECATION")
                val uris=if(android.os.Build.VERSION.SDK_INT>=33) savedInstanceState.getParcelableArrayList("pending_image_uris",android.net.Uri::class.java) else savedInstanceState.getParcelableArrayList<android.net.Uri>("pending_image_uris")
                sharedImages.value=org.hermesmobile.app.data.SharedImages(uris.orEmpty(),savedInstanceState.getString("pending_image_caption").orEmpty(),savedInstanceState.getBoolean("pending_image_invalid"))
            } else sharedImages.value=org.hermesmobile.app.data.SharedImages.parse(intent)
        }
        destination.value=(if(savedInstanceState?.containsKey("requested_destination")==true) savedInstanceState.getString("requested_destination") else intent.getStringExtra("destination"))?.takeIf {it in setOf("Tasks","Chat")}
        WindowCompat.enableEdgeToEdge(window)
        if (savedInstanceState?.getBoolean("hermes_opened") != true && intent.isLauncherEntry()) {
            routeLauncher()
        } else {
            openHermes()
        }
    }

    private fun Intent.isLauncherEntry(): Boolean =
        action == Intent.ACTION_MAIN && hasCategory(Intent.CATEGORY_LAUNCHER) &&
            data == null && type == null && clipData == null &&
            !hasExtra("destination") && !hasExtra(Intent.EXTRA_STREAM)

    private fun routeLauncher() {
        launcherRouting = lifecycleScope.launch {
            val preferences = (application as HermesApplication).preferences
            val startOnPhone = try {
                withTimeoutOrNull(5_000) { preferences.phoneStartup.first() }
            } catch (_: IOException) {
                null
            }
            lifecycle.withResumed {
                when (startOnPhone) {
                    true -> openPhone()
                    false -> openHermes()
                    null -> setContent {
                        HermesAppTheme(AppPreferences(), window) { PhoneStartupUnavailable(::openPhone, ::openHermes) }
                    }
                }
            }
        }
    }

    private fun openPhone() {
        startActivity(Intent(this, LocalAiActivity::class.java)
            .putExtra(LocalAiActivity.FROM_LAUNCHER, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    private fun openHermes() {
        if (hermesOpened) return
        hermesOpened = true
        setContent {
            val vm: HermesViewModel=viewModel()
            val p by vm.preferences.collectAsStateWithLifecycle()
            val updates:org.hermesmobile.app.updates.AppUpdateViewModel=viewModel()
            val connecting by vm.connecting.collectAsStateWithLifecycle()
            val activeRun by vm.busy.collectAsStateWithLifecycle()
            val activeControl by vm.controlBusy.collectAsStateWithLifecycle()
            val activeJob by vm.jobBusy.collectAsStateWithLifecycle()
            val transfer by vm.notebookHandoffDraft.collectAsStateWithLifecycle()
            val intake = notebookToken.value
            LaunchedEffect(intake) {
                if (intake != null) {
                    vm.receiveNotebookHandoff(intake)
                    notebookToken.value = null
                }
            }
            HermesAppTheme(p, window) {
                AppUpdateHost(updates,activeRun || activeControl || activeJob || connecting) {
                    HermesApp(vm,destination.value,handled={destination.value=null},sharedImages=sharedImages.value,
                        shareHandled={shareConsumed=true;sharedImages.value=null})
                    transfer?.let { NotebookHandoffDialog(vm, it) { destination.value = "Chat" } }
                }
            }
        }
    }
}
