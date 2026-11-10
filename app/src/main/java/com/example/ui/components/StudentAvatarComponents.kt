package com.example.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.data.model.Student
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Non-destructive circular avatar framing parameters stored alongside the full uncropped photo:
 * - [zoom]: 0.35f (fit entire rectangular photo inside circle) .. 4.0f (close-up on face)
 * - [panXFraction] & [panYFraction]: translation offset in multiples of the circular frame diameter
 * - [rotationDegrees]: 0, 90, 180, or 270 degrees
 */
data class PhotoCropFrame(
    val zoom: Float = 1f,
    val panXFraction: Float = 0f,
    val panYFraction: Float = 0f,
    val rotationDegrees: Int = 0
)

object StudentAvatarImageHelper {

    const val PHOTO_DELIMITER = "|||"
    const val CROP_METADATA_MARKER = "#crop="
    private const val ALBUM_PREFS_NAME = "kam_student_photo_albums"
    private const val HD_PHOTO_PREFS_NAME = "kam_student_full_photos_hd"
    private const val MAX_FIRESTORE_AVATAR_CHARS = 45_000
    private const val MAX_LOCAL_ALBUM_PHOTOS = 20

    private val framedAvatarCache = object : LruCache<Int, ImageBitmap>(160) {}
    private val fullPhotoCache = object : LruCache<Int, ImageBitmap>(120) {}

    /**
     * Parses a photo entry of the form `<rawBase64>#crop=zoom,panX,panY,rot`
     * into `(rawBase64, PhotoCropFrame)`. Backward-compatible with plain Base64 strings.
     */
    fun parsePhotoEntry(photoEntry: String): Pair<String, PhotoCropFrame> {
        val trimmed = photoEntry.trim()
        if (trimmed.isBlank()) return "" to PhotoCropFrame()
        val rawPart = trimmed.substringBefore(CROP_METADATA_MARKER).trim()
        if (!trimmed.contains(CROP_METADATA_MARKER)) {
            return rawPart to PhotoCropFrame()
        }
        val metaPart = trimmed.substringAfter(CROP_METADATA_MARKER, "").trim()
        val parts = metaPart.split(",")
        val zoom = parts.getOrNull(0)?.toFloatOrNull()?.coerceIn(0.35f, 4.0f) ?: 1f
        val panX = parts.getOrNull(1)?.toFloatOrNull()?.coerceIn(-1.5f, 1.5f) ?: 0f
        val panY = parts.getOrNull(2)?.toFloatOrNull()?.coerceIn(-1.5f, 1.5f) ?: 0f
        val rot = ((parts.getOrNull(3)?.toIntOrNull() ?: 0) % 360 + 360) % 360
        return rawPart to PhotoCropFrame(
            zoom = zoom,
            panXFraction = panX,
            panYFraction = panY,
            rotationDegrees = rot
        )
    }

    /**
     * Attaches non-destructive circular framing metadata to a full uncropped Base64 photo string.
     */
    fun attachCropFrame(rawBase64: String, frame: PhotoCropFrame): String {
        val cleanRaw = rawBase64.substringBefore(CROP_METADATA_MARKER).trim()
        if (cleanRaw.isBlank()) return ""
        val z = String.format(Locale.US, "%.3f", frame.zoom.coerceIn(0.35f, 4.0f))
        val px = String.format(Locale.US, "%.3f", frame.panXFraction.coerceIn(-1.5f, 1.5f))
        val py = String.format(Locale.US, "%.3f", frame.panYFraction.coerceIn(-1.5f, 1.5f))
        val rot = ((frame.rotationDegrees % 360) + 360) % 360
        return "$cleanRaw$CROP_METADATA_MARKER$z,$px,$py,$rot"
    }

    private fun isSameUnderlyingPhoto(entryA: String, entryB: String): Boolean {
        val rawA = entryA.substringBefore(CROP_METADATA_MARKER).trim()
        val rawB = entryB.substringBefore(CROP_METADATA_MARKER).trim()
        return rawA.isNotBlank() && rawA == rawB
    }

    /**
     * Extracts the primary/current photo entry (including its `#crop=` framing metadata) from [Student.avatarBase64].
     */
    fun extractMainPhotoBase64(rawAvatarField: String): String {
        if (rawAvatarField.isBlank()) return ""
        return rawAvatarField.substringBefore(PHOTO_DELIMITER).trim()
    }

    /**
     * Extracts all photo entries encoded inside [rawAvatarField] (main photo first, followed by saved album photos),
     * deduplicating by underlying uncropped image while keeping the first (most recent) framing metadata.
     */
    fun extractAllPhotosFromField(rawAvatarField: String): List<String> {
        if (rawAvatarField.isBlank()) return emptyList()
        val rawEntries = rawAvatarField
            .split(PHOTO_DELIMITER)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val deduplicated = mutableListOf<String>()
        for (entry in rawEntries) {
            if (deduplicated.none { isSameUnderlyingPhoto(it, entry) }) {
                deduplicated.add(entry)
            }
        }
        return deduplicated
    }

    /**
     * Returns the complete photo album for a student by combining cloud-synced photos in [rawAvatarField]
     * with locally persisted album photos for [studentId], ensuring old photos are never lost.
     */
    fun getMergedStudentPhotoAlbum(
        context: Context?,
        studentId: String,
        rawAvatarField: String
    ): List<String> {
        val cloudPhotos = extractAllPhotosFromField(rawAvatarField)
        val localPhotos = if (context != null && studentId.isNotBlank()) {
            val prefs = context.getSharedPreferences(ALBUM_PREFS_NAME, Context.MODE_PRIVATE)
            val savedRaw = prefs.getString("album_$studentId", "").orEmpty()
            extractAllPhotosFromField(savedRaw)
        } else {
            emptyList()
        }
        val merged = mutableListOf<String>()
        for (entry in (cloudPhotos + localPhotos)) {
            if (merged.none { isSameUnderlyingPhoto(it, entry) }) {
                merged.add(entry)
            }
        }
        return merged.take(MAX_LOCAL_ALBUM_PHOTOS)
    }

    /**
     * Adds or updates a photo in the student's album without deleting previous photos:
     * - If [setAsMain] is true, [newPhotoBase64] becomes the first (main) photo and the old main photo
     *   automatically moves into the album below it.
     * - If [setAsMain] is false, the current main photo stays main and [newPhotoBase64] is appended to the album.
     */
    fun addPhotoPreservingOldAlbum(
        context: Context?,
        studentId: String,
        currentRawAvatarField: String,
        newPhotoBase64: String,
        setAsMain: Boolean = true
    ): String {
        val cleanNew = newPhotoBase64.trim()
        val existingAlbum = getMergedStudentPhotoAlbum(context, studentId, currentRawAvatarField)
        if (cleanNew.isBlank()) {
            return packAlbumForFirestoreAndSaveLocal(context, studentId, existingAlbum)
        }

        val updatedAlbum = if (setAsMain || existingAlbum.isEmpty()) {
            listOf(cleanNew) + existingAlbum.filterNot { isSameUnderlyingPhoto(it, cleanNew) }
        } else {
            val main = existingAlbum.first()
            if (isSameUnderlyingPhoto(main, cleanNew)) {
                listOf(cleanNew) + existingAlbum.drop(1)
            } else {
                val rest = existingAlbum.drop(1).filterNot { isSameUnderlyingPhoto(it, cleanNew) }
                listOf(main, cleanNew) + rest
            }
        }.take(MAX_LOCAL_ALBUM_PHOTOS)

        return packAlbumForFirestoreAndSaveLocal(context, studentId, updatedAlbum)
    }

    /**
     * Updates the non-destructive circular framing (`#crop=zoom,panX,panY,rot`) for an existing photo
     * in the student's album without losing the full uncropped image.
     */
    fun updatePhotoFramingInAlbum(
        context: Context?,
        studentId: String,
        currentRawAvatarField: String,
        targetPhotoEntry: String,
        newFrame: PhotoCropFrame,
        promoteToMain: Boolean = false
    ): String {
        val (rawBase64, _) = parsePhotoEntry(targetPhotoEntry)
        val updatedEntry = attachCropFrame(rawBase64, newFrame)
        val existingAlbum = getMergedStudentPhotoAlbum(context, studentId, currentRawAvatarField)
        if (existingAlbum.isEmpty()) {
            return addPhotoPreservingOldAlbum(context, studentId, currentRawAvatarField, updatedEntry, setAsMain = true)
        }
        val replaced = existingAlbum.map { entry ->
            if (isSameUnderlyingPhoto(entry, updatedEntry)) updatedEntry else entry
        }
        val finalAlbum = if (promoteToMain) {
            listOf(updatedEntry) + replaced.filterNot { isSameUnderlyingPhoto(it, updatedEntry) }
        } else {
            replaced
        }
        return packAlbumForFirestoreAndSaveLocal(context, studentId, finalAlbum)
    }

    /**
     * Sets an existing photo from the student's album as the main profile picture while keeping all other photos.
     */
    fun promoteAlbumPhotoToMain(
        context: Context?,
        studentId: String,
        currentRawAvatarField: String,
        targetPhotoBase64: String
    ): String {
        return addPhotoPreservingOldAlbum(
            context = context,
            studentId = studentId,
            currentRawAvatarField = currentRawAvatarField,
            newPhotoBase64 = targetPhotoBase64,
            setAsMain = true
        )
    }

    /**
     * Removes a single photo from the student's album (restricted to Group Admin in UI).
     */
    fun removePhotoFromAlbum(
        context: Context?,
        studentId: String,
        currentRawAvatarField: String,
        photoToRemoveBase64: String
    ): String {
        val existingAlbum = getMergedStudentPhotoAlbum(context, studentId, currentRawAvatarField)
        val remaining = existingAlbum.filterNot { isSameUnderlyingPhoto(it, photoToRemoveBase64) }
        return packAlbumForFirestoreAndSaveLocal(context, studentId, remaining)
    }

    private fun packAlbumForFirestoreAndSaveLocal(
        context: Context?,
        studentId: String,
        album: List<String>
    ): String {
        val cleanList = mutableListOf<String>()
        for (item in album) {
            val trimmed = item.trim()
            if (trimmed.isNotBlank() && cleanList.none { isSameUnderlyingPhoto(it, trimmed) }) {
                cleanList.add(trimmed)
            }
        }
        val cappedList = cleanList.take(MAX_LOCAL_ALBUM_PHOTOS)

        if (context != null && studentId.isNotBlank()) {
            context.getSharedPreferences(ALBUM_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("album_$studentId", cappedList.joinToString(PHOTO_DELIMITER))
                .apply()
        }
        if (cappedList.isEmpty()) return ""

        val packed = StringBuilder()
        for (photo in cappedList) {
            if (packed.isEmpty()) {
                if (photo.length <= MAX_FIRESTORE_AVATAR_CHARS) {
                    packed.append(photo)
                }
            } else if (packed.length + PHOTO_DELIMITER.length + photo.length <= MAX_FIRESTORE_AVATAR_CHARS) {
                packed.append(PHOTO_DELIMITER).append(photo)
            } else {
                break
            }
        }
        return packed.toString()
    }

    /**
     * Preserves the FULL, UNCROPPED [source] image (keeping its original width:height aspect ratio)
     * and attaches non-destructive circular framing metadata (`#crop=zoom,panX,panY,rot`) so:
     * 1. The album and downloader always have the complete uncropped original photo.
     * 2. The circular profile avatar (`StudentAvatarCircle`) displays only the exact face/region selected by the user.
     */
    fun encodeFullPhotoWithCropMetadata(
        context: Context?,
        source: Bitmap,
        zoom: Float = 1f,
        panXFraction: Float = 0f,
        panYFraction: Float = 0f,
        rotationDegrees: Int = 0,
        maxDimensionPx: Int = 520,
        maxBytes: Int = 14_500
    ): String {
        if (source.width <= 0 || source.height <= 0) return ""

        // Scale down proportionally WITHOUT cropping so the full rectangular image is preserved
        val srcW = source.width
        val srcH = source.height
        val longestSide = max(srcW, srcH)
        val cloudBitmap = if (longestSide > maxDimensionPx) {
            val scale = maxDimensionPx.toFloat() / longestSide.toFloat()
            val targetW = max(1, (srcW * scale).roundToInt())
            val targetH = max(1, (srcH * scale).roundToInt())
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        } else {
            source
        }

        var quality = 78
        var compressedBytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            cloudBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            compressedBytes = out.toByteArray()
            quality -= 8
        } while (compressedBytes.size > maxBytes && quality >= 24)

        val cloudBase64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)

        // Also save a higher-definition uncropped copy locally so local viewing/downloading is extra crisp
        if (context != null && cloudBase64.isNotBlank()) {
            try {
                val hdMaxDim = 1080
                val hdBitmap = if (longestSide > hdMaxDim) {
                    val scale = hdMaxDim.toFloat() / longestSide.toFloat()
                    Bitmap.createScaledBitmap(
                        source,
                        max(1, (srcW * scale).roundToInt()),
                        max(1, (srcH * scale).roundToInt()),
                        true
                    )
                } else {
                    source
                }
                val hdOut = ByteArrayOutputStream()
                hdBitmap.compress(Bitmap.CompressFormat.JPEG, 88, hdOut)
                val hdBase64 = Base64.encodeToString(hdOut.toByteArray(), Base64.NO_WRAP)
                context.getSharedPreferences(HD_PHOTO_PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("hd_${cloudBase64.hashCode()}", hdBase64)
                    .apply()
            } catch (_: Exception) {
                // Ignore if local storage is tight; cloudBase64 still contains the full uncropped photo
            }
        }

        val frame = PhotoCropFrame(
            zoom = zoom.coerceIn(0.35f, 4.0f),
            panXFraction = panXFraction.coerceIn(-1.5f, 1.5f),
            panYFraction = panYFraction.coerceIn(-1.5f, 1.5f),
            rotationDegrees = ((rotationDegrees % 360) + 360) % 360
        )
        return attachCropFrame(cloudBase64, frame)
    }

    /**
     * Backward-compatible wrapper that saves the full uncropped photo + framing metadata.
     */
    fun cropAndCompressWithTransform(
        source: Bitmap,
        zoom: Float = 1f,
        panXFraction: Float = 0f,
        panYFraction: Float = 0f,
        rotationDegrees: Int = 0,
        targetSizePx: Int = 520,
        maxBytes: Int = 14_500
    ): String {
        return encodeFullPhotoWithCropMetadata(
            context = null,
            source = source,
            zoom = zoom,
            panXFraction = panXFraction,
            panYFraction = panYFraction,
            rotationDegrees = rotationDegrees,
            maxDimensionPx = max(360, targetSizePx),
            maxBytes = maxBytes
        )
    }

    fun cropAndCompressToBase64(
        source: Bitmap,
        targetSizePx: Int = 520,
        maxBytes: Int = 14_500
    ): String {
        return encodeFullPhotoWithCropMetadata(
            context = null,
            source = source,
            zoom = 1f,
            panXFraction = 0f,
            panYFraction = 0f,
            rotationDegrees = 0,
            maxDimensionPx = max(360, targetSizePx),
            maxBytes = maxBytes
        )
    }

    /**
     * Renders the square/circular avatar view from a full uncropped [source] bitmap using [frame].
     * Matches the Facebook-style full-screen editor 1:1.
     */
    fun renderFramedAvatarToBitmap(
        source: Bitmap,
        frame: PhotoCropFrame,
        targetSizePx: Int = 220
    ): Bitmap {
        val rot = ((frame.rotationDegrees % 360) + 360) % 360
        val rotated = if (rot != 0) {
            val matrix = Matrix().apply { postRotate(rot.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        val w = rotated.width.toFloat().coerceAtLeast(1f)
        val h = rotated.height.toFloat().coerceAtLeast(1f)
        val d = targetSizePx.toFloat()
        val safeZoom = frame.zoom.coerceIn(0.35f, 4.0f)

        val outBitmap = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(outBitmap)
        canvas.drawColor(android.graphics.Color.rgb(15, 23, 42))

        // At zoom = 1.0f, the shorter dimension fills the circle diameter d.
        // At zoom = min(d/w, d/h) / max(d/w, d/h), the entire rectangular photo fits inside the circle.
        val baseScale = max(d / w, d / h) * safeZoom
        val scaledW = w * baseScale
        val scaledH = h * baseScale

        val dx = (d - scaledW) / 2f + (frame.panXFraction * d)
        val dy = (d - scaledH) / 2f + (frame.panYFraction * d)

        val drawMatrix = Matrix().apply {
            postScale(baseScale, baseScale)
            postTranslate(dx, dy)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(rotated, drawMatrix, paint)
        return outBitmap
    }

    /**
     * Calculates the exact zoom value needed so the ENTIRE full photo fits inside the circular avatar
     * without cutting off any corner or edge.
     */
    fun computeFitFullPhotoZoom(width: Int, height: Int, rotationDegrees: Int): Float {
        val rot = ((rotationDegrees % 360) + 360) % 360
        val w = (if (rot == 90 || rot == 270) height else width).toFloat().coerceAtLeast(1f)
        val h = (if (rot == 90 || rot == 270) width else height).toFloat().coerceAtLeast(1f)
        val ratio = min(w, h) / max(w, h)
        // Multiply by 0.96f so the entire photo is comfortably visible inside the circle border
        return (ratio * 0.96f).coerceIn(0.35f, 1.0f)
    }

    fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            var sampleSize = 1
            val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxDim / sampleSize > 1280) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the cached **framed avatar** bitmap (for `StudentAvatarCircle`).
     */
    fun getCachedBitmap(base64: String): ImageBitmap? {
        val main = extractMainPhotoBase64(base64)
        if (main.isBlank()) return null
        return framedAvatarCache.get(main.hashCode())
    }

    /**
     * Decodes [base64] and applies its `#crop=zoom,panX,panY,rot` framing so `StudentAvatarCircle`
     * displays only the user-selected region while the underlying saved photo remains 100% uncropped.
     */
    fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
        val main = extractMainPhotoBase64(base64)
        if (main.isBlank()) return null
        val cacheKey = main.hashCode()
        framedAvatarCache.get(cacheKey)?.let { return it }

        return try {
            val (rawBase64, frame) = parsePhotoEntry(main)
            val clean = rawBase64.substringAfter("base64,", rawBase64)
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            val fullBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val framedBmp = renderFramedAvatarToBitmap(fullBmp, frame, targetSizePx = 220)
            val imageBitmap = framedBmp.asImageBitmap()
            framedAvatarCache.put(cacheKey, imageBitmap)
            imageBitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the cached **full uncropped** photo bitmap (for the Student Photo Album & Full Photo Viewer).
     */
    fun getCachedFullPhotoBitmap(photoEntry: String): ImageBitmap? {
        val main = extractMainPhotoBase64(photoEntry)
        if (main.isBlank()) return null
        val (rawBase64, frame) = parsePhotoEntry(main)
        val cacheKey = "${rawBase64.hashCode()}_rot_${frame.rotationDegrees}".hashCode()
        return fullPhotoCache.get(cacheKey)
    }

    /**
     * Decodes the **FULL, UNCROPPED** photo (applying only 90° rotation if rotated, never cropping!).
     * Checks local HD storage first for maximum sharpness, falling back to the cloud-synced full photo.
     */
    fun decodeFullUncroppedAndroidBitmap(context: Context?, photoEntry: String): Bitmap? {
        val main = extractMainPhotoBase64(photoEntry)
        if (main.isBlank()) return null
        return try {
            val (rawBase64, frame) = parsePhotoEntry(main)
            val cleanCloud = rawBase64.substringAfter("base64,", rawBase64)
            val hdBase64 = context
                ?.getSharedPreferences(HD_PHOTO_PREFS_NAME, Context.MODE_PRIVATE)
                ?.getString("hd_${cleanCloud.hashCode()}", null)
                ?.takeIf { it.isNotBlank() }

            val targetBase64 = hdBase64 ?: cleanCloud
            val bytes = Base64.decode(targetBase64, Base64.DEFAULT)
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val rot = ((frame.rotationDegrees % 360) + 360) % 360
            if (rot != 0) {
                val matrix = Matrix().apply { postRotate(rot.toFloat()) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            } else {
                decoded
            }
        } catch (_: Exception) {
            null
        }
    }

    fun decodeFullPhotoToImageBitmap(context: Context?, photoEntry: String): ImageBitmap? {
        val main = extractMainPhotoBase64(photoEntry)
        if (main.isBlank()) return null
        val (rawBase64, frame) = parsePhotoEntry(main)
        val cacheKey = "${rawBase64.hashCode()}_rot_${frame.rotationDegrees}".hashCode()
        fullPhotoCache.get(cacheKey)?.let { return it }

        val bmp = decodeFullUncroppedAndroidBitmap(context, main) ?: return null
        val imageBitmap = bmp.asImageBitmap()
        fullPhotoCache.put(cacheKey, imageBitmap)
        return imageBitmap
    }

    /**
     * Saves the FULL, UNCROPPED photo directly to the user's phone Gallery inside `Pictures/kAM_Lite`
     * without requiring any dangerous storage permissions on modern Android.
     */
    fun saveFullPhotoToDeviceGallery(
        context: Context,
        studentName: String,
        photoEntry: String
    ): Result<String> {
        return try {
            val fullBitmap = decodeFullUncroppedAndroidBitmap(context, photoEntry)
                ?: return Result.failure(IllegalStateException("تعذر قراءة الصورة الكاملة"))

            val safeStudentName = studentName
                .trim()
                .replace(Regex("[^\\p{L}\\p{N}_-]+"), "_")
                .ifBlank { "student" }
            val fileName = "kAM_Lite_${safeStudentName}_${System.currentTimeMillis()}.jpg"

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/kAM_Lite")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val insertedUri = resolver.insert(collectionUri, contentValues)
            if (insertedUri != null) {
                resolver.openOutputStream(insertedUri)?.use { outStream ->
                    fullBitmap.compress(Bitmap.CompressFormat.JPEG, 96, outStream)
                } ?: return Result.failure(IllegalStateException("تعذر فتح ملف الحفظ في المعرض"))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val completeValues = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    resolver.update(insertedUri, completeValues, null, null)
                }
                Result.success("Pictures/kAM_Lite/$fileName")
            } else {
                // Fallback to app external pictures directory if MediaStore returns null
                val fallbackDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "kAM_Lite"
                ).apply { mkdirs() }
                val fallbackFile = File(fallbackDir, fileName)
                FileOutputStream(fallbackFile).use { outStream ->
                    fullBitmap.compress(Bitmap.CompressFormat.JPEG, 96, outStream)
                }
                Result.success(fallbackFile.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Circular thumbnail for a child.
 * - Single tap (`onTap`): invokes [onClick] (e.g., opens the profile photo & album dialog).
 * - Fast press-and-hold (`onLongPress`): immediately opens the floating Quick Peek popup showing
 *   the student's full photo (or large initials if no photo is set), full name, student number,
 *   and block badge, and automatically dismisses the instant the finger is lifted.
 */
@Composable
fun StudentAvatarCircle(
    student: Student,
    blockAccent: Color,
    size: Dp = 42.dp,
    onClick: () -> Unit,
    testTag: String = "student_avatar_${student.id}",
    blockLabel: String? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val currentOnClick by rememberUpdatedState(onClick)

    val mainBase64 = remember(student.avatarBase64) {
        StudentAvatarImageHelper.extractMainPhotoBase64(student.avatarBase64)
    }
    var imageBitmap by remember(mainBase64) {
        mutableStateOf(StudentAvatarImageHelper.getCachedBitmap(mainBase64))
    }
    var fullImageBitmap by remember(mainBase64) {
        mutableStateOf(StudentAvatarImageHelper.getCachedFullPhotoBitmap(mainBase64))
    }
    var isHoldPreviewVisible by remember { mutableStateOf(false) }

    DisposableEffect(student.id) {
        onDispose {
            isHoldPreviewVisible = false
        }
    }

    // Preload both the framed circular avatar AND the full uncropped photo in background
    // so press-and-hold opens in 0ms without any decoding delay.
    LaunchedEffect(mainBase64) {
        if (mainBase64.isBlank()) {
            imageBitmap = null
            fullImageBitmap = null
        } else {
            if (imageBitmap == null) {
                imageBitmap = withContext(Dispatchers.Default) {
                    StudentAvatarImageHelper.decodeBase64ToImageBitmap(mainBase64)
                }
            }
            if (fullImageBitmap == null) {
                fullImageBitmap = withContext(Dispatchers.Default) {
                    StudentAvatarImageHelper.decodeFullPhotoToImageBitmap(context, mainBase64)
                }
            }
        }
    }

    val baseViewConfig = LocalViewConfiguration.current
    val fastHoldViewConfig = remember(baseViewConfig) {
        object : ViewConfiguration by baseViewConfig {
            // Fast 210ms press-and-hold trigger so the peek popup opens rapidly
            override val longPressTimeoutMillis: Long
                get() = 210L
        }
    }

    CompositionLocalProvider(LocalViewConfiguration provides fastHoldViewConfig) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(blockAccent.copy(alpha = 0.25f))
                .border(
                    width = if (isHoldPreviewVisible) 2.2.dp else 1.5.dp,
                    color = blockAccent,
                    shape = CircleShape
                )
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    onClick(label = "فتح صورة ${student.name}") {
                        currentOnClick()
                        true
                    }
                    onLongClick(label = "معاينة سريعة لصورة ${student.name}") {
                        isHoldPreviewVisible = true
                        true
                    }
                }
                .pointerInput(student.id) {
                    detectTapGestures(
                        onPress = {
                            try {
                                tryAwaitRelease()
                            } finally {
                                isHoldPreviewVisible = false
                            }
                        },
                        onTap = {
                            isHoldPreviewVisible = false
                            currentOnClick()
                        },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isHoldPreviewVisible = true
                        }
                    )
                }
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap!!,
                    contentDescription = "صورة ${student.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = student.avatarInitials,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (isHoldPreviewVisible) {
        StudentHoldQuickPeekPopup(
            student = student,
            blockAccent = blockAccent,
            blockLabel = blockLabel,
            fullPhotoBitmap = fullImageBitmap ?: imageBitmap,
            framedAvatarBitmap = imageBitmap
        )
    }
}

/**
 * Instant, non-focus-stealing floating modal shown while the user holds their finger on [StudentAvatarCircle].
 * Automatically disappears the exact moment the user lifts their finger.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentHoldQuickPeekPopup(
    student: Student,
    blockAccent: Color,
    blockLabel: String?,
    fullPhotoBitmap: ImageBitmap?,
    framedAvatarBitmap: ImageBitmap?
) {
    val nonFocusablePopupProps = remember {
        PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false
        )
    }

    Popup(
        alignment = Alignment.Center,
        properties = nonFocusablePopupProps
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 20.dp, vertical = 28.dp)
                .testTag("student_quick_peek_popup_${student.id}"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 360.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 12.dp,
                shadowElevation = 20.dp,
                border = BorderStroke(2.dp, blockAccent)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Hero Photo or Large Initials Preview Area
                    if (fullPhotoBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 240.dp, max = 360.dp)
                                .background(Color(0xFF0B1120)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = fullPhotoBitmap,
                                contentDescription = "الصورة الكاملة للطالب ${student.name}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 240.dp, max = 360.dp)
                                    .testTag("student_quick_peek_full_image_${student.id}")
                            )

                            // Small circular framed avatar badge in the corner
                            if (framedAvatarBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp)
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0F172A))
                                        .border(2.dp, blockAccent, CircleShape)
                                ) {
                                    Image(
                                        bitmap = framedAvatarBitmap,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            blockAccent.copy(alpha = 0.28f),
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(112.dp)
                                        .clip(CircleShape)
                                        .background(blockAccent.copy(alpha = 0.22f))
                                        .border(3.dp, blockAccent, CircleShape)
                                        .testTag("student_quick_peek_initials_${student.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = student.avatarInitials,
                                        style = MaterialTheme.typography.displaySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Text(
                                    text = "بدون صورة مضافة بعد",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = blockAccent.copy(alpha = 0.35f))

                    // 2. Student Name, Number & Block Metadata Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = student.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("student_quick_peek_name_${student.id}")
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Student Number Badge
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "رقم #${student.studentNumber}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                        .testTag("student_quick_peek_number_${student.id}")
                                )
                            }

                            // Servant Block Badge
                            val resolvedBlockLabel = blockLabel?.takeIf { it.isNotBlank() } ?: "بدون بلوك"
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = blockAccent.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, blockAccent)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(blockAccent)
                                    )
                                    Text(
                                        text = resolvedBlockLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("student_quick_peek_block_${student.id}")
                                    )
                                }
                            }
                        }

                        Text(
                            text = "ارفع إصبعك لإغلاق المعاينة السريعة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog for viewing the child's current profile picture, launching the Facebook-style Full-Screen
 * Profile Photo Editor, downloading full photos to the device, and managing the non-destructive photo album.
 */
@Composable
fun StudentPhotoPickerDialog(
    student: Student,
    blockAccent: Color,
    isAdmin: Boolean = false,
    onDismiss: () -> Unit,
    onSaveAvatarBase64: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentPackedField by remember(student.avatarBase64) { mutableStateOf(student.avatarBase64) }
    var editorSourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editorExistingPhotoEntry by remember { mutableStateOf<String?>(null) }
    var editorInitialFrame by remember { mutableStateOf(PhotoCropFrame()) }
    var saveAsMainPhoto by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusBannerMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            errorMessage = null
            statusBannerMessage = null
            scope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    StudentAvatarImageHelper.decodeBitmapFromUri(context, uri)
                }
                isProcessing = false
                if (decoded != null) {
                    editorExistingPhotoEntry = null
                    editorInitialFrame = PhotoCropFrame()
                    editorSourceBitmap = decoded
                } else {
                    errorMessage = "تعذر قراءة الصورة المختارة"
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            editorExistingPhotoEntry = null
            editorInitialFrame = PhotoCropFrame()
            editorSourceBitmap = bitmap
            errorMessage = null
            statusBannerMessage = null
        }
    }

    val mainPhotoEntry = remember(currentPackedField) {
        StudentAvatarImageHelper.extractMainPhotoBase64(currentPackedField)
    }

    var framedPreviewBitmap by remember(mainPhotoEntry) {
        mutableStateOf(StudentAvatarImageHelper.getCachedBitmap(mainPhotoEntry))
    }
    LaunchedEffect(mainPhotoEntry) {
        if (mainPhotoEntry.isBlank()) {
            framedPreviewBitmap = null
        } else if (framedPreviewBitmap == null) {
            framedPreviewBitmap = withContext(Dispatchers.Default) {
                StudentAvatarImageHelper.decodeBase64ToImageBitmap(mainPhotoEntry)
            }
        }
    }

    // Full-screen Facebook-style profile photo framing editor modal
    val activeEditorBitmap = editorSourceBitmap
    if (activeEditorBitmap != null) {
        FacebookStyleProfilePhotoEditorDialog(
            studentName = student.name,
            sourceBitmap = activeEditorBitmap,
            initialFrame = editorInitialFrame,
            saveButtonLabel = if (saveAsMainPhoto) {
                "حفظ الصورة كاملة وتطبيق كادر البروفايل"
            } else {
                "حفظ الصورة كاملة في ألبوم الطفل"
            },
            isSaving = isProcessing,
            onDismiss = {
                editorSourceBitmap = null
                editorExistingPhotoEntry = null
            },
            onConfirmFrame = { chosenFrame ->
                isProcessing = true
                scope.launch {
                    val existingEntry = editorExistingPhotoEntry
                    val updatedPacked = withContext(Dispatchers.Default) {
                        if (existingEntry != null) {
                            StudentAvatarImageHelper.updatePhotoFramingInAlbum(
                                context = context,
                                studentId = student.id,
                                currentRawAvatarField = currentPackedField,
                                targetPhotoEntry = existingEntry,
                                newFrame = chosenFrame,
                                promoteToMain = saveAsMainPhoto
                            )
                        } else {
                            val encodedFullWithFrame = StudentAvatarImageHelper.encodeFullPhotoWithCropMetadata(
                                context = context,
                                source = activeEditorBitmap,
                                zoom = chosenFrame.zoom,
                                panXFraction = chosenFrame.panXFraction,
                                panYFraction = chosenFrame.panYFraction,
                                rotationDegrees = chosenFrame.rotationDegrees
                            )
                            StudentAvatarImageHelper.addPhotoPreservingOldAlbum(
                                context = context,
                                studentId = student.id,
                                currentRawAvatarField = currentPackedField,
                                newPhotoBase64 = encodedFullWithFrame,
                                setAsMain = saveAsMainPhoto
                            )
                        }
                    }
                    isProcessing = false
                    if (updatedPacked.isNotBlank()) {
                        currentPackedField = updatedPacked
                        editorSourceBitmap = null
                        editorExistingPhotoEntry = null
                        statusBannerMessage = "تم حفظ الصورة كاملة وضبط كادر الوجه الدائري بنجاح ✓"
                        onSaveAvatarBase64(updatedPacked)
                    } else {
                        errorMessage = "تعذر حفظ الصورة"
                    }
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "صورة البروفايل وألبوم: ${student.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Circular Avatar Preview + Quick Re-frame / Download Buttons
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .background(blockAccent.copy(alpha = 0.22f))
                        .border(2.5.dp, blockAccent, CircleShape)
                        .clickable(enabled = mainPhotoEntry.isNotBlank()) {
                            // Tapping the current profile circle opens the Facebook-style editor on the current full photo
                            scope.launch {
                                isProcessing = true
                                val fullBmp = withContext(Dispatchers.IO) {
                                    StudentAvatarImageHelper.decodeFullUncroppedAndroidBitmap(context, mainPhotoEntry)
                                }
                                isProcessing = false
                                if (fullBmp != null) {
                                    val (_, savedFrame) = StudentAvatarImageHelper.parsePhotoEntry(mainPhotoEntry)
                                    saveAsMainPhoto = true
                                    editorExistingPhotoEntry = mainPhotoEntry
                                    editorInitialFrame = savedFrame
                                    editorSourceBitmap = fullBmp
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val currentPreview = framedPreviewBitmap
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    } else if (currentPreview != null) {
                        Image(
                            bitmap = currentPreview,
                            contentDescription = "معاينة صورة ${student.name}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = student.avatarInitials,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Quick actions for the current main photo (Re-frame face like Facebook + Download full photo)
                if (mainPhotoEntry.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isProcessing = true
                                    val fullBmp = withContext(Dispatchers.IO) {
                                        StudentAvatarImageHelper.decodeFullUncroppedAndroidBitmap(context, mainPhotoEntry)
                                    }
                                    isProcessing = false
                                    if (fullBmp != null) {
                                        val (_, savedFrame) = StudentAvatarImageHelper.parsePhotoEntry(mainPhotoEntry)
                                        saveAsMainPhoto = true
                                        editorExistingPhotoEntry = mainPhotoEntry
                                        editorInitialFrame = savedFrame
                                        editorSourceBitmap = fullBmp
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reframe_current_main_photo_button"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ضبط مكان الوجه", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        StudentAvatarImageHelper.saveFullPhotoToDeviceGallery(
                                            context = context,
                                            studentName = student.name,
                                            photoEntry = mainPhotoEntry
                                        )
                                    }
                                    result.onSuccess {
                                        val msg = "تم تحميل الصورة الكاملة في معرض الهاتف (مجلد kAM Lite) ✓"
                                        statusBannerMessage = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }.onFailure {
                                        errorMessage = "تعذر تحميل الصورة: ${it.localizedMessage ?: ""}"
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_current_main_photo_button"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تحميل الصورة كاملة", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Text(
                    text = "الصور تُحفظ كاملةً بدون أي قص داخل الألبوم، والجزء الذي تحدده فقط يظهر داخل دائرة البروفايل مثل فيسبوك.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                statusBannerMessage?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF065F46),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }

                errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            saveAsMainPhoto = true
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("pick_student_photo_gallery_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اختيار من المعرض")
                    }

                    OutlinedButton(
                        onClick = {
                            saveAsMainPhoto = true
                            cameraLauncher.launch(null)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("pick_student_photo_camera_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("الكاميرا")
                    }
                }

                // White/Ivory Student Photo Album Card at the Bottom
                StudentPhotoAlbumSection(
                    student = student.copy(avatarBase64 = currentPackedField),
                    isAdmin = isAdmin,
                    onAddPhotoFromGallery = {
                        saveAsMainPhoto = false
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onAddPhotoFromCamera = {
                        saveAsMainPhoto = false
                        cameraLauncher.launch(null)
                    },
                    onUpdatePackedAvatarField = { updatedField ->
                        currentPackedField = updatedField
                        onSaveAvatarBase64(updatedField)
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveAvatarBase64(currentPackedField)
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_save_student_photo_button")
            ) {
                Text("تم")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}

/**
 * Full-Screen Facebook-Style Profile Photo Editor (`FacebookStyleProfilePhotoEditorDialog`):
 * - Opens in a dedicated full-screen modal with ZERO scroll interference.
 * - Uses `rememberUpdatedState` inside `pointerInput` so 1-finger drag (pan) and 2-finger pinch-to-zoom
 *   are 100% smooth and never jump or freeze.
 * - Shows the FULL uncropped photo under a translucent dark mask with a circular profile window cutout in the center.
 * - Includes quick buttons for "إظهار الصورة كاملة بالدائرة" (Fit Full Photo), "تركيز على الوجه" (Center Face),
 *   smooth zoom slider with `-` / `+` buttons, and 90° rotation.
 */
@Composable
fun FacebookStyleProfilePhotoEditorDialog(
    studentName: String,
    sourceBitmap: Bitmap,
    initialFrame: PhotoCropFrame = PhotoCropFrame(),
    saveButtonLabel: String = "حفظ الصورة كاملة وتطبيق كادر البروفايل",
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmFrame: (PhotoCropFrame) -> Unit
) {
    var zoom by remember(sourceBitmap, initialFrame) {
        mutableFloatStateOf(initialFrame.zoom.coerceIn(0.35f, 4.0f))
    }
    var panXFraction by remember(sourceBitmap, initialFrame) {
        mutableFloatStateOf(initialFrame.panXFraction.coerceIn(-1.5f, 1.5f))
    }
    var panYFraction by remember(sourceBitmap, initialFrame) {
        mutableFloatStateOf(initialFrame.panYFraction.coerceIn(-1.5f, 1.5f))
    }
    var rotationDegrees by remember(sourceBitmap, initialFrame) {
        mutableIntStateOf(((initialFrame.rotationDegrees % 360) + 360) % 360)
    }

    val rotatedBitmap = remember(sourceBitmap, rotationDegrees) {
        val rot = ((rotationDegrees % 360) + 360) % 360
        if (rot != 0) {
            val matrix = Matrix().apply { postRotate(rot.toFloat()) }
            Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, matrix, true)
        } else {
            sourceBitmap
        }
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val circleDiameterPx = remember(canvasSize) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            min(canvasSize.width, canvasSize.height) * 0.78f
        } else {
            600f
        }
    }

    // Keep fresh state references for smooth 60fps gesture tracking without stale closure values
    val currentZoom by rememberUpdatedState(zoom)
    val currentPanX by rememberUpdatedState(panXFraction)
    val currentPanY by rememberUpdatedState(panYFraction)
    val currentCircleDiameter by rememberUpdatedState(circleDiameterPx)

    val fitFullZoom = remember(sourceBitmap, rotationDegrees) {
        StudentAvatarImageHelper.computeFitFullPhotoZoom(
            width = sourceBitmap.width,
            height = sourceBitmap.height,
            rotationDegrees = rotationDegrees
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF060911))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("interactive_photo_cropper_section"),
            color = Color(0xFF060911)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // --- Top App Bar ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "ضبط صورة البروفايل: $studentName",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "اسحب الصورة بإصبعك على الوجه • الصورة تُحفظ كاملة بدون قص",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // --- Interactive Facebook-Style Full Canvas with Circular Cutout Window ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF060911))
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(rotatedBitmap) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val d = currentCircleDiameter.coerceAtLeast(180f)
                                val updatedZoom = (currentZoom * gestureZoom).coerceIn(0.35f, 4.0f)
                                val updatedPanX = (currentPanX + (pan.x / d)).coerceIn(-1.5f, 1.5f)
                                val updatedPanY = (currentPanY + (pan.y / d)).coerceIn(-1.5f, 1.5f)
                                zoom = updatedZoom
                                panXFraction = updatedPanX
                                panYFraction = updatedPanY
                            }
                        }
                        .testTag("photo_crop_canvas_box"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasW = size.width
                        val canvasH = size.height
                        if (canvasW <= 0f || canvasH <= 0f) return@Canvas

                        val d = min(canvasW, canvasH) * 0.78f
                        val radius = d / 2f
                        val centerX = canvasW / 2f
                        val centerY = canvasH / 2f

                        val imgW = rotatedBitmap.width.toFloat().coerceAtLeast(1f)
                        val imgH = rotatedBitmap.height.toFloat().coerceAtLeast(1f)

                        // Exact same math as renderFramedAvatarToBitmap so screen matches avatar 1:1
                        val baseScale = max(d / imgW, d / imgH) * zoom
                        val scaledW = imgW * baseScale
                        val scaledH = imgH * baseScale

                        val left = centerX - (scaledW / 2f) + (panXFraction * d)
                        val top = centerY - (scaledH / 2f) + (panYFraction * d)

                        // Draw the full uncropped image transformed smoothly on the canvas
                        val androidMatrix = Matrix().apply {
                            postScale(baseScale, baseScale)
                            postTranslate(left, top)
                        }
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        drawContext.canvas.nativeCanvas.drawBitmap(rotatedBitmap, androidMatrix, paint)

                        // Draw subtle outline around the full uncropped image so the user sees its full bounds
                        drawRect(
                            color = Color.White.copy(alpha = 0.25f),
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(scaledW, scaledH),
                            style = Stroke(width = 1.dp.toPx())
                        )

                        // Facebook-style semi-transparent dark mask outside the circular profile window
                        val maskPath = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(0f, 0f, canvasW, canvasH))
                            addOval(
                                Rect(
                                    centerX - radius,
                                    centerY - radius,
                                    centerX + radius,
                                    centerY + radius
                                )
                            )
                        }
                        drawPath(
                            path = maskPath,
                            color = Color(0xFF060911).copy(alpha = 0.68f)
                        )

                        // Rule-of-thirds alignment lines inside the circle
                        val thirdOffset = radius * 0.35f
                        val guideColor = Color.White.copy(alpha = 0.22f)
                        drawLine(
                            color = guideColor,
                            start = Offset(centerX - thirdOffset, centerY - radius * 0.92f),
                            end = Offset(centerX - thirdOffset, centerY + radius * 0.92f),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = guideColor,
                            start = Offset(centerX + thirdOffset, centerY - radius * 0.92f),
                            end = Offset(centerX + thirdOffset, centerY + radius * 0.92f),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = guideColor,
                            start = Offset(centerX - radius * 0.92f, centerY - thirdOffset),
                            end = Offset(centerX + radius * 0.92f, centerY - thirdOffset),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = guideColor,
                            start = Offset(centerX - radius * 0.92f, centerY + thirdOffset),
                            end = Offset(centerX + radius * 0.92f, centerY + thirdOffset),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Bright circular profile ring (like Facebook's profile ring)
                        drawCircle(
                            color = Color.White,
                            radius = radius,
                            center = Offset(centerX, centerY),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                        drawCircle(
                            color = Color(0xFFF59E0B),
                            radius = radius + 3.dp.toPx(),
                            center = Offset(centerX, centerY),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                            )
                        )
                    }

                    // Helper badge at top of canvas
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                    ) {
                        Text(
                            text = "ما داخل الدائرة يظهر في البروفايل • الصورة كاملة تُحفظ في الألبوم",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                // --- Bottom Controls Panel ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Smooth Zoom Slider with - and + buttons
                    val zoomPercent = (zoom * 100f).roundToInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "حجم الصورة داخل الدائرة (تكبير / تصغير):",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFE2E8F0)
                        )
                        Text(
                            text = "$zoomPercent%",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { zoom = (zoom - 0.15f).coerceIn(0.35f, 4.0f) },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                                .testTag("photo_crop_zoom_out_button")
                        ) {
                            Text(
                                text = "−",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = zoom,
                            onValueChange = { newZoom ->
                                zoom = newZoom.coerceIn(0.35f, 4.0f)
                            },
                            valueRange = 0.35f..4.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFF59E0B),
                                activeTrackColor = Color(0xFFF59E0B),
                                inactiveTrackColor = Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("photo_crop_zoom_slider")
                        )

                        IconButton(
                            onClick = { zoom = (zoom + 0.15f).coerceIn(0.35f, 4.0f) },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                                .testTag("photo_crop_zoom_in_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "تكبير",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Quick Action Presets Row: Fit Whole Photo inside Circle | Focus Face | Rotate 90°
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                zoom = fitFullZoom
                                panXFraction = 0f
                                panYFraction = 0f
                            },
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("photo_crop_fit_full_button")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إظهار الصورة كاملة", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                zoom = 1.0f
                                panXFraction = 0f
                                panYFraction = 0f
                            },
                            border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("photo_crop_reset_button")
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ملء الدائرة", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                rotationDegrees = (rotationDegrees + 90) % 360
                            },
                            border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(0.85f)
                                .testTag("photo_crop_rotate_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("90°", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Primary Save Button + Cancel Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onConfirmFrame(
                                    PhotoCropFrame(
                                        zoom = zoom,
                                        panXFraction = panXFraction,
                                        panYFraction = panYFraction,
                                        rotationDegrees = rotationDegrees
                                    )
                                )
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("confirm_crop_photo_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = saveButtonLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            border = BorderStroke(1.dp, Color(0xFF475569)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("إلغاء")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated White/Ivory Student Photo Album Card ("الصفحة البيضاء اللي تحت في ملف الطالب"):
 * - Preserves and displays the FULL, UNCROPPED photos of the child.
 * - Self-contained Gallery & Camera launchers + Facebook-Style Full-Screen Profile Photo Editor so
 *   any servant can add photos directly from the student profile dialog or the photo picker dialog.
 * - Allows any servant to tap any photo to view it FULL and UNCROPPED, download it to their phone's
 *   Gallery (`Pictures/kAM_Lite`), re-adjust its circular face framing like Facebook, or set it as main.
 * - Restricts permanent deletion strictly to the Group Admin (`isAdmin == true`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentPhotoAlbumSection(
    student: Student,
    isAdmin: Boolean,
    onAddPhotoFromGallery: (() -> Unit)? = null,
    onAddPhotoFromCamera: (() -> Unit)? = null,
    onUpdatePackedAvatarField: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val albumPhotos = remember(student.id, student.avatarBase64) {
        StudentAvatarImageHelper.getMergedStudentPhotoAlbum(
            context = context,
            studentId = student.id,
            rawAvatarField = student.avatarBase64
        )
    }
    val mainPhoto = remember(student.avatarBase64, albumPhotos) {
        StudentAvatarImageHelper.extractMainPhotoBase64(student.avatarBase64)
            .ifBlank { albumPhotos.firstOrNull().orEmpty() }
    }

    var selectedPhotoForViewer by remember { mutableStateOf<String?>(null) }
    var downloadStatusMessage by remember { mutableStateOf<String?>(null) }

    // Self-contained Facebook-style editor state so adding/editing from StudentDetailsAndHistoryDialog works directly
    var localEditorBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var localEditorExistingEntry by remember { mutableStateOf<String?>(null) }
    var localEditorInitialFrame by remember { mutableStateOf(PhotoCropFrame()) }
    var localEditorSaveAsMain by remember { mutableStateOf(false) }
    var isLocalSaving by remember { mutableStateOf(false) }

    val internalGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    StudentAvatarImageHelper.decodeBitmapFromUri(context, uri)
                }
                if (decoded != null) {
                    localEditorExistingEntry = null
                    localEditorInitialFrame = PhotoCropFrame()
                    localEditorSaveAsMain = albumPhotos.isEmpty()
                    localEditorBitmap = decoded
                }
            }
        }
    }

    val internalCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            localEditorExistingEntry = null
            localEditorInitialFrame = PhotoCropFrame()
            localEditorSaveAsMain = albumPhotos.isEmpty()
            localEditorBitmap = bitmap
        }
    }

    val activeLocalEditorBmp = localEditorBitmap
    if (activeLocalEditorBmp != null) {
        FacebookStyleProfilePhotoEditorDialog(
            studentName = student.name,
            sourceBitmap = activeLocalEditorBmp,
            initialFrame = localEditorInitialFrame,
            saveButtonLabel = if (localEditorExistingEntry != null) {
                "حفظ تعديل مكان الوجه بالدائرة"
            } else {
                "حفظ الصورة كاملة في ألبوم الطفل"
            },
            isSaving = isLocalSaving,
            onDismiss = {
                localEditorBitmap = null
                localEditorExistingEntry = null
            },
            onConfirmFrame = { chosenFrame ->
                isLocalSaving = true
                scope.launch {
                    val existingEntry = localEditorExistingEntry
                    val updatedField = withContext(Dispatchers.Default) {
                        if (existingEntry != null) {
                            StudentAvatarImageHelper.updatePhotoFramingInAlbum(
                                context = context,
                                studentId = student.id,
                                currentRawAvatarField = student.avatarBase64,
                                targetPhotoEntry = existingEntry,
                                newFrame = chosenFrame,
                                promoteToMain = localEditorSaveAsMain
                            )
                        } else {
                            val encodedFull = StudentAvatarImageHelper.encodeFullPhotoWithCropMetadata(
                                context = context,
                                source = activeLocalEditorBmp,
                                zoom = chosenFrame.zoom,
                                panXFraction = chosenFrame.panXFraction,
                                panYFraction = chosenFrame.panYFraction,
                                rotationDegrees = chosenFrame.rotationDegrees
                            )
                            StudentAvatarImageHelper.addPhotoPreservingOldAlbum(
                                context = context,
                                studentId = student.id,
                                currentRawAvatarField = student.avatarBase64,
                                newPhotoBase64 = encodedFull,
                                setAsMain = localEditorSaveAsMain
                            )
                        }
                    }
                    isLocalSaving = false
                    localEditorBitmap = null
                    localEditorExistingEntry = null
                    if (updatedField.isNotBlank()) {
                        onUpdatePackedAvatarField(updatedField)
                        downloadStatusMessage = "تم حفظ الصورة كاملة وضبط كادر البروفايل بنجاح ✓"
                    }
                }
            }
        )
    }

    // White / Warm Ivory Page Card at the bottom of the student profile
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFFBEB),
        border = BorderStroke(1.5.dp, Color(0xFFF59E0B).copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("student_photo_album_white_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ألبوم صور الطفل الكاملة المحفوظة (${albumPhotos.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "الصور تُحفظ هنا كاملةً بدون قص • اضغط على أي صورة لعرضها كاملة أو تحميلها على هاتفك أو ضبط مكان الوجه",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF475569)
                    )
                }
            }

            downloadStatusMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF065F46),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Add Photo to Album Buttons Row (available for any servant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (onAddPhotoFromGallery != null) {
                            onAddPhotoFromGallery()
                        } else {
                            internalGalleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD97706),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("add_photo_to_student_album_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة صورة للألبوم", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        if (onAddPhotoFromCamera != null) {
                            onAddPhotoFromCamera()
                        } else {
                            internalCameraLauncher.launch(null)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFB45309)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("add_camera_photo_to_student_album_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color(0xFFB45309),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "كاميرا",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFB45309),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (albumPhotos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد صور محفوظة في ألبوم الطفل حتى الآن.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    albumPhotos.forEachIndexed { idx, photoEntry ->
                        val isMain = photoEntry == mainPhoto
                        AlbumPhotoThumbnailItem(
                            photoEntry = photoEntry,
                            isMain = isMain,
                            index = idx,
                            onClick = { selectedPhotoForViewer = photoEntry },
                            onQuickDownload = {
                                scope.launch {
                                    val res = withContext(Dispatchers.IO) {
                                        StudentAvatarImageHelper.saveFullPhotoToDeviceGallery(
                                            context = context,
                                            studentName = student.name,
                                            photoEntry = photoEntry
                                        )
                                    }
                                    res.onSuccess {
                                        val msg = "تم تحميل الصورة الكاملة في معرض الهاتف (مجلد kAM Lite) ✓"
                                        downloadStatusMessage = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }.onFailure {
                                        val err = "تعذر تحميل الصورة"
                                        downloadStatusMessage = err
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    selectedPhotoForViewer?.let { targetPhoto ->
        val isCurrentlyMain = targetPhoto == mainPhoto
        StudentAlbumPhotoViewerDialog(
            studentName = student.name,
            photoEntry = targetPhoto,
            isCurrentlyMain = isCurrentlyMain,
            isAdmin = isAdmin,
            onDismiss = { selectedPhotoForViewer = null },
            onDownloadFullPhoto = {
                scope.launch {
                    val res = withContext(Dispatchers.IO) {
                        StudentAvatarImageHelper.saveFullPhotoToDeviceGallery(
                            context = context,
                            studentName = student.name,
                            photoEntry = targetPhoto
                        )
                    }
                    res.onSuccess {
                        val msg = "تم حفظ الصورة الكاملة في معرض الهاتف (مجلد kAM Lite) ✓"
                        downloadStatusMessage = msg
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }.onFailure {
                        Toast.makeText(context, "تعذر تحميل الصورة", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onAdjustFaceFraming = {
                selectedPhotoForViewer = null
                scope.launch {
                    val fullBmp = withContext(Dispatchers.IO) {
                        StudentAvatarImageHelper.decodeFullUncroppedAndroidBitmap(context, targetPhoto)
                    }
                    if (fullBmp != null) {
                        val (_, savedFrame) = StudentAvatarImageHelper.parsePhotoEntry(targetPhoto)
                        localEditorExistingEntry = targetPhoto
                        localEditorInitialFrame = savedFrame
                        localEditorSaveAsMain = isCurrentlyMain
                        localEditorBitmap = fullBmp
                    }
                }
            },
            onSetAsMainPhoto = {
                val updated = StudentAvatarImageHelper.promoteAlbumPhotoToMain(
                    context = context,
                    studentId = student.id,
                    currentRawAvatarField = student.avatarBase64,
                    targetPhotoBase64 = targetPhoto
                )
                onUpdatePackedAvatarField(updated)
                selectedPhotoForViewer = null
            },
            onAdminDeletePhoto = {
                val updated = StudentAvatarImageHelper.removePhotoFromAlbum(
                    context = context,
                    studentId = student.id,
                    currentRawAvatarField = student.avatarBase64,
                    photoToRemoveBase64 = targetPhoto
                )
                onUpdatePackedAvatarField(updated)
                selectedPhotoForViewer = null
            }
        )
    }
}

@Composable
private fun AlbumPhotoThumbnailItem(
    photoEntry: String,
    isMain: Boolean,
    index: Int,
    onClick: () -> Unit,
    onQuickDownload: () -> Unit
) {
    val context = LocalContext.current
    var fullThumbBitmap by remember(photoEntry) {
        mutableStateOf(StudentAvatarImageHelper.getCachedFullPhotoBitmap(photoEntry))
    }
    LaunchedEffect(photoEntry) {
        if (fullThumbBitmap == null && photoEntry.isNotBlank()) {
            fullThumbBitmap = withContext(Dispatchers.Default) {
                StudentAvatarImageHelper.decodeFullPhotoToImageBitmap(context, photoEntry)
            }
        }
    }

    Box(
        modifier = Modifier
            .size(86.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .border(
                width = if (isMain) 2.5.dp else 1.dp,
                color = if (isMain) Color(0xFFD97706) else Color(0xFF94A3B8),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag("student_album_photo_$index")
    ) {
        fullThumbBitmap?.let { bmp ->
            // Show the FULL uncropped image inside the album card so nothing is cut off
            Image(
                bitmap = bmp,
                contentDescription = "صورة كاملة محفوظة ${index + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isMain) {
            Surface(
                shape = RoundedCornerShape(bottomEnd = 8.dp),
                color = Color(0xFFD97706),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "الرئيسية",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // One-tap Download Badge on the bottom-end corner of each album photo
        Surface(
            shape = CircleShape,
            color = Color(0xFF065F46).copy(alpha = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(26.dp)
                .clickable(onClick = onQuickDownload)
                .testTag("quick_download_album_photo_$index")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "تحميل الصورة على الجهاز",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * Full Uncropped Photo Viewer Dialog (`StudentAlbumPhotoViewerDialog`):
 * - Displays the COMPLETE, UNCROPPED photo (`ContentScale.Fit`) alongside a preview of its circular avatar framing.
 * - Provides a one-tap **"تحميل الصورة كاملة على الجهاز"** button for all servants.
 * - Provides a **"تعديل مكان الوجه في الدائرة (مثل فيسبوك)"** button to adjust the circular avatar framing without cropping the saved photo.
 * - Provides **"تعيين كصورة رئيسية للطفل"** for all servants, and **"حذف الصورة نهائياً"** for the Group Admin only.
 */
@Composable
private fun StudentAlbumPhotoViewerDialog(
    studentName: String,
    photoEntry: String,
    isCurrentlyMain: Boolean,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onDownloadFullPhoto: () -> Unit,
    onAdjustFaceFraming: () -> Unit,
    onSetAsMainPhoto: () -> Unit,
    onAdminDeletePhoto: () -> Unit
) {
    val context = LocalContext.current
    var fullBitmap by remember(photoEntry) {
        mutableStateOf(StudentAvatarImageHelper.getCachedFullPhotoBitmap(photoEntry))
    }
    var framedCircleBitmap by remember(photoEntry) {
        mutableStateOf(StudentAvatarImageHelper.getCachedBitmap(photoEntry))
    }
    var downloadConfirmed by remember { mutableStateOf(false) }

    LaunchedEffect(photoEntry) {
        if (photoEntry.isNotBlank()) {
            if (fullBitmap == null) {
                fullBitmap = withContext(Dispatchers.Default) {
                    StudentAvatarImageHelper.decodeFullPhotoToImageBitmap(context, photoEntry)
                }
            }
            if (framedCircleBitmap == null) {
                framedCircleBitmap = withContext(Dispatchers.Default) {
                    StudentAvatarImageHelper.decodeBase64ToImageBitmap(photoEntry)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الصورة الكاملة: $studentName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // Mini preview of how the circular avatar looks
                framedCircleBitmap?.let { circleBmp ->
                    Image(
                        bitmap = circleBmp,
                        contentDescription = "معاينة الدائرة",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color(0xFFF59E0B), CircleShape)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Full Uncropped Photo Display (ContentScale.Fit so 100% of the original photo is shown)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 320.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF090D16))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    fullBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp,
                            contentDescription = "الصورة الأصلية الكاملة بدون قص",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                        )
                    }
                }

                if (downloadConfirmed) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF065F46),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "تم حفظ الصورة الكاملة في معرض الهاتف (مجلد Pictures/kAM_Lite) ✓",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }

                // 1. Download Full Photo to Device Button (Available to ALL Servants)
                Button(
                    onClick = {
                        downloadConfirmed = true
                        onDownloadFullPhoto()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF059669),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_student_photo_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تحميل الصورة كاملة على الجهاز (المعرض)", fontWeight = FontWeight.Bold)
                }

                // 2. Adjust Circular Face Framing Like Facebook (Available to ALL Servants)
                OutlinedButton(
                    onClick = onAdjustFaceFraming,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adjust_album_photo_framing_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تعديل مكان الوجه بالدائرة (مثل فيسبوك)")
                }

                // 3. Set as Main Profile Photo
                if (!isCurrentlyMain) {
                    Button(
                        onClick = onSetAsMainPhoto,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("set_album_photo_as_main_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تعيين كصورة رئيسية للطفل")
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "هذه هي الصورة الرئيسية الحالية للطفل ✓",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // 4. Strictly Admin only: delete a photo from the album
                if (isAdmin) {
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = onAdminDeletePhoto,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_delete_album_photo_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حذف هذه الصورة نهائياً من الألبوم (للأمين)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}
