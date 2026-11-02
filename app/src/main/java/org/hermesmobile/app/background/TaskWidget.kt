package org.hermesmobile.app.background

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.widget.RemoteViews
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.hermesmobile.app.*
import org.hermesmobile.app.ui.Words
import org.hermesmobile.app.ui.localized
import java.time.LocalDate

class TaskWidget:AppWidgetProvider() {
    override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) {refresh(context)}
    override fun onReceive(context: Context,intent: Intent) {
        super.onReceive(context,intent)
        if(intent.action==REFRESH) refresh(context)
    }
    private fun refresh(context: Context) {
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {try {updateAll(context)} finally {pending.finish()}}
    }
    companion object {
        const val REFRESH="org.hermesmobile.app.WIDGET_REFRESH"
        suspend fun updateAll(context: Context) {
            val app=context.applicationContext as HermesApplication
            val manager=AppWidgetManager.getInstance(context)
            val ids=manager.getAppWidgetIds(ComponentName(context,TaskWidget::class.java))
            if(ids.isEmpty()) return
            val views=buildViews(app)
            ids.forEach {manager.updateAppWidget(it,views)}
        }
        suspend fun buildViews(context: Context): RemoteViews {
            val app=context.applicationContext as HermesApplication
            val p=app.preferences.flow.first();val tasks=app.database.dao().tasks().first()
            val open=tasks.count {!it.completed};val due=ReminderPolicy.due(tasks,LocalDate.now()).size
            val views=RemoteViews(context.packageName,R.layout.task_widget)
            fun compact(word: Words)=word.en+if(p.chinese) " / "+word.zh else ""
            views.setTextViewText(R.id.widget_title,compact(Words.Tasks))
            views.setTextViewText(R.id.widget_count,"$open · "+compact(Words.OpenTasks)+"\n$due · "+compact(Words.DueTasks))
            views.setTextViewText(R.id.widget_open,compact(Words.OpenTasksApp))
            views.setContentDescription(R.id.widget_open,Words.OpenTasksApp.localized(p))
            views.setTextViewText(R.id.widget_refresh,compact(Words.Refresh))
            views.setContentDescription(R.id.widget_refresh,Words.Refresh.localized(p))
            val launch=PendingIntent.getActivity(context,41,Intent(context,MainActivity::class.java).putExtra("destination","Tasks").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_open,launch)
            views.setOnClickPendingIntent(R.id.widget_count,launch)
            views.setOnClickPendingIntent(R.id.widget_refresh,PendingIntent.getBroadcast(context,42,Intent(context,TaskWidget::class.java).setAction(REFRESH),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return views
        }
    }
}
