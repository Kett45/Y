package com.example.ui.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class BulkImportLinePreview(
    val rawName: String,
    val normalizedName: String,
    val duplicateInPaste: Boolean,
    val existingSimilarStudentName: String?
)

data class StudentCardTypeBreakdown(
    val cardType: CardType,
    val count: Int,
    val points: Long
)

data class StudentDetailedLeaderboardEntry(
    val student: Student,
    val rank: Int,
    val totalPoints: Long,
    val totalCardsCount: Int,
    val attendanceCount: Int,
    val attendancePoints: Long,
    val breakdownByCard: List<StudentCardTypeBreakdown>
)

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
    val previousGroupId: String = ""
) {
    val remainingStudentCapacity: Int
        get() = (MAX_STUDENTS_PER_GROUP - students.size).coerceAtLeast(0)

    val canReturnToPreviousGroup: Boolean
        get() = previousGroupId.isNotBlank() ||
            !userProfile?.joinedGroupIds.isNullOrEmpty() ||
            userGroups.isNotEmpty()

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

    private val monthlyPointsByStudentId: Map<String, Long> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildMap(students.size + monthlyEventsByStudentId.size + monthlyBalanceByStudentId.size) {
            monthlyBalanceByStudentId.forEach { (stuId, pts) ->
                put(stuId, pts)
            }
            monthlyEventsByStudentId.forEach { (stuId, stuEvents) ->
                put(stuId, stuEvents.sumOf { it.points })
            }
        }
    }

    private val displayedEventsByStudentId: Map<String, List<CardEvent>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        displayedCardEvents.groupBy { it.studentId }
    }

    private val displayedPointsByStudentId: Map<String, Long> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (!selectedWeekKey.isNullOrBlank()) {
            displayedEventsByStudentId.mapValues { (_, evs) -> evs.sumOf { it.points } }
        } else {
            monthlyPointsByStudentId
        }
    }

    private val displayedCardCountsByStudentAndType: Map<Pair<String, String>, Int> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val counts = HashMap<Pair<String, String>, Int>(displayedCardEvents.size.coerceAtLeast(16))
        displayedCardEvents.forEach { ev ->
            val key = ev.studentId to ev.cardType
            counts[key] = (counts[key] ?: 0) + 1
        }
        counts
    }

    /**
     * Detects duplicate student records with the same normalized Arabic name inside the active group
     * (from migration or simultaneous offline additions) so the user can compare and decide manually.
     */
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

    /**
     * Detects duplicate cards where the SAME child received the SAME card type in the SAME week
     * from 2 or more DIFFERENT servants.
     */
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

    fun pointsForStudentInSelectedMonth(studentId: String): Long {
        return monthlyPointsByStudentId[studentId] ?: 0L
    }

    fun pointsForStudentInDisplayedScope(studentId: String): Long {
        return displayedPointsByStudentId[studentId] ?: 0L
    }

    fun cardCountForStudent(studentId: String, cardType: CardType): Int {
        return displayedCardCountsByStudentAndType[studentId to cardType.id] ?: 0
    }

    fun eventsForStudent(studentId: String): List<CardEvent> {
        return displayedEventsByStudentId[studentId] ?: emptyList()
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
    fun computeClassLeaderboardEntries(
        selectedMonths: Set<String> = setOf(selectedMonthKey),
        selectedWeekOverride: String? = null
    ): List<StudentDetailedLeaderboardEntry> {
        if (students.isEmpty()) return emptyList()
        val allEvents = effectiveAllCardEvents
        val allBalances = effectiveAllMonthlyBalances
        val activeCards = enabledCardTypes.ifEmpty { CardType.entries }
        val targetMonths = selectedMonths.filter { it.isNotBlank() }.toSet().ifEmpty {
            if (selectedMonthKey.isNotBlank()) setOf(selectedMonthKey) else emptySet()
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

        return unsorted
            .sortedWith(
                compareByDescending<StudentDetailedLeaderboardEntry> { it.totalPoints }
                    .thenByDescending { it.attendancePoints }
                    .thenByDescending { it.totalCardsCount }
                    .thenBy { it.student.studentNumber }
            )
            .mapIndexed { idx, entry ->
                entry.copy(rank = idx + 1)
            }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
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

    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    // In-memory debounce map to prevent accidental rapid double-taps (< 700ms) on the same student + card
    private val recentTapTimestamps = mutableMapOf<String, Long>()
    private var lastMigratedGroupKey: String = ""
    private var lastSyncedSequenceSignature: String = ""
    private var lastSavedCacheSignature: String = ""
    private var lastAutoRotatedGroupKey: String = ""
    private var lastEjectedBannedGroupId: String = ""
    private var hasSeededPrefsFromCloud: Boolean = false
    private var bannerJob: Job? = null

    init {
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
                .catch { err ->
                    Log.w(TAG, "Error observing allMonthlyBalances", err)
                    emit(cachedBalances)
                }
        }
    }

    val uiState: StateFlow<KamScreenState> = combine(
        combine(userProfileFlow, groupFlow, userGroupsFlow, blocksFlow, _localPrefsOverride) { profile, group, userGroups, blocks, localPrefs ->
            arrayOf<Any?>(profile, group, userGroups, blocks, localPrefs)
        },
        combine(studentsFlow, allCardEventsFlow, allMonthlyBalancesFlow, invitationsFlow) { students, allEvents, allBalances, invites ->
            arrayOf<Any?>(students, allEvents, allBalances, invites)
        },
        combine(
            combine(_selectedMonthKey, _selectedWeekKey, _searchQuery, _dismissedDuplicateNames) { month, week, query, dismissed ->
                arrayOf<Any?>(month, week, query, dismissed)
            },
            combine(isOnlineFlow, hasPendingWritesFlow, _actionBanner, _operationError) { online, pending, banner, error ->
                arrayOf<Any?>(online, pending, banner, error)
            }
        ) { filterArr, netArr ->
            arrayOf<Any?>(
                filterArr[0], filterArr[1], filterArr[2], filterArr[3],
                netArr[0], netArr[1], netArr[2], netArr[3]
            )
        }
    ) { groupArr, dataArr, metaArr ->
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
        val rawStudents = dataArr[0] as List<Student>
        @Suppress("UNCHECKED_CAST")
        val allEvents = dataArr[1] as List<CardEvent>
        @Suppress("UNCHECKED_CAST")
        val allBalances = dataArr[2] as List<MonthlyBalance>
        @Suppress("UNCHECKED_CAST")
        val invitations = dataArr[3] as List<Invitation>

        val selectedMonth = metaArr[0] as String
        val selectedWeek = metaArr[1] as String?
        val searchQuery = metaArr[2] as String
        @Suppress("UNCHECKED_CAST")
        val dismissedDuplicates = metaArr[3] as Set<String>
        val isOnline = metaArr[4] as Boolean
        val hasPendingWrites = metaArr[5] as Boolean
        val banner = metaArr[6] as String?
        val error = metaArr[7] as String?

        val events = allEvents.filter { it.monthKey == selectedMonth }
        val balances = allBalances.filter { it.monthKey == selectedMonth }

        // If the current user was removed & banned from the active group, immediately eject them and show the required message
        val isBannedOrRemovedFromGroup = group != null && (
            currentUserId in group.bannedUids ||
                (currentUserId !in group.memberUids && group.adminId != currentUserId)
            )
        if (isBannedOrRemovedFromGroup && group != null) {
            if (lastEjectedBannedGroupId != group.inviteCode) {
                lastEjectedBannedGroupId = group.inviteCode
                _operationError.value = "تم إزالتك من هذه المجموعة"
                val remainingJoined = profile?.joinedGroupIds.orEmpty().filter { it != group.inviteCode }
                viewModelScope.launch {
                    repository.updateUserGroup("", remainingJoined)
                }
            }
        }

        val activeGroup = if (isBannedOrRemovedFromGroup) null else group

        // Auto-renew the 24-hour daily invite code when the Admin is online and the 24-hour window has expired
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

        // Compute global sequential student numbering (1, 2, 3, 4...) across the entire group
        val sequentiallyNumberedStudents = computeSequentiallyNumberedStudents(rawStudents, blocks)

        if (activeGroup != null && currentUserId in activeGroup.memberUids && rawStudents.isNotEmpty()) {
            val rawById = rawStudents.associateBy { it.id }
            val mismatchedNumbers = sequentiallyNumberedStudents.mapNotNull { numberedStu ->
                val rawStu = rawById[numberedStu.id]
                if (rawStu != null && rawStu.studentNumber != numberedStu.studentNumber) {
                    numberedStu.id to numberedStu.studentNumber
                } else {
                    null
                }
            }.toMap()
            val seqSignature = "${activeGroup.inviteCode}:${sequentiallyNumberedStudents.joinToString(",") { "${it.id}=${it.studentNumber}" }}"
            if (mismatchedNumbers.isNotEmpty() && seqSignature != lastSyncedSequenceSignature) {
                lastSyncedSequenceSignature = seqSignature
                viewModelScope.launch {
                    repository.syncStudentSequentialNumbers(
                        groupId = activeGroup.inviteCode,
                        numberUpdates = mismatchedNumbers,
                        waitForServerAck = false
                    )
                }
            }
        }

        // Save classroom snapshot locally on device whenever data changes so next app launch is 0ms
        if (profile != null) {
            val cacheSig = "${profile.groupId}_${activeGroup?.inviteCode}_${blocks.size}_${sequentiallyNumberedStudents.size}_${allEvents.size}_${profile.enabledCards.joinToString(",")}_${profile.collapsedBlockIds.joinToString(",")}_${profile.hiddenBlockIds.joinToString(",")}_${profile.themeId}_${profile.uiScaleMode}_${profile.uiScaleFactor}_${profile.showFloatingAddButton}"
            if (cacheSig != lastSavedCacheSignature) {
                lastSavedCacheSignature = cacheSig
                LocalClassroomCache.saveClassroomSnapshot(
                    userId = currentUserId,
                    userProfile = profile,
                    group = activeGroup,
                    userGroups = userGroups,
                    blocks = blocks,
                    students = sequentiallyNumberedStudents,
                    cardEvents = allEvents,
                    monthlyBalances = allBalances
                )
            }
        }

        val fallbackPrevGroup = _previousGroupId.value.ifBlank {
            profile?.joinedGroupIds?.lastOrNull { it.isNotBlank() && it != lastEjectedBannedGroupId }
                ?: userGroups.firstOrNull { it.inviteCode != lastEjectedBannedGroupId }?.inviteCode.orEmpty()
        }

        KamScreenState(
            isLoading = profile == null && error == null,
            errorMessage = error ?: _operationError.value,
            actionBannerMessage = banner,
            userProfile = profile,
            group = activeGroup,
            userGroups = userGroups.filter { currentUserId !in it.bannedUids },
            isAdmin = activeGroup?.adminId == currentUserId,
            blocks = if (activeGroup == null) emptyList() else blocks,
            students = if (activeGroup == null) emptyList() else sequentiallyNumberedStudents,
            cardEvents = if (activeGroup == null) emptyList() else events,
            monthlyBalances = if (activeGroup == null) emptyList() else balances,
            allGroupCardEvents = if (activeGroup == null) emptyList() else allEvents,
            allGroupMonthlyBalances = if (activeGroup == null) emptyList() else allBalances,
            invitations = if (activeGroup == null) emptyList() else invitations,
            selectedMonthKey = selectedMonth,
            selectedWeekKey = selectedWeek,
            currentWeekKey = currentYearWeek(),
            searchQuery = searchQuery,
            isOnline = isOnline,
            hasPendingWrites = hasPendingWrites,
            dismissedDuplicateNames = dismissedDuplicates,
            previousGroupId = fallbackPrevGroup
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
        lastSyncedSequenceSignature = ""
        lastAutoRotatedGroupKey = ""
        lastEjectedBannedGroupId = ""
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
        val newPosition = maxPositionInTarget + 1000.0
        viewModelScope.launch {
            repository.moveStudentToBlock(
                groupId = group.inviteCode,
                studentId = student.id,
                targetBlockId = targetBlockId,
                newSortOrder = maxSortInTarget + 1L,
                newPosition = newPosition,
                memberUids = group.memberUids,
                waitForServerAck = false
            ).onSuccess {
                val targetName = if (targetBlockId.isBlank()) {
                    "بدون بلوك"
                } else {
                    state.blocks.find { it.id == targetBlockId }?.servantName ?: "البلوك"
                }
                showBanner("تم نقل '${student.name}' إلى $targetName وتحديث التسلسل")
            }.onFailure {
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
        viewModelScope.launch {
            repository.updateStudentAvatar(
                groupId = group.inviteCode,
                studentId = student.id,
                avatarBase64 = avatarBase64,
                memberUids = group.memberUids,
                waitForServerAck = false
            ).onSuccess {
                if (avatarBase64.isBlank()) {
                    showBanner("تمت إزالة صورة '${student.name}' والعودة للحروف")
                } else if (!state.isOnline) {
                    showBanner("تم حفظ صورة '${student.name}' محلياً وستتزامن تلقائياً")
                } else {
                    showBanner("تم تحديث صورة '${student.name}'")
                }
            }.onFailure {
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
                recentTapTimestamps.remove(tapKey)
                _operationError.value = err.localizedMessage ?: "تعذر تسجيل الكارت"
            }
        }
    }

    fun undoCardEvent(event: CardEvent) {
        val state = uiState.value
        val group = state.group ?: return
        viewModelScope.launch {
            repository.undoCardEvent(
                groupId = group.inviteCode,
                event = event,
                waitForServerAck = false
            ).onSuccess {
                showBanner("تم إلغاء كارت من سجل '${event.studentName}'")
            }.onFailure {
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
                append("تمت دعوتك للانضمام كخادم في مجموعة «$groupName» على تطبيق كارت المذبح (kAM Lite).\n\n")
                append("🔑 رمز الدخول اليومي للمجموعة (صالح لمدة ٢٤ ساعة فقط): $inviteCode\n\n")
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
    }
}
