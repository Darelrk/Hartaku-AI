package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Budget
import com.example.ui.components.GlassPanel
import com.example.ui.theme.MidnightAbyss
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GhostWhite
import com.example.ui.theme.LimeSqueeze
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.RupiahFormatter

/**
 * DeletedBudgetsScreen — archive view for soft-deleted budgets.
 *
 * Lists all budgets where `deletedAt IS NOT NULL`. Each row shows the category,
 * original amount, and deletion date, with a "Pulihkan" action that calls
 * [BudgetManagementViewModel.restoreBudget] (which clears `deletedAt` and the
 * budget reappears in the active list).
 *
 * Same ViewModel instance as [BudgetManagementScreen] (cached by class in the
 * Activity's ViewModelStore) so the Flows stay in sync — restoring here removes
 * the row from this screen and adds it back to the active list when the user
 * returns to [BudgetManagementScreen].
 *
 * @param onClose back navigation
 * @param viewModelOverride optional VM for tests; when null, uses AppContainer's repos
 */
@Composable
fun DeletedBudgetsScreen(
    onClose: () -> Unit,
    viewModelOverride: BudgetManagementViewModel? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: BudgetManagementViewModel = viewModelOverride ?: viewModel(
        factory = BudgetManagementViewModel.factory(context)
    )
    val uiState by viewModel.uiState.collectAsState()

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
                    text = "Anggaran Dihapus",
                    style = MaterialTheme.typography.headlineMedium,
                    color = GhostWhite
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = if (uiState.deletedBudgets.isEmpty()) {
                    "Anggaran yang diarsipkan akan muncul di sini"
                } else {
                    "${uiState.deletedBudgets.size} diarsipkan · pulihkan untuk mengaktifkan kembali"
                },
                style = MaterialTheme.typography.bodySmall,
                color = GhostWhite.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Body
            if (uiState.deletedBudgets.isEmpty()) {
                DeletedBudgetsEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.deletedBudgets, key = { it.id }) { budget ->
                        DeletedBudgetRow(
                            budget = budget,
                            onRestore = { viewModel.restoreBudget(budget.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletedBudgetRow(
    budget: Budget,
    onRestore: () -> Unit
) {
    val deletedAtText = remember(budget.deletedAt) {
        budget.deletedAt?.let { deletedAtMillis ->
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("id", "ID"))
            formatter.format(Instant.ofEpochMilli(deletedAtMillis).atZone(ZoneId.systemDefault()))
        } ?: "—"
    }

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
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
                    tint = GhostWhite.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = budget.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = GhostWhite.copy(alpha = 0.7f)
                )
                Text(
                    text = "${RupiahFormatter.format(budget.amount)} · Dihapus $deletedAtText",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = GhostWhite.copy(alpha = 0.4f)
                )
            }
            TextButton(onClick = onRestore) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    tint = LimeSqueeze,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Pulihkan",
                    color = LimeSqueeze,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun DeletedBudgetsEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = GhostWhite.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tidak Ada Anggaran Dihapus",
                style = MaterialTheme.typography.titleMedium,
                color = GhostWhite
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Anggaran yang diarsipkan akan muncul di sini",
                style = MaterialTheme.typography.bodySmall,
                color = GhostWhite.copy(alpha = 0.5f)
            )
        }
    }
}
