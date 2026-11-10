package com.example.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
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
 * 2. [DeltaOnlySync]: Version difference is small (`<= maxDeltaGap`) and compatible -> download ONLY
 *    the few-KB `delta-sync.json` and apply updates silently in-place without downloading full APK.
 * 3. [FullPackageUpgradeRequired]: Version difference is large (`> maxDeltaGap` or `installed < minDeltaBaseVersionCode`
 *    or `requiresFullInstall == true`) -> apply safe config delta AND surface a non-intrusive 1-tap bar
 *    to install the full updated APK from the repo's direct `latest` release link.
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

    /**
     * Fixed GitHub repository slug baked into BuildConfig (automatically updated by GitHub Actions on build).
     */
    val FIXED_GITHUB_REPO: String
        get() = BuildConfig.GITHUB_REPO.trim().ifBlank { "fkk7777k/kAM-Lite" }

    fun defaultVersionManifestUrl(repoSlug: String = FIXED_GITHUB_REPO, branch: String = "main"): String =
        "https://raw.githubusercontent.com/$repoSlug/$branch/releases/version.json"

    fun defaultDeltaPayloadUrl(repoSlug: String = FIXED_GITHUB_REPO, branch: String = "main"): String =
        "https://raw.githubusercontent.com/$repoSlug/$branch/releases/delta-sync.json"

    fun defaultDirectApkUrl(repoSlug: String = FIXED_GITHUB_REPO): String =
        "https://github.com/$repoSlug/releases/download/latest/kAM-Lite.apk"

    fun defaultRawApkUrl(repoSlug: String = FIXED_GITHUB_REPO, branch: String = "main"): String =
        "https://raw.githubusercontent.com/$repoSlug/$branch/releases/kAM-Lite.apk"

    private val _syncState = MutableStateFlow(GitHubSyncUiState())
    val syncState: StateFlow<GitHubSyncUiState> = _syncState.asStateFlow()

    /**
     * Pure, deterministic strategy evaluator:
     * - Decides whether to do nothing ([GitHubSyncDecision.UpToDate]),
     * - Apply a lightweight delta-only update without downloading the full APK ([GitHubSyncDecision.DeltaOnlySync]),
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
                forceFull -> "يحتوي الإصدار ${remoteManifest.versionName} على تغييرات جذرية في النظام تتطلب تثبيت الحزمة الكاملة"
                exceedsMaxDeltaGap -> "الفارق بين نسختك الحالية وأحدث إصدار كبير ($binaryGap إصدارات)؛ يلزم التحديث الكامل لمرة واحدة"
                else -> "نسختك الحالية أقدم من الحد الأدنى للتحديث الجزئي؛ يلزم تثبيت أحدث حزمة كاملة"
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
                apkDirectUrl = obj.optString("apkDirectUrl", defaultDirectApkUrl()),
                backupApkUrl = obj.optString("backupApkUrl", defaultRawApkUrl()),
                deltaSyncUrl = obj.optString("deltaSyncUrl", defaultDeltaPayloadUrl()),
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
                latestApkDirectUrl = obj.optString("latestApkDirectUrl", defaultDirectApkUrl()),
                backupApkUrl = obj.optString("backupApkUrl", defaultRawApkUrl()),
                changedModules = modules,
                announcementBanner = dynConfig?.optString("announcementBanner", "").orEmpty()
            )
        }.getOrNull()
    }

    /**
     * Loads cached delta state from SharedPreferences immediately so startup is 0ms.
     */
    fun initializeFromLocalCache(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedCode = prefs.getInt(KEY_SYNCED_VERSION_CODE, BuildConfig.VERSION_CODE)
            .coerceAtLeast(BuildConfig.VERSION_CODE)
        val cachedName = prefs.getString(KEY_SYNCED_VERSION_NAME, null)
            ?.takeIf { it.isNotBlank() && cachedCode >= BuildConfig.VERSION_CODE }
            ?: BuildConfig.VERSION_NAME
        val cachedSha = prefs.getString(KEY_SYNCED_COMMIT_SHA, "").orEmpty()
        val cachedSummary = prefs.getString(KEY_LAST_SUMMARY, null)
            ?.takeIf { it.isNotBlank() }
            ?: "متزامن تلقائياً مع مستودع GitHub"
        val cachedDirectUrl = prefs.getString(KEY_LATEST_DIRECT_APK_URL, null)
            ?.takeIf { it.isNotBlank() }
            ?: defaultDirectApkUrl()
        val cachedBackupUrl = prefs.getString(KEY_BACKUP_APK_URL, null)
            ?.takeIf { it.isNotBlank() }
            ?: defaultRawApkUrl()

        _syncState.value = _syncState.value.copy(
            repositorySlug = FIXED_GITHUB_REPO,
            installedBinaryVersionCode = BuildConfig.VERSION_CODE,
            installedBinaryVersionName = BuildConfig.VERSION_NAME,
            effectiveSyncedVersionCode = cachedCode,
            effectiveSyncedVersionName = cachedName,
            lastSyncedCommitSha = cachedSha,
            lastSyncSummaryArabic = cachedSummary,
            latestDirectApkUrl = cachedDirectUrl,
            backupApkUrl = cachedBackupUrl
        )
    }

    /**
     * Performs a silent background check against the GitHub repository:
     * - If a small incremental update exists (`DeltaOnlySync`), downloads ONLY `delta-sync.json` (a few KB)
     *   and applies it in-place without downloading the full APK.
     * - If the version difference is large (`FullPackageUpgradeRequired`), updates the direct links
     *   and displays a compact non-intrusive 1-tap bar so the user can install the full APK.
     */
    suspend fun checkAndSyncSilently(
        context: Context,
        forceManualCheck: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        initializeFromLocalCache(appContext)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        _syncState.value = _syncState.value.copy(isCheckingInBackground = true)

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

            when (decision) {
                is GitHubSyncDecision.UpToDate -> {
                    prefs.edit()
                        .putString(KEY_LATEST_DIRECT_APK_URL, manifest.apkDirectUrl.ifBlank { defaultDirectApkUrl() })
                        .putString(KEY_BACKUP_APK_URL, manifest.backupApkUrl.ifBlank { defaultRawApkUrl() })
                        .apply()

                    _syncState.value = _syncState.value.copy(
                        isCheckingInBackground = false,
                        latestDirectApkUrl = manifest.apkDirectUrl.ifBlank { defaultDirectApkUrl() },
                        backupApkUrl = manifest.backupApkUrl.ifBlank { defaultRawApkUrl() },
                        majorUpdatePrompt = null,
                        majorUpdateReasonArabic = null,
                        silentDeltaBannerMessage = if (forceManualCheck) {
                            "التطبيق محدّث بالفعل لأعلى إصدار (${_syncState.value.effectiveSyncedVersionName})"
                        } else {
                            null
                        }
                    )
                }

                is GitHubSyncDecision.DeltaOnlySync -> {
                    // Download ONLY the lightweight delta JSON (~1 KB) instead of the full APK!
                    val deltaUrl = manifest.deltaSyncUrl.ifBlank { defaultDeltaPayloadUrl(FIXED_GITHUB_REPO, "main") }
                    val deltaJson = fetchTextFromUrl(deltaUrl)
                        ?: fetchTextFromUrl(defaultDeltaPayloadUrl(FIXED_GITHUB_REPO, "master"))
                    val deltaPayload = deltaJson?.let { parseDeltaPayloadJson(it) }

                    val newVersionCode = deltaPayload?.targetVersionCode ?: manifest.versionCode
                    val newVersionName = deltaPayload?.targetVersionName?.ifBlank { manifest.versionName }
                        ?: manifest.versionName
                    val newSha = deltaPayload?.commitSha?.ifBlank { manifest.commitSha } ?: manifest.commitSha
                    val summary = deltaPayload?.deltaSummaryArabic?.ifBlank { manifest.releaseNotesArabic }
                        ?: manifest.releaseNotesArabic.ifBlank { "تمت مزامنة التحديثات الخفيفة تلقائياً بالخلفية" }
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
                        majorUpdatePrompt = null,
                        majorUpdateReasonArabic = null,
                        silentDeltaBannerMessage = "تم تحديث آخر التطورات ($newVersionName) تلقائياً بالخلفية بدون تنزيل التطبيق بالكامل"
                    )
                }

                is GitHubSyncDecision.FullPackageUpgradeRequired -> {
                    // Scenario where the version gap between installed and remote is LARGE:
                    // 1. Update direct download links from the manifest so the user gets the newest full APK.
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
     * Opens the always-updated direct GitHub latest APK link (with fallback to the raw repository APK link)
     * when a major version gap requires a full package upgrade or when the user taps download in Settings.
     */
    fun openLatestFullApkDownload(context: Context, useBackupLink: Boolean = false) {
        val state = _syncState.value
        val targetUrl = if (useBackupLink) {
            state.backupApkUrl.ifBlank { defaultRawApkUrl() }
        } else {
            state.latestDirectApkUrl.ifBlank { defaultDirectApkUrl() }
        }
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            if (!useBackupLink) {
                openLatestFullApkDownload(context, useBackupLink = true)
            }
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
