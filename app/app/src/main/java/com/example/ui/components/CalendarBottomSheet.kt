package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Bill
import com.example.ui.theme.*
import com.example.RupiahFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarBottomSheet(
    bills: List<Bill>,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MidnightAbyss,
        contentColor = GhostWhite,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Kalender Tagihan",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = GhostWhite,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Calculate calendar grid
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday
            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            
            // Adjust to Monday first
            val offset = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2
            
            val daysOfWeek = listOf("Sn", "Sl", "Rb", "Km", "Jm", "Sb", "Mg")

            // Weekday header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                daysOfWeek.forEach { day ->
                    Text(
                        text = day,
                        color = GhostWhite.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar grid
            val totalCells = offset + daysInMonth
            val rows = Math.ceil(totalCells / 7.0).toInt()

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(totalCells) { index ->
                    if (index < offset) {
                        Box(modifier = Modifier.aspectRatio(1f)) // Empty cell
                    } else {
                        val day = index - offset + 1
                        
                        // Handle max day depending on month, bill.dueDate might be 31 on a 28 day month
                        val activeBillsToday = bills.filter { bill -> 
                            val due = if (bill.dueDate > daysInMonth) daysInMonth else bill.dueDate
                            due == day
                        }
                        
                        val hasUnpaidBill = activeBillsToday.any { !it.isPaidThisMonth }
                        val isPaidBill = activeBillsToday.isNotEmpty() && activeBillsToday.all { it.isPaidThisMonth }
                        
                        val borderColor = when {
                            hasUnpaidBill -> SunsetOrange
                            isPaidBill -> EmeraldSprint
                            else -> Color.Transparent
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(if (activeBillsToday.isNotEmpty()) DarkSurface else Color.Transparent)
                                .border(
                                    width = if (borderColor != Color.Transparent) 2.dp else 0.dp,
                                    color = borderColor,
                                    shape = CircleShape
                                )
                        ) {
                            Text(
                                text = day.toString(),
                                color = GhostWhite,
                                fontSize = 14.sp,
                                fontWeight = if (activeBillsToday.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Daftar Tagihan",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = GhostWhite,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (bills.isEmpty()) {
                Text(
                    text = "Tidak ada tagihan aktif bulan ini.",
                    color = GhostWhite.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bills.sortedBy { it.dueDate }) { bill ->
                        BillItemRow(bill, daysInMonth)
                    }
                }
            }
        }
    }
}

@Composable
fun BillItemRow(bill: Bill, daysInMonth: Int) {
    val actualDueDate = if (bill.dueDate > daysInMonth) daysInMonth else bill.dueDate
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = bill.name,
                color = GhostWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Jatuh tempo: Tanggal $actualDueDate",
                color = GhostWhite.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
        
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${RupiahFormatter.format(bill.amount)}",
                color = GhostWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (bill.isPaidThisMonth) {
                Text(
                    text = "Lunas",
                    color = EmeraldSprint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "Belum Lunas",
                    color = SunsetOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}