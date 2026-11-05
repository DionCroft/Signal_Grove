package org.hermesmobile.app

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.hermesmobile.app.updates.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class AppUpdateStorageTest {
    @get:Rule val fixture=UpdateFixtureRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val repository get()=AppUpdateRepository(context)
    private fun candidate(file:File,name:String=BuildConfig.VERSION_NAME):AppRelease {
        val sha=file.inputStream().use {input->val digest=MessageDigest.getInstance("SHA-256");val b=ByteArray(32768);while(true) {val n=input.read(b);if(n<0) break;digest.update(b,0,n)};digest.digest().joinToString("") {"%02x".format(it)}}
        val asset="hermes-agent-mobile-$name-debug.apk"
        return AppRelease(111,"v$name",AppVersion.parse(name)!!,"fixture",asset,"https://github.com/DionCroft/Signal_Grove/releases/download/v$name/$asset".toHttpUrl(),file.length(),sha,true)
    }
    private fun ownApk()=File(context.applicationInfo.sourceDir).copyTo(File(fixture.folder,"fixture.apk"))
    @Test fun availableNoticeSurvivesRepositoryRestartButNotChannelChangeOrInstalledVersion() {
        val prefs=context.getSharedPreferences("app_updates",0)
        val release=AppRelease(900,"v9.0.0",AppVersion.parse("9.0.0")!!,"fixture",null,null,null,null,false)
        prefs.edit().putBoolean("previews",false).putString("available",release.json().toString()).commit()
        assertEquals(release.key,AppUpdateRepository(context).cachedRelease()!!.key)
        repository.setPreviews(true);assertNull(AppUpdateRepository(context).cachedRelease())
        val current=AppRelease(901,"v"+BuildConfig.VERSION_NAME,AppVersion.parse(BuildConfig.VERSION_NAME)!!,"fixture",null,null,null,null,true)
        prefs.edit().putString("available",current.json().toString()).commit();assertNull(repository.cachedRelease());assertFalse(prefs.contains("available"))
    }
    @Test fun installedApkCannotBeOfferedAsANewerUpdate()=runBlocking {val file=ownApk();assertTrue(runCatching {repository.validate(file,candidate(file))}.exceptionOrNull() is UpdateVersionFailure)}
    @Test fun anotherPackageCannotUpdateThisApplication()=runBlocking {val file=File(InstrumentationRegistry.getInstrumentation().context.applicationInfo.sourceDir).copyTo(File(fixture.folder,"fixture.apk"));assertTrue(runCatching {repository.validate(file,candidate(file,"1.2.0"))}.exceptionOrNull() is UpdatePackageFailure)}
    @Test fun releaseVersionMustMatchTheActualApk()=runBlocking {val file=ownApk();assertTrue(runCatching {repository.validate(file,candidate(file,"1.9.0"))}.exceptionOrNull() is UpdatePackageFailure)}
    @Test fun modifiedBytesCannotReachPackageInspectionOrInstaller()=runBlocking {val file=ownApk();val release=candidate(file);file.appendBytes(byteArrayOf(1));assertTrue(runCatching {repository.validate(file,release)}.exceptionOrNull() is UpdateIntegrityFailure)}
    @Test fun filesOutsideOwnedUpdateDirectoryAreRejected()=runBlocking {val file=File(context.applicationInfo.sourceDir);assertTrue(runCatching {repository.validate(file,candidate(file))}.exceptionOrNull() is UpdateIntegrityFailure)}
    @Test fun installerSharesOnlyScopedContentUriWithReadGrant() {
        val file=ownApk();val release=candidate(file);val intent=repository.installerIntent(VerifiedAppUpdate(release,file,999))
        @Suppress("DEPRECATION") assertEquals(Intent.ACTION_INSTALL_PACKAGE,intent.action)
        assertEquals("content",intent.data!!.scheme);assertEquals(context.packageName+".updates",intent.data!!.authority);assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0);assertFalse(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION!=0)
        assertEquals(file.length(),context.contentResolver.openAssetFileDescriptor(intent.data!!,"r")!!.use {it.length})
        @Suppress("DEPRECATION") val provider=context.packageManager.resolveContentProvider(context.packageName+".updates",0)!!
        assertFalse(provider.exported);assertTrue(provider.grantUriPermissions)
    }
    @Test fun completedOrStaleReadyMetadataCannotReinstallAnOldVersion()=runBlocking {
        val file=ownApk();val release=candidate(file);val stored=File(fixture.folder,release.sha256+".apk");check(file.renameTo(stored));context.getSharedPreferences("app_updates",0).edit().putString("ready",release.json().toString()).commit()
        assertNull(repository.restored());assertFalse(context.getSharedPreferences("app_updates",0).contains("ready"));assertFalse(stored.exists())
    }
}
