package com.expeysi.app.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище выбранной семьи. Интерфейс существует ради тестируемости
 * потребителей; классы зависят от него, а не от SharedPreferences.
 */
interface FamilyStore {

    /** null — семья не выбрана: не создана, не вступили или вышел. */
    val familyId: StateFlow<String?>

    fun select(id: String)

    fun clear()
}

/**
 * Реализация поверх SharedPreferences. familyId — код приглашения и
 * одновременно id семьи в удалённом хранилище; ключ прежний, «family_id»,
 * чтобы сохранённое значение пережило обновление приложения.
 */
@Singleton
class SharedPrefsFamilyStore @Inject constructor(
    private val prefs: SharedPreferences,
) : FamilyStore {
    private val _familyId = MutableStateFlow(prefs.getString(KEY, null))
    override val familyId: StateFlow<String?> = _familyId.asStateFlow()

    override fun select(id: String) {
        prefs.edit().putString(KEY, id).apply()
        _familyId.value = id
    }

    override fun clear() {
        prefs.edit().remove(KEY).apply()
        _familyId.value = null
    }

    private companion object {
        const val KEY = "family_id"
    }
}
