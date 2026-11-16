package com.expeysi.app.data.reminder

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.expeysi.app.data.ReminderStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Состояние уникальной работы WORK_NAME после sync() — через тестовый WorkManager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderSchedulerWorkTest {

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

    private fun workStates(): List<WorkInfo.State> = workManager
        .getWorkInfosForUniqueWork(ReminderScheduler.WORK_NAME)
        .get()
        .map { it.state }

    @Test
    fun `sync при включенном ставит ровно одну работу и KEEP не дублирует`() {
        store.setEnabled(true)
        scheduler.sync()
        assertEquals(listOf(WorkInfo.State.ENQUEUED), workStates())

        scheduler.sync(ExistingWorkPolicy.KEEP)
        assertEquals(listOf(WorkInfo.State.ENQUEUED), workStates())
    }

    @Test
    fun `sync при выключенном отменяет запланированную работу`() {
        store.setEnabled(true)
        scheduler.sync()
        assertEquals(listOf(WorkInfo.State.ENQUEUED), workStates())

        store.setEnabled(false)
        scheduler.sync()
        val states = workStates()
        assertTrue(
            "ожидали CANCELLED или отсутствие работы, было: $states",
            states.isEmpty() || states.all { it == WorkInfo.State.CANCELLED },
        )
    }
}
