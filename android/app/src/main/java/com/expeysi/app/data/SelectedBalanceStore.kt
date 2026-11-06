package com.expeysi.app.data

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Выбранный на главном экране баланс. Хранится локально, не синхронизируется. */
@Singleton
class SelectedBalanceStore @Inject constructor(
    private val prefs: SharedPreferences,
) {
    private val _selectedId = MutableStateFlow(prefs.getString(KEY, null))
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    fun select(id: String) {
        prefs.edit().putString(KEY, id).apply()
        _selectedId.value = id
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
        _selectedId.value = null
    }

    private companion object {
        const val KEY = "selected_balance_id"
    }
}
