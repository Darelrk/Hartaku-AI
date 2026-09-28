package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.LimeSqueeze
import com.example.ui.theme.GhostWhite
import com.example.ui.theme.MidnightAbyss
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.absoluteValue

private const val DAY_COUNT = 5          // -2..+2
private const val TODAY_PAGE = 2         // initialPage → offset 0
private const val SLIDE_COUNT = 4        // Summary, Transaksi, Chart, AI Insight

/** Map page index pager → dayOffset (-2..+2). */
private fun pageToOffset(page: Int): Int = page - TODAY_PAGE

@Composable
fun HomeFeed() {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(context))

    val today = remember { Calendar.getInstance() }
    val dayPager = rememberPagerState(initialPage = TODAY_PAGE, pageCount = { DAY_COUNT })

    LaunchedEffect(Unit) { viewModel.ensureMockData() }

    val categories by viewModel.categories.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MidnightAbyss)) {
        HeaderAndDateStrip(currentPage = dayPager.currentPage, today = today)

        HorizontalPager(
            state = dayPager,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 1
        ) { page ->
            val offset = pageToOffset(page)
            val dayState by viewModel.dayData(offset).collectAsState()

            DaySlides(
                dayPage = page,
                dayPager = dayPager,
                state = dayState,
                categories = categories,
                onDelete = viewModel::deleteTransaction,
                onUpdate = viewModel::updateTransaction,
                chatHistory = viewModel.getChatHistory(offset).collectAsState().value,
                isChatLoading = viewModel.isChatLoading.collectAsState().value,
                onSendMessage = { msg -> viewModel.sendChatMessage(offset, msg) },
            )
        }
    }
}

/**
 * Inner vertical pager — 4 slide per hari, full-height snap + animasi TikTok.
 * Tiap hari punya VerticalPager state sendiri (page hidup bersamaan tidak boleh share state).
 */
@Composable
private fun DaySlides(
    dayPage: Int,
    dayPager: PagerState,
    state: DayUiState,
    categories: List<com.example.data.Category>,
    onDelete: (com.example.data.Transaction) -> Unit,
    onUpdate: (com.example.data.Transaction) -> Unit,
    chatHistory: List<com.example.ai.ChatMessageItem> = emptyList(),
    isChatLoading: Boolean = false,
    onSendMessage: (String) -> Unit = {},
) {
    val slidePager = rememberPagerState(initialPage = 0, pageCount = { SLIDE_COUNT })

    VerticalPager(
        state = slidePager,
        modifier = Modifier.fillMaxSize()
    ) { slide ->
        // Animasi TikTok: gabungan offset vertikal (slide) + horizontal (hari).
        val slideOffset = (slidePager.currentPage - slide) + slidePager.currentPageOffsetFraction
        val dayOffset = (dayPager.currentPage - dayPage) + dayPager.currentPageOffsetFraction
        val distance = (slideOffset.absoluteValue + dayOffset.absoluteValue).coerceIn(0f, 1f)

        val scale = lerp(1f, 0.86f, distance)
        val fade = lerp(1f, 0.4f, distance)
        val parallax = slideOffset * 40f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = parallax
                }
                .alpha(fade)
        ) {
            when (slide) {
                0 -> SummarySlide(
                    totalSpending = state.totalSpending,
                    totalIncome = state.totalIncome,
                    previousDaySpending = state.previousDaySpending,
                    transactionCount = state.transactionCount,
                    categoryBreakdown = state.categoryBreakdown,
                    budgets = state.budgets,
                    insightText = state.insightText
                )
                1 -> AdvancedTransactionListSlide(
                    transactions = state.transactions,
                    categories = categories,
                    onDelete = onDelete,
                    onUpdate = onUpdate
                )
                2 -> ChartDashboardSlide(
                    twoWeek = state.twoWeekExpense,
                    budgets = state.budgets
                )
                3 -> DailyInsightSlide(
                    chatHistory = chatHistory,
                    isChatLoading = isChatLoading,
                    onSendMessage = onSendMessage,
                )
            }

            // Scroll indicator dots (kanan) — slide aktif highlighted
            SlideDots(
                current = slidePager.currentPage,
                count = SLIDE_COUNT,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
            )
        }
    }
}

@Composable
private fun SlideDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(count) { i ->
            val active = i == current
            Box(
                modifier = Modifier
                    .size(if (active) 8.dp else 6.dp)
                    .background(
                        if (active) LimeSqueeze else GhostWhite.copy(alpha = 0.3f),
                        CircleShape
                    )
            )
        }
    }
}

@Composable
fun HeaderAndDateStrip(currentPage: Int, today: Calendar) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "HARTAKU AI",
            style = MaterialTheme.typography.headlineMedium,
            color = GhostWhite,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dayLabels = remember(today) {
                val sdf = SimpleDateFormat("EEE d", Locale("id"))
                (0 until DAY_COUNT).map { page ->
                    val cal = today.clone() as Calendar
                    cal.add(Calendar.DAY_OF_YEAR, page - TODAY_PAGE)
                    sdf.format(cal.time)
                }
            }

            dayLabels.forEachIndexed { index, label ->
                DateItem(text = label, isActive = index == currentPage)
                if (index < dayLabels.size - 1) {
                    Spacer(modifier = Modifier.width(24.dp))
                }
            }
        }
    }
}

@Composable
fun DateItem(text: String, isActive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            color = if (isActive) GhostWhite else GhostWhite.copy(alpha = 0.5f),
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    if (isActive) LimeSqueeze else Color.Transparent,
                    shape = CircleShape
                )
        )
    }
}

/** Linear interpolation helper (hindari dependency androidx.compose.ui.util.lerp ambiguity). */
private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
