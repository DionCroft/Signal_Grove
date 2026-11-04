package org.hermesmobile.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hermesmobile.app.BuildConfig
import org.hermesmobile.app.updates.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val LocalAppUpdates=staticCompositionLocalOf<AppUpdateViewModel?> {null}
private fun sizeText(bytes:Long)=when {bytes<1024->"$bytes B";bytes<1024*1024->String.format(java.util.Locale.ROOT,"%.1f KB",bytes/1024.0);else->String.format(java.util.Locale.ROOT,"%.1f MB",bytes/1024.0/1024.0)}

@Composable fun AppUpdateHost(vm:AppUpdateViewModel,activeWork:Boolean,content:@Composable ()->Unit) {
    val s by vm.state.collectAsStateWithLifecycle();val context=LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val install=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {vm.installerReturned()}
    val managementBusy by (context.applicationContext as org.hermesmobile.app.HermesApplication).updateWorkGuard.active.collectAsStateWithLifecycle()
    LaunchedEffect(vm,lifecycle) {if(lifecycle.isAtLeast(Lifecycle.State.RESUMED)) vm.checkIfDue()}
    LaunchedEffect(s.installRequested,s.ready,lifecycle,activeWork,managementBusy) {
        if(s.installRequested && s.ready!=null && !activeWork && !managementBusy && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
            if(!vm.canInstall()) vm.pauseInstall()
            else vm.installationIntent()?.let {try {install.launch(it);vm.installerLaunched()} catch(e:Exception) {vm.installerUnavailable()}}
        }
    }
    CompositionLocalProvider(LocalAppUpdates provides vm) {content()}

}
@Composable fun AppUpdateSettings(activeWork:Boolean) {
    val vm=LocalAppUpdates.current ?: return
    val s by vm.state.collectAsStateWithLifecycle();val context=LocalContext.current;val uri=LocalUriHandler.current
    val managementBusy by (context.applicationContext as org.hermesmobile.app.HermesApplication).updateWorkGuard.active.collectAsStateWithLifecycle()
    val workBusy=activeWork || managementBusy
    var review by remember {mutableStateOf<AppRelease?>(null)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {if(vm.canInstall()) vm.requestInstall() else vm.pauseInstall()}
    HermesCard(Modifier.testTag("app-updates")) {
        Word(Words.AppUpdates)
        Text(label(Words.UpdateInstalled)+" "+BuildConfig.VERSION_NAME,style=MaterialTheme.typography.labelMedium)
        Text(label(Words.UpdateSource),style=MaterialTheme.typography.bodySmall)
        Row {Text(label(Words.UpdateAutomatic),Modifier.weight(1f));Switch(s.automatic,vm::automatic,enabled=!s.busy,modifier=Modifier.testTag("update-automatic"))}
        Row {Text(label(Words.UpdatePreviews),Modifier.weight(1f));Switch(s.previews,vm::previews,enabled=!s.busy,modifier=Modifier.testTag("update-previews"))}
        if(s.checked>0) Text(label(Words.UpdateLastChecked)+" "+DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(s.checked)),style=MaterialTheme.typography.labelSmall)
        if(s.phase==UpdatePhase.Initializing || s.phase==UpdatePhase.Checking) QuietProgress()
        else if(s.phase==UpdatePhase.Downloading) {
            LinearProgressIndicator(progress={s.progress},modifier=Modifier.fillMaxWidth())
            Text(label(Words.UpdateDownloading)+" "+(s.progress*100).toInt()+"%")
        }
        if(s.phase in setOf(UpdatePhase.Checking,UpdatePhase.Downloading)) TextButton(vm::cancel,Modifier.testTag("update-cancel")) {Text(label(Words.Cancel))}
        s.notice?.let {Text(label(it),modifier=Modifier.testTag("update-notice"),style=MaterialTheme.typography.bodySmall)}
        s.release?.let {release->
            Text(label(if(s.ready!=null) Words.UpdateReady else Words.UpdateAvailable)+" "+release.version.text,style=MaterialTheme.typography.titleMedium)
            if(!release.downloadable) Text(label(Words.UpdateMissingApk))
            if(s.ready==null) PixelPrimaryButton({review=release},enabled=!s.busy && release.downloadable,modifier=Modifier.testTag("update-review-open")) {Text(label(Words.UpdateReview))}
            else {
                Text(label(Words.UpdateInstallEffect),style=MaterialTheme.typography.bodySmall)
                if(workBusy) Text(label(Words.UpdateFinishWork))
                PixelPrimaryButton({
                    if(vm.canInstall()) vm.requestInstall()
                    else try {permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+context.packageName)))} catch(e:Exception) {vm.installerUnavailable()}
                },enabled=!s.busy && !workBusy,modifier=Modifier.testTag("update-install")) {Text(label(if(vm.canInstall()) Words.UpdateInstall else Words.UpdateAllowInstall))}
            }
            TextButton({uri.openUri(release.page)},Modifier.testTag("update-release-page")) {Text(label(Words.UpdateReleasePage))}
        }
        TextButton(vm::check,enabled=!s.busy,modifier=Modifier.testTag("update-check")) {Text(label(Words.UpdateCheck))}
    }
    review?.let {release->AlertDialog(onDismissRequest={review=null},title={Text(label(Words.UpdateReview))},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()).testTag("update-review"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(release.version.text,style=MaterialTheme.typography.titleLarge);Text(OfficialUpdateEndpoints.REPO);release.bytes?.let {Text(sizeText(it))};Text(label(Words.UpdateDownloadEffect));Text(label(Words.UpdateInstallEffect));if(workBusy) Text(label(Words.UpdateFinishWork));if(release.notes.isNotBlank()) {Text(label(Words.UpdateNotes));Text(release.notes)}
    }},confirmButton={TextButton({review=null;vm.download(release)},enabled=!s.busy && release.downloadable,modifier=Modifier.testTag("update-download-confirm")) {Text(label(Words.UpdateDownload))}},dismissButton={TextButton({review=null},Modifier.testTag("update-review-cancel")) {Text(label(Words.Cancel))}})}
}
@Composable fun AppUpdateHint(openSettings:()->Unit) {
    val vm=LocalAppUpdates.current ?: return;val s by vm.state.collectAsStateWithLifecycle()
    if(s.release!=null && !s.dismissed) HermesCard(Modifier.testTag("update-hint")) {
        Text(label(if(s.ready!=null) Words.UpdateReady else Words.UpdateAvailable)+" "+s.release!!.version.text,style=MaterialTheme.typography.titleMedium)
        Text(label(Words.UpdateHint),style=MaterialTheme.typography.bodySmall)
        Row {TextButton(openSettings) {Text(label(Words.AppUpdates))};TextButton(vm::dismiss) {Text(label(Words.Later))}}
    }
}
