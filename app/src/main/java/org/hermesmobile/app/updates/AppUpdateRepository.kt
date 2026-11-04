package org.hermesmobile.app.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import org.hermesmobile.app.BuildConfig

class VerifiedAppUpdate(val release:AppRelease,val file:File,val versionCode:Long)
class AppUpdateRepository(private val context:Context,private val client:GitHubUpdateClient=GitHubUpdateClient()) {
    private val downloads=Mutex()
    private val prefs=context.getSharedPreferences("app_updates",Context.MODE_PRIVATE)
    private val folder=File(context.filesDir,"updates/apks").apply {mkdirs()}.canonicalFile
    val autoCheck get()=prefs.getBoolean("automatic",true)
    val previews get()=prefs.getBoolean("previews",false)
    val lastAttempt get()=prefs.getLong("last_attempt",0)
    val lastChecked get()=prefs.getLong("last_checked",0)
    fun setAuto(value:Boolean) {prefs.edit().putBoolean("automatic",value).apply()}
    fun setPreviews(value:Boolean) {prefs.edit().putBoolean("previews",value).putLong("last_attempt",0).remove("available").apply()}
    fun cachedRelease():AppRelease? {
        val raw=prefs.getString("available",null) ?: return null
        val release=runCatching {AppRelease.restore(Json.parseToJsonElement(raw).jsonObject,BuildConfig.DEBUG)}.getOrNull()
        val current=AppVersion.parse(BuildConfig.VERSION_NAME)
        if(release==null || current==null || release.version<=current || release.previews!=previews) {prefs.edit().remove("available").apply();return null}
        return release
    }
    fun dismissed(release:AppRelease)=prefs.getString("dismissed",null)==release.key
    fun dismiss(release:AppRelease) {prefs.edit().putString("dismissed",release.key).apply()}
    suspend fun check():AppRelease? {
        prefs.edit().putLong("last_attempt",System.currentTimeMillis()).apply()
        val current=AppVersion.parse(BuildConfig.VERSION_NAME) ?: throw UpdateVersionFailure()
        val release=client.check(current,BuildConfig.DEBUG,previews)
        prefs.edit().putLong("last_checked",System.currentTimeMillis()).putString("available",release?.json()?.toString()).apply();return release
    }
    @Suppress("DEPRECATION")
    private fun info(path:String?=null):PackageInfo? {
        val flags=if(Build.VERSION.SDK_INT>=28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return if(path==null) context.packageManager.getPackageInfo(context.packageName,flags) else context.packageManager.getPackageArchiveInfo(path,flags)
    }
    @Suppress("DEPRECATION")
    private fun code(info:PackageInfo)=if(Build.VERSION.SDK_INT>=28) info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION")
    private fun signers(info:PackageInfo):Set<String> {
        val signatures=if(Build.VERSION.SDK_INT>=28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map {MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") {b->"%02x".format(b)}}.toSet()
    }
    suspend fun validate(file:File,release:AppRelease):VerifiedAppUpdate=withContext(Dispatchers.IO) {
        if(!release.downloadable || file.canonicalFile.parentFile!=folder || !file.isFile || file.length()!=release.bytes) throw UpdateIntegrityFailure()
        val hash=MessageDigest.getInstance("SHA-256");file.inputStream().use {input->val buffer=ByteArray(32*1024);while(true) {currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0) break;hash.update(buffer,0,n)}}
        if(hash.digest().joinToString("") {"%02x".format(it)}!=release.sha256) throw UpdateIntegrityFailure()
        val archive=info(file.path) ?: throw UpdatePackageFailure();val installed=info() ?: throw UpdatePackageFailure()
        if(archive.packageName!=context.packageName || archive.versionName!=release.version.text) throw UpdatePackageFailure()
        if(!BuildConfig.DEBUG && archive.applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0) throw UpdatePackageFailure()
        if(code(archive)<=code(installed)) throw UpdateVersionFailure()
        if((archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE)>Build.VERSION.SDK_INT) throw UpdateAndroidFailure()
        val actual=signers(archive);if(actual.isEmpty() || actual!=signers(installed)) throw UpdateSignatureFailure()
        VerifiedAppUpdate(release,file,code(archive))
    }
    suspend fun download(release:AppRelease,progress:(Long,Long)->Unit):VerifiedAppUpdate=withContext(Dispatchers.IO) {downloads.withLock {
        require(release.downloadable);discard()
        val file=File(folder,release.sha256+".apk");val partial=File(folder,release.sha256+".part.apk")
        try {
            client.download(release,partial,progress)
            val verified=validate(partial,release)
            currentCoroutineContext().ensureActive()
            if(file.exists()) file.delete();check(partial.renameTo(file))
            check(prefs.edit().putString("ready",release.json().toString()).commit())
            VerifiedAppUpdate(verified.release,file,verified.versionCode)
        } finally {partial.delete()}
    }}
    suspend fun restored():VerifiedAppUpdate?=withContext(Dispatchers.IO) {
        val json=prefs.getString("ready",null) ?: run {discard();return@withContext null}
        var staged:File?=null
        try {val release=AppRelease.restore(Json.parseToJsonElement(json).jsonObject,BuildConfig.DEBUG);staged=File(folder,release.sha256+".apk");validate(staged,release)}
        catch(e:CancellationException) {throw e}
        catch(e:Exception) {staged?.takeIf {it.canonicalFile.parentFile==folder}?.delete();prefs.edit().remove("ready").apply();null}
    }
    fun canInstall()=context.packageManager.canRequestPackageInstalls()
    @Suppress("DEPRECATION")
    fun installerIntent(verified:VerifiedAppUpdate):Intent {
        require(verified.file.canonicalFile.parentFile==folder)
        val uri=FileProvider.getUriForFile(context,context.packageName+".updates",verified.file)
        return Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true)
    }
    fun discard() {prefs.edit().remove("ready").apply();folder.listFiles()?.filter {it.name.matches(Regex("[a-f0-9]{64}\\.(apk|part\\.apk)")) && it.canonicalFile.parentFile==folder}?.forEach {it.delete()}}
}
