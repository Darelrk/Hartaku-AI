package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

class NavbarShape(
    private val cornerRadius: Dp = 24.dp,
    private val notchWidth: Dp = 56.dp,
    private val notchDepth: Dp = 20.dp,
    private val notchBottomWidth: Dp = 40.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val (cr, nw, nd, nbw) = with(density) {
            listOf(cornerRadius.toPx(), notchWidth.toPx(), notchDepth.toPx(), notchBottomWidth.toPx())
        }
        val cx = size.width / 2f
        val w = size.width
        val h = size.height

        return Outline.Generic(Path().apply {
            // Start at left edge, curve up to top
            moveTo(0f, cr)
            arcTo(Rect(0f, 0f, cr * 2, cr * 2), 180f, 90f, false)
            // Top edge to notch start
            lineTo(cx - nw / 2, 0f)
            // Left notch curve (downward, widening into teardrop bulb)
            cubicTo(cx - nw / 2, nd * 0.4f, cx - nbw / 2, nd, cx, nd)
            // Right notch curve (upward, narrowing back to opening)
            cubicTo(cx + nbw / 2, nd, cx + nw / 2, nd * 0.4f, cx + nw / 2, 0f)
            // Top edge to right corner
            lineTo(w - cr, 0f)
            arcTo(Rect(w - cr * 2, 0f, w, cr * 2), 270f, 90f, false)
            // Right edge, bottom edge, close
            lineTo(w, h)
            lineTo(0f, h)
            close()
        })
    }
}

@Composable
fun BottomNavBar(
    currentTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val navbarShape = NavbarShape()

    Box(
        modifier = Modifier
            .navigationBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(64.dp)
            .shadow(
                elevation = 8.dp,
                shape = navbarShape,
                clip = true,
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .background(
                color = MidnightAbyss.copy(alpha = 0.95f),
                shape = navbarShape
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                NavItem(icon = Icons.Default.Home, label = "Home", isSelected = currentTab == 0, onClick = { onTabSelected(0) })
            }
            Box(Modifier.weight(1f))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                NavItem(icon = Icons.Default.Person, label = "Profile", isSelected = currentTab == 1, onClick = { onTabSelected(1) })
            }
        }
    }
}

@Composable
fun NavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconColor = if (isSelected) GhostWhite else GhostWhite.copy(alpha = 0.4f)
    val labelColor = if (isSelected) GhostWhite else GhostWhite.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .drawBehind {
                if (isSelected) {
                    drawRoundRect(
                        color = LimeSqueeze,
                        topLeft = Offset.Zero,
                        size = Size(size.width, 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = labelColor
            )
        }
    }
}
