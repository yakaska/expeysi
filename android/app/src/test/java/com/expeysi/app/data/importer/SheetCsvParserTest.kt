package com.expeysi.app.data.importer

import com.expeysi.app.data.csv.CsvFormat.HEADER
import com.expeysi.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Импорт внешнего табличного формата: выгрузка внешнего приложения
 * (пример — test-import.csv). Строки в тестах скопированы из реального файла.
 */
class SheetCsvParserTest {

    private val parser = SheetCsvParser()

    /** Заголовок реального файла: 14 колонок, последние — служебные внешнего приложения. */
    private val sheetHeader =
        "ID;Дата;Время;Название счета;Владелец счета;Группа;Категория;Сумма;Валюта;" +
            "Комментарий;ID Категории;ЦВЕТ;ИКОНКА;Отметка времениЗапланированные\n"

    private fun row(line: String) = "$sheetHeader$line\n"

    /** Заголовок без столбца метки времени: старые выгрузки, работает эвристика момента. */
    private val fallbackHeader =
        "ID;Дата;Время;Название счета;Владелец счета;Группа;Категория;Сумма;Валюта;" +
            "Комментарий;ID Категории;ЦВЕТ;ИКОНКА\n"

    private fun fallback(line: String) = "$fallbackHeader$line\n"

    @Test
    fun `формат распознаётся по первой колонке ID`() {
        val sheet = sheetHeader + "1;2026-10-16;18:47;Наличные;Личное;Общее;Еда;-105000;VND;;6;71;300;1;0\n"
        assertEquals(true, SheetCsvParser.canParse(sheet))
        assertEquals(false, SheetCsvParser.canParse("$HEADER\n2026-01-02;03:04;Карта;Еда;-100;VND;\n"))
        assertEquals(false, SheetCsvParser.canParse(""))
    }

    @Test
    fun `научная нотация Excel читается в сотые`() {
        val transactions = parser.parseText(
            row("1;2026-10-16;18:47;Наличные;Личное;Общее;Жилье;-8e+06;VND;Квартира;8;97;600;1792151503172;0") +
                "2;2026-10-18;22:40;Наличные;Личное;Общее;Еда;-1.27533e+06;VND;Бар;6;71;300;1792385258936;0\n" +
                "3;2026-10-31;11:37;Наличные;Личное;Общее;Развлечения;-33333.3;VND;Такси;13;47;1301;1793507872511;0\n" +
                "4;2026-10-17;12:15;Наличные;Личное;Общее;Зарплата;1e+07;VND;;3;39;209;1792214131972;0\n",
        )

        assertEquals(listOf("Жилье", "Еда", "Развлечения", "Зарплата"), transactions.map { it.categoryName })
        // −8 000 000 дунги → −800 000 000 сотых; −1 275 330 → −127 533 000.
        assertEquals(800_000_000L, transactions.first().amountMinor)
        assertEquals(127_533_000L, transactions[1].amountMinor)
        // Дробный VND листа: 33 333,3 → 3 333 330 сотых.
        assertEquals(3_333_330L, transactions[2].amountMinor)
        // Положительная сумма — доход.
        assertEquals(TxType.INCOME, transactions.last().type)
        assertEquals(1_000_000_000L, transactions.last().amountMinor)
        assertEquals("VND", transactions.first().currencyCode)
        assertEquals("Квартира", transactions.first().note)
    }

    @Test
    fun `баланс берется из владельца счета`() {
        val transactions = parser.parseText(
            row("1;2026-11-16;15:11;Сперма 228;Общее;;Перевод;-1000;CNY;;2;10;210;1;0") +
                "2;2026-10-16;18:47;Наличные;Личное;Общее;Еда;-105000;VND;;6;71;300;2;0\n" +
                "3;2026-10-16;18:50;Карта;;Личное;Еда;-100;RUB;;6;71;300;3;0\n" +
                "4;2026-10-16;18:51;Наличные;;;Еда;-100;USD;;6;71;300;4;0\n",
        )

        // Владелец заполнен — баланс из него; пустой владелец — из группы;
        // оба пустых — null (в сервисе автосозданный «Бюджет (USD)»).
        assertEquals(listOf("Общее", "Личное", "Личное", null), transactions.map { it.balanceName })
        assertNull(transactions.last().balanceName)
    }

    @Test
    fun `коды цвета и иконки переводятся в ключи оформления`() {
        // Все коды реального файла (test-import.csv): категория, ЦВЕТ, ИКОНКА.
        val transactions = parser.parseText(
            row("1;2026-10-16;18:47;Наличные;Личное;Общее;Еда;-105000;VND;;6;71;300;1;0") +
                "2;2026-10-16;18:48;Наличные;Личное;Общее;Здоровье;-100;VND;;7;2;502;2;0\n" +
                "3;2026-10-16;18:49;Наличные;Личное;Общее;Косметика;-100;VND;;8;1;1004;3;0\n" +
                "4;2026-10-16;18:50;Наличные;Личное;Общее;Связь;-100;VND;;9;64;1100;4;0\n" +
                "5;2026-10-16;18:51;Наличные;Личное;Общее;Жилье;-8000000;VND;;10;97;600;5;0\n" +
                "6;2026-10-16;18:52;Наличные;Личное;Общее;Транспорт;-100;VND;;11;39;800;6;0\n" +
                "7;2026-10-16;18:53;Наличные;Личное;Общее;Одежда;-100;VND;;12;41;400;7;0\n" +
                "8;2026-10-16;18:54;Наличные;Личное;Общее;Развлечения;-100;VND;;13;47;1301;8;0\n" +
                "9;2026-10-17;12:15;Наличные;Личное;Общее;Зарплата;10000000;VND;;3;39;209;9;0\n",
        )

        assertEquals(
            listOf("green", "teal", "pink", "purple", "brown", "blue", "deep_purple", "orange", "blue"),
            transactions.map { it.categoryColorKey },
        )
        assertEquals(
            listOf("restaurant", "medical_services", "face", "smartphone", "home", "directions_bus", "checkroom", "sports_esports", "work"),
            transactions.map { it.categoryIconKey },
        )
    }

    @Test
    fun `коды оформления ноябрьского экспорта переводятся тоже`() {
        // Коды, которых не было в test-import.csv (строки файла ноября).
        val transactions = parser.parseText(
            row("1;2026-11-01;11:24;Наличные донги;Общее;Общее;Кафе/ресторан;-1.12639e+06;VND;Бар;14;56;301;1;0") +
                "2;2026-11-05;11:52;Вьетнамский счет;Общее;Общее;Кофе;-25000;VND;;19;99;302;2;0\n" +
                "3;2026-11-07;22:31;Вьетнамский счет;Общее;Общее;Вода;-71000;VND;;29;35;302;3;0\n" +
                "4;2026-11-05;17:28;Накопительный ПСБ;Общее;null;Выплаты процентов;4469.91;RUB;;22;35;203;4;0\n" +
                "5;2026-11-05;17:29;Карта ПСБ;Общее;null;Кешбек;587;RUB;;23;35;205;5;0\n" +
                "6;2026-11-07;18:43;Карта Сбер;Общее;null;Подарки;25000;RUB;;5;8;1600;6;0\n" +
                "7;2026-11-07;18:41;Озон карта;Общее;Общее;Отпуск;-69000;RUB;;38;76;903;7;0\n" +
                "8;2026-11-15;10:14;Карта Сбер;Общее;Общее;Иностранный язык;-6139;RUB;;43;87;903;8;0\n" +
                "9;2026-11-15;22:18;Озон карта;Общее;Общее;Учеба;-90000;RUB;;33;63;1601;9;0\n" +
                "10;2026-11-16;11:02;Вьетнамский счет;Общее;Общее;Уход;-62500;VND;;17;11;1004;10;0\n",
        )

        assertEquals(
            listOf("amber", "brown", "teal", "teal", "teal", "red", "blue", "purple", "deep_purple", "pink_light"),
            transactions.map { it.categoryColorKey },
        )
        assertEquals(
            listOf("restaurant", "local_cafe", "local_cafe", "attach_money", "attach_money", "card_giftcard", "star", "star", "work", "face"),
            transactions.map { it.categoryIconKey },
        )
    }

    @Test
    fun `коды оформления полной истории 2024 переводятся тоже`() {
        // Коды, которых не было в ноябрьском экспорте: строки файла 2024.csv.
        val transactions = parser.parseText(
            row("103;2026-02-10;04:00;Карта ВТБ;Общее;null;Перевод;1000;RUB;От лены;27;28;101;1770678076831;0") +
                "2;2025-12-27;13:00;Карта ПСБ;Общее;Общее;Валберис;-808;RUB;;15;17;1612;1766829678583;0\n" +
                "27;2026-01-13;22:12;Карта ПСБ;Общее;null;Возврат;3201.9;RUB;За билет на 9 января;25;16;209;1768331566353;0\n" +
                "693;2026-07-04;17:49;Карта ПСБ;Общее;Общее;Маникюр;-2000;RUB;;40;4;410;1783176579916;0\n" +
                "8;2025-12-29;16:00;Карта ПСБ;Общее;Общее;Новый год;-391;RUB;;16;1;1504;1767013246654;0\n" +
                "113;2026-02-15;19:29;Карта ПСБ;Общее;Общее;Переезд;-3293;RUB;Мешки, пупырка, скотч;28;22;802;1771173026771;0\n" +
                "293;2026-04-03;16:03;Карта ПСБ;Общее;null;Премия;100000;RUB;;4;63;204;1775221401647;0\n" +
                "731;2026-07-22;08:58;Карта ПСБ;Общее;Общее;Госпошлина;-3200;RUB;;41;28;203;1784692741996;0\n" +
                "16;2026-01-04;09:59;Карта ПСБ;Общее;Общее;Интернет;-355;RUB;;18;34;909;1767985540602;0\n" +
                "291;2026-04-03;13:56;Накопительный ПСБ;Общее;null;Проценты по счету;2921.44;RUB;;34;40;203;1775213806832;0\n" +
                "691;2026-07-04;09:52;Карта Тбанк;Общее;Общее;Помощь/благотворительност;-15000;RUB;;39;53;203;1783147976058;0\n" +
                "383;2026-04-30;14:36;Карта ПСБ;Общее;Общее;Доставки;-1127;RUB;Пицца;20;59;312;1777549027372;0\n" +
                "351;2026-04-18;15:06;Карта ПСБ;Общее;Общее;Для дома;-246;RUB;;36;65;601;1776514050531;0\n" +
                "52;2026-01-21;15:59;Карта ПСБ;Общее;Общее;Столовая;-190;RUB;;26;70;300;1769001762801;0\n" +
                "855;2026-08-24;10:19;Озон карта;Общее;Общее;Зимовка;-34887;RUB;Билет во вьетнам;44;83;905;1787556001016;0\n" +
                "217;2026-03-09;22:20;Карта ПСБ;Общее;Общее;Одежда;-2999;RUB;Джинсы;31;88;403;1773084081719;0\n" +
                "372;2026-04-26;14:18;Карта Тбанк;Общее;Общее;Психолог;-2520;RUB;;37;107;1504;1777202360365;0\n",
        )

        assertEquals(
            listOf(
                "blue", "purple", "green", "pink_light", "pink", "brown", "deep_purple", "blue",
                "deep_purple", "teal", "pink", "orange", "brown", "amber", "teal", "blue", "purple",
            ),
            transactions.map { it.categoryColorKey },
        )
        assertEquals(
            listOf(
                "swap_horiz", "shopping_cart", "work", "face", "card_giftcard", "home", "attach_money", "attach_money",
                "smartphone", "attach_money", "attach_money", "restaurant", "home", "restaurant", "directions_bus",
                "checkroom", "card_giftcard",
            ),
            transactions.map { it.categoryIconKey },
        )
    }

    @Test
    fun `группа null читается как пустое значение`() {
        val transactions = parser.parseText(
            row("1;2026-11-05;17:28;Накопительный ПСБ;;null;Выплаты процентов;4469.91;RUB;;22;35;203;1;0") +
                "2;2026-11-05;17:29;Карта ПСБ;;NULL;Выплаты процентов;100;RUB;;22;35;203;2;0\n",
        )

        // Владелец пуст, в группе литеральное «null» — значение считается
        // пустым: баланс автосозданный, а не «импорт null».
        assertEquals(listOf(null, null), transactions.map { it.balanceName })
    }

    @Test
    fun `служебные строки начального остатка листового формата дают остатки счетов`() {
        // Остаток пишется служебной строкой категории «Начальный остаток»,
        // как в экспорте Expeysi. Листы внешнего приложения в реальности
        // таких строк не содержат, но поддержку закрепляем тем же сервисным
        // механизмом splitOpeningRows → resolveAccounts.
        val transactions = parser.parseText(
            row("1;2026-10-16;18:47;Наличные;Личное;Личное;Начальный остаток;2e+06;VND;Открытие;0;0;0;1;0") +
                "2;2026-10-16;18:48;Карта;Личное;Личное;Начальный остаток;-150000.50;RUB;;0;0;0;2;0\n" +
                "3;2026-10-16;18:49;Карта;Личное;Личное;Начальный остаток;250000;RUB;Поправка;0;0;0;3;0\n" +
                "4;2026-10-17;09:00;Наличные;Личное;Личное;Еда;-1000;VND;;6;71;300;4;0\n",
        )

        // Отрицательный остаток — строка расхода: сервис вернет ей минус.
        assertEquals(TxType.EXPENSE, transactions[1].type)
        assertEquals(15_000_050L, transactions[1].amountMinor)

        val (openingByKey, operations) = splitOpeningRows(transactions)
        val accounts = resolveAccounts(
            items = transactions,
            balanceByKey = resolveBalances(transactions, takenNames = emptySet(), nowMillis = 77),
            openingByKey = openingByKey,
            nowMillis = 77,
        )

        // Служебные строки отделились в остатки, обычная операция осталась.
        assertEquals(listOf("Еда"), operations.map { it.categoryName })
        assertEquals(
            mapOf(
                AccountKey("Наличные", "VND", "Личное") to 200_000_000L,
                // При нескольких строках на счете действует последняя.
                AccountKey("Карта", "RUB", "Личное") to 25_000_000L,
            ),
            openingByKey,
        )
        assertEquals(200_000_000L, accounts.getValue(AccountKey("Наличные", "VND", "Личное")).openingBalanceMinor)
        assertEquals(25_000_000L, accounts.getValue(AccountKey("Карта", "RUB", "Личное")).openingBalanceMinor)
    }

    @Test
    fun `неизвестные нечисловые и пустые коды оформление не задают`() {
        val transactions = parser.parseText(
            row("1;2026-10-16;18:47;Наличные;Личное;Общее;Еда;-105000;VND;;6;999;300;1;0") +
                "2;2026-10-16;18:48;Наличные;Личное;Общее;Еда;-100;VND;;6;71;красный;2;0\n" +
                "3;2026-10-16;18:49;Наличные;Личное;Общее;Еда;-100;VND;;6;71;400.5;3;0\n" +
                "4;2026-10-16;18:50;Наличные;Личное;Общее;Еда;-100;VND;;6;;300;4;0\n",
        )

        assertEquals(listOf(null, "green", "green", null), transactions.map { it.categoryColorKey })
        assertEquals(listOf("restaurant", null, null, "restaurant"), transactions.map { it.categoryIconKey })
    }

    @Test
    fun `строки перевода дают иконку строки перевода`() {
        // Одиночные строки «Перевод» остаются операциями и создают категорию:
        // код цвета ног (10, 58) не читается, иконка 210 — «swap_horiz».
        val transactions = parser.parseText(
            row("1;2026-11-15;17:26;Наличные;Личное;;Перевод;-1000;VND;;2;10;210;1;0") +
                "2;2026-11-15;18:00;Наличные;Личное;;Перевод;1000;VND;;1;58;210;2;0\n",
        )

        assertEquals(listOf("swap_horiz", "swap_horiz"), transactions.map { it.categoryIconKey })
        assertEquals(listOf(null, null), transactions.map { it.categoryColorKey })
    }

    @Test
    fun `заголовок без колонок оформления читается без ключей`() {
        val transactions = parser.parseText(
            "ID;Дата;Время;Название счета;Владелец счета;Группа;Категория;Сумма;Валюта;Комментарий\n" +
                "1;2026-10-16;18:47;Наличные;Личное;Общее;Еда;-105000;VND;Ужин\n",
        )

        assertEquals(1, transactions.size)
        assertEquals("Ужин", transactions.single().note)
        assertNull(transactions.single().categoryIconKey)
        assertNull(transactions.single().categoryColorKey)
    }

    @Test
    fun `пары переводов связываются по метке экспорта`() {
        // Реальные строки файла: два перевода в одну и ту же минуту —
        // каждое списание встаёт в пару с зачислением, чья метка ровно
        // на 100 мс позже его метки.
        val transactions = parser.parseText(
            row("85;2026-11-15;17:26;Наличные;Личное;;Перевод;-1000;VND;;2;10;210;1794738411354;0") +
                "86;2026-11-15;17:26;Наличные;Личное;;Перевод;1000;VND;;1;58;210;1794738411454;0\n" +
                "87;2026-11-15;17:26;Наличные;Личное;;Перевод;-222222;VND;;2;10;210;1794738423073;0\n" +
                "88;2026-11-15;17:26;Наличные;Личное;;Перевод;222222;VND;;1;58;210;1794738423173;0\n",
        )

        val links = transactions.map { it.transferLinkId }
        assertEquals(2, links.toSet().size)
        assertEquals(listOf(links[0], links[0], links[2], links[2]), links)
        assertEquals("VND", transactions[2].currencyCode)
    }

    @Test
    fun `межвалютный перевод сводится по меткам без равных сумм`() {
        // Строки файла: −1000 CNY против +5 VND — суммой не сходятся, но метка
        // зачисления ровно на 100 мс позже метки списания: связываются парой.
        val transactions = parser.parseText(
            row("89;2026-11-16;15:11;Сперма 228;Общее;;Перевод;-1000;CNY;;2;10;210;1794816724920;0") +
                "90;2026-11-16;15:11;Наличные;Личное;;Перевод;5;VND;;1;58;210;1794816725020;0\n",
        )

        val link = transactions.first().transferLinkId
        assertEquals(link, transactions.last().transferLinkId)
        assertEquals(TxType.EXPENSE, transactions.first().type)
        assertEquals(TxType.INCOME, transactions.last().type)
        assertEquals(listOf("CNY", "VND"), transactions.map { it.currencyCode })
        assertEquals(listOf("Общее", "Личное"), transactions.map { it.balanceName })
    }

    @Test
    fun `непарный перевод и строка другого момента остаются операциями`() {
        val transactions = parser.parseText(
            fallback("1;2026-11-15;17:26;Наличные;Личное;;Перевод;-1000;VND;;2;10;210") +
                "2;2026-11-15;18:00;Наличные;Личное;;Перевод;1000;VND;;1;58;210\n" +
                "3;2026-11-15;19:00;Наличные;Личное;;Перевод;-500;VND;;2;10;210\n",
        )

        // Разные моменты не сводятся, одинокой пары нет: связок нет.
        assertEquals(listOf(null, null, null), transactions.map { it.transferLinkId })
    }

    @Test
    fun `списание предпочитает зачисление с равной суммой`() {
        // Файл без метки — эвристика момента. Два перевода в одну минуту,
        // суммы пересекаются между парами: списание 1000 берет зачисление 1000,
        // а не 2000.
        val transactions = parser.parseText(
            fallback("1;2026-11-15;17:26;Наличные;Личное;;Перевод;-2000;VND;;2;10;210") +
                "2;2026-11-15;17:26;Карта;Личное;;Перевод;1000;VND;;1;58;210\n" +
                "3;2026-11-15;17:26;Наличные;Личное;;Перевод;-1000;VND;;2;10;210\n" +
                "4;2026-11-15;17:26;Карта;Личное;;Перевод;2000;VND;;1;58;210\n",
        )

        val first = transactions[0].transferLinkId to transactions[2].transferLinkId
        val second = transactions[1].transferLinkId to transactions[3].transferLinkId
        assertEquals(2, transactions.map { it.transferLinkId }.toSet().size)
        // Ноги каждой пары: −1000↔+1000 (индексы 2 и 1), −2000↔+2000 (0 и 3).
        assertEquals(first.second, transactions[2].transferLinkId)
        assertEquals(second.second, transactions[3].transferLinkId)
    }

    @Test
    fun `пара сводится по метке, постороннее начисление той же минуты не мешает`() {
        // Реальные строки 2024.csv (317–319, одна минута): пара 318/319 сходится
        // метками (+100 мс), а 317 — внешний доход «Диплом» с собственной
        // меткой в ту же минуту: в пару не встаёт и остаётся операцией.
        val transactions = parser.parseText(
            row("317;2026-04-07;20:50;Карта ПСБ;Общее;null;Перевод;20000;RUB;Диплом;27;28;101;1775584243238;0") +
                "318;2026-04-07;20:50;Карта ПСБ;Общее;;Перевод;-20000;RUB;;2;10;210;1775584261638;0\n" +
                "319;2026-04-07;20:50;Брокерский счет;Общее;;Перевод;20000;RUB;;1;58;210;1775584261738;0\n",
        )

        assertEquals(listOf("Карта ПСБ", "Карта ПСБ", "Брокерский счет"), transactions.map { it.accountName })
        assertNull(transactions[0].transferLinkId)
        val link = transactions[1].transferLinkId
        assertEquals(link, transactions[2].transferLinkId)
        assertEquals(TxType.EXPENSE, transactions[1].type)
        assertEquals(TxType.INCOME, transactions[2].type)
    }

    @Test
    fun `списание без зачисления на сотую секунду позже остается операцией`() {
        // Те же строки без 319: у списания 318 нет зачисления с меткой ровно
        // на 100 мс позже — эвристика минуты связку не подменяет.
        val transactions = parser.parseText(
            row("317;2026-04-07;20:50;Карта ПСБ;Общее;null;Перевод;20000;RUB;Диплом;27;28;101;1775584243238;0") +
                "318;2026-04-07;20:50;Карта ПСБ;Общее;;Перевод;-20000;RUB;;2;10;210;1775584261638;0\n",
        )

        assertEquals(listOf(null, null), transactions.map { it.transferLinkId })
    }

    @Test
    fun `две пары одинаковой суммы в одну минуту сходятся при любом порядке строк`() {
        // Реальные строки 2024.csv (699–702, одна минута, суммы равны 32000):
        // метки различают пары — «Озон → Карта ВТБ» и «Карта ВТБ → Наличные»,
        // порядок строк результата не меняет.
        val rows = listOf(
            "699;2026-07-06;07:03;Озон, отпуск;Общее;;Перевод;-32000;RUB;;2;10;210;1783456867211;0",
            "700;2026-07-06;07:03;Карта ВТБ;Общее;;Перевод;32000;RUB;;1;58;210;1783456867311;0",
            "701;2026-07-06;07:03;Карта ВТБ;Общее;;Перевод;-32000;RUB;;2;10;210;1784121528035;0",
            "702;2026-07-06;07:03;Наличные;Личное;;Перевод;32000;RUB;;1;58;210;1784121528135;0",
        )
        val expected = mapOf(
            "Озон, отпуск" to "Карта ВТБ",
            "Карта ВТБ" to "Наличные",
        )

        listOf(rows, rows.reversed()).forEach { ordered ->
            val transactions = parser.parseText(sheetHeader + ordered.joinToString("") { "$it\n" })
            val pairs = transactions.filter { it.transferLinkId != null }.groupBy { it.transferLinkId!! }
            assertEquals(2, pairs.size)
            val byExpense = pairs.values.associate { legs ->
                legs.single { it.type == TxType.EXPENSE }.accountName to
                    legs.single { it.type == TxType.INCOME }.accountName
            }
            assertEquals(expected, byExpense)
        }
    }

    @Test
    fun `межвалютная пара реального файла сходится метками`() {
        // Реальные строки 2024.csv (976/977): −8600 наличными против +100
        // долларов — суммой и валютой не сходятся, метки сходятся.
        val transactions = parser.parseText(
            row("976;2026-09-19;16:56;Наличные;Личное;;Перевод;-8600;RUB;;2;10;210;1789826198421;0") +
                "977;2026-09-19;16:56;Наличные доллары;Общее;;Перевод;100;USD;;1;58;210;1789826198521;0\n",
        )

        val link = transactions.first().transferLinkId
        assertEquals(link, transactions.last().transferLinkId)
        assertEquals(listOf("RUB", "USD"), transactions.map { it.currencyCode })
        assertEquals(listOf("Личное", "Общее"), transactions.map { it.balanceName })
    }

    @Test
    fun `файл без столбца метки сводит пару эвристикой момента`() {
        val transactions = parser.parseText(
            fallback("1;2026-11-15;17:26;Наличные;Личное;;Перевод;-1000;VND;;2;10;210") +
                "2;2026-11-15;17:26;Карта;Личное;;Перевод;1000;VND;;1;58;210\n",
        )

        assertEquals(transactions[0].transferLinkId, transactions[1].transferLinkId)
    }

    @Test
    fun `нераспознанная метка переводит файл на эвристику момента`() {
        // Столбец метки есть (заголовок), но клетки ног не числа или пустые:
        // файл целиком сводится старой эвристикой — тот же момент, знак.
        val transactions = parser.parseText(
            row("1;2026-11-15;17:26;Наличные;Личное;;Перевод;-1000;VND;;2;10;210;запланирована;0") +
                "2;2026-11-15;17:26;Карта;Личное;;Перевод;1000;VND;;1;58;210;;0\n",
        )

        assertEquals(transactions[0].transferLinkId, transactions[1].transferLinkId)
    }

    @Test
    fun `ошибки строк сообщают номер строки`() {
        val badAmount = assertThrows(BudgetImportException::class.java) {
            parser.parseText(row("1;2026-10-16;18:47;Наличные;Личное;Общее;Еда;abc;VND;;6;71;300;1;0"))
        }
        assertEquals(BudgetImportError.BAD_AMOUNT, badAmount.reason)
        assertEquals(2, badAmount.line)

        val badDate = assertThrows(BudgetImportException::class.java) {
            parser.parseText(row("1;16.10.2026;18:47;Наличные;Личное;Общее;Еда;-100;VND;;6;71;300;1;0"))
        }
        assertEquals(BudgetImportError.BAD_DATE, badDate.reason)

        val badCurrency = assertThrows(BudgetImportException::class.java) {
            parser.parseText(row("1;2026-10-16;18:47;Наличные;Личное;Общее;Еда;-100;VNDR;;6;71;300;1;0"))
        }
        assertEquals(BudgetImportError.BAD_CURRENCY, badCurrency.reason)

        val missing = assertThrows(BudgetImportException::class.java) {
            parser.parseText("ID;Дата;Время;Название счета;Сумма;Валюта\n1;2026-10-16;18:47;Наличные;-100;VND\n")
        }
        assertEquals(BudgetImportError.MISSING_COLUMN, missing.reason)
        assertEquals("Категория", missing.column)
    }

    @Test
    fun `нулевой и пустой суммы сообщают ошибку`() {
        val zero = assertThrows(BudgetImportException::class.java) {
            parser.parseText(row("1;2026-10-16;18:47;Наличные;Личное;Общее;Еда;0;VND;;6;71;300;1;0"))
        }
        assertEquals(BudgetImportError.ZERO_AMOUNT, zero.reason)

        val empty = assertThrows(BudgetImportException::class.java) {
            parser.parseText(row("1;2026-10-16;18:47;Наличные;Личное;Общее;Еда;;VND;;6;71;300;1;0"))
        }
        assertEquals(BudgetImportError.BAD_AMOUNT, empty.reason)
    }

    @Test
    fun `кавычки и пробелы в числах и комментариях разбираются`() {
        val transactions = parser.parseText(
            row("1;2026-10-16;18:47;Наличные;Личное;Общее;Еда;\"-105 000\";VND;\"Ужин; с \"\"друзьями\"\"\";6;71;300;1;0"),
        )

        assertEquals(10_500_000L, transactions.single().amountMinor)
        assertEquals("Ужин; с \"друзьями\"", transactions.single().note)
    }

    @Test
    fun `файл читается и из потока`() {
        val transactions = parser.parse(
            ByteArrayInputStream(row("1;2026-10-16;18:47;Наличные;Личное;Общее;Еда;-105000;VND;;6;71;300;1;0").toByteArray()),
        )

        assertEquals(1, transactions.size)
        assertEquals(10_500_000L, transactions.single().amountMinor)
    }

    @Test
    fun `даты и время до миллисекунд разбираются как моменты`() {
        val transactions = parser.parseText(
            fallback("1;2026-10-16;18:47;Наличные;Личное;Общее;Еда;-100;VND;;6;71;300") +
                "2;2026-10-16;18:47:33;Наличные;Личное;Общее;Перевод;-200;VND;;2;10;210\n" +
                "3;2026-10-16;18:47:33;Наличные;Личное;Общее;Перевод;200;VND;;1;58;210\n",
        )

        // Переводы, записанные с секундами, связываются между собой,
        // а не со строкой той же минуты без секунд.
        assertNull(transactions.first().transferLinkId)
        assertEquals(transactions[1].transferLinkId, transactions[2].transferLinkId)
    }
}
