package org.hermesmobile.app

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.mockwebserver.MockWebServer
import org.junit.rules.ExternalResource

/** Keep fixture credentials out of other tests and preserve an installed test vault. */
class CredentialFixtureRule:ExternalResource() {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HermesApplication
    private var values:Map<String,String> = emptyMap()
    private var managementKeys:Set<String> = emptySet()
    private fun receiptKeys():Set<String> = app.database.openHelper.readableDatabase.query("SELECT [key] FROM management_operations").use {rows ->
        buildSet {while(rows.moveToNext()) add(rows.getString(0))}
    }
    private var previous:org.hermesmobile.app.data.AppPreferences?=null
    private val connectionNamespace=java.util.UUID.randomUUID().toString()
    /**
     * The product deliberately retains pending work across inference credentials.
     * Bind a new fixture origin rather than erasing history or relying on a new bearer token.
     */
    fun managementServer(portForAttempt:(Int)->Int={0}):MockWebServer {
        runBlocking {app.managementReady.await()}
        val used=app.database.openHelper.readableDatabase.query("SELECT DISTINCT url FROM management_operations").use {rows ->
            buildSet {while(rows.moveToNext()) {
                val url=rows.getString(0).toHttpUrlOrNull()
                if(url!=null && url.host in setOf("localhost","127.0.0.1","::1")) add(url.port)
            }}
        }
        repeat(32) {attempt ->
            val server=MockWebServer()
            try {server.start(portForAttempt(attempt))}
            catch(error:Exception) {server.close();throw error}
            if(server.port !in used) return server
            server.close()
        }
        error("No fresh local management fixture origin was available.")
    }
    /** Keep durable fixture receipts isolated even when the OS reuses a mock-server port. */
    fun uniqueToken(prefix:String)=prefix+"-"+connectionNamespace
    override fun before() {
        runBlocking {app.managementReady.await()}
        managementKeys=receiptKeys()
        val prefs=app.getSharedPreferences("credentials",0)
        values=prefs.all.mapValues {(_,value)->require(value is String);value}
        previous=runBlocking {app.preferences.flow.first()}
        check(prefs.edit().clear().commit())
        runBlocking {app.preferences.connection(urlValue="",demoValue=true,httpValue=false)}
    }
    override fun after() {
        try {
            // Delete only exact test-created keys. Preserve all pre-existing installed history.
            val created=receiptKeys()-managementKeys
            val db=app.database.openHelper.writableDatabase
            db.beginTransaction()
            try {created.forEach {db.execSQL("DELETE FROM management_operations WHERE [key]=?",arrayOf(it))};db.setTransactionSuccessful()}
            finally {db.endTransaction()}
        } finally {
            val editor=app.getSharedPreferences("credentials",0).edit().clear()
            values.forEach {(key,value)->editor.putString(key,value)};check(editor.commit())
            previous?.let {runBlocking {app.preferences.connection(urlValue=it.url,demoValue=it.demo,httpValue=it.debugHttp)}}
        }
    }
}
