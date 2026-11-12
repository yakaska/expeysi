package com.expeysi.app.data.sync

import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.db.TransactionEntity

/**
 * Шов для импортёра: хранит импортированные строки в удалённом журнале
 * семьи. Строки перед вызовом обязаны быть уже сохранены в Room.
 */
interface ImportStore {

    /**
     * Массовая загрузка импортированных данных: батчи по 500 в транспорте,
     * подтверждения первых батчей ждут с таймаутом (инвариант 5) — импорт не
     * считается завершённым, пока данные не переданы транспорту либо таймаут
     * не перевёл их в офлайн-очередь. Без выбранной семьи ничего не отправляет.
     */
    suspend fun uploadImportedData(
        balances: List<BalanceEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
    )
}
