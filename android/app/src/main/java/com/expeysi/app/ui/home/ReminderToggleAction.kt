package com.expeysi.app.ui.home

import android.os.Build

/**
 * Решение при включении тоглера напоминания. Чистая функция без Android-контекста:
 * зависимости подаются параметрами, поэтому покрывается JVM-тестами.
 */
internal enum class ReminderToggleAction {
    /** Разрешение и уведомления в порядке — включаем. */
    ENABLE,

    /** Разрешения POST_NOTIFICATIONS нет — сначала запрос. */
    REQUEST_PERMISSION,

    /** Разрешение есть, но уведомления отключены в настройках системы. */
    BLOCKED,
}

internal fun reminderToggleAction(
    sdkInt: Int,
    hasPostNotificationPermission: Boolean,
    notificationsEnabledInSystem: Boolean,
): ReminderToggleAction = when {
    // Без разрешения уведомления не работают в принципе — сначала запрос.
    sdkInt >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationPermission ->
        ReminderToggleAction.REQUEST_PERMISSION
    // Разрешение есть, но пользователь выключил уведомления в настройках системы.
    !notificationsEnabledInSystem -> ReminderToggleAction.BLOCKED
    else -> ReminderToggleAction.ENABLE
}
