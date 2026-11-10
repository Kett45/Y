package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.model.BlockColorPreset
import com.example.data.model.CardType

object KamSemanticColors {
    val onlineIndicator: Color = KamOnlineGreen
    val offlineIndicator: Color = KamOfflineAmber
    var elevatedSurface: Color by mutableStateOf(KamSurfaceElevatedDark)
        internal set
    val unassignedBlock: Color = BlockUnassigned
    val mutedText: Color = KamTextMuted

    fun blockColor(hex: String): Color {
        return when (BlockColorPreset.fromHex(hex)) {
            BlockColorPreset.BLUE -> BlockBlue
            BlockColorPreset.EMERALD -> BlockEmerald
            BlockColorPreset.VIOLET -> BlockViolet
            BlockColorPreset.AMBER -> BlockAmber
            BlockColorPreset.ROSE -> BlockRose
            BlockColorPreset.CYAN -> BlockCyan
            BlockColorPreset.ORANGE -> BlockOrange
        }
    }

    fun cardColor(cardType: CardType): Color {
        return when (cardType) {
            CardType.ATTENDANCE -> CardAttendanceColor
            CardType.QUIET -> CardQuietColor
            CardType.PARTICIPATION -> CardParticipationColor
            CardType.PRAISE -> CardPraiseColor
            CardType.GOLD -> CardGoldColor
            CardType.MEMORIZATION -> CardMemorizationColor
            CardType.CONFESSION -> CardConfessionColor
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

    SideEffect {
        KamSemanticColors.elevatedSurface = resolvedAppearance.elevatedSurfaceColor
    }

    // Enforce RTL and provide adaptive/manual UI density and theme/icon controller across the entire application
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalUiScaleController provides uiScaleController,
        LocalAppAppearanceController provides appearanceController,
        LocalDensity provides effectiveDensity
    ) {
        MaterialTheme(
            colorScheme = resolvedAppearance.colorScheme,
            typography = Typography,
            content = content
        )
    }
}
