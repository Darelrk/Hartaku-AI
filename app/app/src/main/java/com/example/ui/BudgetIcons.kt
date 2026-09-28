package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Budget icon registry — shared across all surfaces that render a budget (BudgetCard,
 * DeletedBudgetsScreen, ProfileScreen, C2 form picker, etc.).
 *
 * `Budget.icon` stores a string key chosen by the user in the C2 form. This helper
 * resolves the key to a Material [ImageVector]. Unknown / null keys fall back to
 * [Icons.Default.AccountBalanceWallet].
 *
 * Icon keys offered in the C2 picker (see [com.example.ui.BudgetEditDialog]):
 *  - "wallet"          → AccountBalanceWallet (default)
 *  - "food"            → Restaurant
 *  - "transport"       → DirectionsCar
 *  - "shopping"        → ShoppingCart
 *  - "entertainment"   → Movie
 *  - "health"          → LocalHospital
 *  - "education"       → School
 *  - "savings"         → Savings
 */
fun iconForBudget(iconKey: String?): ImageVector = when (iconKey) {
    "wallet" -> Icons.Default.AccountBalanceWallet
    "food" -> Icons.Default.Restaurant
    "transport" -> Icons.Default.DirectionsCar
    "shopping" -> Icons.Default.ShoppingCart
    "entertainment" -> Icons.Default.Movie
    "health" -> Icons.Default.LocalHospital
    "education" -> Icons.Default.School
    "savings" -> Icons.Default.Savings
    else -> Icons.Default.AccountBalanceWallet
}
