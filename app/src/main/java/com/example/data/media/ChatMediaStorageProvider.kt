package com.example.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.util.LruCache
import com.example.data.model.ChatMessage
import com.example.data.model.ChatMessageType
import com.example.data.model.PreparedChatMediaPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Single replaceable interface for Chat Media Storage:
 * - Decouples the Chat UI and ViewModel from how images and voice notes are stored and retrieved.
 * - On the Spark free tier, [InlineFirestoreChatMediaStorageProvider] compresses images (<60KB, max 1024px)
 *   and voice notes (<100KB, max 30s mono) into Base64 inside the message document, and persists them
 *   locally in `filesDir/chat_media_cache` after first display so they are never decoded or fetched twice.
 * - If the app ever migrates to paid Cloud Storage in the future, only a new implementation of this
 *   interface is needed without changing any Chat UI or ViewModel code.
 */
interface ChatMediaStorageProvider {
    suspend fun prepareImagePayload(
        context: Context,
        imageUri: Uri,
        captionText: String = ""
    ): Result<PreparedChatMediaPayload>

    suspend fun prepareEditedImagePayload(
        bitmap: Bitmap,
        captionText: String = ""
    ): Result<PreparedChatMediaPayload>

    suspend fun prepareAudioPayload(
        audioFile: File,
        durationSec: Int
    ): Result<PreparedChatMediaPayload>

    suspend fun loadOrCacheImageBitmap(
        context: Context,
        message: ChatMessage
    ): Bitmap?

    suspend fun loadOrCacheAudioFile(
        context: Context,
        message: ChatMessage
    ): File?

    suspend fun saveOutgoingLocalCache(
        context: Context,
        messageId: String,
        payload: PreparedChatMediaPayload
    )

    suspend fun saveImageToDeviceGallery(
        context: Context,
        message: ChatMessage
    ): Result<String>

    suspend fun saveAudioToDeviceDownloads(
        context: Context,
        message: ChatMessage
    ): Result<String>
}

/**
 * Spark-tier implementation of [ChatMediaStorageProvider]:
 * - Stores compressed Base64 inside the Firestore message document (`mediaBase64`).
 * - Caches decoded media files on local disk (`filesDir/chat_media_cache`) on `Dispatchers.IO`
 *   after the first display (and immediately on send).
 */
object InlineFirestoreChatMediaStorageProvider : ChatMediaStorageProvider {
    private const val TAG = "ChatMediaStorage"
    private const val CACHE_DIR_NAME = "chat_media_cache"
    private val SAFE_ID_REGEX = Regex("[^a-zA-Z0-9_-]")

    // Target binary byte ceilings so that Base64 encoding (4/3x) stays strictly below the Firestore rule limits:
    // - 450,000 Base64 chars = 337,500 raw bytes -> we target <= 330,000 raw bytes (< 440,000 Base64 chars < 460KB rule cap)
    // - 100,000 Base64 chars = 75,000 raw bytes -> we target <= 72,000 raw bytes (< 96,000 Base64 chars < 100KB)
    private const val MAX_RAW_IMAGE_BYTES = 330_000
    private const val MAX_RAW_AUDIO_BYTES = 72_000

    private val memoryBitmapCache = object : LruCache<String, Bitmap>(24) {}

    private fun getCacheDir(context: Context): File {
        val dir = File(context.applicationContext.filesDir, CACHE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun imageCacheFile(context: Context, messageId: String): File {
        val safeId = messageId.replace(SAFE_ID_REGEX, "_").take(80)
        return File(getCacheDir(context), "img_$safeId.jpg")
    }

    private fun audioCacheFile(context: Context, messageId: String): File {
        val safeId = messageId.replace(SAFE_ID_REGEX, "_").take(80)
        return File(getCacheDir(context), "aud_$safeId.m4a")
    }

    override suspend fun prepareImagePayload(
        context: Context,
        imageUri: Uri,
        captionText: String
    ): Result<PreparedChatMediaPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.applicationContext.contentResolver

            // 1. Decode bounds first without allocating pixels
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            }
            val rawW = boundsOptions.outWidth
            val rawH = boundsOptions.outHeight
            require(rawW > 0 && rawH > 0) { "تعذر قراءة أبعاد الصورة" }

            // 2. Calculate sample size to get close to 1024px
            var sampleSize = 1
            while (max(rawW / sampleSize, rawH / sampleSize) > ChatMessage.MAX_IMAGE_DIMENSION_PX * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = resolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: throw IllegalStateException("تعذر فتح الصورة المختارة")

            compressAndBuildImagePayload(
                sourceBitmap = decoded,
                captionText = captionText,
                recycleSourceWhenScaled = true
            )
        }
    }

    override suspend fun prepareEditedImagePayload(
        bitmap: Bitmap,
        captionText: String
    ): Result<PreparedChatMediaPayload> = withContext(Dispatchers.IO) {
        runCatching {
            require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
                "تعذر قراءة الصورة المعدّلة"
            }
            compressAndBuildImagePayload(
                sourceBitmap = bitmap,
                captionText = captionText,
                recycleSourceWhenScaled = false
            )
        }
    }

    private fun compressAndBuildImagePayload(
        sourceBitmap: Bitmap,
        captionText: String,
        recycleSourceWhenScaled: Boolean
    ): PreparedChatMediaPayload {
        // Scale down proportionally only if longest edge exceeds MAX_IMAGE_DIMENSION_PX (1440px)
        val maxDim = max(sourceBitmap.width, sourceBitmap.height)
        var currentBitmap = if (maxDim > ChatMessage.MAX_IMAGE_DIMENSION_PX) {
            val ratio = ChatMessage.MAX_IMAGE_DIMENSION_PX.toFloat() / maxDim.toFloat()
            val targetW = (sourceBitmap.width * ratio).roundToInt().coerceAtLeast(1)
            val targetH = (sourceBitmap.height * ratio).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(sourceBitmap, targetW, targetH, true).also {
                if (recycleSourceWhenScaled && it !== sourceBitmap) sourceBitmap.recycle()
            }
        } else {
            sourceBitmap
        }
        var ownsCurrentBitmap = currentBitmap !== sourceBitmap || recycleSourceWhenScaled

        // Compress with high clarity first; gently downscale only if huge raw image exceeds budget
        var compressedBytes = compressBitmapToTarget(currentBitmap, MAX_RAW_IMAGE_BYTES)
        var scaleAttempts = 0
        while (compressedBytes.size > MAX_RAW_IMAGE_BYTES && scaleAttempts < 3) {
            scaleAttempts++
            val nextW = (currentBitmap.width * 0.85f).roundToInt().coerceAtLeast(480)
            val nextH = (currentBitmap.height * 0.85f).roundToInt().coerceAtLeast(480)
            val smaller = Bitmap.createScaledBitmap(currentBitmap, nextW, nextH, true)
            if (ownsCurrentBitmap && smaller !== currentBitmap) {
                currentBitmap.recycle()
            }
            currentBitmap = smaller
            ownsCurrentBitmap = true
            compressedBytes = compressBitmapToTarget(currentBitmap, MAX_RAW_IMAGE_BYTES)
        }

        val finalW = currentBitmap.width
        val finalH = currentBitmap.height
        val base64Str = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
        require(base64Str.length <= ChatMessage.MAX_IMAGE_BASE64_CHARS) {
            "حجم الصورة كبير جداً حتى بعد الضغط"
        }

        val cleanCaption = captionText.trim().take(ChatMessage.MAX_TEXT_LENGTH)
            .ifBlank { ChatMessage.IMAGE_DEFAULT_LABEL }

        return PreparedChatMediaPayload(
            type = ChatMessageType.IMAGE,
            textLabel = cleanCaption,
            mediaBase64 = base64Str,
            mediaMimeType = "image/jpeg",
            imageWidth = finalW,
            imageHeight = finalH
        )
    }

    private fun compressBitmapToTarget(bitmap: Bitmap, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var quality = 90
        while (quality >= 66) {
            out.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= maxBytes) {
                return out.toByteArray()
            }
            quality -= 6
        }
        return out.toByteArray()
    }

    override suspend fun prepareAudioPayload(
        audioFile: File,
        durationSec: Int
    ): Result<PreparedChatMediaPayload> = withContext(Dispatchers.IO) {
        runCatching {
            require(audioFile.exists() && audioFile.length() > 0L) { "ملف التسجيل الصوتي غير موجود" }
            val safeDuration = durationSec.coerceIn(1, ChatMessage.MAX_AUDIO_DURATION_SEC)
            val bytes = audioFile.readBytes()
            require(bytes.size <= MAX_RAW_AUDIO_BYTES) {
                "حجم التسجيل الصوتي تجاوز الحد المسموح (100KB)"
            }
            val base64Str = Base64.encodeToString(bytes, Base64.NO_WRAP)
            require(base64Str.length <= ChatMessage.MAX_AUDIO_BASE64_CHARS) {
                "حجم التسجيل الصوتي تجاوز 100KB"
            }

            PreparedChatMediaPayload(
                type = ChatMessageType.AUDIO,
                textLabel = "${ChatMessage.AUDIO_DEFAULT_LABEL} (${ safeDuration } ث)",
                mediaBase64 = base64Str,
                mediaMimeType = "audio/mp4",
                audioDurationSec = safeDuration
            )
        }
    }

    override suspend fun saveOutgoingLocalCache(
        context: Context,
        messageId: String,
        payload: PreparedChatMediaPayload
    ) = withContext(Dispatchers.IO) {
        if (messageId.isBlank() || payload.mediaBase64.isBlank()) return@withContext
        runCatching {
            val bytes = Base64.decode(payload.mediaBase64, Base64.DEFAULT)
            when (payload.type) {
                ChatMessageType.IMAGE -> {
                    val file = imageCacheFile(context, messageId)
                    file.writeBytes(bytes)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bmp ->
                        memoryBitmapCache.put(messageId, bmp)
                    }
                }
                ChatMessageType.AUDIO -> {
                    val file = audioCacheFile(context, messageId)
                    file.writeBytes(bytes)
                }
                else -> Unit
            }
        }.onFailure { err ->
            Log.w(TAG, "saveOutgoingLocalCache($messageId) failed: ${err.message}")
        }
    }

    override suspend fun loadOrCacheImageBitmap(
        context: Context,
        message: ChatMessage
    ): Bitmap? {
        if (message.isDeleted || message.id.isBlank()) return null
        memoryBitmapCache.get(message.id)?.let { return it }

        return withContext(Dispatchers.IO) {
            memoryBitmapCache.get(message.id)?.let { return@withContext it }
            runCatching {
                val file = imageCacheFile(context, message.id)
                if (file.exists() && file.length() > 0L) {
                    val diskBmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (diskBmp != null) {
                        memoryBitmapCache.put(message.id, diskBmp)
                        return@runCatching diskBmp
                    }
                }

                if (message.mediaBase64.isNotBlank()) {
                    val bytes = Base64.decode(message.mediaBase64, Base64.DEFAULT)
                    // Persist locally on disk after first decode so future displays read from disk/LruCache
                    file.writeBytes(bytes)
                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (decoded != null) {
                        memoryBitmapCache.put(message.id, decoded)
                    }
                    decoded
                } else {
                    null
                }
            }.getOrNull()
        }
    }

    override suspend fun loadOrCacheAudioFile(
        context: Context,
        message: ChatMessage
    ): File? = withContext(Dispatchers.IO) {
        if (message.isDeleted || message.id.isBlank()) return@withContext null
        runCatching {
            val file = audioCacheFile(context, message.id)
            if (file.exists() && file.length() > 0L) {
                return@runCatching file
            }
            if (message.mediaBase64.isNotBlank()) {
                val bytes = Base64.decode(message.mediaBase64, Base64.DEFAULT)
                file.writeBytes(bytes)
                file
            } else {
                null
            }
        }.getOrNull()
    }

    override suspend fun saveImageToDeviceGallery(
        context: Context,
        message: ChatMessage
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(!message.isDeleted && message.id.isNotBlank()) { "الصورة غير متوفرة للحفظ" }
            val appCtx = context.applicationContext
            val cachedFile = imageCacheFile(appCtx, message.id)
            val bytes: ByteArray = when {
                cachedFile.exists() && cachedFile.length() > 0L -> cachedFile.readBytes()
                message.mediaBase64.isNotBlank() -> {
                    Base64.decode(message.mediaBase64, Base64.DEFAULT).also { decoded ->
                        runCatching { cachedFile.writeBytes(decoded) }
                    }
                }
                else -> throw IllegalStateException("بيانات الصورة غير متوفرة")
            }

            val safeId = message.id.replace(SAFE_ID_REGEX, "_").take(24)
            val fileName = "kAM_Chat_${safeId}_${System.currentTimeMillis()}.jpg"
            val resolver = appCtx.contentResolver
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
            val insertedUri = runCatching { resolver.insert(collectionUri, contentValues) }.getOrNull()
            if (insertedUri != null) {
                resolver.openOutputStream(insertedUri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                } ?: throw IllegalStateException("تعذر الكتابة في معرض الصور")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(
                        insertedUri,
                        ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                        null,
                        null
                    )
                }
                "Pictures/kAM_Lite/$fileName"
            } else {
                val fallbackDir = File(
                    appCtx.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "kAM_Lite"
                ).apply { mkdirs() }
                val fallbackFile = File(fallbackDir, fileName)
                FileOutputStream(fallbackFile).use { out ->
                    out.write(bytes)
                    out.flush()
                }
                fallbackFile.absolutePath
            }
        }
    }

    override suspend fun saveAudioToDeviceDownloads(
        context: Context,
        message: ChatMessage
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(!message.isDeleted && message.id.isNotBlank()) { "التسجيل الصوتي غير متوفر للحفظ" }
            val appCtx = context.applicationContext
            val cachedFile = loadOrCacheAudioFile(appCtx, message)
            val bytes = when {
                cachedFile != null && cachedFile.exists() && cachedFile.length() > 0L -> cachedFile.readBytes()
                message.mediaBase64.isNotBlank() -> Base64.decode(message.mediaBase64, Base64.DEFAULT)
                else -> throw IllegalStateException("ملف التسجيل غير متوفر")
            }

            val safeId = message.id.replace(SAFE_ID_REGEX, "_").take(24)
            val fileName = "kAM_Voice_${safeId}_${System.currentTimeMillis()}.m4a"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = appCtx.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/kAM_Lite")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val collectionUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val insertedUri = runCatching { resolver.insert(collectionUri, contentValues) }.getOrNull()
                if (insertedUri != null) {
                    resolver.openOutputStream(insertedUri)?.use { out ->
                        out.write(bytes)
                        out.flush()
                    } ?: throw IllegalStateException("تعذر حفظ التسجيل في التنزيلات")
                    resolver.update(
                        insertedUri,
                        ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                        null,
                        null
                    )
                    return@runCatching "Downloads/kAM_Lite/$fileName"
                }
            }
            val fallbackDir = File(
                appCtx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "kAM_Lite"
            ).apply { mkdirs() }
            val fallbackFile = File(fallbackDir, fileName)
            FileOutputStream(fallbackFile).use { out ->
                out.write(bytes)
                out.flush()
            }
            fallbackFile.absolutePath
        }
    }
}

/**
 * Low-bitrate mono voice recorder (max 30 seconds, <100KB) using Android's [MediaRecorder].
 * Configured with 1 channel (mono), 16 kHz sampling rate, and ~14 kbps AAC bitrate so a 30-second
 * clip is ~52KB binary (~70KB Base64, well below the 100KB limit).
 */
class ChatVoiceRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startElapsedMillis: Long = 0L

    val isRecording: Boolean
        get() = recorder != null

    fun elapsedSeconds(): Int {
        if (recorder == null || startElapsedMillis <= 0L) return 0
        return ((System.currentTimeMillis() - startElapsedMillis) / 1000L)
            .toInt()
            .coerceIn(0, ChatMessage.MAX_AUDIO_DURATION_SEC)
    }

    fun startRecording(
        onMaxDurationReached: ((File, Int) -> Unit)? = null
    ): Result<File> = runCatching {
        stopAndDiscard()
        val tempDir = File(context.applicationContext.cacheDir, "voice_rec_temp")
        if (!tempDir.exists()) tempDir.mkdirs()
        val targetFile = File(tempDir, "rec_${System.currentTimeMillis()}.m4a")

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context.applicationContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mr.setAudioChannels(1) // Mono
        mr.setAudioSamplingRate(16_000) // 16 kHz speech clarity
        mr.setAudioEncodingBitRate(14_000) // 14 kbps -> 30s is ~52.5 KB raw (~70 KB Base64 < 100 KB)
        mr.setMaxDuration(ChatMessage.MAX_AUDIO_DURATION_SEC * 1000)
        mr.setOutputFile(targetFile.absolutePath)
        mr.setOnInfoListener { _, what, _ ->
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                val (savedFile, dur) = stopRecording()
                if (savedFile != null) {
                    onMaxDurationReached?.invoke(savedFile, dur)
                }
            }
        }
        mr.prepare()
        mr.start()

        recorder = mr
        outputFile = targetFile
        startElapsedMillis = System.currentTimeMillis()
        targetFile
    }

    fun stopRecording(): Pair<File?, Int> {
        val currentRecorder = recorder ?: return null to 0
        val file = outputFile
        val elapsedSec = ((System.currentTimeMillis() - startElapsedMillis) / 1000L)
            .toInt()
            .coerceIn(1, ChatMessage.MAX_AUDIO_DURATION_SEC)
        recorder = null
        outputFile = null
        return try {
            currentRecorder.stop()
            currentRecorder.release()
            file to elapsedSec
        } catch (_: Exception) {
            runCatching { currentRecorder.release() }
            runCatching { file?.delete() }
            null to 0
        }
    }

    fun cancelRecording() {
        stopAndDiscard()
    }

    fun stopAndDiscard() {
        val currentRecorder = recorder
        val file = outputFile
        recorder = null
        outputFile = null
        if (currentRecorder != null) {
            runCatching { currentRecorder.stop() }
            runCatching { currentRecorder.release() }
        }
        runCatching { file?.delete() }
    }
}

/**
 * Lightweight [MediaPlayer] wrapper for playing cached voice notes in chat bubbles.
 */
class ChatAudioPlayerController {
    private var mediaPlayer: MediaPlayer? = null
    var activeMessageId: String = ""
        private set

    val isPlaying: Boolean
        get() = runCatching { mediaPlayer?.isPlaying == true }.getOrDefault(false)

    val currentPositionMs: Int
        get() = runCatching { mediaPlayer?.currentPosition ?: 0 }.getOrDefault(0)

    val durationMs: Int
        get() = runCatching { mediaPlayer?.duration ?: 0 }.getOrDefault(0)

    fun playOrToggle(
        messageId: String,
        audioFile: File,
        onCompletion: () -> Unit,
        onStateChanged: () -> Unit
    ) {
        if (activeMessageId == messageId && mediaPlayer != null) {
            runCatching {
                val mp = mediaPlayer!!
                if (mp.isPlaying) {
                    mp.pause()
                } else {
                    mp.start()
                }
                onStateChanged()
            }
            return
        }

        stop()
        runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(audioFile.absolutePath)
            mp.setOnCompletionListener {
                activeMessageId = ""
                onCompletion()
                onStateChanged()
            }
            mp.prepare()
            mp.start()
            mediaPlayer = mp
            activeMessageId = messageId
            onStateChanged()
        }.onFailure {
            stop()
            onStateChanged()
        }
    }

    fun stop() {
        val mp = mediaPlayer
        mediaPlayer = null
        activeMessageId = ""
        if (mp != null) {
            runCatching {
                if (mp.isPlaying) mp.stop()
            }
            runCatching { mp.release() }
        }
    }
}
