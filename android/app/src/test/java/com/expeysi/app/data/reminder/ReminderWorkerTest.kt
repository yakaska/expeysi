package com.expeysi.app.data.reminder

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.expeysi.app.data.ReminderStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Поведение воркера через doWork() на тестовом WorkManager: показ уведомления
 * и продолжение/отмена цепочки в зависимости от состояния напоминания.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderWorkerTest {

    private lateinit var context: Context
    private lateinit var store: ReminderStore
    private lateinit var scheduler: ReminderScheduler
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        store = ReminderStore(context.getSharedPreferences("reminder_test", Context.MODE_PRIVATE))
        scheduler = ReminderScheduler(context, store)
    }

    /** Воркер с реальными store/scheduler, но без Hilt. */
    private fun worker(): ReminderWorker = TestListenableWorkerBuilder<ReminderWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                parameters: WorkerParameters,
            ): ListenableWorker = ReminderWorker(appContext, parameters, store, scheduler)
        })
        .build()

    @Test
    fun `напоминание включено - уведомление с нужным id показано и цепочка продолжается`() = runTest {
        store.setEnabled(true)
        worker().doWork()

        val ids = context.getSystemService(NotificationManager::class.java)
            .activeNotifications
            .map { it.id }
        assertTrue(
            "ожидали уведомление ${ReminderNotifications.NOTIFICATION_ID}, было: $ids",
            ids.contains(ReminderNotifications.NOTIFICATION_ID),
        )

        // Воркер перезапланировал следующий день тем же уникальным именем.
        val infos = workManager.getWorkInfosForUniqueWork(ReminderScheduler.WORK_NAME).get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
    }

    @Test
    fun `напоминание выключено - уведомлений нет и цепочка отменяется`() = runTest {
        // Работа появилась до выключения — есть что отменять.
        store.setEnabled(true)
        scheduler.sync()

        store.setEnabled(false)
        worker().doWork()

        val active = context.getSystemService(NotificationManager::class.java)
            .activeNotifications
        assertTrue(
            "уведомления быть не должно, было: ${active.map { it.id }}",
            active.isEmpty(),
        )
        val infos = workManager.getWorkInfosForUniqueWork(ReminderScheduler.WORK_NAME).get()
        assertTrue(
            "цепочка должна завершиться: работа отсутствует или отменена, было: " +
                infos.map { it.state },
            infos.isEmpty() || infos.all { it.state == WorkInfo.State.CANCELLED },
        )
    }
}
