package com.example.ui
import com.example.RupiahFormatter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Category
import com.example.ui.theme.GhostWhite
import com.example.ui.theme.LimeSqueeze
import com.example.ui.theme.MidnightAbyss
import com.example.ui.theme.SunsetOrange
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BudgetEditDialog(
    currentMonthIncome: Double,
    expenseCategories: List<Category>,
    onSave: (
        name: String,
        icon: String,
        period: String,
        categoryIds: List<String>,
        percent: Double,
        manualAmount: Double?,
        note: String?
    ) -> Unit,
    onClose: () -> Unit
) {
    val hasIncome = currentMonthIncome > 0.0

    // Local form state
    var nameText by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("wallet") }
    var selectedPeriod by remember { mutableStateOf("monthly") }
    var selectedCategoryIds by remember { mutableStateOf(setOf<String>()) }
    var isAutoAmount by remember { mutableStateOf(true) }
    var percentFloat by remember { mutableStateOf(10f) }
    var percentText by remember { mutableStateOf("10") }
    var nominalText by remember { mutableStateOf("") }
    var manualAmountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    val nominal = (percentFloat.toDouble() / 100.0) * currentMonthIncome
    val canSave = nameText.isNotBlank() && selectedCategoryIds.isNotEmpty() &&
        if (isAutoAmount) percentFloat > 0f && hasIncome
        else manualAmountText.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0 > 0

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MidnightAbyss)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(GhostWhite.copy(alpha = 0.05f), CircleShape)
                        .border(1.dp, GhostWhite.copy(alpha = 0.15f), CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Kembali",
                        tint = GhostWhite
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Anggaran Baru",
                        style = MaterialTheme.typography.headlineMedium,
                        color = GhostWhite
                    )
                    Text(
                        text = if (selectedPeriod == "weekly") "Periode: Mingguan" else "Periode: Bulanan",
                        style = MaterialTheme.typography.labelSmall,
                        color = GhostWhite.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Form (scrollable)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Field 1: Nama Budget
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Nama Budget *") },
                    placeholder = {
                        Text(
                            "Mis. 'Makan', 'Transport', 'Konsumsi'",
                            color = GhostWhite.copy(alpha = 0.3f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = formFieldColors(),
                    singleLine = true
                )

                // Field 2: Ikon
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ikon",
                        style = MaterialTheme.typography.labelMedium,
                        color = GhostWhite.copy(alpha = 0.7f)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BudgetIconChoice.entries.forEach { choice ->
                            val isSelected = choice.key == selectedIcon
                            IconButton(
                                onClick = { selectedIcon = choice.key },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(
                                        color = if (isSelected) LimeSqueeze.copy(alpha = 0.15f)
                                                else GhostWhite.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) LimeSqueeze
                                                else GhostWhite.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = iconForBudget(choice.key),
                                    contentDescription = choice.label,
                                    tint = if (isSelected) LimeSqueeze
                                           else GhostWhite.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                // Field 3: Period toggle
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Periode",
                        style = MaterialTheme.typography.labelMedium,
                        color = GhostWhite.copy(alpha = 0.7f)
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = selectedPeriod == "weekly",
                            onClick = { selectedPeriod = "weekly" },
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = LimeSqueeze.copy(alpha = 0.2f),
                                activeContentColor = LimeSqueeze,
                                inactiveContainerColor = GhostWhite.copy(alpha = 0.05f),
                                inactiveContentColor = GhostWhite.copy(alpha = 0.6f)
                            ),
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("Mingguan") }
                        SegmentedButton(
                            selected = selectedPeriod == "monthly",
                            onClick = { selectedPeriod = "monthly" },
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = LimeSqueeze.copy(alpha = 0.2f),
                                activeContentColor = LimeSqueeze,
                                inactiveContainerColor = GhostWhite.copy(alpha = 0.05f),
                                inactiveContentColor = GhostWhite.copy(alpha = 0.6f)
                            ),
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("Bulanan") }
                    }
                }

                // Field 4: Category multi-select
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Kategori *",
                        style = MaterialTheme.typography.labelMedium,
                        color = GhostWhite.copy(alpha = 0.7f)
                    )
                    if (expenseCategories.isEmpty()) {
                        Text(
                            text = "Belum ada kategori. Tambah kategori dulu di input transaksi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GhostWhite.copy(alpha = 0.4f)
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            expenseCategories.forEach { cat ->
                                val isSelected = cat.id in selectedCategoryIds
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedCategoryIds = if (isSelected)
                                            selectedCategoryIds - cat.id
                                        else selectedCategoryIds + cat.id
                                    },
                                    label = { Text(cat.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LimeSqueeze.copy(alpha = 0.15f),
                                        selectedLabelColor = LimeSqueeze,
                                        containerColor = GhostWhite.copy(alpha = 0.05f),
                                        labelColor = GhostWhite.copy(alpha = 0.7f)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = GhostWhite.copy(alpha = 0.2f),
                                        selectedBorderColor = LimeSqueeze.copy(alpha = 0.5f),
                                        enabled = true,
                                        selected = isSelected
                                    )
                                )
                            }
                        }
                    }
                }

                // Field 5: Amount mode toggle
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "Otomatis" button
                        Button(
                            onClick = { isAutoAmount = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAutoAmount) LimeSqueeze.copy(alpha = 0.2f)
                                    else GhostWhite.copy(alpha = 0.05f),
                                contentColor = if (isAutoAmount) LimeSqueeze
                                    else GhostWhite.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("% Income", fontWeight = FontWeight.Medium, fontSize = 13.sp) }

                        // "Manual" button
                        Button(
                            onClick = { isAutoAmount = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isAutoAmount) LimeSqueeze.copy(alpha = 0.2f)
                                    else GhostWhite.copy(alpha = 0.05f),
                                contentColor = if (!isAutoAmount) LimeSqueeze
                                    else GhostWhite.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Manual", fontWeight = FontWeight.Medium, fontSize = 13.sp) }
                    }

                    if (isAutoAmount) {
                        // Auto mode: slider + percent + nominal
                        Text(
                            text = "Alokasi Income (%)",
                            style = MaterialTheme.typography.labelMedium,
                            color = GhostWhite.copy(alpha = 0.7f)
                        )
                        Slider(
                            value = percentFloat,
                            onValueChange = { newValue ->
                                percentFloat = newValue
                                percentText = newValue.toInt().toString()
                                if (hasIncome) {
                                    val newNominal = (newValue.toDouble() / 100.0) * currentMonthIncome
                                    nominalText = RupiahFormatter.format(newNominal)
                                }
                            },
                            valueRange = 1f..100f,
                            steps = 99,
                            enabled = hasIncome,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = LimeSqueeze,
                                activeTrackColor = LimeSqueeze,
                                inactiveTrackColor = GhostWhite.copy(alpha = 0.2f),
                                disabledThumbColor = GhostWhite.copy(alpha = 0.3f),
                                disabledActiveTrackColor = GhostWhite.copy(alpha = 0.2f),
                                disabledInactiveTrackColor = GhostWhite.copy(alpha = 0.1f)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = percentText,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }
                                    percentText = filtered
                                    val parsed = filtered.toFloatOrNull()
                                    if (parsed != null) {
                                        val clamped = parsed.coerceIn(1f, 100f)
                                        percentFloat = clamped
                                        if (hasIncome) {
                                            nominalText = RupiahFormatter.format(
                                                (clamped.toDouble() / 100.0) * currentMonthIncome
                                            )
                                        }
                                    }
                                },
                                label = { Text("Persen") },
                                suffix = { Text("%", color = GhostWhite.copy(alpha = 0.5f)) },
                                modifier = Modifier.weight(1f),
                                colors = formFieldColors(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = hasIncome
                            )
                            OutlinedTextField(
                                value = nominalText,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }
                                    nominalText = filtered
                                    if (hasIncome) {
                                        val nominal = filtered.toDoubleOrNull() ?: 0.0
                                        val newPercent = ((nominal / currentMonthIncome) * 100.0)
                                            .coerceIn(1.0, 100.0)
                                        percentFloat = newPercent.toFloat()
                                        percentText = newPercent.toInt().toString()
                                    }
                                },
                                label = { Text("Nominal") },
                                prefix = { Text("Rp ", color = GhostWhite.copy(alpha = 0.5f)) },
                                modifier = Modifier.weight(1f),
                                colors = formFieldColors(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = hasIncome
                            )
                        }
                        if (hasIncome) {
                            Text(
                                text = "= ${RupiahFormatter.format(nominal)} (dari income ${RupiahFormatter.format(currentMonthIncome)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = GhostWhite.copy(alpha = 0.5f)
                            )
                        } else {
                            Text(
                                text = "Tambah income dulu untuk hitung otomatis",
                                style = MaterialTheme.typography.bodySmall,
                                color = SunsetOrange.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        // Manual mode: just rupiah input
                        Text(
                            text = "Jumlah Anggaran",
                            style = MaterialTheme.typography.labelMedium,
                            color = GhostWhite.copy(alpha = 0.7f)
                        )
                        OutlinedTextField(
                            value = manualAmountText,
                            onValueChange = { manualAmountText = it.filter { c -> c.isDigit() } },
                            label = { Text("Nominal *") },
                            prefix = { Text("Rp ", color = GhostWhite.copy(alpha = 0.5f)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = formFieldColors(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        if (hasIncome && manualAmountText.isNotBlank()) {
                            val manual = manualAmountText.toDoubleOrNull() ?: 0.0
                            val pct = ((manual / currentMonthIncome) * 100.0).coerceIn(0.0, 999.0)
                            Text(
                                text = "≈ ${pct.toInt()}% dari income ${RupiahFormatter.format(currentMonthIncome)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = GhostWhite.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Field 6: Catatan
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Catatan (opsional)") },
                    placeholder = { Text("Mis. 'Budget bulanan'", color = GhostWhite.copy(alpha = 0.3f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = formFieldColors(),
                    minLines = 2,
                    maxLines = 4
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save button
            Button(
                onClick = {
                    val manualAmt = if (!isAutoAmount)
                        manualAmountText.filter { it.isDigit() }.toDoubleOrNull()
                    else null
                    onSave(
                        nameText,
                        selectedIcon,
                        selectedPeriod,
                        selectedCategoryIds.toList(),
                        if (isAutoAmount) percentFloat.toDouble() else 0.0,
                        manualAmt,
                        noteText.takeIf { it.isNotBlank() }
                    )
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(1000.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LimeSqueeze,
                    contentColor = MidnightAbyss,
                    disabledContainerColor = GhostWhite.copy(alpha = 0.1f),
                    disabledContentColor = GhostWhite.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = "Simpan Anggaran",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
    }
}

private enum class BudgetIconChoice(val key: String, val label: String) {
    WALLET("wallet", "Dompet"),
    FOOD("food", "Makanan"),
    TRANSPORT("transport", "Transport"),
    SHOPPING("shopping", "Belanja"),
    BILL("bill", "Tagihan"),
    INVESTMENT("investment", "Investasi"),
    HEALTH("health", "Kesehatan"),
    EMERGENCY("emergency", "Darurat")
}

@Composable
private fun formFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = GhostWhite,
    unfocusedTextColor = GhostWhite,
    cursorColor = LimeSqueeze,
    focusedBorderColor = LimeSqueeze,
    unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
    focusedLabelColor = LimeSqueeze,
    unfocusedLabelColor = GhostWhite.copy(alpha = 0.4f)
)
