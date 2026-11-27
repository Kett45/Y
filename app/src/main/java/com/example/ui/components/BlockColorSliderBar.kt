package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.KamSemanticColors
import kotlin.math.roundToInt

/**
 * Full-spectrum vibrant block color slider bar.
 * Dragging or tapping anywhere along the bar smoothly chooses any hue across the 360° rainbow
 * and emits a rich, vivid #RRGGBB hex code for the block.
 */
@Composable
fun BlockColorSliderBar(
    selectedColorHex: String,
    onColorHexSelected: (String) -> Unit,
    previewBlockName: String = "",
    modifier: Modifier = Modifier,
    testTag: String = "block_color_slider_bar"
) {
    val initialHue = remember(selectedColorHex) { hexToHueDegrees(selectedColorHex) }
    var currentHue by remember(selectedColorHex) { mutableFloatStateOf(initialHue) }
    var trackWidthPx by remember { mutableStateOf(0f) }
    val currentOnColorSelected by rememberUpdatedState(onColorHexSelected)

    val spectrumColors = remember {
        listOf(
            Color(0xFFFF1744), // 0° Vivid Red
            Color(0xFFFF6D00), // 30° Vivid Orange
            Color(0xFFFFC400), // 55° Vivid Amber
            Color(0xFF76FF03), // 95° Vivid Lime
            Color(0xFF00E676), // 140° Vivid Emerald
            Color(0xFF00E5FF), // 185° Vivid Cyan
            Color(0xFF2979FF), // 220° Vivid Electric Blue
            Color(0xFF651FFF), // 260° Vivid Deep Indigo/Violet
            Color(0xFFD500F9), // 290° Vivid Purple
            Color(0xFFFF2E7E), // 330° Vivid Rose/Magenta
            Color(0xFFFF1744)  // 360° Vivid Red
        )
    }

    val activeHex = remember(currentHue, selectedColorHex) {
        hueDegreesToVividHex(currentHue)
    }
    val previewColor = KamSemanticColors.blockColor(
        if (selectedColorHex.isNotBlank()) selectedColorHex else activeHex
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Live block color preview badge
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = previewColor.copy(alpha = if (KamSemanticColors.isDark) 0.22f else 0.14f),
            border = BorderStroke(1.5.dp, previewColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (previewBlockName.isNotBlank()) {
                            "معاينة: بلوك ${previewBlockName.trim()}"
                        } else {
                            "معاينة لون البلوك المختار"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "اسحب لاختيار اللون",
                    style = MaterialTheme.typography.labelSmall,
                    color = previewColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Interactive Rainbow Hue Slider Track + Draggable Circular Thumb
        val thumbSizeDp = 30.dp
        val trackHeightDp = 24.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val fraction = (offset.x / width).coerceIn(0f, 1f)
                        val newHue = fraction * 360f
                        currentHue = newHue
                        currentOnColorSelected(hueDegreesToVividHex(newHue))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            val fraction = (offset.x / width).coerceIn(0f, 1f)
                            val newHue = fraction * 360f
                            currentHue = newHue
                            currentOnColorSelected(hueDegreesToVividHex(newHue))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            val fraction = (change.position.x / width).coerceIn(0f, 1f)
                            val newHue = fraction * 360f
                            currentHue = newHue
                            currentOnColorSelected(hueDegreesToVividHex(newHue))
                        }
                    )
                }
                .testTag(testTag),
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeightDp)
                    .align(Alignment.Center)
            ) {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(
                    brush = Brush.horizontalGradient(colors = spectrumColors),
                    cornerRadius = radius
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.30f),
                    cornerRadius = radius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )
            }

            // Thumb positioned using physical left-to-right fraction so it always aligns under the user's finger
            val fraction = (currentHue / 360f).coerceIn(0f, 1f)
            val thumbPx = with(androidx.compose.ui.platform.LocalDensity.current) { thumbSizeDp.toPx() }
            val usableWidth = (trackWidthPx - thumbPx).coerceAtLeast(0f)
            val thumbOffsetX = (fraction * usableWidth).roundToInt()

            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                // Force physical left offset regardless of RTL by using absolute layout math via offset
                androidx.compose.ui.layout.Layout(
                    content = {
                        Box(
                            modifier = Modifier
                                .size(thumbSizeDp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(hueDegreesToVividColor(currentHue))
                        )
                    }
                ) { measurables, constraints ->
                    val placeable = measurables.first().measure(constraints)
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val maxLeft = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
                        val x = (fraction * maxLeft).roundToInt().coerceIn(0, maxLeft)
                        val y = ((constraints.maxHeight - placeable.height) / 2).coerceAtLeast(0)
                        placeable.place(IntOffset(x, y))
                    }
                }
            }
        }
    }
}

fun hueDegreesToVividColor(hueDegrees: Float): Color {
    val hsv = floatArrayOf(hueDegrees.coerceIn(0f, 360f) % 360f, 0.90f, 0.98f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

fun hueDegreesToVividHex(hueDegrees: Float): String {
    val color = hueDegreesToVividColor(hueDegrees)
    val rgb = color.toArgb() and 0x00FFFFFF
    return String.format("#%06X", rgb)
}

fun hexToHueDegrees(hex: String): Float {
    val clean = hex.trim()
    if (clean.isBlank()) return 215f
    val parsed = runCatching {
        val withHash = if (clean.startsWith("#")) clean else "#$clean"
        android.graphics.Color.parseColor(withHash)
    }.getOrNull() ?: return 215f
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(parsed, hsv)
    return hsv[0].coerceIn(0f, 360f)
}
