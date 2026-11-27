package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.BlockColorPreset
import com.example.data.model.CardType
import com.example.data.model.NotificationCategory

val LocalKamExtendedColors = staticCompositionLocalOf<KamExtendedColors> {
    buildDarkExtendedColors()
}

val KamShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

object KamSemanticColors {
    var activeExtended: KamExtendedColors by mutableStateOf(buildDarkExtendedColors())
        internal set

    var elevatedSurface: Color by mutableStateOf(KamSurfaceElevatedDark)
        internal set

    val isDark: Boolean
        get() = activeExtended.isDark

    val onlineIndicator: Color
        get() = activeExtended.onlineGreen

    val offlineIndicator: Color
        get() = activeExtended.offlineAmber

    val hairline: Color
        get() = activeExtended.hairline

    val hairlineStrong: Color
        get() = activeExtended.hairlineStrong

    val unassignedBlock: Color
        get() = activeExtended.unassignedBlock

    val mutedText: Color
        get() = activeExtended.mutedText

    val rankGold: Color
        get() = activeExtended.rankGold

    val rankSilver: Color
        get() = activeExtended.rankSilver

    val rankBronze: Color
        get() = activeExtended.rankBronze

    fun blockColor(hex: String): Color {
        val ext = activeExtended
        val clean = hex.trim()
        val matchedPreset = BlockColorPreset.entries.find { it.hex.equals(clean, ignoreCase = true) }
        if (matchedPreset != null) {
            return when (matchedPreset) {
                BlockColorPreset.BLUE -> ext.blockBlue
                BlockColorPreset.EMERALD -> ext.blockEmerald
                BlockColorPreset.VIOLET -> ext.blockViolet
                BlockColorPreset.AMBER -> ext.blockAmber
                BlockColorPreset.ROSE -> ext.blockRose
                BlockColorPreset.CYAN -> ext.blockCyan
                BlockColorPreset.ORANGE -> ext.blockOrange
            }
        }
        val parsed = runCatching {
            val normalized = if (clean.startsWith("#")) clean else "#$clean"
            android.graphics.Color.parseColor(normalized)
        }.getOrNull() ?: return ext.blockBlue

        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(parsed, hsv)
        if (hsv[1] > 0.08f) {
            hsv[1] = hsv[1].coerceAtLeast(0.82f)
        }
        hsv[2] = if (ext.isDark) {
            hsv[2].coerceAtLeast(0.92f)
        } else {
            hsv[2].coerceIn(0.68f, 0.84f)
        }
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    fun cardColorByKey(colorKey: String): Color {
        val ext = activeExtended
        val trimmed = colorKey.trim()
        if (trimmed.startsWith("#")) {
            return blockColor(trimmed)
        }
        return when (trimmed.uppercase()) {
            "EMERALD" -> ext.cardAttendance
            "SKY" -> ext.cardQuiet
            "VIOLET" -> ext.cardParticipation
            "INDIGO" -> ext.cardPraise
            "GOLD" -> ext.cardGold
            "TEAL" -> ext.cardMemorization
            "ROSE" -> ext.cardConfession
            "ORANGE" -> ext.blockOrange
            else -> if (trimmed.length == 6 && trimmed.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                blockColor("#$trimmed")
            } else {
                ext.cardGold
            }
        }
    }

    fun cardColorKeyToHex(colorKey: String): String {
        val trimmed = colorKey.trim()
        if (trimmed.startsWith("#") && trimmed.length == 7) return trimmed.uppercase()
        return when (trimmed.uppercase()) {
            "EMERALD" -> "#10B981"
            "SKY" -> "#0EA5E9"
            "VIOLET" -> "#8B5CF6"
            "INDIGO" -> "#6366F1"
            "GOLD" -> "#F59E0B"
            "TEAL" -> "#14B8A6"
            "ROSE" -> "#F43F5E"
            "ORANGE" -> "#F97316"
            else -> "#F59E0B"
        }
    }

    fun cardColor(cardType: CardType, customConfig: com.example.data.model.CustomCardConfig? = null): Color {
        val ext = activeExtended
        return when (cardType) {
            CardType.ATTENDANCE -> ext.cardAttendance
            CardType.QUIET -> ext.cardQuiet
            CardType.PARTICIPATION -> ext.cardParticipation
            CardType.PRAISE -> ext.cardPraise
            CardType.GOLD -> ext.cardGold
            CardType.MEMORIZATION -> ext.cardMemorization
            CardType.CONFESSION -> ext.cardConfession
            CardType.CUSTOM_ELDER -> cardColorByKey(customConfig?.colorKey ?: cardType.colorKey)
        }
    }

    val NotificationCardConflict: Color
        get() = activeExtended.notifyConflict
    val NotificationAbsentIssue: Color
        get() = activeExtended.notifyAbsent
    val NotificationChildComment: Color
        get() = activeExtended.notifyComment
    val NotificationVisitation: Color
        get() = activeExtended.notifyVisitation
    val NotificationMemorization: Color
        get() = activeExtended.notifyMemorization
    val NotificationCardPositive: Color
        get() = activeExtended.notifyCardPositive
    val NotificationGeneralSystem: Color
        get() = activeExtended.notifyGeneralSystem

    fun notificationColor(category: NotificationCategory): Color {
        val ext = activeExtended
        return when (category) {
            NotificationCategory.CARD_CONFLICT -> ext.notifyConflict
            NotificationCategory.ABSENT_ISSUE -> ext.notifyAbsent
            NotificationCategory.CHILD_COMMENT -> ext.notifyComment
            NotificationCategory.VISITATION -> ext.notifyVisitation
            NotificationCategory.MEMORIZATION -> ext.notifyMemorization
            NotificationCategory.CARD_POSITIVE -> ext.notifyCardPositive
            NotificationCategory.GENERAL_SYSTEM -> ext.notifyGeneralSystem
        }
    }
}

@Composable
fun KamLiteTheme(
    content: @Composable () -> Unit
) {
    val (uiScaleController, effectiveDensity) = rememberUiScaleController()
    val appearanceController = rememberAppAppearanceController()
    val resolvedAppearance = rememberResolvedKamAppearance(appearanceController.selectedTheme)
    val baseViewConfig = LocalViewConfiguration.current
    val scaleFactor = uiScaleController.effectiveScaleFactor
    val scrollAlignedViewConfig = remember(baseViewConfig, scaleFactor) {
        val touchSlopMultiplier = scaleFactor.coerceIn(0.78f, 1.0f)
        object : ViewConfiguration by baseViewConfig {
            override val touchSlop: Float
                get() = baseViewConfig.touchSlop * touchSlopMultiplier
        }
    }

    SideEffect {
        KamSemanticColors.elevatedSurface = resolvedAppearance.elevatedSurfaceColor
        KamSemanticColors.activeExtended = resolvedAppearance.extendedColors
    }

    // Enforce RTL and provide adaptive/manual UI density, theme/icon controller, extended semantic colors, and scale-aligned touch physics at root
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalUiScaleController provides uiScaleController,
        LocalAppAppearanceController provides appearanceController,
        LocalKamExtendedColors provides resolvedAppearance.extendedColors,
        LocalDensity provides effectiveDensity,
        LocalViewConfiguration provides scrollAlignedViewConfig
    ) {
        MaterialTheme(
            colorScheme = resolvedAppearance.colorScheme,
            typography = Typography,
            shapes = KamShapes,
            content = content
        )
    }
}
