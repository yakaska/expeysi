package com.expeysi.app.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Ключ иконки → Material Icon. Набор фиксированный: новые ключи добавляем
 * только вместе с маппингом. Неизвестный ключ рисуется иконкой по умолчанию.
 */
object IconCatalog {

    const val DEFAULT_KEY = "category"

    private val icons: Map<String, ImageVector> = mapOf(
        "payments" to Icons.Filled.Payments,
        "credit_card" to Icons.Filled.CreditCard,
        "shopping_cart" to Icons.Filled.ShoppingCart,
        "directions_bus" to Icons.Filled.DirectionsBus,
        "home" to Icons.Filled.Home,
        "local_cafe" to Icons.Filled.LocalCafe,
        "medical_services" to Icons.Filled.MedicalServices,
        "sports_esports" to Icons.Filled.SportsEsports,
        "more_horiz" to Icons.Filled.MoreHoriz,
        "work" to Icons.Filled.Work,
        "attach_money" to Icons.Filled.AttachMoney,
        "category" to Icons.Filled.Category,
    )

    val availableKeys: List<String> = icons.keys.toList()

    fun icon(key: String): ImageVector = icons[key] ?: Icons.Filled.Category
}
