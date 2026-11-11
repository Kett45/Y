package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/**
 * Backward-compatible overload that extracts the packed 64-bit card counts for [studentId]
 * and delegates to the primitive-parameter [CompactCardButtonsRow].
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
        enabledCards = enabledCards,
        packedCardCounts = state.packedCardCountsForStudent(studentId),
        tagPrefix = tagPrefix,
        onCardClick = onCardClick
    )
}

/**
 * Ultra-fast, smart-skippable card buttons row displayed under each child:
 * - Accepts primitive [packedCardCounts] (`Long`, 8 bits per [CardType.ordinal]) instead of the whole screen state,
 *   allowing Compose to skip 149 out of 150 rows when a single student's card is tapped.
 * - Uses a single-pass [Row] instead of multi-pass `FlowRow` to eliminate layout thrashing during scrolling.
 */
@Composable
fun CompactCardButtonsRow(
    studentId: String,
    enabledCards: List<CardType>,
    packedCardCounts: Long,
    tagPrefix: String = "card_btn",
    onCardClick: (CardType) -> Unit
) {
    val isCompactMobile = LocalUiScaleController.current.isCompactMobile
    val buttonSize = if (isCompactMobile) 38.dp else 42.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until enabledCards.size) {
            val card = enabledCards[i]
            val cardColor = KamSemanticColors.cardColor(card)
            val countInScope = KamScreenState.unpackCardCount(packedCardCounts, card)
            val isAwarded = countInScope > 0
            val displayedPoints = if (countInScope > 1) {
                card.points * countInScope
            } else {
                card.points
            }

            Surface(
                shape = CardButtonCornerShape,
                color = if (isAwarded) {
                    cardColor
                } else {
                    cardColor.copy(alpha = 0.12f)
                },
                border = BorderStroke(
                    width = if (isAwarded) 2.dp else 1.2.dp,
                    color = if (isAwarded) cardColor else cardColor.copy(alpha = 0.65f)
                ),
                modifier = Modifier
                    .size(buttonSize)
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isAwarded) {
                        // Awarded state: symbol transforms into the card's points number
                        Text(
                            text = "$displayedPoints",
                            style = MaterialTheme.typography.titleMedium,
                            color = AwardedPointsTextColor,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    } else {
                        // Normal state: only the single distinct symbol
                        Text(
                            text = card.badgeSymbol,
                            style = MaterialTheme.typography.titleMedium,
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
}

