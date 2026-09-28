package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Budget
import com.example.data.CategoryTotal
import com.example.ui.theme.*
import com.example.RupiahFormatter

/**
 * Slide 1 — Daily Summary (full-height, PRD-aligned).
 * Masuk/Keluar/Saldo cards + top kategori (REAL breakdown) + budget mini + quick insight.
 */
@Composable
fun SummarySlide(
    totalSpending: Double = 0.0,
    totalIncome: Double = 0.0,
    previousDaySpending: Double = 0.0,
    transactionCount: Int = 0,
    categoryBreakdown: List<CategoryTotal> = emptyList(),
    budgets: List<Budget> = emptyList(),
    insightText: String = ""
) {
    val balance = totalIncome - totalSpending
    val comparison = if (previousDaySpending > 0) {
        ((totalSpending - previousDaySpending) / previousDaySpending * 100).toInt()
    } else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Text(
                text = "RINGKASAN HARI INI",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = GhostWhite.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 3 summary cards: Masuk / Keluar / Saldo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryCard(
                    label = "MASUK",
                    amount = totalIncome,
                    accent = EmeraldSprint,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    label = "KELUAR",
                    amount = totalSpending,
                    accent = SunsetOrange,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            BalanceCard(balance = balance, transactionCount = transactionCount, comparison = comparison)

            Spacer(modifier = Modifier.height(20.dp))

            // Top kategori (real breakdown)
            if (categoryBreakdown.isNotEmpty()) {
                Text(
                    text = "TOP KATEGORI",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = GhostWhite.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                val total = categoryBreakdown.sumOf { it.total }.coerceAtLeast(1.0)
                categoryBreakdown.take(4).forEach { cat ->
                    TopCategoryBar(
                        name = cat.name,
                        amount = cat.total,
                        fraction = (cat.total / total).toFloat()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Quick insight (Lavender Mist accent)
            if (insightText.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LavenderMist.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                        .border(1.dp, LavenderMist.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(LavenderMist, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = insightText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GhostWhite.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    amount: Double,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(GhostWhite.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            .border(1.dp, GhostWhite.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = GhostWhite.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${RupiahFormatter.format(amount)}",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = accent
        )
    }
}

@Composable
private fun BalanceCard(balance: Double, transactionCount: Int, comparison: Int?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LimeSqueeze.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .border(1.dp, LimeSqueeze.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Text(
            text = "SALDO HARI INI",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = GhostWhite.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${if (balance >= 0) "+ " else "- "}${RupiahFormatter.format(kotlin.math.abs(balance))}",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp),
            color = if (balance >= 0) LimeSqueeze else SunsetOrange
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (comparison != null) {
                val up = comparison > 0
                Text(
                    text = "${if (up) "\u2191" else "\u2193"} ${kotlin.math.abs(comparison)}%",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (up) SunsetOrange else EmeraldSprint
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "dari kemarin",
                    style = MaterialTheme.typography.bodySmall,
                    color = GhostWhite.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(
                text = "$transactionCount transaksi",
                style = MaterialTheme.typography.bodySmall,
                color = GhostWhite.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun TopCategoryBar(name: String, amount: Double, fraction: Float) {
    val barColor = barColorForCategory(name)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(barColor, CircleShape))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = GhostWhite.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${RupiahFormatter.format(amount)}",
                style = MaterialTheme.typography.bodySmall,
                color = GhostWhite.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = barColor
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(DarkSurface, RoundedCornerShape(50))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(barColor, RoundedCornerShape(50))
            )
        }
    }
}

/** Map kategori name → warna sesuai PRD color mapping. */
fun barColorForCategory(name: String): Color = when (name.lowercase()) {
    "makanan" -> GoldenRod
    "transport" -> SkyboundBlue
    "belanja" -> EmeraldSprint
    "hiburan" -> AmethystGlow
    "tagihan" -> SunsetOrange
    "gaji" -> LimeSqueeze
    else -> GhostWhite.copy(alpha = 0.4f)
}


