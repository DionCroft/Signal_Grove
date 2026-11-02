package org.hermesmobile.app

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/** Keep fixture credentials out of other tests and preserve an installed test vault. */
class CredentialFixtureRule:ExternalResource() {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HermesApplication
    private var values:Map<String,String> = emptyMap()
    private var previous:org.hermesmobile.app.data.AppPreferences?=null
    override fun before() {
        val prefs=app.getSharedPreferences("credentials",0)
        values=prefs.all.mapValues {(_,value)->require(value is String);value}
        previous=runBlocking {app.preferences.flow.first()}
        check(prefs.edit().clear().commit())
        runBlocking {app.preferences.connection(urlValue="",demoValue=true,httpValue=false)}
    }
    override fun after() {
        val editor=app.getSharedPreferences("credentials",0).edit().clear()
        values.forEach {(key,value)->editor.putString(key,value)};check(editor.commit())
        previous?.let {runBlocking {app.preferences.connection(urlValue=it.url,demoValue=it.demo,httpValue=it.debugHttp)}}
    }
}
