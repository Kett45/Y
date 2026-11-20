package com.example.data.sync

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Immutable
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
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Represents the parsed `releases/version.json` manifest from the GitHub repository.
 */
@Immutable
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
@Immutable
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

@Immutable
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
    val isUpToDateConfirmed: Boolean = false,
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
    private const val KEY_DOWNLOADED_APK_SHA256 = "downloaded_apk_sha256"
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

    @Volatile
    private var hostActivityRef: WeakReference<Activity>? = null

    @Volatile
    private var pendingInstallAfterPermissionGrant: Boolean = false

    fun attachActivity(activity: Activity) {
        hostActivityRef = WeakReference(activity)
    }

    fun onActivityResumed(activity: Activity) {
        hostActivityRef = WeakReference(activity)
        if (pendingInstallAfterPermissionGrant) {
            val canInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { activity.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
            } else {
                true
            }
            if (canInstall) {
                pendingInstallAfterPermissionGrant = false
                installDownloadedApk(activity)
            }
        }
    }

    private fun extractActivity(context: Context): Activity? {
        var current: Context? = context
        var depth = 0
        while (current is ContextWrapper && depth < 10) {
            if (current is Activity) return current
            current = current.baseContext
            depth++
        }
        return current as? Activity
    }

    private fun resolveLaunchContext(context: Context): Context {
        val fromArg = extractActivity(context)?.takeUnless { it.isFinishing || it.isDestroyed }
        if (fromArg != null) {
            hostActivityRef = WeakReference(fromArg)
            return fromArg
        }
        val fromRef = hostActivityRef?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
        return fromRef ?: context.applicationContext
    }

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
            if (installedApkSha256.isNotBlank()) {
                return !installedApkSha256.equals(remoteHash, ignoreCase = true)
            }
            if (lastSyncedApkSha256.isNotBlank()) {
                return !lastSyncedApkSha256.equals(remoteHash, ignoreCase = true)
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
        val binaryGap = (remoteManifest.versionCode - installedBinaryVersionCode).coerceAtLeast(0)
        val hasBinaryVersionUpgrade = remoteManifest.versionCode > installedBinaryVersionCode

        val remoteHash = remoteManifest.apkSha256.trim()
        val effectiveBaselineSha = installedApkSha256.ifBlank { lastSyncedApkSha256 }
        val apkSha256Mismatch = remoteManifest.versionCode >= installedBinaryVersionCode &&
            remoteHash.length >= 16 &&
            effectiveBaselineSha.isNotBlank() &&
            !effectiveBaselineSha.equals(remoteHash, ignoreCase = true)

        val isSameVersionAndCommit = !hasBinaryVersionUpgrade &&
            !apkSha256Mismatch &&
            remoteManifest.versionCode <= installedBinaryVersionCode &&
            (remoteManifest.commitSha.isBlank() ||
                remoteManifest.commitSha == "latest" ||
                remoteManifest.commitSha == lastSyncedCommitSha)

        val exceedsMaxDeltaGap = binaryGap > remoteManifest.maxDeltaGap.coerceAtLeast(1)
        val belowMinBaseVersion = installedBinaryVersionCode < remoteManifest.minDeltaBaseVersionCode
        val forceFull = hasBinaryVersionUpgrade || apkSha256Mismatch

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
                activeVersionCode = installedBinaryVersionCode,
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

    private fun isOnMainThread(): Boolean {
        return runCatching {
            android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
        }.getOrDefault(false)
    }

    /**
     * Loads cached delta state from SharedPreferences on [Dispatchers.IO] so startup is 0ms
     * on the Main Thread without triggering StrictMode DiskReadViolation / DiskWriteViolation.
     */
    fun initializeFromLocalCache(context: Context) {
        val appCtx = context.applicationContext
        if (isOnMainThread()) {
            downloadScope.launch {
                initializeFromLocalCacheInternal(appCtx)
            }
        } else {
            initializeFromLocalCacheInternal(appCtx)
        }
    }

    private fun initializeFromLocalCacheInternal(appCtx: Context) {
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

        val apkFile = getLocalApkUpdateFile(appCtx)
        val installedSha = prefs.getString(KEY_INSTALLED_APK_SHA256, "").orEmpty()
        val hasApkFileOnDisk = apkFile.exists() && apkFile.length() > 100_000L

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
            downloadedApkReady = hasApkFileOnDisk
        )

        if (hasApkFileOnDisk && installedSha.isNotBlank()) {
            downloadScope.launch {
                val cachedApkFileSha = computeFileSha256(apkFile)
                if (cachedApkFileSha.equals(installedSha, ignoreCase = true)) {
                    runCatching { apkFile.delete() }
                    _syncState.value = _syncState.value.copy(downloadedApkReady = false)
                }
            }
        }
    }

    private fun getLocalApkUpdateFile(context: Context): File {
        val updateDir = File(context.cacheDir, APK_UPDATE_DIR).apply { mkdirs() }
        return File(updateDir, APK_UPDATE_FILENAME)
    }

    /**
     * Unified smart action for the single update button in Settings:
     * - If a verified newer APK is already downloaded and ready, opens the native installer immediately.
     * - Otherwise checks GitHub first (`versionCode` + `apkSha256`):
     *   • If a newer version or modified APK exists -> downloads and installs automatically.
     *   • If already on the latest version -> confirms to the user that the app is up to date without downloading.
     */
    fun triggerSmartUpdateCheckOrInstall(context: Context) {
        if (_syncState.value.isCheckingInBackground || _syncState.value.isDownloadingApk) return
        extractActivity(context)?.let { attachActivity(it) }
        val appContext = context.applicationContext
        downloadScope.launch {
            val apkFile = getLocalApkUpdateFile(appContext)
            if (_syncState.value.downloadedApkReady &&
                _syncState.value.majorUpdatePrompt != null &&
                isValidApkFile(appContext, apkFile)
            ) {
                val launched = withContext(Dispatchers.Main) {
                    installDownloadedApk(resolveLaunchContext(context))
                }
                if (launched) return@launch
            }
            checkAndSyncSilently(context, forceManualCheck = true)
        }
    }

    /**
     * Forces a fresh download of the latest APK from GitHub (clearing any cached APK file)
     * and immediately launches the Android Package Installer once verified.
     */
    fun forceDownloadAndInstallLatestApk(context: Context) {
        if (_syncState.value.isDownloadingApk) return
        extractActivity(context)?.let { attachActivity(it) }
        val appContext = context.applicationContext
        downloadScope.launch {
            runCatching { getLocalApkUpdateFile(appContext).delete() }
            _syncState.value = _syncState.value.copy(
                downloadedApkReady = false,
                downloadErrorArabic = null,
                isUpToDateConfirmed = false,
                lastSyncSummaryArabic = "جاري تنزيل أحدث نسخة من GitHub..."
            )
            downloadAndInstallLatestApk(context, autoLaunchInstaller = true)
        }
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
                ?: fetchTextFromUrl("https://github.com/$FIXED_GITHUB_REPO/raw/main/releases/version.json")
                ?: fetchTextFromUrl(defaultVersionManifestUrl(FIXED_GITHUB_REPO, "master"))

            if (manifestJson.isNullOrBlank()) {
                val failMsg = "تعذّر الوصول إلى GitHub للتحقق من التحديث؛ تأكد من الإنترنت وحاول مجدداً"
                _syncState.value = _syncState.value.copy(
                    isCheckingInBackground = false,
                    isUpToDateConfirmed = false,
                    downloadErrorArabic = if (forceManualCheck) failMsg else _syncState.value.downloadErrorArabic,
                    silentDeltaBannerMessage = if (forceManualCheck) failMsg else null
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
            var currentSyncedApkSha256 = installedApkSha256.ifBlank {
                prefs.getString(KEY_SYNCED_APK_SHA256, "").orEmpty()
            }

            // Record actual installed binary SHA-256 baseline (never confuse downloaded APK with installed APK)
            if (installedApkSha256.isNotBlank()) {
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

            val cachedApk = getLocalApkUpdateFile(appContext)
            val downloadedFileMatchesRemote = hasNewerApkBinary &&
                isValidApkFile(appContext, cachedApk) &&
                (manifest.apkSha256.isBlank() ||
                    computeFileSha256(cachedApk).equals(manifest.apkSha256.trim(), ignoreCase = true))

            if (!hasNewerApkBinary || (cachedApk.exists() && !downloadedFileMatchesRemote)) {
                if (cachedApk.exists()) {
                    runCatching { cachedApk.delete() }
                }
            }

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
                    val upToDateMsg = "أنت على أحدث إصدار بالفعل (v$effectiveName) — لا يوجد تحديث جديد للتنزيل"
                    prefs.edit()
                        .putString(KEY_SYNCED_VERSION_NAME, effectiveName)
                        .putString(KEY_LATEST_DIRECT_APK_URL, singleCleanUrl)
                        .putString(KEY_BACKUP_APK_URL, singleCleanUrl)
                        .apply {
                            if (manifest.apkSha256.isNotBlank() && !hasNewerApkBinary) {
                                putString(KEY_SYNCED_APK_SHA256, manifest.apkSha256)
                            }
                            if (forceManualCheck && !hasNewerApkBinary) {
                                putString(KEY_LAST_SUMMARY, upToDateMsg)
                            }
                        }
                        .apply()

                    _syncState.value = _syncState.value.copy(
                        isCheckingInBackground = false,
                        effectiveSyncedVersionName = effectiveName,
                        lastSyncedApkSha256 = manifest.apkSha256.ifBlank { currentSyncedApkSha256 },
                        lastSyncSummaryArabic = if (forceManualCheck && !hasNewerApkBinary) {
                            upToDateMsg
                        } else {
                            _syncState.value.lastSyncSummaryArabic
                        },
                        latestDirectApkUrl = singleCleanUrl,
                        backupApkUrl = singleCleanUrl,
                        downloadedApkReady = if (!hasNewerApkBinary) false else _syncState.value.downloadedApkReady,
                        isUpToDateConfirmed = !hasNewerApkBinary,
                        majorUpdatePrompt = if (shouldPromptBinaryUpgrade) manifest else null,
                        majorUpdateReasonArabic = if (shouldPromptBinaryUpgrade) {
                            manifest.releaseNotesArabic.ifBlank { "يتوفر تحديث جديد (v${manifest.versionName}) جاهز للتثبيت المباشر داخل التطبيق" }
                        } else {
                            null
                        },
                        silentDeltaBannerMessage = if (forceManualCheck && !hasNewerApkBinary) {
                            upToDateMsg
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
                    val upToDateMsg = "أنت على أحدث إصدار بالفعل (v$newVersionName) — لا يوجد تحديث جديد للتنزيل"
                    val summary = if (forceManualCheck && !hasNewerApkBinary) {
                        upToDateMsg
                    } else {
                        deltaPayload?.deltaSummaryArabic?.ifBlank { manifest.releaseNotesArabic }
                            ?: manifest.releaseNotesArabic.ifBlank { "تمت مزامنة التحديثات تلقائياً" }
                    }

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
                        downloadedApkReady = if (!hasNewerApkBinary) false else _syncState.value.downloadedApkReady,
                        isUpToDateConfirmed = !hasNewerApkBinary,
                        majorUpdatePrompt = if (shouldPromptBinaryUpgrade) manifest else null,
                        majorUpdateReasonArabic = if (shouldPromptBinaryUpgrade) {
                            summary.ifBlank { "يتوفر تحديث جديد (v$newVersionName) جاهز للتثبيت المباشر" }
                        } else {
                            null
                        },
                        silentDeltaBannerMessage = when {
                            shouldPromptBinaryUpgrade -> "تم اكتشاف تحديث جديد ($newVersionName)؛ جاري تنزيل التحديث داخل التطبيق..."
                            forceManualCheck && !hasNewerApkBinary -> upToDateMsg
                            else -> "تم تحديث آخر التطورات ($newVersionName) تلقائياً بالخلفية"
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
                        downloadedApkReady = downloadedFileMatchesRemote,
                        isUpToDateConfirmed = false,
                        lastSyncSummaryArabic = decision.reasonArabic,
                        majorUpdatePrompt = if (shouldShowBar) manifest else null,
                        majorUpdateReasonArabic = if (shouldShowBar) decision.reasonArabic else null
                    )

                    if (shouldShowBar && hasNewerApkBinary) {
                        if (downloadedFileMatchesRemote) {
                            withContext(Dispatchers.Main) {
                                installDownloadedApk(resolveLaunchContext(context))
                            }
                        } else {
                            startInAppApkDownloadAndInstall(context, autoLaunchInstaller = true)
                        }
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
        val appContext = context.applicationContext
        _syncState.value = _syncState.value.copy(
            majorUpdatePrompt = null,
            majorUpdateReasonArabic = null
        )
        downloadScope.launch {
            appContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_DISMISSED_MAJOR_VERSION, prompt.versionCode)
                .putString(KEY_DISMISSED_APK_SHA256, prompt.apkSha256)
                .apply()
        }
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
        extractActivity(context)?.let { attachActivity(it) }
        downloadScope.launch {
            downloadAndInstallLatestApk(context, autoLaunchInstaller = autoLaunchInstaller)
        }
    }

    /**
     * Validates that [file] exists, has a realistic APK size (> 500 KB), begins with ZIP/APK magic
     * bytes (`PK\u0003\u0004`), and parses cleanly as an APK matching this app's package name.
     */
    private fun isValidApkFile(context: Context, file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 500_000L) return false
        val hasZipHeader = runCatching {
            FileInputStream(file).use { fis ->
                val magic = ByteArray(4)
                fis.read(magic) == 4 &&
                    magic[0] == 0x50.toByte() &&
                    magic[1] == 0x4B.toByte() &&
                    magic[2] == 0x03.toByte() &&
                    magic[3] == 0x04.toByte()
            }
        }.getOrDefault(false)
        if (!hasZipHeader) return false

        return runCatching {
            val pm = context.applicationContext.packageManager
            @Suppress("DEPRECATION")
            val archiveInfo = pm.getPackageArchiveInfo(file.absolutePath, 0)
            archiveInfo != null && archiveInfo.packageName == context.applicationContext.packageName
        }.getOrDefault(false)
    }

    /**
     * Downloads the APK binary directly inside the app cache directory with live progress updates (0%..100%),
     * validates APK integrity, records its SHA-256 fingerprint, and launches the native Android Package Installer.
     */
    suspend fun downloadAndInstallLatestApk(
        context: Context,
        autoLaunchInstaller: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        if (_syncState.value.isDownloadingApk) return@withContext false
        extractActivity(context)?.let { attachActivity(it) }
        val appContext = context.applicationContext
        val state = _syncState.value

        val candidateUrls = listOf(
            "https://github.com/$FIXED_GITHUB_REPO/releases/latest/download/kAM-Lite.apk",
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
            if (tempFile.exists()) runCatching { tempFile.delete() }
            if (downloadApkFromCandidateUrl(urlStr, tempFile)) {
                if (isValidApkFile(appContext, tempFile)) {
                    if (targetFile.exists()) targetFile.delete()
                    val renamed = tempFile.renameTo(targetFile)
                    if (!renamed) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                    downloadedSuccess = true
                    break
                } else {
                    runCatching { tempFile.delete() }
                }
            }
        }

        if (downloadedSuccess && isValidApkFile(appContext, targetFile)) {
            val downloadedSha256 = computeFileSha256(targetFile)
            if (downloadedSha256.isNotBlank()) {
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_DOWNLOADED_APK_SHA256, downloadedSha256)
                    .apply()
            }

            _syncState.value = _syncState.value.copy(
                isDownloadingApk = false,
                downloadProgressPercent = 100,
                downloadedApkReady = true,
                downloadErrorArabic = null,
                lastSyncSummaryArabic = "اكتمل تنزيل أحدث نسخة داخل التطبيق؛ جاري فتح شاشة التثبيت..."
            )
            if (autoLaunchInstaller) {
                withContext(Dispatchers.Main) {
                    installDownloadedApk(resolveLaunchContext(context))
                }
            }
            true
        } else {
            if (tempFile.exists()) tempFile.delete()
            val errMsg = "تعذر تنزيل ملف التحديث الصحيح من GitHub؛ تأكد من الإنترنت وحاول مرة أخرى"
            _syncState.value = _syncState.value.copy(
                isDownloadingApk = false,
                downloadProgressPercent = 0,
                downloadedApkReady = false,
                downloadErrorArabic = errMsg,
                silentDeltaBannerMessage = errMsg
            )
            false
        }
    }

    private fun downloadApkFromCandidateUrl(urlString: String, destinationFile: File): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            // Only cache-bust the initial GitHub entry URL; NEVER mutate signed redirect URLs (S3/CDN)!
            val initialUrl = if (urlString.contains("releases/latest/download")) {
                urlString.trim()
            } else {
                appendHiddenCacheBuster(urlString)
            }
            var activeConn: HttpURLConnection = (URL(initialUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 45_000
                useCaches = false
                setRequestProperty("User-Agent", "KamLite-Android/${BuildConfig.VERSION_NAME}")
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
                ) && redirectCount < 8
            ) {
                val rawLocation = activeConn.getHeaderField("Location") ?: break
                val resolvedUrl = URL(activeConn.url, rawLocation)
                activeConn.disconnect()
                activeConn = (resolvedUrl.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = 20_000
                    readTimeout = 45_000
                    useCaches = false
                    setRequestProperty("User-Agent", "KamLite-Android/${BuildConfig.VERSION_NAME}")
                    setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
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
                    val buffer = ByteArray(32 * 1024)
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
                                ((downloadedBytes * 100L) / (14L * 1024L * 1024L)).toInt().coerceIn(5, 95)
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
            destinationFile.exists() && destinationFile.length() > 500_000L
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Launches the Android system Package Installer for the downloaded APK using [FileProvider].
     * Uses the foreground [Activity] context when available so Android 10-16 never blocks the launch.
     */
    fun installDownloadedApk(context: Context): Boolean {
        val launchContext = resolveLaunchContext(context)
        val appContext = launchContext.applicationContext
        val apkFile = getLocalApkUpdateFile(appContext)
        if (!isValidApkFile(appContext, apkFile)) {
            runCatching { apkFile.delete() }
            _syncState.value = _syncState.value.copy(downloadedApkReady = false)
            startInAppApkDownloadAndInstall(launchContext, autoLaunchInstaller = true)
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = runCatching { appContext.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
            if (!canInstall) {
                pendingInstallAfterPermissionGrant = true
                runCatching {
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${appContext.packageName}")
                    ).apply {
                        if (launchContext !is Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    launchContext.startActivity(settingsIntent)
                    _syncState.value = _syncState.value.copy(
                        downloadErrorArabic = null,
                        silentDeltaBannerMessage = "يرجى تفعيل 'السماح من هذا المصدر' ثم الرجوع للتطبيق وسيبدأ التثبيت تلقائياً"
                    )
                }.onFailure { err ->
                    pendingInstallAfterPermissionGrant = false
                    val msg = "يرجى تفعيل إذن تثبيت التطبيقات من الإعدادات ثم المحاولة مجدداً (${err.localizedMessage.orEmpty()})"
                    _syncState.value = _syncState.value.copy(
                        downloadErrorArabic = msg,
                        silentDeltaBannerMessage = msg
                    )
                }
                return false
            }
        }

        pendingInstallAfterPermissionGrant = false

        val apkUri = runCatching {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                apkFile
            )
        }.getOrElse { err ->
            val msg = "تعذر تجهيز ملف التثبيت: ${err.localizedMessage ?: "خطأ في FileProvider"}"
            _syncState.value = _syncState.value.copy(
                downloadErrorArabic = msg,
                silentDeltaBannerMessage = msg
            )
            return false
        }

        // Primary: ACTION_VIEW with ClipData + GRANT_READ_URI_PERMISSION
        val viewLaunched = runCatching {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                clipData = ClipData.newRawUri("KamLiteUpdate", apkUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (launchContext !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            launchContext.startActivity(installIntent)
            true
        }.getOrDefault(false)

        if (viewLaunched) {
            _syncState.value = _syncState.value.copy(downloadErrorArabic = null)
            return true
        }

        // Fallback: ACTION_INSTALL_PACKAGE for OEM ROMs that reject ACTION_VIEW
        return runCatching {
            @Suppress("DEPRECATION")
            val fallbackIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                clipData = ClipData.newRawUri("KamLiteUpdate", apkUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                if (launchContext !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            launchContext.startActivity(fallbackIntent)
            _syncState.value = _syncState.value.copy(downloadErrorArabic = null)
            true
        }.getOrElse { err ->
            val msg = "تعذر فتح شاشة التثبيت تلقائياً: ${err.localizedMessage ?: "حاول مرة أخرى"}"
            _syncState.value = _syncState.value.copy(
                downloadErrorArabic = msg,
                silentDeltaBannerMessage = msg
            )
            false
        }
    }

    /**
     * Triggers the in-app APK download & installer directly.
     */
    fun openLatestFullApkDownload(context: Context, useBackupLink: Boolean = false) {
        extractActivity(context)?.let { attachActivity(it) }
        val appContext = context.applicationContext
        val apkFile = getLocalApkUpdateFile(appContext)
        if (_syncState.value.downloadedApkReady && isValidApkFile(appContext, apkFile) && !useBackupLink) {
            if (installDownloadedApk(resolveLaunchContext(context))) return
        }
        startInAppApkDownloadAndInstall(context, autoLaunchInstaller = true)
    }

    private fun fetchTextFromUrl(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val cacheBustedUrl = appendHiddenCacheBuster(urlString)
            val url = URL(cacheBustedUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 12_000
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
