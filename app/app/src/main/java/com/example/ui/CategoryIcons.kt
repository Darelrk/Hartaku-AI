package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.*
import java.util.Locale

/** Map icon string → Material Icon. Shared across screens. */
fun iconFromString(name: String?): ImageVector = when (name) {
    "restaurant" -> Icons.Default.Restaurant
    "directions_car" -> Icons.Default.DirectionsCar
    "shopping_bag" -> Icons.Default.ShoppingBag
    "sports_esports" -> Icons.Default.SportsEsports
    "receipt_long" -> Icons.Default.Receipt
    "account_balance" -> Icons.Default.AccountBalance
    "trending_up" -> Icons.Default.TrendingUp
    "category" -> Icons.Default.Category
    "star" -> Icons.Default.Star
    "favorite" -> Icons.Default.Favorite
    "home" -> Icons.Default.Home
    "school" -> Icons.Default.School
    "local_hospital" -> Icons.Default.LocalHospital
    "flight" -> Icons.Default.Flight
    "pets" -> Icons.Default.Pets
    "local_gas_station" -> Icons.Default.LocalGasStation
    "spa" -> Icons.Default.Spa
    "fitness_center" -> Icons.Default.FitnessCenter
    "volunteer_activism" -> Icons.Default.VolunteerActivism
    "shield" -> Icons.Default.Shield
    "home_repair_service" -> Icons.Default.HomeRepairService
    "sell" -> Icons.Default.Sell
    "card_giftcard" -> Icons.Default.CardGiftcard
    else -> Icons.Default.Category
}

/** Map hex color string → Compose Color. Shared across screens. */
fun colorFromHex(hex: String?): Color = when (hex?.uppercase(Locale.ROOT)) {
    "#FFD700" -> GoldenRod
    "#00BFFF" -> SkyboundBlue
    "#00FF7F", "#2ECC71" -> EmeraldSprint
    "#9B59B6" -> AmethystGlow
    "#E74C3C", "#FF6B6B" -> SunsetOrange
    "#3498DB" -> SkyboundBlue
    "#F39C12" -> GoldenRod
    "#1ABC9C" -> EmeraldSprint
    "#95A5A6" -> GhostWhite.copy(alpha = 0.5f)
    "#16A085" -> EmeraldSprint
    "#8E44AD" -> AmethystGlow
    "#E91E63" -> SunsetOrange
    "#FF5722" -> SunsetOrange
    "#009688" -> EmeraldSprint
    "#607D8B" -> SkyboundBlue
    "#795548" -> GoldenRod
    "#FF9800" -> SunsetOrange
    "#2196F3" -> SkyboundBlue
    "#CDDC39" -> LimeSqueeze
    else -> GhostWhite.copy(alpha = 0.5f)
}