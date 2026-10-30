package com.taandava.manager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Color Palette
val GlassBgDeep = Color(0xFF070B14)
val GlassBgNavy = Color(0xFF0F172A)
val GlassCardSurface = Color(0xC0131E33)
val GlassCardBorder = Color(0x30FFFFFF)
val GlassCardBorderFocus = Color(0x7038BDF8)

val GlassCyan = Color(0xFF38BDF8)
val GlassCyanGlow = Color(0x4038BDF8)
val GlassPurple = Color(0xFFA855F7)
val GlassMint = Color(0xFF10B981)
val GlassAmber = Color(0xFFF59E0B)
val GlassCoral = Color(0xFFF43F5E)
val GlassIndigo = Color(0xFF6366F1)
val GlassGold = Color(0xFFEAB308)
val GlassSlate = Color(0xFF64748B)

val TextPrimary = Color.White
val TextSecondary = Color(0xFFCBD5E1)
val TextMuted = Color(0xFF94A3B8)

@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        GlassBgDeep,
                        Color(0xFF0B132B),
                        GlassBgNavy
                    )
                )
            )
    ) {
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderColor: Color = GlassCardBorder,
    backgroundColor: Color = GlassCardSurface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        modifier.clip(shape).clickable { onClick() }
    } else {
        modifier.clip(shape)
    }

    Box(
        modifier = clickableModifier
            .background(backgroundColor, shape)
            .border(BorderStroke(1.dp, borderColor), shape)
    ) {
        Column {
            // Specular top highlight line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0x10FFFFFF),
                                GlassCyanGlow,
                                Color(0x10FFFFFF)
                            )
                        )
                    )
            )
            content()
        }
    }
}

@Composable
fun GlassBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = GlassCyan,
    showDot: Boolean = true
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.5f)), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
            Text(
                text = text,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BentoActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        tint.copy(alpha = 0.16f),
                        Color(0x40131E33)
                    )
                )
            )
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.42f)), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun GlassPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(38.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = GlassCyan,
            contentColor = Color(0xFF070B14)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GlassSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(38.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = TextPrimary
        ),
        border = BorderStroke(1.dp, GlassSlate.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GlassDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(38.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = GlassCoral.copy(alpha = 0.25f),
            contentColor = GlassCoral
        ),
        border = BorderStroke(1.dp, GlassCoral.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = {
            Text(
                placeholder,
                color = TextMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0x60131E33),
            unfocusedContainerColor = Color(0x40131E33),
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = GlassCardBorderFocus,
            unfocusedBorderColor = GlassSlate.copy(alpha = 0.4f),
            cursorColor = GlassCyan
        )
    )
}
