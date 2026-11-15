package com.expeysi.app.data.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.expeysi.app.data.ReminderStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Планировщик ежедневного напоминания: одноразовая задача WorkManager с задержкой до
 * ближайшего выбранного момента; после выполнения воркер перезапланирует её на следующий
 * день — цепочка переживает перезапуск устройства и закрытие приложения, потерянное
 * напоминание (например, после force stop) досылается при следующем запуске.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ReminderStore,
) {
    /**
     * Приводит запланированную задачу в соответствие с текущим [ReminderStore] состоянием.
     * [policy] — REPLACE для явных действий пользователя (тоглер, время), KEEP при старте
     * процесса, чтобы не сбивать цепочку, только восстановить её после force stop.
     */
    fun sync(policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val workManager = WorkManager.getInstance(context)
        val state = store.state.value
        if (!state.enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request: OneTimeWorkRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(
                Duration.between(LocalDateTime.now(), nextOccurrence(state.hour, state.minute)),
            )
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    companion object {
        /** Имя уникальной запланированной работы напоминания. */
        const val WORK_NAME = "expense_reminder"

        /** Ближайший момент строго после [now] с локальным временем [hour]:[minute]. */
        fun nextOccurrence(
            hour: Int,
            minute: Int,
            now: LocalDateTime = LocalDateTime.now(),
        ): LocalDateTime {
            val candidate = now.toLocalDate().atTime(hour, minute)
            return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
        }
    }
}
