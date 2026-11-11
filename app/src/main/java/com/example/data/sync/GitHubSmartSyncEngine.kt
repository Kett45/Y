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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

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
    val apkSha256: String = "",
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
    val apkSha256: String = "",
    val updatedAt: String = "",
    val deltaSummaryArabic: String = "",
    val latestApkDirectUrl: String = "",
    val backupApkUrl: String = "",
    val changedModules: List<String> = emptyList(),
    val announcementBanner: String = ""
)

/**
 * Decision returned by [GitHubSmartSyncEngine.evaluateSyncStrategy]:
 * 1. [UpToDate]: Local app + applied delta + APK SHA-256 are already at the latest GitHub commit/version.
 * 2. [DeltaOnlySync]: Version difference is small (`<= maxDeltaGap`) and compatible -> download
 *    the few-KB `delta-sync.json` AND if a newer binary (`versionCode` or `apkSha256`) exists,
 *    start in-app APK update download & installation.
 * 3. [FullPackageUpgradeRequired]: Version difference is large (`> maxDeltaGap` or `installed < minDeltaBaseVersionCode`
 *    or `requiresFullInstall == true` with a newer binary) -> download/install the updated APK in-app.
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
    val lastSyncedApkSha256: String = "",
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
    private const val KEY_SYNCED_APK_SHA256 = "synced_apk_sha256"
    private const val KEY_INSTALLED_APK_SHA256 = "installed_apk_sha256"
    private const val KEY_INSTALLED_APK_MTIME = "installed_apk_mtime"
    private const val KEY_LAST_SUMMARY = "last_sync_summary_arabic"
    private const val KEY_LATEST_DIRECT_APK_URL = "latest_direct_apk_url"
    private const val KEY_BACKUP_APK_URL = "backup_apk_url"
    private const val KEY_DISMISSED_MAJOR_VERSION = "dismissed_major_version_code"
    private const val KEY_DISMISSED_APK_SHA256 = "dismissed_apk_sha256"
    private const val APK_UPDATE_DIR = "updates"
    private const val APK_UPDATE_FILENAME = "kAM-Lite-update.apk"

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Fixed GitHub repository slug baked into BuildConfig.
     */
    val FIXED_GITHUB_REPO: String
        get() = BuildConfig.GITHUB_REPO.trim().ifBlank { "Kett45/Y" }

    fun defaultVersionManifestUrl(repoSlug: String = FIXED_GITHUB_REPO, branch: String = "main"): String =
        "https://raw.githubusercontent.com/$repoSlug/$branch/releases/version.json"

    fun defaultDeltaPayloadUrl(repoSlug: String = FIXED_GITHUB_REPO, branch: String = "main"): String =
        "https://raw.githubusercontent.com/$repoSlug/$branch/releases/delta-sync.json"

    fun defaultDirectApkUrl(
        repoSlug: String = FIXED_GITHUB_REPO,
        @Suppress("UNUSED_PARAMETER") versionName: String = BuildConfig.VERSION_NAME
    ): String =
        "https://github.com/$repoSlug/raw/main/releases/kAM-Lite.apk"

    fun defaultRawApkUrl(
        repoSlug: String = FIXED_GITHUB_REPO,
        branch: String = "main",
        @Suppress("UNUSED_PARAMETER") versionName: String = BuildConfig.VERSION_NAME
    ): String =
        "https://github.com/$repoSlug/raw/$branch/releases/kAM-Lite.apk"

    fun defaultReleaseTagApkUrl(repoSlug: String = FIXED_GITHUB_REPO): String =
        "https://github.com/$repoSlug/raw/main/releases/kAM-Lite.apk"

    /**
     * Normalizes any APK link to the single permanent auto-updating GitHub URL
     * (`https://github.com/Kett45/Y/raw/main/releases/kAM-Lite.apk`) without extra query suffixes.
     */
    internal fun sanitizeAndRefreshApkUrl(
        url: String?,
        fallbackUrl: String,
        @Suppress("UNUSED_PARAMETER") activeVersionName: String = BuildConfig.VERSION_NAME
    ): String {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isBlank() ||
            trimmed.contains("fkk7777k/kAM-Lite", ignoreCase = true) ||
            trimmed.contains("kappa.lol", ignoreCase = true)
        ) {
            return fallbackUrl.substringBefore("?")
        }
        val baseWithoutQuery = trimmed.substringBefore("?")
        return if (baseWithoutQuery.endsWith(".apk", ignoreCase = true)) {
            defaultDirectApkUrl(FIXED_GITHUB_REPO)
        } else {
            trimmed
        }
    }

    /**
     * Appends a hidden cache-buster parameter (`?cb=<timestamp>`) for network requests
     * so GitHub CDN and HTTP caches never serve a stale manifest or stale APK binary,
     * while keeping the user-visible link clean.
     */
    internal fun appendHiddenCacheBuster(
        urlString: String,
        timestampMillis: Long = System.currentTimeMillis()
    ): String {
        val clean = urlString.trim()
        if (clean.isBlank()) return clean
        val separator = if (clean.contains("?")) "&" else "?"
        return "${clean}${separator}cb=$timestampMillis"
    }

    /**
     * Determines whether the remote manifest represents a newer or modified APK binary compared to
     * what is currently installed on the device (checks both `versionCode` AND `apkSha256`).
     */
    internal fun hasNewerOrModifiedApkBinary(
        installedVersionCode: Int,
        installedApkSha256: String,
        lastSyncedApkSha256: String,
        remoteManifest: GitHubVersionManifest
    ): Boolean {
        if (remoteManifest.versionCode > installedVersionCode) {
            return true
        }
        if (remoteManifest.versionCode == installedVersionCode && remoteManifest.apkSha256.length >= 16) {
            val remoteHash = remoteManifest.apkSha256.trim()
            val matchesInstalled = installedApkSha256.isNotBlank() &&
                installedApkSha256.equals(remoteHash, ignoreCase = true)
            val matchesLastSynced = lastSyncedApkSha256.isNotBlank() &&
                lastSyncedApkSha256.equals(remoteHash, ignoreCase = true)
            if (!matchesInstalled && !matchesLastSynced &&
                (installedApkSha256.isNotBlank() || lastSyncedApkSha256.isNotBlank())
            ) {
                return true
            }
        }
        return false
    }

    private val _syncState = MutableStateFlow(GitHubSyncUiState())
    val syncState: StateFlow<GitHubSyncUiState> = _syncState.asStateFlow()

    /**
     * Pure, deterministic strategy evaluator:
     * - Decides whether to do nothing ([GitHubSyncDecision.UpToDate]),
     * - Apply a lightweight delta-only update ([GitHubSyncDecision.DeltaOnlySync]),
     * - Or trigger the Full APK Upgrade fallback ([GitHubSyncDecision.FullPackageUpgradeRequired]).
     */
    fun evaluateSyncStrategy(
        installedBinaryVersionCode: Int,
        lastSyncedDeltaVersionCode: Int,
        lastSyncedCommitSha: String,
        remoteManifest: GitHubVersionManifest,
        installedApkSha256: String = "",
        lastSyncedApkSha256: String = ""
    ): GitHubSyncDecision {
        val effectiveLocalVersion = maxOf(installedBinaryVersionCode, lastSyncedDeltaVersionCode)
        val binaryGap = (remoteManifest.versionCode - installedBinaryVersionCode).coerceAtLeast(0)

        val apkSha256Mismatch = remoteManifest.versionCode >= installedBinaryVersionCode &&
            remoteManifest.apkSha256.length >= 16 &&
            (installedApkSha256.isNotBlank() || lastSyncedApkSha256.isNotBlank()) &&
            !(installedApkSha256.isNotBlank() && installedApkSha256.equals(remoteManifest.apkSha256.trim(), ignoreCase = true)) &&
            !(lastSyncedApkSha256.isNotBlank() && lastSyncedApkSha256.equals(remoteManifest.apkSha256.trim(), ignoreCase = true))

        val isSameVersionAndCommit = !apkSha256Mismatch &&
            remoteManifest.versionCode <= effectiveLocalVersion &&
            (remoteManifest.commitSha.isBlank() ||
                remoteManifest.commitSha == "latest" ||
                remoteManifest.commitSha == lastSyncedCommitSha)

        val exceedsMaxDeltaGap = binaryGap > remoteManifest.maxDeltaGap.coerceAtLeast(1)
        val belowMinBaseVersion = installedBinaryVersionCode < remoteManifest.minDeltaBaseVersionCode
        val forceFull = (remoteManifest.requiresFullInstall && remoteManifest.versionCode > installedBinaryVersionCode) ||
            apkSha256Mismatch

        if (exceedsMaxDeltaGap || belowMinBaseVersion || forceFull) {
            val reason = when {
                forceFull -> "يتوفر تحديث جديد للتطبيق (${remoteManifest.versionName}) على GitHub؛ جاري التنزيل والتثبيت المباشر"
                exceedsMaxDeltaGap -> "الفارق بين نسختك الحالية وأحدث إصدار كبير ($binaryGap إصدارات)؛ جاري تنزيل الحزمة المحدّثة"
                else -> "نسختك الحالية أقدم من الحد الأدنى للتحديث الجزئي؛ جاري تنزيل أحدث حزمة كاملة"
            }
            return GitHubSyncDecision.FullPackageUpgradeRequired(
                manifest = remoteManifest,
                versionGap = binaryGap.coerceAtLeast(if (apkSha256Mismatch) 1 else 0),
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
            val apkSha256 = obj.optString("apkSha256", "").trim()
            val rawCommitSha = obj.optString("commitSha", "").trim()
            val effectiveCommitSha = if (rawCommitSha.isBlank() || rawCommitSha == "latest") {
                if (apkSha256.length >= 12) apkSha256.take(12) else rawCommitSha
            } else {
                rawCommitSha
            }
            GitHubVersionManifest(
                versionCode = versionCode,
                versionName = versionName,
                minDeltaBaseVersionCode = obj.optInt("minDeltaBaseVersionCode", 1),
                maxDeltaGap = obj.optInt("maxDeltaGap", DEFAULT_MAX_DELTA_GAP).coerceAtLeast(1),
                requiresFullInstall = obj.optBoolean("requiresFullInstall", false),
                commitSha = effectiveCommitSha,
                apkSha256 = apkSha256,
                updatedAt = obj.optString("updatedAt", ""),
                apkDirectUrl = sanitizeAndRefreshApkUrl(
                    obj.optString("apkDirectUrl", ""),
                    defaultDirectApkUrl(FIXED_GITHUB_REPO, versionName),
                    versionName
                ),
                backupApkUrl = sanitizeAndRefreshApkUrl(
                    obj.optString("backupApkUrl", ""),
                    defaultRawApkUrl(FIXED_GITHUB_REPO, "main", versionName),
                    versionName
                ),
                deltaSyncUrl = sanitizeAndRefreshApkUrl(
                    obj.optString("deltaSyncUrl", ""),
                    defaultDeltaPayloadUrl(),
                    versionName
                ),
                releaseNotesArabic = obj.optString("releaseNotesArabic", "")
            )
        }.getOrNull()
    }

    fun parseDeltaPayloadJson(jsonString: String): GitHubDeltaPayload? {
        return runCatching {
            val obj = JSONObject(jsonString)
            val targetCode = obj.optInt("targetVersionCode", 0)
            if (targetCode <= 0) return null
            val targetName = obj.optString("targetVersionName", "1.0.$targetCode")
            val apkSha256 = obj.optString("apkSha256", "").trim()
            val rawCommitSha = obj.optString("commitSha", "").trim()
            val effectiveCommitSha = if (rawCommitSha.isBlank() || rawCommitSha == "latest") {
                if (apkSha256.length >= 12) apkSha256.take(12) else rawCommitSha
            } else {
                rawCommitSha
            }
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
                targetVersionName = targetName,
                minCompatibleBaseVersion = obj.optInt("minCompatibleBaseVersion", 1),
                commitSha = effectiveCommitSha,
                apkSha256 = apkSha256,
                updatedAt = obj.optString("updatedAt", ""),
                deltaSummaryArabic = obj.optString("deltaSummaryArabic", "تم تحديث آخر التطورات تلقائياً بالخلفية"),
                latestApkDirectUrl = sanitizeAndRefreshApkUrl(
                    obj.optString("latestApkDirectUrl", ""),
                    defaultDirectApkUrl(FIXED_GITHUB_REPO, targetName),
                    targetName
                ),
                backupApkUrl = sanitizeAndRefreshApkUrl(
                    obj.optString("backupApkUrl", ""),
                    defaultRawApkUrl(FIXED_GITHUB_REPO, "main", targetName),
                    targetName
                ),
                changedModules = modules,
                announcementBanner = dynConfig?.optString("announcementBanner", "").orEmpty()
            )
        }.getOrNull()
    }

    internal fun computeFileSha256(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(32 * 1024)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }

    private fun getOrComputeInstalledApkSha256(context: Context): String {
        return runCatching {
            val appContext = context.applicationContext
            val sourceApk = File(appContext.applicationInfo.sourceDir ?: return "")
            if (!sourceApk.exists()) return ""
            val mtime = sourceApk.lastModified()
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cachedMtime = prefs.getLong(KEY_INSTALLED_APK_MTIME, -1L)
            val cachedHash = prefs.getString(KEY_INSTALLED_APK_SHA256, "").orEmpty()
            if (cachedMtime == mtime && cachedHash.length == 64) {
                return cachedHash
            }
            val computed = computeFileSha256(sourceApk)
            if (computed.length == 64) {
                prefs.edit()
                    .putLong(KEY_INSTALLED_APK_MTIME, mtime)
                    .putString(KEY_INSTALLED_APK_SHA256, computed)
                    .apply()
            }
            computed
        }.getOrDefault("")
    }

    /**
     * Loads cached delta state from SharedPreferences immediately so startup is 0ms,
     * and normalizes links to the single permanent auto-updating GitHub URL.
     */
    fun initializeFromLocalCache(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawCachedCode = prefs.getInt(KEY_SYNCED_VERSION_CODE, BuildConfig.VERSION_CODE)
        val isLocalBinaryNewerOrEqual = BuildConfig.VERSION_CODE >= rawCachedCode
        val effectiveCode = maxOf(BuildConfig.VERSION_CODE, rawCachedCode)
        val effectiveName = if (isLocalBinaryNewerOrEqual) {
            BuildConfig.VERSION_NAME
        } else {
            prefs.getString(KEY_SYNCED_VERSION_NAME, null)
                ?.takeIf { it.isNotBlank() }
                ?: BuildConfig.VERSION_NAME
        }
        val cachedSha = prefs.getString(KEY_SYNCED_COMMIT_SHA, "").orEmpty()
        val cachedApkSha256 = prefs.getString(KEY_SYNCED_APK_SHA256, "").orEmpty()
        val cachedSummary = if (isLocalBinaryNewerOrEqual) {
            "متزامن تلقائياً مع مستودع GitHub ($FIXED_GITHUB_REPO • v$effectiveName)"
        } else {
            prefs.getString(KEY_LAST_SUMMARY, null)
                ?.takeIf { it.isNotBlank() }
                ?: "متزامن تلقائياً مع مستودع GitHub ($FIXED_GITHUB_REPO • v$effectiveName)"
        }

        val singleCleanUrl = defaultDirectApkUrl(FIXED_GITHUB_REPO, effectiveName)

        prefs.edit()
            .putInt(KEY_SYNCED_VERSION_CODE, effectiveCode)
            .putString(KEY_SYNCED_VERSION_NAME, effectiveName)
            .putString(KEY_LATEST_DIRECT_APK_URL, singleCleanUrl)
            .putString(KEY_BACKUP_APK_URL, singleCleanUrl)
            .apply()

        val apkFile = getLocalApkUpdateFile(context.applicationContext)
        val isReady = apkFile.exists() && apkFile.length() > 100_000L

        _syncState.value = _syncState.value.copy(
            repositorySlug = FIXED_GITHUB_REPO,
            installedBinaryVersionCode = BuildConfig.VERSION_CODE,
            installedBinaryVersionName = BuildConfig.VERSION_NAME,
            effectiveSyncedVersionCode = effectiveCode,
            effectiveSyncedVersionName = effectiveName,
            lastSyncedCommitSha = cachedSha,
            lastSyncedApkSha256 = cachedApkSha256,
            lastSyncSummaryArabic = cachedSummary,
            latestDirectApkUrl = singleCleanUrl,
            backupApkUrl = singleCleanUrl,
            downloadedApkReady = isReady
        )
    }

    private fun getLocalApkUpdateFile(context: Context): File {
        val updateDir = File(context.cacheDir, APK_UPDATE_DIR).apply { mkdirs() }
        return File(updateDir, APK_UPDATE_FILENAME)
    }

    /**
     * Performs a background check against the GitHub repository:
     * - Checks BOTH `manifest.versionCode` AND `manifest.apkSha256` against the installed binary.
     * - Whenever a newer version or modified APK fingerprint exists on GitHub,
     *   automatically downloads the updated APK (bypassing cache) and opens the Android Package Installer!
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
            val installedApkSha256 = getOrComputeInstalledApkSha256(appContext)
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
            var currentSyncedApkSha256 = prefs.getString(KEY_SYNCED_APK_SHA256, "").orEmpty()

            // Seed baseline SHA-256 on first run if installed binary matches the remote versionCode
            if (currentSyncedApkSha256.isBlank() && installedApkSha256.isNotBlank()) {
                currentSyncedApkSha256 = installedApkSha256
                prefs.edit().putString(KEY_SYNCED_APK_SHA256, installedApkSha256).apply()
            }

            val dismissedMajorVersion = prefs.getInt(KEY_DISMISSED_MAJOR_VERSION, -1)
            val dismissedApkSha256 = prefs.getString(KEY_DISMISSED_APK_SHA256, "").orEmpty()

            val decision = evaluateSyncStrategy(
                installedBinaryVersionCode = BuildConfig.VERSION_CODE,
                lastSyncedDeltaVersionCode = currentSyncedCode,
                lastSyncedCommitSha = currentSyncedSha,
                remoteManifest = manifest,
                installedApkSha256 = installedApkSha256,
                lastSyncedApkSha256 = currentSyncedApkSha256
            )

            val hasNewerApkBinary = hasNewerOrModifiedApkBinary(
                installedVersionCode = BuildConfig.VERSION_CODE,
                installedApkSha256 = installedApkSha256,
                lastSyncedApkSha256 = currentSyncedApkSha256,
                remoteManifest = manifest
            )

            val isDismissed = manifest.versionCode == dismissedMajorVersion &&
                (manifest.apkSha256.isBlank() || manifest.apkSha256.equals(dismissedApkSha256, ignoreCase = true))
            val shouldPromptBinaryUpgrade = hasNewerApkBinary && (forceManualCheck || !isDismissed)

            val singleCleanUrl = defaultDirectApkUrl(FIXED_GITHUB_REPO, manifest.versionName)

            when (decision) {
                is GitHubSyncDecision.UpToDate -> {
                    val effectiveName = if (manifest.versionCode > BuildConfig.VERSION_CODE) {
                        manifest.versionName
                    } else {
                        BuildConfig.VERSION_NAME
                    }
                    prefs.edit()
                        .putString(KEY_SYNCED_VERSION_NAME, effectiveName)
                        .putString(KEY_LATEST_DIRECT_APK_URL, singleCleanUrl)
                        .putString(KEY_BACKUP_APK_URL, singleCleanUrl)
                        .apply {
                            if (manifest.apkSha256.isNotBlank() && !hasNewerApkBinary) {
                                putString(KEY_SYNCED_APK_SHA256, manifest.apkSha256)
                            }
                        }
                        .apply()

                    _syncState.value = _syncState.value.copy(
                        isCheckingInBackground = false,
                        effectiveSyncedVersionName = effectiveName,
                        lastSyncedApkSha256 = manifest.apkSha256.ifBlank { currentSyncedApkSha256 },
                        latestDirectApkUrl = singleCleanUrl,
                        backupApkUrl = singleCleanUrl,
                        majorUpdatePrompt = if (shouldPromptBinaryUpgrade) manifest else null,
                        majorUpdateReasonArabic = if (shouldPromptBinaryUpgrade) {
                            manifest.releaseNotesArabic.ifBlank { "يتوفر تحديث جديد (v${manifest.versionName}) جاهز للتثبيت المباشر داخل التطبيق" }
                        } else {
                            null
                        },
                        silentDeltaBannerMessage = if (forceManualCheck && !hasNewerApkBinary) {
                            "التطبيق محدّث بالفعل لأحدث نسخة على GitHub (v$effectiveName)"
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
                    val newApkSha256 = deltaPayload?.apkSha256?.ifBlank { manifest.apkSha256 } ?: manifest.apkSha256
                    val summary = deltaPayload?.deltaSummaryArabic?.ifBlank { manifest.releaseNotesArabic }
                        ?: manifest.releaseNotesArabic.ifBlank { "تمت مزامنة التحديثات تلقائياً" }

                    prefs.edit()
                        .putInt(KEY_SYNCED_VERSION_CODE, newVersionCode)
                        .putString(KEY_SYNCED_VERSION_NAME, newVersionName)
                        .putString(KEY_SYNCED_COMMIT_SHA, newSha)
                        .putString(KEY_LAST_SUMMARY, summary)
                        .putString(KEY_LATEST_DIRECT_APK_URL, singleCleanUrl)
                        .putString(KEY_BACKUP_APK_URL, singleCleanUrl)
                        .apply {
                            if (!hasNewerApkBinary && newApkSha256.isNotBlank()) {
                                putString(KEY_SYNCED_APK_SHA256, newApkSha256)
                            }
                        }
                        .apply()

                    _syncState.value = _syncState.value.copy(
                        isCheckingInBackground = false,
                        effectiveSyncedVersionCode = newVersionCode,
                        effectiveSyncedVersionName = newVersionName,
                        lastSyncedCommitSha = newSha,
                        lastSyncedApkSha256 = if (!hasNewerApkBinary) newApkSha256 else currentSyncedApkSha256,
                        lastSyncSummaryArabic = summary,
                        latestDirectApkUrl = singleCleanUrl,
                        backupApkUrl = singleCleanUrl,
                        majorUpdatePrompt = if (shouldPromptBinaryUpgrade) manifest else null,
                        majorUpdateReasonArabic = if (shouldPromptBinaryUpgrade) {
                            summary.ifBlank { "يتوفر تحديث جديد (v$newVersionName) جاهز للتثبيت المباشر" }
                        } else {
                            null
                        },
                        silentDeltaBannerMessage = if (shouldPromptBinaryUpgrade) {
                            "تم اكتشاف تحديث جديد ($newVersionName)؛ جاري تنزيل التحديث داخل التطبيق..."
                        } else {
                            "تم تحديث آخر التطورات ($newVersionName) تلقائياً بالخلفية"
                        }
                    )

                    if (shouldPromptBinaryUpgrade) {
                        startInAppApkDownloadAndInstall(context, autoLaunchInstaller = true)
                    }
                }

                is GitHubSyncDecision.FullPackageUpgradeRequired -> {
                    prefs.edit()
                        .putString(KEY_LATEST_DIRECT_APK_URL, singleCleanUrl)
                        .putString(KEY_BACKUP_APK_URL, singleCleanUrl)
                        .apply()

                    val shouldShowBar = forceManualCheck || !isDismissed
                    _syncState.value = _syncState.value.copy(
                        isCheckingInBackground = false,
                        latestDirectApkUrl = singleCleanUrl,
                        backupApkUrl = singleCleanUrl,
                        lastSyncSummaryArabic = decision.reasonArabic,
                        majorUpdatePrompt = if (shouldShowBar) manifest else null,
                        majorUpdateReasonArabic = if (shouldShowBar) decision.reasonArabic else null
                    )

                    if (shouldShowBar && hasNewerApkBinary) {
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
        val prompt = _syncState.value.majorUpdatePrompt ?: return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DISMISSED_MAJOR_VERSION, prompt.versionCode)
            .putString(KEY_DISMISSED_APK_SHA256, prompt.apkSha256)
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
     * Downloads the APK binary directly inside the app cache directory with live progress updates (0%..100%),
     * records its SHA-256 fingerprint, and launches the native Android package installer via [FileProvider].
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
            "https://raw.githubusercontent.com/$FIXED_GITHUB_REPO/main/releases/kAM-Lite.apk"
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
            val downloadedSha256 = computeFileSha256(targetFile)
            if (downloadedSha256.isNotBlank()) {
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_SYNCED_APK_SHA256, downloadedSha256)
                    .apply()
            }

            _syncState.value = _syncState.value.copy(
                isDownloadingApk = false,
                downloadProgressPercent = 100,
                downloadedApkReady = true,
                lastSyncedApkSha256 = downloadedSha256.ifBlank { state.lastSyncedApkSha256 },
                downloadErrorArabic = null,
                lastSyncSummaryArabic = "اكتمل تنزيل أحدث نسخة داخل التطبيق؛ جاري فتح شاشة التثبيت..."
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
            val cacheBustedUrl = appendHiddenCacheBuster(urlString)
            val url = URL(cacheBustedUrl)
            var activeConn: HttpURLConnection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 20_000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("Expires", "0")
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
                activeConn = (URL(appendHiddenCacheBuster(newLocation)).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    connectTimeout = 12_000
                    readTimeout = 20_000
                    useCaches = false
                    setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                    setRequestProperty("Pragma", "no-cache")
                    setRequestProperty("Expires", "0")
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
                                ((downloadedBytes * 100L) / (17L * 1024L * 1024L)).toInt().coerceIn(5, 95)
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
     * Triggers the in-app APK download & installer directly.
     */
    fun openLatestFullApkDownload(context: Context, useBackupLink: Boolean = false) {
        val appContext = context.applicationContext
        val apkFile = getLocalApkUpdateFile(appContext)
        if (_syncState.value.downloadedApkReady && apkFile.exists() && apkFile.length() > 100_000L && !useBackupLink) {
            if (installDownloadedApk(context)) return
        }
        startInAppApkDownloadAndInstall(context, autoLaunchInstaller = true)
    }

    private fun fetchTextFromUrl(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val cacheBustedUrl = appendHiddenCacheBuster(urlString)
            val url = URL(cacheBustedUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("Expires", "0")
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
