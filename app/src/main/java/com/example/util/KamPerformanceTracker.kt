package com.example.util

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Immutable
data class StrictModeViolationEntry(
    val signature: String,
    val type: String,
    val fileAndLine: String,
    val methodName: String,
    val count: Int
)

@Immutable
data class ContextFrameBreakdown(
    val contextName: String,
    val totalFrames: Long,
    val jankFrames: Long,
    val jankPercentage: Double,
    val avgFrameMs: Double,
    val worstFrameMs: Double
)

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
    // Real Frame Metrics & Jank tracking (without self-triggering recomposition loop)
    val totalFramesTracked: Long = 0L,
    val jankFramesCount: Long = 0L,
    val jankPercentage: Double = 0.0,
    val avgFrameDurationMs: Double = 0.0,
    val worstFrameDurationMs: Double = 0.0,
    val worstFrameContext: String = "",
    val lastJankContext: String = "",
    val contextBreakdowns: List<ContextFrameBreakdown> = emptyList(),
    // Scroll benchmark (20s / 58 students)
    val lastScrollBenchmarkAvgMs: Double = 0.0,
    val lastScrollBenchmarkWorstMs: Double = 0.0,
    val lastScrollBenchmarkJankPct: Double = 0.0,
    val lastScrollBenchmarkFrames: Long = 0L,
    // StrictMode tracking with exact file, line, method, and frequency
    val strictModeEnabled: Boolean = false,
    val strictModeViolationsCount: Int = 0,
    val strictModeViolationDetails: List<StrictModeViolationEntry> = emptyList(),
    // Recomposition & Row Complexity counters
    val studentRowRecompositionCount: Long = 0L,
    val recentTimingLogs: List<String> = emptyList()
)

/**
 * High-precision performance, FrameMetrics, and StrictMode tracker (`KAM_PERF`).
 *
 * Key design guarantees:
 * 1. Lock-free recording on `KamFrameMetricsThread` — NEVER triggers `_metrics` StateFlow updates
 *    on every frame or during active scrolling, eliminating the self-triggering recomposition jank loop.
 * 2. Per-context FrameMetrics breakdown (`تمرير الرئيسية`, `ضغط كارت`, `لوحة الشرف`, `شاشة الإعدادات`, `فتح التطبيق`).
 * 3. Detailed StrictMode violation aggregation by (Type, File:Line, Method) sorted by occurrence count.
 */
object KamPerformanceTracker {
    const val TAG = "KAM_PERF"
    private const val MAX_LOG_ENTRIES = 24
    private const val METRICS_PUBLISH_INTERVAL_NANOS = 1_500_000_000L // 1.5s throttle

    // Measured baselines before optimizations
    const val BEFORE_APP_START_MS = 1688L
    const val BEFORE_FIRST_LIST_MS = 1692L
    const val BEFORE_CARD_TAP_MS = 95.0
    const val BEFORE_LEADERBOARD_OPEN_MS = 380.0
    const val BEFORE_SNAPSHOT_SIZE_KB = 1840.0
    const val BEFORE_JANK_PERCENT = 92.9
    const val BEFORE_AVG_FRAME_MS = 36.9
    const val BEFORE_WORST_FRAME_MS = 1530.0
    const val BEFORE_STRICT_MODE_COUNT = 19

    const val CTX_APP_START = "فتح التطبيق"
    const val CTX_HOME_SCROLL = "تمرير الرئيسية"
    const val CTX_HOME_IDLE = "الرئيسية (ثابت)"
    const val CTX_CARD_TAP = "ضغط كارت"
    const val CTX_LEADERBOARD = "لوحة الشرف"
    const val CTX_SETTINGS = "شاشة الإعدادات"
    const val CTX_GROUP = "شاشة المجموعة"

    private class ContextBucket(val name: String) {
        val totalFrames = AtomicLong(0L)
        val jankFrames = AtomicLong(0L)
        val totalDurationNanos = AtomicLong(0L)
        val worstFrameNanos = AtomicLong(0L)

        fun record(durationNanos: Long, isJank: Boolean) {
            totalFrames.incrementAndGet()
            totalDurationNanos.addAndGet(durationNanos)
            if (isJank) {
                jankFrames.incrementAndGet()
            }
            var prevWorst = worstFrameNanos.get()
            while (durationNanos > prevWorst) {
                if (worstFrameNanos.compareAndSet(prevWorst, durationNanos)) break
                prevWorst = worstFrameNanos.get()
            }
        }

        fun reset() {
            totalFrames.set(0L)
            jankFrames.set(0L)
            totalDurationNanos.set(0L)
            worstFrameNanos.set(0L)
        }

        fun toBreakdown(): ContextFrameBreakdown? {
            val total = totalFrames.get()
            if (total <= 0L) return null
            val janks = jankFrames.get()
            val sumNs = totalDurationNanos.get()
            val worstNs = worstFrameNanos.get()
            val jankPct = ((janks.toDouble() / total) * 1000.0).toInt() / 10.0
            val avgMs = (((sumNs.toDouble() / total) / 1_000_000.0) * 10.0).toInt() / 10.0
            val worstMs = ((worstNs / 1_000_000.0) * 10.0).toInt() / 10.0
            return ContextFrameBreakdown(
                contextName = name,
                totalFrames = total,
                jankFrames = janks,
                jankPercentage = jankPct,
                avgFrameMs = avgMs,
                worstFrameMs = worstMs
            )
        }
    }

    private class ViolationCounter(
        val signature: String,
        val type: String,
        val fileAndLine: String,
        val methodName: String
    ) {
        val count = AtomicInteger(0)
    }

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

    @Volatile
    var currentInteractionContext: String = CTX_APP_START
        private set

    @Volatile
    var isHomeScrollingActive: Boolean = false
        private set

    @Volatile
    var isStrictModeActive: Boolean = false

    private val totalFrames = AtomicLong(0L)
    private val jankFrames = AtomicLong(0L)
    private val totalDurationNanosSum = AtomicLong(0L)
    private val worstFrameNanos = AtomicLong(0L)

    @Volatile
    private var worstFrameContextLabel: String = ""

    @Volatile
    private var lastJankContextLabel: String = ""

    @Volatile
    private var lastMetricsPublishNanos: Long = 0L

    private val contextBuckets = ConcurrentHashMap<String, ContextBucket>()
    private val strictModeViolations = ConcurrentHashMap<String, ViolationCounter>()
    private val totalStrictModeViolations = AtomicInteger(0)
    private val studentRowRecompositions = AtomicLong(0L)
    private val avatarMemHits = AtomicInteger(0)
    private val avatarLazyLoads = AtomicInteger(0)

    private val recentLogsLock = Any()
    private val recentLogsBuffer = ArrayList<String>(MAX_LOG_ENTRIES)

    private val _metrics = MutableStateFlow(KamPerfSnapshot())
    val metrics: StateFlow<KamPerfSnapshot> = _metrics.asStateFlow()

    fun markActivityOnCreate() {
        val now = SystemClock.elapsedRealtimeNanos()
        if (activityCreateNanos == 0L) {
            activityCreateNanos = now
            hasRecordedFirstFrame = false
            hasRecordedFirstListRender = false
            currentInteractionContext = CTX_APP_START
            Log.i(TAG, "[START] MainActivity.onCreate initialized")
        }
    }

    fun markFirstUiFrameRendered() {
        if (hasRecordedFirstFrame) return
        hasRecordedFirstFrame = true
        val start = if (activityCreateNanos != 0L) activityCreateNanos else processStartNanos
        val elapsedMs = ((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L).coerceAtLeast(1L)
        val msg = "وقت فتح التطبيق (أول إطار): ${elapsedMs}ms (قبل التحسين: ${BEFORE_APP_START_MS}ms)"
        Log.i(TAG, "[APP_START] $msg")
        appendLogInternal(msg)
        _metrics.update { current ->
            current.copy(
                appColdStartMs = elapsedMs,
                recentTimingLogs = snapshotLogs()
            )
        }
    }

    fun markFirstStudentListRendered(studentCount: Int) {
        if (hasRecordedFirstListRender || studentCount <= 0) return
        hasRecordedFirstListRender = true
        val start = if (activityCreateNanos != 0L) activityCreateNanos else processStartNanos
        val elapsedMs = ((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L).coerceAtLeast(1L)
        currentInteractionContext = CTX_HOME_IDLE
        val msg = "وقت أول عرض لقائمة الأطفال ($studentCount طفل): ${elapsedMs}ms (قبل التحسين: ${BEFORE_FIRST_LIST_MS}ms)"
        Log.i(TAG, "[FIRST_LIST_RENDER] $msg")
        appendLogInternal(msg)
        _metrics.update { current ->
            current.copy(
                firstListRenderMs = elapsedMs,
                recentTimingLogs = snapshotLogs()
            )
        }
    }

    fun setInteractionContext(contextLabel: String, scrolling: Boolean = false) {
        currentInteractionContext = contextLabel
        val wasScrolling = isHomeScrollingActive
        isHomeScrollingActive = scrolling
        // When scrolling finishes, publish the accumulated scroll frame metrics once
        if (wasScrolling && !scrolling) {
            publishFrameMetricsToState()
        }
    }

    fun markCardTapStart(studentId: String, cardId: String) {
        currentInteractionContext = CTX_CARD_TAP
        pendingCardTapStartNanos = SystemClock.elapsedRealtimeNanos()
        pendingCardTapLabel = "$cardId ($studentId)"
    }

    fun markCardTapComplete(studentName: String = "", cardTitle: String = "") {
        val start = pendingCardTapStartNanos
        if (start == 0L) return
        pendingCardTapStartNanos = 0L
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        recordOptimisticCardTapLatency(studentName, cardTitle, elapsedMs)
    }

    fun recordOptimisticCardTapLatency(studentName: String = "", cardTitle: String = "", elapsedMs: Double) {
        val roundedMs = (elapsedMs * 10.0).toInt() / 10.0
        val label = if (cardTitle.isNotBlank()) "$cardTitle - $studentName" else pendingCardTapLabel
        val msg = String.format(
            Locale.US,
            "استجابة ضغطة الكارت (%s): %.1fms",
            label,
            roundedMs
        )
        Log.i(TAG, "[CARD_TAP] $msg")
        appendLogInternal(msg)
        _metrics.update { current ->
            val newCount = current.cardTapCount + 1
            val newAvg = ((current.avgCardTapMs * current.cardTapCount) + roundedMs) / newCount
            current.copy(
                lastCardTapMs = roundedMs,
                avgCardTapMs = (newAvg * 10.0).toInt() / 10.0,
                cardTapCount = newCount,
                recentTimingLogs = snapshotLogs()
            )
        }
    }

    fun resetAllMetrics() {
        resetFrameMetrics()
    }

    fun markLeaderboardOpenStart() {
        currentInteractionContext = CTX_LEADERBOARD
        pendingLeaderboardStartNanos = SystemClock.elapsedRealtimeNanos()
    }

    fun markLeaderboardOpenReady(studentCount: Int, fromCache: Boolean) {
        val start = pendingLeaderboardStartNanos
        val elapsedMs = if (start != 0L) {
            pendingLeaderboardStartNanos = 0L
            (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        } else {
            0.4
        }
        val roundedMs = (elapsedMs * 10.0).toInt() / 10.0
        val sourceLabel = if (fromCache) "من الكاش الجاهز O(1)" else "حساب خلفي"
        val msg = String.format(
            Locale.US,
            "فتح لوحة الشرف (%d طفل - %s): %.1fms",
            studentCount,
            sourceLabel,
            roundedMs
        )
        Log.i(TAG, "[LEADERBOARD_OPEN] $msg")
        appendLogInternal(msg)
        _metrics.update { current ->
            current.copy(
                lastLeaderboardOpenMs = roundedMs,
                leaderboardCacheHits = if (fromCache) current.leaderboardCacheHits + 1 else current.leaderboardCacheHits,
                recentTimingLogs = snapshotLogs()
            )
        }
    }

    fun recordSnapshotDiskWrite(elapsedMs: Double, sizeBytes: Int) {
        val sizeKb = ((sizeBytes / 1024.0) * 10.0).toInt() / 10.0
        val roundedMs = (elapsedMs * 10.0).toInt() / 10.0
        val msg = String.format(
            Locale.US,
            "حفظ Snapshot على Dispatchers.IO: %.1f KB في %.1fms",
            sizeKb,
            roundedMs
        )
        Log.i(TAG, "[DISK_SNAPSHOT] $msg")
        appendLogInternal(msg)
        _metrics.update { current ->
            current.copy(
                lastSnapshotWriteMs = roundedMs,
                lastSnapshotSizeKb = sizeKb,
                recentTimingLogs = snapshotLogs()
            )
        }
    }

    fun recordAvatarMemoryCacheHit() {
        avatarMemHits.incrementAndGet()
    }

    fun recordAvatarDiskOrLazyLoad(studentId: String, elapsedMs: Double) {
        val roundedMs = (elapsedMs * 10.0).toInt() / 10.0
        Log.d(TAG, "[AVATAR_LAZY_LOAD] Loaded avatar for $studentId on background thread in ${roundedMs}ms")
        avatarLazyLoads.incrementAndGet()
    }

    fun recordStudentRowRecomposition() {
        studentRowRecompositions.incrementAndGet()
    }

    /**
     * Records a single UI frame duration from `Window.OnFrameMetricsAvailableListener`.
     * Runs on `KamFrameMetricsThread` using lock-free atomic counters.
     * NEVER triggers immediate Compose state updates during scrolling or on every jank frame!
     */
    fun recordFrameDuration(durationNanos: Long) {
        if (durationNanos <= 0L) return
        val ctx = currentInteractionContext
        val durationMs = durationNanos / 1_000_000.0
        val isJank = durationMs > 16.6

        totalFrames.incrementAndGet()
        totalDurationNanosSum.addAndGet(durationNanos)

        var prevWorst = worstFrameNanos.get()
        while (durationNanos > prevWorst) {
            if (worstFrameNanos.compareAndSet(prevWorst, durationNanos)) {
                worstFrameContextLabel = ctx
                break
            }
            prevWorst = worstFrameNanos.get()
        }

        val bucket = contextBuckets.getOrPut(ctx) { ContextBucket(ctx) }
        bucket.record(durationNanos, isJank)

        if (isJank) {
            val jankNum = jankFrames.incrementAndGet()
            lastJankContextLabel = ctx
            val roundedMs = (durationMs * 10.0).toInt() / 10.0
            // Log severe jank (>32ms) or sampled jank to Logcat without flooding the ring buffer
            if (durationMs >= 28.0 || jankNum <= 15L || jankNum % 10L == 0L) {
                val msg = String.format(
                    Locale.US,
                    "إطار بطيء (Jank): %.1fms في [%s] (> 16.6ms)",
                    roundedMs,
                    ctx
                )
                Log.w(TAG, "[JANK_FRAME] $msg")
                appendLogInternal(msg)
            }
        }

        // Keep frame tracking purely lock-free atomic here so idle UI (especially SettingsScreen)
        // never recomposes periodically in the background. Published on scroll end or explicit refresh.
    }

    fun recordScrollBenchmarkResult(avgFrameMs: Double, worstFrameMs: Double, jankPct: Double, framesCount: Long) {
        val msg = String.format(
            Locale.US,
            "نتيجة اختبار تمرير الرئيسية (%d إطار): متوسط %.1fms • أسوأ %.1fms • Jank %.1f%%",
            framesCount,
            avgFrameMs,
            worstFrameMs,
            jankPct
        )
        Log.i(TAG, "[SCROLL_BENCHMARK] $msg")
        appendLogInternal(msg)
        _metrics.update { current ->
            current.copy(
                lastScrollBenchmarkAvgMs = avgFrameMs,
                lastScrollBenchmarkWorstMs = worstFrameMs,
                lastScrollBenchmarkJankPct = jankPct,
                lastScrollBenchmarkFrames = framesCount,
                recentTimingLogs = snapshotLogs()
            )
        }
        publishFrameMetricsToState()
    }

    /**
     * Records a StrictMode violation on the Main Thread, extracting the exact app source file,
     * line number, method name, and violation type (`DiskRead` / `DiskWrite` / `Network`).
     */
    fun recordStrictModeViolation(violation: Throwable) {
        val rawClassName = violation.javaClass.simpleName.ifBlank { "StrictModeViolation" }
        val typeLabel = when {
            rawClassName.contains("DiskRead", ignoreCase = true) -> "قرص (قراءة DiskRead)"
            rawClassName.contains("DiskWrite", ignoreCase = true) -> "قرص (كتابة DiskWrite)"
            rawClassName.contains("Network", ignoreCase = true) -> "شبكة (Network)"
            else -> rawClassName
        }

        val trace = violation.stackTrace
        val appFrame = trace.firstOrNull { element ->
            element.className.startsWith("com.example") &&
                !element.className.contains("MainActivity\$setupStrictMode") &&
                !element.className.contains("KamPerformanceTracker")
        }

        // Ignore purely internal Android OS / Google Play Services / Firestore C++ internal initialization
        // unless it was triggered directly by a synchronous call from our `com.example` code!
        if (appFrame == null) {
            return
        }

        val fileName = appFrame.fileName ?: appFrame.className.substringAfterLast('.')
        val fileAndLine = "$fileName:${appFrame.lineNumber}"
        val shortClass = appFrame.className.substringAfterLast('.')
        val methodLabel = "$shortClass.${appFrame.methodName}()"
        val sig = "$typeLabel|$fileAndLine|$methodLabel"

        val counter = strictModeViolations.getOrPut(sig) {
            ViolationCounter(
                signature = sig,
                type = typeLabel,
                fileAndLine = fileAndLine,
                methodName = methodLabel
            )
        }
        val newItemCount = counter.count.incrementAndGet()
        totalStrictModeViolations.incrementAndGet()

        val msg = "مخالفة StrictMode [$typeLabel] في $fileAndLine ($methodLabel) — تكرار: $newItemCount"
        Log.e(TAG, "[STRICT_MODE] $msg")
        appendLogInternal(msg)
        publishFrameMetricsToState()
    }

    fun publishFrameMetricsToState() {
        val total = totalFrames.get()
        val janks = jankFrames.get()
        val sumNs = totalDurationNanosSum.get()
        val worstNs = worstFrameNanos.get()

        val jankPct = if (total > 0L) ((janks.toDouble() / total) * 1000.0).toInt() / 10.0 else 0.0
        val avgMs = if (total > 0L) (((sumNs.toDouble() / total) / 1_000_000.0) * 10.0).toInt() / 10.0 else 0.0
        val worstMs = ((worstNs / 1_000_000.0) * 10.0).toInt() / 10.0

        val breakdowns = contextBuckets.values
            .mapNotNull { it.toBreakdown() }
            .sortedByDescending { it.totalFrames }

        val violationsList = strictModeViolations.values
            .map {
                StrictModeViolationEntry(
                    signature = it.signature,
                    type = it.type,
                    fileAndLine = it.fileAndLine,
                    methodName = it.methodName,
                    count = it.count.get()
                )
            }
            .sortedByDescending { it.count }

        val scrollBucket = contextBuckets[CTX_HOME_SCROLL]?.toBreakdown()

        _metrics.update { current ->
            current.copy(
                totalFramesTracked = total,
                jankFramesCount = janks,
                jankPercentage = jankPct,
                avgFrameDurationMs = avgMs,
                worstFrameDurationMs = worstMs,
                worstFrameContext = worstFrameContextLabel,
                lastJankContext = lastJankContextLabel,
                contextBreakdowns = breakdowns,
                lastScrollBenchmarkAvgMs = scrollBucket?.avgFrameMs ?: current.lastScrollBenchmarkAvgMs,
                lastScrollBenchmarkWorstMs = scrollBucket?.worstFrameMs ?: current.lastScrollBenchmarkWorstMs,
                lastScrollBenchmarkJankPct = scrollBucket?.jankPercentage ?: current.lastScrollBenchmarkJankPct,
                lastScrollBenchmarkFrames = scrollBucket?.totalFrames ?: current.lastScrollBenchmarkFrames,
                strictModeEnabled = isStrictModeActive,
                strictModeViolationsCount = totalStrictModeViolations.get(),
                strictModeViolationDetails = violationsList,
                studentRowRecompositionCount = studentRowRecompositions.get(),
                avatarMemoryCacheHits = avatarMemHits.get(),
                avatarDiskOrLazyLoads = avatarLazyLoads.get(),
                recentTimingLogs = snapshotLogs()
            )
        }
    }

    fun resetFrameMetrics() {
        totalFrames.set(0L)
        jankFrames.set(0L)
        totalDurationNanosSum.set(0L)
        worstFrameNanos.set(0L)
        worstFrameContextLabel = ""
        lastJankContextLabel = ""
        contextBuckets.values.forEach { it.reset() }
        strictModeViolations.clear()
        totalStrictModeViolations.set(0)
        studentRowRecompositions.set(0L)
        publishFrameMetricsToState()
        Log.i(TAG, "[FRAME_METRICS] Reset frame metrics and StrictMode counters")
    }

    private fun appendLogInternal(newEntry: String) {
        synchronized(recentLogsLock) {
            recentLogsBuffer.add(0, newEntry)
            while (recentLogsBuffer.size > MAX_LOG_ENTRIES) {
                recentLogsBuffer.removeAt(recentLogsBuffer.lastIndex)
            }
        }
    }

    private fun snapshotLogs(): List<String> {
        synchronized(recentLogsLock) {
            return ArrayList(recentLogsBuffer)
        }
    }
}
