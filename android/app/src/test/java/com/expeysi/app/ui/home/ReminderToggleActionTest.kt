package com.expeysi.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderToggleActionTest {

    @Test
    fun `разрешения нет - сначала запрос`() {
        val action = reminderToggleAction(
            sdkInt = 33,
            hasPostNotificationPermission = false,
            notificationsEnabledInSystem = true,
        )
        assertEquals(ReminderToggleAction.REQUEST_PERMISSION, action)
    }

    @Test
    fun `разрешение есть но уведомления отключены в системе - не включаем`() {
        val action = reminderToggleAction(
            sdkInt = 33,
            hasPostNotificationPermission = true,
            notificationsEnabledInSystem = false,
        )
        assertEquals(ReminderToggleAction.BLOCKED, action)
    }

    @Test
    fun `всё включено - включаем напоминание`() {
        val action = reminderToggleAction(
            sdkInt = 33,
            hasPostNotificationPermission = true,
            notificationsEnabledInSystem = true,
        )
        assertEquals(ReminderToggleAction.ENABLE, action)
    }

    @Test
    fun `до android 13 разрешения не спрашиваем - смотрим только на уведомления`() {
        assertEquals(
            ReminderToggleAction.ENABLE,
            reminderToggleAction(
                sdkInt = 28,
                hasPostNotificationPermission = true,
                notificationsEnabledInSystem = true,
            ),
        )
        assertEquals(
            ReminderToggleAction.BLOCKED,
            reminderToggleAction(
                sdkInt = 28,
                hasPostNotificationPermission = true,
                notificationsEnabledInSystem = false,
            ),
        )
    }
}
