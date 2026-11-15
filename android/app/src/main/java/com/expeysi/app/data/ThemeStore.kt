package com.expeysi.app.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Режим темы интерфейса: по системе, всегда светлая или всегда тёмная. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** Выбранный режим темы. Хранится локально, не синхронизируется. */
@Singleton
class ThemeStore @Inject constructor(
    private val prefs: SharedPreferences,
) {
    private val _mode = MutableStateFlow(prefs.restoreMode())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun set(mode: ThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
        _mode.value = mode
    }

    private fun SharedPreferences.restoreMode(): ThemeMode {
        val stored = getString(KEY, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
    }

    private companion object {
        const val KEY = "theme_mode"
    }
}
