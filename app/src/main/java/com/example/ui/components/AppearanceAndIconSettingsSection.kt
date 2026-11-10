package com.example.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.KamAppThemePreset
import com.example.ui.theme.KamLauncherIconPreset
import com.example.ui.theme.LocalAppAppearanceController

@Composable
fun AppearanceAndIconSettingsSection() {
    val appearanceController = LocalAppAppearanceController.current
    val selectedTheme = appearanceController.selectedTheme
    val selectedIcon = appearanceController.selectedIcon

    val infiniteTransition = rememberInfiniteTransition(label = "preview_neon_pulse")
    val liveGlowColor1 by infiniteTransition.animateColor(
        initialValue = Color(0xFFFF2E93),
        targetValue = Color(0xFF00F5D4),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_1"
    )
    val liveGlowColor2 by infiniteTransition.animateColor(
        initialValue = Color(0xFFFFD60A),
        targetValue = Color(0xFF00F59B),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_2"
    )
    val liveScalePulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_pulse"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Interior App Themes Card (7 Static Themes + 2 Live Animated Glowing Neon Themes = 9 Designs)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_themes_settings_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "مظهر التطبيق من الداخل (٩ تصميمات)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "٧ تصميمات ثابتة + ٢ ثيم نيون حي متوهج وسريع الحركة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (selectedTheme.isLiveAnimated) "ثيم حي ✨" else "ثيم ثابت",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider()

                // First: Highlight the 2 Live Animated Neon Themes
                Text(
                    text = "🔥 الثيمات الحية المتحركة (ألوان نيون متوهجة وسريعة التغير):",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                val liveThemes = KamAppThemePreset.entries.filter { it.isLiveAnimated }
                liveThemes.forEach { preset ->
                    val isSelected = preset == selectedTheme
                    val dynamicAccent = if (preset == KamAppThemePreset.LIVE_CYBER_NEON) {
                        liveGlowColor1
                    } else {
                        liveGlowColor2
                    }

                    ThemePresetRowCard(
                        preset = preset,
                        isSelected = isSelected,
                        dynamicBorderColor = dynamicAccent,
                        onSelect = { appearanceController.selectTheme(preset) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Second: The 7 Static Themes
                Text(
                    text = "🎨 التصميمات الثابتة المتنوعة (٧ تصميمات):",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                val staticThemes = KamAppThemePreset.entries.filter { !it.isLiveAnimated }
                staticThemes.forEach { preset ->
                    val isSelected = preset == selectedTheme
                    ThemePresetRowCard(
                        preset = preset,
                        isSelected = isSelected,
                        dynamicBorderColor = null,
                        onSelect = { appearanceController.selectTheme(preset) }
                    )
                }
            }
        }

        // 2. External App Launcher Icons Card (5 Sunday School / Coptic Icons + 1 Live Neon Icon = 6 Icons)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_icons_settings_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "أيقونة التطبيق من الخارج (٦ أيقونات)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "٥ أيقونات برموز مدارس الأحد والتراث القبطي + ١ أيقونة نيون حية متوهجة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LauncherIconPreviewBadge(
                        preset = selectedIcon,
                        liveColor1 = liveGlowColor1,
                        liveColor2 = liveGlowColor2,
                        livePulse = liveScalePulse,
                        size = 44.dp
                    )
                }

                appearanceController.lastIconStatusMessage?.let { statusMsg ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("launcher_icon_status_banner")
                    ) {
                        Text(
                            text = statusMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                HorizontalDivider()

                KamLauncherIconPreset.entries.forEach { iconPreset ->
                    val isSelected = iconPreset == selectedIcon
                    val borderStrokeColor = when {
                        iconPreset.isLiveIcon -> liveGlowColor1
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        },
                        border = BorderStroke(
                            width = if (isSelected || iconPreset.isLiveIcon) 2.dp else 1.dp,
                            color = borderStrokeColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { appearanceController.selectLauncherIcon(iconPreset) }
                            .testTag("launcher_icon_preset_${iconPreset.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                LauncherIconPreviewBadge(
                                    preset = iconPreset,
                                    liveColor1 = liveGlowColor1,
                                    liveColor2 = liveGlowColor2,
                                    livePulse = liveScalePulse,
                                    size = 48.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = iconPreset.arabicTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = iconPreset.arabicSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isSelected) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "مفعلة",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "مفعّلة",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = "تفعيل",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePresetRowCard(
    preset: KamAppThemePreset,
    isSelected: Boolean,
    dynamicBorderColor: Color?,
    onSelect: () -> Unit
) {
    val borderColor = when {
        dynamicBorderColor != null -> dynamicBorderColor
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        border = BorderStroke(
            width = if (isSelected || preset.isLiveAnimated) 2.dp else 1.dp,
            color = borderColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("theme_preset_${preset.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 4-Color Palette Swatch Preview
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-6).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    preset.previewColors.forEachIndexed { idx, swatchColor ->
                        val displayColor = if (preset.isLiveAnimated && idx == 0 && dynamicBorderColor != null) {
                            dynamicBorderColor
                        } else {
                            swatchColor
                        }
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(displayColor)
                                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.arabicTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = preset.arabicSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isSelected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "مفعل",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "مفعّل",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = "اختيار",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Custom Canvas rendering of the 6 Sunday School & Coptic Launcher Icons
 * (including real-time neon pulse animation for Icon 6: LIVE_NEON_ICON).
 */
@Composable
fun LauncherIconPreviewBadge(
    preset: KamLauncherIconPreset,
    liveColor1: Color,
    liveColor2: Color,
    livePulse: Float,
    size: Dp
) {
    Canvas(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f

        val bgBrush = if (preset.isLiveIcon) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF19002E),
                    liveColor1.copy(alpha = 0.45f),
                    Color(0xFF002924)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(preset.bgTopColor, preset.bgBottomColor)
            )
        }

        drawRoundRect(
            brush = bgBrush,
            size = Size(w, h),
            cornerRadius = CornerRadius(w * 0.24f, h * 0.24f)
        )

        // Subtle inner halo
        drawCircle(
            color = (if (preset.isLiveIcon) liveColor1 else preset.primarySymbolColor).copy(alpha = 0.22f),
            radius = w * 0.36f * (if (preset.isLiveIcon) livePulse else 1f),
            center = Offset(cx, cy)
        )

        when (preset) {
            KamLauncherIconPreset.COPTIC_CROSS -> {
                val barThickness = w * 0.12f
                val armLen = w * 0.56f
                // Vertical & Horizontal arms of Coptic Cross
                drawRoundRect(
                    color = preset.primarySymbolColor,
                    topLeft = Offset(cx - barThickness / 2f, cy - armLen / 2f),
                    size = Size(barThickness, armLen),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRoundRect(
                    color = preset.primarySymbolColor,
                    topLeft = Offset(cx - armLen / 2f, cy - barThickness / 2f),
                    size = Size(armLen, barThickness),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                // Trefoil buds at the 4 ends
                val budR = w * 0.065f
                drawCircle(preset.accentSymbolColor, budR, Offset(cx, cy - armLen / 2f))
                drawCircle(preset.accentSymbolColor, budR, Offset(cx, cy + armLen / 2f))
                drawCircle(preset.accentSymbolColor, budR, Offset(cx - armLen / 2f, cy))
                drawCircle(preset.accentSymbolColor, budR, Offset(cx + armLen / 2f, cy))
                // Center jewel
                drawCircle(Color.White, w * 0.05f, Offset(cx, cy))
            }

            KamLauncherIconPreset.SACRED_CHALICE -> {
                // Host circle above chalice
                val hostY = h * 0.30f
                drawCircle(preset.accentSymbolColor, w * 0.13f, Offset(cx, hostY))
                // Cup body
                drawArc(
                    color = preset.primarySymbolColor,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(w * 0.26f, h * 0.34f),
                    size = Size(w * 0.48f, h * 0.36f)
                )
                // Stem & base
                drawRect(
                    color = preset.primarySymbolColor,
                    topLeft = Offset(cx - w * 0.04f, h * 0.65f),
                    size = Size(w * 0.08f, h * 0.14f)
                )
                drawRoundRect(
                    color = preset.accentSymbolColor,
                    topLeft = Offset(w * 0.30f, h * 0.77f),
                    size = Size(w * 0.40f, h * 0.07f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }

            KamLauncherIconPreset.ROYAL_CROWN -> {
                val crownPath = Path().apply {
                    moveTo(w * 0.22f, h * 0.68f)
                    lineTo(w * 0.18f, h * 0.38f)
                    lineTo(w * 0.38f, h * 0.52f)
                    lineTo(cx, h * 0.28f)
                    lineTo(w * 0.62f, h * 0.52f)
                    lineTo(w * 0.82f, h * 0.38f)
                    lineTo(w * 0.78f, h * 0.68f)
                    close()
                }
                drawPath(crownPath, color = preset.primarySymbolColor)
                drawRoundRect(
                    color = Color(0xFFFDE68A),
                    topLeft = Offset(w * 0.22f, h * 0.71f),
                    size = Size(w * 0.56f, h * 0.08f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawCircle(preset.accentSymbolColor, w * 0.05f, Offset(cx, h * 0.56f))
            }

            KamLauncherIconPreset.BETHLEHEM_STAR -> {
                val starPath = Path().apply {
                    moveTo(cx, h * 0.14f)
                    lineTo(cx + w * 0.09f, cy - h * 0.09f)
                    lineTo(w * 0.86f, cy)
                    lineTo(cx + w * 0.09f, cy + h * 0.09f)
                    lineTo(cx, h * 0.86f)
                    lineTo(cx - w * 0.09f, cy + h * 0.09f)
                    lineTo(w * 0.14f, cy)
                    lineTo(cx - w * 0.09f, cy - h * 0.09f)
                    close()
                }
                drawPath(starPath, color = preset.primarySymbolColor)
                drawCircle(preset.accentSymbolColor, w * 0.10f, Offset(cx, cy))
                drawCircle(Color.White, w * 0.05f, Offset(cx, cy))
            }

            KamLauncherIconPreset.HOLY_BIBLE -> {
                // Open Bible Pages
                drawRoundRect(
                    color = preset.primarySymbolColor,
                    topLeft = Offset(w * 0.20f, h * 0.40f),
                    size = Size(w * 0.28f, h * 0.36f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = preset.primarySymbolColor,
                    topLeft = Offset(w * 0.52f, h * 0.40f),
                    size = Size(w * 0.28f, h * 0.36f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                // Cross above Bible
                drawRect(
                    color = Color(0xFFF59E0B),
                    topLeft = Offset(cx - w * 0.04f, h * 0.18f),
                    size = Size(w * 0.08f, h * 0.26f)
                )
                drawRect(
                    color = Color(0xFFF59E0B),
                    topLeft = Offset(cx - w * 0.14f, h * 0.25f),
                    size = Size(w * 0.28f, h * 0.07f)
                )
                // Bookmark ribbon
                drawRect(
                    color = preset.accentSymbolColor,
                    topLeft = Offset(cx - w * 0.03f, h * 0.72f),
                    size = Size(w * 0.06f, h * 0.14f)
                )
            }

            KamLauncherIconPreset.LIVE_NEON_ICON -> {
                // Live pulsing neon ring + cross
                drawCircle(
                    color = liveColor2.copy(alpha = 0.45f),
                    radius = w * 0.28f * livePulse,
                    center = Offset(cx, cy)
                )
                val barThickness = w * 0.13f
                val armLen = w * 0.58f * livePulse
                drawRoundRect(
                    color = liveColor1,
                    topLeft = Offset(cx - barThickness / 2f, cy - armLen / 2f),
                    size = Size(barThickness, armLen),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = liveColor2,
                    topLeft = Offset(cx - armLen / 2f, cy - barThickness / 2f),
                    size = Size(armLen, barThickness),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawCircle(Color.White, w * 0.07f, Offset(cx, cy))
            }
        }
    }
}
