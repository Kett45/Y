package com.example.ui.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cache.CachedClassroomSnapshot
import com.example.data.cache.LocalClassroomCache
import com.example.data.cache.LocalUserPreferences
import com.example.data.model.ArabicInitialsHelper
import com.example.data.model.CardEvent
import com.example.data.model.CardType
import com.example.data.model.DuplicateCardAlert
import com.example.data.model.DuplicateStudentNameGroup
import com.example.data.model.Group
import com.example.data.model.Invitation
import com.example.data.model.MAX_STUDENTS_PER_GROUP
import com.example.data.model.MonthlyBalance
import com.example.data.model.SaturdayCalendarHelper
import com.example.data.model.ServantBlock
import com.example.data.model.Student
import com.example.data.model.UserProfile
import com.example.data.repository.KamRepository
import com.example.ui.components.StudentAvatarImageHelper
import com.example.util.KamPerformanceTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class BulkImportLinePreview(
    val rawName: String,
    val normalizedName: String,
    val duplicateInPaste: Boolean,
    val existingSimilarStudentName: String?
)

@Immutable
data class StudentCardTypeBreakdown(
    val cardType: CardType,
    val count: Int,
    val points: Long
)

@Immutable
data class StudentDetailedLeaderboardEntry(
    val student: Student,
    val rank: Int,
    val totalPoints: Long,
    val totalCardsCount: Int,
    val attendanceCount: Int,
    val attendancePoints: Long,
    val breakdownByCard: List<StudentCardTypeBreakdown>
)

@Immutable
class PrecomputedClassroomIndices(
    val userProfile: UserProfile?,
    val students: List<Student>,
    val cardEvents: List<CardEvent>,
    val monthlyBalances: List<MonthlyBalance>,
    val selectedWeekKey: String?,
    val dismissedDuplicateNames: Set<String>
) {
    val enabledCardTypes: List<CardType> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val ids = userProfile?.enabledCards ?: CardType.defaultEnabledIds
        CardType.entries.filter { it.id in ids }
    }

    val collapsedBlockIds: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        userProfile?.collapsedBlockIds?.toSet() ?: emptySet()
    }

    val hiddenBlockIds: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        userProfile?.hiddenBlockIds?.toSet() ?: emptySet()
    }

    val displayedCardEvents: List<CardEvent> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (selectedWeekKey.isNullOrBlank()) {
            cardEvents
        } else {
            cardEvents.filter { it.weekKey == selectedWeekKey }
        }
    }

    private val monthlyEventsByStudentId: Map<String, List<CardEvent>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        cardEvents.groupBy { it.studentId }
    }

    private val monthlyBalanceByStudentId: Map<String, Long> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        monthlyBalances.associate { it.studentId to it.totalPoints }
    }

    val monthlyPointsByStudentId: Map<String, Long> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildMap(students.size + monthlyEventsByStudentId.size + monthlyBalanceByStudentId.size) {
            monthlyBalanceByStudentId.forEach { (stuId, pts) ->
                put(stuId, pts)
            }
            monthlyEventsByStudentId.forEach { (stuId, stuEvents) ->
                put(stuId, stuEvents.sumOf { it.points })
            }
        }
    }

    val displayedEventsByStudentId: Map<String, List<CardEvent>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        displayedCardEvents.groupBy { it.studentId }
    }

    val displayedPointsByStudentId: Map<String, Long> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (!selectedWeekKey.isNullOrBlank()) {
            displayedEventsByStudentId.mapValues { (_, evs) -> evs.sumOf { it.points } }
        } else {
            monthlyPointsByStudentId
        }
    }

    val displayedCardCountsByStudentAndType: Map<Pair<String, String>, Int> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val counts = HashMap<Pair<String, String>, Int>(displayedCardEvents.size.coerceAtLeast(16))
        displayedCardEvents.forEach { ev ->
            val key = ev.studentId to ev.cardType
            counts[key] = (counts[key] ?: 0) + 1
        }
        counts
    }

    val packedCardCountsByStudentId: Map<String, Long> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (displayedCardEvents.isEmpty()) {
            emptyMap()
        } else {
            val packedMap = HashMap<String, Long>(displayedEventsByStudentId.size.coerceAtLeast(16))
            displayedEventsByStudentId.forEach { (stuId, evs) ->
                var packed = 0L
                for (i in 0 until evs.size) {
                    val ct = CardType.fromId(evs[i].cardType) ?: continue
                    val shift = ct.ordinal * 8
                    val current = ((packed ushr shift) and 0xFFL).toInt()
                    if (current < 255) {
                        val cleared = packed and (0xFFL shl shift).inv()
                        packed = cleared or ((current + 1).toLong() shl shift)
                    }
                }
                if (packed != 0L) {
                    packedMap[stuId] = packed
                }
            }
            packedMap
        }
    }

    val duplicateStudentsByName: List<DuplicateStudentNameGroup> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (students.size < 2) {
            emptyList()
        } else {
            students
                .groupBy { ArabicInitialsHelper.normalizeArabicName(it.name) }
                .filter { (normName, list) ->
                    normName.isNotBlank() &&
                        list.size >= 2 &&
                        normName !in dismissedDuplicateNames
                }
                .map { (normName, list) ->
                    DuplicateStudentNameGroup(
                        normalizedName = normName,
                        displayName = list.first().name,
                        records = list.sortedBy { it.studentNumber }
                    )
                }
        }
    }

    val duplicateAlerts: List<DuplicateCardAlert> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (displayedCardEvents.size < 2) {
            emptyList()
        } else {
            val studentNameById = students.associate { it.id to it.name }
            displayedCardEvents
                .groupBy { Triple(it.studentId, it.cardType, it.weekKey) }
                .mapNotNull { (key, eventsInGroup) ->
                    if (eventsInGroup.size < 2) return@mapNotNull null
                    val distinctServantEvents = eventsInGroup
                        .sortedBy { it.createdAt?.seconds ?: 0L }
                        .distinctBy { it.servantId }
                    if (distinctServantEvents.size >= 2) {
                        val (studentId, cardTypeId, weekKey) = key
                        val cardType = CardType.fromId(cardTypeId) ?: return@mapNotNull null
                        val studentName = studentNameById[studentId]
                            ?: eventsInGroup.firstOrNull()?.studentName?.ifBlank { "طفل" }
                            ?: "طفل"
                        val servantNames = distinctServantEvents.map {
                            it.servantName.trim().ifBlank { "خادم" }
                        }
                        DuplicateCardAlert(
                            id = "dup_${studentId}_${cardTypeId}_$weekKey",
                            studentId = studentId,
                            studentName = studentName,
                            cardType = cardType,
                            weekKey = weekKey,
                            servantNames = servantNames,
                            events = eventsInGroup.sortedByDescending { it.createdAt?.seconds ?: 0L }
                        )
                    } else {
                        null
                    }
                }
        }
    }

    val leaderboardCache: ConcurrentHashMap<String, List<StudentDetailedLeaderboardEntry>> =
        ConcurrentHashMap()

    fun matchesState(
        profile: UserProfile?,
        stuList: List<Student>,
        evList: List<CardEvent>,
        balList: List<MonthlyBalance>,
        weekKey: String?,
        dismissed: Set<String>
    ): Boolean {
        return userProfile === profile &&
            students === stuList &&
            cardEvents === evList &&
            monthlyBalances === balList &&
            selectedWeekKey == weekKey &&
            dismissedDuplicateNames === dismissed
    }

    fun prewarmAll() {
        enabledCardTypes
        collapsedBlockIds
        hiddenBlockIds
        displayedCardEvents
        monthlyPointsByStudentId
        displayedPointsByStudentId
        displayedCardCountsByStudentAndType
        packedCardCountsByStudentId
        duplicateStudentsByName
        duplicateAlerts
    }
}

@Immutable
data class KamScreenState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val actionBannerMessage: String? = null,
    val userProfile: UserProfile? = null,
    val group: Group? = null,
    val userGroups: List<Group> = emptyList(),
    val isAdmin: Boolean = false,
    val blocks: List<ServantBlock> = emptyList(),
    val students: List<Student> = emptyList(),
    val cardEvents: List<CardEvent> = emptyList(),
    val monthlyBalances: List<MonthlyBalance> = emptyList(),
    val allGroupCardEvents: List<CardEvent> = emptyList(),
    val allGroupMonthlyBalances: List<MonthlyBalance> = emptyList(),
    val invitations: List<Invitation> = emptyList(),
    val selectedMonthKey: String = "",
    val selectedWeekKey: String? = null,
    val currentWeekKey: String = "",
    val searchQuery: String = "",
    val isOnline: Boolean = true,
    val hasPendingWrites: Boolean = false,
    val dismissedDuplicateNames: Set<String> = emptySet(),
    val previousGroupId: String = "",
    val precomputedIndices: PrecomputedClassroomIndices? = null
) {
    private val indices: PrecomputedClassroomIndices by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val existing = precomputedIndices
        if (existing != null && existing.matchesState(
                profile = userProfile,
                stuList = students,
                evList = cardEvents,
                balList = monthlyBalances,
                weekKey = selectedWeekKey,
                dismissed = dismissedDuplicateNames
            )
        ) {
            existing
        } else {
            PrecomputedClassroomIndices(
                userProfile = userProfile,
                students = students,
                cardEvents = cardEvents,
                monthlyBalances = monthlyBalances,
                selectedWeekKey = selectedWeekKey,
                dismissedDuplicateNames = dismissedDuplicateNames
            )
        }
    }

    val remainingStudentCapacity: Int
        get() = (MAX_STUDENTS_PER_GROUP - students.size).coerceAtLeast(0)

    val canReturnToPreviousGroup: Boolean
        get() = previousGroupId.isNotBlank() ||
            !userProfile?.joinedGroupIds.isNullOrEmpty() ||
            userGroups.isNotEmpty()

    val enabledCardTypes: List<CardType>
        get() = indices.enabledCardTypes

    val collapsedBlockIds: Set<String>
        get() = indices.collapsedBlockIds

    val hiddenBlockIds: Set<String>
        get() = indices.hiddenBlockIds

    val displayedCardEvents: List<CardEvent>
        get() = indices.displayedCardEvents

    /**
     * Detects duplicate student records with the same normalized Arabic name inside the active group
     * (from migration or simultaneous offline additions) so the user can compare and decide manually.
     */
    val duplicateStudentsByName: List<DuplicateStudentNameGroup>
        get() = indices.duplicateStudentsByName

    /**
     * Detects duplicate cards where the SAME child received the SAME card type in the SAME week
     * from 2 or more DIFFERENT servants.
     */
    val duplicateAlerts: List<DuplicateCardAlert>
        get() = indices.duplicateAlerts

    fun pointsForStudentInSelectedMonth(studentId: String): Long {
        return indices.monthlyPointsByStudentId[studentId] ?: 0L
    }

    fun pointsForStudentInDisplayedScope(studentId: String): Long {
        return indices.displayedPointsByStudentId[studentId] ?: 0L
    }

    fun cardCountForStudent(studentId: String, cardType: CardType): Int {
        return indices.displayedCardCountsByStudentAndType[studentId to cardType.id] ?: 0
    }

    /**
     * Returns all 7 [CardType] counts for [studentId] packed into a single 64-bit primitive `Long`
     * (8 bits per [CardType.ordinal]). Enables O(1) Compose recomposition skipping per student row.
     */
    fun packedCardCountsForStudent(studentId: String): Long {
        return indices.packedCardCountsByStudentId[studentId] ?: 0L
    }

    fun eventsForStudent(studentId: String): List<CardEvent> {
        return indices.displayedEventsByStudentId[studentId] ?: emptyList()
    }

    companion object {
        fun unpackCardCount(packedCounts: Long, cardType: CardType): Int {
            if (packedCounts == 0L) return 0
            return ((packedCounts ushr (cardType.ordinal * 8)) and 0xFFL).toInt()
        }
    }

    val effectiveAllCardEvents: List<CardEvent>
        get() = if (allGroupCardEvents.isNotEmpty()) allGroupCardEvents else cardEvents

    val effectiveAllMonthlyBalances: List<MonthlyBalance>
        get() = if (allGroupMonthlyBalances.isNotEmpty()) allGroupMonthlyBalances else monthlyBalances

    /**
     * Computes the full-class leaderboard and per-card breakdown for ALL students in the classroom
     * across either:
     * - A single week ([selectedWeekOverride] != null), OR
     * - One or more selected months ([selectedMonths], defaulting to [selectedMonthKey]).
     * Includes every student (even those with 0 points or 0 attendance) sorted from highest to lowest points.
     */
    /**
     * Returns `true` if the leaderboard for [selectedMonths] and [selectedWeekOverride] is already
     * precomputed and cached in memory for the current classroom data snapshot.
     */
    fun isLeaderboardCached(
        selectedMonths: Set<String> = setOf(selectedMonthKey),
        selectedWeekOverride: String? = null
    ): Boolean {
        val targetMonths = selectedMonths.filter { it.isNotBlank() }.toSortedSet().ifEmpty {
            if (selectedMonthKey.isNotBlank()) sortedSetOf(selectedMonthKey) else sortedSetOf()
        }
        val cacheKey = "${targetMonths.joinToString(",")}|${selectedWeekOverride.orEmpty()}|${enabledCardTypes.size}|${effectiveAllCardEvents.size}|${effectiveAllMonthlyBalances.size}|${students.size}"
        return indices.leaderboardCache.containsKey(cacheKey)
    }

    /**
     * Computes the full-class leaderboard and per-card breakdown for ALL students in the classroom
     * across either:
     * - A single week ([selectedWeekOverride] != null), OR
     * - One or more selected months ([selectedMonths], defaulting to [selectedMonthKey]).
     * Memoizes the result in [PrecomputedClassroomIndices.leaderboardCache] so repeated opens or
     * recompositions without actual data changes return in 0ms without recalculating.
     */
    fun computeClassLeaderboardEntries(
        selectedMonths: Set<String> = setOf(selectedMonthKey),
        selectedWeekOverride: String? = null
    ): List<StudentDetailedLeaderboardEntry> {
        if (students.isEmpty()) return emptyList()
        val allEvents = effectiveAllCardEvents
        val allBalances = effectiveAllMonthlyBalances
        val activeCards = enabledCardTypes.ifEmpty { CardType.entries }
        val targetMonths = selectedMonths.filter { it.isNotBlank() }.toSortedSet().ifEmpty {
            if (selectedMonthKey.isNotBlank()) sortedSetOf(selectedMonthKey) else sortedSetOf()
        }
        val cacheKey = "${targetMonths.joinToString(",")}|${selectedWeekOverride.orEmpty()}|${activeCards.size}|${allEvents.size}|${allBalances.size}|${students.size}"
        indices.leaderboardCache[cacheKey]?.let { cached ->
            return cached
        }

        val scopedEvents = if (!selectedWeekOverride.isNullOrBlank()) {
            allEvents.filter { it.weekKey == selectedWeekOverride }
        } else if (targetMonths.isNotEmpty()) {
            allEvents.filter { it.monthKey in targetMonths }
        } else {
            allEvents
        }

        // Pre-index scoped events by studentId and balances by (studentId, monthKey) for O(1) lookup per student
        val scopedEventsByStudentId = scopedEvents.groupBy { it.studentId }
        val balanceByStudentAndMonth = HashMap<Pair<String, String>, Long>(allBalances.size.coerceAtLeast(16))
        allBalances.forEach { bal ->
            balanceByStudentAndMonth[bal.studentId to bal.monthKey] = bal.totalPoints
        }

        val unsorted = students.map { student ->
            val stuEvents = scopedEventsByStudentId[student.id].orEmpty()
            val eventsByType = if (stuEvents.isEmpty()) emptyMap() else stuEvents.groupBy { it.cardType }

            val breakdown = activeCards.map { cardType ->
                val matching = eventsByType[cardType.id].orEmpty()
                StudentCardTypeBreakdown(
                    cardType = cardType,
                    count = matching.size,
                    points = matching.sumOf { it.points }
                )
            }

            val totalPoints = if (!selectedWeekOverride.isNullOrBlank()) {
                stuEvents.sumOf { it.points }
            } else {
                val eventsByMonth = if (stuEvents.isEmpty()) emptyMap() else stuEvents.groupBy { it.monthKey }
                targetMonths.sumOf { mKey ->
                    val monthEvents = eventsByMonth[mKey].orEmpty()
                    if (monthEvents.isNotEmpty()) {
                        monthEvents.sumOf { it.points }
                    } else {
                        balanceByStudentAndMonth[student.id to mKey] ?: 0L
                    }
                }
            }

            val attendanceEvents = eventsByType[CardType.ATTENDANCE.id].orEmpty()
            StudentDetailedLeaderboardEntry(
                student = student,
                rank = 0,
                totalPoints = totalPoints,
                totalCardsCount = stuEvents.size,
                attendanceCount = attendanceEvents.size,
                attendancePoints = attendanceEvents.sumOf { it.points },
                breakdownByCard = breakdown
            )
        }

        val computed = unsorted
            .sortedWith(
                compareByDescending<StudentDetailedLeaderboardEntry> { it.totalPoints }
                    .thenByDescending { it.attendancePoints }
                    .thenByDescending { it.totalCardsCount }
                    .thenBy { it.student.studentNumber }
            )
            .mapIndexed { idx, entry ->
                entry.copy(rank = idx + 1)
            }
        indices.leaderboardCache[cacheKey] = computed
        return computed
    }

    /**
     * Returns `true` if the leaderboard for the custom date range `[startMillis, endMillis]` is already
     * precomputed and cached in memory for the current classroom data snapshot.
     */
    fun isLeaderboardDateRangeCached(
        startMillis: Long,
        endMillis: Long,
        extraHistoricalEventsCount: Int = 0
    ): Boolean {
        val startBound = SaturdayCalendarHelper.normalizeStartOfDayMillis(startMillis)
        val endBound = SaturdayCalendarHelper.normalizeEndOfDayMillis(endMillis)
        val activeCards = enabledCardTypes.ifEmpty { CardType.entries }
        val cacheKey = "RANGE|${startBound / 86_400_000L}|${endBound / 86_400_000L}|${activeCards.size}|${effectiveAllCardEvents.size}|$extraHistoricalEventsCount|${students.size}"
        return indices.leaderboardCache.containsKey(cacheKey)
    }

    /**
     * Computes the full-class leaderboard and per-card breakdown for ALL students in the classroom
     * across an arbitrary custom date range `[startMillis, endMillis]` strictly from `cardEvents`
     * (never from `monthlyBalances`).
     * - Uses `event.effectiveTimestampMillis()` which reads `createdAt` and falls back to `weekKey`/`monthKey`
     *   if an older event lacks a valid `createdAt`.
     * - Memoizes the sorted result in [PrecomputedClassroomIndices.leaderboardCache] so repeated opens
     *   or filter changes return in 0ms without recalculating.
     */
    fun computeClassLeaderboardEntriesForDateRange(
        startMillis: Long,
        endMillis: Long,
        extraHistoricalEvents: List<CardEvent> = emptyList()
    ): List<StudentDetailedLeaderboardEntry> {
        if (students.isEmpty()) return emptyList()
        val startBound = SaturdayCalendarHelper.normalizeStartOfDayMillis(startMillis)
        val endBound = SaturdayCalendarHelper.normalizeEndOfDayMillis(endMillis)
        val allEvents = effectiveAllCardEvents
        val activeCards = enabledCardTypes.ifEmpty { CardType.entries }
        val cacheKey = "RANGE|${startBound / 86_400_000L}|${endBound / 86_400_000L}|${activeCards.size}|${allEvents.size}|${extraHistoricalEvents.size}|${students.size}"
        indices.leaderboardCache[cacheKey]?.let { cached ->
            return cached
        }

        // Merge in-memory events with any fetched historical range events (deduplicated by id)
        val combinedEvents = if (extraHistoricalEvents.isEmpty()) {
            allEvents
        } else {
            val byId = LinkedHashMap<String, CardEvent>(allEvents.size + extraHistoricalEvents.size)
            for (i in 0 until allEvents.size) {
                val ev = allEvents[i]
                val key = ev.id.ifBlank { "${ev.studentId}_${ev.cardType}_${ev.weekKey}_${ev.servantId}" }
                byId[key] = ev
            }
            for (i in 0 until extraHistoricalEvents.size) {
                val ev = extraHistoricalEvents[i]
                val key = ev.id.ifBlank { "${ev.studentId}_${ev.cardType}_${ev.weekKey}_${ev.servantId}" }
                if (!byId.containsKey(key)) {
                    byId[key] = ev
                }
            }
            byId.values.toList()
        }

        val scopedEvents = ArrayList<CardEvent>(combinedEvents.size)
        for (i in 0 until combinedEvents.size) {
            val ev = combinedEvents[i]
            val ts = ev.effectiveTimestampMillis()
            if (ts in startBound..endBound) {
                scopedEvents.add(ev)
            }
        }

        val scopedEventsByStudentId = scopedEvents.groupBy { it.studentId }

        val unsorted = students.map { student ->
            val stuEvents = scopedEventsByStudentId[student.id].orEmpty()
            val eventsByType = if (stuEvents.isEmpty()) emptyMap() else stuEvents.groupBy { it.cardType }

            val breakdown = activeCards.map { cardType ->
                val matching = eventsByType[cardType.id].orEmpty()
                StudentCardTypeBreakdown(
                    cardType = cardType,
                    count = matching.size,
                    points = matching.sumOf { it.points }
                )
            }

            // Strictly sum points from cardEvents in the date range (never monthlyBalances)
            val totalPoints = stuEvents.sumOf { it.points }
            val attendanceEvents = eventsByType[CardType.ATTENDANCE.id].orEmpty()

            StudentDetailedLeaderboardEntry(
                student = student,
                rank = 0,
                totalPoints = totalPoints,
                totalCardsCount = stuEvents.size,
                attendanceCount = attendanceEvents.size,
                attendancePoints = attendanceEvents.sumOf { it.points },
                breakdownByCard = breakdown
            )
        }

        val computed = unsorted
            .sortedWith(
                compareByDescending<StudentDetailedLeaderboardEntry> { it.totalPoints }
                    .thenByDescending { it.attendancePoints }
                    .thenByDescending { it.totalCardsCount }
                    .thenBy { it.student.studentNumber }
            )
            .mapIndexed { idx, entry ->
                entry.copy(rank = idx + 1)
            }
        indices.leaderboardCache[cacheKey] = computed
        return computed
    }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class KamViewModel(
    private val repository: KamRepository,
    private val currentUserId: String,
    private val initialDisplayName: String,
    private val initialEmail: String,
    val isOnlineFlow: Flow<Boolean> = flowOf(true)
) : ViewModel() {

    private val initialCachedSnapshot: CachedClassroomSnapshot? =
        LocalClassroomCache.getCachedSnapshot(currentUserId)

    private val _localPrefsOverride = MutableStateFlow<LocalUserPreferences?>(
        LocalClassroomCache.loadUserPreferences(currentUserId).takeIf { it.updatedAtMillis > 0L }
    )

    private val _selectedMonthKey = MutableStateFlow(currentYearMonth())
    private val _selectedWeekKey = MutableStateFlow<String?>(currentYearWeek())
    private val _searchQuery = MutableStateFlow("")
    private val _actionBanner = MutableStateFlow<String?>(null)
    private val _operationError = MutableStateFlow<String?>(null)
    private val _isBusy = MutableStateFlow(false)
    private val _dismissedDuplicateNames = MutableStateFlow<Set<String>>(emptySet())
    private val _previousGroupId = MutableStateFlow("")

    // Instant 0ms optimistic UI overlays applied before Firestore local cache round-trips
    private val _optimisticAddedEvents = MutableStateFlow<Map<String, CardEvent>>(emptyMap())
    private val _optimisticRemovedEventIds = MutableStateFlow<Set<String>>(emptySet())
    private val _optimisticStudentOverrides = MutableStateFlow<Map<String, Student>>(emptyMap())

    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    // In-memory debounce map to prevent accidental rapid double-taps (< 350ms) on the same student + card
    private val recentTapTimestamps = mutableMapOf<String, Long>()
    private var lastMigratedGroupKey: String = ""
    private var lastMigratedInlineAvatarsGroupKey: String = ""
    private var lastSavedCacheSignature: String = ""
    private var lastAutoRotatedGroupKey: String = ""
    private var lastEjectedBannedGroupId: String = ""
    private var hasSeededPrefsFromCloud: Boolean = false
    private var bannerJob: Job? = null

    /**
     * Emits the first value immediately (0ms delay for cold start cache) and debounces
     * subsequent rapid cloud listener bursts by [timeoutMs] (200ms) so consecutive Firestore
     * updates are coalesced into a single UI state pass.
     */
    private fun <T> Flow<T>.debounceSubsequentCloudEmissions(timeoutMs: Long = 200L): Flow<T> {
        var isFirstEmission = true
        return this.debounce {
            if (isFirstEmission) {
                isFirstEmission = false
                0L
            } else {
                timeoutMs
            }
        }
    }

    init {
        StudentAvatarImageHelper.cloudAvatarFetcher = { groupId, studentId ->
            repository.fetchStudentAvatar(groupId, studentId)
        }
        historicalRangeFetcher = { groupId, startMillis, endMillis ->
            repository.fetchCardEventsForDateRange(groupId, startMillis, endMillis).getOrDefault(emptyList())
        }
        ensureProfileInitialized()
    }

    fun ensureProfileInitialized() {
        viewModelScope.launch {
            Log.d(TAG, "[Step 4] Ensuring user profile in Firestore for uid=$currentUserId ($initialEmail)...")
            repository.ensureUserProfile(
                displayName = initialDisplayName,
                email = initialEmail
            ).onSuccess { profile ->
                Log.d(TAG, "[Step 4] User profile ready: uid=${profile.userId}, groupId='${profile.groupId}'")
            }.onFailure { err ->
                Log.w(TAG, "[Step 4] Profile initialization failed: ${err.javaClass.simpleName} - ${err.message}", err)
                if (initialCachedSnapshot == null) {
                    _operationError.value = err.localizedMessage ?: "تعذر تهيئة حساب الخادم"
                }
            }
        }
    }

    private val userProfileFlow: Flow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .map { profile ->
            Log.d(TAG, "[Step 4] observeUserProfile emitted: exists=${profile != null}, groupId='${profile?.groupId.orEmpty()}'")
            if (profile != null && !hasSeededPrefsFromCloud) {
                hasSeededPrefsFromCloud = true
                val existingLocal = _localPrefsOverride.value
                val mergedPrefs = LocalUserPreferences(
                    enabledCards = existingLocal?.enabledCards ?: profile.enabledCards.ifEmpty { CardType.defaultEnabledIds },
                    collapsedBlockIds = existingLocal?.collapsedBlockIds ?: profile.collapsedBlockIds,
                    hiddenBlockIds = existingLocal?.hiddenBlockIds ?: profile.hiddenBlockIds,
                    themeId = profile.themeId.ifBlank { existingLocal?.themeId ?: "DARK" },
                    uiScaleMode = profile.uiScaleMode.ifBlank { existingLocal?.uiScaleMode ?: "AUTO" },
                    uiScaleFactor = profile.uiScaleFactor.toFloat().takeIf { it in 0.70f..1.30f }
                        ?: existingLocal?.uiScaleFactor ?: 0.85f,
                    showFloatingAddButton = profile.showFloatingAddButton,
                    activeGroupId = profile.groupId
                )
                _localPrefsOverride.value = mergedPrefs
                LocalClassroomCache.saveUserPreferences(currentUserId, mergedPrefs)
            }
            profile
        }
        .onStart {
            initialCachedSnapshot?.userProfile?.let { emit(it) }
        }
        .catch { err ->
            Log.w(TAG, "[Step 4] Error observing userProfile: ${err.javaClass.simpleName} - ${err.message}", err)
            if (initialCachedSnapshot == null) {
                _operationError.value = err.localizedMessage
            }
            emit(initialCachedSnapshot?.userProfile)
        }

    private val userGroupsFlow: Flow<List<Group>> = repository.observeUserGroups(currentUserId)
        .onStart { emit(initialCachedSnapshot?.userGroups.orEmpty()) }
        .catch { err ->
            Log.w(TAG, "Error observing userGroups", err)
            emit(initialCachedSnapshot?.userGroups.orEmpty())
        }

    private val activeGroupIdFlow: Flow<String> = userProfileFlow
        .map { it?.groupId?.trim().orEmpty() }
        .distinctUntilChanged()

    private val groupFlow: Flow<Group?> = activeGroupIdFlow.flatMapLatest { groupId ->
        if (groupId.isBlank()) {
            Log.d(TAG, "[Step 4] User has no active groupId yet (routing to GroupOnboardingScreen)")
            flowOf(null)
        } else {
            Log.d(TAG, "[Step 4] Observing group document for groupId=$groupId")
            val cachedGroup = initialCachedSnapshot?.group?.takeIf { it.inviteCode == groupId }
            repository.observeGroup(groupId)
                .onStart { if (cachedGroup != null) emit(cachedGroup) }
                .catch { err ->
                    Log.w(TAG, "[Step 4] Error observing group ($groupId): ${err.javaClass.simpleName} - ${err.message}", err)
                    emit(cachedGroup)
                }
        }
    }

    private val blocksFlow: Flow<List<ServantBlock>> = activeGroupIdFlow.flatMapLatest { groupId ->
        if (groupId.isBlank()) {
            flowOf(emptyList())
        } else {
            val cachedBlocks = initialCachedSnapshot?.blocks.orEmpty().filter { it.groupId == groupId || it.groupId.isBlank() }
            repository.observeBlocks(groupId, currentUserId)
                .onStart { emit(cachedBlocks) }
                .debounceSubsequentCloudEmissions(200L)
                .catch { err ->
                    Log.w(TAG, "Error observing blocks", err)
                    emit(cachedBlocks)
                }
        }
    }

    private val studentsFlow: Flow<List<Student>> = activeGroupIdFlow.flatMapLatest { groupId ->
        if (groupId.isBlank()) {
            flowOf(emptyList())
        } else {
            val cachedStudents = initialCachedSnapshot?.students.orEmpty().filter { it.groupId == groupId || it.groupId.isBlank() }
            repository.observeStudents(groupId, currentUserId)
                .onStart { emit(cachedStudents) }
                .debounceSubsequentCloudEmissions(200L)
                .catch { err ->
                    Log.w(TAG, "Error observing students", err)
                    emit(cachedStudents)
                }
        }
    }

    private val invitationsFlow: Flow<List<Invitation>> = activeGroupIdFlow.flatMapLatest { groupId ->
        if (groupId.isBlank()) {
            flowOf(emptyList())
        } else {
            repository.observeInvitations(groupId, currentUserId)
                .onStart { emit(emptyList()) }
                .debounceSubsequentCloudEmissions(250L)
                .catch { err ->
                    Log.w(TAG, "Error observing invitations", err)
                    emit(emptyList())
                }
        }
    }

    private val allCardEventsFlow: Flow<List<CardEvent>> = activeGroupIdFlow.flatMapLatest { groupId ->
        if (groupId.isBlank()) {
            flowOf(emptyList())
        } else {
            val cachedEvents = initialCachedSnapshot?.cardEvents.orEmpty().filter { it.groupId == groupId || it.groupId.isBlank() }
            repository.observeAllCardEventsForGroup(groupId, currentUserId)
                .onStart { emit(cachedEvents) }
                .debounceSubsequentCloudEmissions(200L)
                .catch { err ->
                    Log.w(TAG, "Error observing allCardEvents", err)
                    emit(cachedEvents)
                }
        }
    }

    private val hasPendingWritesFlow: Flow<Boolean> = activeGroupIdFlow.flatMapLatest { groupId ->
        if (groupId.isBlank()) {
            flowOf(false)
        } else {
            repository.observeHasPendingWrites(groupId, currentUserId)
                .onStart { emit(false) }
        }
    }

    private val allMonthlyBalancesFlow: Flow<List<MonthlyBalance>> = activeGroupIdFlow.flatMapLatest { groupId ->
        if (groupId.isBlank()) {
            flowOf(emptyList())
        } else {
            val cachedBalances = initialCachedSnapshot?.monthlyBalances.orEmpty().filter { it.groupId == groupId || it.groupId.isBlank() }
            repository.observeAllMonthlyBalancesForGroup(groupId, currentUserId)
                .onStart { emit(cachedBalances) }
                .debounceSubsequentCloudEmissions(200L)
                .catch { err ->
                    Log.w(TAG, "Error observing allMonthlyBalances", err)
                    emit(cachedBalances)
                }
        }
    }

    /**
     * Heavy classroom data processing flow: runs on [Dispatchers.Default] ONLY when actual
     * classroom data, selected month/week, or optimistic overlays change.
     * Pre-warms all [PrecomputedClassroomIndices] on background CPU threads so transient UI updates
     * (search query, banners, pending-writes indicator, online status) never recompute student/card indices!
     */
    private val coreClassroomStateFlow: Flow<KamScreenState> = combine(
        combine(userProfileFlow, groupFlow, userGroupsFlow, blocksFlow, _localPrefsOverride) { profile, group, userGroups, blocks, localPrefs ->
            arrayOf<Any?>(profile, group, userGroups, blocks, localPrefs)
        },
        combine(studentsFlow, allCardEventsFlow, allMonthlyBalancesFlow, invitationsFlow) { students, allEvents, allBalances, invites ->
            arrayOf<Any?>(students, allEvents, allBalances, invites)
        },
        combine(_selectedMonthKey, _selectedWeekKey, _dismissedDuplicateNames) { month, week, dismissed ->
            arrayOf<Any?>(month, week, dismissed)
        },
        combine(_optimisticAddedEvents, _optimisticRemovedEventIds, _optimisticStudentOverrides) { optAdd, optRem, optStu ->
            arrayOf<Any?>(optAdd, optRem, optStu)
        }
    ) { groupArr, dataArr, filterArr, optArr ->
        val rawProfile = groupArr[0] as UserProfile?
        val group = groupArr[1] as Group?
        @Suppress("UNCHECKED_CAST")
        val userGroups = groupArr[2] as List<Group>
        @Suppress("UNCHECKED_CAST")
        val blocks = groupArr[3] as List<ServantBlock>
        val localPrefs = groupArr[4] as LocalUserPreferences?

        val profile = rawProfile?.let { base ->
            if (localPrefs != null) {
                base.copy(
                    enabledCards = localPrefs.enabledCards,
                    collapsedBlockIds = localPrefs.collapsedBlockIds,
                    hiddenBlockIds = localPrefs.hiddenBlockIds,
                    themeId = localPrefs.themeId,
                    uiScaleMode = localPrefs.uiScaleMode,
                    uiScaleFactor = localPrefs.uiScaleFactor.toDouble(),
                    showFloatingAddButton = localPrefs.showFloatingAddButton
                )
            } else {
                base
            }
        }

        @Suppress("UNCHECKED_CAST")
        val cloudStudents = dataArr[0] as List<Student>
        @Suppress("UNCHECKED_CAST")
        val cloudEvents = dataArr[1] as List<CardEvent>
        @Suppress("UNCHECKED_CAST")
        val allBalances = dataArr[2] as List<MonthlyBalance>
        @Suppress("UNCHECKED_CAST")
        val invitations = dataArr[3] as List<Invitation>

        val selectedMonth = filterArr[0] as String
        val selectedWeek = filterArr[1] as String?
        @Suppress("UNCHECKED_CAST")
        val dismissedDuplicates = filterArr[2] as Set<String>

        @Suppress("UNCHECKED_CAST")
        val optimisticAdded = optArr[0] as Map<String, CardEvent>
        @Suppress("UNCHECKED_CAST")
        val optimisticRemoved = optArr[1] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val optimisticStudents = optArr[2] as Map<String, Student>

        val rawStudents = if (optimisticStudents.isEmpty()) {
            cloudStudents
        } else {
            cloudStudents.map { stu -> optimisticStudents[stu.id] ?: stu }
        }

        val allEvents = if (optimisticAdded.isEmpty() && optimisticRemoved.isEmpty()) {
            cloudEvents
        } else {
            val cloudIds = HashSet<String>(cloudEvents.size)
            val filteredCloud = ArrayList<CardEvent>(cloudEvents.size + optimisticAdded.size)
            for (i in 0 until cloudEvents.size) {
                val ev = cloudEvents[i]
                cloudIds.add(ev.id)
                if (ev.id !in optimisticRemoved) {
                    filteredCloud.add(ev)
                }
            }
            optimisticAdded.values.forEach { optEv ->
                val alreadyInCloud = optEv.id in cloudIds || filteredCloud.any {
                    it.studentId == optEv.studentId &&
                        it.cardType == optEv.cardType &&
                        it.weekKey == optEv.weekKey &&
                        it.servantId == optEv.servantId
                }
                if (!alreadyInCloud && optEv.id !in optimisticRemoved) {
                    filteredCloud.add(0, optEv)
                }
            }
            filteredCloud
        }

        val events = allEvents.filter { it.monthKey == selectedMonth }
        val balances = allBalances.filter { it.monthKey == selectedMonth }

        // If the current user was removed & banned from the active group, immediately eject them and show the required message
        val isBannedOrRemovedFromGroup = group != null && (
            currentUserId in group.bannedUids ||
                (currentUserId !in group.memberUids && group.adminId != currentUserId)
            )
        if (isBannedOrRemovedFromGroup) {
            val bannedGrp = group!!
            if (lastEjectedBannedGroupId != bannedGrp.inviteCode) {
                lastEjectedBannedGroupId = bannedGrp.inviteCode
                _operationError.value = "تم إزالتك من هذه المجموعة"
                val remainingJoined = profile?.joinedGroupIds.orEmpty().filter { it != bannedGrp.inviteCode }
                viewModelScope.launch {
                    repository.updateUserGroup("", remainingJoined)
                }
            }
        }

        val activeGroup = if (isBannedOrRemovedFromGroup) null else group

        // Migrate any legacy user-scoped documents & normalize missing groupId/position fields ONCE ever per group
        val migrationKey = "${activeGroup?.inviteCode.orEmpty()}_${activeGroup?.memberUids?.size ?: 0}"
        if (
            activeGroup != null &&
            migrationKey != lastMigratedGroupKey &&
            currentUserId in activeGroup.memberUids &&
            !LocalClassroomCache.isGroupMigrationCompleted(activeGroup.inviteCode)
        ) {
            lastMigratedGroupKey = migrationKey
            viewModelScope.launch {
                repository.migrateAndNormalizeGroupData(activeGroup.inviteCode, currentUserId, activeGroup.memberUids)
                    .onSuccess {
                        LocalClassroomCache.markGroupMigrationCompleted(activeGroup.inviteCode)
                    }
            }
        }

        // Automatically migrate any unmigrated inline student avatars into groups/{groupId}/avatars/{studentId}
        if (activeGroup != null && currentUserId in activeGroup.memberUids) {
            val inlineAvatarStudents = cloudStudents.filter { it.avatarBase64.isNotBlank() }
            val inlineKey = "${activeGroup.inviteCode}_${inlineAvatarStudents.size}"
            if (inlineAvatarStudents.isNotEmpty() && inlineKey != lastMigratedInlineAvatarsGroupKey) {
                lastMigratedInlineAvatarsGroupKey = inlineKey
                // Cache locally before stripping from cloud student doc
                inlineAvatarStudents.forEach { stu ->
                    StudentAvatarImageHelper.writeLocalAlbumRaw(null, stu.id, stu.avatarBase64)
                }
                viewModelScope.launch(Dispatchers.IO) {
                    repository.migrateInlineStudentAvatars(
                        groupId = activeGroup.inviteCode,
                        studentsWithInlineAvatars = inlineAvatarStudents,
                        memberUids = activeGroup.memberUids
                    )
                }
            }
        }

        // Compute global sequential student numbering (1, 2, 3, 4...) across the entire group in memory
        val sequentiallyNumberedStudents = computeSequentiallyNumberedStudents(rawStudents, blocks)

        // Save classroom snapshot locally on device (debounced 3000ms on background IO thread, without photos)
        if (profile != null) {
            val cacheSig = "${profile.groupId}_${activeGroup?.inviteCode}_${blocks.size}_${sequentiallyNumberedStudents.size}_${cloudEvents.size}_${profile.enabledCards.size}_${profile.collapsedBlockIds.size}_${profile.hiddenBlockIds.size}_${profile.themeId}_${profile.uiScaleMode}_${profile.uiScaleFactor}_${profile.showFloatingAddButton}"
            if (cacheSig != lastSavedCacheSignature) {
                lastSavedCacheSignature = cacheSig
                LocalClassroomCache.saveClassroomSnapshot(
                    userId = currentUserId,
                    userProfile = profile,
                    group = activeGroup,
                    userGroups = userGroups,
                    blocks = blocks,
                    students = sequentiallyNumberedStudents,
                    cardEvents = cloudEvents,
                    monthlyBalances = allBalances
                )
            }
        }

        val fallbackPrevGroup = _previousGroupId.value.ifBlank {
            profile?.joinedGroupIds?.lastOrNull { it.isNotBlank() && it != lastEjectedBannedGroupId }
                ?: userGroups.firstOrNull { it.inviteCode != lastEjectedBannedGroupId }?.inviteCode.orEmpty()
        }

        val effectiveBlocks = if (activeGroup == null) emptyList() else blocks
        val effectiveStudents = if (activeGroup == null) emptyList() else sequentiallyNumberedStudents
        val effectiveEvents = if (activeGroup == null) emptyList() else events
        val effectiveBalances = if (activeGroup == null) emptyList() else balances
        val effectiveAllEvents = if (activeGroup == null) emptyList() else allEvents
        val effectiveAllBalances = if (activeGroup == null) emptyList() else allBalances
        val effectiveInvitations = if (activeGroup == null) emptyList() else invitations

        val precomputed = PrecomputedClassroomIndices(
            userProfile = profile,
            students = effectiveStudents,
            cardEvents = effectiveEvents,
            monthlyBalances = effectiveBalances,
            selectedWeekKey = selectedWeek,
            dismissedDuplicateNames = dismissedDuplicates
        ).also { it.prewarmAll() }

        val builtState = KamScreenState(
            isLoading = profile == null,
            userProfile = profile,
            group = activeGroup,
            userGroups = userGroups.filter { currentUserId !in it.bannedUids },
            isAdmin = activeGroup?.adminId == currentUserId,
            blocks = effectiveBlocks,
            students = effectiveStudents,
            cardEvents = effectiveEvents,
            monthlyBalances = effectiveBalances,
            allGroupCardEvents = effectiveAllEvents,
            allGroupMonthlyBalances = effectiveAllBalances,
            invitations = effectiveInvitations,
            selectedMonthKey = selectedMonth,
            selectedWeekKey = selectedWeek,
            currentWeekKey = currentYearWeek(),
            dismissedDuplicateNames = dismissedDuplicates,
            previousGroupId = fallbackPrevGroup,
            precomputedIndices = precomputed
        )
        // Prewarm the default current-month date-range leaderboard on Dispatchers.Default so opening the Leaderboard screen is 0ms
        if (effectiveStudents.isNotEmpty() && selectedMonth.isNotBlank()) {
            val (defaultStartMs, defaultEndMs) = SaturdayCalendarHelper.computeMonthDateRangeUtcMillis(selectedMonth)
            builtState.computeClassLeaderboardEntriesForDateRange(defaultStartMs, defaultEndMs)
        }
        builtState
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<KamScreenState> = combine(
        coreClassroomStateFlow,
        _searchQuery,
        isOnlineFlow,
        hasPendingWritesFlow,
        combine(_actionBanner, _operationError) { banner, error -> banner to error }
    ) { coreState, searchQuery, isOnline, hasPendingWrites, (banner, error) ->
        val activeGroup = coreState.group
        if (activeGroup != null && activeGroup.adminId == currentUserId && isOnline && activeGroup.isDailyCodeExpired()) {
            val rotationKey = "${activeGroup.inviteCode}_${activeGroup.inviteCodeUpdatedAtMillis}"
            if (rotationKey != lastAutoRotatedGroupKey) {
                lastAutoRotatedGroupKey = rotationKey
                viewModelScope.launch {
                    repository.rotateDailyInviteCode(
                        groupId = activeGroup.inviteCode,
                        previousToken = activeGroup.dailyInviteToken
                    )
                }
            }
        }

        val resolvedError = error ?: _operationError.value
        coreState.copy(
            isLoading = coreState.userProfile == null && resolvedError == null,
            errorMessage = resolvedError,
            actionBannerMessage = banner,
            searchQuery = searchQuery,
            isOnline = isOnline,
            hasPendingWrites = hasPendingWrites
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = buildInitialStateFromCache(
                cached = initialCachedSnapshot,
                localPrefs = _localPrefsOverride.value,
                currentUserId = currentUserId
            )
        )

    /**
     * Immediately stops all active Firestore snapshot listeners and background jobs for this session
     * prior to Firebase Auth sign-out so no listeners remain active with unauthenticated state.
     */
    fun clearSession() {
        Log.d(TAG, "[SignOut] Clearing KamViewModel session for uid=$currentUserId")
        bannerJob?.cancel()
        recentTapTimestamps.clear()
        lastMigratedGroupKey = ""
        lastAutoRotatedGroupKey = ""
        lastEjectedBannedGroupId = ""
        _optimisticAddedEvents.value = emptyMap()
        _optimisticRemovedEventIds.value = emptySet()
        _optimisticStudentOverrides.value = emptyMap()
        _operationError.value = null
        _actionBanner.value = null
        _isBusy.value = false
        viewModelScope.coroutineContext[Job]?.cancelChildren()
    }

    fun showBanner(message: String) {
        bannerJob?.cancel()
        _actionBanner.value = message
        bannerJob = viewModelScope.launch {
            delay(2500L)
            _actionBanner.value = null
        }
    }

    fun clearError() {
        _operationError.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectMonthAndWeek(monthKey: String, weekKey: String?) {
        val minStartMonth = String.format(
            Locale.US,
            "%04d-%02d",
            SaturdayCalendarHelper.APP_START_YEAR,
            SaturdayCalendarHelper.APP_START_MONTH
        )
        if (monthKey < minStartMonth) {
            showBanner("شهر نوفمبر ٢٠٢٦ هو بداية التقويم في التطبيق")
            return
        }
        val nowMonth = currentYearMonth()
        if (monthKey > nowMonth) {
            showBanner("لا يمكن فتح الشهور التي لم تبدأ بعد")
            return
        }
        _selectedMonthKey.value = monthKey
        _selectedWeekKey.value = weekKey
    }

    fun selectMonthOffset(deltaMonths: Int) {
        val current = _selectedMonthKey.value
        val parts = current.split("-")
        if (parts.size != 2) return
        val year = parts[0].toIntOrNull() ?: return
        val month = parts[1].toIntOrNull() ?: return
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, deltaMonths)
        }
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        val newMonthKey = sdf.format(cal.time)
        val minStartMonth = String.format(
            Locale.US,
            "%04d-%02d",
            SaturdayCalendarHelper.APP_START_YEAR,
            SaturdayCalendarHelper.APP_START_MONTH
        )
        if (newMonthKey < minStartMonth) {
            showBanner("شهر نوفمبر ٢٠٢٦ هو أول شهر في التطبيق")
            return
        }
        val nowMonthKey = currentYearMonth()
        if (newMonthKey > nowMonthKey) {
            showBanner("الشهر القادم لم يبدأ بعد")
            return
        }
        _selectedMonthKey.value = newMonthKey
        _selectedWeekKey.value = if (newMonthKey == nowMonthKey) {
            currentYearWeek()
        } else {
            null
        }
    }

    // --- Group Creation, Joining & Switching ---

    fun createGroup(groupName: String, adminName: String) {
        viewModelScope.launch {
            _isBusy.value = true
            _operationError.value = null
            repository.createGroup(groupName, adminName)
                .onSuccess { group ->
                    _previousGroupId.value = group.inviteCode
                    _dismissedDuplicateNames.value = emptySet()
                    showBanner("تم إنشاء مجموعة '${group.name}' برمز يومي: ${group.fullDailyInviteCode}")
                }
                .onFailure { err ->
                    _operationError.value = err.localizedMessage ?: "تعذر إنشاء المجموعة"
                }
            _isBusy.value = false
        }
    }

    fun joinGroupByCode(inviteCode: String, servantName: String) {
        viewModelScope.launch {
            _isBusy.value = true
            _operationError.value = null
            repository.joinGroupByCode(inviteCode, servantName)
                .onSuccess { group ->
                    _previousGroupId.value = group.inviteCode
                    _dismissedDuplicateNames.value = emptySet()
                    showBanner("تم الانضمام بنجاح إلى '${group.name}'")
                }
                .onFailure { err ->
                    _operationError.value = err.localizedMessage ?: "تعذر الانضمام برمز المجموعة"
                }
            _isBusy.value = false
        }
    }

    fun refreshDailyInviteCode() {
        val state = uiState.value
        val group = state.group ?: return
        if (!state.isAdmin) {
            showBanner("تجديد رمز المجموعة متاح للأمين فقط")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.rotateDailyInviteCode(
                groupId = group.inviteCode,
                previousToken = group.dailyInviteToken
            ).onSuccess { (newToken, _) ->
                showBanner("تم تجديد الرمز اليومي فوراً: ${group.inviteCode}-$newToken (صالح لمدة ٢٤ ساعة)")
            }.onFailure { err ->
                _operationError.value = err.localizedMessage ?: "تعذر تجديد الرمز اليومي"
            }
            _isBusy.value = false
        }
    }

    fun switchActiveGroup(targetGroupId: String) {
        val state = uiState.value
        val normalizedTarget = targetGroupId.trim().uppercase(Locale.US)
        if (normalizedTarget.isBlank() || normalizedTarget == state.group?.inviteCode) return
        val existingJoined = state.userProfile?.joinedGroupIds.orEmpty()
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateUserGroup(normalizedTarget, existingJoined)
                .onSuccess {
                    _previousGroupId.value = normalizedTarget
                    _dismissedDuplicateNames.value = emptySet()
                    val targetName = state.userGroups.find { it.inviteCode == normalizedTarget }?.name ?: normalizedTarget
                    showBanner("تم التبديل إلى مجموعة '$targetName'")
                }
                .onFailure {
                    _operationError.value = it.localizedMessage ?: "تعذر التبديل بين المجموعات"
                }
            _isBusy.value = false
        }
    }

    fun leaveCurrentGroup() {
        val currentCode = uiState.value.group?.inviteCode?.trim().orEmpty()
        if (currentCode.isNotBlank()) {
            _previousGroupId.value = currentCode
        }
        val existingJoined = (uiState.value.userProfile?.joinedGroupIds.orEmpty() + listOfNotNull(currentCode.takeIf { it.isNotBlank() })).distinct()
        viewModelScope.launch {
            repository.updateUserGroup("", existingJoined)
                .onSuccess { showBanner("يمكنك الآن إنشاء مجموعة جديدة أو الانضمام برمز مجموعة") }
                .onFailure { _operationError.value = it.localizedMessage }
        }
    }

    fun returnToPreviousGroup() {
        val state = uiState.value
        val targetGroupId = _previousGroupId.value
            .ifBlank { state.previousGroupId }
            .ifBlank { state.userProfile?.joinedGroupIds?.lastOrNull { it.isNotBlank() }.orEmpty() }
            .ifBlank { state.userGroups.firstOrNull()?.inviteCode.orEmpty() }
        if (targetGroupId.isBlank()) return
        val existingJoined = state.userProfile?.joinedGroupIds.orEmpty()
        viewModelScope.launch {
            _isBusy.value = true
            _operationError.value = null
            repository.updateUserGroup(targetGroupId, existingJoined)
                .onSuccess {
                    val targetName = state.userGroups.find { it.inviteCode == targetGroupId }?.name ?: targetGroupId
                    showBanner("تمت العودة إلى مجموعة '$targetName'")
                }
                .onFailure {
                    _operationError.value = it.localizedMessage ?: "تعذر الرجوع للمجموعة السابقة"
                }
            _isBusy.value = false
        }
    }

    // --- Personal Block Collapse & Hide Preferences (Saved per User Locally + in users/{uid}) ---

    private fun currentEffectiveLocalPrefs(): LocalUserPreferences {
        _localPrefsOverride.value?.let { return it }
        val profile = uiState.value.userProfile
        return LocalUserPreferences(
            enabledCards = profile?.enabledCards ?: CardType.defaultEnabledIds,
            collapsedBlockIds = profile?.collapsedBlockIds ?: emptyList(),
            hiddenBlockIds = profile?.hiddenBlockIds ?: emptyList(),
            themeId = profile?.themeId ?: "DARK",
            uiScaleMode = profile?.uiScaleMode ?: "AUTO",
            uiScaleFactor = (profile?.uiScaleFactor ?: 0.85).toFloat(),
            showFloatingAddButton = profile?.showFloatingAddButton ?: true,
            activeGroupId = profile?.groupId.orEmpty()
        )
    }

    fun toggleBlockCollapsed(blockId: String) {
        val prefs = currentEffectiveLocalPrefs()
        val currentCollapsed = prefs.collapsedBlockIds
        val updatedCollapsed = if (blockId in currentCollapsed) {
            currentCollapsed.filter { it != blockId }
        } else {
            (currentCollapsed + blockId).distinct()
        }
        val nextPrefs = prefs.copy(collapsedBlockIds = updatedCollapsed)
        _localPrefsOverride.value = nextPrefs
        LocalClassroomCache.saveUserPreferences(currentUserId, nextPrefs)

        viewModelScope.launch {
            repository.updatePersonalBlockPreferences(
                collapsedBlockIds = updatedCollapsed,
                hiddenBlockIds = nextPrefs.hiddenBlockIds
            )
        }
    }

    fun toggleBlockHidden(blockId: String) {
        val prefs = currentEffectiveLocalPrefs()
        val currentHidden = prefs.hiddenBlockIds
        val updatedHidden = if (blockId in currentHidden) {
            currentHidden.filter { it != blockId }
        } else {
            (currentHidden + blockId).distinct()
        }
        val nextPrefs = prefs.copy(hiddenBlockIds = updatedHidden)
        _localPrefsOverride.value = nextPrefs
        LocalClassroomCache.saveUserPreferences(currentUserId, nextPrefs)

        val blockName = uiState.value.blocks.find { it.id == blockId }?.servantName ?: "البلوك"
        if (blockId in updatedHidden) {
            showBanner("تم إخفاء بلوك '$blockName' من شاشتك الشخصية وحفظ التفضيل في حسابك")
        } else {
            showBanner("تم إظهار بلوك '$blockName' في شاشتك الشخصية وحفظ التفضيل في حسابك")
        }

        viewModelScope.launch {
            repository.updatePersonalBlockPreferences(
                collapsedBlockIds = nextPrefs.collapsedBlockIds,
                hiddenBlockIds = updatedHidden
            )
        }
    }

    // --- Duplicate Student Name Resolution (Manual Comparison) ---

    fun mergeDuplicateStudentRecords(keepStudent: Student, duplicateStudent: Student) {
        val state = uiState.value
        val group = state.group ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.mergeDuplicateStudents(
                groupId = group.inviteCode,
                keepStudent = keepStudent,
                duplicateStudent = duplicateStudent,
                memberUids = group.memberUids
            ).onSuccess {
                showBanner("تم الاحتفاظ بسجل '${keepStudent.name}' (#${keepStudent.studentNumber}) ودمج الكروت بنجاح")
            }.onFailure { err ->
                val normName = ArabicInitialsHelper.normalizeArabicName(keepStudent.name)
                if (!state.isAdmin) {
                    _dismissedDuplicateNames.value = _dismissedDuplicateNames.value + normName
                    showBanner("تم دمج الكروت لـ '${keepStudent.name}' (الحذف النهائي للسجل المكرر متاح للأمين)")
                } else {
                    _operationError.value = err.localizedMessage ?: "تعذر دمج السجل المكرر"
                }
            }
            _isBusy.value = false
        }
    }

    fun keepBothDuplicateStudents(normalizedName: String) {
        _dismissedDuplicateNames.value = _dismissedDuplicateNames.value + normalizedName
        showBanner("تم الإبقاء على الاسمين كطفلين منفصلين")
    }

    // --- Servant Blocks ---

    fun createServantBlock(servantName: String, colorHex: String) {
        val state = uiState.value
        val group = state.group ?: return
        viewModelScope.launch {
            val nextOrder = (state.blocks.maxOfOrNull { it.sortOrder.toInt() } ?: -1) + 1
            repository.createBlock(
                groupId = group.inviteCode,
                servantId = currentUserId,
                servantName = servantName,
                colorHex = colorHex,
                sortOrder = nextOrder,
                memberUids = group.memberUids,
                waitForServerAck = false
            ).onSuccess {
                showBanner("تم إضافة بلوك الخادم: $servantName")
            }.onFailure {
                _operationError.value = it.localizedMessage ?: "تعذر إضافة البلوك"
            }
        }
    }

    fun updateServantBlock(block: ServantBlock, newName: String, newColorHex: String) {
        val state = uiState.value
        val group = state.group ?: return
        viewModelScope.launch {
            repository.updateBlock(
                groupId = group.inviteCode,
                blockId = block.id,
                servantName = newName,
                colorHex = newColorHex,
                sortOrder = block.sortOrder,
                memberUids = group.memberUids,
                waitForServerAck = false
            ).onSuccess {
                showBanner("تم تحديث بلوك $newName")
            }.onFailure {
                _operationError.value = it.localizedMessage ?: "تعذر تعديل البلوك"
            }
        }
    }

    /**
     * Removes ONLY the servant from the group and bans their account from rejoining,
     * while keeping the ServantBlock and all children assigned to that block 100% intact.
     */
    fun removeAndBanServantFromBlock(block: ServantBlock) {
        val state = uiState.value
        val group = state.group ?: return
        if (!state.isAdmin) {
            showBanner("إزالة وحظر الخادم متاحة لأمين المجموعة فقط")
            return
        }
        val targetUid = block.servantId.trim()
        if (targetUid.isBlank()) {
            showBanner("هذا البلوك غير مرتبط بحساب خادم حالياً (الأطفال والبلوك محفوظون)")
            return
        }
        if (targetUid == group.adminId) {
            showBanner("لا يمكن إزالة حساب أمين المجموعة")
            return
        }
        val childrenCount = state.students.count { it.blockId == block.id }
        viewModelScope.launch {
            _isBusy.value = true
            repository.removeAndBanServantFromGroup(
                groupId = group.inviteCode,
                block = block,
                currentGroup = group
            ).onSuccess {
                showBanner(
                    "تمت إزالة الخادم '${block.servantName}' وحظره من المجموعة • البلوك وأطفاله ($childrenCount) محفوظون بالكامل"
                )
            }.onFailure { err ->
                _operationError.value = err.localizedMessage ?: "تعذر إزالة وحظر الخادم"
            }
            _isBusy.value = false
        }
    }

    fun unbanServant(servantUid: String, servantName: String) {
        val state = uiState.value
        val group = state.group ?: return
        if (!state.isAdmin) return
        viewModelScope.launch {
            _isBusy.value = true
            repository.unbanServantFromGroup(
                groupId = group.inviteCode,
                servantUid = servantUid,
                currentGroup = group
            ).onSuccess {
                showBanner("تم إلغاء حظر '$servantName' ويمكنه الانضمام مجدداً بالرمز اليومي")
            }.onFailure { err ->
                _operationError.value = err.localizedMessage ?: "تعذر إلغاء الحظر"
            }
            _isBusy.value = false
        }
    }

    fun deleteServantBlock(block: ServantBlock) {
        val state = uiState.value
        val group = state.group ?: return
        // If this block belongs to a servant, remove & ban ONLY the servant while keeping the block and its children intact!
        if (block.servantId.isNotBlank() && block.servantId != group.adminId) {
            removeAndBanServantFromBlock(block)
            return
        }
        viewModelScope.launch {
            // Move any students in this unassigned block back to "بدون بلوك" before deleting the empty shell
            val studentsInBlock = state.students.filter { it.blockId == block.id }
            studentsInBlock.forEach { stu ->
                repository.moveStudentToBlock(
                    groupId = group.inviteCode,
                    studentId = stu.id,
                    targetBlockId = "",
                    newSortOrder = stu.sortOrder,
                    newPosition = stu.effectivePosition,
                    memberUids = group.memberUids,
                    waitForServerAck = false
                )
            }
            repository.deleteBlock(group.inviteCode, block.id)
                .onSuccess {
                    showBanner("تم حذف البلوك الفارغ '${block.servantName}'")
                }
                .onFailure {
                    _operationError.value = "رفض السيرفر الحذف: الحذف مسموح للأمين فقط"
                }
        }
    }

    // --- Students, Global Sequential Numbering (1..150) & Bulk Import ---

    fun addSingleStudent(name: String, customNumber: Int?, targetBlockId: String = "") {
        val state = uiState.value
        val group = state.group ?: return
        if (state.students.size >= MAX_STUDENTS_PER_GROUP) {
            showBanner("وصلت المجموعة للحد الأقصى المسموح به ($MAX_STUDENTS_PER_GROUP اسم)")
            return
        }
        val nextNum = customNumber ?: (state.students.size + 1)
        val siblingsInTarget = state.students.filter { it.blockId == targetBlockId }
        val nextOrder = (siblingsInTarget.maxOfOrNull { it.sortOrder.toInt() } ?: -1) + 1
        val nextPosition = (siblingsInTarget.maxOfOrNull { it.effectivePosition } ?: 0.0) + 1000.0
        viewModelScope.launch {
            repository.addStudent(
                groupId = group.inviteCode,
                name = name,
                studentNumber = nextNum,
                blockId = targetBlockId,
                sortOrder = nextOrder,
                position = nextPosition,
                memberUids = group.memberUids,
                waitForServerAck = false
            ).onSuccess {
                showBanner("تم إضافة الطفل '$name' (${state.students.size + 1} / $MAX_STUDENTS_PER_GROUP)")
            }.onFailure {
                _operationError.value = it.localizedMessage ?: "تعذر إضافة الطفل"
            }
        }
    }

    fun analyzeBulkImportText(rawText: String): List<BulkImportLinePreview> {
        val existingStudents = uiState.value.students
        val existingByNorm = existingStudents.associateBy {
            ArabicInitialsHelper.normalizeArabicName(it.name)
        }
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val seenNorms = mutableSetOf<String>()

        return lines.map { line ->
            val norm = ArabicInitialsHelper.normalizeArabicName(line)
            val isDuplicateInPaste = !seenNorms.add(norm)
            val similarExisting = existingByNorm[norm]?.name
                ?: existingStudents.firstOrNull { stu ->
                    val stuNorm = ArabicInitialsHelper.normalizeArabicName(stu.name)
                    stuNorm.length >= 4 && norm.length >= 4 && (stuNorm.contains(norm) || norm.contains(stuNorm))
                }?.name
            BulkImportLinePreview(
                rawName = line,
                normalizedName = norm,
                duplicateInPaste = isDuplicateInPaste,
                existingSimilarStudentName = similarExisting
            )
        }
    }

    fun importBulkStudents(names: List<String>) {
        val state = uiState.value
        val group = state.group ?: return
        val remainingCapacity = (MAX_STUDENTS_PER_GROUP - state.students.size).coerceAtLeast(0)
        if (remainingCapacity <= 0) {
            showBanner("وصلت المجموعة للحد الأقصى المسموح به ($MAX_STUDENTS_PER_GROUP اسم)")
            return
        }
        val boundedNames = names.take(remainingCapacity)
        val wasTruncated = names.size > remainingCapacity
        val nextNum = state.students.size + 1
        val nextOrder = (state.students.maxOfOrNull { it.sortOrder.toInt() } ?: -1) + 1
        val nextPosition = (state.students.maxOfOrNull { it.effectivePosition } ?: 0.0) + 1000.0
        viewModelScope.launch {
            _isBusy.value = true
            repository.importStudentsBatch(
                groupId = group.inviteCode,
                names = boundedNames,
                startingStudentNumber = nextNum,
                startingSortOrder = nextOrder,
                startingPosition = nextPosition,
                memberUids = group.memberUids,
                waitForServerAck = false
            ).onSuccess { count ->
                if (wasTruncated) {
                    showBanner("تم استيراد $count اسم (تم الوصول للحد الأقصى $MAX_STUDENTS_PER_GROUP اسم للمجموعة)")
                } else {
                    showBanner("تم استيراد $count اسم بنجاح للمجموعة المشتركة")
                }
            }.onFailure { err ->
                _operationError.value = err.localizedMessage ?: "تعذر استيراد القائمة"
            }
            _isBusy.value = false
        }
    }

    fun moveStudentToBlock(student: Student, targetBlockId: String) {
        val state = uiState.value
        val group = state.group ?: return
        val targetSiblings = state.students.filter { it.blockId == targetBlockId && it.id != student.id }
        val maxSortInTarget = targetSiblings.maxOfOrNull { it.sortOrder } ?: -1L
        val maxPositionInTarget = targetSiblings.maxOfOrNull { it.effectivePosition } ?: 0.0
        val newSortOrder = maxSortInTarget + 1L
        val newPosition = maxPositionInTarget + 1000.0

        // Apply 0ms optimistic student update so the UI moves the student immediately
        _optimisticStudentOverrides.update { current ->
            current + (student.id to student.copy(
                blockId = targetBlockId,
                sortOrder = newSortOrder,
                position = newPosition
            ))
        }

        viewModelScope.launch {
            repository.moveStudentToBlock(
                groupId = group.inviteCode,
                studentId = student.id,
                targetBlockId = targetBlockId,
                newSortOrder = newSortOrder,
                newPosition = newPosition,
                memberUids = group.memberUids,
                waitForServerAck = false
            ).onSuccess {
                _optimisticStudentOverrides.update { it - student.id }
                val targetName = if (targetBlockId.isBlank()) {
                    "بدون بلوك"
                } else {
                    state.blocks.find { it.id == targetBlockId }?.servantName ?: "البلوك"
                }
                showBanner("تم نقل '${student.name}' إلى $targetName وتحديث التسلسل")
            }.onFailure {
                _optimisticStudentOverrides.update { it - student.id }
                _operationError.value = it.localizedMessage ?: "تعذر نقل الطفل"
            }
        }
    }

    /**
     * Fractional ordering (`position: Double`) inside a block or "بدون بلوك":
     * Updates the moved student's fractional position and synchronizes the sequential 1..N numbering.
     */
    fun reorderStudentWithinBlock(student: Student, moveUp: Boolean) {
        val state = uiState.value
        val group = state.group ?: return
        val validBlockIds = state.blocks.map { it.id }.toSet()
        val isUnassigned = student.blockId.isBlank() || student.blockId !in validBlockIds

        val siblings = state.students
            .filter {
                if (isUnassigned) {
                    it.blockId.isBlank() || it.blockId !in validBlockIds
                } else {
                    it.blockId == student.blockId
                }
            }
            .sortedWith(
                compareBy<Student> { it.effectivePosition }
                    .thenBy { it.sortOrder }
                    .thenBy { it.studentNumber }
                    .thenBy { it.id }
            )

        val currentIdx = siblings.indexOfFirst { it.id == student.id }
        if (currentIdx == -1) return
        val targetIdx = if (moveUp) currentIdx - 1 else currentIdx + 1
        if (targetIdx !in siblings.indices) return

        // Ensure monotonic base positions if legacy documents have identical positions
        val effectivePositions = siblings.mapIndexed { index, stu ->
            val raw = stu.effectivePosition
            if (index > 0 && raw <= siblings[index - 1].effectivePosition) {
                (index + 1) * 1000.0
            } else {
                raw
            }
        }

        val newPosition: Double = if (moveUp) {
            if (targetIdx == 0) {
                effectivePositions[0] - 500.0
            } else {
                (effectivePositions[targetIdx - 1] + effectivePositions[targetIdx]) / 2.0
            }
        } else {
            if (targetIdx == siblings.lastIndex) {
                effectivePositions[siblings.lastIndex] + 1000.0
            } else {
                (effectivePositions[targetIdx] + effectivePositions[targetIdx + 1]) / 2.0
            }
        }

        val neighborStudent = siblings[targetIdx]
        val movedNewNumber = neighborStudent.studentNumber
        val neighborNewNumber = student.studentNumber

        // Apply 0ms optimistic reorder so the row swaps immediately on tap
        _optimisticStudentOverrides.update { current ->
            current +
                (student.id to student.copy(position = newPosition, studentNumber = movedNewNumber)) +
                (neighborStudent.id to neighborStudent.copy(studentNumber = neighborNewNumber))
        }

        viewModelScope.launch {
            repository.updateStudentPosition(
                groupId = group.inviteCode,
                studentId = student.id,
                newPosition = newPosition,
                newStudentNumber = movedNewNumber,
                waitForServerAck = false
            )
            repository.syncStudentSequentialNumbers(
                groupId = group.inviteCode,
                numberUpdates = mapOf(
                    student.id to movedNewNumber,
                    neighborStudent.id to neighborNewNumber
                ),
                waitForServerAck = false
            )
            _optimisticStudentOverrides.update { it - student.id - neighborStudent.id }
        }
    }

    fun updateStudentDetails(student: Student, newName: String, newNumber: Int, newBlockId: String) {
        val state = uiState.value
        val group = state.group ?: return
        val newPosition = if (newBlockId != student.blockId) {
            val targetMax = state.students
                .filter { it.blockId == newBlockId && it.id != student.id }
                .maxOfOrNull { it.effectivePosition } ?: 0.0
            targetMax + 1000.0
        } else {
            student.effectivePosition
        }
        viewModelScope.launch {
            repository.updateStudent(
                groupId = group.inviteCode,
                studentId = student.id,
                newName = newName,
                newStudentNumber = newNumber,
                newBlockId = newBlockId,
                newSortOrder = student.sortOrder,
                newPosition = newPosition,
                memberUids = group.memberUids,
                waitForServerAck = false
            ).onSuccess {
                showBanner("تم حفظ تعديلات '${newName.trim()}'")
            }.onFailure {
                _operationError.value = it.localizedMessage ?: "تعذر تعديل بيانات الطفل"
            }
        }
    }

    fun updateStudentAvatar(student: Student, avatarBase64: String) {
        val state = uiState.value
        val group = state.group ?: return
        val cleanAvatar = avatarBase64.trim()
        val hasAvatar = cleanAvatar.isNotBlank()
        val newHash = Student.computeAvatarHash(cleanAvatar)
        StudentAvatarImageHelper.writeLocalAlbumRaw(null, student.id, cleanAvatar)
        _optimisticStudentOverrides.update { current ->
            current + (student.id to student.copy(
                avatarBase64 = "",
                hasAvatar = hasAvatar,
                avatarHash = newHash
            ))
        }
        viewModelScope.launch {
            repository.updateStudentAvatar(
                groupId = group.inviteCode,
                studentId = student.id,
                avatarBase64 = cleanAvatar,
                memberUids = group.memberUids,
                waitForServerAck = false
            ).onSuccess {
                _optimisticStudentOverrides.update { it - student.id }
                if (cleanAvatar.isBlank()) {
                    showBanner("تمت إزالة صورة '${student.name}' والعودة للحروف")
                } else if (!state.isOnline) {
                    showBanner("تم حفظ صورة '${student.name}' محلياً وستتزامن تلقائياً")
                } else {
                    showBanner("تم تحديث صورة '${student.name}'")
                }
            }.onFailure {
                _optimisticStudentOverrides.update { it - student.id }
                _operationError.value = it.localizedMessage ?: "تعذر تحديث صورة الطفل"
            }
        }
    }

    fun deleteStudent(student: Student) {
        val group = uiState.value.group ?: return
        viewModelScope.launch {
            repository.deleteStudent(group.inviteCode, student.id)
                .onSuccess {
                    showBanner("تم حذف '${student.name}'")
                }
                .onFailure {
                    _operationError.value = "رفض السيرفر الحذف: الحذف متاح للأمين فقط"
                }
        }
    }

    // --- Cards & Points (Fast 1-Tap with Deterministic ID, Toggle Undo & Duplicate Card Alert) ---

    fun awardCardToStudent(student: Student, cardType: CardType) {
        KamPerformanceTracker.markCardTapStart(student.id, cardType.id)
        val state = uiState.value
        val group = state.group ?: return
        val activeWeekKey = state.selectedWeekKey?.takeIf { it.isNotBlank() }
            ?: SaturdayCalendarHelper.buildWeeksForMonth(
                year = state.selectedMonthKey.substringBefore("-").toIntOrNull() ?: 2026,
                monthNumber = state.selectedMonthKey.substringAfter("-").toIntOrNull() ?: 1
            ).lastOrNull()?.weekKey
            ?: state.currentWeekKey

        val tapKey = "${student.id}_${cardType.id}_$activeWeekKey"
        val nowMs = System.currentTimeMillis()
        val lastTap = recentTapTimestamps[tapKey] ?: 0L

        // Prevent rapid accidental double-taps within 350ms on the exact same student + card
        if (nowMs - lastTap < 350L) {
            return
        }

        // If the SAME servant already recorded this card in the active week:
        // Tapping it again toggles/undoes it back to the symbol (if allowed by group policy or Admin)
        val existingBySameServant = state.cardEvents.firstOrNull {
            it.studentId == student.id &&
                it.cardType == cardType.id &&
                it.weekKey == activeWeekKey &&
                it.servantId == currentUserId
        }
        if (existingBySameServant != null) {
            if (state.isAdmin || group.allowServantUndo) {
                recentTapTimestamps[tapKey] = nowMs
                undoCardEvent(existingBySameServant)
                KamPerformanceTracker.markCardTapComplete(student.name, "${cardType.arabicTitle} (إلغاء)")
            } else {
                showBanner("تم تسجيل كارت '${cardType.arabicTitle}' لـ ${student.name} بالفعل هذا الأسبوع")
            }
            return
        }

        // Check strict weekly limit across all servants if enabled by Admin
        if (group.enforceWeeklyCardLimit) {
            val alreadyGivenThisWeek = state.cardEvents.any {
                it.studentId == student.id &&
                    it.cardType == cardType.id &&
                    it.weekKey == activeWeekKey
            }
            if (alreadyGivenThisWeek) {
                showBanner("تنبيه: كارت '${cardType.arabicTitle}' تم تسجيله بالفعل لـ ${student.name} هذا الأسبوع")
                return
            }
        }

        val currentPoints = state.pointsForStudentInSelectedMonth(student.id)
        if (group.maxMonthlyPoints > 0L && currentPoints + cardType.points > group.maxMonthlyPoints) {
            showBanner("وصل ${student.name} للحد الأقصى الشهري للنقاط (${group.maxMonthlyPoints} نقطة)")
            return
        }

        recentTapTimestamps[tapKey] = nowMs
        val servantName = state.userProfile?.displayName?.ifBlank { initialDisplayName } ?: initialDisplayName
        val slotKey = activeWeekKey
        val currentEventsCount = state.eventsForStudent(student.id).size.toLong()

        // Check if another servant already gave this exact card in this week (for instant feedback if already synced)
        val otherServantEvent = state.cardEvents.firstOrNull {
            it.studentId == student.id &&
                it.cardType == cardType.id &&
                it.weekKey == activeWeekKey &&
                it.servantId != currentUserId
        }

        val deterministicEventId = "ev_${group.inviteCode}_${student.id}_${cardType.id}_${currentUserId}_$slotKey"
        val optimisticEvent = CardEvent(
            id = deterministicEventId,
            groupId = group.inviteCode,
            studentId = student.id,
            studentName = student.name,
            cardType = cardType.id,
            points = cardType.points.toLong(),
            servantId = currentUserId,
            servantName = servantName,
            monthKey = state.selectedMonthKey,
            weekKey = activeWeekKey,
            memberUids = group.memberUids
        )

        // Apply 0ms optimistic card addition before Firestore local cache round-trip
        _optimisticRemovedEventIds.update { it - deterministicEventId }
        _optimisticAddedEvents.update { it + (deterministicEventId to optimisticEvent) }
        KamPerformanceTracker.markCardTapComplete(student.name, cardType.arabicTitle)

        viewModelScope.launch {
            repository.awardCard(
                groupId = group.inviteCode,
                student = student,
                cardType = cardType,
                servantName = servantName,
                monthKey = state.selectedMonthKey,
                weekKey = activeWeekKey,
                deterministicSlotKey = slotKey,
                currentMonthPoints = currentPoints,
                currentMonthEventsCount = currentEventsCount,
                memberUids = group.memberUids,
                waitForServerAck = false
            ).onSuccess {
                _optimisticAddedEvents.update { it - deterministicEventId }
                if (otherServantEvent != null) {
                    showBanner(
                        "تنبيه: الطفل ${student.name} أخد كارت ${cardType.arabicTitle} مرتين: ${otherServantEvent.servantName} و$servantName"
                    )
                } else if (!state.isOnline) {
                    showBanner("+${cardType.points} (${cardType.arabicTitle}) لـ ${student.name} • محفوظ أوفلاين")
                } else {
                    showBanner("+${cardType.points} (${cardType.arabicTitle}) لـ ${student.name}")
                }
            }.onFailure { err ->
                _optimisticAddedEvents.update { it - deterministicEventId }
                recentTapTimestamps.remove(tapKey)
                _operationError.value = err.localizedMessage ?: "تعذر تسجيل الكارت"
            }
        }
    }

    fun undoCardEvent(event: CardEvent) {
        val state = uiState.value
        val group = state.group ?: return

        // Apply 0ms optimistic removal before Firestore local cache round-trip
        _optimisticAddedEvents.update { it - event.id }
        _optimisticRemovedEventIds.update { it + event.id }

        viewModelScope.launch {
            repository.undoCardEvent(
                groupId = group.inviteCode,
                event = event,
                waitForServerAck = false
            ).onSuccess {
                _optimisticRemovedEventIds.update { it - event.id }
                showBanner("تم إلغاء كارت من سجل '${event.studentName}'")
            }.onFailure {
                _optimisticRemovedEventIds.update { it - event.id }
                _operationError.value = "غير مسموح بإلغاء الكارت إلا للأمين أو للخادم المسجِّل حسب إعدادات المجموعة"
            }
        }
    }

    // --- Personal Card Visibility, Appearance & Group Policies ---

    fun toggleCardVisibility(cardType: CardType, enabled: Boolean) {
        val prefs = currentEffectiveLocalPrefs()
        val current = prefs.enabledCards.ifEmpty { CardType.defaultEnabledIds }
        val updated = if (enabled) {
            (current + cardType.id).distinct()
        } else {
            val remaining = current.filter { it != cardType.id }
            if (remaining.isEmpty()) {
                showBanner("يجب إبقاء كارت واحد على الأقل مفعّلاً في الشاشة الرئيسية")
                return
            }
            remaining
        }
        // Update in-memory and local disk cache in 0ms so the switch NEVER reverts when navigating between screens
        val nextPrefs = prefs.copy(enabledCards = updated)
        _localPrefsOverride.value = nextPrefs
        LocalClassroomCache.saveUserPreferences(currentUserId, nextPrefs)

        viewModelScope.launch {
            repository.updateEnabledCards(updated)
        }
    }

    fun syncAppearanceAndScaleToCloud(
        themeId: String? = null,
        uiScaleMode: String? = null,
        uiScaleFactor: Float? = null,
        showFloatingAddButton: Boolean? = null
    ) {
        val prefs = currentEffectiveLocalPrefs()
        val nextPrefs = prefs.copy(
            themeId = (themeId ?: prefs.themeId).ifBlank { "DARK" },
            uiScaleMode = (uiScaleMode ?: prefs.uiScaleMode).ifBlank { "AUTO" },
            uiScaleFactor = (uiScaleFactor ?: prefs.uiScaleFactor).coerceIn(0.70f, 1.30f),
            showFloatingAddButton = showFloatingAddButton ?: prefs.showFloatingAddButton
        )
        _localPrefsOverride.value = nextPrefs
        LocalClassroomCache.saveUserPreferences(currentUserId, nextPrefs)

        viewModelScope.launch {
            repository.updateUserAppearanceAndScalePreferences(
                themeId = nextPrefs.themeId,
                uiScaleMode = nextPrefs.uiScaleMode,
                uiScaleFactor = nextPrefs.uiScaleFactor.toDouble(),
                showFloatingAddButton = nextPrefs.showFloatingAddButton
            )
        }
    }

    fun updateGroupPolicySettings(
        allowServantUndo: Boolean,
        enforceWeeklyCardLimit: Boolean,
        maxMonthlyPoints: Long,
        attendanceIsCardOnly: Boolean,
        showLeaderboard: Boolean,
        autoStudentNumber: Boolean
    ) {
        val group = uiState.value.group ?: return
        viewModelScope.launch {
            repository.updateGroupPolicies(
                groupId = group.inviteCode,
                allowServantUndo = allowServantUndo,
                enforceWeeklyCardLimit = enforceWeeklyCardLimit,
                maxMonthlyPoints = maxMonthlyPoints,
                attendanceIsCardOnly = attendanceIsCardOnly,
                showLeaderboard = showLeaderboard,
                autoStudentNumber = autoStudentNumber
            ).onSuccess {
                showBanner("تم حفظ قواعد وقرارات المجموعة")
            }.onFailure {
                _operationError.value = "تعديل قواعد المجموعة متاح للأمين فقط"
            }
        }
    }

    // --- Invitations ---

    fun sendServantInvitation(
        inviteeName: String,
        onShareReady: (String) -> Unit = {}
    ) {
        val group = uiState.value.group ?: return
        val cleanName = inviteeName.trim().ifBlank { "خادم الفصل" }
        val fullCode = group.fullDailyInviteCode
        val shareText = buildInvitationShareText(
            groupName = group.name,
            inviteCode = fullCode,
            inviteeName = cleanName
        )
        // Open Share Sheet immediately so the user can send the invite link & code right away
        onShareReady(shareText)

        viewModelScope.launch {
            repository.createInvitation(
                groupId = group.inviteCode,
                groupName = group.name,
                inviteeName = cleanName,
                memberUids = group.memberUids,
                fullInviteCode = fullCode
            ).onSuccess {
                showBanner("تم حفظ الدعوة لـ '$cleanName' وتجهيز الرمز اليومي ($fullCode)")
            }.onFailure {
                _operationError.value = "حفظ سجل الدعوات متاح للأمين فقط"
            }
        }
    }

    fun respondToInvitation(invitation: Invitation, accept: Boolean) {
        val group = uiState.value.group ?: return
        viewModelScope.launch {
            repository.respondToInvitation(
                groupId = group.inviteCode,
                invitationId = invitation.id,
                accept = accept,
                memberUids = group.memberUids
            ).onSuccess {
                showBanner(if (accept) "تم قبول الدعوة" else "تم رفض الدعوة")
            }.onFailure {
                _operationError.value = it.localizedMessage ?: "تعذر تحديث حالة الدعوة"
            }
        }
    }

    fun deleteInvitation(invitation: Invitation) {
        val group = uiState.value.group ?: return
        viewModelScope.launch {
            repository.deleteInvitation(group.inviteCode, invitation.id)
                .onSuccess { showBanner("تم حذف الدعوة") }
                .onFailure { _operationError.value = "الحذف متاح للأمين فقط" }
        }
    }

    companion object {
        private const val TAG = "KamViewModel"

        fun currentYearMonth(): String {
            return SaturdayCalendarHelper.currentYearMonth()
        }

        fun currentYearWeek(): String {
            return SaturdayCalendarHelper.currentWeekKey()
        }

        fun buildInitialStateFromCache(
            cached: CachedClassroomSnapshot?,
            localPrefs: LocalUserPreferences?,
            currentUserId: String
        ): KamScreenState {
            val monthKey = currentYearMonth()
            val weekKey = currentYearWeek()
            if (cached == null) {
                return KamScreenState(
                    isLoading = true,
                    selectedMonthKey = monthKey,
                    selectedWeekKey = weekKey,
                    currentWeekKey = weekKey
                )
            }
            val effectiveProfile = cached.userProfile.let { base ->
                if (localPrefs != null) {
                    base.copy(
                        enabledCards = localPrefs.enabledCards,
                        collapsedBlockIds = localPrefs.collapsedBlockIds,
                        hiddenBlockIds = localPrefs.hiddenBlockIds,
                        themeId = localPrefs.themeId,
                        uiScaleMode = localPrefs.uiScaleMode,
                        uiScaleFactor = localPrefs.uiScaleFactor.toDouble(),
                        showFloatingAddButton = localPrefs.showFloatingAddButton
                    )
                } else {
                    base
                }
            }
            val activeGroup = cached.group
            return KamScreenState(
                isLoading = false,
                userProfile = effectiveProfile,
                group = activeGroup,
                userGroups = cached.userGroups,
                isAdmin = activeGroup?.adminId == currentUserId,
                blocks = if (activeGroup == null) emptyList() else cached.blocks,
                students = if (activeGroup == null) emptyList() else cached.students,
                cardEvents = if (activeGroup == null) emptyList() else cached.cardEvents.filter { it.monthKey == monthKey },
                monthlyBalances = if (activeGroup == null) emptyList() else cached.monthlyBalances.filter { it.monthKey == monthKey },
                allGroupCardEvents = if (activeGroup == null) emptyList() else cached.cardEvents,
                allGroupMonthlyBalances = if (activeGroup == null) emptyList() else cached.monthlyBalances,
                selectedMonthKey = monthKey,
                selectedWeekKey = weekKey,
                currentWeekKey = weekKey
            )
        }

        /**
         * Orders all students across the group (ordered Servant Blocks first, then Unassigned)
         * and assigns unified sequential numbers `1, 2, 3, 4...` so any move or reorder
         * immediately updates the student's number without leaving static gaps.
         */
        fun computeSequentiallyNumberedStudents(
            rawStudents: List<Student>,
            blocks: List<ServantBlock>
        ): List<Student> {
            if (rawStudents.isEmpty()) return emptyList()
            val sortedBlocks = blocks.sortedWith(compareBy<ServantBlock> { it.sortOrder }.thenBy { it.id })
            val validBlockIds = sortedBlocks.map { it.id }.toSet()
            val studentComparator = compareBy<Student> { it.effectivePosition }
                .thenBy { it.sortOrder }
                .thenBy { it.studentNumber }
                .thenBy { it.id }

            val orderedList = buildList(rawStudents.size) {
                sortedBlocks.forEach { block ->
                    val inBlock = rawStudents
                        .filter { it.blockId == block.id }
                        .sortedWith(studentComparator)
                    addAll(inBlock)
                }
                val unassigned = rawStudents
                    .filter { it.blockId.isBlank() || it.blockId !in validBlockIds }
                    .sortedWith(studentComparator)
                addAll(unassigned)
            }

            return orderedList.mapIndexed { index, student ->
                val seqNumber = (index + 1).toLong()
                val stablePosition = if (student.position != 0.0) student.position else student.effectivePosition
                if (student.studentNumber == seqNumber && student.position == stablePosition) {
                    student
                } else {
                    student.copy(studentNumber = seqNumber, position = stablePosition)
                }
            }
        }

        fun buildInvitationShareText(
            groupName: String,
            inviteCode: String,
            inviteeName: String = ""
        ): String {
            val greeting = if (inviteeName.isNotBlank()) "أهلاً $inviteeName،\n" else "أهلاً بك،\n"
            return buildString {
                append(greeting)
                append("تمت دعوتك للانضمام كخادم في مجموعة «$groupName» على تطبيق نجم الكارت v1.6.\n\n")
                append("🔑 رمز الدخول اليومي للمجموعة (صالح لمدة ٢٤ ساعة فقط): $inviteCode\n\n")
                append("📲 رابط تحميل أحدث نسخة من التطبيق:\nhttps://github.com/Kett45/Y/raw/main/releases/kAM-Lite.apk\n\n")
                append("خطوات الانضمام:\n")
                append("1. افتح التطبيق واختر «أنا خادم (لدي رمز)».\n")
                append("2. اكتب اسمك وأدخل الرمز الكامل: $inviteCode\n")
                append("وستظهر لك قائمة أطفال وبلوكات المجموعة المشتركة فوراً.")
            }
        }

        fun observeNetworkConnectivity(context: Context): Flow<Boolean> = callbackFlow {
            val appContext = context.applicationContext
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) {
                trySend(true)
                close()
                return@callbackFlow
            }

            fun checkActiveNetwork(): Boolean {
                val active = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(active) ?: return false
                return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }

            trySend(checkActiveNetwork())

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(true)
                }

                override fun onLost(network: Network) {
                    trySend(checkActiveNetwork())
                }
            }

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            try {
                cm.registerNetworkCallback(request, callback)
            } catch (e: Exception) {
                trySend(true)
            }

            awaitClose {
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (_: Exception) {
                }
            }
        }.distinctUntilChanged()

        @Volatile
        var historicalRangeFetcher: (suspend (groupId: String, startMillis: Long, endMillis: Long) -> List<CardEvent>)? = null
    }
}
