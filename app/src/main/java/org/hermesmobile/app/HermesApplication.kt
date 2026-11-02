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
        appScope.launch {preferences.flow.collect {BackgroundChecks.schedule(this@HermesApplication,it);TaskWidget.updateAll(this@HermesApplication)}}
        appScope.launch {database.dao().tasks().collect {TaskWidget.updateAll(this@HermesApplication)}}
    }
    val database by lazy { Room.databaseBuilder(this,HermesDatabase::class.java,"hermes.db").addMigrations(*ALL_HERMES_MIGRATIONS).build() }
    val requests by lazy {TurnRequestStore(java.io.File(filesDir,"turn-requests"))}
    val preferences by lazy { PreferenceStore(this) }
    val credentials by lazy { SecureCredentialStore(this) }
}
