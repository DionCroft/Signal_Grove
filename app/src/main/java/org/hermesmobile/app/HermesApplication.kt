package org.hermesmobile.app

import android.app.Application
import androidx.room.Room
import org.hermesmobile.app.data.*

class HermesApplication : Application() {
    val database by lazy { Room.databaseBuilder(this,HermesDatabase::class.java,"hermes.db").addMigrations(DatabaseMigrations.FROM_1_TO_2).build() }
    val preferences by lazy { PreferenceStore(this) }
    val credentials by lazy { SecureCredentialStore(this) }
}
