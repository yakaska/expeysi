package com.expeysi.app.data.importer

import androidx.room.withTransaction
import com.expeysi.app.data.SelectedBalanceStore
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.AppDatabase
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.data.sync.SyncEngine
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.icons.CategoryPalette
import com.expeysi.app.ui.icons.IconCatalog
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

data class BudgetImportResult(
    val transactionCount: Int,
    val balanceId: String,
)

class BudgetImportService @Inject constructor(
    private val database: AppDatabase,
    private val parser: BudgetCsvParser,
    private val syncEngine: SyncEngine,
    private val selectedBalanceStore: SelectedBalanceStore,
) {
    suspend fun import(input: InputStream): BudgetImportResult {
        val imported = parser.parse(input)
        val now = System.currentTimeMillis()
        val balances = imported.map { it.currencyCode }.distinct().map { currency ->
            BalanceEntity(
                id = UUID.randomUUID().toString(),
                name = "Бюджет ($currency)",
                currencyCode = currency,
                updatedAt = now,
            )
        }
        val balanceByCurrency = balances.associateBy { it.currencyCode }

        val accounts = imported.map { it.accountName to it.currencyCode }.distinct().map { (name, currency) ->
            AccountEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                iconKey = "payments",
                colorKey = "green",
                openingBalanceMinor = 0,
                currencyCode = currency,
                balanceId = balanceByCurrency.getValue(currency).id,
                updatedAt = now,
            )
        }
        val accountBySource = accounts.associateBy { it.name to it.currencyCode }

        val existingCategories = database.categoryDao().observeAll().first().filter { !it.deleted }
        val categoryByName = existingCategories.associateBy {
            CategoryKey(normalizeBudgetCategoryName(it.name), TxType.fromWire(it.type))
        }.toMutableMap()
        val newCategories = mutableListOf<CategoryEntity>()

        val transactions = imported.map { item ->
            val key = CategoryKey(normalizeBudgetCategoryName(item.categoryName), item.type)
            val category = categoryByName.getOrPut(key) {
                CategoryEntity(
                    id = UUID.randomUUID().toString(),
                    name = item.categoryName,
                    iconKey = IconCatalog.DEFAULT_KEY,
                    colorKey = CategoryPalette.DEFAULT_KEY,
                    type = item.type.wire,
                    updatedAt = now,
                ).also(newCategories::add)
            }
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                type = item.type.wire,
                amountMinor = item.amountMinor,
                accountId = accountBySource.getValue(item.accountName to item.currencyCode).id,
                categoryId = category.id,
                date = item.date,
                note = item.note,
                updatedAt = now,
            )
        }

        database.withTransaction {
            database.balanceDao().upsertAll(balances)
            database.accountDao().upsertAll(accounts)
            database.categoryDao().upsertAll(newCategories)
            database.transactionDao().upsertAll(transactions)
        }

        syncEngine.uploadImportedData(balances, accounts, newCategories, transactions)
        selectedBalanceStore.select(balances.first().id)
        return BudgetImportResult(transactions.size, balances.first().id)
    }

    private data class CategoryKey(val name: String, val type: TxType)
}

internal fun normalizeBudgetCategoryName(name: String): String = name.trim().lowercase().replace('ё', 'е')
