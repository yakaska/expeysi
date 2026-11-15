package com.expeysi.app.ui.theme

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import com.expeysi.app.data.ThemeMode
import com.expeysi.app.data.ThemeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Выбор режима темы; применение идёт через appcompat, хранение — в ThemeStore. */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val store: ThemeStore,
) : ViewModel() {
    val mode: StateFlow<ThemeMode> = store.mode

    fun setMode(mode: ThemeMode) {
        store.set(mode)
        AppCompatDelegate.setDefaultNightMode(mode.nightMode())
    }
}

/**
 * Режим темы → константы appcompat: FOLLOW_SYSTEM/NO/YES.
 * Вызов нужен и при выборе в drawer, и при старте процесса (ExpeysiApp), потому
 * что setDefaultNightMode сам выбор не запоминает.
 */
internal fun ThemeMode.nightMode(): Int = when (this) {
    ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
    ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
}
