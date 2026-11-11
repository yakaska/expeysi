package com.expeysi.app.data.seed

/** Одна валюта ISO 4217: код, английское название, символ. */
data class CurrencyRow(val code: String, val name: String, val symbol: String)

/** Значимость валюты в пикере баланса: от самых частых к скрываемым. */
enum class CurrencyTier { CORE, COMMON, EXOTIC, HIDDEN }

/**
 * Полный список валют (сгенерирован из java.util.Currency, JDK 21; в символах
 * вручную проставлены настоящие Unicode-знаки — getSymbol(Locale.ROOT) вместо
 * глифа возвращает ISO-код). У валют без собственного знака символом остаётся код.
 */
object CurrencySeed {
    val all: List<CurrencyRow> = listOf(
        CurrencyRow("ADP", "Andorran Peseta", "ADP"),
        CurrencyRow("AED", "United Arab Emirates Dirham", "AED"),
        CurrencyRow("AFA", "Afghan Afghani (1927\u20132002)", "AFA"),
        CurrencyRow("AFN", "Afghan Afghani", "\u060B"),
        CurrencyRow("ALL", "Albanian Lek", "ALL"),
        CurrencyRow("AMD", "Armenian Dram", "AMD"),
        CurrencyRow("ANG", "Netherlands Antillean Guilder", "ANG"),
        CurrencyRow("AOA", "Angolan Kwanza", "AOA"),
        CurrencyRow("ARS", "Argentine Peso", "ARS"),
        CurrencyRow("ATS", "Austrian Schilling", "ATS"),
        CurrencyRow("AUD", "Australian Dollar", "A$"),
        CurrencyRow("AWG", "Aruban Florin", "AWG"),
        CurrencyRow("AYM", "AYM", "AYM"),
        CurrencyRow("AZM", "Azerbaijani Manat (1993\u20132006)", "AZM"),
        CurrencyRow("AZN", "Azerbaijani Manat", "\u20BC"),
        CurrencyRow("BAM", "Bosnia-Herzegovina Convertible Mark", "BAM"),
        CurrencyRow("BBD", "Barbadian Dollar", "BBD"),
        CurrencyRow("BDT", "Bangladeshi Taka", "\u09F3"),
        CurrencyRow("BEF", "Belgian Franc", "BEF"),
        CurrencyRow("BGL", "Bulgarian Hard Lev", "BGL"),
        CurrencyRow("BGN", "Bulgarian Lev", "BGN"),
        CurrencyRow("BHD", "Bahraini Dinar", "BHD"),
        CurrencyRow("BIF", "Burundian Franc", "BIF"),
        CurrencyRow("BMD", "Bermudan Dollar", "BMD"),
        CurrencyRow("BND", "Brunei Dollar", "BND"),
        CurrencyRow("BOB", "Bolivian Boliviano", "BOB"),
        CurrencyRow("BOV", "Bolivian Mvdol", "BOV"),
        CurrencyRow("BRL", "Brazilian Real", "R$"),
        CurrencyRow("BSD", "Bahamian Dollar", "BSD"),
        CurrencyRow("BTN", "Bhutanese Ngultrum", "BTN"),
        CurrencyRow("BWP", "Botswanan Pula", "BWP"),
        CurrencyRow("BYB", "Belarusian Ruble (1994\u20131999)", "BYB"),
        CurrencyRow("BYN", "Belarusian Ruble", "BYN"),
        CurrencyRow("BYR", "Belarusian Ruble (2000\u20132016)", "BYR"),
        CurrencyRow("BZD", "Belize Dollar", "BZD"),
        CurrencyRow("CAD", "Canadian Dollar", "CA$"),
        CurrencyRow("CDF", "Congolese Franc", "CDF"),
        CurrencyRow("CHE", "WIR Euro", "CHE"),
        CurrencyRow("CHF", "Swiss Franc", "CHF"),
        CurrencyRow("CHW", "WIR Franc", "CHW"),
        CurrencyRow("CLF", "Chilean Unit of Account (UF)", "CLF"),
        CurrencyRow("CLP", "Chilean Peso", "CLP"),
        CurrencyRow("CNY", "Chinese Yuan", "CN\u00A5"),
        CurrencyRow("COP", "Colombian Peso", "COP"),
        CurrencyRow("COU", "Colombian Real Value Unit", "COU"),
        CurrencyRow("CRC", "Costa Rican Col\u00F3n", "\u20A1"),
        CurrencyRow("CSD", "Serbian Dinar (2002\u20132006)", "CSD"),
        CurrencyRow("CUC", "Cuban Convertible Peso", "CUC"),
        CurrencyRow("CUP", "Cuban Peso", "CUP"),
        CurrencyRow("CVE", "Cape Verdean Escudo", "CVE"),
        CurrencyRow("CYP", "Cypriot Pound", "CYP"),
        CurrencyRow("CZK", "Czech Koruna", "CZK"),
        CurrencyRow("DEM", "German Mark", "DEM"),
        CurrencyRow("DJF", "Djiboutian Franc", "DJF"),
        CurrencyRow("DKK", "Danish Krone", "DKK"),
        CurrencyRow("DOP", "Dominican Peso", "DOP"),
        CurrencyRow("DZD", "Algerian Dinar", "DZD"),
        CurrencyRow("EEK", "Estonian Kroon", "EEK"),
        CurrencyRow("EGP", "Egyptian Pound", "EGP"),
        CurrencyRow("ERN", "Eritrean Nakfa", "ERN"),
        CurrencyRow("ESP", "Spanish Peseta", "ESP"),
        CurrencyRow("ETB", "Ethiopian Birr", "ETB"),
        CurrencyRow("EUR", "Euro", "\u20AC"),
        CurrencyRow("FIM", "Finnish Markka", "FIM"),
        CurrencyRow("FJD", "Fijian Dollar", "FJD"),
        CurrencyRow("FKP", "Falkland Islands Pound", "FKP"),
        CurrencyRow("FRF", "French Franc", "FRF"),
        CurrencyRow("GBP", "British Pound", "\u00A3"),
        CurrencyRow("GEL", "Georgian Lari", "\u20BE"),
        CurrencyRow("GHC", "Ghanaian Cedi (1979\u20132007)", "GHC"),
        CurrencyRow("GHS", "Ghanaian Cedi", "\u20B5"),
        CurrencyRow("GIP", "Gibraltar Pound", "GIP"),
        CurrencyRow("GMD", "Gambian Dalasi", "GMD"),
        CurrencyRow("GNF", "Guinean Franc", "GNF"),
        CurrencyRow("GRD", "Greek Drachma", "GRD"),
        CurrencyRow("GTQ", "Guatemalan Quetzal", "GTQ"),
        CurrencyRow("GWP", "Guinea-Bissau Peso", "GWP"),
        CurrencyRow("GYD", "Guyanaese Dollar", "GYD"),
        CurrencyRow("HKD", "Hong Kong Dollar", "HK$"),
        CurrencyRow("HNL", "Honduran Lempira", "HNL"),
        CurrencyRow("HRK", "Croatian Kuna", "HRK"),
        CurrencyRow("HTG", "Haitian Gourde", "HTG"),
        CurrencyRow("HUF", "Hungarian Forint", "HUF"),
        CurrencyRow("IDR", "Indonesian Rupiah", "IDR"),
        CurrencyRow("IEP", "Irish Pound", "IEP"),
        CurrencyRow("ILS", "Israeli New Shekel", "\u20AA"),
        CurrencyRow("INR", "Indian Rupee", "\u20B9"),
        CurrencyRow("IQD", "Iraqi Dinar", "IQD"),
        CurrencyRow("IRR", "Iranian Rial", "IRR"),
        CurrencyRow("ISK", "Icelandic Kr\u00F3na", "ISK"),
        CurrencyRow("ITL", "Italian Lira", "ITL"),
        CurrencyRow("JMD", "Jamaican Dollar", "JMD"),
        CurrencyRow("JOD", "Jordanian Dinar", "JOD"),
        CurrencyRow("JPY", "Japanese Yen", "\u00A5"),
        CurrencyRow("KES", "Kenyan Shilling", "KES"),
        CurrencyRow("KGS", "Kyrgystani Som", "KGS"),
        CurrencyRow("KHR", "Cambodian Riel", "KHR"),
        CurrencyRow("KMF", "Comorian Franc", "KMF"),
        CurrencyRow("KPW", "North Korean Won", "KPW"),
        CurrencyRow("KRW", "South Korean Won", "\u20A9"),
        CurrencyRow("KWD", "Kuwaiti Dinar", "KWD"),
        CurrencyRow("KYD", "Cayman Islands Dollar", "KYD"),
        CurrencyRow("KZT", "Kazakhstani Tenge", "\u20B8"),
        CurrencyRow("LAK", "Laotian Kip", "\u20AD"),
        CurrencyRow("LBP", "Lebanese Pound", "LBP"),
        CurrencyRow("LKR", "Sri Lankan Rupee", "\u20A8"),
        CurrencyRow("LRD", "Liberian Dollar", "LRD"),
        CurrencyRow("LSL", "Lesotho Loti", "LSL"),
        CurrencyRow("LTL", "Lithuanian Litas", "LTL"),
        CurrencyRow("LUF", "Luxembourgian Franc", "LUF"),
        CurrencyRow("LVL", "Latvian Lats", "LVL"),
        CurrencyRow("LYD", "Libyan Dinar", "LYD"),
        CurrencyRow("MAD", "Moroccan Dirham", "MAD"),
        CurrencyRow("MDL", "Moldovan Leu", "MDL"),
        CurrencyRow("MGA", "Malagasy Ariary", "MGA"),
        CurrencyRow("MGF", "Malagasy Franc", "MGF"),
        CurrencyRow("MKD", "Macedonian Denar", "MKD"),
        CurrencyRow("MMK", "Myanmar Kyat", "MMK"),
        CurrencyRow("MNT", "Mongolian Tugrik", "\u20AE"),
        CurrencyRow("MOP", "Macanese Pataca", "MOP"),
        CurrencyRow("MRO", "Mauritanian Ouguiya (1973\u20132017)", "MRO"),
        CurrencyRow("MRU", "Mauritanian Ouguiya", "MRU"),
        CurrencyRow("MTL", "Maltese Lira", "MTL"),
        CurrencyRow("MUR", "Mauritian Rupee", "MUR"),
        CurrencyRow("MVR", "Maldivian Rufiyaa", "MVR"),
        CurrencyRow("MWK", "Malawian Kwacha", "MWK"),
        CurrencyRow("MXN", "Mexican Peso", "MX$"),
        CurrencyRow("MXV", "Mexican Investment Unit", "MXV"),
        CurrencyRow("MYR", "Malaysian Ringgit", "MYR"),
        CurrencyRow("MZM", "Mozambican Metical (1980\u20132006)", "MZM"),
        CurrencyRow("MZN", "Mozambican Metical", "MZN"),
        CurrencyRow("NAD", "Namibian Dollar", "NAD"),
        CurrencyRow("NGN", "Nigerian Naira", "\u20A6"),
        CurrencyRow("NIO", "Nicaraguan C\u00F3rdoba", "NIO"),
        CurrencyRow("NLG", "Dutch Guilder", "NLG"),
        CurrencyRow("NOK", "Norwegian Krone", "NOK"),
        CurrencyRow("NPR", "Nepalese Rupee", "\u20A8"),
        CurrencyRow("NZD", "New Zealand Dollar", "NZ$"),
        CurrencyRow("OMR", "Omani Rial", "OMR"),
        CurrencyRow("PAB", "Panamanian Balboa", "PAB"),
        CurrencyRow("PEN", "Peruvian Sol", "PEN"),
        CurrencyRow("PGK", "Papua New Guinean Kina", "PGK"),
        CurrencyRow("PHP", "Philippine Peso", "\u20B1"),
        CurrencyRow("PKR", "Pakistani Rupee", "\u20A8"),
        CurrencyRow("PLN", "Polish Zloty", "PLN"),
        CurrencyRow("PTE", "Portuguese Escudo", "PTE"),
        CurrencyRow("PYG", "Paraguayan Guarani", "PYG"),
        CurrencyRow("QAR", "Qatari Riyal", "QAR"),
        CurrencyRow("ROL", "Romanian Leu (1952\u20132006)", "ROL"),
        CurrencyRow("RON", "Romanian Leu", "RON"),
        CurrencyRow("RSD", "Serbian Dinar", "RSD"),
        CurrencyRow("RUB", "Russian Ruble", "\u20BD"),
        CurrencyRow("RUR", "Russian Ruble (1991\u20131998)", "RUR"),
        CurrencyRow("RWF", "Rwandan Franc", "RWF"),
        CurrencyRow("SAR", "Saudi Riyal", "SAR"),
        CurrencyRow("SBD", "Solomon Islands Dollar", "SBD"),
        CurrencyRow("SCR", "Seychellois Rupee", "SCR"),
        CurrencyRow("SDD", "Sudanese Dinar (1992\u20132007)", "SDD"),
        CurrencyRow("SDG", "Sudanese Pound", "SDG"),
        CurrencyRow("SEK", "Swedish Krona", "SEK"),
        CurrencyRow("SGD", "Singapore Dollar", "SGD"),
        CurrencyRow("SHP", "St. Helena Pound", "SHP"),
        CurrencyRow("SIT", "Slovenian Tolar", "SIT"),
        CurrencyRow("SKK", "Slovak Koruna", "SKK"),
        CurrencyRow("SLE", "Sierra Leonean Leone", "SLE"),
        CurrencyRow("SLL", "Sierra Leonean Leone (1964\u20142022)", "SLL"),
        CurrencyRow("SOS", "Somali Shilling", "SOS"),
        CurrencyRow("SRD", "Surinamese Dollar", "SRD"),
        CurrencyRow("SRG", "Surinamese Guilder", "SRG"),
        CurrencyRow("SSP", "South Sudanese Pound", "SSP"),
        CurrencyRow("STD", "S\u00E3o Tom\u00E9 & Pr\u00EDncipe Dobra (1977\u20132017)", "STD"),
        CurrencyRow("STN", "S\u00E3o Tom\u00E9 & Pr\u00EDncipe Dobra", "STN"),
        CurrencyRow("SVC", "Salvadoran Col\u00F3n", "SVC"),
        CurrencyRow("SYP", "Syrian Pound", "SYP"),
        CurrencyRow("SZL", "Swazi Lilangeni", "SZL"),
        CurrencyRow("THB", "Thai Baht", "\u0E3F"),
        CurrencyRow("TJS", "Tajikistani Somoni", "TJS"),
        CurrencyRow("TMM", "Turkmenistani Manat (1993\u20132009)", "TMM"),
        CurrencyRow("TMT", "Turkmenistani Manat", "TMT"),
        CurrencyRow("TND", "Tunisian Dinar", "TND"),
        CurrencyRow("TOP", "Tongan Pa\u02BBanga", "TOP"),
        CurrencyRow("TPE", "Timorese Escudo", "TPE"),
        CurrencyRow("TRL", "Turkish Lira (1922\u20132005)", "TRL"),
        CurrencyRow("TRY", "Turkish Lira", "\u20BA"),
        CurrencyRow("TTD", "Trinidad & Tobago Dollar", "TTD"),
        CurrencyRow("TWD", "New Taiwan Dollar", "NT$"),
        CurrencyRow("TZS", "Tanzanian Shilling", "TZS"),
        CurrencyRow("UAH", "Ukrainian Hryvnia", "\u20B4"),
        CurrencyRow("UGX", "Ugandan Shilling", "UGX"),
        CurrencyRow("USD", "US Dollar", "$"),
        CurrencyRow("USN", "US Dollar (Next day)", "USN"),
        CurrencyRow("USS", "US Dollar (Same day)", "USS"),
        CurrencyRow("UYI", "Uruguayan Peso (Indexed Units)", "UYI"),
        CurrencyRow("UYU", "Uruguayan Peso", "UYU"),
        CurrencyRow("UZS", "Uzbekistani Som", "UZS"),
        CurrencyRow("VEB", "Venezuelan Bol\u00EDvar (1871\u20132008)", "VEB"),
        CurrencyRow("VED", "Bol\u00EDvar Soberano", "VED"),
        CurrencyRow("VEF", "Venezuelan Bol\u00EDvar (2008\u20132018)", "VEF"),
        CurrencyRow("VES", "Venezuelan Bol\u00EDvar", "VES"),
        CurrencyRow("VND", "Vietnamese Dong", "\u20AB"),
        CurrencyRow("VUV", "Vanuatu Vatu", "VUV"),
        CurrencyRow("WST", "Samoan Tala", "WST"),
        CurrencyRow("XAF", "Central African CFA Franc", "FCFA"),
        CurrencyRow("XAG", "Silver", "XAG"),
        CurrencyRow("XAU", "Gold", "XAU"),
        CurrencyRow("XBA", "European Composite Unit", "XBA"),
        CurrencyRow("XBB", "European Monetary Unit", "XBB"),
        CurrencyRow("XBC", "European Unit of Account (XBC)", "XBC"),
        CurrencyRow("XBD", "European Unit of Account (XBD)", "XBD"),
        CurrencyRow("XCD", "East Caribbean Dollar", "EC$"),
        CurrencyRow("XDR", "Special Drawing Rights", "XDR"),
        CurrencyRow("XFO", "French Gold Franc", "XFO"),
        CurrencyRow("XFU", "French UIC-Franc", "XFU"),
        CurrencyRow("XOF", "West African CFA Franc", "F\u202FCFA"),
        CurrencyRow("XPD", "Palladium", "XPD"),
        CurrencyRow("XPF", "CFP Franc", "CFPF"),
        CurrencyRow("XPT", "Platinum", "XPT"),
        CurrencyRow("XSU", "Sucre", "XSU"),
        CurrencyRow("XTS", "Testing Currency Code", "XTS"),
        CurrencyRow("XUA", "ADB Unit of Account", "XUA"),
        CurrencyRow("XXX", "Unknown Currency", "\u00A4"),
        CurrencyRow("YER", "Yemeni Rial", "YER"),
        CurrencyRow("YUM", "Yugoslavian New Dinar (1994\u20132002)", "YUM"),
        CurrencyRow("ZAR", "South African Rand", "ZAR"),
        CurrencyRow("ZMK", "Zambian Kwacha (1968\u20132012)", "ZMK"),
        CurrencyRow("ZMW", "Zambian Kwacha", "ZMW"),
        CurrencyRow("ZWD", "Zimbabwean Dollar (1980\u20132008)", "ZWD"),
        CurrencyRow("ZWL", "Zimbabwean Dollar (2009)", "ZWL"),
        CurrencyRow("ZWN", "ZWN", "ZWN"),
        CurrencyRow("ZWR", "Zimbabwean Dollar (2008)", "ZWR"),
    )

    /**
     * Коды, не являющиеся действующими валютами ISO 4217: отменённые (ADP, AFA,
     * национальные марки евро и прочие) и заглушки JDK (AYM, ZWN). В пикере
     * баланса скрыты, из справочника не удалены — у существующих балансов код
     * должен резолвиться.
     */
    val deprecatedCodes: Set<String> = setOf(
        "ADP", "AFA", "AYM", "ATS", "AZM", "BEF", "BGL", "BYB", "BYR", "CSD",
        "CYP", "DEM", "EEK", "ESP", "FIM", "FRF", "GHC", "GRD", "GWP", "HRK",
        "IEP", "ITL", "LTL", "LUF", "LVL", "MGF", "MRO", "MTL", "MZM", "NLG",
        "PTE", "ROL", "RUR", "SDD", "SIT", "SKK", "SRG", "STD", "TMM", "TPE",
        "TRL", "VEB", "VEF", "XBA", "XBB", "XBC", "XBD", "XFO", "XFU", "YUM",
        "ZMK", "ZWD", "ZWN", "ZWR",
    )

    /** Годы в названии вида «(1927–2002)» или «(2009)»: валюта устарела. */
    private val yearsPattern = Regex("\\(\\d{4}([–—-]\\d{4})?\\)")

    /** Устаревшая ли валюта: скрывается в пикере баланса. */
    fun isDeprecated(row: CurrencyRow): Boolean =
        row.code in deprecatedCodes || yearsPattern.containsMatchIn(row.name)

    /** Спец-коды, не являющиеся валютами: металлы, фонды, тестовые и неизвестные. */
    private val hiddenCodes: Set<String> = setOf(
        "XAU", "XAG", "XPT", "XPD", "XDR", "XTS", "XXX", "XSU", "XUA",
    )

    /** Ядро «Популярных»: фиксированный порядок = порядок секции. */
    private val coreCodes: List<String> = listOf("RUB", "USD", "EUR", "VND", "CNY", "KZT")

    /** Распространённые валюты: показываются в «Популярных» после ядра. */
    private val commonCodes: List<String> = listOf(
        "GBP", "JPY", "CHF", "CAD", "AUD", "NZD", "SEK", "NOK", "DKK", "PLN",
        "CZK", "HUF", "TRY", "UAH", "BYN", "AMD", "GEL", "AZN", "UZS", "KGS",
        "TJS", "MDL", "INR", "KRW", "HKD", "SGD", "THB", "IDR", "MYR", "PHP",
        "TWD", "AED", "SAR", "ILS", "EGP", "ZAR", "MXN", "BRL", "ARS", "CLP",
        "UYU",
    )

    /**
     * Тиры валют пикера по коду. Не пишется в БД и не мигрирует: изменения
     * доезжают до существующих пользователей вместе с приложением. HIDDEN в
     * карте — не полный список скрытых: устаревшие добавляет isDeprecated.
     */
    val tiers: Map<String, CurrencyTier> = linkedMapOf<String, CurrencyTier>().apply {
        coreCodes.forEach { put(it, CurrencyTier.CORE) }
        commonCodes.forEach { put(it, CurrencyTier.COMMON) }
        hiddenCodes.forEach { put(it, CurrencyTier.HIDDEN) }
    }

    /** Тир валюты для пикера: HIDDEN (устаревший/спец-код), иначе по карте, иначе EXOTIC. */
    fun tierOf(code: String): CurrencyTier = when {
        code in deprecatedCodes || code in hiddenCodes -> CurrencyTier.HIDDEN
        byCode[code]?.let { yearsPattern.containsMatchIn(it.name) } == true -> CurrencyTier.HIDDEN
        else -> tiers[code] ?: CurrencyTier.EXOTIC
    }

    private val byCode: Map<String, CurrencyRow> = all.associateBy { it.code }
}
