package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.BuildConfig
import com.example.data.Budget
import com.example.data.TaskType
import com.example.data.UpdateChecker
import com.example.data.UpdateInfo
import com.example.ui.components.GlassPanel
import com.example.ui.theme.*
import com.example.work.AgentPrefs
import com.example.RupiahFormatter
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onManageBudgets: () -> Unit = {},
    viewModelOverride: ProfileViewModel? = null
) {
    val context = LocalContext.current
    val viewModel: ProfileViewModel = viewModelOverride
        ?: viewModel(factory = ProfileViewModel.factory(context))
    val state by viewModel.uiState.collectAsState()

    // Direct-create flow: tapping "Tambah Limit Pertama" in the A2 empty state opens
    // [BudgetEditDialog] as a full-screen overlay (skips BudgetManagementScreen).
    // The dialog calls `viewModel.createBudget()` and then closes via [showCreateForm].
    var showCreateForm by remember { mutableStateOf(false) }

    // Manual update check: state lokal, bukan ViewModel — aksi ini one-shot dan
    // [showCreateForm] di atas sudah memakai pola yang sama.
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }

    fun startCheck() {
        scope.launch {
            updateState = UpdateUiState.Checking
            UpdateChecker.check()
                .onSuccess { info ->
                    updateState = UpdateUiState.Done(
                        info = info,
                        isNewer = UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME)
                    )
                }
                .onFailure { e ->
                    updateState = UpdateUiState.Failed(e.message ?: "Gagal mengecek update.")
                }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightAbyss)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = "PROFIL",
                style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 2.sp),
                color = GhostWhite
            )

        Spacer(modifier = Modifier.height(32.dp))

        SectionLabel("STATISTIK")
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                value = "${state.totalTransactions}",
                label = "Total Transaksi",
                color = LimeSqueeze,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = "${state.activeDays}",
                label = "Hari Aktif",
                color = AmethystGlow,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Section dinamai "LIMIT BELANJA" (bukan "TARGET BUDGET") supaya tidak
        // rancu dengan "TOTAL ANGGARAN" di SummarySlide yang sebenarnya merujuk
        // ke total budget. Fitur ini adalah batas pengeluaran per kategori.
        // Section title "LIMIT BELANJA" — saat ada budget, header row di bawah
        // sudah berisi title ini (sebagai clickable affordance), jadi skip di sini.
        if (state.budgets.isEmpty()) {
            SectionLabel("LIMIT BELANJA")
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.budgets.isEmpty()) {
            // A2 empty state — user belum punya anggaran, ajak setup.
            // "Tambah Limit Pertama" → direct flow: open [BudgetEditDialog] overlay
            // (skip BudgetManagementScreen). "Kelola" button (when shown) still routes
            // to BudgetManagementScreen via [onManageBudgets].
            BudgetEmptyStateCard(
                onManageBudgets = onManageBudgets,
                onShowCreateForm = { showCreateForm = true }
            )
        } else {
            // Header bar: title di kiri, "Kelola ›" di kanan. SELURUH row adalah
            // tap target — user bisa tap di mana aja sepanjang header, lebih
            // mudah dijangkau 1 tangan dibanding tombol kecil di sudut.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageBudgets)
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIMIT BELANJA",
                    style = MaterialTheme.typography.labelLarge,
                    color = GhostWhite.copy(alpha = 0.7f),
                    letterSpacing = 1.5.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Kelola",
                        color = LimeSqueeze,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Kelola anggaran",
                        tint = LimeSqueeze,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Total summary card — mirrors BudgetManagementScreen visual sync:
            // 4-tier color threshold, footer Sisa/Lewat, formatRupiah.
            BudgetTotalSummaryCard(budgets = state.budgets)
            Spacer(modifier = Modifier.height(16.dp))

            state.budgets.forEach { budget ->
                val icon = iconForBudget(budget.icon)

                BudgetTargetRow(
                    icon = icon,
                    label = budget.name,
                    spent = budget.spent,
                    target = budget.amount
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Agent Toggles
        SectionLabel("ASISTEN PROAKTIF")
        Spacer(modifier = Modifier.height(12.dp))
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                val toggles = remember { mutableStateOf(AgentPrefs.all(context)) }
                listOf(
                    TaskType.BUDGET_ALERT to "Budget Alert",
                    TaskType.SPENDING_CHECK to "Ringkasan Harian",
                    TaskType.BILL_REMINDER to "Pengingat Tagihan",
                    TaskType.INACTIVITY_REMINDER to "Pengingat Catatan"
                ).forEach { (type, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = GhostWhite, style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = toggles.value[type] ?: true,
                            onCheckedChange = { enabled ->
                                AgentPrefs.setEnabled(context, type, enabled)
                                toggles.value = AgentPrefs.all(context)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        SectionLabel("TENTANG")
        Spacer(modifier = Modifier.height(12.dp))
        GlassPanel(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = GhostWhite.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "HartaKu AI",
                            style = MaterialTheme.typography.bodyLarge,
                            color = GhostWhite
                        )
                        Text(
                            // Dibaca dari BuildConfig, bukan literal — nilai yang sama
                            // dengan yang dibandingkan UpdateChecker.isNewer.
                            text = "Versi ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = GhostWhite.copy(alpha = 0.4f)
                        )
                    }
                }

                val checking = updateState is UpdateUiState.Checking
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clickable(enabled = !checking) { startCheck() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = GhostWhite.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Cek Update",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GhostWhite
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (checking) "Memeriksa..." else BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.labelSmall,
                        color = GhostWhite.copy(alpha = 0.4f)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = GhostWhite.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        }

        // Direct-create flow overlay: [BudgetEditDialog] rendered as a full-screen
        // layer on top of the scrollable content. Renders only while the user has
        // tapped "Tambah Limit Pertama" in the A2 empty state. Save dispatches
        // straight to [ProfileViewModel.createBudget] and closes the dialog.
        if (showCreateForm) {
            BudgetEditDialog(
                currentMonthIncome = state.currentMonthIncome,
                expenseCategories = state.expenseCategories,
                onSave = { name, icon, period, categoryIds, percent, manualAmount, note ->
                    viewModel.createBudget(name, icon, period, categoryIds, percent, manualAmount, note)
                    showCreateForm = false
                },
                onClose = { showCreateForm = false }
            )
        }

        // Hasil "Cek Update": dialog muncul hanya setelah request selesai
        // (Done) atau gagal (Failed). Menutupnya selalu mengembalikan state ke Idle.
        val dialogState = updateState
        if (dialogState is UpdateUiState.Done || dialogState is UpdateUiState.Failed) {
            val title = when (dialogState) {
                is UpdateUiState.Failed -> "Gagal Cek Update"
                is UpdateUiState.Done -> if (dialogState.isNewer) "Update Tersedia" else "Aplikasi Terbaru"
                else -> ""
            }
            val message = when (dialogState) {
                is UpdateUiState.Failed ->
                    "${dialogState.message}\n\nBuka halaman rilis untuk cek manual."
                is UpdateUiState.Done -> if (dialogState.isNewer) {
                    "Versi ${dialogState.info.version} sudah tersedia. Unduh dari halaman rilis."
                } else {
                    "Kamu sudah memakai versi terbaru (${BuildConfig.VERSION_NAME})."
                }
                else -> ""
            }
            val targetUrl = when (dialogState) {
                is UpdateUiState.Done -> dialogState.info.releaseUrl
                else -> UpdateChecker.RELEASES_PAGE
            }
            AlertDialog(
                onDismissRequest = { updateState = UpdateUiState.Idle },
                containerColor = DarkSurface,
                title = {
                    Text(title, color = GhostWhite, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(message, color = GhostWhite.copy(alpha = 0.7f))
                },
                confirmButton = {
                    TextButton(onClick = {
                        openUpdateUrl(context, targetUrl)
                        updateState = UpdateUiState.Idle
                    }) { Text("Buka Halaman Rilis", color = LimeSqueeze) }
                },
                dismissButton = {
                    TextButton(onClick = { updateState = UpdateUiState.Idle }) {
                        Text("Tutup", color = GhostWhite)
                    }
                }
            )
        }
    }
}

/** Status satu-cek-update: [Checking] selama request berjalan, lalu dialog hasil. */
private sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Done(val info: UpdateInfo, val isNewer: Boolean) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

/**
 * Buka halaman rilis di browser. `runCatching` menutup kemungkinan perangkat
 * tanpa browser handler — gagal di sini tidak layak jadi dialog kedua.
 */
private fun openUpdateUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/**
 * A2 empty state — shown di ProfileScreen section "LIMIT BELANJA" ketika
 * user belum punya limit sama sekali. Card dengan border GhostWhite 0.15f,
 * icon AccountBalanceWallet, judul, subtitle, dan tombol CTA lime "Tambah Limit Pertama".
 *
 * Direct-create flow (default behaviour): tapping the CTA invokes [onShowCreateForm]
 * which should open the [BudgetEditDialog] overlay (no intermediate screen).
 * [onManageBudgets] remains for callers that want the legacy navigation path
 * to [BudgetManagementScreen] (kept for tests/back-compat).
 */
@Composable
fun BudgetEmptyStateCard(
    onManageBudgets: () -> Unit = {},
    onShowCreateForm: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GhostWhite.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .background(DarkSurface, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = GhostWhite.copy(alpha = 0.3f), modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Belum Ada Limit",
                style = MaterialTheme.typography.titleMedium,
                color = GhostWhite
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Atur limit per kategori untuk kontrol belanja",
                style = MaterialTheme.typography.bodySmall,
                color = GhostWhite.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onShowCreateForm,
                shape = RoundedCornerShape(1000.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LimeSqueeze,
                    contentColor = MidnightAbyss
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Tambah Limit Pertama",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
        color = GhostWhite.copy(alpha = 0.6f)
    )
}

@Composable
fun StatCard(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 36.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = GhostWhite.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun BudgetTargetRow(
    icon: ImageVector,
    label: String,
    spent: Double,
    target: Double
) {
    // 4-tier color threshold (mirrors BudgetManagementScreen):
    //   >1f    → over budget  (SunsetOrange)
    //   >0.8f  → near limit   (SunsetOrange)
    //   >0.6f  → warning      (GoldenRod)
    //   else   → safe         (LimeSqueeze)
    // `fraction` is the uncoerced ratio so the >1f tier triggers when spent > target.
    // `barWidth` is the coerced ratio so the visual bar fill never exceeds 100%.
    val fraction = if (target > 0) (spent / target).toFloat() else 0f
    val barColor = when {
        fraction > 1f -> SunsetOrange
        fraction > 0.8f -> SunsetOrange
        fraction > 0.6f -> GoldenRod
        else -> LimeSqueeze
    }
    val barWidth = fraction.coerceIn(0f, 1f)
    val sisa = target - spent
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
                        imageVector = icon,
                        contentDescription = label,
                        tint = GhostWhite.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = GhostWhite
                    )
                    Text(
                        text = "${RupiahFormatter.format(spent)} / ${RupiahFormatter.format(target)}",
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
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(DarkSurface, RoundedCornerShape(50))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barWidth)
                        .fillMaxHeight()
                        .background(barColor, RoundedCornerShape(50))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Footer: Sisa / Lewat (matches BudgetManagementScreen BudgetCard)
            Text(
                text = if (isOver) "Lewat: ${RupiahFormatter.format(-sisa)}" else "Sisa: ${RupiahFormatter.format(sisa)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = if (isOver) SunsetOrange else GhostWhite.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Total summary card for the "LIMIT BELANJA" section in [ProfileScreen].
 *
 * Sums the [Budget.amount] and [Budget.spent] across all active budgets, then
 * applies the same 4-tier color threshold + Sisa/Lewat footer as [BudgetTargetRow].
 * Shown above the per-budget rows so the user sees their overall position at a
 * glance before drilling into individual limits.
 */
@Composable
private fun BudgetTotalSummaryCard(budgets: List<Budget>) {
    val totalAmount = budgets.sumOf { it.amount }
    val totalSpent = budgets.sumOf { it.spent }
    val totalSisa = totalAmount - totalSpent
    val totalIsOver = totalSisa < 0
    val totalFraction = if (totalAmount > 0) (totalSpent / totalAmount).toFloat() else 0f
    val totalBarColor = when {
        totalFraction > 1f -> SunsetOrange
        totalFraction > 0.8f -> SunsetOrange
        totalFraction > 0.6f -> GoldenRod
        else -> LimeSqueeze
    }
    val totalBarWidth = totalFraction.coerceIn(0f, 1f)

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TOTAL ANGGARAN",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = GhostWhite.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${RupiahFormatter.format(totalSpent)} / ${RupiahFormatter.format(totalAmount)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = GhostWhite
                    )
                }
                Text(
                    text = "${(totalFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = totalBarColor
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(DarkSurface, RoundedCornerShape(50))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(totalBarWidth)
                        .fillMaxHeight()
                        .background(totalBarColor, RoundedCornerShape(50))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Footer: Sisa / Lewat
            Text(
                text = if (totalIsOver) "Lewat: ${RupiahFormatter.format(-totalSisa)}" else "Sisa: ${RupiahFormatter.format(totalSisa)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = if (totalIsOver) SunsetOrange else LimeSqueeze
            )
        }
    }
}
