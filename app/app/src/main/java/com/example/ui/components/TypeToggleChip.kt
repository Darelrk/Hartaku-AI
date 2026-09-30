package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GhostWhite

/**
 * Chip toggle dua-keadaan untuk memilih satu nilai dari beberapa opsi
 * (mis. tipe transaksi: Pengeluaran / Pemasukan / Tagihan).
 *
 * Dipakai oleh [com.example.ui.home.EditTransactionSheet] dan
 * [com.example.ui.ManualInputScreen] agar tampilan toggle konsisten di
 * kedua layar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeToggleChip(
    label: String,
    selected: Boolean,
    activeColor: Color,
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
