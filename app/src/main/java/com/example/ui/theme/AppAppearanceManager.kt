package com.example.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
 * Unified Mercury × Linear identity across Dark (default) and Light (high-contrast daylight):
 * 1. DARK (داكن): Nocturnal obsidian-indigo surface ladder (#07080D -> #1D202E) with indigo primary (#5E6AD2).
 * 2. LIGHT (فاتح - للشمس والإضاءة القوية): Architectural white (#FBFCFD) + lavender tint (#F3F4FC) + deep indigo ink (#1C1A30).
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
        titleArabic = "ليلي أنيق (الافتراضي)",
        subtitleArabic = "سطوح داكنة متدرجة بهوية نيلية هادئة وحدود دقيقة",
        isDark = true,
        previewBackgroundHex = 0xFF07080D,
        previewSurfaceHex = 0xFF0F1017,
        previewPrimaryHex = 0xFF5E6AD2,
        previewSecondaryHex = 0xFF9CB4E8
    ),
    LIGHT(
        id = "LIGHT",
        titleArabic = "نهاري ناصع (للإضاءة القوية)",
        subtitleArabic = "خلفية بيضاء معمارية بمسحة لافندر وحبر داكن عالي التباين",
        isDark = false,
        previewBackgroundHex = 0xFFFBFCFD,
        previewSurfaceHex = 0xFFFFFFFF,
        previewPrimaryHex = 0xFF4B5CE5,
        previewSecondaryHex = 0xFF554E7B
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
        titleArabic = "نجمة الكارت",
        subtitleArabic = "أيقونة النجمة الثمانية المعتمدة للتطبيق",
        isLiveIcon = false,
        previewBgStartHex = 0xFF07080D,
        previewBgEndHex = 0xFF1E2347,
        previewAccentHex = 0xFFEAB34B
    );

    companion object {
        fun fromId(@Suppress("UNUSED_PARAMETER") id: String?): KamLauncherIconPreset {
            return BETHLEHEM_STAR
        }
    }
}

@Immutable
data class KamExtendedColors(
    val isDark: Boolean,
    val elevatedSurface: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val mutedText: Color,
    val onlineGreen: Color,
    val offlineAmber: Color,
    val rankGold: Color,
    val rankSilver: Color,
    val rankBronze: Color,
    val unassignedBlock: Color,
    val blockBlue: Color,
    val blockEmerald: Color,
    val blockViolet: Color,
    val blockAmber: Color,
    val blockRose: Color,
    val blockCyan: Color,
    val blockOrange: Color,
    val cardAttendance: Color,
    val cardQuiet: Color,
    val cardParticipation: Color,
    val cardPraise: Color,
    val cardGold: Color,
    val cardMemorization: Color,
    val cardConfession: Color,
    val notifyConflict: Color,
    val notifyAbsent: Color,
    val notifyComment: Color,
    val notifyVisitation: Color,
    val notifyMemorization: Color,
    val notifyCardPositive: Color,
    val notifyGeneralSystem: Color
)

data class ResolvedKamAppearance(
    val colorScheme: ColorScheme,
    val elevatedSurfaceColor: Color,
    val extendedColors: KamExtendedColors
)

@Composable
fun rememberResolvedKamAppearance(preset: KamAppThemePreset): ResolvedKamAppearance {
    return remember(preset) {
        buildPaletteForPreset(preset)
    }
}

internal fun buildDarkExtendedColors(): KamExtendedColors = KamExtendedColors(
    isDark = true,
    elevatedSurface = KamSurfaceElevatedDark,
    hairline = KamBorderDark,
    hairlineStrong = KamBorderStrongDark,
    mutedText = KamTextMutedDark,
    onlineGreen = KamOnlineGreenDark,
    offlineAmber = KamOfflineAmberDark,
    rankGold = KamRankGoldDark,
    rankSilver = KamRankSilverDark,
    rankBronze = KamRankBronzeDark,
    unassignedBlock = BlockUnassignedDark,
    blockBlue = BlockBlueDark,
    blockEmerald = BlockEmeraldDark,
    blockViolet = BlockVioletDark,
    blockAmber = BlockAmberDark,
    blockRose = BlockRoseDark,
    blockCyan = BlockCyanDark,
    blockOrange = BlockOrangeDark,
    cardAttendance = CardAttendanceDark,
    cardQuiet = CardQuietDark,
    cardParticipation = CardParticipationDark,
    cardPraise = CardPraiseDark,
    cardGold = CardGoldDark,
    cardMemorization = CardMemorizationDark,
    cardConfession = CardConfessionDark,
    notifyConflict = NotifyConflictOrange,
    notifyAbsent = NotifyAbsentRed,
    notifyComment = NotifyCommentSky,
    notifyVisitation = NotifyVisitationPurple,
    notifyMemorization = NotifyMemorizationTeal,
    notifyCardPositive = NotifyCardGreen,
    notifyGeneralSystem = NotifySystemIndigo
)

internal fun buildLightExtendedColors(): KamExtendedColors = KamExtendedColors(
    isDark = false,
    elevatedSurface = KamSurfaceElevatedLight,
    hairline = KamBorderLight,
    hairlineStrong = KamBorderStrongLight,
    mutedText = KamTextMutedLight,
    onlineGreen = KamOnlineGreenLight,
    offlineAmber = KamOfflineAmberLight,
    rankGold = KamRankGoldLight,
    rankSilver = KamRankSilverLight,
    rankBronze = KamRankBronzeLight,
    unassignedBlock = BlockUnassignedLight,
    blockBlue = BlockBlueLight,
    blockEmerald = BlockEmeraldLight,
    blockViolet = BlockVioletLight,
    blockAmber = BlockAmberLight,
    blockRose = BlockRoseLight,
    blockCyan = BlockCyanLight,
    blockOrange = BlockOrangeLight,
    cardAttendance = CardAttendanceLight,
    cardQuiet = CardQuietLight,
    cardParticipation = CardParticipationLight,
    cardPraise = CardPraiseLight,
    cardGold = CardGoldLight,
    cardMemorization = CardMemorizationLight,
    cardConfession = CardConfessionLight,
    notifyConflict = NotifyConflictOrangeLight,
    notifyAbsent = NotifyAbsentRedLight,
    notifyComment = NotifyCommentSkyLight,
    notifyVisitation = NotifyVisitationPurpleLight,
    notifyMemorization = NotifyMemorizationTealLight,
    notifyCardPositive = NotifyCardGreenLight,
    notifyGeneralSystem = NotifySystemIndigoLight
)

internal fun buildPaletteForPreset(preset: KamAppThemePreset): ResolvedKamAppearance {
    return when (preset) {
        KamAppThemePreset.DARK -> ResolvedKamAppearance(
            colorScheme = darkColorScheme(
                primary = KamPrimaryIndigoDark,
                onPrimary = KamOnPrimaryDark,
                primaryContainer = KamPrimaryContainerDark,
                onPrimaryContainer = KamOnPrimaryContainerDark,
                secondary = KamSecondaryPeriwinkleDark,
                onSecondary = KamOnSecondaryDark,
                secondaryContainer = KamSecondaryContainerDark,
                onSecondaryContainer = KamOnSecondaryContainerDark,
                tertiary = KamTertiarySkyDark,
                onTertiary = KamOnPrimaryDark,
                background = KamBackgroundDark,
                onBackground = KamTextPrimaryDark,
                surface = KamSurfaceDark,
                onSurface = KamTextPrimaryDark,
                surfaceVariant = KamSurfaceVariantDark,
                onSurfaceVariant = KamTextSecondaryDark,
                surfaceContainerLowest = KamBackgroundDark,
                surfaceContainerLow = KamSurfaceDark,
                surfaceContainer = KamSurfaceDark,
                surfaceContainerHigh = KamSurfaceVariantDark,
                surfaceContainerHighest = KamSurfaceElevatedDark,
                outline = KamBorderDark,
                outlineVariant = KamBorderDark,
                error = KamErrorRedDark,
                onError = Color.White,
                errorContainer = KamErrorContainerDark,
                onErrorContainer = KamOnErrorContainerDark
            ),
            elevatedSurfaceColor = KamSurfaceElevatedDark,
            extendedColors = buildDarkExtendedColors()
        )

        KamAppThemePreset.LIGHT -> ResolvedKamAppearance(
            colorScheme = lightColorScheme(
                primary = KamPrimaryIndigoLight,
                onPrimary = KamOnPrimaryLight,
                primaryContainer = KamPrimaryContainerLight,
                onPrimaryContainer = KamOnPrimaryContainerLight,
                secondary = KamSecondaryInkLight,
                onSecondary = KamOnSecondaryLight,
                secondaryContainer = KamSecondaryContainerLight,
                onSecondaryContainer = KamOnSecondaryContainerLight,
                tertiary = KamTertiarySkyLight,
                onTertiary = Color.White,
                background = KamBackgroundLight,
                onBackground = KamTextPrimaryLight,
                surface = KamSurfaceLight,
                onSurface = KamTextPrimaryLight,
                surfaceVariant = KamSurfaceVariantLight,
                onSurfaceVariant = KamTextSecondaryLight,
                surfaceContainerLowest = KamSurfaceLight,
                surfaceContainerLow = KamBackgroundLight,
                surfaceContainer = KamSurfaceLight,
                surfaceContainerHigh = KamSurfaceVariantLight,
                surfaceContainerHighest = KamSurfaceElevatedLight,
                outline = KamBorderLight,
                outlineVariant = KamBorderLight,
                error = KamErrorRedLight,
                onError = Color.White,
                errorContainer = KamErrorContainerLight,
                onErrorContainer = KamOnErrorContainerLight
            ),
            elevatedSurfaceColor = KamSurfaceElevatedLight,
            extendedColors = buildLightExtendedColors()
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
