package com.expeysi.app.data.importer

import androidx.room.withTransaction
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.AppDatabase
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.db.TransactionEntity
import javax.inject.Inject

/**
 * Шов импортёра: запись импортированных строк в Room. Строки приходят
 * с dirty = true — отправку выполняет рабочий цикл SyncEngine.
 */
interface ImportWriter {

    /** Пишет все импортированные строки одной Room-транзакцией. */
    suspend fun write(
        balances: List<BalanceEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
    )
}

/** Room-реализация: одна транзакция на весь импорт. */
class RoomImportWriter @Inject constructor(
    private val database: AppDatabase,
) : ImportWriter {

    override suspend fun write(
        balances: List<BalanceEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
    ) {
        database.withTransaction {
            database.balanceDao().upsertAll(balances)
            database.accountDao().upsertAll(accounts)
            database.categoryDao().upsertAll(categories)
            database.transactionDao().upsertAll(transactions)
        }
    }
}
