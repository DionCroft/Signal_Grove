package org.hermesmobile.app.local

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import org.hermesmobile.app.HermesApplication
import org.hermesmobile.app.LocalAiActivity
import org.hermesmobile.app.R
import org.hermesmobile.app.background.BackgroundChecks
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.ui.Words
import org.hermesmobile.app.ui.localized
import org.hermesmobile.app.ui.phoneDownloadPhase

/** A user-initiated, process-local lease for one reviewed download. */
class PhoneModelDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller get() = (application as HermesApplication).localAi
    private val notifications get() = checkNotNull(getSystemService(NotificationManager::class.java))
    private var owner: String? = null
    private var observation: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ID)
        if (intent?.action == PAUSE) {
            if (id != null && id == owner) controller.stopBackgroundDownload(id)
            if (owner == null) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val state = controller.state.value
        val model = state.activeDownload
        if (intent?.action != START || id == null || state.backgroundDownloadId != id || model == null) {
            if (owner == null) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (owner == id) return START_NOT_STICKY

        val words = AppPreferences(chinese = intent.getBooleanExtra(EXTRA_ZH, false), pinyin = intent.getBooleanExtra(EXTRA_PIN, false))
        try {
            if (!visible(this, words)) throw IllegalStateException("Download notifications are disabled")
            ServiceCompat.startForeground(this, NOTICE_ID, notice(id, model, words, Progress.from(state)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (_: RuntimeException) {
            controller.onDownloadServiceUnavailable(id)
            if (owner == null) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (!controller.onDownloadServicePromoted(id)) {
            if (owner == null) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
            return START_NOT_STICKY
        }
        observation?.cancel()
        owner = id
        observation = scope.launch {
            try {
                coroutineScope {
                    val visibility = launch {
                        while (isActive) {
                            delay(1_000)
                            if (!visible(this@PhoneModelDownloadService, words)) {
                                controller.stopBackgroundDownload(id, force = true)
                                finish(id)
                                break
                            }
                        }
                    }
                    try {
                        controller.state.takeWhile { it.backgroundDownloadId == id }
                            .map(Progress::from).distinctUntilChanged().collect { progress ->
                                notifications.notify(NOTICE_ID, notice(id, model, words, progress))
                            }
                    } finally {
                        visibility.cancel()
                    }
                }
                finish(id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: RuntimeException) {
                controller.stopBackgroundDownload(id, force = true)
                finish(id)
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        owner?.let { controller.stopBackgroundDownload(it, force = true); finish(it) } ?: stopSelf()
    }

    override fun onDestroy() {
        owner?.let { controller.stopBackgroundDownload(it, force = true) }
        scope.cancel()
        super.onDestroy()
    }

    private fun finish(id: String) {
        if (owner != id) return
        owner = null
        observation?.cancel()
        observation = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private data class Progress(val percent: Int?, val phase: LocalTransferPhase?) {
        companion object {
            fun from(state: LocalAiState): Progress {
                val transfer = state.transfer ?: return Progress(null, null)
                return Progress(((transfer.bytes.toDouble() / transfer.total) * 100).toInt().coerceIn(0, 100), transfer.phase)
            }
        }
    }

    private fun notice(id: String, model: CuratedPhoneModel, words: AppPreferences, progress: Progress): Notification {
        val pause = PendingIntent.getService(this, 0, Intent(this, PhoneModelDownloadService::class.java)
            .setAction(PAUSE).setData(Uri.fromParts("signal-grove-download", id, null)).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, LocalAiActivity::class.java)
            .setData(Uri.fromParts("signal-grove-model-downloads", id, null))
            .putExtra(LocalAiActivity.OPEN_DOWNLOADS, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val phase = phoneDownloadPhase(progress.phase).localized(words)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(model.displayName)
            .setContentText(phase)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, progress.percent ?: 0, progress.percent == null)
            .apply {
                if (progress.phase != LocalTransferPhase.INSTALLING) addAction(0, Words.PhoneDownloadPause.localized(words), pause)
            }
            .build()
    }

    companion object {
        internal const val CHANNEL = "phone_model_download"
        internal const val NOTICE_ID = 0x53474d44
        private const val START = "org.hermesmobile.app.model_download.START"
        private const val PAUSE = "org.hermesmobile.app.model_download.PAUSE"
        private const val EXTRA_ID = "lease"
        private const val EXTRA_ZH = "chinese"
        private const val EXTRA_PIN = "pinyin"

        internal fun start(activity: Activity, controller: LocalAiController, model: CuratedPhoneModel, words: AppPreferences) {
            if (!visible(activity, words)) {
                controller.backgroundNotificationRequired()
                return
            }
            val id = controller.requestBackgroundDownload(model) ?: return
            try {
                ContextCompat.startForegroundService(activity, Intent(activity, PhoneModelDownloadService::class.java)
                    .setAction(START).putExtra(EXTRA_ID, id)
                    .putExtra(EXTRA_ZH, words.chinese).putExtra(EXTRA_PIN, words.pinyin))
            } catch (_: RuntimeException) {
                controller.onDownloadServiceUnavailable(id)
            }
        }

        internal fun notificationSettings(context: Context): Intent =
            if (BackgroundChecks.permitted(context)) {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
            } else {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }

        private fun visible(context: Context, words: AppPreferences): Boolean {
            val manager = checkNotNull(context.getSystemService(NotificationManager::class.java))
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, Words.PhoneDownloads.localized(words),
                    NotificationManager.IMPORTANCE_LOW))
            }
            val importance = manager.getNotificationChannel(CHANNEL)?.importance
            return BackgroundChecks.permitted(context) && importance != null && importance != NotificationManager.IMPORTANCE_NONE
        }
    }
}
