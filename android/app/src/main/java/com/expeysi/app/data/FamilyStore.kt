package com.expeysi.app.data

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class FamilyStore @Inject constructor(
    private val prefs: SharedPreferences,
) {
    private val _familyId = MutableStateFlow(prefs.getString(KEY, null))
    val familyId: StateFlow<String?> = _familyId.asStateFlow()

    fun select(id: String) {
        prefs.edit().putString(KEY, id).apply()
        _familyId.value = id
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
        _familyId.value = null
    }

    private companion object {
        const val KEY = "family_id"
    }
}
