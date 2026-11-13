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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CardType
import com.example.ui.theme.KamSemanticColors
import com.example.ui.theme.LocalUiScaleController
import com.example.ui.viewmodel.KamScreenState

private val AwardedPointsTextColor = Color(0xFF090D16)
private val CardButtonCornerShape = RoundedCornerShape(11.dp)

private val AllCardTypesArray: Array<CardType> = CardType.entries.toTypedArray()
private val PreallocatedCardBaseColor: Array<Color> = Array(AllCardTypesArray.size) { idx ->
    KamSemanticColors.cardColor(AllCardTypesArray[idx])
}
private val PreallocatedCardUnawardedBg: Array<Color> = Array(AllCardTypesArray.size) { idx ->
    PreallocatedCardBaseColor[idx].copy(alpha = 0.12f)
}
private val PreallocatedAwardedBorder: Array<BorderStroke> = Array(AllCardTypesArray.size) { idx ->
    BorderStroke(2.dp, PreallocatedCardBaseColor[idx])
}
private val PreallocatedUnawardedBorder: Array<BorderStroke> = Array(AllCardTypesArray.size) { idx ->
    BorderStroke(1.2.dp, PreallocatedCardBaseColor[idx].copy(alpha = 0.65f))
}

/**
 * Backward-compatible overload that extracts the packed 64-bit card counts and 7-bit enabled mask
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
    tagPrefix: String = "card_btn",
    onCardClick: (CardType) -> Unit
) {
    CompactCardButtonsRow(
        studentId = studentId,
        enabledCardsMask = KamScreenState.packEnabledCardsMask(enabledCards),
        packedCardCounts = packedCardCounts,
        tagPrefix = tagPrefix,
        onCardClick = onCardClick
    )
}

/**
 * Ultra-fast, 100% Compose-skippable card buttons row displayed under each child:
 * - Accepts primitive [enabledCardsMask] (`Int`) and [packedCardCounts] (`Long`, 8 bits per [CardType.ordinal])
 *   so every parameter is primitive/stable, allowing Compose to skip 57 out of 58 rows when 1 student's card is tapped.
 * - Uses lightweight [Box] containers with pre-allocated static [BorderStroke] and [Color] tokens instead of
 *   7 Material3 `Surface` wrappers per row, eliminating `CompositionLocalProvider` overhead during fast scrolling.
 */
@Composable
fun CompactCardButtonsRow(
    studentId: String,
    enabledCardsMask: Int,
    packedCardCounts: Long,
    tagPrefix: String = "card_btn",
    onCardClick: (CardType) -> Unit
) {
    val isCompactMobile = LocalUiScaleController.current.isCompactMobile
    val buttonSize = if (isCompactMobile) 38.dp else 42.dp
    val typographyStyle = MaterialTheme.typography.titleMedium

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (ordinal in 0 until AllCardTypesArray.size) {
            if ((enabledCardsMask and (1 shl ordinal)) == 0) continue
            val card = AllCardTypesArray[ordinal]
            val cardColor = PreallocatedCardBaseColor[ordinal]
            val countInScope = KamScreenState.unpackCardCount(packedCardCounts, card)
            val isAwarded = countInScope > 0
            val displayedPoints = if (countInScope > 1) {
                card.points * countInScope
            } else {
                card.points
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(buttonSize)
                    .background(
                        color = if (isAwarded) cardColor else PreallocatedCardUnawardedBg[ordinal],
                        shape = CardButtonCornerShape
                    )
                    .border(
                        border = if (isAwarded) PreallocatedAwardedBorder[ordinal] else PreallocatedUnawardedBorder[ordinal],
                        shape = CardButtonCornerShape
                    )
                    .clickable { onCardClick(card) }
                    .semantics {
                        contentDescription = if (isAwarded) {
                            "${card.arabicTitle} مسجل $displayedPoints نقطة"
                        } else {
                            card.arabicTitle
                        }
                    }
                    .testTag("${tagPrefix}_${studentId}_${card.id}")
            ) {
                if (isAwarded) {
                    Text(
                        text = "$displayedPoints",
                        style = typographyStyle,
                        color = AwardedPointsTextColor,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                } else {
                    Text(
                        text = card.badgeSymbol,
                        style = typographyStyle,
                        color = cardColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

