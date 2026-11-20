package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.GroupsOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ChatFormatHelper
import com.example.ui.theme.LocalKamExtendedColors

@Immutable
data class KamNavDestination(
    val index: Int,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    val badgeCount: Int = 0
)

/**
 * Sleek, floating animated capsule bottom navigation bar designed specifically for RTL Arabic labels:
 * - Every tab shares the EXACT same fixed icon & label baseline so icons never jump unevenly.
 * - A spring-physics sliding capsule indicator glides smoothly between tabs with a subtle top glow bar.
 * - Unread messages badge is overlaid without shifting the icon center.
 * - Smoothly hides on downward scroll or inside active chat rooms, and reappears on upward scroll.
 */
@Composable
fun KamFloatingBottomNavBar(
    visible: Boolean,
    selectedIndex: Int,
    unreadMessagesCount: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val extColors = LocalKamExtendedColors.current

    val destinations = remember(unreadMessagesCount) {
        listOf(
            KamNavDestination(
                index = 0,
                labelResId = R.string.tab_home,
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                testTag = "nav_tab_home"
            ),
            KamNavDestination(
                index = 1,
                labelResId = R.string.tab_visitation,
                selectedIcon = Icons.Filled.Favorite,
                unselectedIcon = Icons.Outlined.FavoriteBorder,
                testTag = "nav_tab_visitation"
            ),
            KamNavDestination(
                index = 2,
                labelResId = R.string.tab_messages,
                selectedIcon = Icons.Filled.ChatBubble,
                unselectedIcon = Icons.Filled.ChatBubbleOutline,
                testTag = "nav_tab_messages",
                badgeCount = unreadMessagesCount
            ),
            KamNavDestination(
                index = 3,
                labelResId = R.string.tab_group,
                selectedIcon = Icons.Filled.Groups,
                unselectedIcon = Icons.Filled.GroupsOutline,
                testTag = "nav_tab_group"
            ),
            KamNavDestination(
                index = 4,
                labelResId = R.string.tab_settings,
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                testTag = "nav_tab_settings"
            )
        )
    }

    val safeSelectedIndex = selectedIndex.coerceIn(0, destinations.lastIndex)

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = 0.78f,
                stiffness = Spring.StiffnessMedium
            ),
            initialOffsetY = { fullHeight -> fullHeight + 96 }
        ) + fadeIn(animationSpec = tween(durationMillis = 190)),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = 220),
            targetOffsetY = { fullHeight -> fullHeight + 96 }
        ) + fadeOut(animationSpec = tween(durationMillis = 150)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val capsuleContainerColor = if (extColors.isDark) {
                extColors.elevatedSurface.copy(alpha = 0.96f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
            }
            val capsuleBorderColor = if (extColors.isDark) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
            }
            val activePillBrush = if (extColors.isDark) {
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    )
                )
            }

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = capsuleContainerColor,
                border = BorderStroke(1.dp, capsuleBorderColor),
                shadowElevation = if (extColors.isDark) 6.dp else 10.dp,
                tonalElevation = if (extColors.isDark) 3.dp else 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .height(66.dp)
                    .testTag("kam_floating_bottom_nav")
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                ) {
                    val slotCount = destinations.size
                    val slotWidth = maxWidth / slotCount
                    val indicatorStartOffset by animateDpAsState(
                        targetValue = slotWidth * safeSelectedIndex,
                        animationSpec = spring(
                            dampingRatio = 0.72f,
                            stiffness = 430f
                        ),
                        label = "nav_indicator_slide"
                    )

                    // 1. Sliding Active Capsule Pill + Subtle Top Glow Accent
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorStartOffset)
                            .width(slotWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(activePillBrush)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(
                                    alpha = if (extColors.isDark) 0.34f else 0.18f
                                ),
                                shape = RoundedCornerShape(22.dp)
                            )
                    ) {
                        // Top subtle illuminated bar centered inside the active pill
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 3.dp)
                                .width(20.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }

                    // 2. Five Perfectly Aligned Equal-Width Navigation Slots
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        destinations.forEach { dest ->
                            val isSelected = dest.index == safeSelectedIndex
                            val labelText = stringResource(dest.labelResId)
                            val badgeText = remember(dest.badgeCount) {
                                ChatFormatHelper.formatUnreadBadge(dest.badgeCount)
                            }

                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.12f else 0.96f,
                                animationSpec = spring(
                                    dampingRatio = 0.62f,
                                    stiffness = 460f
                                ),
                                label = "nav_icon_scale_${dest.index}"
                            )
                            val iconTint by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                animationSpec = tween(durationMillis = 180),
                                label = "nav_icon_tint_${dest.index}"
                            )
                            val labelTint by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    extColors.mutedText
                                },
                                animationSpec = tween(durationMillis = 180),
                                label = "nav_label_tint_${dest.index}"
                            )

                            val interactionSource = remember(dest.index) { MutableInteractionSource() }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(22.dp))
                                    .semantics { selected = isSelected }
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = ripple(
                                            bounded = true,
                                            color = MaterialTheme.colorScheme.primary
                                        ),
                                        role = Role.Tab,
                                        onClick = { onSelectIndex(dest.index) }
                                    )
                                    .testTag(dest.testTag),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                ) {
                                    // Fixed-dimension icon container guarantees every icon aligns on one baseline
                                    Box(
                                        modifier = Modifier.size(width = 34.dp, height = 26.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = labelText,
                                            tint = iconTint,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .graphicsLayer {
                                                    scaleX = iconScale
                                                    scaleY = iconScale
                                                }
                                        )

                                        if (badgeText.isNotBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 4.dp, y = (-3).dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.error)
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = capsuleContainerColor,
                                                        shape = CircleShape
                                                    )
                                                    .padding(horizontal = 4.5.dp, vertical = 1.dp)
                                                    .testTag("nav_messages_unread_badge"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = badgeText,
                                                    color = MaterialTheme.colorScheme.onError,
                                                    fontSize = 9.sp,
                                                    lineHeight = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = labelText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.5.sp,
                                            lineHeight = 13.sp
                                        ),
                                        color = labelTint,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
