package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AirDropCenterButton(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onMicClick: () -> Unit,
    onCameraClick: () -> Unit,
    onTextClick: () -> Unit
) {
    val scale = animateFloatAsState(targetValue = if (isExpanded) 1f else 0f, animationSpec = spring())
    val rotation = animateFloatAsState(targetValue = if (isExpanded) 45f else 0f, animationSpec = spring())

    Box(modifier = Modifier.fillMaxSize()) {
        if (isExpanded) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(MidnightAbyss.copy(alpha = 0.7f))
                .clickable { onToggle() }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp) // Offset above bottom navbar properly
        ) {
            // Options
            if (scale.value > 0.01f) {
                ActionFab(
                    icon = Icons.Default.Mic,
                    label = "Suara",
                    color = LimeSqueeze,
                    angle = 195.0,
                    radius = 90.0,
                    scale = scale.value,
                    onClick = { onMicClick() }
                )
                ActionFab(
                    icon = Icons.Default.CameraAlt,
                    label = "Kamera",
                    color = AmethystGlow,
                    angle = 270.0,
                    radius = 90.0,
                    scale = scale.value,
                    onClick = { onCameraClick() }
                )
                ActionFab(
                    icon = Icons.Default.Edit,
                    label = "Teks",
                    color = SkyboundBlue,
                    angle = 345.0,
                    radius = 90.0,
                    scale = scale.value,
                    onClick = { onTextClick() }
                )
            }

            // Pulse ring animation
            val infiniteTransition = rememberInfiniteTransition()
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                )
            )

            // Pulse ring (outer expanding circle)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(pulseScale)
                    .alpha(pulseAlpha)
                    .border(2.dp, LimeSqueeze.copy(alpha = 0.3f), CircleShape)
            )

            // Main FAB — gradient Lime → Emerald
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(LimeSqueeze, EmeraldSprint)
                        ),
                        CircleShape
                    )
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add",
                    tint = MidnightAbyss,
                    modifier = Modifier
                        .size(32.dp)
                        .scale(1f + (rotation.value / 45f)*0.2f)
                )
            }
        }
    }
}

@Composable
fun BoxScope.ActionFab(
    icon: ImageVector,
    label: String,
    color: Color,
    angle: Double,
    radius: Double,
    scale: Float,
    onClick: () -> Unit
) {
    val angleRad = Math.toRadians(angle)
    val xOffset = (cos(angleRad) * radius * scale).dp
    val yOffset = (sin(angleRad) * radius * scale).dp

    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .offset(x = xOffset, y = yOffset)
            .alpha(scale)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = MidnightAbyss)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = GhostWhite, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

