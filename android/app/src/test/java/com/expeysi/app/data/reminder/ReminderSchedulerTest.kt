package com.expeysi.app.data.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReminderSchedulerTest {

    @Test
    fun `выбранное время позже сегодня - напоминаем сегодня`() {
        val now = LocalDateTime.of(2026, 11, 15, 10, 0)
        val next = ReminderScheduler.nextOccurrence(21, 30, now)
        assertEquals(LocalDateTime.of(2026, 11, 15, 21, 30), next)
    }

    @Test
    fun `выбранное время уже прошло - переносим на завтра`() {
        val now = LocalDateTime.of(2026, 11, 15, 10, 0)
        val next = ReminderScheduler.nextOccurrence(9, 0, now)
        assertEquals(LocalDateTime.of(2026, 11, 16, 9, 0), next)
    }

    @Test
    fun `выбранное время совпадает с текущим моментом - переносим на завтра`() {
        val now = LocalDateTime.of(2026, 11, 15, 10, 0)
        val next = ReminderScheduler.nextOccurrence(10, 0, now)
        assertEquals(LocalDateTime.of(2026, 11, 16, 10, 0), next)
    }

    @Test
    fun `перенос пересекает конец месяца`() {
        val now = LocalDateTime.of(2026, 11, 30, 22, 0)
        val next = ReminderScheduler.nextOccurrence(9, 0, now)
        assertEquals(LocalDateTime.of(2026, 12, 1, 9, 0), next)
    }

    @Test
    fun `перенос пересекает конец года`() {
        val now = LocalDateTime.of(2026, 12, 31, 22, 0)
        val next = ReminderScheduler.nextOccurrence(9, 0, now)
        assertEquals(LocalDateTime.of(2027, 1, 1, 9, 0), next)
    }
}
