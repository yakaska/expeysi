package com.expeysi.app.data.importer

import android.util.Log
import androidx.room.withTransaction
import com.expeysi.app.data.SelectedBalanceStore
import com.expeysi.app.data.csv.CsvFormat
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
        val now = nowMillis()
        val (openingByKey, operations) = splitOpeningRows(imported)
        // Импорт аддитивен: балансы и счета всегда создаются новыми, а текущие
        // не изменяются. Активные имена нужны лишь для нумерации копий балансов.
        val activeBalanceNames = database.balanceDao().observeAll().first()
            .filter { !it.deleted }
            .map { it.name }
            .toSet()
        val balanceByKey = resolveBalances(
            items = imported,
            takenNames = activeBalanceNames,
            nowMillis = now,
        )
        val accountByKey = resolveAccounts(
            items = imported,
            balanceByKey = balanceByKey,
            openingByKey = openingByKey,
            nowMillis = now,
        )
        val newCategories = mutableListOf<CategoryEntity>()
        val recoloredCategories = mutableMapOf<String, CategoryEntity>()
        val categoryByName = database.categoryDao().observeAll().first()
            .filter { !it.deleted }
            .associateBy {
                CategoryKey(normalizeBudgetCategoryName(it.name), TxType.fromWire(it.type))
            }.toMutableMap()

        val transactions = operations.map { item ->
            val categoryKey = CategoryKey(normalizeBudgetCategoryName(item.categoryName), item.type)
            val category = categoryByName.getOrPut(categoryKey) {
                CategoryEntity(
                    id = UUID.randomUUID().toString(),
                    name = item.categoryName,
                    iconKey = item.categoryIconKey ?: IconCatalog.DEFAULT_KEY,
                    colorKey = item.categoryColorKey ?: CategoryPalette.DEFAULT_KEY,
                    type = item.type.wire,
                    updatedAt = now,
                ).also(newCategories::add)
            }
            // Явные иконка и цвет в строке — признак файла экспорта Expeysi:
            // его round-trip обязан вернуть оформление даже существующим категориям.
            restyledCategory(category, item, now)?.let { updated ->
                categoryByName[categoryKey] = updated
                recoloredCategories[updated.id] = updated
            }
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                type = item.type.wire,
                amountMinor = item.amountMinor,
                accountId = accountByKey.getValue(item.accountKey()).id,
                categoryId = category.id,
                date = item.date,
                note = item.note,
                updatedAt = now,
            )
        }

        database.withTransaction {
            database.balanceDao().upsertAll(balanceByKey.values.toList())
            database.accountDao().upsertAll(accountByKey.values.toList())
            database.categoryDao().upsertAll(newCategories + recoloredCategories.values)
            database.transactionDao().upsertAll(transactions)
        }
        Log.d(TAG, "import committed transactions=${transactions.size}")

        syncEngine.uploadImportedData(
            balanceByKey.values.toList(),
            accountByKey.values.toList(),
            newCategories + recoloredCategories.values,
            transactions,
        )
        selectedBalanceStore.select(balanceByKey.values.first().id)
        return BudgetImportResult(transactions.size, balanceByKey.values.first().id)
    }

    private fun nowMillis(): Long = System.currentTimeMillis()

    private data class CategoryKey(val name: String, val type: TxType)

    private companion object {
        const val TAG = "ExpeysiImport"
    }
}

/**
 * Перекрашивает существующую категорию явными иконкой и цветом из строки файла.
 * Непустые значения есть только у файлов экспорта Expeysi: их round-trip обязан
 * вернуть оформление, в том числе сущностям, уже пришедшим из синхронизации.
 * Файлы внешнего «Бюджета» таких колонок не пишут — оформление не трогается.
 * null — менять нечего.
 */
internal fun restyledCategory(
    existing: CategoryEntity,
    item: BudgetTransaction,
    nowMillis: Long,
): CategoryEntity? {
    val icon = item.categoryIconKey
    val color = item.categoryColorKey
    val changed = (icon != null && existing.iconKey != icon) ||
        (color != null && existing.colorKey != color)
    if (!changed) return null
    return existing.copy(
        iconKey = icon ?: existing.iconKey,
        colorKey = color ?: existing.colorKey,
        updatedAt = nowMillis,
    )
}

internal fun normalizeBudgetCategoryName(name: String): String = name.trim().lowercase().replace('ё', 'е')

/**
 * Делит разобранные строки на служебные начальные остатки и обычные операции.
 * Служебная строка — с зарезервированной категорией [CsvFormat.OPENING_ROW_CATEGORY]:
 * тип задаёт знак остатка (доход — положительный), сумма хранится без знака.
 * Остаток живёт на счете, поэтому ключ — [AccountKey], а не пара имя+валюта.
 * При нескольких строках на один счет действует последняя.
 */
internal fun splitOpeningRows(items: List<BudgetTransaction>): Pair<Map<AccountKey, Long>, List<BudgetTransaction>> {
    val opening = mutableMapOf<AccountKey, Long>()
    val operations = mutableListOf<BudgetTransaction>()
    items.forEach { item ->
        if (CsvFormat.isOpeningRowCategory(item.categoryName)) {
            val sign = if (item.type == TxType.INCOME) 1L else -1L
            opening[item.accountKey()] = sign * item.amountMinor
        } else {
            operations += item
        }
    }
    return opening to operations
}

/** Ключ баланса CSV-файла: без названия — автосозданный «Бюджет (валюта)». */
internal data class BalanceKey(val name: String?, val currency: String)

/** Ключ счета CSV-файла: в одном балансе имя счета уникально. */
internal data class AccountKey(val name: String, val currency: String, val balanceName: String?)

internal fun BudgetTransaction.balanceKey(): BalanceKey = BalanceKey(name = balanceName, currency = currencyCode)

internal fun BudgetTransaction.accountKey(): AccountKey = AccountKey(
    name = accountName,
    currency = currencyCode,
    balanceName = balanceName,
)

/** Префикс имени загруженной копии баланса; строка данных, как «Бюджет (валюта)». */
internal const val IMPORT_BALANCE_PREFIX = "импорт "

/**
 * Имя загруженной копии баланса: префикс [IMPORT_BALANCE_PREFIX] отделяет её
 * от текущих балансов пользователя. Если имя «импорт Base» ещё свободно, копия
 * называется без номера; когда занято — номер в скобках, тупо последовательно
 * за максимальным занятым: «импорт Dong», «импорт Dong (2)», «импорт Dong (3)»…
 * Если безномерная копия удалена, имя освобождается и снова берётся без номера.
 * Счёт идёт только по активным балансам: удалённые копии нумерацию не учитывают.
 * [takenNames] задаёт занятые имена — активные имена из БД плюс уже присвоенные
 * в этой же загрузке.
 */
internal fun importedBalanceName(baseName: String, takenNames: Set<String>): String {
    val prefixed = "$IMPORT_BALANCE_PREFIX$baseName"
    if (prefixed !in takenNames) return prefixed
    val nextNumber = takenNames.maxOf { existing ->
        if (existing == prefixed) {
            1
        } else {
            val number = existing.removePrefix("$prefixed (").removeSuffix(")").toIntOrNull()
            if (number != null && existing == "$prefixed ($number)") number else 0
        }
    } + 1
    return "$prefixed ($nextNumber)"
}

/**
 * Балансы файла всегда создаются новыми: импорт аддитивен и не переиспользует
 * существующие балансы — данные файла ложатся отдельной копией рядом с текущими,
 * неудачный импорт убирается целиком удалением нового баланса. Имя копии
 * получает префикс и нумерацию повторов — см. [importedBalanceName]. Баланс без
 * названия — автосозданный «Бюджет (валюта)», как у файлов внешнего «Бюджета».
 * Оформление берется из первой строки ключа.
 */
internal fun resolveBalances(
    items: List<BudgetTransaction>,
    takenNames: Set<String>,
    nowMillis: Long,
): Map<BalanceKey, BalanceEntity> {
    val result = linkedMapOf<BalanceKey, BalanceEntity>()
    val assignedNames = mutableSetOf<String>()
    items.forEach { item ->
        val key = item.balanceKey()
        if (key in result) return@forEach
        val sample = items.first { it.balanceKey() == key }
        val name = importedBalanceName(
            baseName = key.name ?: "Бюджет (${key.currency})",
            takenNames = takenNames + assignedNames,
        )
        assignedNames += name
        result[key] = BalanceEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            currencyCode = key.currency,
            iconKey = sample.balanceIconKey ?: "",
            colorKey = sample.balanceColorKey ?: "default",
            updatedAt = nowMillis,
        )
    }
    return result
}

/**
 * Счета файла всегда создаются новыми под свежими балансами: импорт аддитивен
 * и не переиспользует существующие счета. Валюта резервируется из колонки
 * файла, а не из БД. Один счёт на тройку (имя, валюта, баланс): одноимённые
 * счета разных балансов не сливаются. Начальный остаток берётся из служебной
 * строки «Начальный остаток» (последняя побеждает), оформление — из первой
 * строки ключа.
 */
internal fun resolveAccounts(
    items: List<BudgetTransaction>,
    balanceByKey: Map<BalanceKey, BalanceEntity>,
    openingByKey: Map<AccountKey, Long>,
    nowMillis: Long,
): Map<AccountKey, AccountEntity> {
    val result = linkedMapOf<AccountKey, AccountEntity>()
    items.forEach { item ->
        val key = item.accountKey()
        if (key in result) return@forEach
        val sample = items.first { it.accountKey() == key }
        result[key] = AccountEntity(
            id = UUID.randomUUID().toString(),
            name = key.name,
            iconKey = sample.accountIconKey ?: "payments",
            colorKey = sample.accountColorKey ?: "green",
            openingBalanceMinor = openingByKey[key] ?: 0,
            currencyCode = key.currency,
            balanceId = balanceByKey.getValue(BalanceKey(key.balanceName, key.currency)).id,
            updatedAt = nowMillis,
        )
    }
    return result
}
