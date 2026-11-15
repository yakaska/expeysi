package com.expeysi.app.data.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.expeysi.app.R

/** Канал и сборка уведомления-напоминания о внесении расходов за сегодня. */
internal object ReminderNotifications {
    /** Технический идентификатор канала, пользователь не видит его. */
    const val CHANNEL_ID = "expense_reminder"

    const val NOTIFICATION_ID = 1

    /** Создаёт канал, если его ещё нет; вызов можно повторять перед каждым показом. */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        channel.description = context.getString(R.string.reminder_channel_description)
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /** Уведомление: тап по нему просто открывает приложение. */
    fun build(context: Context): Notification {
        val contentIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.let { launchIntent ->
                PendingIntent.getActivity(
                    context,
                    0,
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder_notification)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(context.getString(R.string.reminder_notification_text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }
}
