package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ai.AiTrace
import com.example.ai.AiTraceLog
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GhostWhite
import com.example.ui.theme.LimeSqueeze
import com.example.ui.theme.MidnightAbyss
import kotlinx.coroutines.delay

/**
 * Diagnostics AI — 20 trace terakhir dari [AiTraceLog].
 *
 * Trace hidup di memori proses saja (sengaja tidak persisten), jadi daftar
 * terisi setelah pengguna menjalankan query chat. Tombol "Bersihkan" mengosongkan
 * ring buffer.
 */
@Composable
fun AiDiagnosticsScreen(onClose: () -> Unit) {
    var traces by remember { mutableStateOf(AiTraceLog.recent()) }

    // Trace baru masuk saat halaman terbuka, jadi poll ringan supaya daftarnya
    // tidak perlu ditutup-dibuka oleh pengguna.
    LaunchedEffect(Unit) {
        while (true) {
            traces = AiTraceLog.recent()
            delay(500)
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
                .padding(horizontal = 20.dp, vertical = 16.dp)
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
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Kembali",
                        tint = GhostWhite
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Diagnostics AI",
                    style = MaterialTheme.typography.headlineMedium,
                    color = GhostWhite
                )
            }

            Spacer(modifier = Modifier.padding(top = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${traces.size} trace terakhir",
                    style = MaterialTheme.typography.labelSmall,
                    color = GhostWhite.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    AiTraceLog.clear()
                    traces = AiTraceLog.recent()
                }) {
                    Text("Bersihkan", color = LimeSqueeze)
                }
            }

            if (traces.isEmpty()) {
                Text(
                    text = "Belum ada trace.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GhostWhite.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(traces.reversed()) { trace -> TraceCard(trace) }
                }
            }
        }
    }
}

@Composable
private fun TraceCard(trace: AiTrace) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, CircleShape)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = trace.query.ifBlank { "(tanpa query)" },
                style = MaterialTheme.typography.bodyMedium,
                color = GhostWhite
            )
            Spacer(modifier = Modifier.padding(top = 4.dp))
            Text(
                text = trace.stages.joinToString("  ") { "${it.name}=${it.ms}ms" } +
                    "  total=${trace.totalMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = GhostWhite.copy(alpha = 0.6f)
            )
            Text(
                text = "token ${trace.promptTokens ?: "-"}/${trace.completionTokens ?: "-"}" +
                    "  ·  validate=${trace.validation}",
                style = MaterialTheme.typography.labelSmall,
                color = GhostWhite.copy(alpha = 0.4f)
            )
        }
    }
}
