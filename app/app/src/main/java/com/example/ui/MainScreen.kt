package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.ReceiptScanResult
import com.example.ui.components.AirDropCenterButton
import com.example.ui.components.BottomNavBar
import com.example.ui.home.HomeFeed
import com.example.ui.theme.MidnightAbyss

enum class ScreenRoute {
    HOME,
    VOICE_INPUT,
    SCAN_RECEIPT,
    MANUAL_INPUT,
    CATEGORY_MANAGEMENT,
    BUDGET_MANAGEMENT,
    DELETED_BUDGETS,
    AI_DIAGNOSTICS
}

@Composable
fun MainScreen() {
    var currentTab by remember { mutableStateOf(0) }
    var isFabExpanded by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(ScreenRoute.HOME) }
    var voiceText by remember { mutableStateOf<String?>(null) }
    var receiptResult by remember { mutableStateOf<ReceiptScanResult?>(null) }

    when (currentScreen) {
        ScreenRoute.VOICE_INPUT -> {
            VoiceInputScreen(
                onClose = { recognizedText ->
                    if (recognizedText != null) {
                        voiceText = recognizedText
                        receiptResult = null
                        currentScreen = ScreenRoute.MANUAL_INPUT
                    } else {
                        currentScreen = ScreenRoute.HOME
                    }
                },
                onDirectSave = { savedTxs ->
                    // Directly returned to home after successful background save
                    voiceText = null
                    receiptResult = null
                    currentScreen = ScreenRoute.HOME
                }
            )
            return
        }
        ScreenRoute.SCAN_RECEIPT -> {
            ScanReceiptScreen(onClose = { result ->
                if (result != null) {
                    receiptResult = result
                    voiceText = null
                    currentScreen = ScreenRoute.MANUAL_INPUT
                } else {
                    currentScreen = ScreenRoute.HOME
                }
            })
            return
        }
        ScreenRoute.MANUAL_INPUT -> {
            ManualInputScreen(
                initialText = voiceText,
                receiptResult = receiptResult,
                onManageCategories = { currentScreen = ScreenRoute.CATEGORY_MANAGEMENT },
                onClose = {
                    voiceText = null
                    receiptResult = null
                    currentScreen = ScreenRoute.HOME
                }
            )
            return
        }
        ScreenRoute.CATEGORY_MANAGEMENT -> {
            CategoryManagementScreen(onClose = { currentScreen = ScreenRoute.MANUAL_INPUT })
            return
        }
        ScreenRoute.BUDGET_MANAGEMENT -> {
            BudgetManagementScreen(onClose = { currentScreen = ScreenRoute.HOME })
            return
        }
        ScreenRoute.DELETED_BUDGETS -> {
            DeletedBudgetsScreen(onClose = { currentScreen = ScreenRoute.HOME })
            return
        }
        ScreenRoute.AI_DIAGNOSTICS -> {
            AiDiagnosticsScreen(onClose = { currentScreen = ScreenRoute.HOME })
            return
        }
        ScreenRoute.HOME -> { /* rendered below */ }
    }

    Box(modifier = Modifier.fillMaxSize().background(MidnightAbyss).statusBarsPadding()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MidnightAbyss,
            bottomBar = {
                BottomNavBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        slideInHorizontally { width -> direction * width / 4 } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -direction * width / 4 } + fadeOut()
                    },
                    label = "tabContent"
                ) { tab ->
                    if (tab == 0) {
                        HomeFeed()
                    } else {
                        ProfileScreen(
                            onManageBudgets = { currentScreen = ScreenRoute.BUDGET_MANAGEMENT },
                            onOpenDiagnostics = { currentScreen = ScreenRoute.AI_DIAGNOSTICS }
                        )
                    }
                }
            }
        }

        AirDropCenterButton(
            isExpanded = isFabExpanded,
            onToggle = { isFabExpanded = !isFabExpanded },
            onMicClick = {
                isFabExpanded = false
                currentScreen = ScreenRoute.VOICE_INPUT
            },
            onCameraClick = {
                isFabExpanded = false
                currentScreen = ScreenRoute.SCAN_RECEIPT
            },
            onTextClick = {
                isFabExpanded = false
                voiceText = null
                receiptResult = null  // fresh input
                currentScreen = ScreenRoute.MANUAL_INPUT
            }
        )
    }
}