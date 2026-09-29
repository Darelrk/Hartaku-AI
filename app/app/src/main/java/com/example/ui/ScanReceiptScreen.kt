package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.AppContainer
import com.example.data.ReceiptItem
import com.example.data.ReceiptScanResult
import com.example.ui.theme.*
import com.example.RupiahFormatter
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "ScanReceiptScreen"

@Composable
fun ScanReceiptScreen(onClose: (ReceiptScanResult?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val container = remember { AppContainer.getInstance(context) }
    val receiptParser = remember { container.receiptParser }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var isProcessing by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<ReceiptScanResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) error = "Izinkan akses kamera untuk scan receipt"
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Cleanup on dispose
    // Keeps camera provider alive and properly cleaned up
    val cameraProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraProvider.value?.unbindAll()
            cameraProvider.value = null
            capturedBitmap?.recycle()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightAbyss)
            .systemBarsPadding()
    ) {
        if (capturedBitmap != null) {
            // === Captured — show processing or result ===
            Box(
                modifier = Modifier.fillMaxSize().background(MidnightAbyss)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(GhostWhite.copy(alpha = 0.1f), CircleShape)
                                .border(1.dp, GhostWhite.copy(alpha = 0.15f), CircleShape)
                                .clickable {
                                    capturedBitmap = null
                                    scanResult = null
                                    error = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = GhostWhite)
                        }
                        Text(
                            text = if (scanResult != null) "HASIL SCAN" else "MEMPROSES",
                            style = MaterialTheme.typography.headlineMedium,
                            color = GhostWhite
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(GhostWhite.copy(alpha = 0.1f), CircleShape)
                                .border(1.dp, GhostWhite.copy(alpha = 0.15f), CircleShape)
                                .clickable { onClose(null) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = GhostWhite)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Crossfade(
                        targetState = when {
                            isProcessing -> 0
                            scanResult != null -> 1
                            error != null -> 2
                            else -> 3
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        animationSpec = tween(150),
                        label = "receiptStatus"
                    ) { status ->
                        Column(modifier = Modifier.fillMaxSize()) {
                            when (status) {
                        0 -> {
                            Spacer(modifier = Modifier.weight(1f))
                            CircularProgressIndicator(color = LimeSqueeze, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("MEMPROSES...", style = MaterialTheme.typography.headlineMedium, color = LimeSqueeze)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("AI Vision sedang membaca struk", style = MaterialTheme.typography.bodyLarge, color = GhostWhite.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        1 -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(DarkSurface, RoundedCornerShape(20.dp))
                                    .border(1.dp, GhostWhite.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                    .padding(20.dp)
                            ) {
                                Column(Modifier.fillMaxSize()) {
                                    Text(scanResult!!.summaryText, style = MaterialTheme.typography.bodyMedium, color = GhostWhite)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .background(GhostWhite.copy(alpha = 0.1f), RoundedCornerShape(1000.dp))
                                        .border(1.dp, GhostWhite.copy(alpha = 0.3f), RoundedCornerShape(1000.dp))
                                        .clickable {
                                            capturedBitmap = null
                                            scanResult = null
                                            error = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("SCAN ULANG", color = GhostWhite)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .background(LimeSqueeze, RoundedCornerShape(1000.dp))
                                        .clickable { onClose(scanResult) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("LANJUT", color = MidnightAbyss, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        2 -> {
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Warning, contentDescription = null, tint = SunsetOrange, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("GAGAL SCAN", style = MaterialTheme.typography.headlineMedium, color = SunsetOrange)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                error ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GhostWhite.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                            Spacer(modifier = Modifier.height(32.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .background(GhostWhite.copy(alpha = 0.1f), RoundedCornerShape(1000.dp))
                                        .border(1.dp, GhostWhite.copy(alpha = 0.3f), RoundedCornerShape(1000.dp))
                                        .clickable {
                                            capturedBitmap = null
                                            error = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("COBA LAGI", color = GhostWhite)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .background(LimeSqueeze, RoundedCornerShape(1000.dp))
                                        .clickable { onClose(null) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("INPUT MANUAL", color = MidnightAbyss, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                        }
                    }
                }
            }
        } else if (hasCameraPermission) {
            // === Camera preview ===
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { previewView ->
                            if (cameraProvider.value == null) {
                                val future = ProcessCameraProvider.getInstance(ctx)
                                future.addListener({
                                    val provider = future.get()
                                    cameraProvider.value = provider
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    try {
                                        provider.unbindAll()
                                        provider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageCapture
                                        )
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Camera bind failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                            } else {
                                val provider = cameraProvider.value!!
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                try {
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageCapture
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Camera bind failed", e)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(GhostWhite.copy(alpha = 0.1f), CircleShape)
                                .border(1.dp, GhostWhite.copy(alpha = 0.15f), CircleShape)
                                .clickable { onClose(null) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = GhostWhite)
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(GhostWhite.copy(alpha = 0.1f), CircleShape)
                                .border(1.dp, GhostWhite.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FlashAuto, contentDescription = "Flash", tint = GhostWhite)
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Viewfinder frame
                    Box(modifier = Modifier.width(300.dp).height(400.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(GhostWhite.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .border(1.dp, GhostWhite.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        )
                        listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEach { align ->
                            Box(modifier = Modifier.align(align).size(40.dp).drawBehind {
                                val s = 25.dp.toPx()
                                val w = 3.dp.toPx()
                                when (align) {
                                    Alignment.TopStart -> { drawLine(LimeSqueeze, Offset.Zero, Offset(s, 0f), w); drawLine(LimeSqueeze, Offset.Zero, Offset(0f, s), w) }
                                    Alignment.TopEnd -> { drawLine(LimeSqueeze, Offset(size.width, 0f), Offset(size.width - s, 0f), w); drawLine(LimeSqueeze, Offset(size.width, 0f), Offset(size.width, s), w) }
                                    Alignment.BottomStart -> { drawLine(LimeSqueeze, Offset(0f, size.height), Offset(s, size.height), w); drawLine(LimeSqueeze, Offset(0f, size.height), Offset(0f, size.height - s), w) }
                                    Alignment.BottomEnd -> { drawLine(LimeSqueeze, Offset(size.width, size.height), Offset(size.width - s, size.height), w); drawLine(LimeSqueeze, Offset(size.width, size.height), Offset(size.width, size.height - s), w) }
                                }
                            })
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("ARAHKAN KE STRUK", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp), color = GhostWhite, textAlign = TextAlign.Center)

                    Spacer(modifier = Modifier.weight(1f))

                    // Capture button
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .border(3.dp, GhostWhite, CircleShape)
                            .padding(6.dp)
                            .clickable {
                                val photoFile = File(context.cacheDir, "receipt_${System.currentTimeMillis()}.jpg")
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                imageCapture.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                            capturedBitmap = bitmap
                                            photoFile.delete()

                                            if (receiptParser != null && bitmap != null) {
                                                isProcessing = true
                                                scope.launch {
                                                    val resultData = receiptParser.parse(bitmap)
                                                    isProcessing = false
                                                    resultData.fold(
                                                        onSuccess = { receipt ->
                            val autoText = "${receipt.store} ${RupiahFormatter.format(receipt.total)}"
                                                            val summary = buildString {
                                                                appendLine("Store: ${receipt.store}")
                                                                appendLine("Total: ${RupiahFormatter.format(receipt.total)}")
                                                                if (receipt.items.isNotEmpty()) {
                                                                    appendLine()
                                                                    appendLine("Items:")
                                                                    receipt.items.forEach { item ->
                                                                        appendLine("  • ${item.name} (${item.quantity}x) — ${RupiahFormatter.format(item.price)}")
                                                                    }
                                                                }
                                                                if (receipt.date != null) appendLine("\nTanggal: ${receipt.date}")
                                                            }
                                                            scanResult = ReceiptScanResult(
                                                                store = receipt.store,
                                                                total = receipt.total,
                                                                items = receipt.items.map { ReceiptItem(it.name, it.price, it.quantity) },
                                                                date = receipt.date,
                                                                autoFillText = autoText,
                                                                summaryText = summary
                                                            )
                                                        },
                                                        onFailure = { error = it.message ?: "Gagal memproses receipt" }
                                                    )
                                                }
                                            } else {
                                                error = "Receipt parser tidak tersedia"
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            Log.e(TAG, "Capture failed", exception)
                                            error = "Gagal mengambil foto: ${exception.message}"
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.fillMaxSize().background(GhostWhite, CircleShape))
                    }
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        } else {
            // === No permission ===
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("IZINKAN AKSES KAMERA", style = MaterialTheme.typography.headlineMedium, color = SunsetOrange)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Ketuk tombol di bawah untuk mengizinkan.", style = MaterialTheme.typography.bodyLarge, color = GhostWhite.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(32.dp))
                Box(
                    modifier = Modifier.size(80.dp).background(LimeSqueeze, CircleShape).clickable { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Grant permission", tint = MidnightAbyss, modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}