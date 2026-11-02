package org.hermesmobile.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.hermesmobile.app.network.LocalNetworkAccess

@Composable fun LocalNetworkSettings() {
    if(Build.VERSION.SDK_INT<37) return
    val context=LocalContext.current
    var allowed by remember {mutableStateOf(LocalNetworkAccess.granted(context))}
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {allowed=LocalNetworkAccess.granted(context)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {allowed=LocalNetworkAccess.granted(context)}
    Text(label(Words.LocalNetworkInfo),style=MaterialTheme.typography.bodySmall)
    TextButton({permission.launch(LocalNetworkAccess.PERMISSION)},Modifier.testTag("lan-permission"),enabled=!allowed) {Text(label(if(allowed) Words.LocalNetworkGranted else Words.LocalNetworkAllow))}
    TextButton({context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}) {Text(label(Words.SystemPermissions))}
}
