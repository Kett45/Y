package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.media.ExifInterface
import com.example.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class ChatCropAspectPreset(
    val id: String,
    val arabicLabel: String,
    val ratio: Float? // width / height, null = Original
) {
    ORIGINAL("original", "أصلي", null),
    SQUARE("1:1", "1:1", 1f),
    STANDARD("4:3", "4:3", 4f / 3f),
    WIDE("16:9", "16:9", 16f / 9f)
}

/**
 * Full-screen Telegram-style Pre-Send Media Preview & Editor.
 * Supports:
 * - 90° clockwise rotation
 * - Aspect-ratio crop framing (Original, 1:1, 4:3, 16:9) + pinch-to-zoom & drag-to-pan
 * - Reset transform
 * - Caption input before sending
 */
@Composable
fun ChatImageEditorDialog(
    imageUri: Uri,
    initialCaption: String = "",
    onConfirmSend: (editedBitmap: Bitmap, caption: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var isLoadingSource by remember(imageUri) { mutableStateOf(true) }
    var loadError by remember(imageUri) { mutableStateOf<String?>(null) }
    var isRenderingOutput by remember { mutableStateOf(false) }

    var rotationSteps by remember(imageUri) { mutableIntStateOf(0) } // 0, 1, 2, 3 -> 0°, 90°, 180°, 270°
    var selectedPreset by remember(imageUri) { mutableStateOf(ChatCropAspectPreset.ORIGINAL) }
    var zoomScale by remember(imageUri) { mutableFloatStateOf(1f) }
    var panX by remember(imageUri) { mutableFloatStateOf(0f) }
    var panY by remember(imageUri) { mutableFloatStateOf(0f) }
    var captionText by remember(imageUri) { mutableStateOf(initialCaption) }
    var showCropPresetsBar by remember(imageUri) { mutableStateOf(true) }

    LaunchedEffect(imageUri) {
        isLoadingSource = true
        loadError = null
        val decoded = withContext(Dispatchers.IO) {
            decodeHighQualityEditableBitmap(context, imageUri, maxDimensionPx = 1600)
        }
        if (decoded != null) {
            sourceBitmap = decoded
        } else {
            loadError = "تعذّر فتح الصورة للتعديل"
        }
        isLoadingSource = false
    }

    // Active rotated bitmap preview so crop aspect ratio & pan bounds match visual orientation cleanly
    val activeBitmap = remember(sourceBitmap, rotationSteps) {
        val base = sourceBitmap ?: return@remember null
        val degrees = ((rotationSteps % 4) + 4) % 4 * 90f
        if (degrees == 0f) {
            base
        } else {
            val matrix = Matrix().apply { postRotate(degrees) }
            runCatching {
                Bitmap.createBitmap(base, 0, 0, base.width, base.height, matrix, true)
            }.getOrDefault(base)
        }
    }

    // Track last viewport crop box dimensions (px) so rendering matches exact screen framing
    var lastCropBoxWidthPx by remember { mutableFloatStateOf(0f) }
    var lastCropBoxHeightPx by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = { if (!isRenderingOutput) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B0E14))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .testTag("chat_image_editor_dialog")
            ) {
                // Top Telegram-Style Toolbar (Close, Title, Rotate 90°, Crop Toggle, Reset)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { if (!isRenderingOutput) onDismiss() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_image_editor_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إلغاء",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = "تعديل الصورة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                rotationSteps = (rotationSteps + 1) % 4
                                zoomScale = 1f
                                panX = 0f
                                panY = 0f
                            },
                            enabled = activeBitmap != null && !isRenderingOutput,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_image_editor_rotate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RotateRight,
                                contentDescription = "تدوير 90 درجة",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = { showCropPresetsBar = !showCropPresetsBar },
                            enabled = activeBitmap != null && !isRenderingOutput,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_image_editor_crop_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Crop,
                                contentDescription = "إعدادات القص",
                                tint = if (showCropPresetsBar) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                rotationSteps = 0
                                selectedPreset = ChatCropAspectPreset.ORIGINAL
                                zoomScale = 1f
                                panX = 0f
                                panY = 0f
                            },
                            enabled = activeBitmap != null && !isRenderingOutput,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_image_editor_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "إعادة ضبط",
                                tint = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                // Interactive Crop & Zoom Stage
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val density = LocalDensity.current
                    val stageWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                    val stageHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

                    when {
                        isLoadingSource -> {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                        loadError != null || activeBitmap == null -> {
                            Text(
                                text = loadError ?: "تعذّر تحميل الصورة",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        else -> {
                            val bmp = activeBitmap
                            val naturalRatio = (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.35f, 3.0f)
                            val targetRatio = selectedPreset.ratio ?: naturalRatio

                            // Fit crop frame inside stage while preserving targetRatio
                            val (cropWpx, cropHpx) = if (stageWidthPx / stageHeightPx > targetRatio) {
                                val h = stageHeightPx
                                (h * targetRatio) to h
                            } else {
                                val w = stageWidthPx
                                w to (w / targetRatio)
                            }

                            LaunchedEffect(cropWpx, cropHpx) {
                                lastCropBoxWidthPx = cropWpx
                                lastCropBoxHeightPx = cropHpx
                            }

                            // Compute base ContentScale.Crop rendered image dimensions inside the crop box
                            val baseScale = max(cropWpx / bmp.width.toFloat(), cropHpx / bmp.height.toFloat())
                            val renderedBaseW = bmp.width * baseScale
                            val renderedBaseH = bmp.height * baseScale

                            val cropWdp = with(density) { cropWpx.toDp() }
                            val cropHdp = with(density) { cropHpx.toDp() }

                            Box(
                                modifier = Modifier
                                    .size(width = cropWdp, height = cropHdp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clipToBounds()
                                    .background(Color.Black)
                                    .pointerInput(bmp, selectedPreset, cropWpx, cropHpx) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val nextScale = (zoomScale * zoom).coerceIn(1f, 4.5f)
                                            val maxPanX = ((renderedBaseW * nextScale - cropWpx) / 2f).coerceAtLeast(0f)
                                            val maxPanY = ((renderedBaseH * nextScale - cropHpx) / 2f).coerceAtLeast(0f)
                                            zoomScale = nextScale
                                            panX = (panX + pan.x).coerceIn(-maxPanX, maxPanX)
                                            panY = (panY + pan.y).coerceIn(-maxPanY, maxPanY)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "معاينة الصورة قبل الإرسال",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = zoomScale
                                            scaleY = zoomScale
                                            translationX = panX
                                            translationY = panY
                                        }
                                )

                                // Crisp Telegram-style Crop Border & Rule-of-Thirds Overlay
                                ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeWidth = 2.dp.toPx()
                                    val gridWidth = 0.9.dp.toPx()
                                    val borderColor = Color.White.copy(alpha = 0.92f)
                                    val gridColor = Color.White.copy(alpha = 0.35f)

                                    drawRect(
                                        color = borderColor,
                                        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                        style = Stroke(width = strokeWidth)
                                    )
                                    // Vertical rule-of-thirds
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(size.width / 3f, 0f),
                                        end = Offset(size.width / 3f, size.height),
                                        strokeWidth = gridWidth
                                    )
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(size.width * 2f / 3f, 0f),
                                        end = Offset(size.width * 2f / 3f, size.height),
                                        strokeWidth = gridWidth
                                    )
                                    // Horizontal rule-of-thirds
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(0f, size.height / 3f),
                                        end = Offset(size.width, size.height / 3f),
                                        strokeWidth = gridWidth
                                    )
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(0f, size.height * 2f / 3f),
                                        end = Offset(size.width, size.height * 2f / 3f),
                                        strokeWidth = gridWidth
                                    )
                                }
                            }
                        }
                    }
                }

                // Aspect Ratio Crop Chips Strip + Hint
                if (showCropPresetsBar && activeBitmap != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "اسحب أو قرّب بإصبعين لضبط القص",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChatCropAspectPreset.entries.forEach { preset ->
                                val isSelected = selectedPreset == preset
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
                                    border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable {
                                            selectedPreset = preset
                                            zoomScale = 1f
                                            panX = 0f
                                            panY = 0f
                                        }
                                        .testTag("chat_image_crop_preset_${preset.id}")
                                ) {
                                    Text(
                                        text = preset.arabicLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Telegram-Style Caption Field + Send Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141821))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = captionText,
                        onValueChange = { captionText = it.take(ChatMessage.MAX_TEXT_LENGTH) },
                        placeholder = {
                            Text(
                                text = "أضف تعليقاً على الصورة...",
                                color = Color.White.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.White.copy(alpha = 0.10f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_image_editor_caption_input")
                    )

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .clickable(enabled = activeBitmap != null && !isRenderingOutput) {
                                val bmp = activeBitmap ?: return@clickable
                                isRenderingOutput = true
                                scope.launch {
                                    val rendered = withContext(Dispatchers.Default) {
                                        renderCroppedBitmap(
                                            source = bmp,
                                            cropBoxWidthPx = lastCropBoxWidthPx,
                                            cropBoxHeightPx = lastCropBoxHeightPx,
                                            zoomScale = zoomScale,
                                            panX = panX,
                                            panY = panY,
                                            maxOutputEdgePx = ChatMessage.MAX_IMAGE_DIMENSION_PX
                                        )
                                    }
                                    isRenderingOutput = false
                                    onConfirmSend(rendered, captionText.trim())
                                }
                            }
                            .testTag("chat_image_editor_send_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isRenderingOutput) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "إرسال الصورة",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun decodeHighQualityEditableBitmap(
    context: Context,
    uri: Uri,
    maxDimensionPx: Int
): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val origW = bounds.outWidth
        val origH = bounds.outHeight
        if (origW <= 0 || origH <= 0) return@runCatching null

        var inSampleSize = 1
        while (origW / (inSampleSize * 2) >= maxDimensionPx || origH / (inSampleSize * 2) >= maxDimensionPx) {
            inSampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            this.inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return@runCatching null

        // Respect camera EXIF orientation so portrait photos never load sideways
        val exifOrientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } else {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        }

        val oriented = if (!matrix.isIdentity) {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
                if (it !== raw) raw.recycle()
            }
        } else {
            raw
        }

        val maxSide = max(oriented.width, oriented.height)
        if (maxSide <= maxDimensionPx) {
            oriented
        } else {
            val scale = maxDimensionPx.toFloat() / maxSide.toFloat()
            val outW = (oriented.width * scale).roundToInt().coerceAtLeast(1)
            val outH = (oriented.height * scale).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(oriented, outW, outH, true).also {
                if (it !== oriented) oriented.recycle()
            }
        }
    }.getOrNull()
}

private fun renderCroppedBitmap(
    source: Bitmap,
    cropBoxWidthPx: Float,
    cropBoxHeightPx: Float,
    zoomScale: Float,
    panX: Float,
    panY: Float,
    maxOutputEdgePx: Int
): Bitmap {
    if (cropBoxWidthPx <= 1f || cropBoxHeightPx <= 1f) return source

    // Map screen crop box + ContentScale.Crop + zoom + pan back to source bitmap pixels
    val baseScale = max(cropBoxWidthPx / source.width.toFloat(), cropBoxHeightPx / source.height.toFloat())
    val totalScale = (baseScale * zoomScale).coerceAtLeast(0.01f)

    // Visible rectangle size in source bitmap coordinates
    val visibleSrcW = (cropBoxWidthPx / totalScale).coerceIn(1f, source.width.toFloat())
    val visibleSrcH = (cropBoxHeightPx / totalScale).coerceIn(1f, source.height.toFloat())

    val centerSrcX = (source.width / 2f) - (panX / totalScale)
    val centerSrcY = (source.height / 2f) - (panY / totalScale)

    val left = (centerSrcX - visibleSrcW / 2f).coerceIn(0f, (source.width - visibleSrcW).coerceAtLeast(0f))
    val top = (centerSrcY - visibleSrcH / 2f).coerceIn(0f, (source.height - visibleSrcH).coerceAtLeast(0f))

    val targetScale = min(1f, maxOutputEdgePx.toFloat() / max(visibleSrcW, visibleSrcH))
    val outW = (visibleSrcW * targetScale).roundToInt().coerceAtLeast(1)
    val outH = (visibleSrcH * targetScale).roundToInt().coerceAtLeast(1)

    val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val srcRect = android.graphics.Rect(
        left.roundToInt().coerceIn(0, source.width - 1),
        top.roundToInt().coerceIn(0, source.height - 1),
        (left + visibleSrcW).roundToInt().coerceIn(1, source.width),
        (top + visibleSrcH).roundToInt().coerceIn(1, source.height)
    )
    val dstRect = android.graphics.Rect(0, 0, outW, outH)
    canvas.drawBitmap(source, srcRect, dstRect, paint)
    return output
}
