package com.example.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class UiScaleMode {
    AUTO,
    MANUAL;

    companion object {
        fun fromString(raw: String?): UiScaleMode {
            if (raw.isNullOrBlank()) return AUTO
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AUTO
        }
    }
}

@Stable
class UiScaleController(
    private val context: Context?,
    initialMode: UiScaleMode,
    initialManualScale: Float,
    val rawScreenWidthDp: Int,
    val systemDensity: Float
) {
    var mode by mutableStateOf(initialMode)
        private set

    var manualScaleFactor by mutableFloatStateOf(initialManualScale.coerceIn(MIN_SCALE, MAX_SCALE))
        private set

    var onCloudScaleSyncRequested: ((mode: String, scaleFactor: Float) -> Unit)? = null

    val isCompactMobile: Boolean
        get() = rawScreenWidthDp < 600

    val autoRecommendedScale: Float
        get() = computeAutoScaleForWidth(rawScreenWidthDp)

    val effectiveScaleFactor: Float
        get() = when (mode) {
            UiScaleMode.AUTO -> autoRecommendedScale
            UiScaleMode.MANUAL -> manualScaleFactor.coerceIn(MIN_SCALE, MAX_SCALE)
        }

    val effectiveScalePercent: Int
        get() = (effectiveScaleFactor * 100f).roundToInt()

    fun setAutoMode(syncToCloud: Boolean = true) {
        mode = UiScaleMode.AUTO
        savePreferences()
        if (syncToCloud) {
            onCloudScaleSyncRequested?.invoke(mode.name, manualScaleFactor)
        }
    }

    fun setManualScale(scale: Float, syncToCloud: Boolean = true) {
        val clamped = ((scale * 20f).roundToInt() / 20f).coerceIn(MIN_SCALE, MAX_SCALE)
        mode = UiScaleMode.MANUAL
        manualScaleFactor = clamped
        savePreferences()
        if (syncToCloud) {
            onCloudScaleSyncRequested?.invoke(mode.name, manualScaleFactor)
        }
    }

    fun applyCloudSyncedScale(modeStr: String, scaleFactor: Float) {
        val resolvedMode = UiScaleMode.fromString(modeStr)
        val clampedScale = ((scaleFactor * 20f).roundToInt() / 20f).coerceIn(MIN_SCALE, MAX_SCALE)
        var changed = false
        if (mode != resolvedMode) {
            mode = resolvedMode
            changed = true
        }
        if (abs(manualScaleFactor - clampedScale) > 0.005f) {
            manualScaleFactor = clampedScale
            changed = true
        }
        if (changed) {
            savePreferences()
        }
    }

    private fun savePreferences() {
        val modeToSave = mode
        val scaleToSave = manualScaleFactor
        cachedScalePair = modeToSave to scaleToSave
        val ctx = context?.applicationContext ?: return
        ioScope.launch {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, modeToSave.name)
                .putFloat(KEY_MANUAL_SCALE, scaleToSave)
                .apply()
        }
    }

    internal fun applyLoadedPrefsFromDisk(loadedMode: UiScaleMode, loadedScale: Float) {
        if (mode != loadedMode) {
            mode = loadedMode
        }
        val clamped = loadedScale.coerceIn(MIN_SCALE, MAX_SCALE)
        if (abs(manualScaleFactor - clamped) > 0.005f) {
            manualScaleFactor = clamped
        }
    }

    companion object {
        private const val PREFS_NAME = "kam_ui_scale_prefs"
        private const val KEY_MODE = "ui_scale_mode"
        private const val KEY_MANUAL_SCALE = "manual_scale_factor"

        const val MIN_SCALE = 0.70f
        const val MAX_SCALE = 1.30f
        const val PRESET_COMPACT_MOBILE = 0.85f
        const val PRESET_STANDARD = 1.00f
        const val PRESET_LARGE_TABLET = 1.15f

        private val ioScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        )

        @Volatile
        private var cachedScalePair: Pair<UiScaleMode, Float>? = null

        fun preloadAsync(context: Context) {
            if (cachedScalePair != null) return
            val appCtx = context.applicationContext
            ioScope.launch {
                loadFromDiskInternal(appCtx)
            }
        }

        fun computeAutoScaleForWidth(widthDp: Int): Float {
            return when {
                widthDp <= 360 -> 0.85f
                widthDp <= 420 -> 0.88f
                widthDp < 600 -> 0.92f
                else -> 1.00f
            }
        }

        private fun loadFromDiskInternal(context: Context): Pair<UiScaleMode, Float> {
            cachedScalePair?.let { return it }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val modeStr = prefs.getString(KEY_MODE, UiScaleMode.AUTO.name) ?: UiScaleMode.AUTO.name
            val parsedMode = UiScaleMode.fromString(modeStr)
            val savedScale = prefs.getFloat(KEY_MANUAL_SCALE, PRESET_COMPACT_MOBILE).coerceIn(MIN_SCALE, MAX_SCALE)
            val pair = parsedMode to savedScale
            cachedScalePair = pair
            return pair
        }

        fun loadFromContext(context: Context): Pair<UiScaleMode, Float> {
            cachedScalePair?.let { return it }
            val isMain = runCatching {
                android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
            }.getOrDefault(false)
            if (isMain) {
                preloadAsync(context)
                return UiScaleMode.AUTO to PRESET_COMPACT_MOBILE
            }
            return loadFromDiskInternal(context.applicationContext)
        }
    }
}

val LocalUiScaleController = staticCompositionLocalOf<UiScaleController> {
    UiScaleController(
        context = null,
        initialMode = UiScaleMode.AUTO,
        initialManualScale = 1.0f,
        rawScreenWidthDp = 400,
        systemDensity = 2.75f
    )
}

@Composable
fun rememberUiScaleController(): Pair<UiScaleController, Density> {
    val context = LocalContext.current
    val displayMetrics = context.resources.displayMetrics
    val systemDensity = displayMetrics.density.coerceAtLeast(1f)
    val rawWidthDp = (displayMetrics.widthPixels / systemDensity).roundToInt().coerceAtLeast(280)

    val controller = remember(context, rawWidthDp, systemDensity) {
        val (savedMode, savedScale) = UiScaleController.loadFromContext(context)
        UiScaleController(
            context = context.applicationContext,
            initialMode = savedMode,
            initialManualScale = savedScale,
            rawScreenWidthDp = rawWidthDp,
            systemDensity = systemDensity
        )
    }

    androidx.compose.runtime.LaunchedEffect(controller, context) {
        val (diskMode, diskScale) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            UiScaleController.loadFromContext(context.applicationContext)
        }
        controller.applyLoadedPrefsFromDisk(diskMode, diskScale)
    }

    val effectiveDensity = Density(
        density = (systemDensity * controller.effectiveScaleFactor).coerceAtLeast(1f),
        fontScale = 1.0f
    )

    return controller to effectiveDensity
}
