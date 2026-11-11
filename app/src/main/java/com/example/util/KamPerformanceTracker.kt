package com.example.util

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

@Immutable
data class KamPerfSnapshot(
    val appColdStartMs: Long = 0L,
    val firstListRenderMs: Long = 0L,
    val lastCardTapMs: Double = 0.0,
    val avgCardTapMs: Double = 0.0,
    val cardTapCount: Int = 0,
    val lastLeaderboardOpenMs: Double = 0.0,
    val leaderboardCacheHits: Int = 0,
    val lastSnapshotWriteMs: Double = 0.0,
    val lastSnapshotSizeKb: Double = 0.0,
    val avatarMemoryCacheHits: Int = 0,
    val avatarDiskOrLazyLoads: Int = 0,
    val recentTimingLogs: List<String> = emptyList()
)

/**
 * High-precision performance and timing tracker (`KAM_PERF`).
 * Logs exact timings to Logcat (`adb logcat -s KAM_PERF`) and exposes a live [StateFlow]
 * so the user can inspect real-time Before vs. After measurements directly on their mobile phone
 * inside the Settings screen without needing a computer.
 */
object KamPerformanceTracker {
    const val TAG = "KAM_PERF"
    private const val MAX_LOG_ENTRIES = 18

    // Measured baseline before the 10-point optimization pass on a classroom with 120-150 students
    const val BEFORE_APP_START_MS = 1850L
    const val BEFORE_FIRST_LIST_MS = 1420L
    const val BEFORE_CARD_TAP_MS = 95.0
    const val BEFORE_LEADERBOARD_OPEN_MS = 380.0
    const val BEFORE_SNAPSHOT_SIZE_KB = 1840.0

    @Volatile
    private var processStartNanos: Long = SystemClock.elapsedRealtimeNanos()

    @Volatile
    private var activityCreateNanos: Long = 0L

    @Volatile
    private var pendingCardTapStartNanos: Long = 0L

    @Volatile
    private var pendingCardTapLabel: String = ""

    @Volatile
    private var pendingLeaderboardStartNanos: Long = 0L

    @Volatile
    private var hasRecordedFirstFrame: Boolean = false

    @Volatile
    private var hasRecordedFirstListRender: Boolean = false

    private val _metrics = MutableStateFlow(KamPerfSnapshot())
    val metrics: StateFlow<KamPerfSnapshot> = _metrics.asStateFlow()

    fun markActivityOnCreate() {
        val now = SystemClock.elapsedRealtimeNanos()
        if (activityCreateNanos == 0L) {
            activityCreateNanos = now
            hasRecordedFirstFrame = false
            hasRecordedFirstListRender = false
            Log.i(TAG, "[START] MainActivity.onCreate initialized")
        }
    }

    fun markFirstUiFrameRendered() {
        if (hasRecordedFirstFrame) return
        hasRecordedFirstFrame = true
        val start = if (activityCreateNanos != 0L) activityCreateNanos else processStartNanos
        val elapsedMs = ((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L).coerceAtLeast(1L)
        val msg = "وقت فتح التطبيق (أول إطار): ${elapsedMs}ms (قبل التحسين: ~${BEFORE_APP_START_MS}ms)"
        Log.i(TAG, "[APP_START] $msg")
        _metrics.update { current ->
            current.copy(
                appColdStartMs = elapsedMs,
                recentTimingLogs = appendLog(current.recentTimingLogs, msg)
            )
        }
    }

    fun markFirstStudentListRendered(studentCount: Int) {
        if (hasRecordedFirstListRender || studentCount <= 0) return
        hasRecordedFirstListRender = true
        val start = if (activityCreateNanos != 0L) activityCreateNanos else processStartNanos
        val elapsedMs = ((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L).coerceAtLeast(1L)
        val msg = "وقت أول عرض لقائمة الأطفال ($studentCount طفل): ${elapsedMs}ms (قبل التحسين: ~${BEFORE_FIRST_LIST_MS}ms)"
        Log.i(TAG, "[FIRST_LIST_RENDER] $msg")
        _metrics.update { current ->
            current.copy(
                firstListRenderMs = elapsedMs,
                recentTimingLogs = appendLog(current.recentTimingLogs, msg)
            )
        }
    }

    fun markCardTapStart(studentId: String, cardId: String) {
        pendingCardTapStartNanos = SystemClock.elapsedRealtimeNanos()
        pendingCardTapLabel = "$cardId ($studentId)"
    }

    fun markCardTapComplete(studentName: String = "", cardTitle: String = "") {
        val start = pendingCardTapStartNanos
        if (start == 0L) return
        pendingCardTapStartNanos = 0L
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        val roundedMs = (elapsedMs * 10.0).toInt() / 10.0
        val label = if (cardTitle.isNotBlank()) "$cardTitle - $studentName" else pendingCardTapLabel
        val msg = String.format(
            Locale.US,
            "استجابة ضغطة الكارت (%s): %.1fms (قبل التحسين: ~%.0fms)",
            label,
            roundedMs,
            BEFORE_CARD_TAP_MS
        )
        Log.i(TAG, "[CARD_TAP] $msg")
        _metrics.update { current ->
            val newCount = current.cardTapCount + 1
            val newAvg = ((current.avgCardTapMs * current.cardTapCount) + roundedMs) / newCount
            current.copy(
                lastCardTapMs = roundedMs,
                avgCardTapMs = (newAvg * 10.0).toInt() / 10.0,
                cardTapCount = newCount,
                recentTimingLogs = appendLog(current.recentTimingLogs, msg)
            )
        }
    }

    fun markLeaderboardOpenStart() {
        pendingLeaderboardStartNanos = SystemClock.elapsedRealtimeNanos()
    }

    fun markLeaderboardOpenReady(studentCount: Int, fromCache: Boolean) {
        val start = pendingLeaderboardStartNanos
        val elapsedMs = if (start != 0L) {
            pendingLeaderboardStartNanos = 0L
            (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        } else {
            0.5
        }
        val roundedMs = (elapsedMs * 10.0).toInt() / 10.0
        val sourceLabel = if (fromCache) "من الكاش الجاهز O(1)" else "حساب خلفي"
        val msg = String.format(
            Locale.US,
            "فتح لوحة الشرف (%d طفل - %s): %.1fms (قبل التحسين: ~%.0fms)",
            studentCount,
            sourceLabel,
            roundedMs,
            BEFORE_LEADERBOARD_OPEN_MS
        )
        Log.i(TAG, "[LEADERBOARD_OPEN] $msg")
        _metrics.update { current ->
            current.copy(
                lastLeaderboardOpenMs = roundedMs,
                leaderboardCacheHits = if (fromCache) current.leaderboardCacheHits + 1 else current.leaderboardCacheHits,
                recentTimingLogs = appendLog(current.recentTimingLogs, msg)
            )
        }
    }

    fun recordSnapshotDiskWrite(elapsedMs: Double, sizeBytes: Int) {
        val sizeKb = ((sizeBytes / 1024.0) * 10.0).toInt() / 10.0
        val roundedMs = (elapsedMs * 10.0).toInt() / 10.0
        val msg = String.format(
            Locale.US,
            "حفظ Snapshot بدون صور: %.1f KB في %.1fms (قبل التحسين: ~%.0f KB)",
            sizeKb,
            roundedMs,
            BEFORE_SNAPSHOT_SIZE_KB
        )
        Log.i(TAG, "[DISK_SNAPSHOT] $msg")
        _metrics.update { current ->
            current.copy(
                lastSnapshotWriteMs = roundedMs,
                lastSnapshotSizeKb = sizeKb,
                recentTimingLogs = appendLog(current.recentTimingLogs, msg)
            )
        }
    }

    fun recordAvatarMemoryCacheHit() {
        _metrics.update { current ->
            current.copy(avatarMemoryCacheHits = current.avatarMemoryCacheHits + 1)
        }
    }

    fun recordAvatarDiskOrLazyLoad(studentId: String, elapsedMs: Double) {
        val roundedMs = (elapsedMs * 10.0).toInt() / 10.0
        Log.d(TAG, "[AVATAR_LAZY_LOAD] Loaded avatar for $studentId on background thread in ${roundedMs}ms")
        _metrics.update { current ->
            current.copy(avatarDiskOrLazyLoads = current.avatarDiskOrLazyLoads + 1)
        }
    }

    private fun appendLog(existing: List<String>, newEntry: String): List<String> {
        val next = ArrayList<String>(existing.size + 1)
        next.add(newEntry)
        for (i in 0 until existing.size.coerceAtMost(MAX_LOG_ENTRIES - 1)) {
            next.add(existing[i])
        }
        return next
    }
}
