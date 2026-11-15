package com.expeysi.app.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Состояние напоминания о внесении расходов за сегодня: включённость и время. */
data class ReminderState(
    val enabled: Boolean = false,
    val hour: Int = 21,
    val minute: Int = 0,
)

/**
 * Включённость напоминания и выбранное время. Хранится локально, не синхронизируется.
 * Время сохраняется и при выключенных уведомлениях: тоглер его никогда не сбрасывает.
 */
@Singleton
class ReminderStore @Inject constructor(
    private val prefs: SharedPreferences,
) {
    private val _state = MutableStateFlow(prefs.restore())
    val state: StateFlow<ReminderState> = _state.asStateFlow()

    /** Включает или выключает напоминание, время не трогает. */
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _state.value = _state.value.copy(enabled = enabled)
    }

    /** Выбирает время напоминания, включённость не трогает. */
    fun setTime(hour: Int, minute: Int) {
        prefs.edit().putInt(KEY_HOUR, hour).putInt(KEY_MINUTE, minute).apply()
        _state.value = _state.value.copy(hour = hour, minute = minute)
    }

    private fun SharedPreferences.restore(): ReminderState = ReminderState(
        enabled = getBoolean(KEY_ENABLED, false),
        hour = getInt(KEY_HOUR, DEFAULT.hour).coerceIn(0, 23),
        minute = getInt(KEY_MINUTE, DEFAULT.minute).coerceIn(0, 59),
    )

    private companion object {
        val DEFAULT = ReminderState()
        const val KEY_ENABLED = "reminder_enabled"
        const val KEY_HOUR = "reminder_hour"
        const val KEY_MINUTE = "reminder_minute"
    }
}
