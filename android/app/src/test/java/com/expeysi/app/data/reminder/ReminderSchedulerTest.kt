package com.expeysi.app.data.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderSchedulerTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    /** Момент в заданной зоне — сокращение для тестов. */
    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zoneId: ZoneId = zone,
    ): ZonedDateTime = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zoneId)

    @Test
    fun `выбранное время позже сегодня - напоминаем сегодня`() {
        val now = at(2026, 11, 15, 10, 0)
        val next = ReminderScheduler.nextOccurrence(21, 30, now, zone)
        assertEquals(at(2026, 11, 15, 21, 30), next)
    }

    @Test
    fun `выбранное время уже прошло - переносим на завтра`() {
        val now = at(2026, 11, 15, 10, 0)
        val next = ReminderScheduler.nextOccurrence(9, 0, now, zone)
        assertEquals(at(2026, 11, 16, 9, 0), next)
    }

    @Test
    fun `выбранное время совпадает с текущим моментом - переносим на завтра`() {
        val now = at(2026, 11, 15, 10, 0)
        val next = ReminderScheduler.nextOccurrence(10, 0, now, zone)
        assertEquals(at(2026, 11, 16, 10, 0), next)
    }

    @Test
    fun `перенос пересекает конец месяца`() {
        val now = at(2026, 11, 30, 22, 0)
        val next = ReminderScheduler.nextOccurrence(9, 0, now, zone)
        assertEquals(at(2026, 12, 1, 9, 0), next)
    }

    @Test
    fun `перенос пересекает конец года`() {
        val now = at(2026, 12, 31, 22, 0)
        val next = ReminderScheduler.nextOccurrence(9, 0, now, zone)
        assertEquals(at(2027, 1, 1, 9, 0), next)
    }

    @Test
    fun `зона параметром отличается от зоны now - момент остаётся в будущем`() {
        // В UTC 10:00, в Берлине уже 11:00; цель 12:30 по Берлину — всё ещё сегодня.
        val now = at(2026, 11, 16, 10, 0, zoneId = ZoneId.of("UTC"))
        val next = ReminderScheduler.nextOccurrence(12, 30, now, zone)
        assertEquals(at(2026, 11, 16, 12, 30), next)
        assertEquals(Duration.ofMinutes(90), Duration.between(now, next))
    }

    @Test
    fun `DST вперёд - время в пропуске сдвигается на час и задержка реальная`() {
        // Берлин, 29.03.2026: в 02:00 CET (+01) часы прыгают на 03:00 CEST (+02),
        // стенное время 02:30 не существует.
        val now = at(2026, 3, 29, 1, 50) // +01, UTC 00:50
        val next = ReminderScheduler.nextOccurrence(2, 30, now, zone)
        // atZone сдвигает пропуск вперёд: 03:30 CEST (+02, UTC 01:30).
        assertEquals(at(2026, 3, 29, 3, 30), next)
        // Реальных минут до срабатывания — 40, хотя по календарю разница 1 ч 40 мин.
        assertEquals(Duration.ofMinutes(40), Duration.between(now, next))
    }

    @Test
    fun `DST вперёд на кануне - длительность соответствует реальному времени`() {
        val now = at(2026, 3, 28, 12, 0) // CET (+01), UTC 11:00
        val next = ReminderScheduler.nextOccurrence(2, 30, now, zone)
        assertEquals(at(2026, 3, 29, 3, 30), next)
        // UTC: 01:30 - 11:00 = 14 ч 30 м реального времени; календарно было бы 15 ч 30 м.
        assertEquals(Duration.ofHours(14).plusMinutes(30), Duration.between(now, next))
    }

    @Test
    fun `DST назад - дубль времени берётся более ранним и задержка реальная`() {
        // Берлин, 25.10.2026: в 03:00 CEST (+02) часы возвращаются на 02:00 CET (+01),
        // стенное время 02:30 наступает дважды.
        val now = at(2026, 10, 24, 12, 0) // CEST (+02), UTC 10:00
        val next = ReminderScheduler.nextOccurrence(2, 30, now, zone)
        // atZone берёт первый дубль: 02:30 (+02), UTC 00:30.
        assertEquals(at(2026, 10, 25, 2, 30), next)
        assertEquals(java.time.ZoneOffset.ofHours(2), next.offset)
        // Реальных минут 14 ч 30 м; второй дубль (+01) дал бы 15 ч 30 м.
        assertEquals(Duration.ofHours(14).plusMinutes(30), Duration.between(now, next))
    }

    @Test
    fun `перенос внутри DST-дня сохраняет локальное время на завтра`() {
        // Цель 02:30 29.03 в пропуске сдвинулась на 03:30 — завтрашний перенос
        // всё равно строится как 02:30 локального времени следующего дня.
        val now = at(2026, 3, 29, 3, 40) // уже CEST (+02), позже сдвинутого кандидата
        val next = ReminderScheduler.nextOccurrence(2, 30, now, zone)
        assertEquals(at(2026, 3, 30, 2, 30), next)
        assertEquals(LocalDate.of(2026, 3, 30), next.toLocalDate())
    }
}
