package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Category
import com.example.data.Transaction
import com.example.data.TransactionType
import kotlin.math.roundToInt
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.ui.theme.*
import com.example.RupiahFormatter
import java.time.Instant

/**
 * List transaksi hari itu + search bar + category chips + swipe-to-delete.
 */
@Composable
fun AdvancedTransactionListSlide(
    transactions: List<Transaction> = emptyList(),
    categories: List<Category> = emptyList(),
    onDelete: (Transaction) -> Unit = {},
    onUpdate: (Transaction) -> Unit = {}
) {
    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }
    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) focusRequester.requestFocus()
    }

    val filteredTransactions = remember(transactions, searchQuery) {
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            transactions.filter {
                it.description.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
            }
        } else {
            transactions
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TRANSAKSI",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                        color = GhostWhite.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${filteredTransactions.size} transaksi",
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostWhite.copy(alpha = 0.4f)
                    )
                }
                if (!isSearchOpen) {
                    IconButton(
                        onClick = { isSearchOpen = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(GhostWhite.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Cari transaksi",
                                tint = GhostWhite.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            // Search bar (expand on tap)
            if (isSearchOpen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { focusRequester.requestFocus() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = GhostWhite.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari...", color = GhostWhite.copy(alpha = 0.3f), fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = GhostWhite.copy(alpha = 0.6f)
                        )
                    )
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            isSearchOpen = false
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Tutup pencarian",
                            tint = GhostWhite.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }


            // Transaction list or empty state
            if (filteredTransactions.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(GhostWhite.copy(alpha = 0.05f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = GhostWhite.copy(alpha = 0.3f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Tidak ada hasil untuk \"$searchQuery\""
                               else "Belum ada transaksi hari ini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GhostWhite.copy(alpha = 0.4f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTransactions, key = { it.id }) { tx ->
                        SwipeableTransactionRow(
                            tx = tx,
                            onEdit = { editingTx = tx },
                            onDelete = { pendingDelete = tx }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    pendingDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = DarkSurface,
            titleContentColor = GhostWhite,
            textContentColor = GhostWhite.copy(alpha = 0.7f),
            title = { Text("Hapus transaksi?") },
            text = { Text("\"${tx.description}\" ${RupiahFormatter.format(tx.amount)} akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = { onDelete(tx); pendingDelete = null }) {
                    Text("Hapus", color = SunsetOrange)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Batal", color = GhostWhite.copy(alpha = 0.6f))
                }
            }
        )
    }

    // Edit sheet
    editingTx?.let { tx ->
        EditTransactionSheet(
            transaction = tx,
            categories = categories,
            onUpdate = onUpdate,
            onDismiss = { editingTx = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTransactionRow(
    tx: Transaction,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val swipeThreshold = 150f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX < -swipeThreshold) onDelete()
                        else if (offsetX > swipeThreshold) onEdit()
                        offsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> offsetX = (offsetX + dragAmount).coerceIn(-300f, 300f) }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GhostWhite.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconForCategory(tx.category),
                contentDescription = null,
                tint = GhostWhite.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GhostWhite
                )
                Text(
                    text = buildString {
                        val dateStr = DateTimeFormatter.ofPattern("dd MMM", Locale("id", "ID"))
                            .format(Instant.ofEpochMilli(tx.timestamp).atZone(ZoneId.systemDefault()))
                        append(dateStr)
                        if (tx.category.isNotBlank()) { append(" · ${tx.category}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = GhostWhite.copy(alpha = 0.4f)
                )
            }
            Text(
                text = "${RupiahFormatter.format(tx.amount)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (tx.type == TransactionType.INCOME) EmeraldSprint else GhostWhite
            )
        }
    }
}


private fun iconForCategory(category: String): ImageVector = when (category.lowercase()) {
    "makanan" -> Icons.Default.Restaurant
    "transport" -> Icons.Default.DirectionsCar
    "belanja" -> Icons.Default.ShoppingCart
    "hiburan" -> Icons.Default.Movie
    "tagihan" -> Icons.Default.Receipt
    "kesehatan" -> Icons.Default.LocalHospital
    "pendidikan" -> Icons.Default.School
    else -> Icons.Default.CreditCard
}
