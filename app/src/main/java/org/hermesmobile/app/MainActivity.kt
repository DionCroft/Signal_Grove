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
    private val sharedImages=mutableStateOf<org.hermesmobile.app.data.SharedImages?>(null)
    private var shareConsumed=false
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        destination.value=intent.getStringExtra("destination")?.takeIf {it in setOf("Tasks","Chat")}
        org.hermesmobile.app.data.SharedImages.parse(intent)?.let {shareConsumed=false;sharedImages.value=it}
    }
    override fun onSaveInstanceState(outState:Bundle) {
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
        shareConsumed=savedInstanceState?.getBoolean("image_share_consumed") ?: false
        if(!shareConsumed) {
            if(savedInstanceState?.containsKey("pending_image_uris")==true) {
                @Suppress("DEPRECATION")
                val uris=if(android.os.Build.VERSION.SDK_INT>=33) savedInstanceState.getParcelableArrayList("pending_image_uris",android.net.Uri::class.java) else savedInstanceState.getParcelableArrayList<android.net.Uri>("pending_image_uris")
                sharedImages.value=org.hermesmobile.app.data.SharedImages(uris.orEmpty(),savedInstanceState.getString("pending_image_caption").orEmpty(),savedInstanceState.getBoolean("pending_image_invalid"))
            } else sharedImages.value=org.hermesmobile.app.data.SharedImages.parse(intent)
        }
        destination.value=(if(savedInstanceState?.containsKey("requested_destination")==true) savedInstanceState.getString("requested_destination") else intent.getStringExtra("destination"))?.takeIf {it in setOf("Tasks","Chat")}
        enableEdgeToEdge()
        setContent {
            val vm: HermesViewModel=viewModel()
            val p by vm.preferences.collectAsStateWithLifecycle()
            val updates:org.hermesmobile.app.updates.AppUpdateViewModel=viewModel()
            val connecting by vm.connecting.collectAsStateWithLifecycle()
            val activeRun by vm.busy.collectAsStateWithLifecycle()
            val activeControl by vm.controlBusy.collectAsStateWithLifecycle()
            val activeJob by vm.jobBusy.collectAsStateWithLifecycle()
            HermesAppTheme(p) { AppUpdateHost(updates,activeRun || activeControl || activeJob || connecting) { HermesApp(vm,destination.value,handled={destination.value=null},sharedImages=sharedImages.value,shareHandled={shareConsumed=true;sharedImages.value=null}) } }
        }
    }
}
