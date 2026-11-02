package org.hermesmobile.app

import android.app.NotificationManager
import android.widget.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.hermesmobile.app.background.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BackgroundTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrumentation.targetContext.applicationContext as HermesApplication
    @Test fun notificationsAreOptInPrivateAndDeduplicatedAcrossWorkerInstances()=runBlocking {
        val previous=app.preferences.flow.first();val manager=app.getSystemService(NotificationManager::class.java)
        val task=LocalTask("notification-qa","PRIVATE FIXTURE TITLE",LocalDate.now().toString(),notes="PRIVATE FIXTURE NOTES")
        val id=ReminderPolicy.taskKey(task,LocalDate.now()).hashCode()
        if(android.os.Build.VERSION.SDK_INT>=33) instrumentation.uiAutomation.executeShellCommand("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS").use {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}
        try {
            app.database.dao().save(task);app.preferences.update(previous.copy(notifications=false,monitorRuns=false))
            TestListenableWorkerBuilder<ReminderWorker>(app).build().doWork()
            assertFalse(manager.activeNotifications.any {it.id==id})
            app.preferences.update(previous.copy(notifications=true,monitorRuns=false))
            TestListenableWorkerBuilder<ReminderWorker>(app).build().doWork()
            val notice=manager.activeNotifications.single {it.id==id}.notification
            assertEquals(android.app.Notification.VISIBILITY_PRIVATE,notice.visibility)
            assertFalse(notice.extras.toString().contains(task.title));assertFalse(notice.extras.toString().contains(task.notes))
            manager.cancel(id)
            TestListenableWorkerBuilder<ReminderWorker>(app).build().doWork()
            assertFalse(manager.activeNotifications.any {it.id==id})
        } finally {app.database.dao().delete(task);app.preferences.update(previous);manager.cancelAll()}
    }
    @Test fun registeredWidgetRendersLocalTotalsWithoutPrivateTaskText()=runBlocking {
        val task=LocalTask("widget-qa","PRIVATE WIDGET TITLE",LocalDate.now().toString())
        try {
            app.database.dao().save(task)
            val info=android.appwidget.AppWidgetManager.getInstance(app).installedProviders.single {it.provider.className==TaskWidget::class.java.name}
            assertTrue(info.minWidth>0)
            val views=TaskWidget.buildViews(app)
            instrumentation.runOnMainSync {
                val view=views.apply(app,FrameLayout(app))
                assertTrue(view.findViewById<TextView>(R.id.widget_count).text.isNotBlank())
                assertFalse(view.findViewById<TextView>(R.id.widget_count).text.contains(task.title))
                assertTrue(view.findViewById<TextView>(R.id.widget_open).hasOnClickListeners())
            }
        } finally {app.database.dao().delete(task)}
    }
}
