package com.example.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Budget
import com.example.ui.components.GlassPanel
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GhostWhite
import com.example.ui.theme.GoldenRod
import com.example.ui.theme.LimeSqueeze
import com.example.ui.theme.MidnightAbyss
import com.example.ui.theme.SunsetOrange
import com.example.RupiahFormatter

/**
 * BudgetManagementScreen — B1 card list spec.
 *
 * Per-card content:
 *  - Icon (lookup from `budget.icon` → [iconForBudget], fallback AccountBalanceWallet)
 *  - Category name
 *  - Progress bar (green <60%, gold 60-80%, orange >80%, red >100% — SunsetOrange for both)
 *  - "Rp {spent} / {target}" (mono font)
 *  - Percent (right)
 *  - Sisa / Lewat footer (mono font)
 *  - Overflow menu (⋮) → Edit, Arsipkan
 *
 * Header: "N anggaran · total Rp X"
 * Footer: FAB "+ Anggaran Baru" — opens the C2 form (BudgetEditDialog)
 *
 * @param onClose back navigation
 * @param onShowDeleted navigate to DeletedBudgetsScreen
 * @param viewModelOverride optional VM for tests; when null, uses AppContainer's repos
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetManagementScreen(
    onClose: () -> Unit,
    onShowDeleted: () -> Unit = {},

    viewModelOverride: BudgetManagementViewModel? = null
) {
    val context = LocalContext.current
    val viewModel: BudgetManagementViewModel = viewModelOverride ?: viewModel(
        factory = BudgetManagementViewModel.factory(context)
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateForm by remember { mutableStateOf(false) }

    // Snackbar undo for archive (5s ≈ SnackbarDuration.Short = 4s).
    LaunchedEffect(uiState.justArchived) {
        val archived = uiState.justArchived ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "${archived.name} diarsipkan",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undoArchive()
            SnackbarResult.Dismissed -> viewModel.clearJustArchived()
        }
    }

    // `uiState.error` diisi ViewModel tapi tidak pernah dirender, sehingga
    // kegagalan create/edit terlihat seperti form yang hilang tanpa alasan.
    // Dipakai kembali snackbar host yang sudah ada, tanpa menambah layout.
    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
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
                Text(
                    text = "Kelola Anggaran",
                    style = MaterialTheme.typography.headlineMedium,
                    color = GhostWhite
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Header: count + total
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.activeBudgets.size} anggaran" +
                           if (uiState.activeBudgets.isNotEmpty())
                               " · total ${RupiahFormatter.format(uiState.activeBudgets.sumOf { it.amount })}"
                           else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GhostWhite.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                if (uiState.activeBudgets.isNotEmpty() || uiState.justArchived != null) {
                    TextButton(onClick = onShowDeleted) {
                        Text(
                            text = "Lihat yang dihapus",
                            color = GhostWhite.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body
            Crossfade(
                targetState = when {
                    uiState.isLoading -> 0
                    uiState.activeBudgets.isEmpty() -> 1
                    else -> 2
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                animationSpec = tween(150),
                label = "budgetBody"
            ) { bodyState ->
                when (bodyState) {
                    0 -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = LimeSqueeze)
                        }
                    }
                    1 -> {
                        BudgetListEmptyState(onCreateBudget = { showCreateForm = true })
                    }
                    2 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.activeBudgets, key = { it.id }) { budget ->
                                BudgetCard(
                                    budget = budget,
                                    onEdit = { viewModel.openEditDialog(budget) },
                                    onArchive = { viewModel.archiveBudget(budget) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { showCreateForm = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = LimeSqueeze,
            contentColor = MidnightAbyss
        ) {
            Icon(Icons.Default.Add, contentDescription = "Tambah Anggaran")
        }

        // Snackbar host (bottom-center, above FAB area)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp, start = 16.dp, end = 16.dp)
        )
    }

    // Ringkas edit dialog (percent edit only per Decision #7).
    uiState.editingBudget?.let { budget ->
        BudgetRingkasEditDialog(
            budget = budget,
            currentMonthIncome = uiState.currentMonthIncome,
            onConfirm = { newPercent ->
                viewModel.updateBudget(budget, newPercent)
            },
            onDismiss = { viewModel.closeEditDialog() }
        )
    }

    // C2 form (full screen overlay) — create flow.
    if (showCreateForm) {
        BudgetEditDialog(
            currentMonthIncome = uiState.currentMonthIncome,
            expenseCategories = uiState.expenseCategories,
            onSave = { name, icon, period, categoryIds, percent, manualAmount, note ->
                viewModel.createBudget(name, icon, period, categoryIds, percent, manualAmount, note)
                showCreateForm = false
            },
            onClose = { showCreateForm = false }
        )
    }
}

// ---------- B1 Budget card ----------------------------------------------------

@Composable
private fun BudgetCard(
    budget: Budget,
    onEdit: () -> Unit,
    onArchive: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val fraction = if (budget.amount > 0) (budget.spent / budget.amount).toFloat() else 0f
    val barColor = when {
        fraction > 1f -> SunsetOrange          // over budget
        fraction > 0.8f -> SunsetOrange         // near limit
        fraction > 0.6f -> GoldenRod            // warning
        else -> LimeSqueeze                     // safe
    }
    val sisa = budget.amount - budget.spent
    val isOver = sisa < 0

    GlassPanel(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(DarkSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconForBudget(budget.icon),
                        contentDescription = null,
                        tint = GhostWhite.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = budget.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = GhostWhite
                    )
                    Text(
                        text = "${RupiahFormatter.format(budget.spent)} / ${RupiahFormatter.format(budget.amount)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = GhostWhite.copy(alpha = 0.5f)
                    )
                }
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = barColor
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = GhostWhite.copy(alpha = 0.5f)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Arsipkan", color = SunsetOrange) },
                            onClick = {
                                menuExpanded = false
                                onArchive()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Archive,
                                    contentDescription = null,
                                    tint = SunsetOrange
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(DarkSurface, RoundedCornerShape(50))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(barColor, RoundedCornerShape(50))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer: sisa / lewat
            Text(
                text = if (isOver) "Lewat: ${RupiahFormatter.format(-sisa)}" else "Sisa: ${RupiahFormatter.format(sisa)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = if (isOver) SunsetOrange else GhostWhite.copy(alpha = 0.5f)
            )
        }
    }
}

// ---------- Ringkas edit dialog (percent-based) -------------------------------

@Composable
private fun BudgetRingkasEditDialog(
    budget: Budget,
    currentMonthIncome: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val hasIncome = currentMonthIncome > 0.0

    // Initialize from existing budget
    var percentFloat by remember { mutableStateOf(budget.percent.toFloat().coerceIn(1f, 100f)) }
    var percentText by remember { mutableStateOf(budget.percent.toInt().toString()) }
    var nominalText by remember { mutableStateOf("") }

    // Derive nominal from current percent + income
    val nominal = (percentFloat.toDouble() / 100.0) * currentMonthIncome

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Column {
                Text("Edit Anggaran", color = GhostWhite)
                Text(
                    text = budget.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = GhostWhite.copy(alpha = 0.5f)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // No-income hint
                if (!hasIncome) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                LimeSqueeze.copy(alpha = 0.08f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                LimeSqueeze.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Tambah income dulu untuk mulai budgeting",
                            style = MaterialTheme.typography.bodySmall,
                            color = LimeSqueeze
                        )
                    }
                }

                // Slider
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

                // Percent text + nominal text (side by side)
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
                                    val newNominal =
                                        (clamped.toDouble() / 100.0) * currentMonthIncome
                                    nominalText = RupiahFormatter.format(newNominal)
                                }
                            }
                        },
                        label = { Text("Persen") },
                        suffix = { Text("%", color = GhostWhite.copy(alpha = 0.5f)) },
                        modifier = Modifier.weight(1f),
                        colors = dialogFieldColors(),
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
                                val parsedNominal = filtered.toDoubleOrNull() ?: 0.0
                                val newPercent =
                                    ((parsedNominal / currentMonthIncome) * 100.0)
                                        .coerceIn(1.0, 100.0)
                                percentFloat = newPercent.toFloat()
                                percentText = newPercent.toInt().toString()
                            }
                        },
                        label = { Text("Nominal") },
                        prefix = { Text("Rp ", color = GhostWhite.copy(alpha = 0.5f)) },
                        modifier = Modifier.weight(1f),
                        colors = dialogFieldColors(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = hasIncome
                    )
                }

                // Live preview
                Text(
                    text = if (hasIncome) {
                        "= ${RupiahFormatter.format(nominal)} (dari income ${RupiahFormatter.format(currentMonthIncome)})"
                    } else {
                        "= Rp 0 (butuh income untuk hitung)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = GhostWhite.copy(alpha = 0.5f)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (hasIncome) {
                        onConfirm(percentFloat.toDouble())
                    }
                },
                enabled = hasIncome
            ) {
                Text("Simpan", color = LimeSqueeze, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = GhostWhite.copy(alpha = 0.6f))
            }
        }
    )
}

// ---------- Empty state for the list -----------------------------------------

@Composable
private fun BudgetListEmptyState(onCreateBudget: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = GhostWhite.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Belum Ada Anggaran Aktif",
                style = MaterialTheme.typography.titleMedium,
                color = GhostWhite
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap tombol + untuk membuat anggaran pertama",
                style = MaterialTheme.typography.bodySmall,
                color = GhostWhite.copy(alpha = 0.5f)
            )
        }
    }
}

// ---------- Helpers ------------------------------------------------------------

// `iconForCategory` removed in t-005 — use the shared `iconForBudget(budget.icon)`
// from BudgetIcons.kt instead. Budgets now carry an `icon` string key chosen by
// the user in the C2 form, not a category name.

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = GhostWhite,
    unfocusedTextColor = GhostWhite,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    cursorColor = LimeSqueeze,
    focusedBorderColor = LimeSqueeze,
    unfocusedBorderColor = GhostWhite.copy(alpha = 0.3f),
    focusedLabelColor = LimeSqueeze,
    unfocusedLabelColor = GhostWhite.copy(alpha = 0.5f)
)

