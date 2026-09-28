package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Budget
import com.example.ui.components.GlassPanel
import com.example.ui.theme.GhostWhite
import com.example.ui.theme.LimeSqueeze
import com.example.ui.theme.SunsetOrange

/**
 * Stateless section yang menampilkan opsi alokasi pemasukan ke budget.
 *
 * Section ini dipakai oleh [ManualInputScreen] ketika user memilih
 * tipe transaksi PEMASUKAN. Dipisah sebagai composable sendiri agar
 * bisa diuji secara UI-testable tanpa harus meng-instansiasi ViewModel
 * dan database riil.
 *
 * @param useDefaultAllocation true jika pakai rencana persen default dari Budget.percent
 * @param customAllocations map dari nama kategori ke persen alokasi custom
 * @param budgetCategories daftar Budget aktif yang jadi target alokasi
 * @param onUseDefaultChange callback ketika user mengganti mode default/custom
 * @param onCustomAllocationChange callback ketika user mengubah nilai persen
 *        untuk sebuah kategori di mode custom
 */
@Composable
fun IncomeAllocationSection(
    useDefaultAllocation: Boolean,
    customAllocations: Map<String, Double>,
    budgetCategories: List<Budget>,
    onUseDefaultChange: (Boolean) -> Unit,
    onCustomAllocationChange: (String, Double) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Alokasi Pemasukan ke Anggaran",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = GhostWhite.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(12.dp))

        // A3 empty state: kalau user belum punya anggaran sama sekali,
        // tampilkan CTA card informatif. Save flow TIDAK diblok — transaction
        // tetap tersimpan, hanya alokasi yang di-skip. User bisa setup anggaran
        // nanti lewat BudgetManagementScreen (entry point di ProfileScreen).
        if (budgetCategories.isEmpty()) {
            SetupBudgetCtaCard()
            return@Column
        }

        // Toggle alokasi default vs kustom (seluruh Row clickable via selectable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = useDefaultAllocation,
                    onClick = { onUseDefaultChange(true) },
                    role = Role.RadioButton
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = useDefaultAllocation,
                onClick = null, // handled by selectable on Row
                colors = RadioButtonDefaults.colors(
                    selectedColor = LimeSqueeze,
                    unselectedColor = GhostWhite.copy(alpha = 0.5f)
                )
            )
            Text(
                text = "Gunakan Rencana Persen Default",
                color = GhostWhite,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = !useDefaultAllocation,
                    onClick = { onUseDefaultChange(false) },
                    role = Role.RadioButton
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = !useDefaultAllocation,
                onClick = null, // handled by selectable on Row
                colors = RadioButtonDefaults.colors(
                    selectedColor = LimeSqueeze,
                    unselectedColor = GhostWhite.copy(alpha = 0.5f)
                )
            )
            Text(
                text = "Kustom Alokasi (%)",
                color = GhostWhite,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (!useDefaultAllocation) {
            Spacer(modifier = Modifier.height(16.dp))
            budgetCategories.forEach { budget ->
                val currentPct = customAllocations[budget.name] ?: budget.percent
                var textVal by remember(currentPct) { mutableStateOf(currentPct.toString()) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = budget.name,
                        color = GhostWhite,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = textVal,
                        onValueChange = { newVal ->
                            textVal = newVal
                            newVal.toDoubleOrNull()?.let { dVal ->
                                onCustomAllocationChange(budget.name, dVal)
                            }
                        },
                        modifier = Modifier.width(90.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = GhostWhite),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text("%", color = GhostWhite) }
                    )
                }
            }
            val totalCustom = budgetCategories.sumOf {
                customAllocations[it.name] ?: it.percent
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Total Kustom: $totalCustom% (Harus 100% untuk menyimpan)",
                color = if (totalCustom == 100.0) LimeSqueeze else SunsetOrange,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * A3 empty-state CTA card shown by [IncomeAllocationSection] when the user has
 * no active budgets yet. Informational only — does NOT block the save flow.
 *
 * "Setup Anggaran Dulu" nudges the user toward creating their first budget via
 * [BudgetManagementScreen] (reachable from ProfileScreen's A2 empty state).
 */
@Composable
private fun SetupBudgetCtaCard() {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Setup Anggaran Dulu",
                style = MaterialTheme.typography.titleSmall,
                color = LimeSqueeze,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Belum ada anggaran. Atur limit per kategori agar alokasi pemasukan bisa " +
                       "dibagi otomatis. Transaksimu tetap tersimpan — alokasi bisa di-setup nanti.",
                style = MaterialTheme.typography.bodySmall,
                color = GhostWhite.copy(alpha = 0.7f)
            )
        }
    }
}
