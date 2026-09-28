package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Category
import com.example.data.Transaction
import com.example.data.TransactionType
import com.example.ui.DynamicCategoryChip
import com.example.ui.colorFromHex
import com.example.ui.iconFromString
import com.example.ui.theme.*

/**
 * Bottom-sheet form terstruktur untuk EDIT transaksi (swipe KIRI).
 *
 * Field pre-filled dari [transaction]: description, amount, type toggle, category.
 * Save → [onUpdate] dengan tx.copy(...) lalu tutup sheet via [onDismiss].
 *
 * D4: timestamp TIDAK diubah — transaksi tetap di kolom hari asal.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTransactionSheet(
    transaction: Transaction,
    categories: List<Category>,
    onUpdate: (Transaction) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var descText by remember { mutableStateOf(transaction.description) }
    var amountText by remember {
        mutableStateOf(transaction.amount.toLong().toString())
    }
    var type by remember { mutableStateOf(transaction.type) }
    var selectedCategory by remember {
        mutableStateOf(categories.find { it.id == transaction.categoryId })
    }

    val typeClass = if (type == TransactionType.EXPENSE) "EXPENSE" else "INCOME"
    val filteredCategories = categories.filter { it.typeClass == typeClass }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        contentColor = GhostWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "EDIT TRANSAKSI",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = GhostWhite.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Deskripsi
            OutlinedTextField(
                value = descText,
                onValueChange = { descText = it },
                label = { Text("Deskripsi") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = editFieldColors()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Nominal (numeric)
            OutlinedTextField(
                value = amountText,
                onValueChange = { input -> amountText = input.filter { it.isDigit() } },
                label = { Text("Nominal") },
                prefix = { Text("Rp ", color = GhostWhite.copy(alpha = 0.6f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = editFieldColors()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Toggle tipe: Pengeluaran / Pemasukan
            Text(
                text = "Tipe",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = GhostWhite.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TypeToggleChip(
                    label = "Pengeluaran",
                    selected = type == TransactionType.EXPENSE,
                    activeColor = SunsetOrange,
                    onClick = {
                        if (type != TransactionType.EXPENSE) {
                            type = TransactionType.EXPENSE
                            if (selectedCategory?.typeClass != "EXPENSE") selectedCategory = null
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                TypeToggleChip(
                    label = "Pemasukan",
                    selected = type == TransactionType.INCOME,
                    activeColor = EmeraldSprint,
                    onClick = {
                        if (type != TransactionType.INCOME) {
                            type = TransactionType.INCOME
                            if (selectedCategory?.typeClass != "INCOME") selectedCategory = null
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Kategori difilter by type aktif
            Text(
                text = "Kategori",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = GhostWhite.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                filteredCategories.forEach { cat ->
                    DynamicCategoryChip(
                        icon = iconFromString(cat.icon),
                        label = cat.name,
                        color = colorFromHex(cat.color),
                        isSelected = selectedCategory?.id == cat.id,
                        onClick = { selectedCategory = cat }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Nominal ikut divalidasi di sini, bukan hanya di repository, supaya
            // tombol Simpan tidak menyala untuk input yang pasti ditolak.
            val typedAmount = amountText.filter { it.isDigit() }.toDoubleOrNull()

            // Aksi
            Button(
                onClick = {
                    val parsedAmount = typedAmount ?: transaction.amount
                    val updated = transaction.copy(
                        amount = parsedAmount,
                        description = descText.trim().ifBlank { transaction.description },
                        category = selectedCategory?.name ?: transaction.category,
                        categoryId = selectedCategory?.id ?: transaction.categoryId,
                        type = type
                    )
                    onUpdate(updated)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(1000.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LimeSqueeze,
                    contentColor = MidnightAbyss
                ),
                enabled = descText.isNotBlank() && typedAmount != null && typedAmount > 0.0
            ) {
                Text(
                    text = "Simpan",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Batal", color = GhostWhite.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun TypeToggleChip(
    label: String,
    selected: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) activeColor.copy(alpha = 0.2f) else GhostWhite.copy(alpha = 0.05f)
    val content = if (selected) activeColor else GhostWhite.copy(alpha = 0.5f)
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(1.dp, if (selected) activeColor else GhostWhite.copy(alpha = 0.15f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = content
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = GhostWhite,
    unfocusedTextColor = GhostWhite,
    focusedBorderColor = LimeSqueeze,
    unfocusedBorderColor = GhostWhite.copy(alpha = 0.15f),
    focusedLabelColor = LimeSqueeze,
    unfocusedLabelColor = GhostWhite.copy(alpha = 0.5f),
    cursorColor = LimeSqueeze
)
