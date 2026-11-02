package org.hermesmobile.app.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.*
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.background.*

@Composable fun BackgroundSettings(vm: HermesViewModel) {
    val p by vm.preferences.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var permissionBlocked by remember {mutableStateOf(false)}
    var queued by remember {mutableStateOf(false)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {allowed ->
        permissionBlocked=!allowed
        if(allowed) vm.updatePreferences(vm.preferences.value.copy(notifications=true))
    }
    HermesCard {
        Word(Words.Notifications);Text(label(Words.NotificationInfo))
        Button({
            if(p.notifications) vm.updatePreferences(p.copy(notifications=false),p)
            else if(Build.VERSION.SDK_INT>=33 && !BackgroundChecks.permitted(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else if(BackgroundChecks.permitted(context)) vm.updatePreferences(p.copy(notifications=true),p)
            else permissionBlocked=true
        }) {Text(label(if(p.notifications) Words.DisableNotifications else Words.EnableNotifications))}
        if(permissionBlocked || (p.notifications && !BackgroundChecks.permitted(context))) Text(label(Words.PermissionNeeded))
        TextButton({context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}) {Text(label(Words.NotificationSettings))}
        Row(Modifier.fillMaxWidth()) {Text(label(Words.MonitorRuns),Modifier.weight(1f));Switch(p.monitorRuns,{vm.updatePreferences(p.copy(monitorRuns=it),p)},enabled=p.notifications)}
        TextButton({BackgroundChecks.checkNow(context);queued=true},enabled=p.notifications && BackgroundChecks.permitted(context)) {Text(label(Words.CheckNow))}
        if(queued) Text(label(Words.CheckQueued))
    }
    HermesCard {
        Word(Words.TaskWidget);Text(label(Words.WidgetInfo))
        val manager=AppWidgetManager.getInstance(context)
        if(manager.isRequestPinAppWidgetSupported) Button({manager.requestPinAppWidget(ComponentName(context,TaskWidget::class.java),null,null)}) {Text(label(Words.AddWidget))}
    }
}
