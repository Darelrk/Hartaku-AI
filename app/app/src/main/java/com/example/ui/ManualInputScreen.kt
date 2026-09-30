package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.InputMode
import com.example.ui.components.TypeToggleChip
import com.example.data.Category
import com.example.data.ReceiptScanResult
import com.example.ui.theme.*
import com.example.RupiahFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualInputScreen(
    onClose: () -> Unit,
    initialText: String? = null,
    receiptResult: ReceiptScanResult? = null,
    onManageCategories: (() -> Unit)? = null,
    viewModelOverride: ManualInputViewModel? = null
) {
    val context = LocalContext.current
    val viewModel: ManualInputViewModel = viewModelOverride ?: viewModel(factory = ManualInputViewModel.factory(context))
    val state by viewModel.uiState.collectAsState()

    // Pre-fill: voice > receipt > nothing
    val preFillText = initialText ?: receiptResult?.autoFillText ?: ""

    LaunchedEffect(preFillText) {
        if (preFillText.isNotBlank()) {
            viewModel.updateText(preFillText)
        }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            viewModel.resetSaved()
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightAbyss)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
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
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = GhostWhite)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = when {
                        initialText != null -> "Input Suara"
                        receiptResult != null -> "Scan Struk"
                        else -> "Ketik Transaksi"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = GhostWhite
                )
            }

            // Source banner
            when {
                initialText != null -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LimeSqueeze.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .border(1.dp, LimeSqueeze.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = LimeSqueeze, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Teks dari rekaman suara — edit jika perlu", style = MaterialTheme.typography.bodySmall, color = LimeSqueeze.copy(alpha = 0.8f))
                        }
                    }
                }
                receiptResult != null -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SkyboundBlue.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .border(1.dp, SkyboundBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = SkyboundBlue, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Hasil scan dari ${receiptResult.store}", style = MaterialTheme.typography.bodySmall, color = SkyboundBlue.copy(alpha = 0.8f))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${RupiahFormatter.format(receiptResult.total)} — ${receiptResult.items.size} item",
                                style = MaterialTheme.typography.bodySmall,
                                color = GhostWhite.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Text input
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(DarkSurface, RoundedCornerShape(20.dp))
                    .border(1.dp, GhostWhite.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                if (state.text.isEmpty()) {
                    Text(
                        text = "Contoh: " + state.inputMode.descriptionLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = GhostWhite.copy(alpha = 0.3f)
                    )
                }
                TextField(
                    value = state.text,
                    onValueChange = { viewModel.updateText(it) },
                    modifier = Modifier.fillMaxSize(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = GhostWhite,
                        unfocusedTextColor = GhostWhite,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = LimeSqueeze,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tipe transaksi menentukan kategori mana yang relevan
            Text(
                text = "Tipe",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = GhostWhite.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TypeToggleChip(
                    label = InputMode.EXPENSE.displayName,
                    selected = state.inputMode == InputMode.EXPENSE,
                    activeColor = SunsetOrange,
                    onClick = { viewModel.setInputMode(InputMode.EXPENSE) },
                    modifier = Modifier.weight(1f)
                )
                TypeToggleChip(
                    label = InputMode.INCOME.displayName,
                    selected = state.inputMode == InputMode.INCOME,
                    activeColor = EmeraldSprint,
                    onClick = { viewModel.setInputMode(InputMode.INCOME) },
                    modifier = Modifier.weight(1f)
                )
                TypeToggleChip(
                    label = InputMode.BILL.displayName,
                    selected = state.inputMode == InputMode.BILL,
                    activeColor = SkyboundBlue,
                    onClick = { viewModel.setInputMode(InputMode.BILL) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Category chips (dinamis dari DB)
            Text(
                text = "Kategori",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = GhostWhite.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                state.categories.forEach { cat ->
                    DynamicCategoryChip(
                        icon = iconFromString(cat.icon),
                        label = cat.name,
                        color = colorFromHex(cat.color),
                        isSelected = state.selectedCategory?.id == cat.id,
                        onClick = { viewModel.selectCategory(cat) }
                    )
                }
                // Manage button
                if (onManageCategories != null) {
                    DynamicCategoryChip(
                        icon = androidx.compose.material.icons.Icons.Default.Add,
                        label = "Kelola",
                        color = GhostWhite.copy(alpha = 0.3f),
                        isSelected = false,
                        onClick = onManageCategories
                    )
                }
            }

            if (state.error != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = SunsetOrange
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save button
            Button(
                onClick = { viewModel.saveTransaction() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(1000.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LimeSqueeze,
                    contentColor = MidnightAbyss
                ),
                enabled = state.text.isNotBlank() && !state.isProcessing
            ) {
                if (state.isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MidnightAbyss,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Menyimpan...",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                } else {
                    Text(
                        text = "Simpan",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun DynamicCategoryChip(
    icon: ImageVector,
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) color.copy(alpha = 0.2f) else GhostWhite.copy(alpha = 0.05f)
    val borderColor = if (isSelected) color else GhostWhite.copy(alpha = 0.15f)
    val iconTint = if (isSelected) color else GhostWhite.copy(alpha = 0.5f)
    val cornerRadius = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 72.dp)
            .background(bgColor, cornerRadius)
            .border(1.dp, borderColor, cornerRadius)
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = iconTint,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}