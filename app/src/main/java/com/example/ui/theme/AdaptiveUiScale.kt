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
import kotlin.math.roundToInt

enum class UiScaleMode {
    AUTO,
    MANUAL
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

    fun setAutoMode() {
        mode = UiScaleMode.AUTO
        savePreferences()
    }

    fun setManualScale(scale: Float) {
        val clamped = ((scale * 20f).roundToInt() / 20f).coerceIn(MIN_SCALE, MAX_SCALE)
        mode = UiScaleMode.MANUAL
        manualScaleFactor = clamped
        savePreferences()
    }

    private fun savePreferences() {
        val ctx = context ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .putFloat(KEY_MANUAL_SCALE, manualScaleFactor)
            .apply()
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

        fun computeAutoScaleForWidth(widthDp: Int): Float {
            return when {
                widthDp <= 360 -> 0.85f
                widthDp <= 420 -> 0.88f
                widthDp < 600 -> 0.92f
                else -> 1.00f
            }
        }

        fun loadFromContext(context: Context): Pair<UiScaleMode, Float> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val modeStr = prefs.getString(KEY_MODE, UiScaleMode.AUTO.name) ?: UiScaleMode.AUTO.name
            val parsedMode = runCatching { UiScaleMode.valueOf(modeStr) }.getOrDefault(UiScaleMode.AUTO)
            val savedScale = prefs.getFloat(KEY_MANUAL_SCALE, PRESET_COMPACT_MOBILE).coerceIn(MIN_SCALE, MAX_SCALE)
            return parsedMode to savedScale
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

    // By applying effectiveScaleFactor to density and normalizing fontScale = 1.0f,
    // both .dp layout dimensions and .sp typography scale uniformly in complete harmony
    // and never suffer from extreme OS font magnification crushing cards on mobile.
    val effectiveDensity = Density(
        density = (systemDensity * controller.effectiveScaleFactor).coerceAtLeast(1f),
        fontScale = 1.0f
    )

    return controller to effectiveDensity
}
