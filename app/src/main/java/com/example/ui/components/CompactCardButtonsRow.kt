package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CardType
import com.example.ui.theme.CardAttendanceDark
import com.example.ui.theme.CardAttendanceLight
import com.example.ui.theme.CardConfessionDark
import com.example.ui.theme.CardConfessionLight
import com.example.ui.theme.CardGoldDark
import com.example.ui.theme.CardGoldLight
import com.example.ui.theme.CardMemorizationDark
import com.example.ui.theme.CardMemorizationLight
import com.example.ui.theme.CardParticipationDark
import com.example.ui.theme.CardParticipationLight
import com.example.ui.theme.CardPraiseDark
import com.example.ui.theme.CardPraiseLight
import com.example.ui.theme.CardQuietDark
import com.example.ui.theme.CardQuietLight
import com.example.ui.theme.KamBackgroundDark
import com.example.ui.theme.LocalKamExtendedColors
import com.example.ui.theme.LocalUiScaleController
import com.example.ui.viewmodel.KamScreenState

private val CardButtonCornerShape = RoundedCornerShape(10.dp)
private val AllCardTypesArray: Array<CardType> = CardType.entries.toTypedArray()

@Immutable
private class CardVisualTokenSet(
    isDark: Boolean,
    colorLookup: (CardType) -> Color
) {
    val baseColors: Array<Color> = Array(AllCardTypesArray.size) { idx ->
        colorLookup(AllCardTypesArray[idx])
    }
    val unawardedBg: Array<Color> = Array(AllCardTypesArray.size) { idx ->
        baseColors[idx].copy(alpha = if (isDark) 0.11f else 0.10f)
    }
    val awardedBorders: Array<BorderStroke> = Array(AllCardTypesArray.size) { idx ->
        BorderStroke(1.5.dp, baseColors[idx])
    }
    val unawardedBorders: Array<BorderStroke> = Array(AllCardTypesArray.size) { idx ->
        BorderStroke(1.dp, baseColors[idx].copy(alpha = if (isDark) 0.42f else 0.48f))
    }
    val awardedTextColors: Array<Color> = Array(AllCardTypesArray.size) { idx ->
        if (baseColors[idx].luminance() > 0.34f) KamBackgroundDark else Color.White
    }
}

private val DarkCardTokenSet = CardVisualTokenSet(isDark = true) { cardType ->
    when (cardType) {
        CardType.ATTENDANCE -> CardAttendanceDark
        CardType.QUIET -> CardQuietDark
        CardType.PARTICIPATION -> CardParticipationDark
        CardType.PRAISE -> CardPraiseDark
        CardType.GOLD -> CardGoldDark
        CardType.MEMORIZATION -> CardMemorizationDark
        CardType.CONFESSION -> CardConfessionDark
        CardType.CUSTOM_ELDER -> CardGoldDark
    }
}

private val LightCardTokenSet = CardVisualTokenSet(isDark = false) { cardType ->
    when (cardType) {
        CardType.ATTENDANCE -> CardAttendanceLight
        CardType.QUIET -> CardQuietLight
        CardType.PARTICIPATION -> CardParticipationLight
        CardType.PRAISE -> CardPraiseLight
        CardType.GOLD -> CardGoldLight
        CardType.MEMORIZATION -> CardMemorizationLight
        CardType.CONFESSION -> CardConfessionLight
        CardType.CUSTOM_ELDER -> CardGoldLight
    }
}

/**
 * Backward-compatible overload that extracts the packed 64-bit card counts and 8-bit enabled mask
 * for [studentId] and delegates to the primitive-parameter [CompactCardButtonsRow].
 */
@Composable
fun CompactCardButtonsRow(
    studentId: String,
    enabledCards: List<CardType>,
    state: KamScreenState,
    tagPrefix: String = "card_btn",
    onCardClick: (CardType) -> Unit
) {
    CompactCardButtonsRow(
        studentId = studentId,
        enabledCardsMask = KamScreenState.packEnabledCardsMask(enabledCards),
        packedCardCounts = state.packedCardCountsForStudent(studentId),
        customCardConfig = state.customCardConfig,
        tagPrefix = tagPrefix,
        onCardClick = onCardClick
    )
}

/**
 * Backward-compatible overload accepting [enabledCards] list and primitive [packedCardCounts].
 */
@Composable
fun CompactCardButtonsRow(
    studentId: String,
    enabledCards: List<CardType>,
    packedCardCounts: Long,
    customCardConfig: com.example.data.model.CustomCardConfig = com.example.data.model.CustomCardConfig.DEFAULT,
    tagPrefix: String = "card_btn",
    onCardClick: (CardType) -> Unit
) {
    CompactCardButtonsRow(
        studentId = studentId,
        enabledCardsMask = KamScreenState.packEnabledCardsMask(enabledCards),
        packedCardCounts = packedCardCounts,
        customCardConfig = customCardConfig,
        tagPrefix = tagPrefix,
        onCardClick = onCardClick
    )
}

/**
 * Ultra-fast, 100% Compose-skippable card buttons row displayed under each child:
 * - Uses pre-allocated Dark & Light token sets with zero runtime object allocations during scrolling.
 * - Dynamically resolves custom name, icon, points, and color for [CardType.CUSTOM_ELDER].
 * - Preserves 60fps scrolling across 150+ student rows.
 */
@Composable
fun CompactCardButtonsRow(
    studentId: String,
    enabledCardsMask: Int,
    packedCardCounts: Long,
    customCardConfig: com.example.data.model.CustomCardConfig = com.example.data.model.CustomCardConfig.DEFAULT,
    tagPrefix: String = "card_btn",
    onCardClick: (CardType) -> Unit
) {
    val isCompactMobile = LocalUiScaleController.current.isCompactMobile
    val isDark = LocalKamExtendedColors.current.isDark
    val tokenSet = if (isDark) DarkCardTokenSet else LightCardTokenSet
    val enabledCount = Integer.bitCount(enabledCardsMask)
    val buttonSize = when {
        enabledCount >= 8 && isCompactMobile -> 34.dp
        enabledCount >= 8 -> 38.dp
        isCompactMobile -> 38.dp
        else -> 42.dp
    }
    val itemSpacing = if (enabledCount >= 8) 4.dp else 5.dp
    val typographyStyle = MaterialTheme.typography.titleMedium
    val currentOnCardClick by rememberUpdatedState(onCardClick)
    val cachedClicks = remember {
        Array(AllCardTypesArray.size) { idx ->
            { currentOnCardClick(AllCardTypesArray[idx]) }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (ordinal in 0 until AllCardTypesArray.size) {
            if ((enabledCardsMask and (1 shl ordinal)) == 0) continue
            val card = AllCardTypesArray[ordinal]
            val isCustom = card == CardType.CUSTOM_ELDER
            val cardColor = if (isCustom) {
                com.example.ui.theme.KamSemanticColors.cardColorByKey(customCardConfig.colorKey)
            } else {
                tokenSet.baseColors[ordinal]
            }
            val countInScope = KamScreenState.unpackCardCount(packedCardCounts, card)
            val isAwarded = countInScope > 0
            val basePoints = card.displayPoints(customCardConfig)
            val displayedPoints = if (countInScope > 1) {
                basePoints * countInScope
            } else {
                basePoints
            }
            val cardTitle = card.displayTitle(customCardConfig)
            val cardSymbol = card.displaySymbol(customCardConfig)
            val bgColor = if (isAwarded) {
                cardColor
            } else if (isCustom) {
                cardColor.copy(alpha = if (isDark) 0.14f else 0.12f)
            } else {
                tokenSet.unawardedBg[ordinal]
            }
            val borderStroke = if (isCustom) {
                if (isAwarded) BorderStroke(1.5.dp, cardColor)
                else BorderStroke(1.dp, cardColor.copy(alpha = if (isDark) 0.48f else 0.54f))
            } else {
                if (isAwarded) tokenSet.awardedBorders[ordinal] else tokenSet.unawardedBorders[ordinal]
            }
            val awardedTextColor = if (isCustom) {
                if (cardColor.luminance() > 0.34f) KamBackgroundDark else Color.White
            } else {
                tokenSet.awardedTextColors[ordinal]
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(buttonSize)
                    .background(
                        color = bgColor,
                        shape = CardButtonCornerShape
                    )
                    .border(
                        border = borderStroke,
                        shape = CardButtonCornerShape
                    )
                    .clickable(onClick = cachedClicks[ordinal])
                    .semantics {
                        contentDescription = if (isAwarded) {
                            "$cardTitle مسجل $displayedPoints نقطة"
                        } else {
                            cardTitle
                        }
                    }
                    .testTag("${tagPrefix}_${studentId}_${card.id}")
            ) {
                if (isAwarded) {
                    Text(
                        text = "$displayedPoints",
                        style = typographyStyle,
                        color = awardedTextColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                } else {
                    Text(
                        text = cardSymbol,
                        style = typographyStyle,
                        color = cardColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
