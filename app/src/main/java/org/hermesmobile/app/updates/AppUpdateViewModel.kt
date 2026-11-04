package org.hermesmobile.app.updates

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.hermesmobile.app.ui.Words

enum class UpdatePhase {Initializing,Idle,Checking,Available,Downloading,Ready}
data class AppUpdateState(val phase:UpdatePhase=UpdatePhase.Initializing,val release:AppRelease?=null,val ready:VerifiedAppUpdate?=null,val progress:Float=0f,val notice:Words?=null,val automatic:Boolean=true,val previews:Boolean=false,val checked:Long=0,val dismissed:Boolean=false,val installRequested:Boolean=false,val installerOpen:Boolean=false) {
    val busy get()=phase in setOf(UpdatePhase.Initializing,UpdatePhase.Checking,UpdatePhase.Downloading) || installerOpen
}
class AppUpdateViewModel @JvmOverloads constructor(application:Application,private val repository:AppUpdateRepository=AppUpdateRepository(application),private val startAutomatically:Boolean=true):AndroidViewModel(application) {
    val state=MutableStateFlow(AppUpdateState());private var job:Job?=null;private val epoch=java.util.concurrent.atomic.AtomicLong()
    init {job=viewModelScope.launch {
        val ready=repository.restored()
        val release=ready?.release ?: repository.cachedRelease()
        state.value=AppUpdateState(if(ready!=null) UpdatePhase.Ready else if(release!=null) UpdatePhase.Available else UpdatePhase.Idle,release,ready,automatic=repository.autoCheck,previews=repository.previews,checked=repository.lastChecked,dismissed=release?.let(repository::dismissed) ?: false)
        checkIfDue()
    }}
    fun checkIfDue() {
        if(startAutomatically && repository.autoCheck && !state.value.busy && !state.value.installRequested && System.currentTimeMillis()-repository.lastAttempt>=24*60*60*1000L) check()
    }
    private fun error(e:Exception)=when(e) {is UpdateIntegrityFailure->Words.UpdateIntegrity;is UpdateSignatureFailure->Words.UpdateSignature;is UpdatePackageFailure->Words.UpdatePackage;is UpdateVersionFailure->Words.UpdateVersion;is UpdateAndroidFailure->Words.UpdateAndroid;is UpdateRateLimited->Words.UpdateRateLimit;else->Words.UpdateFailed}
    fun check() {
        if(state.value.busy) return
        val old=state.value;state.value=old.copy(phase=UpdatePhase.Checking,notice=null,installRequested=false)
        job=viewModelScope.launch {try {
            val release=repository.check()
            val ready=old.ready?.takeIf {it.release.key==release?.key}
            if(old.ready!=null && ready==null) repository.discard()
            state.value=state.value.copy(phase=if(ready!=null) UpdatePhase.Ready else if(release==null) UpdatePhase.Idle else UpdatePhase.Available,release=release,ready=ready,notice=if(release==null) Words.UpdateCurrent else null,checked=repository.lastChecked,dismissed=release?.let(repository::dismissed) ?: false)
        } catch(e:CancellationException) {throw e} catch(e:Exception) {state.value=old.copy(notice=error(e),installRequested=false)}}
    }
    fun download(release:AppRelease) {
        if(state.value.busy || state.value.release?.key!=release.key || !release.downloadable) return
        val operation=epoch.incrementAndGet()
        state.value=state.value.copy(phase=UpdatePhase.Downloading,progress=0f,notice=null,ready=null,installRequested=false)
        job=viewModelScope.launch {try {
            val verified=repository.download(release) {done,total->state.update {current->if(epoch.get()==operation && current.phase==UpdatePhase.Downloading && current.release?.key==release.key) current.copy(progress=(done.toDouble()/total).toFloat().coerceIn(0f,1f)) else current}}
            state.value=state.value.copy(phase=UpdatePhase.Ready,ready=verified,installRequested=true,progress=1f)
        } catch(e:CancellationException) {throw e} catch(e:Exception) {state.value=state.value.copy(phase=UpdatePhase.Available,ready=null,notice=error(e),installRequested=false)}}
    }
    fun cancel() {if(state.value.phase !in setOf(UpdatePhase.Checking,UpdatePhase.Downloading)) return;epoch.incrementAndGet();job?.cancel();state.value=state.value.copy(phase=if(state.value.ready!=null) UpdatePhase.Ready else if(state.value.release!=null) UpdatePhase.Available else UpdatePhase.Idle,notice=Words.UpdateCanceled,installRequested=false)}
    suspend fun awaitOperation() {job?.join()}
    fun automatic(value:Boolean) {repository.setAuto(value);state.value=state.value.copy(automatic=value)}
    fun previews(value:Boolean) {if(state.value.busy) return;repository.setPreviews(value);repository.discard();state.value=state.value.copy(previews=value,ready=null,release=null,phase=UpdatePhase.Idle,installRequested=false,notice=null)}
    fun dismiss() {state.value.release?.let {repository.dismiss(it)};state.value=state.value.copy(dismissed=true,installRequested=false)}
    fun pauseInstall() {state.value=state.value.copy(installRequested=false)}
    fun requestInstall() {if(state.value.ready!=null && !state.value.busy) state.value=state.value.copy(installRequested=true,notice=null)}
    fun canInstall()=repository.canInstall()
    suspend fun installationIntent():Intent? {
        val ready=state.value.ready ?: return null
        // The host's effect is keyed by installRequested. Keep it true across suspended verification.
        state.value=state.value.copy(installerOpen=true)
        return try {val verified=repository.validate(ready.file,ready.release);repository.installerIntent(verified)}
        catch(e:CancellationException) {state.value=state.value.copy(installerOpen=false);throw e}
        catch(e:Exception) {state.value=state.value.copy(installerOpen=false,installRequested=false,ready=null,phase=UpdatePhase.Available,notice=error(e));null}
    }
    fun installerLaunched() {state.value=state.value.copy(installRequested=false)}
    fun installerReturned() {state.value=state.value.copy(installerOpen=false,installRequested=false,notice=Words.UpdateInstallerReturned)}
    fun installerUnavailable() {state.value=state.value.copy(installerOpen=false,installRequested=false,notice=Words.UpdateInstallUnavailable)}
}
