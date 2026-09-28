package com.example.ui.home
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Budget
import com.example.data.DailyExpense
import com.example.ui.components.GlassPanel
import com.example.ui.theme.EmeraldSprint
import com.example.ui.theme.GhostWhite
import com.example.ui.theme.GoldenRod
import com.example.ui.theme.LavenderMist
import com.example.ui.theme.LimeSqueeze
import com.example.ui.theme.SunsetOrange
import com.example.RupiahFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

/**
 * Slide 3 of 4 — Chart Dashboard (TREN & PERBANDINGAN).
 *
 * Menampilkan:
 *  - 2 KPI cards: "PENGELUARAN 7 HARI" (dengan delta vs minggu lalu) dan "RATA-RATA HARIAN"
 *  - Bar chart 14 hari: 7 hari lalu (dim) + 7 hari ini (colored by delta)
 *
 * Menggunakan Canvas API murni (bukan library chart) untuk kontrol visual penuh.
 * Style konsisten dengan SummarySlide: GlassPanel, Monospace font, theme colors.
 */
@Composable
fun ChartDashboardSlide(
    twoWeek: TwoWeekExpense,
    budgets: List<Budget> = emptyList(),
    dayOffset: Int = 0
) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,

                    tint = LavenderMist,
                    modifier = Modifier.height(16.dp).width(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "TREN & PERBANDINGAN",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = GhostWhite.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "14 hari terakhir vs 7 hari sebelumnya",
                style = MaterialTheme.typography.bodySmall,
                color = GhostWhite.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(18.dp))

            // KPI Row: 2 cards side-by-side
            if (twoWeek.daily.isEmpty()) {
                EmptyChartCard(text = "Belum ada data 14 hari")
            } else {
                val kpiAccent1 = when {
                    twoWeek.deltaPct == null -> LimeSqueeze
                    twoWeek.deltaPct!! <= 0 -> LimeSqueeze
                    twoWeek.deltaPct!! <= 20.0 -> GoldenRod
                    else -> SunsetOrange
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card A: PENGELUARAN 7 HARI — gradient + animated counter
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        kpiAccent1.copy(alpha = 0.18f),
                                        GhostWhite.copy(alpha = 0.05f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        kpiAccent1.copy(alpha = 0.35f),
                                        GhostWhite.copy(alpha = 0.10f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Column {

                            Text(
                                text = "PENGELUARAN 7 HARI",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                color = GhostWhite.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AnimatedRupiah(
                                target = twoWeek.thisWeekTotal,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = GhostWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val delta = twoWeek.deltaPct
                            val deltaText = when {
                                delta == null -> "—"
                                delta > 0 -> "▲ +${String.format("%.0f", delta)}% naik"
                                delta < 0 -> "▼ ${String.format("%.0f", delta)}% turun"
                                else -> "stabil"
                            }
                            val deltaColor = when {
                                delta == null -> GhostWhite.copy(alpha = 0.5f)
                                delta > 0 -> SunsetOrange
                                delta < 0 -> EmeraldSprint
                                else -> GhostWhite.copy(alpha = 0.5f)
                            }
                            Text(
                                text = deltaText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = deltaColor
                            )
                        }
                    }

                    // Card B: RATA-RATA HARIAN — gradient + animated counter
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        LavenderMist.copy(alpha = 0.18f),
                                        GhostWhite.copy(alpha = 0.05f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        LavenderMist.copy(alpha = 0.35f),
                                        GhostWhite.copy(alpha = 0.10f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "RATA-RATA HARIAN",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                color = GhostWhite.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AnimatedRupiah(
                                target = twoWeek.avgDaily,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = GhostWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "dari 7 hari terakhir",
                                style = MaterialTheme.typography.bodySmall,
                                color = GhostWhite.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bar chart: 14 hari
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PENGELUARAN 14 HARI",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = GhostWhite.copy(alpha = 0.5f)
                        )
                        val delta = twoWeek.deltaPct
                        val deltaText = when {
                            delta == null -> "—"
                            delta > 0 -> "+${String.format("%.0f", delta)}% naik"
                            delta < 0 -> "${String.format("%.0f", delta)}% turun"
                            else -> "stabil"
                        }
                        val deltaColor = when {
                            delta == null -> GhostWhite.copy(alpha = 0.5f)
                            delta > 0 -> SunsetOrange
                            delta < 0 -> EmeraldSprint
                            else -> GhostWhite.copy(alpha = 0.5f)
                        }
                        Text(
                            text = deltaText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = deltaColor
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    if (twoWeek.daily.isEmpty()) {
                        Text(
                            text = "Belum ada data 14 hari",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GhostWhite.copy(alpha = 0.5f)
                        )
                    } else {
                        val daily = twoWeek.daily
                        val maxValue = daily.maxOfOrNull { it.total } ?: 0.0
                        val delta = twoWeek.deltaPct
                        val currentWeekColor = when {
                            delta == null -> LimeSqueeze
                            delta <= 0 -> LimeSqueeze
                            delta <= 20.0 -> GoldenRod
                            else -> SunsetOrange
                        }
                        val dateFmt = remember { DateTimeFormatter.ofPattern("EEE", Locale("id", "ID")) }

                        // Horizontal bar chart — each bar = Row[label | bar | amount]
                        Column(Modifier.fillMaxWidth()) {
                            daily.forEachIndexed { index, expense ->
                                val fraction = if (maxValue > 0) (expense.total / maxValue).toFloat().coerceAtLeast(0f) else 0f
                                val barColor = if (index < 7) currentWeekColor.copy(alpha = 0.20f) else currentWeekColor
                                val amountColor = if (index < 7) GhostWhite.copy(alpha = 0.30f) else GhostWhite
                                val dayName = formatDayName(expense.dayStart)
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(24.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dayName,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        ),
                                        color = GhostWhite.copy(alpha = 0.5f),
                                        modifier = Modifier.width(30.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(16.dp)
                                            .background(GhostWhite.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                                                .background(barColor, RoundedCornerShape(4.dp))
                                        )
                                    }
                                    Text(
                                        text = "${RupiahFormatter.format(expense.total)}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        ),
                                        color = amountColor,
                                        modifier = Modifier.width(80.dp),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Insight kontekstual: hari tertinggi + budget vs aktual + saran hemat
            if (twoWeek.daily.isNotEmpty()) {
                val maxDay: DailyExpense? = twoWeek.daily.maxByOrNull { it.total }
                val totalBudget: Double = budgets.sumOf { it.amount }
                val totalSpent: Double = budgets.sumOf { it.spent }
                val budgetPct: Int = if (totalBudget > 0) ((totalSpent / totalBudget) * 100).toInt() else -1
                val delta = twoWeek.deltaPct

                val dayText: String = if (maxDay != null && maxDay.total > 0) {
                    val dateStr = DateTimeFormatter.ofPattern("dd MMM", Locale("id", "ID")).format(Instant.ofEpochMilli(maxDay.dayStart).atZone(ZoneId.systemDefault()))
                    "Hari tertinggi: ${formatDayName(maxDay.dayStart)}, $dateStr — ${RupiahFormatter.format(maxDay.total)}"
                } else {
                    "Belum ada hari dengan pengeluaran signifikan."
                }

                val (budgetText, budgetColor) = when {
                    totalBudget <= 0.0 -> "Belum ada anggaran. Atur di tab Profile untuk tracking." to GhostWhite.copy(alpha = 0.55f)
                    else -> {
                        val pctStr = budgetPct.toString()
                        "Anggaran: ${RupiahFormatter.format(totalSpent)} / ${RupiahFormatter.format(totalBudget)} ($pctStr%)" to when {
                            budgetPct < 80 -> EmeraldSprint
                            budgetPct <= 100 -> GoldenRod
                            else -> SunsetOrange
                        }
                    }
                }

                val (saranText, saranColor) = when {
                    delta == null -> "Tidak cukup data untuk saran." to GhostWhite.copy(alpha = 0.5f)
                    delta > 20.0 -> "Pengeluaran naik ${String.format("%.0f", delta)}% minggu ini. Pertimbangkan kurangi transaksi non-esensial." to SunsetOrange
                    delta > 0.0 -> "Pengeluaran sedikit naik. Pantau kategori makanan & transport." to GoldenRod
                    delta < 0.0 -> "Bagus! Pengeluaran turun ${String.format("%.0f", -delta)}% vs minggu lalu." to EmeraldSprint
                    else -> "Pengeluaran stabil dibanding minggu lalu." to GhostWhite.copy(alpha = 0.6f)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = LavenderMist.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = LavenderMist.copy(alpha = 0.20f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = LavenderMist,
                                modifier = Modifier.height(14.dp).width(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "INSIGHT",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                color = LavenderMist
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = dayText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = GhostWhite
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = budgetText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = budgetColor
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = saranText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = saranColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
/** Format hari singkat 3 huruf Indonesia (Sen, Sel, Rab, ...). */
private fun formatDayName(ts: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale("id"))
        .format(java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
        .let { it.replaceFirstChar { c -> c.uppercaseChar() } }

/**
 * Counter rupiah animasi: animate dari 0 ke [target] selama ~900ms.
 * Dipakai agar nominal KPI terasa hidup saat pertama kali muncul.
 */
@Composable
private fun AnimatedRupiah(
    target: Double,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = GhostWhite
) {
    val animated: Float by animateFloatAsState(
        targetValue = target.toFloat(),
        animationSpec = tween(durationMillis = 900),
        label = "rupiahCounter"
    )
    Text(
        text = "${RupiahFormatter.format(animated.toDouble())}",
        style = style,
        color = color
    )
}

@Composable
private fun EmptyChartCard(text: String) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = GhostWhite.copy(alpha = 0.5f)
            )
        }
    }
}
