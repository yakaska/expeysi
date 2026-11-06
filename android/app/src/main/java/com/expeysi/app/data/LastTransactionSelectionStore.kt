package com.expeysi.app.data

import android.content.SharedPreferences
import com.expeysi.app.domain.TxType
import javax.inject.Inject
import javax.inject.Singleton

/** Последние счёт и категории пользователя. Хранятся локально, не синхронизируются. */
@Singleton
class LastTransactionSelectionStore @Inject constructor(
    private val prefs: SharedPreferences,
) {
    fun accountId(userId: String): String? = prefs.getString(accountKey(userId), null)

    fun categoryId(userId: String, type: TxType): String? =
        prefs.getString(categoryKey(userId, type), null)

    fun selectAccount(userId: String, id: String) {
        prefs.edit().putString(accountKey(userId), id).apply()
    }

    fun selectCategory(userId: String, type: TxType, id: String) {
        prefs.edit().putString(categoryKey(userId, type), id).apply()
    }

    private fun accountKey(userId: String) = "last_transaction_account:$userId"

    private fun categoryKey(userId: String, type: TxType) =
        "last_transaction_category:$userId:${type.wire}"
}
