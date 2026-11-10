package com.example.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

/**
 * 9 Interior App Themes:
 * - 7 Distinct Static Themes
 * - 2 Fast-Shifting Glowing Live Neon Animated Themes
 */
enum class KamAppThemePreset(
    val id: String,
    val arabicTitle: String,
    val arabicSubtitle: String,
    val isLiveAnimated: Boolean,
    val previewColors: List<Color>
) {
    // --- 7 Static Themes ---
    ROYAL_GOLD(
        id = "ROYAL_GOLD",
        arabicTitle = "١. الذهبي الملكي الكلاسيكي",
        arabicSubtitle = "كحلي ملكي داكن مع أزرار ذهبية وتركواز",
        isLiveAnimated = false,
        previewColors = listOf(Color(0xFF0B1120), Color(0xFF1E293B), Color(0xFFF59E0B), Color(0xFF14B8A6))
    ),
    COPTIC_CRIMSON(
        id = "COPTIC_CRIMSON",
        arabicTitle = "٢. الياقوت القبطي القرمزي",
        arabicSubtitle = "عنابي قطيفة داكن مع ياقوتي وذهبي دافئ",
        isLiveAnimated = false,
        previewColors = listOf(Color(0xFF1A0810), Color(0xFF331020), Color(0xFFF43F5E), Color(0xFFFBBF24))
    ),
    EMERALD_SANCTUARY(
        id = "EMERALD_SANCTUARY",
        arabicTitle = "٣. الزمرد الروحاني المضيء",
        arabicSubtitle = "أخضر زمردي عميق مع أزرار خضراء مشرقة وذهبي",
        isLiveAnimated = false,
        previewColors = listOf(Color(0xFF061A14), Color(0xFF0F3327), Color(0xFF10B981), Color(0xFFFCD34D))
    ),
    HEAVENLY_SAPPHIRE(
        id = "HEAVENLY_SAPPHIRE",
        arabicTitle = "٤. السماء السماوية المضيئة",
        arabicSubtitle = "أزرق سماوي ملكي مع إضاءة سماوية ساطعة",
        isLiveAnimated = false,
        previewColors = listOf(Color(0xFF071326), Color(0xFF132A4A), Color(0xFF38BDF8), Color(0xFF818CF8))
    ),
    PATRIARCHAL_AMETHYST(
        id = "PATRIARCHAL_AMETHYST",
        arabicTitle = "٥. البنفسجي البطريركي الفخم",
        arabicSubtitle = "بنفسجي ملكي داكن مع أرجواني ووردي مشرق",
        isLiveAnimated = false,
        previewColors = listOf(Color(0xFF130924), Color(0xFF271447), Color(0xFFA855F7), Color(0xFFF472B6))
    ),
    WARM_IVORY_LIGHT(
        id = "WARM_IVORY_LIGHT",
        arabicTitle = "٦. النهاري العاجي المشرق",
        arabicSubtitle = "مظهر نهاري دافئ ومريح للعين بتباين واضح",
        isLiveAnimated = false,
        previewColors = listOf(Color(0xFFFFFBEB), Color(0xFFFEF3C7), Color(0xFFD97706), Color(0xFF0D9488))
    ),
    AMOLED_OBSIDIAN(
        id = "AMOLED_OBSIDIAN",
        arabicTitle = "٧. الليل الفحمي فائق التباين",
        arabicSubtitle = "أسود نقي لشاشات AMOLED مع برتقالي وسماوي",
        isLiveAnimated = false,
        previewColors = listOf(Color(0xFF020409), Color(0xFF111827), Color(0xFFFF6B00), Color(0xFF00E5FF))
    ),

    // --- 2 Live Animated Glowing Neon Themes ---
    LIVE_CYBER_NEON(
        id = "LIVE_CYBER_NEON",
        arabicTitle = "٨. نيون الشفق الحي المتوهج (حي متحرك)",
        arabicSubtitle = "ألوان نيون متوهجة وسريعة التغير (وردي ⇄ بنفسجي ⇄ سماوي ليزري) مع نبض حي",
        isLiveAnimated = true,
        previewColors = listOf(Color(0xFFFF2E93), Color(0xFF9D4EDD), Color(0xFF00F5D4), Color(0xFF120428))
    ),
    LIVE_GOLDEN_EMERALD_FIRE(
        id = "LIVE_GOLDEN_EMERALD_FIRE",
        arabicTitle = "٩. لهيب الذهب والزمرد الحي (حي متحرك)",
        arabicSubtitle = "توهج نيون سريع ونابض (ذهبي مشع ⇄ برتقالي ناري ⇄ أخضر نيون ⇄ أزرق كهربائي)",
        isLiveAnimated = true,
        previewColors = listOf(Color(0xFFFFD60A), Color(0xFFFF5400), Color(0xFF00F59B), Color(0xFF00BBF9))
    );

    companion object {
        fun fromId(id: String?): KamAppThemePreset {
            return entries.firstOrNull { it.id == id } ?: ROYAL_GOLD
        }
    }
}

/**
 * 6 External App Launcher Icons:
 * - 5 Sunday School & Coptic Spiritual Icons
 * - 1 Live Glowing Neon Multi-Color Icon
 */
enum class KamLauncherIconPreset(
    val id: String,
    val arabicTitle: String,
    val arabicSubtitle: String,
    val aliasClassName: String,
    val isLiveIcon: Boolean,
    val bgTopColor: Color,
    val bgBottomColor: Color,
    val primarySymbolColor: Color,
    val accentSymbolColor: Color
) {
    COPTIC_CROSS(
        id = "COPTIC_CROSS",
        arabicTitle = "١. الصليب القبطي الذهبي",
        arabicSubtitle = "صليب قبطي ذهبي مزخرف على خلفية كحلية ملكية (الافتراضي)",
        aliasClassName = "com.example.LauncherAliasCross",
        isLiveIcon = false,
        bgTopColor = Color(0xFF0B132B),
        bgBottomColor = Color(0xFF1C2541),
        primarySymbolColor = Color(0xFFF59E0B),
        accentSymbolColor = Color(0xFFFDE68A)
    ),
    SACRED_CHALICE(
        id = "SACRED_CHALICE",
        arabicTitle = "٢. الكأس والقربانة المقدسة",
        arabicSubtitle = "رمز الكأس الذهبي المشع والقربانة على خلفية ياقوتية",
        aliasClassName = "com.example.LauncherAliasChalice",
        isLiveIcon = false,
        bgTopColor = Color(0xFF3B071E),
        bgBottomColor = Color(0xFF19040D),
        primarySymbolColor = Color(0xFFF59E0B),
        accentSymbolColor = Color(0xFFFDE68A)
    ),
    ROYAL_CROWN(
        id = "ROYAL_CROWN",
        arabicTitle = "٣. التاج الملكي والنجوم",
        arabicSubtitle = "تاج الخدمة الذهبي المرصع بالجواهر على خلفية زمردية",
        aliasClassName = "com.example.LauncherAliasCrown",
        isLiveIcon = false,
        bgTopColor = Color(0xFF062E22),
        bgBottomColor = Color(0xFF031811),
        primarySymbolColor = Color(0xFFF59E0B),
        accentSymbolColor = Color(0xFF10B981)
    ),
    BETHLEHEM_STAR(
        id = "BETHLEHEM_STAR",
        arabicTitle = "٤. نجمة المشرق المضيئة",
        arabicSubtitle = "نجمة بيت لحم الثمانية المشعة على خلفية سماوية",
        aliasClassName = "com.example.LauncherAliasStar",
        isLiveIcon = false,
        bgTopColor = Color(0xFF08203E),
        bgBottomColor = Color(0xFF040D1A),
        primarySymbolColor = Color(0xFFFDE68A),
        accentSymbolColor = Color(0xFF38BDF8)
    ),
    HOLY_BIBLE(
        id = "HOLY_BIBLE",
        arabicTitle = "٥. الكتاب المقدس والصليب",
        arabicSubtitle = "الكتاب المقدس المفتوح والصليب الذهبي على خلفية بنفسجية",
        aliasClassName = "com.example.LauncherAliasBible",
        isLiveIcon = false,
        bgTopColor = Color(0xFF240B36),
        bgBottomColor = Color(0xFF11051C),
        primarySymbolColor = Color(0xFFFDE68A),
        accentSymbolColor = Color(0xFFF43F5E)
    ),
    LIVE_NEON_ICON(
        id = "LIVE_NEON_ICON",
        arabicTitle = "٦. أيقونة النيون الحية المتوهجة (أيقونة حية)",
        arabicSubtitle = "صليب ونجمة نيون متوهجة متعددة الألوان مع نبض حي",
        aliasClassName = "com.example.LauncherAliasLive",
        isLiveIcon = true,
        bgTopColor = Color(0xFF19002E),
        bgBottomColor = Color(0xFF002924),
        primarySymbolColor = Color(0xFFFF2E93),
        accentSymbolColor = Color(0xFF00F5D4)
    );

    companion object {
        fun fromId(id: String?): KamLauncherIconPreset {
            return entries.firstOrNull { it.id == id } ?: COPTIC_CROSS
        }
    }
}

@Stable
class AppAppearanceController(
    private val context: Context?,
    initialTheme: KamAppThemePreset,
    initialIcon: KamLauncherIconPreset,
    initialShowFloatingAddButton: Boolean = true
) {
    var selectedTheme by mutableStateOf(initialTheme)
        private set

    var selectedIcon by mutableStateOf(initialIcon)
        private set

    var showFloatingAddButton by mutableStateOf(initialShowFloatingAddButton)
        private set

    var lastIconStatusMessage by mutableStateOf<String?>(null)
        private set

    fun selectTheme(theme: KamAppThemePreset) {
        selectedTheme = theme
        savePreferences()
    }

    fun updateShowFloatingAddButton(show: Boolean) {
        showFloatingAddButton = show
        savePreferences()
    }

    fun selectLauncherIcon(icon: KamLauncherIconPreset) {
        selectedIcon = icon
        savePreferences()
        val applied = applyLauncherIconToDevice(context, icon)
        lastIconStatusMessage = if (applied) {
            "تم تطبيق «${icon.arabicTitle}» كأيقونة خارجية للتطبيق على شاشة الهاتف الرئيسية بنجاح"
        } else {
            "تم حفظ اختيار «${icon.arabicTitle}»"
        }
    }

    private fun savePreferences() {
        cachedPreferences = Triple(selectedTheme, selectedIcon, showFloatingAddButton)
        val ctx = context ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_ID, selectedTheme.id)
            .putString(KEY_ICON_ID, selectedIcon.id)
            .putBoolean(KEY_SHOW_ADD_FAB, showFloatingAddButton)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "kam_appearance_prefs"
        private const val KEY_THEME_ID = "selected_theme_id"
        private const val KEY_ICON_ID = "selected_launcher_icon_id"
        private const val KEY_SHOW_ADD_FAB = "show_floating_add_button"

        @Volatile
        private var cachedPreferences: Triple<KamAppThemePreset, KamLauncherIconPreset, Boolean>? = null

        fun loadFromContext(context: Context): Triple<KamAppThemePreset, KamLauncherIconPreset, Boolean> {
            cachedPreferences?.let { return it }
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val theme = KamAppThemePreset.fromId(prefs.getString(KEY_THEME_ID, null))
            val icon = KamLauncherIconPreset.fromId(prefs.getString(KEY_ICON_ID, null))
            val showAddFab = prefs.getBoolean(KEY_SHOW_ADD_FAB, true)
            return Triple(theme, icon, showAddFab).also { cachedPreferences = it }
        }

        fun applyLauncherIconToDevice(context: Context?, targetPreset: KamLauncherIconPreset): Boolean {
            val ctx = context ?: return false
            return runCatching {
                val pm = ctx.packageManager
                val pkg = ctx.packageName
                // Enable the selected alias first, then disable the other aliases using DONT_KILL_APP
                val targetComponent = ComponentName(pkg, targetPreset.aliasClassName)
                pm.setComponentEnabledSetting(
                    targetComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )

                KamLauncherIconPreset.entries.forEach { preset ->
                    if (preset != targetPreset) {
                        val comp = ComponentName(pkg, preset.aliasClassName)
                        pm.setComponentEnabledSetting(
                            comp,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                }
                true
            }.getOrDefault(false)
        }
    }
}

val LocalAppAppearanceController = staticCompositionLocalOf<AppAppearanceController> {
    AppAppearanceController(
        context = null,
        initialTheme = KamAppThemePreset.ROYAL_GOLD,
        initialIcon = KamLauncherIconPreset.COPTIC_CROSS,
        initialShowFloatingAddButton = true
    )
}

@Composable
fun rememberAppAppearanceController(): AppAppearanceController {
    val context = LocalContext.current
    return remember(context) {
        val (savedTheme, savedIcon, savedShowFab) = AppAppearanceController.loadFromContext(context)
        AppAppearanceController(
            context = context,
            initialTheme = savedTheme,
            initialIcon = savedIcon,
            initialShowFloatingAddButton = savedShowFab
        )
    }
}

data class ResolvedKamAppearance(
    val colorScheme: ColorScheme,
    val elevatedSurfaceColor: Color,
    val livePulseFraction: Float
)

@Composable
fun rememberResolvedKamAppearance(preset: KamAppThemePreset): ResolvedKamAppearance {
    if (!preset.isLiveAnimated) {
        val (scheme, elevated) = remember(preset) { buildStaticColorScheme(preset) }
        return remember(preset, scheme, elevated) {
            ResolvedKamAppearance(
                colorScheme = scheme,
                elevatedSurfaceColor = elevated,
                livePulseFraction = 0f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "live_neon_theme_transition")

    val rawPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neon_pulse"
    )

    // Quantize the live pulse into 16 smooth steps so MaterialTheme only recomposes ~6 times/sec
    // instead of 60 times/sec, keeping LazyColumn scrolling at a locked 60/120fps.
    val stepIndex = (rawPulse * 16f).toInt().coerceIn(0, 16)
    val pulse = stepIndex / 16f

    return remember(preset, stepIndex) {
        if (preset == KamAppThemePreset.LIVE_CYBER_NEON) {
            val primaryNeon = lerp(Color(0xFFFF2E93), Color(0xFF00F5D4), pulse)
            val secondaryNeon = lerp(Color(0xFF9D4EDD), Color(0xFFFFBE0B), pulse)
            val bgBase = lerp(Color(0xFF0D041C), Color(0xFF041824), pulse)
            val surfaceBase = lerp(Color(0xFF1A0933), Color(0xFF0B2538), pulse)
            val elevatedBase = lerp(Color(0xFF2D1252), Color(0xFF133B57), pulse)
            val containerBase = lerp(Color(0xFF4A0E38), Color(0xFF073B3A), pulse)

            val scheme = darkColorScheme(
                primary = primaryNeon,
                onPrimary = Color(0xFF07020D),
                primaryContainer = containerBase,
                onPrimaryContainer = Color(0xFFFFF0F8),
                secondary = secondaryNeon,
                onSecondary = Color(0xFF07020D),
                secondaryContainer = elevatedBase,
                onSecondaryContainer = Color(0xFFE0FBFC),
                tertiary = Color(0xFF00BBF9),
                background = bgBase,
                onBackground = Color(0xFFF8FAFC),
                surface = surfaceBase,
                onSurface = Color(0xFFF8FAFC),
                surfaceVariant = elevatedBase,
                onSurfaceVariant = Color(0xFFE2E8F0),
                outline = primaryNeon.copy(alpha = 0.65f),
                error = KamErrorRed,
                errorContainer = KamErrorContainerDark,
                onErrorContainer = KamOnErrorContainer
            )
            ResolvedKamAppearance(
                colorScheme = scheme,
                elevatedSurfaceColor = elevatedBase,
                livePulseFraction = pulse
            )
        } else {
            val primaryFire = lerp(Color(0xFFFFD60A), Color(0xFF00F59B), pulse)
            val secondaryFire = lerp(Color(0xFFFF5400), Color(0xFF00BBF9), pulse)
            val bgBase = lerp(Color(0xFF170B02), Color(0xFF031A12), pulse)
            val surfaceBase = lerp(Color(0xFF291405), Color(0xFF072B1F), pulse)
            val elevatedBase = lerp(Color(0xFF3D1F08), Color(0xFF0D4230), pulse)
            val containerBase = lerp(Color(0xFF522806), Color(0xFF094732), pulse)

            val scheme = darkColorScheme(
                primary = primaryFire,
                onPrimary = Color(0xFF080A0C),
                primaryContainer = containerBase,
                onPrimaryContainer = Color(0xFFFFFBEB),
                secondary = secondaryFire,
                onSecondary = Color(0xFF080A0C),
                secondaryContainer = elevatedBase,
                onSecondaryContainer = Color(0xFFECFDF5),
                tertiary = Color(0xFFFF007F),
                background = bgBase,
                onBackground = Color(0xFFF8FAFC),
                surface = surfaceBase,
                onSurface = Color(0xFFF8FAFC),
                surfaceVariant = elevatedBase,
                onSurfaceVariant = Color(0xFFE2E8F0),
                outline = primaryFire.copy(alpha = 0.65f),
                error = KamErrorRed,
                errorContainer = KamErrorContainerDark,
                onErrorContainer = KamOnErrorContainer
            )
            ResolvedKamAppearance(
                colorScheme = scheme,
                elevatedSurfaceColor = elevatedBase,
                livePulseFraction = pulse
            )
        }
    }
}

private fun buildStaticColorScheme(preset: KamAppThemePreset): Pair<ColorScheme, Color> {
    return when (preset) {
        KamAppThemePreset.ROYAL_GOLD -> {
            darkColorScheme(
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
                errorContainer = KamErrorContainerDark,
                onErrorContainer = KamOnErrorContainer
            ) to KamSurfaceElevatedDark
        }

        KamAppThemePreset.COPTIC_CRIMSON -> {
            val elevated = Color(0xFF3B1325)
            darkColorScheme(
                primary = Color(0xFFF43F5E),
                onPrimary = Color(0xFF19040B),
                primaryContainer = Color(0xFF54122B),
                onPrimaryContainer = Color(0xFFFFE4E6),
                secondary = Color(0xFFFBBF24),
                onSecondary = Color(0xFF1F1202),
                secondaryContainer = Color(0xFF472B07),
                onSecondaryContainer = Color(0xFFFEF3C7),
                tertiary = Color(0xFFFB7185),
                background = Color(0xFF14060C),
                onBackground = Color(0xFFFFF1F2),
                surface = Color(0xFF220A15),
                onSurface = Color(0xFFFFF1F2),
                surfaceVariant = Color(0xFF30101F),
                onSurfaceVariant = Color(0xFFFECDD3),
                outline = Color(0xFF6B213E),
                error = KamErrorRed,
                errorContainer = KamErrorContainerDark,
                onErrorContainer = KamOnErrorContainer
            ) to elevated
        }

        KamAppThemePreset.EMERALD_SANCTUARY -> {
            val elevated = Color(0xFF123D2F)
            darkColorScheme(
                primary = Color(0xFF10B981),
                onPrimary = Color(0xFF031F16),
                primaryContainer = Color(0xFF064E3B),
                onPrimaryContainer = Color(0xFFD1FAE5),
                secondary = Color(0xFFFBBF24),
                onSecondary = Color(0xFF1F1202),
                secondaryContainer = Color(0xFF115E59),
                onSecondaryContainer = Color(0xFFCCFBF1),
                tertiary = Color(0xFF34D399),
                background = Color(0xFF04140F),
                onBackground = Color(0xFFECFDF5),
                surface = Color(0xFF09221A),
                onSurface = Color(0xFFECFDF5),
                surfaceVariant = Color(0xFF0E3025),
                onSurfaceVariant = Color(0xFFA7F3D0),
                outline = Color(0xFF1E5643),
                error = KamErrorRed,
                errorContainer = KamErrorContainerDark,
                onErrorContainer = KamOnErrorContainer
            ) to elevated
        }

        KamAppThemePreset.HEAVENLY_SAPPHIRE -> {
            val elevated = Color(0xFF183661)
            darkColorScheme(
                primary = Color(0xFF38BDF8),
                onPrimary = Color(0xFF041629),
                primaryContainer = Color(0xFF0C4A6E),
                onPrimaryContainer = Color(0xFFE0F2FE),
                secondary = Color(0xFF818CF8),
                onSecondary = Color(0xFF0D1030),
                secondaryContainer = Color(0xFF1E3A8A),
                onSecondaryContainer = Color(0xFFDBEAFE),
                tertiary = Color(0xFF22D3EE),
                background = Color(0xFF06101F),
                onBackground = Color(0xFFF0F9FF),
                surface = Color(0xFF0C1D36),
                onSurface = Color(0xFFF0F9FF),
                surfaceVariant = Color(0xFF132A4D),
                onSurfaceVariant = Color(0xFFBAE6FD),
                outline = Color(0xFF254C82),
                error = KamErrorRed,
                errorContainer = KamErrorContainerDark,
                onErrorContainer = KamOnErrorContainer
            ) to elevated
        }

        KamAppThemePreset.PATRIARCHAL_AMETHYST -> {
            val elevated = Color(0xFF32185A)
            darkColorScheme(
                primary = Color(0xFFC084FC),
                onPrimary = Color(0xFF1B0630),
                primaryContainer = Color(0xFF4C1D95),
                onPrimaryContainer = Color(0xFFF3E8FF),
                secondary = Color(0xFFF472B6),
                onSecondary = Color(0xFF290617),
                secondaryContainer = Color(0xFF581C87),
                onSecondaryContainer = Color(0xFFFAE8FF),
                tertiary = Color(0xFFE879F9),
                background = Color(0xFF110721),
                onBackground = Color(0xFFFAF5FF),
                surface = Color(0xFF1C0D36),
                onSurface = Color(0xFFFAF5FF),
                surfaceVariant = Color(0xFF28134B),
                onSurfaceVariant = Color(0xFFE9D5FF),
                outline = Color(0xFF53288E),
                error = KamErrorRed,
                errorContainer = KamErrorContainerDark,
                onErrorContainer = KamOnErrorContainer
            ) to elevated
        }

        KamAppThemePreset.WARM_IVORY_LIGHT -> {
            val elevated = Color(0xFFFDE68A).copy(alpha = 0.45f)
            lightColorScheme(
                primary = Color(0xFFB45309),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFEF3C7),
                onPrimaryContainer = Color(0xFF78350F),
                secondary = Color(0xFF0F766E),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFCCFBF1),
                onSecondaryContainer = Color(0xFF134E4A),
                tertiary = Color(0xFF1D4ED8),
                background = Color(0xFFFFFBEB),
                onBackground = Color(0xFF1E293B),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFFEF3C7),
                onSurfaceVariant = Color(0xFF475569),
                outline = Color(0xFFD97706).copy(alpha = 0.45f),
                error = Color(0xFFDC2626),
                errorContainer = Color(0xFFFEE2E2),
                onErrorContainer = Color(0xFF7F1D1D)
            ) to elevated
        }

        KamAppThemePreset.AMOLED_OBSIDIAN -> {
            val elevated = Color(0xFF161F30)
            darkColorScheme(
                primary = Color(0xFFFF6B00),
                onPrimary = Color(0xFF0A0500),
                primaryContainer = Color(0xFF431407),
                onPrimaryContainer = Color(0xFFFFEDD5),
                secondary = Color(0xFF00E5FF),
                onSecondary = Color(0xFF001F24),
                secondaryContainer = Color(0xFF083344),
                onSecondaryContainer = Color(0xFFCFFAFE),
                tertiary = Color(0xFF38BDF8),
                background = Color(0xFF000000),
                onBackground = Color(0xFFFFFFFF),
                surface = Color(0xFF090D16),
                onSurface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFF111827),
                onSurfaceVariant = Color(0xFFCBD5E1),
                outline = Color(0xFF334155),
                error = KamErrorRed,
                errorContainer = KamErrorContainerDark,
                onErrorContainer = KamOnErrorContainer
            ) to elevated
        }

        else -> {
            darkColorScheme() to KamSurfaceElevatedDark
        }
    }
}
