package com.expeysi.app.data

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Последние введённые курсы перевода по парам валют «откуда → куда».
 * Хранится локально, из сети не берётся и не синхронизируется: курс каждый
 * пользователь вводит сам, подставляется только как черновик.
 */
@Singleton
class TransferRateStore @Inject constructor(
    private val prefs: SharedPreferences,
) {
    fun rate(fromCode: String, toCode: String): String? {
        if (fromCode == toCode) return null
        return prefs.getString(key(fromCode, toCode), null)
    }

    fun saveRate(fromCode: String, toCode: String, rate: String) {
        if (fromCode == toCode || rate.isEmpty()) return
        prefs.edit().putString(key(fromCode, toCode), rate).apply()
    }

    private fun key(fromCode: String, toCode: String) = "last_transfer_rate:$fromCode:$toCode"
}
