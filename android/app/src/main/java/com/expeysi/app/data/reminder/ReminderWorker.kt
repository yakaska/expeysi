package com.expeysi.app.data.reminder

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.expeysi.app.data.ReminderStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Показывает уведомление-напоминание (только если напоминание всё ещё включено —
 * страховка от гонки с тоглером) и перезапланирует задачу на следующий выбранный
 * момент, так что цепочка не обрывается.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val store: ReminderStore,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (store.state.value.enabled) {
            ReminderNotifications.ensureChannel(context)
            val notification = ReminderNotifications.build(context)
            context.getSystemService(NotificationManager::class.java)
                .notify(ReminderNotifications.NOTIFICATION_ID, notification)
        }
        scheduler.sync()
        return Result.success()
    }
}
