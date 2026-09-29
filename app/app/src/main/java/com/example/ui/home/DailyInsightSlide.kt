package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.ChatMessageItem
import com.example.ui.theme.GhostWhite
import com.example.ui.theme.LavenderMist

/**
 * Slide 4 — Chat room dengan AI (LocalRuleBasedChat).
 * Full-screen chat, input di bottom.
 */
@Composable
fun DailyInsightSlide(
    chatHistory: List<ChatMessageItem> = emptyList(),
    isChatLoading: Boolean = false,
    onSendMessage: (String) -> Unit = {},
) {
    val density = LocalDensity.current
    // Keyboard harus benar-benar MENGGESER baris input ke atas, bukan sekadar
    // memperkecil padding bawah. Sebelumnya `imeBottom` hanya memilih nilai
    // padding, sehingga baris input tetap di posisi naturalnya — di belakang
    // keyboard — dan tombol Send tidak pernah bisa ditekan pengguna.
    val imeBottom = WindowInsets.ime.getBottom(density)
    val bottomPad = if (imeBottom == 0) 48.dp else 8.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = bottomPad)
            .imePadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header dengan icon sparkle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(LavenderMist.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = LavenderMist)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "TANYA AI",
                    style = MaterialTheme.typography.headlineMedium,
                    color = GhostWhite
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chat messages list
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(12.dp)
            ) {
                if (chatHistory.isEmpty()) {
                    Text(
                        text = "Tanyakan sesuatu tentang keuangan Anda...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GhostWhite.copy(alpha = 0.4f),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val listState = rememberLazyListState()
                    val isNearBottom = remember(listState) {
                        derivedStateOf {
                            val layoutInfo = listState.layoutInfo
                            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                            layoutInfo.totalItemsCount == 0 ||
                                lastVisibleIndex >= layoutInfo.totalItemsCount - 2
                        }
                    }
                    LaunchedEffect(chatHistory.size, isChatLoading) {
                        if (isNearBottom.value) {
                            val targetIndex = if (isChatLoading) chatHistory.size else chatHistory.size - 1
                            listState.animateScrollToItem(targetIndex)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("dailyInsightMessages")
                    ) {
                        itemsIndexed(
                            items = chatHistory,
                            key = { index, _ -> index.toString() }
                        ) { index, message ->
                            when (message.role) {
                                "system" -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = message.content,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GhostWhite.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                                else -> {
                                    ChatMessageBubble(
                                        message = message,
                                        isUser = message.role == "user"
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (isChatLoading) {
                            item {
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = LavenderMist,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "AI sedang berpikir...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GhostWhite.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                        }
                }
            } // Close the Box containing chat history

            // Input field and send button
            var inputText by remember { mutableStateOf(TextFieldValue("")) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(GhostWhite.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                    .border(1.dp, GhostWhite.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(color = GhostWhite),
                    singleLine = true,
                    cursorBrush = SolidColor(LavenderMist),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (inputText.text.isEmpty()) {
                                Text(
                                    text = "Ketik pertanyaan...",
                                    color = GhostWhite.copy(alpha = 0.4f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                val canSend = inputText.text.isNotBlank() && !isChatLoading
                IconButton(
                    onClick = {
                        if (canSend) {
                            onSendMessage(inputText.text)
                            inputText = TextFieldValue("")
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Kirim pesan",
                        tint = if (canSend) LavenderMist else GhostWhite.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: ChatMessageItem, isUser: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .background(
                        if (isUser) LavenderMist.copy(alpha = 0.2f) else GhostWhite.copy(alpha = 0.1f),
                        RoundedCornerShape(16.dp)
                    )
                    .border(
                        1.dp,
                        if (isUser) LavenderMist.copy(alpha = 0.3f) else GhostWhite.copy(alpha = 0.15f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GhostWhite
                )
            }
        }
    }
}
