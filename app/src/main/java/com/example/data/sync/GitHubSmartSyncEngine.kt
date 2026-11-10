package com.example.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Represents the parsed `releases/version.json` manifest from the GitHub repository.
 */
data class GitHubVersionManifest(
    val versionCode: Int,
    val versionName: String,
    val minDeltaBaseVersionCode: Int = 1,
    val maxDeltaGap: Int = GitHubSmartSyncEngine.DEFAULT_MAX_DELTA_GAP,
    val requiresFullInstall: Boolean = false,
    val commitSha: String = "",
    val updatedAt: String = "",
    val apkDirectUrl: String = "",
    val backupApkUrl: String = "",
    val deltaSyncUrl: String = "",
    val releaseNotesArabic: String = ""
)

/**
 * Represents the lightweight incremental delta payload (`releases/delta-sync.json`)
 * applied silently in-place without downloading the full APK when the version gap is small.
 */
data class GitHubDeltaPayload(
    val targetVersionCode: Int,
    val targetVersionName: String,
    val minCompatibleBaseVersion: Int = 1,
    val commitSha: String = "",
    val updatedAt: String = "",
    val deltaSummaryArabic: String = "",
    val latestApkDirectUrl: String = "",
    val backupApkUrl: String = "",
    val changedModules: List<String> = emptyList(),
    val announcementBanner: String = ""
)

/**
 * Decision returned by [GitHubSmartSyncEngine.evaluateSyncStrategy]:
 * 1. [UpToDate]: Local app + applied delta are already at the latest GitHub commit/version.
 * 2. [DeltaOnlySync]: Version difference is small (`<= maxDeltaGap`) and compatible -> download
 *    the few-KB `delta-sync.json` AND if `manifest.versionCode > installedBinaryVersionCode`, offer/start
 *    in-app APK update download & installation.
 * 3. [FullPackageUpgradeRequired]: Version difference is large (`> maxDeltaGap` or `installed < minDeltaBaseVersionCode`
 *    or `requiresFullInstall == true`) -> apply safe config delta AND download/install the updated APK in-app.
 */
sealed class GitHubSyncDecision {
    data class UpToDate(
        val activeVersionCode: Int,
        val activeVersionName: String
    ) : GitHubSyncDecision()

    data class DeltaOnlySync(
        val manifest: GitHubVersionManifest,
        val versionGap: Int
    ) : GitHubSyncDecision()

    data class FullPackageUpgradeRequired(
        val manifest: GitHubVersionManifest,
        val versionGap: Int,
        val reasonArabic: String
    ) : GitHubSyncDecision()
}

data class GitHubSyncUiState(
    val repositorySlug: String = GitHubSmartSyncEngine.FIXED_GITHUB_REPO,
    val installedBinaryVersionCode: Int = BuildConfig.VERSION_CODE,
    val installedBinaryVersionName: String = BuildConfig.VERSION_NAME,
    val effectiveSyncedVersionCode: Int = BuildConfig.VERSION_CODE,
    val effectiveSyncedVersionName: String = BuildConfig.VERSION_NAME,
    val lastSyncedCommitSha: String = "",
    val lastSyncSummaryArabic: String = "متزامن تلقائياً مع آخر تحديث على GitHub",
    val latestDirectApkUrl: String = GitHubSmartSyncEngine.defaultDirectApkUrl(),
    val backupApkUrl: String = GitHubSmartSyncEngine.defaultRawApkUrl(),
    val isCheckingInBackground: Boolean = false,
    val isDownloadingApk: Boolean = false,
    val downloadProgressPercent: Int = 0,
    val downloadedApkReady: Boolean = false,
    val downloadErrorArabic: String? = null,
    val silentDeltaBannerMessage: String? = null,
    val majorUpdatePrompt: GitHubVersionManifest? = null,
    val majorUpdateReasonArabic: String? = null
)

object GitHubSmartSyncEngine {

    const val DEFAULT_MAX_DELTA_GAP = 3
    private const val PREFS_NAME = "kam_github_smart_sync_prefs"
    private const val KEY_SYNCED_VERSION_CODE = "synced_delta_version_code"
    private const val KEY_SYNCED_VERSION_NAME = "synced_delta_version_name"
    private const val KEY_SYNCED_COMMIT_SHA = "synced_commit_sha"
    private const val KEY_LAST_SUMMARY = "last_sync_summary_arabic"
    private const val KEY_LATEST_DIRECT_APK_URL = "latest_direct_apk_url"
    private const val KEY_BACKUP_APK_URL = "backup_apk_url"
    private const val KEY_DISMISSED_MAJOR_VERSION = "dismissed_major_version_code"
    private const val APK_UPDATE_DIR = "updates"
    private const val APK_UPDATE_FILENAME = "kAM-Lite-update.apk"

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Fixed GitHub repository slug baked into BuildConfig (automatically updated by GitHub Actions on build).
     */
    val FIXED_GITHUB_REPO: String
        get() = BuildConfig.GITHUB_REPO.trim().ifBlank { "Kett45/Y" }

    fun defaultVersionManifestUrl(repoSlug: String = FIXED_GITHUB_REPO, branch: String = "main"): String =
        "https://raw.githubusercontent.com/$repoSlug/$branch/releases/version.json"

    fun defaultDeltaPayloadUrl(repoSlug: String = FIXED_GITHUB_REPO, branch: String = "main"): String =
        "https://raw.githubusercontent.com/$repoSlug/$branch/releases/delta-sync.json"

    fun defaultDirectApkUrl(repoSlug: String = FIXED_GITHUB_REPO): String =
        "https://github.com/$repoSlug/raw/main/releases/kAM-Lite.apk?v=${BuildConfig.VERSION_NAME}"

    fun defaultRawApkUrl(repoSlug: String = FIXED_GITHUB_REPO, branch: String = "main"): String =
        "https://raw.githubusercontent.com/$repoSlug/$branch/releases/kAM-Lite.apk?v=${BuildConfig.VERSION_NAME}"

    fun defaultReleaseTagApkUrl(repoSlug: String = FIXED_GITHUB_REPO): String =
        "https://github.com/$repoSlug/releases/download/latest/kAM-Lite.apk"

    private fun sanitizeLegacyRepoUrl(url: String?, fallbackUrl: String): String {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isBlank() ||
            trimmed.contains("fkk7777k/kAM-Lite", ignoreCase = true) ||
            trimmed.contains("kappa.lol", ignoreCase = true)
        ) {
            return fallbackUrl
        }
        return trimmed
    }

    private val _syncState = MutableStateFlow(GitHubSyncUiState())
    val syncState: StateFlow<GitHubSyncUiState> = _syncState.asStateFlow()

    /**
     * Pure, deterministic strategy evaluator:
     * - Decides whether to do nothing ([GitHubSyncDecision.UpToDate]),
     * - Apply a lightweight delta-only update ([GitHubSyncDecision.DeltaOnlySync]),
     * - Or trigger the Full APK Upgrade fallback when the version gap between installed and remote is large
     *   ([GitHubSyncDecision.FullPackageUpgradeRequired]).
     */
    fun evaluateSyncStrategy(
        installedBinaryVersionCode: Int,
        lastSyncedDeltaVersionCode: Int,
        lastSyncedCommitSha: String,
        remoteManifest: GitHubVersionManifest
    ): GitHubSyncDecision {
        val effectiveLocalVersion = maxOf(installedBinaryVersionCode, lastSyncedDeltaVersionCode)
        val binaryGap = (remoteManifest.versionCode - installedBinaryVersionCode).coerceAtLeast(0)
        val isSameVersionAndCommit = remoteManifest.versionCode <= effectiveLocalVersion &&
            (remoteManifest.commitSha.isBlank() ||
                remoteManifest.commitSha == "latest" ||
                remoteManifest.commitSha == lastSyncedCommitSha)

        // Check if the installed binary is too far behind (Large Version Gap Scenario)
        val exceedsMaxDeltaGap = binaryGap > remoteManifest.maxDeltaGap.coerceAtLeast(1)
        val belowMinBaseVersion = installedBinaryVersionCode < remoteManifest.minDeltaBaseVersionCode
        val forceFull = remoteManifest.requiresFullInstall && remoteManifest.versionCode > installedBinaryVersionCode

        if (exceedsMaxDeltaGap || belowMinBaseVersion || forceFull) {
            val reason = when {
                forceFull -> "يحتوي الإصدار ${remoteManifest.versionName} على تحديثات جديدة في التطبيق؛ جاري تنزيل وتثبيت التحديث"
                exceedsMaxDeltaGap -> "الفارق بين نسختك الحالية وأحدث إصدار كبير ($binaryGap إصدارات)؛ جاري تنزيل الحزمة المحدّثة"
                else -> "نسختك الحالية أقدم من الحد الأدنى للتحديث الجزئي؛ جاري تنزيل أحدث حزمة كاملة"
            }
            return GitHubSyncDecision.FullPackageUpgradeRequired(
                manifest = remoteManifest,
                versionGap = binaryGap,
                reasonArabic = reason
            )
        }

        if (isSameVersionAndCommit) {
            return GitHubSyncDecision.UpToDate(
                activeVersionCode = effectiveLocalVersion,
                activeVersionName = remoteManifest.versionName
            )
        }

        return GitHubSyncDecision.DeltaOnlySync(
            manifest = remoteManifest,
            versionGap = binaryGap
        )
    }

    fun parseVersionManifestJson(jsonString: String): GitHubVersionManifest? {
        return runCatching {
            val obj = JSONObject(jsonString)
            val versionCode = obj.optInt("versionCode", 0)
            if (versionCode <= 0) return null
            val versionName = obj.optString("versionName", "1.0.$versionCode")
            GitHubVersionManifest(
                versionCode = versionCode,
                versionName = versionName,
                minDeltaBaseVersionCode = obj.optInt("minDeltaBaseVersionCode", 1),
                maxDeltaGap = obj.optInt("maxDeltaGap", DEFAULT_MAX_DELTA_GAP).coerceAtLeast(1),
                requiresFullInstall = obj.optBoolean("requiresFullInstall", false),
                commitSha = obj.optString("commitSha", ""),
                updatedAt = obj.optString("updatedAt", ""),
                apkDirectUrl = sanitizeLegacyRepoUrl(obj.optString("apkDirectUrl", ""), defaultDirectApkUrl()),
                backupApkUrl = sanitizeLegacyRepoUrl(obj.optString("backupApkUrl", ""), defaultRawApkUrl()),
                deltaSyncUrl = sanitizeLegacyRepoUrl(obj.optString("deltaSyncUrl", ""), defaultDeltaPayloadUrl()),
                releaseNotesArabic = obj.optString("releaseNotesArabic", "")
            )
        }.getOrNull()
    }

    fun parseDeltaPayloadJson(jsonString: String): GitHubDeltaPayload? {
        return runCatching {
            val obj = JSONObject(jsonString)
            val targetCode = obj.optInt("targetVersionCode", 0)
            if (targetCode <= 0) return null
            val modulesArr = obj.optJSONArray("changedModules")
            val modules = buildList {
                if (modulesArr != null) {
                    for (i in 0 until modulesArr.length()) {
                        val item = modulesArr.optString(i).trim()
                        if (item.isNotBlank()) add(item)
                    }
                }
            }
            val dynConfig = obj.optJSONObject("dynamicConfig")
            GitHubDeltaPayload(
                targetVersionCode = targetCode,
                targetVersionName = obj.optString("targetVersionName", "1.0.$targetCode"),
                minCompatibleBaseVersion = obj.optInt("minCompatibleBaseVersion", 1),
                commitSha = obj.optString("commitSha", ""),
                updatedAt = obj.optString("updatedAt", ""),
                deltaSummaryArabic = obj.optString("deltaSummaryArabic", "تم تحديث آخر التطورات تلقائياً بالخلفية"),
                latestApkDirectUrl = sanitizeLegacyRepoUrl(obj.optString("latestApkDirectUrl", ""), defaultDirectApkUrl()),
                backupApkUrl = sanitizeLegacyRepoUrl(obj.optString("backupApkUrl", ""), defaultRawApkUrl()),
                changedModules = modules,
                announcementBanner = dynConfig?.optString("announcementBanner", "").orEmpty()
            )
        }.getOrNull()
    }

    /**
     * Loads cached delta state from SharedPreferences immediately so startup is 0ms,
     * and automatically purges any legacy repository URLs.
     */
    fun initializeFromLocalCache(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedCode = prefs.getInt(KEY_SYNCED_VERSION_CODE, BuildConfig.VERSION_CODE)
            .coerceAtLeast(BuildConfig.VERSION_CODE)
        val cachedName = prefs.getString(KEY_SYNCED_VERSION_NAME, null)
            ?.takeIf { it.isNotBlank() && cachedCode > BuildConfig.VERSION_CODE }
            ?: BuildConfig.VERSION_NAME
        val cachedSha = prefs.getString(KEY_SYNCED_COMMIT_SHA, "").orEmpty()
        val cachedSummary = prefs.getString(KEY_LAST_SUMMARY, null)
            ?.takeIf { it.isNotBlank() }
            ?: "متزامن تلقائياً مع مستودع GitHub ($FIXED_GITHUB_REPO)"
        val cachedDirectUrl = sanitizeLegacyRepoUrl(
            prefs.getString(KEY_LATEST_DIRECT_APK_URL, null),
            defaultDirectApkUrl()
        )
        val cachedBackupUrl = sanitizeLegacyRepoUrl(
            prefs.getString(KEY_BACKUP_APK_URL, null),
            defaultRawApkUrl()
        )

        prefs.edit()
            .putString(KEY_LATEST_DIRECT_APK_URL, cachedDirectUrl)
            .putString(KEY_BACKUP_APK_URL, cachedBackupUrl)
            .apply()

        val apkFile = getLocalApkUpdateFile(context.applicationContext)
        val isReady = apkFile.exists() && apkFile.length() > 100_000L

        _syncState.value = _syncState.value.copy(
            repositorySlug = FIXED_GITHUB_REPO,
            installedBinaryVersionCode = BuildConfig.VERSION_CODE,
            installedBinaryVersionName = BuildConfig.VERSION_NAME,
            effectiveSyncedVersionCode = cachedCode,
            effectiveSyncedVersionName = cachedName,
            lastSyncedCommitSha = cachedSha,
            lastSyncSummaryArabic = cachedSummary,
            latestDirectApkUrl = cachedDirectUrl,
            backupApkUrl = cachedBackupUrl,
            downloadedApkReady = isReady
        )
    }

    private fun getLocalApkUpdateFile(context: Context): File {
        val updateDir = File(context.cacheDir, APK_UPDATE_DIR).apply { mkdirs() }
        return File(updateDir, APK_UPDATE_FILENAME)
    }

    /**
     * Performs a background check against the GitHub repository:
     * - Whenever `manifest.versionCode > BuildConfig.VERSION_CODE` (a newer APK binary exists on GitHub),
     *   automatically downloads the updated APK inside the app and triggers the Android Package Installer!
     */
    suspend fun checkAndSyncSilently(
        context: Context,
        forceManualCheck: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        initializeFromLocalCache(appContext)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        _syncState.value = _syncState.value.copy(
            isCheckingInBackground = true,
            downloadErrorArabic = null
        )

        try {
            val manifestJson = fetchTextFromUrl(defaultVersionManifestUrl(FIXED_GITHUB_REPO, "main"))
                ?: fetchTextFromUrl(defaultVersionManifestUrl(FIXED_GITHUB_REPO, "master"))

            if (manifestJson.isNullOrBlank()) {
                _syncState.value = _syncState.value.copy(
                    isCheckingInBackground = false,
                    silentDeltaBannerMessage = if (forceManualCheck) {
                        "التطبيق يعمل على أحدث نسخة متاحة (${_syncState.value.effectiveSyncedVersionName})"
                    } else {
                        null
                    }
                )
                return@withContext
            }

            val manifest = parseVersionManifestJson(manifestJson) ?: run {
                _syncState.value = _syncState.value.copy(isCheckingInBackground = false)
                return@withContext
            }

            val currentSyncedCode = prefs.getInt(KEY_SYNCED_VERSION_CODE, BuildConfig.VERSION_CODE)
                .coerceAtLeast(BuildConfig.VERSION_CODE)
            val currentSyncedSha = prefs.getString(KEY_SYNCED_COMMIT_SHA, "").orEmpty()
            val dismissedMajorVersion = prefs.getInt(KEY_DISMISSED_MAJOR_VERSION, -1)

            val decision = evaluateSyncStrategy(
                installedBinaryVersionCode = BuildConfig.VERSION_CODE,
                lastSyncedDeltaVersionCode = currentSyncedCode,
                lastSyncedCommitSha = currentSyncedSha,
                remoteManifest = manifest
            )

            val hasNewerApkBinary = manifest.versionCode > BuildConfig.VERSION_CODE
            val shouldPromptBinaryUpgrade = hasNewerApkBinary &&
                (forceManualCheck || manifest.versionCode != dismissedMajorVersion)

            when (decision) {
                is GitHubSyncDecision.UpToDate -> {
                    val directUrl = manifest.apkDirectUrl.ifBlank { defaultDirectApkUrl() }
                    val backupUrl = manifest.backupApkUrl.ifBlank { defaultRawApkUrl() }
                    prefs.edit()
                        .putString(KEY_LATEST_DIRECT_APK_URL, directUrl)
                        .putString(KEY_BACKUP_APK_URL, backupUrl)
                        .apply()

                    _syncState.value = _syncState.value.copy(
                        isCheckingInBackground = false,
                        latestDirectApkUrl = directUrl,
                        backupApkUrl = backupUrl,
                        majorUpdatePrompt = if (shouldPromptBinaryUpgrade) manifest else null,
                        majorUpdateReasonArabic = if (shouldPromptBinaryUpgrade) {
                            manifest.releaseNotesArabic.ifBlank { "يتوفر إصدار جديد (v${manifest.versionName}) جاهز للتثبيت المباشر داخل التطبيق" }
                        } else {
                            null
                        },
                        silentDeltaBannerMessage = if (forceManualCheck && !hasNewerApkBinary) {
                            "التطبيق محدّث بالفعل لأعلى إصدار (${_syncState.value.effectiveSyncedVersionName})"
                        } else {
                            null
                        }
                    )

                    if (shouldPromptBinaryUpgrade) {
                        startInAppApkDownloadAndInstall(context, autoLaunchInstaller = true)
                    }
                }

                is GitHubSyncDecision.DeltaOnlySync -> {
                    val deltaUrl = manifest.deltaSyncUrl.ifBlank { defaultDeltaPayloadUrl(FIXED_GITHUB_REPO, "main") }
                    val deltaJson = fetchTextFromUrl(deltaUrl)
                        ?: fetchTextFromUrl(defaultDeltaPayloadUrl(FIXED_GITHUB_REPO, "master"))
                    val deltaPayload = deltaJson?.let { parseDeltaPayloadJson(it) }

                    val newVersionCode = deltaPayload?.targetVersionCode ?: manifest.versionCode
                    val newVersionName = deltaPayload?.targetVersionName?.ifBlank { manifest.versionName }
                        ?: manifest.versionName
                    val newSha = deltaPayload?.commitSha?.ifBlank { manifest.commitSha } ?: manifest.commitSha
                    val summary = deltaPayload?.deltaSummaryArabic?.ifBlank { manifest.releaseNotesArabic }
                        ?: manifest.releaseNotesArabic.ifBlank { "تمت مزامنة التحديثات تلقائياً" }
                    val directUrl = deltaPayload?.latestApkDirectUrl?.ifBlank { manifest.apkDirectUrl }
                        ?: manifest.apkDirectUrl.ifBlank { defaultDirectApkUrl() }
                    val backupUrl = deltaPayload?.backupApkUrl?.ifBlank { manifest.backupApkUrl }
                        ?: manifest.backupApkUrl.ifBlank { defaultRawApkUrl() }

                    prefs.edit()
                        .putInt(KEY_SYNCED_VERSION_CODE, newVersionCode)
                        .putString(KEY_SYNCED_VERSION_NAME, newVersionName)
                        .putString(KEY_SYNCED_COMMIT_SHA, newSha)
                        .putString(KEY_LAST_SUMMARY, summary)
                        .putString(KEY_LATEST_DIRECT_APK_URL, directUrl)
                        .putString(KEY_BACKUP_APK_URL, backupUrl)
                        .apply()

                    _syncState.value = _syncState.value.copy(
                        isCheckingInBackground = false,
                        effectiveSyncedVersionCode = newVersionCode,
                        effectiveSyncedVersionName = newVersionName,
                        lastSyncedCommitSha = newSha,
                        lastSyncSummaryArabic = summary,
                        latestDirectApkUrl = directUrl,
                        backupApkUrl = backupUrl,
                        majorUpdatePrompt = if (shouldPromptBinaryUpgrade) manifest else null,
                        majorUpdateReasonArabic = if (shouldPromptBinaryUpgrade) {
                            summary.ifBlank { "يتوفر إصدار جديد (v$newVersionName) جاهز للتثبيت المباشر" }
                        } else {
                            null
                        },
                        silentDeltaBannerMessage = if (shouldPromptBinaryUpgrade) {
                            "تم اكتشاف إصدار جديد ($newVersionName)؛ جاري تنزيل التحديث داخل التطبيق..."
                        } else {
                            "تم تحديث آخر التطورات ($newVersionName) تلقائياً بالخلفية"
                        }
                    )

                    if (shouldPromptBinaryUpgrade) {
                        startInAppApkDownloadAndInstall(context, autoLaunchInstaller = true)
                    }
                }

                is GitHubSyncDecision.FullPackageUpgradeRequired -> {
                    val directUrl = manifest.apkDirectUrl.ifBlank { defaultDirectApkUrl() }
                    val backupUrl = manifest.backupApkUrl.ifBlank { defaultRawApkUrl() }

                    prefs.edit()
                        .putString(KEY_LATEST_DIRECT_APK_URL, directUrl)
                        .putString(KEY_BACKUP_APK_URL, backupUrl)
                        .apply()

                    val shouldShowBar = forceManualCheck || manifest.versionCode != dismissedMajorVersion
                    _syncState.value = _syncState.value.copy(
                        isCheckingInBackground = false,
                        latestDirectApkUrl = directUrl,
                        backupApkUrl = backupUrl,
                        lastSyncSummaryArabic = decision.reasonArabic,
                        majorUpdatePrompt = if (shouldShowBar) manifest else null,
                        majorUpdateReasonArabic = if (shouldShowBar) decision.reasonArabic else null
                    )

                    if (shouldShowBar && manifest.versionCode > BuildConfig.VERSION_CODE) {
                        startInAppApkDownloadAndInstall(context, autoLaunchInstaller = true)
                    }
                }
            }
        } catch (_: Exception) {
            _syncState.value = _syncState.value.copy(isCheckingInBackground = false)
        }
    }

    fun clearSilentDeltaBanner() {
        _syncState.value = _syncState.value.copy(silentDeltaBannerMessage = null)
    }

    fun dismissMajorUpdatePrompt(context: Context) {
        val promptVersion = _syncState.value.majorUpdatePrompt?.versionCode ?: return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DISMISSED_MAJOR_VERSION, promptVersion)
            .apply()
        _syncState.value = _syncState.value.copy(
            majorUpdatePrompt = null,
            majorUpdateReasonArabic = null
        )
    }

    /**
     * Starts downloading the latest APK inside the application and automatically opens
     * the Android Package Installer screen once the download completes.
     */
    fun startInAppApkDownloadAndInstall(
        context: Context,
        autoLaunchInstaller: Boolean = true
    ) {
        if (_syncState.value.isDownloadingApk) return
        val appContext = context.applicationContext
        downloadScope.launch {
            downloadAndInstallLatestApk(appContext, autoLaunchInstaller = autoLaunchInstaller)
        }
    }

    /**
     * Downloads the APK binary directly inside the app cache directory with live progress updates (0%..100%)
     * and launches the native Android package installer via [FileProvider].
     */
    suspend fun downloadAndInstallLatestApk(
        context: Context,
        autoLaunchInstaller: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        if (_syncState.value.isDownloadingApk) return@withContext false
        val appContext = context.applicationContext
        val state = _syncState.value

        val candidateUrls = listOf(
            state.latestDirectApkUrl.ifBlank { defaultDirectApkUrl() },
            state.backupApkUrl.ifBlank { defaultRawApkUrl() },
            defaultReleaseTagApkUrl(),
            defaultRawApkUrl(FIXED_GITHUB_REPO, "main")
        ).distinct()

        _syncState.value = _syncState.value.copy(
            isDownloadingApk = true,
            downloadProgressPercent = 2,
            downloadErrorArabic = null
        )

        val targetFile = getLocalApkUpdateFile(appContext)
        val tempFile = File(targetFile.parentFile, "$APK_UPDATE_FILENAME.tmp")

        var downloadedSuccess = false
        for (urlStr in candidateUrls) {
            if (downloadApkFromCandidateUrl(urlStr, tempFile)) {
                if (tempFile.exists() && tempFile.length() > 100_000L) {
                    if (targetFile.exists()) targetFile.delete()
                    val renamed = tempFile.renameTo(targetFile)
                    if (!renamed) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                    downloadedSuccess = true
                    break
                }
            }
        }

        if (downloadedSuccess && targetFile.exists() && targetFile.length() > 100_000L) {
            _syncState.value = _syncState.value.copy(
                isDownloadingApk = false,
                downloadProgressPercent = 100,
                downloadedApkReady = true,
                downloadErrorArabic = null,
                lastSyncSummaryArabic = "اكتمل تنزيل التحديث داخل التطبيق؛ جاري فتح شاشة التثبيت..."
            )
            if (autoLaunchInstaller) {
                withContext(Dispatchers.Main) {
                    installDownloadedApk(appContext)
                }
            }
            true
        } else {
            if (tempFile.exists()) tempFile.delete()
            _syncState.value = _syncState.value.copy(
                isDownloadingApk = false,
                downloadProgressPercent = 0,
                downloadErrorArabic = "تعذر تنزيل ملف التحديث تلقائياً؛ يمكنك المحاولة مرة أخرى"
            )
            false
        }
    }

    private fun downloadApkFromCandidateUrl(urlString: String, destinationFile: File): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            var activeConn: HttpURLConnection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 20_000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
            }
            connection = activeConn

            var code = activeConn.responseCode
            var redirectCount = 0
            while (code in listOf(
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307,
                    308
                ) && redirectCount < 5
            ) {
                val newLocation = activeConn.getHeaderField("Location") ?: break
                activeConn.disconnect()
                activeConn = (URL(newLocation).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    connectTimeout = 12_000
                    readTimeout = 20_000
                    useCaches = false
                }
                connection = activeConn
                code = activeConn.responseCode
                redirectCount++
            }

            if (code !in 200..299) return false

            val totalBytes = activeConn.contentLengthLong
            var downloadedBytes = 0L
            var lastProgressEmitMillis = 0L

            BufferedInputStream(activeConn.inputStream).use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val now = System.currentTimeMillis()
                        if (now - lastProgressEmitMillis >= 120L) {
                            lastProgressEmitMillis = now
                            val percent = if (totalBytes > 0L) {
                                ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(5, 99)
                            } else {
                                // Estimate against ~12 MB APK when Content-Length is omitted
                                ((downloadedBytes * 100L) / (12L * 1024L * 1024L)).toInt().coerceIn(5, 95)
                            }
                            _syncState.value = _syncState.value.copy(
                                isDownloadingApk = true,
                                downloadProgressPercent = percent
                            )
                        }
                    }
                    output.flush()
                }
            }
            destinationFile.exists() && destinationFile.length() > 100_000L
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Launches the Android system Package Installer for the downloaded APK using [FileProvider].
     * If the user hasn't granted "Install unknown apps" permission on Android 8.0+, opens the
     * system permission screen for this package so they can allow it and tap install immediately.
     */
    fun installDownloadedApk(context: Context): Boolean {
        val appContext = context.applicationContext
        val apkFile = getLocalApkUpdateFile(appContext)
        if (!apkFile.exists() || apkFile.length() <= 100_000L) {
            startInAppApkDownloadAndInstall(appContext, autoLaunchInstaller = true)
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = runCatching { appContext.packageManager.canRequestPackageInstalls() }.getOrDefault(true)
            if (!canInstall) {
                runCatching {
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${appContext.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    _syncState.value = _syncState.value.copy(
                        silentDeltaBannerMessage = "يرجى السماح بتثبيت التحديث من هذا المصدر ثم الضغط على 'تثبيت التحديث الآن'"
                    )
                }
                return false
            }
        }

        return runCatching {
            val apkUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        }.getOrElse {
            false
        }
    }

    /**
     * Triggers the in-app APK download & installer directly (and falls back to browser only if requested).
     */
    fun openLatestFullApkDownload(context: Context, useBackupLink: Boolean = false) {
        val appContext = context.applicationContext
        val apkFile = getLocalApkUpdateFile(appContext)
        if (_syncState.value.downloadedApkReady && apkFile.exists() && apkFile.length() > 100_000L && !useBackupLink) {
            if (installDownloadedApk(context)) return
        }
        if (!useBackupLink) {
            startInAppApkDownloadAndInstall(context, autoLaunchInstaller = true)
            return
        }
        val state = _syncState.value
        val targetUrl = state.backupApkUrl.ifBlank { defaultRawApkUrl() }
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun fetchTextFromUrl(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Accept", "application/json, text/plain, */*")
            }
            if (connection.responseCode in 200..299) {
                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
