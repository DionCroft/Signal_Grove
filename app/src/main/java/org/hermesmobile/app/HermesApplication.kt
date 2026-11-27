package org.hermesmobile.app

import android.app.Application
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.hermesmobile.app.background.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.local.AndroidModelDownloadNetwork

class HermesApplication : Application() {
    private val appScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 28 && Process.isIsolated()) return
        imageDraftReady.start()
        managementReady.start()
        appScope.launch {preferences.flow.collect {BackgroundChecks.schedule(this@HermesApplication,it);TaskWidget.updateAll(this@HermesApplication)}}
        appScope.launch {database.dao().tasks().collect {TaskWidget.updateAll(this@HermesApplication)}}
    }
    val phoneDrafts=org.hermesmobile.app.local.PhoneDraftHandoff()
    internal val phoneWriting = org.hermesmobile.app.local.PhoneWritingWorkspace()
    internal val writingIntake = org.hermesmobile.app.local.PhoneDraftHandoff()
    internal val notebookHandoffs = org.hermesmobile.app.local.PhoneDraftHandoff()
    val updateWorkGuard=org.hermesmobile.app.updates.UpdateWorkGuard()
    val localAi by lazy {
        val network = AndroidModelDownloadNetwork(checkNotNull(getSystemService(ConnectivityManager::class.java))) {
            preferences.phoneDownloadUnmetered.first()
        }
        org.hermesmobile.app.local.LocalAiController(
            java.io.File(noBackupFilesDir,"local-ai/models"),java.io.File(noBackupFilesDir,"local-ai/chats"),
            applicationInfo.nativeLibraryDir,android.os.Process.is64Bit(),
            {android.app.ActivityManager.MemoryInfo().also {getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it)}.availMem},
            updateWorkGuard,
            readLoadPssKiB = { android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }.totalPss },
            prepareDownloadRoute = network::prepare)
    }
    val managementReady by lazy {appScope.async {database.dao().recoverManagementOperations()}}
    val database by lazy { Room.databaseBuilder(this,HermesDatabase::class.java,"hermes.db").addMigrations(*ALL_HERMES_MIGRATIONS).build() }
    val imageDrafts by lazy {ImageBlobStore(java.io.File(filesDir,"image-drafts"))}
    val imageDraftReady by lazy {appScope.async {imageDrafts.clearAll()}}
    fun discardImageDraft(scope:String,namespace:String) {appScope.launch {runCatching {imageDraftReady.await();imageDrafts.deleteConversation(scope,namespace)}}}
    val images by lazy {ImageMedia(ImageBlobStore(java.io.File(filesDir,"images")))}
    val requests by lazy {TurnRequestStore(java.io.File(filesDir,"turn-requests"))}
    val preferences by lazy { PreferenceStore(this) }
    val credentials by lazy { SecureCredentialStore(this) }
}
