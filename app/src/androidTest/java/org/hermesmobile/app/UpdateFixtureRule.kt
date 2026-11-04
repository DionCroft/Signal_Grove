package org.hermesmobile.app

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import java.io.File
import java.util.UUID

/** Preserve an installed updater's cache/settings while exercising isolated update fixtures. */
class UpdateFixtureRule:ExternalResource() {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get()=context.getSharedPreferences("app_updates",0)
    private var saved:Map<String,*> = emptyMap<String,Any>()
    private val backupName="update-fixture-backup-"+UUID.randomUUID()
    val folder get()=File(context.filesDir,"updates/apks").apply {mkdirs()}
    override fun before() {
        saved=prefs.all.toMap();check(prefs.edit().clear().putBoolean("automatic",false).commit())
        val backup=File(context.cacheDir,backupName).apply {mkdirs()}
        folder.listFiles().orEmpty().forEach {file->check(file.isFile);file.copyTo(File(backup,file.name));check(file.delete())}
    }
    override fun after() {
        folder.listFiles().orEmpty().forEach {check(it.isFile);check(it.delete())}
        val backup=File(context.cacheDir,backupName);backup.listFiles().orEmpty().forEach {it.copyTo(File(folder,it.name));check(it.delete())};backup.delete()
        val edit=prefs.edit().clear();saved.forEach {(k,v)->when(v) {is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Long->edit.putLong(k,v);is Int->edit.putInt(k,v);else->error("Unexpected updater setting")}};check(edit.commit())
    }
}
