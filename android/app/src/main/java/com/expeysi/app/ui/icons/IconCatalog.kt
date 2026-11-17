package com.expeysi.app.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.expeysi.app.domain.TransferFees

/**
 * Ключ иконки → Material Icon. Набор фиксированный: новые ключи добавляем
 * только вместе с маппингом. Неизвестный ключ рисуется иконкой по умолчанию.
 */
object IconCatalog {

    const val DEFAULT_KEY = "category"

    /**
     * Ключ иконки строки перевода в списках операций. Literal живёт в
     * домене (TransferFees.ICON_KEY): псевдо-категория комиссий в
     * разрезках рисуется той же иконкой, а данные слои не зависят от UI.
     */
    const val TRANSFER_KEY = TransferFees.ICON_KEY

    /**
     * Порядок = порядок в сетке выбора: шесть ячеек в ряду, рядом стоят
     * иконки из одной смысловой группы. Пустая ячейка не предусмотрена.
     */
    private val icons: Map<String, ImageVector> = mapOf(
        // Деньги и счёта
        "wallet" to Icons.Filled.Wallet,
        "payments" to Icons.Filled.Payments,
        "credit_card" to Icons.Filled.CreditCard,
        "savings" to Icons.Filled.Savings,
        "account_balance" to Icons.Filled.AccountBalance,
        "trending_up" to Icons.Filled.TrendingUp,
        // Прочие деньги, документы, переводы, транспорт
        "attach_money" to Icons.Filled.AttachMoney,
        "receipt" to Icons.Filled.Receipt,
        "swap_horiz" to Icons.Filled.SwapHoriz,
        "directions_bus" to Icons.Filled.DirectionsBus,
        "directions_car" to Icons.Filled.DirectionsCar,
        "local_taxi" to Icons.Filled.LocalTaxi,
        // Жильё и связь
        "flight" to Icons.Filled.Flight,
        "home" to Icons.Filled.Home,
        "bolt" to Icons.Filled.Bolt,
        "wifi" to Icons.Filled.Wifi,
        "subscriptions" to Icons.Filled.Subscriptions,
        "cleaning_services" to Icons.Filled.CleaningServices,
        // Еда и покупки
        "restaurant" to Icons.Filled.Restaurant,
        "fastfood" to Icons.Filled.Fastfood,
        "local_cafe" to Icons.Filled.LocalCafe,
        "shopping_bag" to Icons.Filled.ShoppingBag,
        "shopping_cart" to Icons.Filled.ShoppingCart,
        "checkroom" to Icons.Filled.Checkroom,
        // Здоровье и досуг
        "content_cut" to Icons.Filled.ContentCut,
        "medical_services" to Icons.Filled.MedicalServices,
        "medication" to Icons.Filled.Medication,
        "fitness_center" to Icons.Filled.FitnessCenter,
        "sports_esports" to Icons.Filled.SportsEsports,
        "movie" to Icons.Filled.Movie,
        // Праздники и дела
        "celebration" to Icons.Filled.Celebration,
        "card_giftcard" to Icons.Filled.CardGiftcard,
        "volunteer_activism" to Icons.Filled.VolunteerActivism,
        "work" to Icons.Filled.Work,
        "school" to Icons.Filled.School,
        "child_care" to Icons.Filled.ChildCare,
        // Личное и прочее
        "pets" to Icons.Filled.Pets,
        "face" to Icons.Filled.Face,
        "smartphone" to Icons.Filled.Smartphone,
        "handyman" to Icons.Filled.Handyman,
        "star" to Icons.Filled.Star,
        DEFAULT_KEY to Icons.Filled.Category,
        "more_horiz" to Icons.Filled.MoreHoriz,
        // Псевдо-категория списаний долга (DebtWriteOffs.ICON_KEY).
        "block" to Icons.Filled.Block,
    )

    val availableKeys: List<String> = icons.keys.toList()

    fun icon(key: String): ImageVector = icons[key] ?: Icons.Filled.Category
}
