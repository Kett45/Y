package com.example.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * Modular, extensible theme definition (`KamThemePaletteSpec`).
 * To add any new theme in the future, simply add a new entry to [KamAppThemePreset] with its palette colors.
 * Currently provides the 2 approved themes requested by the user:
 * 1. DARK (داكن): The signature Royal Dark Navy + Gold/Teal palette.
 * 2. LIGHT (فاتح - للشمس والإضاءة القوية): Pure white background (#FFFFFF), pitch-black text (#090D16),
 *    and deep Navy & Gold high-contrast buttons for effortless outdoor/sunlight readability.
 */
enum class KamAppThemePreset(
    val id: String,
    val titleArabic: String,
    val subtitleArabic: String,
    val isDark: Boolean,
    val isLiveAnimated: Boolean = false,
    val previewBackgroundHex: Long,
    val previewSurfaceHex: Long,
    val previewPrimaryHex: Long,
    val previewSecondaryHex: Long
) {
    DARK(
        id = "DARK",
        titleArabic = "داكن (الافتراضي)",
        subtitleArabic = "خلفية كحلية داكنة مع لمسات ذهبية مريحة للعين",
        isDark = true,
        previewBackgroundHex = 0xFF0B1120,
        previewSurfaceHex = 0xFF111827,
        previewPrimaryHex = 0xFFF59E0B,
        previewSecondaryHex = 0xFF14B8A6
    ),
    LIGHT(
        id = "LIGHT",
        titleArabic = "فاتح (للشمس والإضاءة القوية)",
        subtitleArabic = "خلفية بيضاء نقية ونصوص سوداء وأزرار كحلية وذهبية عالية التباين",
        isDark = false,
        previewBackgroundHex = 0xFFFFFFFF,
        previewSurfaceHex = 0xFFF8FAFC,
        previewPrimaryHex = 0xFF0F2942,
        previewSecondaryHex = 0xFFB45309
    );

    companion object {
        fun fromId(id: String?): KamAppThemePreset {
            if (id.isNullOrBlank()) return DARK
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) }?.let { return it }
            // Map any legacy light theme IDs to LIGHT, and everything else to DARK
            return when (id.uppercase()) {
                "MORNING_PEARL", "JERUSALEM_SAND", "LIGHT" -> LIGHT
                else -> DARK
            }
        }
    }
}

/**
 * Single approved app launcher icon: Star of Bethlehem (نجمة بيت لحم).
 */
enum class KamLauncherIconPreset(
    val id: String,
    val aliasClassName: String,
    val titleArabic: String,
    val subtitleArabic: String,
    val isLiveIcon: Boolean = false,
    val previewBgStartHex: Long,
    val previewBgEndHex: Long,
    val previewAccentHex: Long
) {
    BETHLEHEM_STAR(
        id = "BETHLEHEM_STAR",
        aliasClassName = "com.example.MainActivity",
        titleArabic = "نجمة الكارت الذهبية",
        subtitleArabic = "أيقونة النجمة الثمانية الذهبية المعتمدة للتطبيق",
        isLiveIcon = false,
        previewBgStartHex = 0xFF0B1528,
        previewBgEndHex = 0xFF1D3557,
        previewAccentHex = 0xFFFBBF24
    );

    companion object {
        fun fromId(@Suppress("UNUSED_PARAMETER") id: String?): KamLauncherIconPreset {
            return BETHLEHEM_STAR
        }
    }
}

data class ResolvedKamAppearance(
    val colorScheme: ColorScheme,
    val elevatedSurfaceColor: Color
)

@Composable
fun rememberResolvedKamAppearance(preset: KamAppThemePreset): ResolvedKamAppearance {
    return remember(preset) {
        buildPaletteForPreset(preset)
    }
}

internal fun buildPaletteForPreset(preset: KamAppThemePreset): ResolvedKamAppearance {
    return when (preset) {
        KamAppThemePreset.DARK -> ResolvedKamAppearance(
            colorScheme = darkColorScheme(
                primary = KamPrimaryGold,
                onPrimary = KamOnPrimaryDark,
                primaryContainer = KamPrimaryContainerDark,
                onPrimaryContainer = KamOnPrimaryContainerGold,
                secondary = KamSecondaryTeal,
                onSecondary = KamOnSecondaryDark,
                secondaryContainer = KamSecondaryContainerDark,
                onSecondaryContainer = KamOnSecondaryContainerTeal,
                tertiary = KamTertiarySky,
                background = KamBackgroundDark,
                onBackground = KamTextPrimary,
                surface = KamSurfaceDark,
                onSurface = KamTextPrimary,
                surfaceVariant = KamSurfaceVariantDark,
                onSurfaceVariant = KamTextSecondary,
                outline = KamBorderDark,
                error = KamErrorRed,
                onError = Color.White,
                errorContainer = KamErrorContainerDark,
                onErrorContainer = KamOnErrorContainer
            ),
            elevatedSurfaceColor = KamSurfaceElevatedDark
        )

        KamAppThemePreset.LIGHT -> ResolvedKamAppearance(
            // High-Contrast Sunlight Palette:
            // Pure White (#FFFFFF) background, crisp slate cards (#F8FAFC) with visible borders (#94A3B8),
            // Pitch-black text (#090D16), Deep Royal Navy primary buttons (#0F2942) & Rich Gold accents (#B45309)
            colorScheme = lightColorScheme(
                primary = Color(0xFF0F2942),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFEF3C7),
                onPrimaryContainer = Color(0xFF090D16),
                secondary = Color(0xFFB45309),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFE0F2FE),
                onSecondaryContainer = Color(0xFF082F49),
                tertiary = Color(0xFF0369A1),
                background = Color(0xFFFFFFFF),
                onBackground = Color(0xFF090D16),
                surface = Color(0xFFF8FAFC),
                onSurface = Color(0xFF090D16),
                surfaceVariant = Color(0xFFE2E8F0),
                onSurfaceVariant = Color(0xFF1E293B),
                outline = Color(0xFF64748B),
                error = Color(0xFFB91C1C),
                onError = Color(0xFFFFFFFF),
                errorContainer = Color(0xFFFEE2E2),
                onErrorContainer = Color(0xFF7F1D1D)
            ),
            elevatedSurfaceColor = Color(0xFFE2E8F0)
        )
    }
}

@Stable
class AppAppearanceController(
    private val context: Context?,
    initialTheme: KamAppThemePreset,
    initialIcon: KamLauncherIconPreset,
    initialShowFloatingAddButton: Boolean
) {
    var selectedTheme by mutableStateOf(initialTheme)
        private set

    var selectedIcon by mutableStateOf(initialIcon)
        private set

    var showFloatingAddButton by mutableStateOf(initialShowFloatingAddButton)
        private set

    var onCloudPreferenceSyncRequested: ((themeId: String, showFloatingAdd: Boolean) -> Unit)? = null

    fun selectTheme(preset: KamAppThemePreset, syncToCloud: Boolean = true) {
        selectedTheme = preset
        saveToLocalPrefs()
        if (syncToCloud) {
            onCloudPreferenceSyncRequested?.invoke(selectedTheme.id, showFloatingAddButton)
        }
    }

    fun selectLauncherIcon(@Suppress("UNUSED_PARAMETER") preset: KamLauncherIconPreset): Boolean {
        selectedIcon = KamLauncherIconPreset.BETHLEHEM_STAR
        saveToLocalPrefs()
        return true
    }

    fun updateShowFloatingAddButton(show: Boolean, syncToCloud: Boolean = true) {
        showFloatingAddButton = show
        saveToLocalPrefs()
        if (syncToCloud) {
            onCloudPreferenceSyncRequested?.invoke(selectedTheme.id, showFloatingAddButton)
        }
    }

    fun applyCloudSyncedPreferences(themeId: String, showFloatingAdd: Boolean) {
        val resolvedTheme = KamAppThemePreset.fromId(themeId)
        var changed = false
        if (selectedTheme != resolvedTheme) {
            selectedTheme = resolvedTheme
            changed = true
        }
        if (showFloatingAddButton != showFloatingAdd) {
            showFloatingAddButton = showFloatingAdd
            changed = true
        }
        if (changed) {
            saveToLocalPrefs()
        }
    }

    private fun saveToLocalPrefs() {
        val themeToSave = selectedTheme
        val iconToSave = selectedIcon
        val showFabToSave = showFloatingAddButton
        cachedAppearanceTriple = Triple(themeToSave, iconToSave, showFabToSave)
        val ctx = context?.applicationContext ?: return
        ioScope.launch {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME_ID, themeToSave.id)
                .putString(KEY_ICON_ID, iconToSave.id)
                .putBoolean(KEY_SHOW_FLOATING_ADD_BUTTON, showFabToSave)
                .apply()
        }
    }

    internal fun applyLoadedPrefsFromDisk(
        loadedTheme: KamAppThemePreset,
        loadedIcon: KamLauncherIconPreset,
        loadedShowFab: Boolean
    ) {
        if (selectedTheme != loadedTheme) {
            selectedTheme = loadedTheme
        }
        if (selectedIcon != loadedIcon) {
            selectedIcon = loadedIcon
        }
        if (showFloatingAddButton != loadedShowFab) {
            showFloatingAddButton = loadedShowFab
        }
    }

    companion object {
        private const val PREFS_NAME = "kam_appearance_prefs"
        private const val KEY_THEME_ID = "selected_theme_id"
        private const val KEY_ICON_ID = "selected_launcher_icon_id"
        private const val KEY_SHOW_FLOATING_ADD_BUTTON = "show_floating_add_button"

        private val ioScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        )

        @Volatile
        private var cachedAppearanceTriple: Triple<KamAppThemePreset, KamLauncherIconPreset, Boolean>? = null

        fun preloadAsync(context: Context) {
            if (cachedAppearanceTriple != null) return
            val appCtx = context.applicationContext
            ioScope.launch {
                loadFromDiskInternal(appCtx)
            }
        }

        private fun loadFromDiskInternal(context: Context): Triple<KamAppThemePreset, KamLauncherIconPreset, Boolean> {
            cachedAppearanceTriple?.let { return it }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val theme = KamAppThemePreset.fromId(prefs.getString(KEY_THEME_ID, KamAppThemePreset.DARK.id))
            val icon = KamLauncherIconPreset.BETHLEHEM_STAR
            val showFab = prefs.getBoolean(KEY_SHOW_FLOATING_ADD_BUTTON, true)
            val loaded = Triple(theme, icon, showFab)
            cachedAppearanceTriple = loaded
            return loaded
        }

        fun loadFromContext(context: Context): Triple<KamAppThemePreset, KamLauncherIconPreset, Boolean> {
            cachedAppearanceTriple?.let { return it }
            val isMain = runCatching {
                android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
            }.getOrDefault(false)
            if (isMain) {
                preloadAsync(context)
                return Triple(KamAppThemePreset.DARK, KamLauncherIconPreset.BETHLEHEM_STAR, true)
            }
            return loadFromDiskInternal(context.applicationContext)
        }
    }
}

val LocalAppAppearanceController = staticCompositionLocalOf<AppAppearanceController> {
    AppAppearanceController(
        context = null,
        initialTheme = KamAppThemePreset.DARK,
        initialIcon = KamLauncherIconPreset.BETHLEHEM_STAR,
        initialShowFloatingAddButton = true
    )
}

@Composable
fun rememberAppAppearanceController(): AppAppearanceController {
    val context = LocalContext.current
    val controller = remember(context) {
        val (savedTheme, savedIcon, savedShowFab) = AppAppearanceController.loadFromContext(context)
        AppAppearanceController(
            context = context.applicationContext,
            initialTheme = savedTheme,
            initialIcon = savedIcon,
            initialShowFloatingAddButton = savedShowFab
        )
    }
    androidx.compose.runtime.LaunchedEffect(controller, context) {
        val (diskTheme, diskIcon, diskShowFab) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            AppAppearanceController.loadFromContext(context.applicationContext)
        }
        controller.applyLoadedPrefsFromDisk(diskTheme, diskIcon, diskShowFab)
    }
    return controller
}
