package org.hermesmobile.app

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.ui.*
import org.hermesmobile.app.updates.*
import java.io.File
import java.security.MessageDigest

/** Explicit opt-in runner, never scanned as a JUnit test. Only owned emulator self-update QA uses it. */
class SelfUpdateInstrumentation:Instrumentation() {
    private var args=Bundle()
    override fun onCreate(arguments:Bundle?) {super.onCreate(arguments);args=arguments ?: Bundle();start()}
    @Suppress("DEPRECATION") private fun installedCode():Long {val info=targetContext.packageManager.getPackageInfo(targetContext.packageName,0);return if(android.os.Build.VERSION.SDK_INT>=28) info.longVersionCode else info.versionCode.toLong()}
    private fun status(stage:String) {sendStatus(1,Bundle().apply {putString("stage",stage)})}
    private fun marker()=File(targetContext.filesDir,"update-selftest-marker")
    private fun candidate(file:File,version:String):AppRelease {
        val digest=MessageDigest.getInstance("SHA-256");file.inputStream().use {input->val b=ByteArray(32768);while(true) {val n=input.read(b);if(n<0) break;digest.update(b,0,n)}}
        val sha=digest.digest().joinToString("") {"%02x".format(it)};val name="hermes-agent-mobile-$version-debug.apk"
        return AppRelease(900,"v$version",AppVersion.parse(version)!!,"Owned emulator upgrade fixture",name,"https://github.com/DionCroft/hermes-agent-android/releases/download/v$version/$name".toHttpUrl(),file.length(),sha,true)
    }
    private fun textNode(text:String):AccessibilityNodeInfo? {
        // Compose virtual nodes need a tree walk; platform find-by-text is optional on virtual providers.
        fun find(node:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
            if(node==null) return null
            if(node.text?.toString()?.trim()?.equals(text,true)==true || node.contentDescription?.toString()?.trim()?.equals(text,true)==true) return node
            for(i in 0 until node.childCount) find(node.getChild(i))?.let {return it}
            return null
        }
        return find(uiAutomation.rootInActiveWindow)
    }
    private fun clickNode(start:AccessibilityNodeInfo):Boolean {var node=start;repeat(6) {if(node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;node=node.parent ?: return false};return false}
    private fun click(text:String):Boolean=textNode(text)?.let(::clickNode) ?: false
    private fun launchOwnedActivity():MainActivity {
        // startActivitySync waits for main-thread idle, which ambient animation need not provide.
        val current=java.util.concurrent.atomic.AtomicReference<MainActivity>()
        val app=targetContext.applicationContext as android.app.Application
        val callbacks=object:android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity:Activity) {if(activity is MainActivity) current.set(activity)}
            override fun onActivityCreated(activity:Activity,state:Bundle?) {}
            override fun onActivityStarted(activity:Activity) {}
            override fun onActivityPaused(activity:Activity) {}
            override fun onActivityStopped(activity:Activity) {}
            override fun onActivitySaveInstanceState(activity:Activity,state:Bundle) {}
            override fun onActivityDestroyed(activity:Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {targetContext.startActivity(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await(20) {current.get()!=null};return current.get()!!}
        finally {app.unregisterActivityLifecycleCallbacks(callbacks)}
    }
    private fun await(seconds:Int,condition:()->Boolean) {val until=System.currentTimeMillis()+seconds*1000;while(System.currentTimeMillis()<until) {if(condition()) return;Thread.sleep(150)};error("Timed out waiting for owned update fixture stage")}
    override fun onStart() {
        try {
            val expected=args.getString("expected_code")?.toLongOrNull() ?: 103L;val markerText=args.getString("marker") ?: error("Marker required")
            if(args.getString("mode")=="verify") {check(installedCode()==expected);check(marker().readText()==markerText);finish(Activity.RESULT_OK,Bundle().apply {putLong("installed_code",installedCode());putBoolean("data_retained",true)});return}
            check(targetContext.packageName.endsWith(".debug"));check(installedCode()<expected)
            val external=File(targetContext.getExternalFilesDir(null),"qa-updates").canonicalFile
            fun source(key:String)=File(args.getString(key) ?: error("APK fixture required")).canonicalFile.also {check(it.parentFile==external && it.isFile)}
            val apk=source("candidate");val wrong=source("wrong_signer");val version=args.getString("version") ?: error("Version required")
            val repository=AppUpdateRepository(targetContext);val folder=File(targetContext.filesDir,"updates/apks").apply {mkdirs()}
            val wrongRelease=candidate(wrong,version);val wrongCopy=wrong.copyTo(File(folder,wrongRelease.sha256+".apk"),overwrite=true)
            val rejected=runBlocking {runCatching {repository.validate(wrongCopy,wrongRelease)}.exceptionOrNull()};check(rejected is UpdateSignatureFailure);wrongCopy.delete();status("wrong_signer_rejected")
            targetContext.getSharedPreferences("app_updates",0).edit().putBoolean("automatic",false).putBoolean("previews",true).remove("ready").remove("available").remove("dismissed").commit()
            marker().writeText(markerText)
            val guard=(targetContext.applicationContext as HermesApplication).updateWorkGuard;val workOwner=Any();guard.set(workOwner,true)
            val release=candidate(apk,version)
            MockWebServer().use {server->
                val payload=buildJsonObject {put("id",release.id);put("tag_name",release.tag);put("draft",false);put("prerelease",true);put("body",release.notes);put("assets",buildJsonArray {add(buildJsonObject {put("name",release.assetName);put("state","uploaded");put("size",release.bytes);put("digest","sha256:"+release.sha256);put("browser_download_url",release.assetUrl.toString())})})}
                server.dispatcher=object:Dispatcher() {override fun dispatch(q:RecordedRequest):MockResponse {check(q.getHeader("Authorization")==null && q.getHeader("Cookie")==null);return if(q.path.orEmpty().startsWith("/repos/")) MockResponse().setHeader("Content-Type","application/json").setBody(JsonArray(listOf(payload)).toString()) else MockResponse().setBody(okio.Buffer().write(apk.readBytes()))}}
                val base=server.url("/")
                val http=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).addInterceptor {chain->val q=chain.request();chain.proceed(q.newBuilder().url(base.newBuilder().encodedPath(q.url.encodedPath).encodedQuery(q.url.encodedQuery).build()).build())}.build()
                val activity=launchOwnedActivity()
                lateinit var vm:AppUpdateViewModel
                runOnMainSync {vm=AppUpdateViewModel(activity.application,AppUpdateRepository(targetContext,GitHubUpdateClient(http=http)),false);activity.setContent {HermesAppTheme(AppPreferences(chinese=false,pinyin=false,reducedMotion=true)) {AppUpdateHost(vm,false) {Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {AppUpdateSettings(false)}}}}}
                await(10) {vm.state.value.phase==UpdatePhase.Idle};runOnMainSync {vm.check()};await(20) {vm.state.value.phase==UpdatePhase.Available};status("release_available")
                await(10) {click(Words.UpdateReview.en)};status("review_opened");await(10) {click(Words.UpdateDownload.en)};status("download_approved")
                await(90) {vm.state.value.ready!=null};status("apk_verified")
                await(10) {textNode(Words.UpdateFinishWork.en)!=null};check(!vm.state.value.installerOpen);status("active_work_delays_install");guard.set(workOwner,false)
                if(!vm.canInstall()) {
                    await(10) {click(Words.UpdateAllowInstall.en)}
                    await(15) {uiAutomation.rootInActiveWindow?.packageName?.toString()=="com.android.settings"}
                    fun switch(node:AccessibilityNodeInfo?):AccessibilityNodeInfo? {if(node==null) return null;if(node.isCheckable) return node;for(i in 0 until node.childCount) switch(node.getChild(i))?.let {return it};return null}
                    await(15) {switch(uiAutomation.rootInActiveWindow)!=null}
                    val toggle=switch(uiAutomation.rootInActiveWindow) ?: error("Owned-source permission switch missing")
                    if(!toggle.isChecked) {check(clickNode(toggle))}
                    await(10) {vm.canInstall()}
                    uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);status("source_permission_approved")
                }
                await(20) {
                    val pkg=uiAutomation.rootInActiveWindow?.packageName?.toString().orEmpty()
                    if(pkg==targetContext.packageName || pkg=="com.android.settings") false else click("Update") || click("Install")
                }
                status("android_install_confirmed")
                await(30) {installedCode()==expected};check(marker().readText()==markerText)
                finish(Activity.RESULT_OK,Bundle().apply {putLong("installed_code",installedCode());putBoolean("data_retained",true)})
            }
        } catch(e:Exception) {
            runCatching {val file=File(targetContext.getExternalFilesDir(null),"qa/self-update-driver-failure.png");file.parentFile!!.mkdirs();uiAutomation.takeScreenshot()?.let {bitmap->file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}}
            finish(Activity.RESULT_CANCELED,Bundle().apply {putString("failure_type",e.javaClass.simpleName);putString("failure",e.message?.take(200))})
        }
    }
}
