package org.hermesmobile.app

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.hermesmobile.app.background.*
import org.hermesmobile.app.data.*

class HermesApplication : Application() {
    private val appScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        imageDraftReady.start()
        managementReady.start()
        appScope.launch {preferences.flow.collect {BackgroundChecks.schedule(this@HermesApplication,it);TaskWidget.updateAll(this@HermesApplication)}}
        appScope.launch {database.dao().tasks().collect {TaskWidget.updateAll(this@HermesApplication)}}
    }
    val updateWorkGuard=org.hermesmobile.app.updates.UpdateWorkGuard()
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
