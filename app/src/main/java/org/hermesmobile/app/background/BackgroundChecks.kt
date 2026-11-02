package org.hermesmobile.app.background

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.hermesmobile.app.*
import org.hermesmobile.app.R
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.hermesmobile.app.ui.Words
import org.hermesmobile.app.ui.localized
import org.hermesmobile.app.ui.statusWord
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object ReminderPolicy {
    fun due(tasks: List<LocalTask>,today: LocalDate)=tasks.filter {!it.completed && runCatching {LocalDate.parse(it.dueDate)<=today}.getOrDefault(false)}
    fun taskKey(task: LocalTask,today: LocalDate)="task:${task.id}:${task.dueDate}:$today"
    fun runKey(scope: String,run: String,status: String)="run:$scope:$run:$status"
    fun noteworthy(status: String)=status in setOf("completed","failed","cancelled","interrupted","waiting_for_approval","waiting_for_input")
}
object BackgroundChecks {
    const val CHANNEL="hermes_updates_v1"
    private const val TAG="hermes-background-checks"
    fun permitted(context: Context)= (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun channel(context: Context,p: AppPreferences) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,Words.Notifications.localized(p),NotificationManager.IMPORTANCE_DEFAULT).apply {description=Words.NotificationInfo.localized(p);lockscreenVisibility=Notification.VISIBILITY_PRIVATE})
    }
    fun schedule(context: Context,p: AppPreferences) {
        val manager=WorkManager.getInstance(context)
        if(!p.notifications) {manager.cancelAllWorkByTag(TAG);NotificationManagerCompat.from(context).cancelAll();return}
        channel(context,p)
        manager.enqueueUniquePeriodicWork(TAG,ExistingPeriodicWorkPolicy.UPDATE,PeriodicWorkRequestBuilder<ReminderWorker>(15,TimeUnit.MINUTES).addTag(TAG).build())
    }
    fun checkNow(context: Context) {WorkManager.getInstance(context).enqueueUniqueWork("hermes-check-now",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<ReminderWorker>().addTag(TAG).build())}
}
class ReminderWorker(context: Context,params: WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val app=applicationContext as HermesApplication
        val dao=app.database.dao();val p=app.preferences.flow.first()
        TaskWidget.updateAll(app)
        if(!p.notifications || !BackgroundChecks.permitted(app)) return Result.success()
        BackgroundChecks.channel(app,p)
        suspend fun enabled(config: ServerConfig?=null): Boolean {
            val now=app.preferences.flow.first()
            return now.notifications && BackgroundChecks.permitted(app) && (config==null || (now.monitorRuns && !now.demo && app.credentials.readConfig()==config))
        }
        suspend fun notify(key: String,title: String,text: String,route: String,config: ServerConfig?=null) {
            if(!enabled(config)) return
            if(dao.claimNotification(NotificationReceipt(key,System.currentTimeMillis()))==-1L) return
            val open=PendingIntent.getActivity(app,route.hashCode(),Intent(app,MainActivity::class.java).putExtra("destination",route).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val public=NotificationCompat.Builder(app,BackgroundChecks.CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("Hermes Agent Mobile").setContentText(Words.UpdatesAvailable.localized(p)).build()
            val notification=NotificationCompat.Builder(app,BackgroundChecks.CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public).build()
            try {NotificationManagerCompat.from(app).notify(key.hashCode(),notification)} catch(_:SecurityException) { }
        }
        val today=LocalDate.now()
        for(task in ReminderPolicy.due(dao.tasks().first(),today)) {
            notify(ReminderPolicy.taskKey(task,today),Words.TaskDue.localized(p),Words.TaskDueInfo.localized(p),"Tasks")
        }
        if(p.monitorRuns && !p.demo) {
            try {withTimeout(60_000) {
                val config=app.credentials.readConfig() ?: return@withTimeout
                val api=HermesConnectionRepository(app).client(config)
                for(c in dao.trackedRuns(config.scope)) {
                    ensureActive();if(!enabled(config)) break
                    try {
                        val status=api.run(c.runId).text("status")
                        if(ReminderPolicy.noteworthy(status)) notify(ReminderPolicy.runKey(config.scope,c.runId,status),Words.RunUpdate.localized(p),statusWord(status).localized(p),"Chat",config)
                    } catch(e:CancellationException) {throw e} catch(_:Exception) { /* Read-only best effort; keep receipts unchanged. */ }
                }
            }} catch(e:CancellationException) {if(isStopped) throw e} catch(_:Exception) { }
        }
        return Result.success()
    }
}
