package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private val ICON_OPTIONS = listOf(
    "restaurant" to "Makan",
    "directions_car" to "Transport",
    "shopping_bag" to "Belanja",
    "sports_esports" to "Hiburan",
    "receipt_long" to "Tagihan",
    "account_balance" to "Keuangan",
    "category" to "Umum",
    "star" to "Favorit",
    "favorite" to "Hati",
    "home" to "Rumah",
    "school" to "Sekolah",
    "local_hospital" to "Kesehatan",
    "flight" to "Travel",
    "pets" to "Hewan",
    "local_gas_station" to "Bensin"
)

private val COLOR_OPTIONS = listOf(
    "#FFD700" to "Emas",
    "#00BFFF" to "Biru Langit",
    "#00FF7F" to "Hijau Mint",
    "#9B59B6" to "Ungu",
    "#E74C3C" to "Merah",
    "#FF6B6B" to "Pink",
    "#2ECC71" to "Hijau",
    "#3498DB" to "Biru",
    "#F39C12" to "Oranye",
    "#1ABC9C" to "Tosca"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onClose: () -> Unit,
    /**
     * Membuka arsip kategori. Tanpa ini, teks konfirmasi penghapusan
     * ("Lihat di 'Lihat yang dihapus' untuk memulihkan") menunjuk ke layar
     * yang tidak punya jalan masuk sama sekali.
     */
    onShowDeleted: () -> Unit = {},
    categoryRepositoryOverride: CategoryRepository? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { categoryRepositoryOverride ?: AppContainer.getInstance(context).categoryRepository }

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<Category?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Category?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    // Slug bentrok dilempar sebagai exception dari repository; tanpa state ini
    // coroutine UI mati dan pengguna tidak pernah tahu kenapa dialog tak
    // pernah tertutup.
    var formError by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            categories = repo.getAllActive()
        }
    }

    LaunchedEffect(Unit) { reload() }

    val filtered = if (searchQuery.isBlank()) categories
    else categories.filter { it.name.contains(searchQuery, ignoreCase = true) }

    val expenseCats = filtered.filter { it.typeClass == "EXPENSE" }
    val incomeCats = filtered.filter { it.typeClass == "INCOME" }
    val neutralCats = filtered.filter { it.typeClass == "NEUTRAL" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightAbyss)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
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
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = GhostWhite)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text("Kelola Kategori", style = MaterialTheme.typography.headlineMedium, color = GhostWhite)
                Spacer(modifier = Modifier.weight(1f))
                Text("${categories.size} kategori", style = MaterialTheme.typography.bodySmall, color = GhostWhite.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onShowDeleted) {
                    Text(
                        text = "Lihat yang dihapus",
                        color = GhostWhite.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari kategori...", color = GhostWhite.copy(alpha = 0.4f)) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = GhostWhite,
                    unfocusedTextColor = GhostWhite,
                    focusedContainerColor = MidnightAbyss,
                    unfocusedContainerColor = MidnightAbyss,
                    cursorColor = LimeSqueeze,
                    focusedIndicatorColor = LimeSqueeze.copy(alpha = 0.5f),
                    unfocusedIndicatorColor = GhostWhite.copy(alpha = 0.2f)
                ),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GhostWhite.copy(alpha = 0.5f)) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (expenseCats.isNotEmpty()) {
                    item {
                        SectionHeader(Icons.Default.ShoppingCart, "Pengeluaran", expenseCats.size)
                    }
                    items(expenseCats, key = { it.id }) { cat ->
                        CategoryRow(
                            category = cat,
                            onEdit = { showEditDialog = cat },
                            onDelete = { showDeleteConfirm = cat }
                        )
                    }
                }

                if (incomeCats.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader(Icons.Default.TrendingUp, "Pemasukan", incomeCats.size)
                    }
                    items(incomeCats, key = { it.id }) { cat ->
                        CategoryRow(
                            category = cat,
                            onEdit = { showEditDialog = cat },
                            onDelete = { showDeleteConfirm = cat }
                        )
                    }
                }

                if (neutralCats.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader(Icons.Default.SwapHoriz, "Netral", neutralCats.size)
                    }
                    items(neutralCats, key = { it.id }) { cat ->
                        CategoryRow(
                            category = cat,
                            onEdit = { showEditDialog = cat },
                            onDelete = { showDeleteConfirm = cat }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        formError?.let { message ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 88.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SunsetOrange,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { formError = null }) {
                        Text("Tutup", color = LimeSqueeze)
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = LimeSqueeze,
            contentColor = MidnightAbyss
        ) {
            Icon(Icons.Default.Add, contentDescription = "Tambah Kategori")
        }
    }

    if (showCreateDialog) {
        CategoryFormDialog(
            title = "Tambah Kategori",
            initialName = "",
            initialTypeClass = "EXPENSE",
            onConfirm = { name, slug, typeClass, icon, color, aliasesStr ->
                scope.launch {
                    try {
                        val cat = Category(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            slug = slug,
                            typeClass = typeClass,
                            icon = icon,
                            color = color,
                            aliases = aliasesStr
                        )
                        repo.insert(cat)
                        reload()
                        formError = null
                        showCreateDialog = false
                    } catch (e: Exception) {
                        formError = e.message ?: "Gagal menyimpan kategori"
                    }
                }
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    showEditDialog?.let { cat ->
        CategoryFormDialog(
            title = "Edit Kategori",
            initialName = cat.name,
            initialTypeClass = cat.typeClass,
            initialIcon = cat.icon,
            initialColor = cat.color,
            initialAliases = cat.aliases,
            onConfirm = { name, slug, typeClass, icon, color, aliasesStr ->
                scope.launch {
                    try {
                        repo.update(cat.copy(
                            name = name,
                            slug = slug,
                            typeClass = typeClass,
                            icon = icon,
                            color = color,
                            aliases = aliasesStr,
                            updatedAt = System.currentTimeMillis()
                        ))
                        reload()
                        formError = null
                        showEditDialog = null
                    } catch (e: Exception) {
                        // @Update tetap menabrak unique index slug; alasan
                        // kegagalannya harus tampil, bukan hilang diam-diam.
                        formError = e.message ?: "Gagal menyimpan kategori"
                    }
                }
            },
            onDismiss = { showEditDialog = null }
        )
    }

    showDeleteConfirm?.let { cat ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = DarkSurface,
            title = { Text("Hapus ${cat.name}?", color = GhostWhite) },
            text = {
                Column {
                    Text(
                        "Transaksi lama tetap aman di database.",
                        color = GhostWhite.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Lihat di 'Lihat yang dihapus' untuk memulihkan.",
                        color = GhostWhite.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.softDelete(cat.id)
                        reload()
                        showDeleteConfirm = null
                    }
                }) { Text("Hapus", color = SunsetOrange) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Batal", color = GhostWhite) }
            }
        )
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = GhostWhite.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, color = GhostWhite.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.width(8.dp))
        Text("($count)", style = MaterialTheme.typography.labelSmall, color = GhostWhite.copy(alpha = 0.4f))
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(14.dp))
            .border(1.dp, GhostWhite.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable { onEdit() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colorFromHex(category.color).copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                iconFromString(category.icon),
                contentDescription = null,
                tint = colorFromHex(category.color),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                color = GhostWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val aliases = try {
                org.json.JSONArray(category.aliases).let { arr ->
                    (0 until minOf(arr.length(), 4)).joinToString(", ") { arr.getString(it) }
                }
            } catch (_: Exception) { "" }
            if (aliases.isNotBlank()) {
                Text(
                    text = aliases,
                    style = MaterialTheme.typography.bodySmall,
                    color = GhostWhite.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = SunsetOrange.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryFormDialog(
    title: String,
    initialName: String,
    initialTypeClass: String = "EXPENSE",
    initialIcon: String? = null,
    initialColor: String? = null,
    initialAliases: String = "[]",
    onConfirm: (name: String, slug: String, typeClass: String, icon: String?, color: String?, aliases: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var typeClass by remember { mutableStateOf(initialTypeClass) }
    var selectedIcon by remember { mutableStateOf(initialIcon ?: "category") }
    var selectedColor by remember { mutableStateOf(initialColor ?: "#95A5A6") }
    var aliasesText by remember { mutableStateOf(
        try {
            org.json.JSONArray(initialAliases).let { arr ->
                (0 until arr.length()).joinToString(", ") { arr.getString(it) }
            }
        } catch (_: Exception) { "" }
    ) }

    val slug = name.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9\\s]"), "")
        .replace(Regex("\\s+"), "_")
        .trim('_')
        .ifBlank { "category_${System.currentTimeMillis()}" }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text(title, color = GhostWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Kategori") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogFieldColors(),
                    singleLine = true
                )

                Text("ID: $slug", style = MaterialTheme.typography.bodySmall, color = GhostWhite.copy(alpha = 0.4f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tipe:", color = GhostWhite.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(12.dp))
                    listOf("EXPENSE" to "Pengeluaran", "INCOME" to "Pemasukan", "NEUTRAL" to "Netral").forEach { (value, label) ->
                        FilterChip(
                            selected = typeClass == value,
                            onClick = { typeClass = value },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(end = 8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LimeSqueeze.copy(alpha = 0.2f),
                                selectedLabelColor = LimeSqueeze
                            )
                        )
                    }
                }

                Text("Icon:", color = GhostWhite.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ICON_OPTIONS.forEach { (icon, _) ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (selectedIcon == icon) colorFromHex(selectedColor).copy(alpha = 0.2f) else Color.Transparent,
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    if (selectedIcon == icon) colorFromHex(selectedColor) else GhostWhite.copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .clickable { selectedIcon = icon },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                iconFromString(icon),
                                contentDescription = null,
                                tint = if (selectedIcon == icon) colorFromHex(selectedColor) else GhostWhite.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Text("Warna:", color = GhostWhite.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    COLOR_OPTIONS.forEach { (hex, _) ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(colorFromHex(hex), CircleShape)
                                .border(
                                    2.dp,
                                    if (selectedColor == hex) GhostWhite else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }

                OutlinedTextField(
                    value = aliasesText,
                    onValueChange = { aliasesText = it },
                    label = { Text("Alias (pisahkan dengan koma)") },
                    placeholder = { Text("contoh: kopi, ngopi, coffe") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogFieldColors(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val aliasArray = org.json.JSONArray()
                    aliasesText.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.forEach { aliasArray.put(it) }
                    onConfirm(name, slug, typeClass, selectedIcon, selectedColor, aliasArray.toString())
                },
                enabled = name.isNotBlank()
            ) {
                Text("Simpan", color = if (name.isNotBlank()) LimeSqueeze else GhostWhite.copy(alpha = 0.3f))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = GhostWhite.copy(alpha = 0.6f)) }
        }
    )
}

@Composable
private fun dialogFieldColors() = TextFieldDefaults.colors(
    focusedTextColor = GhostWhite,
    unfocusedTextColor = GhostWhite,
    focusedContainerColor = DarkSurface,
    unfocusedContainerColor = DarkSurface,
    cursorColor = LimeSqueeze,
    focusedIndicatorColor = LimeSqueeze.copy(alpha = 0.5f),
    unfocusedIndicatorColor = GhostWhite.copy(alpha = 0.2f),
    focusedLabelColor = LimeSqueeze,
    unfocusedLabelColor = GhostWhite.copy(alpha = 0.5f)
)

@Composable
fun DeletedCategoriesScreen(
    onClose: () -> Unit,
    onCategoryRestored: (Category) -> Unit = {},
    categoryRepositoryOverride: CategoryRepository? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { categoryRepositoryOverride ?: AppContainer.getInstance(context).categoryRepository }

    var deletedCategories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var showRestoreConfirm by remember { mutableStateOf<Category?>(null) }

    fun reload() {
        scope.launch {
            deletedCategories = repo.getAllDeleted()
        }
    }

    LaunchedEffect(Unit) { reload() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightAbyss)
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text("Kategori yang Dihapus", style = MaterialTheme.typography.headlineMedium, color = GhostWhite)
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(deletedCategories, key = { it.id }) { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(DarkSurface, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(cat.name, color = GhostWhite, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { showRestoreConfirm = cat },
                            modifier = Modifier.testTag("restore_button_${cat.name}")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = "Restore", tint = LimeSqueeze)
                        }
                    }
                }
            }
        }

        showRestoreConfirm?.let { cat ->
            AlertDialog(
                onDismissRequest = { showRestoreConfirm = null },
                containerColor = DarkSurface,
                title = { Text("Pulihkan ${cat.name}?", color = GhostWhite) },
                text = { Text("Kategori ini akan aktif kembali.", color = GhostWhite.copy(alpha = 0.7f)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                repo.restore(cat.id)
                                onCategoryRestored(cat)
                                reload()
                                showRestoreConfirm = null
                            }
                        },
                        modifier = Modifier.testTag("restore_confirm_button")
                    ) { Text("Pulihkan", color = LimeSqueeze) }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirm = null }) { Text("Batal", color = GhostWhite.copy(alpha = 0.6f)) }
                }
            )
        }
    }
}