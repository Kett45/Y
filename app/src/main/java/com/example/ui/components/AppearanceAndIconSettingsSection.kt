package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.KamAppThemePreset
import com.example.ui.theme.KamLauncherIconPreset
import com.example.ui.theme.LocalAppAppearanceController
import com.example.ui.theme.LocalUiScaleController
import com.example.ui.theme.UiScaleMode
import kotlin.math.roundToInt

/**
 * Entry Card displayed in SettingsScreen with a button that opens the dedicated "المظهر" (Appearance) screen.
 */
@Composable
fun AppearanceSettingsEntryCard(
    onOpenAppearanceScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appearanceController = LocalAppAppearanceController.current
    val uiScaleController = LocalUiScaleController.current
    val currentTheme = appearanceController.selectedTheme
    val currentPercent = uiScaleController.effectiveScalePercent

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAppearanceScreen)
            .testTag("appearance_settings_entry_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "المظهر والسمات",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "المظهر والسمات",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "السمة الحالية: ${currentTheme.titleArabic} • الحجم: $currentPercent%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = onOpenAppearanceScreen,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_appearance_screen_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "فتح صفحة المظهر والسمات",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Full dedicated "المظهر" (Appearance) screen opened from Settings:
 * 1. السمات (Themes): Only 2 themes — Dark (داكن) & High-Contrast Sunlight Light (فاتح للشمس).
 * 2. حجم الخط والواجهة + زر الإضافة العائم.
 * 3. أيقونة التطبيق المعتمدة (نجمة الكارت الذهبية).
 * All changes save locally in 0ms and sync automatically to the user's cloud account.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceFullScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBack()
    }

    val appearanceController = LocalAppAppearanceController.current
    val uiScaleController = LocalUiScaleController.current
    val selectedTheme = appearanceController.selectedTheme
    val currentPercent = uiScaleController.effectiveScalePercent
    val autoPercent = (uiScaleController.autoRecommendedScale * 100f).roundToInt()
    val deviceTypeLabel = if (uiScaleController.isCompactMobile) "هاتف موبايل" else "تابلت / شاشة كبيرة"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("appearance_full_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header with Back Button + App Name & Version
        item(key = "appearance_header") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("appearance_screen_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع للإعدادات",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("رجوع", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "المظهر • نجم الكارت v1.6",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "يتم حفظ تفضيلات المظهر في حسابك تلقائياً على أي هاتف",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Section 1: Themes (السمات — داكن وفاتح للشمس فقط)
        item(key = "appearance_themes_card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("theme_settings_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "السمات (داكن / فاتح للشمس)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "اختر السمة المناسبة لوقت الخدمة؛ السمة الفاتحة عالية التباين لقراءة واضحة جداً تحت أشعة الشمس:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider()

                    KamAppThemePreset.entries.forEach { preset ->
                        ThemeOptionRowCard(
                            preset = preset,
                            selected = selectedTheme == preset,
                            onClick = { appearanceController.selectTheme(preset, syncToCloud = true) }
                        )
                    }
                }
            }
        }

        // Section 2: UI & Font Scale + Floating Add Button
        item(key = "appearance_ui_scale_card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ui_scale_settings_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "حجم الخط والواجهة",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "الجهاز: $deviceTypeLabel • الوضع: ${if (uiScaleController.mode == UiScaleMode.AUTO) "تلقائي ذكي" else "تحكم يدوي"}",
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
                                text = "$currentPercent%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("ui_scale_percent_badge")
                            )
                        }
                    }

                    HorizontalDivider()

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isAuto = uiScaleController.mode == UiScaleMode.AUTO
                        AppearanceScalePresetChip(
                            label = "تلقائي ذكي ($autoPercent%)",
                            selected = isAuto,
                            onClick = { uiScaleController.setAutoMode(syncToCloud = true) },
                            testTag = "ui_scale_preset_auto"
                        )
                        AppearanceScalePresetChip(
                            label = "صغير للموبايل (85%)",
                            selected = !isAuto && currentPercent == 85,
                            onClick = { uiScaleController.setManualScale(0.85f, syncToCloud = true) },
                            testTag = "ui_scale_preset_compact"
                        )
                        AppearanceScalePresetChip(
                            label = "قياسي (100%)",
                            selected = !isAuto && currentPercent == 100,
                            onClick = { uiScaleController.setManualScale(1.00f, syncToCloud = true) },
                            testTag = "ui_scale_preset_standard"
                        )
                        AppearanceScalePresetChip(
                            label = "كبير للتابلت (115%)",
                            selected = !isAuto && currentPercent == 115,
                            onClick = { uiScaleController.setManualScale(1.15f, syncToCloud = true) },
                            testTag = "ui_scale_preset_tablet"
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "شريط الضبط اليدوي الدقيق (70% – 130%):",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val next = (uiScaleController.effectiveScaleFactor - 0.05f).coerceIn(0.70f, 1.30f)
                                        uiScaleController.setManualScale(next, syncToCloud = true)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .testTag("ui_scale_decrease_button")
                                ) {
                                    Text("- 5%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val next = (uiScaleController.effectiveScaleFactor + 0.05f).coerceIn(0.70f, 1.30f)
                                        uiScaleController.setManualScale(next, syncToCloud = true)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .testTag("ui_scale_increase_button")
                                ) {
                                    Text("+ 5%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Slider(
                            value = uiScaleController.effectiveScaleFactor,
                            onValueChange = { newValue ->
                                val stepped = (newValue * 20f).roundToInt() / 20f
                                uiScaleController.setManualScale(stepped, syncToCloud = true)
                            },
                            valueRange = 0.70f..1.30f,
                            steps = 11,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ui_scale_slider")
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            Text(
                                text = "إظهار زر (+ إضافة / استيراد الأسماء) العائم",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "زر عائم يظهر في الشاشة الرئيسية وينكمش تلقائياً عند التمرير، ويُحفظ تفضيله في حسابك.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = appearanceController.showFloatingAddButton,
                            onCheckedChange = { checked ->
                                appearanceController.updateShowFloatingAddButton(checked, syncToCloud = true)
                            },
                            modifier = Modifier.testTag("switch_show_floating_add_button")
                        )
                    }
                }
            }
        }

        // Section 3: App Icon (أيقونة النجمة الوحيدة المعتمدة)
        item(key = "appearance_star_icon_card") {
            val starPreset = KamLauncherIconPreset.BETHLEHEM_STAR
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("launcher_icon_settings_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "أيقونة التطبيق (نجم الكارت v1.6)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "أيقونة النجمة الذهبية المعتمدة على الشاشة الرئيسية للهاتف",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider()

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("icon_preset_${starPreset.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                StarLauncherIconPreviewBox(preset = starPreset)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "نجم الكارت v1.6",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = starPreset.subtitleArabic,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "مفعلة",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionRowCard(
    preset: KamAppThemePreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = Color(preset.previewBackgroundHex)
    val surfaceColor = Color(preset.previewSurfaceHex)
    val primaryColor = Color(preset.previewPrimaryHex)
    val secondaryColor = Color(preset.previewSecondaryHex)
    val previewTextColor = if (preset.isDark) Color(0xFFF8FAFC) else Color(0xFF090D16)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.70f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("theme_preset_${preset.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Visual Swatch of the Theme
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = bgColor,
                    border = BorderStroke(1.5.dp, primaryColor),
                    modifier = Modifier.size(width = 64.dp, height = 52.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(primaryColor)
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(secondaryColor)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = surfaceColor,
                            border = BorderStroke(0.8.dp, primaryColor.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (preset.isDark) "داكن" else "فاتح",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = previewTextColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.titleArabic,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = preset.subtitleArabic,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "السمة المختارة",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun StarLauncherIconPreviewBox(preset: KamLauncherIconPreset) {
    val startColor = Color(preset.previewBgStartHex)
    val endColor = Color(preset.previewBgEndHex)
    val accentColor = Color(preset.previewAccentHex)

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(startColor, endColor))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(38.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            drawCircle(
                color = accentColor.copy(alpha = 0.20f),
                radius = w * 0.44f,
                center = Offset(cx, cy)
            )

            val starPath = Path().apply {
                moveTo(cx, h * 0.04f)
                lineTo(w * 0.59f, h * 0.37f)
                lineTo(w * 0.96f, cy)
                lineTo(w * 0.59f, h * 0.63f)
                lineTo(cx, h * 0.96f)
                lineTo(w * 0.41f, h * 0.63f)
                lineTo(w * 0.04f, cy)
                lineTo(w * 0.41f, h * 0.37f)
                close()
            }
            drawPath(starPath, color = accentColor)
            drawCircle(color = Color.White, radius = w * 0.08f, center = Offset(cx, cy))
        }
    }
}

@Composable
private fun AppearanceScalePresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
