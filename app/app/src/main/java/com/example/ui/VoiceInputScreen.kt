package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.AppContainer
import com.example.ui.theme.*
import com.example.RupiahFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun VoiceInputScreen(
    onClose: (String?) -> Unit,
    onDirectSave: (List<com.example.data.Transaction>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = remember { AppContainer.getInstance(context) }
    val audioRecorder = remember { container.audioRecorder }
    val whisperClient = remember { container.whisperApiClient }
    val transactionRepo = remember { container.transactionRepository }
    val categoryRepo = remember { container.categoryRepository }
    val aiParser = remember { container.transactionAiParser }

    var hasMicPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingStage by remember { mutableStateOf("") } // "whisper" | "nim" | "success" | "none"
    var transcription by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var startTime by remember { mutableLongStateOf(0L) }
    var successDetails by remember { mutableStateOf("") }

    // Direct AI parse and save helper function
    fun processTextToDirectSave(text: String) {
        isProcessing = true
        processingStage = "nim"
        scope.launch {
            try {
                val allCats = categoryRepo.getAllActive()
                if (aiParser != null) {
                    val parseResult = aiParser.parse(text, allCats)
                    val validTxs = parseResult.transactions.filter { it.amount > 0.0 }
                    
                    if (validTxs.isNotEmpty()) {
                        val savedList = mutableListOf<com.example.data.Transaction>()
                        validTxs.forEach { parsed ->
                            // Match category hierarchy:
                            // 1. AI Classified Category: resolved/created via categoryRepo.getOrCreateByName()
                            // 2. Fallback to default "Lainnya" if category name is empty (should not happen)
                            val typeClass = if (parsed.type == "income") "INCOME" else "EXPENSE"
                            val matchedCat = if (parsed.category.isNotBlank()) categoryRepo.getOrCreateByName(parsed.category, typeClass) else allCats.find { it.slug == "lainnya" }

                            val tx = com.example.data.Transaction(
                                amount = parsed.amount,
                                description = parsed.description,
                                category = matchedCat?.name ?: parsed.category,
                                categoryId = matchedCat?.id,
                                type = if (parsed.type == "income") com.example.data.TransactionType.INCOME else com.example.data.TransactionType.EXPENSE,
                                timestamp = System.currentTimeMillis()
                            )
                            transactionRepo.insertTransaction(tx)
                            savedList.add(tx)
                        }
                        
                        // Direct Success state
                        processingStage = "success"
                        val totalStr = RupiahFormatter.format(validTxs.sumOf { it.amount })
                        val firstDesc = validTxs.first().description
                        successDetails = "Berhasil mencatat $totalStr untuk $firstDesc"
                        
                        delay(1500)
                        
                        isProcessing = false
                        processingStage = "none"
                        onDirectSave(savedList)
                    } else {
                        // AI parse returned empty/no valid transaction (likely system complaints or chats)
                        // Redirect to ManualInputScreen to let user check
                        isProcessing = false
                        processingStage = "none"
                        onClose(text)
                    }
                } else {
                    // Fallback to manual edit if AI parser is not initialized
                    isProcessing = false
                    processingStage = "none"
                    onClose(text)
                }
            } catch (e: Exception) {
                isProcessing = false
                processingStage = "none"
                error = "Gagal menyimpan: ${e.message}"
                // Wait briefly and navigate to Manual screen
                delay(1000)
                onClose(text)
            }
        }
    }

    // Auto-start recording as soon as permission is granted
    LaunchedEffect(hasMicPermission) {
        if (hasMicPermission && !isRecording && transcription == null && error == null) {
            audioRecorder.startRecording()
            isRecording = true
            startTime = System.currentTimeMillis()
            error = null
        }
    }

    // Auto-stop recording timer (max 7 seconds)
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val recordingStartTime = startTime // capture starting time reference
            while (isRecording) {
                val elapsed = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - recordingStartTime)
                elapsedSeconds = elapsed
                if (elapsed >= 7) {
                    // Auto-stop
                    isRecording = false
                    val audioFile = audioRecorder.stopRecording()
                    if (audioFile != null && whisperClient != null) {
                        isProcessing = true
                        processingStage = "whisper"
                        scope.launch {
                            val result = whisperClient.transcribe(audioFile)
                            result.fold(
                                onSuccess = { text ->
                                    transcription = text.text
                                    // Auto-start AI Parsing
                                    processTextToDirectSave(text.text)
                                },
                                onFailure = {
                                    isProcessing = false
                                    processingStage = "none"
                                    error = it.message ?: "Gagal transkripsi"
                                }
                            )
                            audioFile.delete()
                        }
                    } else {
                        isRecording = false
                        error = "Whisper API tidak tersedia"
                    }
                    break
                }
                delay(500)
            }
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (!granted) {
            error = "Izinkan akses mikrofon untuk menggunakan fitur ini"
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) audioRecorder.cancel()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightAbyss)
            .systemBarsPadding()
    ) {
        // Ambient glow
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.Center)
                .background(AmethystGlow.copy(alpha = 0.15f), shape = CircleShape)
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.Center)
                .offset(x = (-50).dp, y = (-50).dp)
                .background(SkyboundBlue.copy(alpha = 0.1f), shape = CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(GhostWhite.copy(alpha = 0.05f), CircleShape)
                        .border(1.dp, GhostWhite.copy(alpha = 0.15f), CircleShape)
                        .clickable {
                            if (isRecording) audioRecorder.cancel()
                            onClose(null)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = GhostWhite)
                }
                Text(
                    text = "HARTAKU AI",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                    color = GhostWhite.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Waveform
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(200.dp)
            ) {
                if (isRecording) {
                    repeat(7) { index ->
                        WaveBar(
                            height = (40 + index * 20).dp,
                            color = if (index % 2 == 0) AmethystGlow else SkyboundBlue,
                            delayMillis = (index + 1) * 100
                        )
                    }
                } else if (isProcessing) {
                    // Processing animation — pulsing
                    repeat(7) { index ->
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(100.dp)
                                .background(LimeSqueeze.copy(alpha = 0.3f), CircleShape)
                        )
                    }
                } else {
                    listOf(40, 80, 140, 180, 140, 80, 40).forEachIndexed { index, h ->
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(h.dp)
                                .background(
                                    if (index % 2 == 0) AmethystGlow.copy(alpha = 0.3f) else SkyboundBlue.copy(alpha = 0.3f),
                                    CircleShape
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Timer
            val minutes = elapsedSeconds / 60
            val seconds = elapsedSeconds % 60
            Text(
                text = String.format("%d:%02d / 0:07", minutes, seconds),
                style = MaterialTheme.typography.bodyMedium,
                color = GhostWhite.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status text
            Crossfade(
                targetState = when {
                    !hasMicPermission -> 0
                    isRecording -> 1
                    isProcessing -> 2
                    error != null -> 3
                    transcription != null -> 4
                    else -> 5
                },
                animationSpec = tween(150),
                label = "voiceStatus"
            ) { status ->
                when (status) {
                    0 -> {
                        Text("IZINKAN AKSES MIKROFON", style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp), color = SunsetOrange, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Ketuk tombol di bawah untuk mengizinkan.", style = MaterialTheme.typography.bodyLarge, color = GhostWhite.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                    }
                    1 -> {
                        Text("MEREKAM...", style = MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp), color = LimeSqueeze, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Bicara dengan jelas. Perekaman akan berhenti otomatis dalam 7 detik.", style = MaterialTheme.typography.bodyLarge, color = GhostWhite.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                    }
                    2 -> {
                        CircularProgressIndicator(color = LimeSqueeze, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val title = when (processingStage) {
                            "whisper" -> "MENTRANSKRIPSI..."
                            "nim" -> "MENGANALISIS AI..."
                            "success" -> "SUKSES!"
                            else -> "MEMPROSES..."
                        }
                        val subtitle = when (processingStage) {
                            "whisper" -> "Mengonversi rekaman suara Anda menjadi teks..."
                            "nim" -> "NVIDIA NIM sedang mengurai transaksi Anda..."
                            "success" -> successDetails
                            else -> "Mohon tunggu sebentar..."
                        }
                        
                        Text(title, style = MaterialTheme.typography.headlineMedium, color = LimeSqueeze, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = GhostWhite.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                    }
                    3 -> {
                        Text("ERROR", style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp), color = SunsetOrange, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(error ?: "", style = MaterialTheme.typography.bodyLarge, color = GhostWhite.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                    }
                    4 -> {
                        Text("TERDETEKSI", style = MaterialTheme.typography.headlineMedium, color = LimeSqueeze, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("\"$transcription\"", style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp), color = GhostWhite, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                    }
                    5 -> {
                        Text("SIAP", style = MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp), color = GhostWhite, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Mulai bicara, perekaman otomatis berjalan.", style = MaterialTheme.typography.bodyLarge, color = GhostWhite.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    // Processing / success state — show nothing or spinner
                    isProcessing -> {
                        // Keep spacing stable
                        Spacer(modifier = Modifier.height(96.dp))
                    }
                    // Transcription ready — Retry + Save
                    transcription != null -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(GhostWhite.copy(alpha = 0.1f), CircleShape)
                                .border(1.dp, GhostWhite.copy(alpha = 0.3f), CircleShape)
                                .clickable {
                                    transcription = null
                                    error = null
                                    elapsedSeconds = 0
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Retry", tint = GhostWhite, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(32.dp))
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(LimeSqueeze, CircleShape)
                                .clickable { onClose(transcription) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("SIMPAN", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MidnightAbyss)
                        }
                    }
                    // Recording — Stop button
                    isRecording -> {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(SunsetOrange, CircleShape)
                                .clickable {
                                    isRecording = false
                                    val audioFile = audioRecorder.stopRecording()
                                    if (audioFile != null && whisperClient != null) {
                                        isProcessing = true
                                        processingStage = "whisper"
                                        scope.launch {
                                            val result = whisperClient.transcribe(audioFile)
                                            result.fold(
                                                onSuccess = { text ->
                                                    transcription = text.text
                                                    processTextToDirectSave(text.text)
                                                },
                                                onFailure = {
                                                    isProcessing = false
                                                    processingStage = "none"
                                                    error = it.message ?: "Gagal transkripsi"
                                                }
                                            )
                                            audioFile.delete()
                                        }
                                    } else {
                                        error = "Whisper API tidak tersedia"
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(32.dp).background(MidnightAbyss, RoundedCornerShape(4.dp)))
                        }
                    }
                    // Default — Start mic button
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(LimeSqueeze, CircleShape)
                                .clickable {
                                    if (hasMicPermission) {
                                        audioRecorder.startRecording()
                                        isRecording = true
                                        startTime = System.currentTimeMillis()
                                        error = null
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Start", tint = MidnightAbyss, modifier = Modifier.size(48.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun WaveBar(height: androidx.compose.ui.unit.Dp, color: Color, delayMillis: Int) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = delayMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .width(12.dp)
            .height(height * scale)
            .background(color, CircleShape)
    )
}