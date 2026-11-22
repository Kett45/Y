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
import com.example.data.model.AttendanceRecord
import com.example.data.model.CardEvent
import com.example.data.model.CardType
import com.example.data.model.ChatMessageType
import com.example.data.model.ChildNote
import com.example.data.model.ChildNoteType
import com.example.data.model.CustomCardConfig
import com.example.data.model.CustomCardFrequency
import com.example.data.model.DuplicateCardAlert
import com.example.data.model.DuplicateStudentNameGroup
import com.example.data.model.Group
import com.example.data.model.Invitation
import com.example.data.model.MAX_STUDENTS_PER_GROUP
import com.example.data.model.MonthlyBalance
import com.example.data.model.NotificationCategory
import com.example.data.model.PhoneNumberFormatHelper
import com.example.data.model.SaturdayCalendarHelper
import com.example.data.model.ServantBlock
import com.example.data.model.ServantMemberProfile
import com.example.data.model.Student
import com.example.data.model.UserProfile
import com.example.data.model.VisitationStatus
import com.example.data.repository.ChatRepository
import com.example.data.repository.KamRepository
import com.example.ui.components.StudentAvatarImageHelper
import com.example.util.KamPerformanceTracker
import com.google.firebase.firestore.FirebaseFirestoreException
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
    val dismissedDuplicateNames: Set<String>,
    val memberProfiles: List<ServantMemberProfile> = emptyList(),
    val customCardEnabled: Boolean = false,
    private val reusedDuplicatesByName: List<DuplicateStudentNameGroup>? = null
) {
    val membersByUid: Map<String, ServantMemberProfile> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (memberProfiles.isEmpty()) {
            emptyMap()
        } else {
            memberProfiles.associateBy { it.effectiveUid }
        }
    }
    val enabledCardTypes: List<CardType> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val ids = userProfile?.enabledCards ?: CardType.defaultEnabledIds
        CardType.entries.filter { card ->
            if (card == CardType.CUSTOM_ELDER) {
                customCardEnabled
            } else {
                card.id in ids
            }
        }
    }

    val enabledCardsMask: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
        KamScreenState.packEnabledCardsMask(enabledCardTypes)
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
        if (reusedDuplicatesByName != null) {
            reusedDuplicatesByName
        } else if (students.size < 2) {
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
        dismissed: Set<String>,
        members: List<ServantMemberProfile> = emptyList(),
        isCustomCardEnabled: Boolean = false
    ): Boolean {
        return userProfile === profile &&
            students === stuList &&
            cardEvents === evList &&
            monthlyBalances === balList &&
            selectedWeekKey == weekKey &&
            dismissedDuplicateNames === dismissed &&
            memberProfiles === members &&
            customCardEnabled == isCustomCardEnabled
    }

    fun prewarmHomeEssential() {
        membersByUid
        enabledCardTypes
        enabledCardsMask
        collapsedBlockIds
        hiddenBlockIds
        displayedPointsByStudentId
        packedCardCountsByStudentId
        duplicateStudentsByName
    }

    fun prewarmAll() {
        prewarmHomeEssential()
        displayedCardEvents
        monthlyPointsByStudentId
        displayedCardCountsByStudentAndType
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
    val isPrimaryAdmin: Boolean = false,
    val isAssistantAdmin: Boolean = false,
    val blocks: List<ServantBlock> = emptyList(),
    val memberProfiles: List<ServantMemberProfile> = emptyList(),
    val students: List<Student> = emptyList(),
    val cardEvents: List<CardEvent> = emptyList(),
    val monthlyBalances: List<MonthlyBalance> = emptyList(),
    val allGroupCardEvents: List<CardEvent> = emptyList(),
    val allGroupMonthlyBalances: List<MonthlyBalance> = emptyList(),
    val invitations: List<Invitation> = emptyList(),
    val childNotes: List<ChildNote> = emptyList(),
    val allAttendanceRecords: List<AttendanceRecord> = emptyList(),
    val selectedMonthKey: String = "",
    val selectedWeekKey: String? = null,
    val currentWeekKey: String = "",
    val searchQuery: String = "",
    val isOnline: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasPendingWrites: Boolean = false,
    val dismissedDuplicateNames: Set<String> = emptySet(),
    val previousGroupId: String = "",
    val shouldPromptPhoneOnboarding: Boolean = false,
    val ownPrimaryPhone: String = "",
    val ownSecondaryPhone: String = "",
    val ownPhoneHidden: Boolean = false,
    val ownAvatarHash: String = "",
    val precomputedIndices: PrecomputedClassroomIndices? = null
) {
    val customCardConfig: CustomCardConfig
        get() = group?.customCardConfig ?: CustomCardConfig.DEFAULT

    val activeAttendanceWeekKey: String
        get() = selectedWeekKey?.takeIf { it.isNotBlank() }
            ?: currentWeekKey.ifBlank { SaturdayCalendarHelper.currentWeekKey() }

    val activeWeekAttendanceByStudentId: Map<String, AttendanceRecord> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (allAttendanceRecords.isEmpty()) {
            emptyMap()
        } else {
            val targetWeek = activeAttendanceWeekKey
            allAttendanceRecords
                .filter { it.weekKey == targetWeek }
                .associateBy { it.studentId }
        }
    }

    fun attendanceRecordsForSelection(
        selectedMonthKeys: Set<String>,
        selectedWeekKeys: Set<String>
    ): List<AttendanceRecord> {
        if (allAttendanceRecords.isEmpty()) return emptyList()
        val targetMonths = selectedMonthKeys.filter { it.isNotBlank() }.toSet()
        val targetWeeks = selectedWeekKeys.filter { it.isNotBlank() }.toSet()
        val effectiveMonths = if (targetMonths.isEmpty() && targetWeeks.isEmpty()) {
            setOf(selectedMonthKey.ifBlank { SaturdayCalendarHelper.currentYearMonth() })
        } else {
            targetMonths
        }
        return allAttendanceRecords.filter { rec ->
            val wk = rec.weekKey.trim()
            val mk = wk.substringBefore("-W", "")
            (wk.isNotBlank() && wk in targetWeeks) || (mk.isNotBlank() && mk in effectiveMonths)
        }
    }

    private val indices: PrecomputedClassroomIndices by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val existing = precomputedIndices
        val isCustomEnabled = group?.customCardEnabled == true
        if (existing != null && existing.matchesState(
                profile = userProfile,
                stuList = students,
                evList = cardEvents,
                balList = monthlyBalances,
                weekKey = selectedWeekKey,
                dismissed = dismissedDuplicateNames,
                members = memberProfiles,
                isCustomCardEnabled = isCustomEnabled
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
                dismissedDuplicateNames = dismissedDuplicateNames,
                memberProfiles = memberProfiles,
                customCardEnabled = isCustomEnabled
            )
        }
    }

    val servantAliases: Map<String, String>
        get() = userProfile?.servantAliases ?: emptyMap()

    fun resolvedServantName(servantUid: String, fallbackName: String = "خادم"): String {
        if (servantUid.isNotBlank()) {
            val alias = servantAliases[servantUid]?.trim()?.takeIf { it.isNotBlank() }
            if (alias != null) return alias
            val memberName = memberProfileForServant(servantUid)?.displayName?.trim()?.takeIf { it.isNotBlank() }
            if (memberName != null) return memberName
        }
        return fallbackName.ifBlank { "خادم" }
    }

    fun resolvedBlockOwnerName(block: ServantBlock): String {
        val explicitBlockName = block.servantName.trim()
        if (block.servantId.isNotBlank()) {
            val alias = servantAliases[block.servantId]?.trim()?.takeIf { it.isNotBlank() }
            if (alias != null) return alias
            if (explicitBlockName.isNotBlank()) return explicitBlockName
            val memberName = memberProfileForServant(block.servantId)?.displayName?.trim()?.takeIf { it.isNotBlank() }
            if (memberName != null) return memberName
        }
        return explicitBlockName.ifBlank { "خادم" }
    }

    fun memberProfileForBlock(block: ServantBlock): ServantMemberProfile? {
        val uid = block.servantId.trim()
        if (uid.isBlank()) return null
        return memberProfileForServant(uid)
    }

    val allGroupServantCandidates: List<ServantMemberProfile>
        get() {
            val byUid = LinkedHashMap<String, ServantMemberProfile>()
            memberProfiles.forEach { profile ->
                val uid = profile.effectiveUid.trim()
                if (uid.isNotBlank()) {
                    byUid[uid] = profile
                }
            }
            val grp = group
            if (grp != null && grp.adminId.isNotBlank() && grp.adminId !in byUid) {
                byUid[grp.adminId] = ServantMemberProfile(
                    id = grp.adminId,
                    uid = grp.adminId,
                    displayName = grp.adminName.ifBlank { "الأمين" },
                    groupId = grp.inviteCode
                )
            }
            blocks.forEach { blk ->
                val bUid = blk.servantId.trim()
                if (bUid.isNotBlank() && bUid !in byUid) {
                    byUid[bUid] = ServantMemberProfile(
                        id = bUid,
                        uid = bUid,
                        displayName = blk.servantName.ifBlank { "خادم" },
                        groupId = blk.groupId
                    )
                }
            }
            grp?.memberUids?.forEachIndexed { idx, uid ->
                val cleanUid = uid.trim()
                if (cleanUid.isNotBlank() && cleanUid !in byUid && cleanUid !in grp.bannedUids) {
                    val fallback = if (cleanUid == userProfile?.userId) {
                        userProfile.displayName.ifBlank { "خادم ${idx + 1}" }
                    } else {
                        "خادم مشترك (${idx + 1})"
                    }
                    byUid[cleanUid] = ServantMemberProfile(
                        id = cleanUid,
                        uid = cleanUid,
                        displayName = fallback,
                        groupId = grp.inviteCode
                    )
                }
            }
            return byUid.values.toList()
        }

    fun blockNameForStudent(student: Student): String {
        val blk = blocks.firstOrNull { it.id == student.blockId }
        return if (blk != null && blk.servantName.isNotBlank()) {
            val resolvedServant = resolvedBlockOwnerName(blk)
            "بلوك $resolvedServant"
        } else {
            "بدون بلوك"
        }
    }

    fun notesForStudent(studentId: String, type: ChildNoteType? = null): List<ChildNote> {
        if (studentId.isBlank()) return emptyList()
        return childNotes.filter { note ->
            note.studentId == studentId && (type == null || note.noteType == type)
        }
    }

    fun memberProfileForServant(servantUid: String): ServantMemberProfile? {
        if (servantUid.isBlank()) return null
        return indices.membersByUid[servantUid]
    }

    val remainingStudentCapacity: Int
        get() = (MAX_STUDENTS_PER_GROUP - students.size).coerceAtLeast(0)

    val canReturnToPreviousGroup: Boolean
        get() = previousGroupId.isNotBlank() ||
            !userProfile?.joinedGroupIds.isNullOrEmpty() ||
            userGroups.isNotEmpty()

    val enabledCardTypes: List<CardType>
        get() = indices.enabledCardTypes

    val enabledCardsMask: Int
        get() = indices.enabledCardsMask

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
        const val ALL_CARDS_ENABLED_MASK: Int = 0xFF

        fun packEnabledCardsMask(cards: List<CardType>): Int {
            var mask = 0
            for (i in 0 until cards.size) {
                mask = mask or (1 shl cards[i].ordinal)
            }
            return mask
        }

        fun isCardEnabledInMask(mask: Int, cardType: CardType): Boolean {
            return (mask and (1 shl cardType.ordinal)) != 0
        }

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
     * Computes the full-class leaderboard and per-card breakdown for ALL students in the classroom
     * across either a single week ([selectedWeekOverride] != null) or one or more selected months ([selectedMonths]).
     * Delegates to [computeClassLeaderboardEntriesForSelection] so all leaderboard calculations share a single path.
     */
    fun computeClassLeaderboardEntries(
        selectedMonths: Set<String> = setOf(selectedMonthKey),
        selectedWeekOverride: String? = null
    ): List<StudentDetailedLeaderboardEntry> {
        return if (!selectedWeekOverride.isNullOrBlank()) {
            computeClassLeaderboardEntriesForSelection(
                selectedMonthKeys = emptySet(),
                selectedWeekKeys = setOf(selectedWeekOverride)
            )
        } else {
            computeClassLeaderboardEntriesForSelection(
                selectedMonthKeys = selectedMonths,
                selectedWeekKeys = emptySet()
            )
        }
    }

    /**
     * Checks if the leaderboard for the multi-month and multi-week selection is already cached in memory.
     */
    fun isLeaderboardSelectionCached(
        selectedMonthKeys: Set<String>,
        selectedWeekKeys: Set<String>,
        extraHistoricalEventsCount: Int = 0
    ): Boolean {
        val targetMonths = selectedMonthKeys.filter { it.isNotBlank() }.toSortedSet()
        val targetWeeks = selectedWeekKeys.filter { it.isNotBlank() }.toSortedSet()
        val activeCards = enabledCardTypes.ifEmpty { CardType.entries }
        val cacheKey = "SEL|${targetMonths.joinToString(",")}|${targetWeeks.joinToString(",")}|${activeCards.size}|${effectiveAllCardEvents.size}|$extraHistoricalEventsCount|${students.size}"
        return indices.leaderboardCache.containsKey(cacheKey)
    }

    /**
     * Computes the full-class leaderboard for an arbitrary set of selected months and weeks
     * strictly from `cardEvents` (never from `monthlyBalances`).
     * - Breaks ties by: totalPoints DESC -> attendanceCount DESC (الأكثر حضوراً) -> attendancePoints DESC -> Arabic name ASC (أبجدياً) -> studentNumber ASC.
     * - Memoizes in [PrecomputedClassroomIndices.leaderboardCache] for instantaneous (0ms) response.
     */
    fun computeClassLeaderboardEntriesForSelection(
        selectedMonthKeys: Set<String>,
        selectedWeekKeys: Set<String>,
        extraHistoricalEvents: List<CardEvent> = emptyList()
    ): List<StudentDetailedLeaderboardEntry> {
        if (students.isEmpty()) return emptyList()
        val targetMonths = selectedMonthKeys.filter { it.isNotBlank() }.toSortedSet()
        val targetWeeks = selectedWeekKeys.filter { it.isNotBlank() }.toSortedSet()
        val effectiveMonths = if (targetMonths.isEmpty() && targetWeeks.isEmpty()) {
            sortedSetOf(selectedMonthKey.ifBlank { SaturdayCalendarHelper.currentYearMonth() })
        } else {
            targetMonths
        }
        val allEvents = effectiveAllCardEvents
        val hasCustomElderEvents = allEvents.any { it.cardType == CardType.CUSTOM_ELDER.id } ||
            extraHistoricalEvents.any { it.cardType == CardType.CUSTOM_ELDER.id }
        val activeCards = buildList {
            val base = enabledCardTypes.ifEmpty { CardType.entries.filter { it != CardType.CUSTOM_ELDER } }
            addAll(base)
            if ((customCardConfig.enabled || hasCustomElderEvents) && CardType.CUSTOM_ELDER !in this) {
                add(CardType.CUSTOM_ELDER)
            }
        }
        val cacheKey = "SEL|${effectiveMonths.joinToString(",")}|${targetWeeks.joinToString(",")}|${activeCards.size}|${allEvents.size}|${extraHistoricalEvents.size}|${students.size}|${customCardConfig.enabled}"
        indices.leaderboardCache[cacheKey]?.let { cached ->
            return cached
        }

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
            val matchesMonth = ev.monthKey.isNotBlank() && ev.monthKey in effectiveMonths
            val matchesWeek = ev.weekKey.isNotBlank() && ev.weekKey in targetWeeks
            if (matchesMonth || matchesWeek) {
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
                    .thenByDescending { it.attendanceCount }
                    .thenByDescending { it.attendancePoints }
                    .thenBy { ArabicInitialsHelper.normalizeArabicName(it.student.name) }
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
    val isOnlineFlow: Flow<Boolean> = flowOf(true),
    private val chatRepository: ChatRepository? = null
) : ViewModel() {

    @Volatile
    private var initialCachedSnapshot: CachedClassroomSnapshot? =
        LocalClassroomCache.getMemorySnapshot(currentUserId)

    private val _localPrefsOverride = MutableStateFlow<LocalUserPreferences?>(
        LocalClassroomCache.getMemoryUserPreferences(currentUserId)?.takeIf { it.updatedAtMillis > 0L }
    )
    @Volatile
    private var hasLoadedPrefsFromDisk: Boolean =
        LocalClassroomCache.getMemoryUserPreferences(currentUserId) != null

    private suspend fun resolveCachedSnapshot(): CachedClassroomSnapshot? {
        initialCachedSnapshot?.let { return it }
        return LocalClassroomCache.loadSnapshotFromDiskAsync(currentUserId)?.also {
            initialCachedSnapshot = it
        }
    }

    private val _selectedMonthKey = MutableStateFlow(currentYearMonth())
    private val _selectedWeekKey = MutableStateFlow<String?>(currentYearWeek())
    private val _searchQuery = MutableStateFlow("")
    private val _actionBanner = MutableStateFlow<String?>(null)
    private val _operationError = MutableStateFlow<String?>(null)
    private val _isBusy = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _dismissedDuplicateNames = MutableStateFlow<Set<String>>(emptySet())
    private val _previousGroupId = MutableStateFlow("")

    // Instant 0ms optimistic UI overlays applied before Firestore local cache round-trips
    private val _optimisticAddedEvents = MutableStateFlow<Map<String, CardEvent>>(emptyMap())
    private val _optimisticRemovedEventIds = MutableStateFlow<Set<String>>(emptySet())
    private val _optimisticStudentOverrides = MutableStateFlow<Map<String, Student>>(emptyMap())
    private val _optimisticMemberOverrides = MutableStateFlow<Map<String, ServantMemberProfile>>(emptyMap())
    private val _optimisticGroupPolicyOverride = MutableStateFlow<Group?>(null)
    private val _optimisticChildNotes = MutableStateFlow<List<ChildNote>>(emptyList())
    private val _optimisticDeletedNoteIds = MutableStateFlow<Set<String>>(emptySet())
    private var policyMutationSeq: Long = 0L

    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    // In-memory debounce map to prevent accidental rapid double-taps (< 350ms) on the same student + card
    private val recentTapTimestamps = mutableMapOf<String, Long>()
    private var lastMigratedGroupKey: String = ""
    private var lastMigratedInlineAvatarsGroupKey: String = ""
    private var lastRepairedAdminBlocksGroupKey: String = ""
    private var lastSyncedMemberGroupKey: String = ""
    private var lastSavedCacheSignature: String = ""
    private var lastAutoRotatedGroupKey: String = ""
    private var lastEjectedBannedGroupId: String = ""
    private var hasSeededPrefsFromCloud: Boolean = false
    private var bannerJob: Job? = null
    @Volatile
    private var lastDupCheckStudents: List<Student>? = null
    @Volatile
    private var lastDupCheckDismissed: Set<String>? = null
    @Volatile
    private var cachedDuplicatesByName: List<DuplicateStudentNameGroup>? = null
    @Volatile
    private var lastSeqInputStudents: List<Student>? = null
    @Volatile
    private var lastSeqInputBlocks: List<ServantBlock>? = null
    @Volatile
    private var cachedSeqStudents: List<Student>? = null

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
        StudentAvatarImageHelper.cloudAvatarFetcher = { groupId, targetId ->
            if (targetId.startsWith("servant_") && !targetId.startsWith("servant_block_")) {
                repository.fetchServantAvatar(groupId, targetId.removePrefix("servant_"))
            } else {
                repository.fetchStudentAvatar(groupId, targetId)
            }
        }
        historicalRangeFetcher = { groupId, startMillis, endMillis ->
            repository.fetchCardEventsForDateRange(groupId, startMillis, endMillis).getOrDefault(emptyList())
        }
        viewModelScope.launch(Dispatchers.IO) {
            val loadedPrefs = LocalClassroomCache.loadUserPreferencesAsync(currentUserId)
            hasLoadedPrefsFromDisk = true
            if (_localPrefsOverride.value == null) {
                _localPrefsOverride.value = loadedPrefs
            }
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
                if (resolveCachedSnapshot() == null) {
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
                    activeGroupId = profile.groupId,
                    phoneOnboardingDone = existingLocal?.phoneOnboardingDone ?: false,
                    primaryPhone = existingLocal?.primaryPhone.orEmpty(),
                    secondaryPhone = existingLocal?.secondaryPhone.orEmpty(),
                    phoneHidden = existingLocal?.phoneHidden ?: false,
                    servantAvatarHash = existingLocal?.servantAvatarHash.orEmpty()
                )
                _localPrefsOverride.value = mergedPrefs
                LocalClassroomCache.saveUserPreferences(currentUserId, mergedPrefs)
            }
            profile
        }
        .onStart {
            resolveCachedSnapshot()?.userProfile?.let { emit(it) }
        }
        .catch { err ->
            Log.w(TAG, "[Step 4] Error observing userProfile: ${err.javaClass.simpleName} - ${err.message}", err)
            val snap = resolveCachedSnapshot()
            if (snap == null) {
                _operationError.value = err.localizedMessage
            }
            emit(snap?.userProfile)
        }

    private val userGroupsFlow: Flow<List<Group>> = repository.observeUserGroups(currentUserId)
        .onStart { emit(resolveCachedSnapshot()?.userGroups.orEmpty()) }
        .catch { err ->
            Log.w(TAG, "Error observing userGroups", err)
            emit(resolveCachedSnapshot()?.userGroups.orEmpty())
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
            repository.observeGroup(groupId)
                .onStart {
                    val cachedGroup = resolveCachedSnapshot()?.group?.takeIf { it.inviteCode == groupId }
                    emit(cachedGroup)
                }
                .catch { err ->
                    Log.w(TAG, "[Step 4] Error observing group ($groupId): ${err.javaClass.simpleName} - ${err.message}", err)
                    emit(resolveCachedSnapshot()?.group?.takeIf { it.inviteCode == groupId })
                }
        }
    }

    private val blocksFlow: Flow<List<ServantBlock>> = activeGroupIdFlow.flatMapLatest { groupId ->
        if (groupId.isBlank()) {
            flowOf(emptyList())
        } else {
            repository.observeBlocks(groupId, currentUserId)
                .onStart {
                    val cachedBlocks = resolveCachedSnapshot()?.blocks.orEmpty().filter { it.groupId == groupId || it.groupId.isBlank() }
                    emit(cachedBlocks)
                }
                .debounceSubsequentCloudEmissions(200L)
                .catch { err ->
                    Log.w(TAG, "Error observing blocks", err)
                    val cachedBlocks = resolveCachedSnapshot()?.blocks.orEmpty().filter { it.groupId == groupId || it.groupId.isBlank() }
                    emit(cachedBlocks)
                }
        }
    }

    private val studentsFlow: Flow<List<Student>> = activeGroupIdFlow.flatMapLatest { groupId ->
        if (groupId.isBlank()) {
            flowOf(emptyList())
        } else {
            repository.observeStudents(groupId, currentUserId)
                .onStart {
                    val cachedStudents = resolveCachedSnapshot()?.students.orEmpty().filter { it.groupId == groupId || it.groupId.isBlank() }
                    emit(cachedStudents)
                }
                .debounceSubsequentCloudEmissions(200L)
                .catch { err ->
                    Log.w(TAG, "Error observing students", err)
                    val cachedStudents = resolveCachedSnapshot()?.students.orEmpty().filter { it.groupId == groupId || it.groupId.isBlank() }
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
            repository.observeAllCardEventsForGroup(groupId, currentUserId)
                .onStart {
                    val cachedEvents = resolveCachedSnapshot()?.cardEvents.orEmpty().filter { it.groupId == groupId || it.groupId.isBlank() }
                    emit(cachedEvents)
                }
                .debounceSubsequentCloudEmissions(200L)
                .catch { err ->
                    Log.w(TAG, "Error observing allCardEvents", err)
                    val cachedEvents = resolveCachedSnapshot()?.cardEvents.orEmpty().filter { it.groupId == groupId || it.groupId.isBlank() }
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
                .distinctUntilChanged()
        }
    }.distinctUntilChanged()

    private val allMonthlyBalancesFlow: Flow<List<MonthlyBalance>> = activeGroupIdFlow.flatMapLatest { groupId ->
        if (groupId.isBlank()) {
            flowOf(emptyList())
        } else {
            repository.observeAllMonthlyBalancesForGroup(groupId, currentUserId)
                .onStart {
                    val cachedBalances = resolveCachedSnapshot()?.monthlyBalances.orEmpty().filter { it.groupId == groupId || it.groupId.isBlank() }
                    emit(cachedBalances)
                }
                .debounceSubsequentCloudEmissions(200L)
                .catch { err ->
                    Log.w(TAG, "Error observing allMonthlyBalances", err)
                    val cachedBalances = resolveCachedSnapshot()?.monthlyBalances.orEmpty().filter { it.groupId == groupId || it.groupId.isBlank() }
                    emit(cachedBalances)
                }
        }
    }

    private val memberProfilesFlow: Flow<List<ServantMemberProfile>> = activeGroupIdFlow.flatMapLatest { groupId ->
        if (groupId.isBlank()) {
            flowOf(emptyList())
        } else {
            repository.observeGroupMembers(groupId)
                .onStart {
                    val cachedMembers = resolveCachedSnapshot()?.memberProfiles.orEmpty()
                        .filter { it.groupId == groupId || it.groupId.isBlank() }
                    emit(cachedMembers)
                }
                .debounceSubsequentCloudEmissions(200L)
                .catch { err ->
                    Log.w(TAG, "Error observing group members", err)
                    val cachedMembers = resolveCachedSnapshot()?.memberProfiles.orEmpty()
                        .filter { it.groupId == groupId || it.groupId.isBlank() }
                    emit(cachedMembers)
                }
        }
    }

    private val childNotesFlow: Flow<List<ChildNote>> = activeGroupIdFlow.flatMapLatest { groupId ->
        if (groupId.isBlank()) {
            flowOf(emptyList())
        } else {
            repository.observeChildNotes(groupId)
                .map { notes ->
                    LocalClassroomCache.saveChildNotes(groupId, notes)
                    notes
                }
                .onStart {
                    val cached = LocalClassroomCache.loadChildNotesAsync(groupId)
                    emit(cached)
                }
                .debounceSubsequentCloudEmissions(200L)
                .catch {
                    emit(LocalClassroomCache.getMemoryChildNotes(groupId))
                }
        }
    }

    private val allAttendanceFlow: Flow<List<AttendanceRecord>> = activeGroupIdFlow.flatMapLatest { groupId ->
        if (groupId.isBlank()) {
            flowOf(emptyList())
        } else {
            repository.observeAllGroupAttendance(groupId)
                .onStart { emit(emptyList()) }
                .debounceSubsequentCloudEmissions(180L)
                .catch { err ->
                    Log.w(TAG, "Error observing group attendance", err)
                    emit(emptyList())
                }
        }
    }

    private val coreClassroomStateFlow: Flow<KamScreenState> = combine(
        combine(userProfileFlow, groupFlow, userGroupsFlow, blocksFlow, _localPrefsOverride) { profile, group, userGroups, blocks, localPrefs ->
            arrayOf<Any?>(profile, group, userGroups, blocks, localPrefs)
        },
        combine(
            studentsFlow,
            allCardEventsFlow,
            allMonthlyBalancesFlow,
            combine(invitationsFlow, childNotesFlow) { inv, notes -> inv to notes },
            memberProfilesFlow
        ) { students, allEvents, allBalances, invitesAndNotes, members ->
            arrayOf<Any?>(students, allEvents, allBalances, invitesAndNotes, members)
        },
        combine(_selectedMonthKey, _selectedWeekKey, _dismissedDuplicateNames) { month, week, dismissed ->
            arrayOf<Any?>(month, week, dismissed)
        },
        combine(
            _optimisticAddedEvents,
            _optimisticRemovedEventIds,
            _optimisticStudentOverrides,
            _optimisticGroupPolicyOverride,
            combine(_optimisticMemberOverrides, _optimisticChildNotes, _optimisticDeletedNoteIds) { mem, notes, delNotes ->
                Triple(mem, notes, delNotes)
            }
        ) { optAdd, optRem, optStu, optGroup, optNotesTriple ->
            arrayOf<Any?>(optAdd, optRem, optStu, optGroup, optNotesTriple)
        }
    ) { groupArr, dataArr, filterArr, optArr ->
        val rawProfile = groupArr[0] as UserProfile?
        val cloudGroup = groupArr[1] as Group?
        @Suppress("UNCHECKED_CAST")
        val userGroups = groupArr[2] as List<Group>
        @Suppress("UNCHECKED_CAST")
        val blocks = groupArr[3] as List<ServantBlock>
        val localPrefs = groupArr[4] as LocalUserPreferences?
        val optimisticGroupOverride = optArr[3] as Group?
        val group = cloudGroup?.let { base ->
            if (optimisticGroupOverride != null && optimisticGroupOverride.inviteCode == base.inviteCode) {
                base.copy(
                    allowServantUndo = optimisticGroupOverride.allowServantUndo,
                    enforceWeeklyCardLimit = optimisticGroupOverride.enforceWeeklyCardLimit,
                    maxMonthlyPoints = optimisticGroupOverride.maxMonthlyPoints,
                    attendanceIsCardOnly = optimisticGroupOverride.attendanceIsCardOnly,
                    showLeaderboard = optimisticGroupOverride.showLeaderboard,
                    autoStudentNumber = optimisticGroupOverride.autoStudentNumber,
                    avatarBase64 = optimisticGroupOverride.avatarBase64.ifBlank { base.avatarBase64 },
                    avatarHash = optimisticGroupOverride.avatarHash.ifBlank { base.avatarHash },
                    customCardEnabled = optimisticGroupOverride.customCardEnabled,
                    customCardName = optimisticGroupOverride.customCardName,
                    customCardPoints = optimisticGroupOverride.customCardPoints,
                    customCardIcon = optimisticGroupOverride.customCardIcon,
                    customCardColorKey = optimisticGroupOverride.customCardColorKey,
                    customCardFrequency = optimisticGroupOverride.customCardFrequency
                )
            } else {
                base
            }
        }

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
        val invitesAndNotes = dataArr[3] as Pair<List<Invitation>, List<ChildNote>>
        val invitations = invitesAndNotes.first
        val cloudNotes = invitesAndNotes.second
        @Suppress("UNCHECKED_CAST")
        val cloudMembers = dataArr[4] as List<ServantMemberProfile>

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
        @Suppress("UNCHECKED_CAST")
        val optNotesTriple = optArr[4] as Triple<Map<String, ServantMemberProfile>, List<ChildNote>, Set<String>>
        val optimisticMembers = optNotesTriple.first
        val optimisticNotes = optNotesTriple.second
        val optimisticDeletedNoteIds = optNotesTriple.third

        val mergedMembers = if (optimisticMembers.isEmpty()) {
            cloudMembers
        } else {
            val map = LinkedHashMap<String, ServantMemberProfile>(cloudMembers.size + optimisticMembers.size)
            cloudMembers.forEach { m -> map[m.effectiveUid] = m }
            optimisticMembers.forEach { (uid, m) -> map[uid] = m }
            map.values.sortedBy { it.effectiveUid }
        }

        val mergedNotes = if (optimisticNotes.isEmpty() && optimisticDeletedNoteIds.isEmpty()) {
            cloudNotes
        } else {
            val byId = LinkedHashMap<String, ChildNote>()
            optimisticNotes.forEach { n -> if (n.id !in optimisticDeletedNoteIds) byId[n.id] = n }
            cloudNotes.forEach { n -> if (n.id !in optimisticDeletedNoteIds) byId.putIfAbsent(n.id, n) }
            byId.values.sortedByDescending { it.effectiveTimeMillis }
        }

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

        // Sync current servant's public member profile inside groups/{groupId}/members/{currentUserId}
        val ownCloudMember = mergedMembers.find { it.effectiveUid == currentUserId }
        val effectiveLocalPrefs = if (
            activeGroup != null &&
            currentUserId in activeGroup.memberUids &&
            ownCloudMember != null &&
            ownCloudMember.effectivePrimaryPhone.isNotBlank() &&
            localPrefs?.primaryPhone.isNullOrBlank() &&
            !(localPrefs?.phoneOnboardingDone ?: false)
        ) {
            val basePrefs = localPrefs ?: LocalUserPreferences()
            val syncedPrefs = basePrefs.copy(
                phoneOnboardingDone = true,
                primaryPhone = ownCloudMember.effectivePrimaryPhone,
                secondaryPhone = ownCloudMember.secondaryPhone,
                phoneHidden = !ownCloudMember.effectiveShowPhone,
                servantAvatarHash = ownCloudMember.avatarHash.ifBlank { basePrefs.servantAvatarHash }
            )
            LocalClassroomCache.saveUserPreferences(currentUserId, syncedPrefs)
            syncedPrefs
        } else {
            localPrefs
        }

        if (activeGroup != null && currentUserId in activeGroup.memberUids) {
            // Ensure groups/{groupId}/members/{currentUserId} exists or reflects local phone/avatar
            val syncMemberKey = "${activeGroup.inviteCode}_${effectiveLocalPrefs?.primaryPhone.orEmpty()}_${effectiveLocalPrefs?.secondaryPhone.orEmpty()}_${effectiveLocalPrefs?.phoneHidden ?: false}_${effectiveLocalPrefs?.servantAvatarHash.orEmpty()}"
            val needsMemberUpsert = ownCloudMember == null || (
                !effectiveLocalPrefs?.primaryPhone.isNullOrBlank() &&
                    ownCloudMember.effectivePrimaryPhone.isBlank()
                )
            if (needsMemberUpsert && syncMemberKey != lastSyncedMemberGroupKey) {
                lastSyncedMemberGroupKey = syncMemberKey
                val servantDisplayName = blocks.find { it.servantId == currentUserId }?.servantName
                    ?.takeIf { it.isNotBlank() }
                    ?: profile?.displayName?.takeIf { it.isNotBlank() }
                    ?: initialDisplayName.ifBlank { "خادم" }
                viewModelScope.launch(Dispatchers.IO) {
                    repository.upsertOwnMemberProfile(
                        groupId = activeGroup.inviteCode,
                        displayName = servantDisplayName,
                        primaryPhone = effectiveLocalPrefs?.primaryPhone?.takeIf { it.isNotBlank() },
                        secondaryPhone = effectiveLocalPrefs?.secondaryPhone?.takeIf { it.isNotBlank() },
                        phoneHidden = effectiveLocalPrefs?.phoneHidden,
                        avatarBase64 = null,
                        memberUids = activeGroup.memberUids,
                        existingProfile = ownCloudMember,
                        waitForServerAck = false
                    )
                }
            }
        }

        // Compute global sequential student numbering (1, 2, 3, 4...) across the entire group in memory,
        // reusing the prior list instance whenever rawStudents and blocks references are unchanged.
        val cachedSeq = cachedSeqStudents
        val sequentiallyNumberedStudents = if (
            cachedSeq != null &&
            lastSeqInputStudents === rawStudents &&
            lastSeqInputBlocks === blocks
        ) {
            cachedSeq
        } else {
            computeSequentiallyNumberedStudents(rawStudents, blocks).also { computed ->
                lastSeqInputStudents = rawStudents
                lastSeqInputBlocks = blocks
                cachedSeqStudents = computed
            }
        }

        // Save classroom snapshot locally on device (debounced 3000ms on background IO thread, without photos)
        if (profile != null) {
            val cacheSig = "${profile.groupId}_${activeGroup?.inviteCode}_${blocks.size}_${mergedMembers.size}_${sequentiallyNumberedStudents.size}_${cloudEvents.size}_${profile.enabledCards.size}_${profile.collapsedBlockIds.size}_${profile.hiddenBlockIds.size}_${profile.themeId}_${profile.uiScaleMode}_${profile.uiScaleFactor}_${profile.showFloatingAddButton}"
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
                    monthlyBalances = allBalances,
                    memberProfiles = mergedMembers
                )
            }
        }

        val fallbackPrevGroup = _previousGroupId.value.ifBlank {
            profile?.joinedGroupIds?.lastOrNull { it.isNotBlank() && it != lastEjectedBannedGroupId }
                ?: userGroups.firstOrNull { it.inviteCode != lastEjectedBannedGroupId }?.inviteCode.orEmpty()
        }

        val rawEffectiveBlocks = if (activeGroup == null) emptyList() else blocks
        val effectiveMembers = if (activeGroup == null) emptyList() else mergedMembers

        // Sanitize legacy blocks that accidentally received adminId when Admin created blocks for other servants
        val (effectiveBlocks, blocksNeedingCloudRepair) = sanitizeLegacyAdminBlocks(
            rawBlocks = rawEffectiveBlocks,
            activeGroup = activeGroup,
            members = effectiveMembers,
            profile = profile
        )
        if (
            activeGroup != null &&
            activeGroup.adminId == currentUserId &&
            blocksNeedingCloudRepair.isNotEmpty()
        ) {
            val repairSig = "${activeGroup.inviteCode}_${blocksNeedingCloudRepair.joinToString(",") { "${it.id}:${it.servantId}" }}"
            if (repairSig != lastRepairedAdminBlocksGroupKey) {
                lastRepairedAdminBlocksGroupKey = repairSig
                val snapGroup = activeGroup
                viewModelScope.launch(Dispatchers.IO) {
                    blocksNeedingCloudRepair.forEach { repairedBlock ->
                        repository.updateBlock(
                            groupId = snapGroup.inviteCode,
                            blockId = repairedBlock.id,
                            servantName = repairedBlock.servantName,
                            colorHex = repairedBlock.colorHex,
                            sortOrder = repairedBlock.sortOrder,
                            memberUids = snapGroup.memberUids,
                            servantId = repairedBlock.servantId,
                            waitForServerAck = false
                        )
                    }
                }
            }
        }

        val effectiveStudents = if (activeGroup == null) emptyList() else sequentiallyNumberedStudents
        val effectiveEvents = if (activeGroup == null) emptyList() else events
        val effectiveBalances = if (activeGroup == null) emptyList() else balances
        val effectiveAllEvents = if (activeGroup == null) emptyList() else allEvents
        val effectiveAllBalances = if (activeGroup == null) emptyList() else allBalances
        val effectiveInvitations = if (activeGroup == null) emptyList() else invitations

        val reusableDuplicates = if (
            lastDupCheckStudents === effectiveStudents &&
            lastDupCheckDismissed === dismissedDuplicates
        ) {
            cachedDuplicatesByName
        } else {
            null
        }

        val precomputed = PrecomputedClassroomIndices(
            userProfile = profile,
            students = effectiveStudents,
            cardEvents = effectiveEvents,
            monthlyBalances = effectiveBalances,
            selectedWeekKey = selectedWeek,
            dismissedDuplicateNames = dismissedDuplicates,
            memberProfiles = effectiveMembers,
            customCardEnabled = activeGroup?.customCardEnabled == true,
            reusedDuplicatesByName = reusableDuplicates
        ).also { indices ->
            indices.prewarmHomeEssential()
            lastDupCheckStudents = effectiveStudents
            lastDupCheckDismissed = dismissedDuplicates
            cachedDuplicatesByName = indices.duplicateStudentsByName
        }

        val effectiveOwnPrimary = effectiveLocalPrefs?.primaryPhone?.takeIf { it.isNotBlank() }
            ?: ownCloudMember?.effectivePrimaryPhone.orEmpty()
        val effectiveOwnSecondary = effectiveLocalPrefs?.secondaryPhone?.takeIf { it.isNotBlank() }
            ?: ownCloudMember?.secondaryPhone.orEmpty()
        val effectiveOwnHidden = false
        val effectiveOwnAvatarHash = effectiveLocalPrefs?.servantAvatarHash?.takeIf { it.isNotBlank() }
            ?: ownCloudMember?.effectiveAvatarKey.orEmpty()
        val onboardingDone = (effectiveLocalPrefs?.phoneOnboardingDone == true) ||
            effectiveOwnPrimary.isNotBlank() ||
            effectiveOwnSecondary.isNotBlank()

        val effectiveNotes = if (activeGroup == null) emptyList() else mergedNotes

        // Auto-report cross-servant Friday card conflicts to Group Chat once per (studentId, cardType, weekKey)
        if (activeGroup != null && precomputed.duplicateAlerts.isNotEmpty() && chatRepository != null) {
            val alertsToReport = precomputed.duplicateAlerts.filter { alert ->
                val conflictKey = "${alert.studentId}_${alert.cardType.id}_${alert.weekKey}"
                val triggeringServant = alert.events.firstOrNull()?.servantId.orEmpty()
                (triggeringServant == currentUserId || activeGroup.adminId == currentUserId) &&
                    !LocalClassroomCache.isConflictNotificationReported(activeGroup.inviteCode, conflictKey)
            }
            if (alertsToReport.isNotEmpty()) {
                val snapStudents = effectiveStudents
                val snapBlocks = effectiveBlocks
                val snapGroup = activeGroup
                viewModelScope.launch(Dispatchers.IO) {
                    alertsToReport.forEach { alert ->
                        val conflictKey = "${alert.studentId}_${alert.cardType.id}_${alert.weekKey}"
                        if (!LocalClassroomCache.isConflictNotificationReported(snapGroup.inviteCode, conflictKey)) {
                            LocalClassroomCache.markConflictNotificationReported(snapGroup.inviteCode, conflictKey)
                            val stu = snapStudents.firstOrNull { it.id == alert.studentId }
                            val blkName = snapBlocks.firstOrNull { it.id == stu?.blockId }
                                ?.servantName?.takeIf { it.isNotBlank() }?.let { "بلوك $it" } ?: "بدون بلوك"
                            val msg = "⚠️ تضارب كارت ${alert.cardType.arabicTitle}: الطفل ${alert.studentName} ($blkName) أخد الكارت مرتين من (${alert.servantNames.joinToString(" و ")})"
                            chatRepository.sendGroupMessage(
                                groupId = snapGroup.inviteCode,
                                senderName = "نظام التنبيهات",
                                text = msg,
                                memberUids = snapGroup.memberUids,
                                type = ChatMessageType.SYSTEM,
                                notificationCategory = NotificationCategory.CARD_CONFLICT.id,
                                childId = alert.studentId,
                                childName = alert.studentName,
                                childBlockName = blkName,
                                waitForServerAck = false
                            )
                        }
                    }
                }
            }
        }

        KamScreenState(
            isLoading = profile == null,
            userProfile = profile,
            group = activeGroup,
            userGroups = userGroups.filter { currentUserId !in it.bannedUids },
            isAdmin = activeGroup?.isUserAdmin(currentUserId) == true,
            isPrimaryAdmin = activeGroup?.isPrimaryAdmin(currentUserId) == true,
            isAssistantAdmin = activeGroup?.isAssistantAdmin(currentUserId) == true,
            blocks = effectiveBlocks,
            memberProfiles = effectiveMembers,
            students = effectiveStudents,
            cardEvents = effectiveEvents,
            monthlyBalances = effectiveBalances,
            allGroupCardEvents = effectiveAllEvents,
            allGroupMonthlyBalances = effectiveAllBalances,
            invitations = effectiveInvitations,
            childNotes = effectiveNotes,
            selectedMonthKey = SaturdayCalendarHelper.clampMonthKey(selectedMonth),
            selectedWeekKey = selectedWeek,
            currentWeekKey = currentYearWeek(),
            dismissedDuplicateNames = dismissedDuplicates,
            previousGroupId = fallbackPrevGroup,
            shouldPromptPhoneOnboarding = profile != null && activeGroup != null && hasLoadedPrefsFromDisk && !onboardingDone,
            ownPrimaryPhone = effectiveOwnPrimary,
            ownSecondaryPhone = effectiveOwnSecondary,
            ownPhoneHidden = effectiveOwnHidden,
            ownAvatarHash = effectiveOwnAvatarHash,
            precomputedIndices = precomputed
        )
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<KamScreenState> = combine(
        coreClassroomStateFlow,
        allAttendanceFlow,
        combine(_searchQuery, _isRefreshing) { q, ref -> q to ref },
        combine(isOnlineFlow, hasPendingWritesFlow) { online, pending -> online to pending },
        combine(_actionBanner, _operationError) { banner, error -> banner to error }
    ) { coreState, attendanceRecords, (searchQuery, isRefreshing), (isOnline, hasPendingWrites), (banner, error) ->
        val activeGroup = coreState.group
        if (activeGroup != null && activeGroup.isUserAdmin(currentUserId) && isOnline && activeGroup.isDailyCodeExpired()) {
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
            allAttendanceRecords = if (activeGroup == null) emptyList() else attendanceRecords,
            searchQuery = searchQuery,
            isOnline = isOnline,
            isRefreshing = isRefreshing,
            hasPendingWrites = hasPendingWrites
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
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
        lastSyncedMemberGroupKey = ""
        lastAutoRotatedGroupKey = ""
        lastEjectedBannedGroupId = ""
        _optimisticAddedEvents.value = emptyMap()
        _optimisticRemovedEventIds.value = emptySet()
        _optimisticStudentOverrides.value = emptyMap()
        _optimisticMemberOverrides.value = emptyMap()
        _optimisticGroupPolicyOverride.value = null
        policyMutationSeq = 0L
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
        val normalizedMonth = SaturdayCalendarHelper.clampMonthKey(monthKey)
        _selectedMonthKey.value = normalizedMonth
        _selectedWeekKey.value = weekKey?.takeIf { it.isNotBlank() }
    }

    fun selectMonthOffset(deltaMonths: Int) {
        val current = SaturdayCalendarHelper.clampMonthKey(_selectedMonthKey.value)
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
        val targetYear = cal.get(Calendar.YEAR)
        val nowYear = Calendar.getInstance().get(Calendar.YEAR)
        val minAllowedYear = minOf(SaturdayCalendarHelper.APP_START_YEAR, nowYear) - 1
        val maxAllowedYear = maxOf(SaturdayCalendarHelper.APP_START_YEAR, nowYear) + 2
        if (targetYear < minAllowedYear || targetYear > maxAllowedYear) {
            return
        }
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        val newMonthKey = sdf.format(cal.time)
        val nowMonthKey = currentYearMonth()
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
        val currentState = uiState.value
        val profile = currentState.userProfile
        return LocalUserPreferences(
            enabledCards = profile?.enabledCards ?: CardType.defaultEnabledIds,
            collapsedBlockIds = profile?.collapsedBlockIds ?: emptyList(),
            hiddenBlockIds = profile?.hiddenBlockIds ?: emptyList(),
            themeId = profile?.themeId ?: "DARK",
            uiScaleMode = profile?.uiScaleMode ?: "AUTO",
            uiScaleFactor = (profile?.uiScaleFactor ?: 0.85).toFloat(),
            showFloatingAddButton = profile?.showFloatingAddButton ?: true,
            activeGroupId = profile?.groupId.orEmpty(),
            phoneOnboardingDone = !currentState.shouldPromptPhoneOnboarding ||
                currentState.ownPrimaryPhone.isNotBlank() ||
                currentState.ownSecondaryPhone.isNotBlank(),
            primaryPhone = currentState.ownPrimaryPhone,
            secondaryPhone = currentState.ownSecondaryPhone,
            phoneHidden = currentState.ownPhoneHidden,
            servantAvatarHash = currentState.ownAvatarHash
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

    // --- Servant Blocks (Create, Edit & Assign Servant strictly Admin-only) ---

    fun createServantBlock(
        servantName: String,
        colorHex: String,
        servantUid: String = ""
    ) {
        val state = uiState.value
        val group = state.group ?: return
        if (!state.isAdmin) {
            showBanner("إنشاء البلوكات متاح لأمين المجموعة فقط")
            return
        }
        val cleanServantUid = servantUid.trim()
        val cleanServantName = servantName.trim()
        viewModelScope.launch {
            val nextOrder = (state.blocks.maxOfOrNull { it.sortOrder.toInt() } ?: -1) + 1
            repository.createBlock(
                groupId = group.inviteCode,
                servantId = cleanServantUid,
                servantName = cleanServantName,
                colorHex = colorHex,
                sortOrder = nextOrder,
                memberUids = group.memberUids,
                waitForServerAck = false
            ).onSuccess {
                showBanner("تم إضافة بلوك الخادم: $cleanServantName")
            }.onFailure {
                _operationError.value = it.localizedMessage ?: "تعذر إضافة البلوك (متاح للأمين فقط)"
            }
        }
    }

    fun updateServantBlock(
        block: ServantBlock,
        newName: String,
        newColorHex: String,
        newServantId: String? = null
    ) {
        val state = uiState.value
        val group = state.group ?: return
        if (!state.isAdmin && block.servantId != currentUserId) {
            showBanner("تعديل البلوكات وتعيين الخدام متاح لأمين المجموعة فقط")
            return
        }
        viewModelScope.launch {
            repository.updateBlock(
                groupId = group.inviteCode,
                blockId = block.id,
                servantName = newName.trim(),
                colorHex = newColorHex,
                sortOrder = block.sortOrder,
                memberUids = group.memberUids,
                servantId = if (state.isAdmin) newServantId?.trim() else null,
                waitForServerAck = false
            ).onSuccess {
                showBanner("تم تحديث بلوك ${newName.trim()}")
            }.onFailure {
                _operationError.value = it.localizedMessage ?: "تعذر تعديل البلوك"
            }
        }
    }

    fun updateBlockCustomAvatar(
        block: ServantBlock,
        avatarBase64: String
    ) {
        val state = uiState.value
        val group = state.group ?: return
        if (!state.isAdmin) {
            showBanner("تخصيص صورة البلوك متاح لأمين المجموعة فقط")
            return
        }
        val cleanAvatar = avatarBase64.trim().take(StudentAvatarImageHelper.MAX_FIRESTORE_AVATAR_CHARS)
        val hasAvatar = cleanAvatar.isNotBlank()
        viewModelScope.launch {
            repository.updateBlockCustomAvatar(
                groupId = group.inviteCode,
                blockId = block.id,
                avatarBase64 = cleanAvatar,
                memberUids = group.memberUids,
                waitForServerAck = false
            ).onSuccess {
                showBanner(
                    if (hasAvatar) "تم تحديث صورة «بلوك ${block.servantName}»"
                    else "تم حذف الصورة المخصصة لـ «بلوك ${block.servantName}»"
                )
            }.onFailure { err ->
                _operationError.value = err.localizedMessage ?: "تعذر تحديث صورة البلوك"
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
        if (targetUid == group.adminId || targetUid == currentUserId) {
            showBanner("لا يمكن إزالة حساب أمين المجموعة أو حسابك الشخصي")
            return
        }
        if (!group.isPrimaryAdmin(currentUserId) && group.isAssistantAdmin(targetUid)) {
            showBanner("لا يمكن لمساعد الأمين إزالة مساعد أمين آخر")
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

    fun toggleAssistantAdmin(
        targetUid: String,
        targetName: String,
        makeAssistant: Boolean
    ) {
        val state = uiState.value
        val group = state.group ?: return
        if (!group.isPrimaryAdmin(currentUserId)) {
            showBanner("تعيين أو إلغاء مساعد الأمين متاح لأمين المجموعة الأساسي فقط")
            return
        }
        if (targetUid.isBlank() || targetUid == group.adminId) return
        viewModelScope.launch {
            _isBusy.value = true
            repository.setAssistantAdminStatus(
                groupId = group.inviteCode,
                targetUid = targetUid,
                makeAssistant = makeAssistant,
                currentGroup = group
            ).onSuccess {
                if (makeAssistant) {
                    showBanner("تم تعيين '$targetName' كمساعد أمين بكامل الصلاحيات ✓")
                } else {
                    showBanner("تم إلغاء صلاحية مساعد الأمين عن '$targetName'")
                }
            }.onFailure { err ->
                _operationError.value = err.localizedMessage ?: "تعذر تحديث صلاحية مساعد الأمين"
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
        if (!state.isAdmin) {
            showBanner("حذف البلوك متاح لأمين المجموعة فقط")
            return
        }
        if (block.servantId.isNotBlank() && block.servantId != state.group?.adminId) {
            removeAndBanServantFromBlock(block)
            return
        }
        deleteBlockKeepingChildren(block)
    }

    fun deleteBlockKeepingChildren(block: ServantBlock) {
        val state = uiState.value
        val group = state.group ?: return
        if (!state.isAdmin) {
            showBanner("حذف البلوك متاح لأمين المجموعة فقط")
            return
        }
        val studentIdsInBlock = state.students.filter { it.blockId == block.id }.map { it.id }
        viewModelScope.launch {
            repository.deleteBlockKeepingChildren(
                groupId = group.inviteCode,
                blockId = block.id,
                studentIdsInBlock = studentIdsInBlock,
                memberUids = group.memberUids,
                waitForServerAck = false
            ).onSuccess {
                showBanner("تم حذف بلوك '${block.servantName}' ونقل الأطفال (${studentIdsInBlock.size}) إلى بدون بلوك")
            }.onFailure {
                _operationError.value = "رفض السيرفر الحذف: الحذف مسموح للأمين فقط"
            }
        }
    }

    fun deleteBlockWithChildren(block: ServantBlock) {
        val state = uiState.value
        val group = state.group ?: return
        if (!state.isAdmin) {
            showBanner("حذف البلوك والأطفال متاح لأمين المجموعة فقط")
            return
        }
        val studentIdsInBlock = state.students.filter { it.blockId == block.id }.map { it.id }.toSet()
        val noteIds = state.childNotes.filter { it.studentId in studentIdsInBlock }.map { it.id }
        val balanceIds = state.effectiveAllMonthlyBalances.filter { it.studentId in studentIdsInBlock }.map { it.id }
        viewModelScope.launch {
            _isBusy.value = true
            repository.deleteBlockWithChildren(
                groupId = group.inviteCode,
                blockId = block.id,
                studentIdsInBlock = studentIdsInBlock.toList(),
                noteIdsToDelete = noteIds,
                balanceIdsToDelete = balanceIds,
                waitForServerAck = false
            ).onSuccess {
                showBanner("تم حذف بلوك '${block.servantName}' مع أطفاله (${studentIdsInBlock.size})")
            }.onFailure {
                _operationError.value = "تعذر حذف البلوك والأطفال (متاح للأمين فقط)"
            }
            _isBusy.value = false
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
        val cleanName = newName.trim().take(100)
        if (cleanName.isBlank()) {
            _operationError.value = "اسم الطفل لا يمكن أن يكون فارغاً"
            return
        }
        val safeNumber = newNumber.coerceIn(1, 2000)
        val safeBlockId = newBlockId.trim().take(80)
        val movedToNewBlock = safeBlockId != student.blockId
        val newPosition = if (movedToNewBlock) {
            val targetMax = state.students
                .filter { it.blockId == safeBlockId && it.id != student.id }
                .maxOfOrNull { it.effectivePosition } ?: 0.0
            targetMax + 1000.0
        } else {
            student.effectivePosition
        }
        val safeSortOrder = student.sortOrder.coerceIn(0L, 5000L)
        val updatedStudent = student.copy(
            name = cleanName,
            studentNumber = safeNumber.toLong(),
            blockId = safeBlockId,
            sortOrder = safeSortOrder,
            position = newPosition,
            avatarInitials = ArabicInitialsHelper.extractInitials(cleanName).take(10)
        )
        _optimisticStudentOverrides.update { it + (student.id to updatedStudent) }

        val targetBlockName = if (movedToNewBlock) {
            state.blocks.firstOrNull { it.id == safeBlockId }?.let { state.resolvedBlockOwnerName(it) } ?: "بدون بلوك"
        } else {
            null
        }

        viewModelScope.launch {
            repository.updateStudent(
                groupId = group.inviteCode,
                studentId = student.id,
                newName = cleanName,
                newStudentNumber = safeNumber,
                newBlockId = safeBlockId,
                newSortOrder = safeSortOrder,
                newPosition = newPosition,
                memberUids = group.memberUids,
                waitForServerAck = state.isOnline
            ).onSuccess {
                _optimisticStudentOverrides.update { it - student.id }
                if (targetBlockName != null) {
                    showBanner("تم حفظ '$cleanName' ونقله إلى «$targetBlockName»")
                } else {
                    showBanner("تم حفظ تعديلات '$cleanName'")
                }
            }.onFailure { err ->
                _optimisticStudentOverrides.update { it - student.id }
                _operationError.value = err.localizedMessage ?: "تعذر تعديل بيانات الطفل"
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
        val customConfig = group.customCardConfig
        val cardTitle = cardType.displayTitle(customConfig)
        val cardPoints = cardType.displayPoints(customConfig)

        if (cardType == CardType.CUSTOM_ELDER && !customConfig.enabled) {
            showBanner("كارت '$cardTitle' غير مفعّل حالياً من إعدادات الأمين")
            return
        }

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

        // Special frequency enforcement for CUSTOM_ELDER (WEEKLY / MONTHLY / ONCE_EVER)
        if (cardType == CardType.CUSTOM_ELDER) {
            val allStudentCustomEvents = state.effectiveAllCardEvents.filter {
                it.studentId == student.id && it.cardType == CardType.CUSTOM_ELDER.id
            }
            when (customConfig.frequency) {
                CustomCardFrequency.WEEKLY -> {
                    val ownInWeek = allStudentCustomEvents.firstOrNull {
                        it.weekKey == activeWeekKey && it.servantId == currentUserId
                    }
                    if (ownInWeek != null) {
                        if (state.isAdmin || group.allowServantUndo) {
                            recentTapTimestamps[tapKey] = nowMs
                            undoCardEvent(ownInWeek)
                        } else {
                            showBanner("تم تسجيل '$cardTitle' لـ ${student.name} بالفعل هذا الأسبوع")
                        }
                        return
                    }
                    val anyInWeek = allStudentCustomEvents.firstOrNull { it.weekKey == activeWeekKey }
                    if (anyInWeek != null) {
                        showBanner("تنبيه: '$cardTitle' يُعطى مرة واحدة في الأسبوع فقط، وقد حصل عليه ${student.name} بالفعل")
                        return
                    }
                }
                CustomCardFrequency.MONTHLY -> {
                    val ownInMonth = allStudentCustomEvents.firstOrNull {
                        it.monthKey == state.selectedMonthKey && it.servantId == currentUserId
                    }
                    if (ownInMonth != null) {
                        if (state.isAdmin || group.allowServantUndo) {
                            recentTapTimestamps[tapKey] = nowMs
                            undoCardEvent(ownInMonth)
                        } else {
                            showBanner("تم تسجيل '$cardTitle' لـ ${student.name} بالفعل هذا الشهر")
                        }
                        return
                    }
                    val anyInMonth = allStudentCustomEvents.firstOrNull { it.monthKey == state.selectedMonthKey }
                    if (anyInMonth != null) {
                        showBanner("تنبيه: '$cardTitle' يُعطى مرة واحدة في الشهر فقط، وقد حصل عليه ${student.name} بالفعل هذا الشهر")
                        return
                    }
                }
                CustomCardFrequency.ONCE_EVER -> {
                    val ownInCurrentWeek = allStudentCustomEvents.firstOrNull {
                        it.weekKey == activeWeekKey && it.servantId == currentUserId
                    }
                    if (ownInCurrentWeek != null && (state.isAdmin || group.allowServantUndo)) {
                        recentTapTimestamps[tapKey] = nowMs
                        undoCardEvent(ownInCurrentWeek)
                        return
                    }
                    if (allStudentCustomEvents.isNotEmpty()) {
                        showBanner("تنبيه: '$cardTitle' يُعطى مرة واحدة فقط ولا يتكرر، وقد حصل عليه ${student.name} من قبل")
                        return
                    }
                }
            }
        } else {
            // If the SAME servant already recorded this standard card in the active week:
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
                    KamPerformanceTracker.markCardTapComplete(student.name, "$cardTitle (إلغاء)")
                } else {
                    showBanner("تم تسجيل كارت '$cardTitle' لـ ${student.name} بالفعل هذا الأسبوع")
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
                    showBanner("تنبيه: كارت '$cardTitle' تم تسجيله بالفعل لـ ${student.name} هذا الأسبوع")
                    return
                }
            }
        }

        val currentPoints = state.pointsForStudentInSelectedMonth(student.id)
        if (group.maxMonthlyPoints > 0L && currentPoints + cardPoints > group.maxMonthlyPoints) {
            showBanner("وصل ${student.name} للحد الأقصى الشهري للنقاط (${group.maxMonthlyPoints} نقطة)")
            return
        }

        recentTapTimestamps[tapKey] = nowMs
        val servantName = state.userProfile?.displayName?.ifBlank { initialDisplayName } ?: initialDisplayName
        val slotKey = if (cardType == CardType.CUSTOM_ELDER) {
            when (customConfig.frequency) {
                CustomCardFrequency.WEEKLY -> activeWeekKey
                CustomCardFrequency.MONTHLY -> state.selectedMonthKey
                CustomCardFrequency.ONCE_EVER -> "ONCE"
            }
        } else {
            activeWeekKey
        }
        val currentEventsCount = state.eventsForStudent(student.id).size.toLong()

        // Check if another servant already gave this exact card in this week (for instant feedback if already synced)
        val otherServantEvent = state.cardEvents.firstOrNull {
            it.studentId == student.id &&
                it.cardType == cardType.id &&
                it.weekKey == activeWeekKey &&
                it.servantId != currentUserId
        }

        val safeSlot = slotKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "")
        val deterministicEventId = "evt_${student.id}_${cardType.id}_${safeSlot}_${currentUserId}".take(120)
        val optimisticEvent = CardEvent(
            id = deterministicEventId,
            groupId = group.inviteCode,
            studentId = student.id,
            studentName = student.name,
            cardType = cardType.id,
            points = cardPoints.toLong(),
            servantId = currentUserId,
            servantName = servantName,
            monthKey = state.selectedMonthKey,
            weekKey = activeWeekKey,
            memberUids = group.memberUids
        )

        // Apply 0ms optimistic card addition before Firestore local cache round-trip
        _optimisticRemovedEventIds.update { it - deterministicEventId }
        _optimisticAddedEvents.update { it + (deterministicEventId to optimisticEvent) }
        KamPerformanceTracker.markCardTapComplete(student.name, cardTitle)

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
                waitForServerAck = false,
                pointsOverride = if (cardType == CardType.CUSTOM_ELDER) cardPoints.toLong() else null
            ).onSuccess { savedId ->
                _optimisticAddedEvents.update { (it - deterministicEventId) - savedId }
                if (otherServantEvent != null) {
                    showBanner(
                        "تنبيه: الطفل ${student.name} أخد كارت $cardTitle مرتين: ${otherServantEvent.servantName} و$servantName"
                    )
                } else if (!state.isOnline) {
                    showBanner("+$cardPoints ($cardTitle) لـ ${student.name} • محفوظ أوفلاين")
                } else {
                    showBanner("+$cardPoints ($cardTitle) لـ ${student.name}")
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
        if (!group.isUserAdmin(currentUserId)) {
            _operationError.value = "تعديل قواعد المجموعة متاح لأمين المجموعة أو مساعده فقط (${group.adminName})"
            return
        }

        val clampedMaxPoints = maxMonthlyPoints.coerceIn(0L, 10000L)
        val seq = ++policyMutationSeq
        val optimisticGroup = group.copy(
            allowServantUndo = allowServantUndo,
            enforceWeeklyCardLimit = enforceWeeklyCardLimit,
            maxMonthlyPoints = clampedMaxPoints,
            attendanceIsCardOnly = attendanceIsCardOnly,
            showLeaderboard = showLeaderboard,
            autoStudentNumber = autoStudentNumber
        )
        _optimisticGroupPolicyOverride.value = optimisticGroup

        viewModelScope.launch {
            repository.updateGroupPolicies(
                groupId = group.inviteCode,
                allowServantUndo = allowServantUndo,
                enforceWeeklyCardLimit = enforceWeeklyCardLimit,
                maxMonthlyPoints = clampedMaxPoints,
                attendanceIsCardOnly = attendanceIsCardOnly,
                showLeaderboard = showLeaderboard,
                autoStudentNumber = autoStudentNumber
            ).onSuccess {
                showBanner("تم حفظ قواعد وقرارات المجموعة")
                delay(600L)
                if (policyMutationSeq == seq) {
                    _optimisticGroupPolicyOverride.value = null
                }
            }.onFailure { err ->
                if (policyMutationSeq == seq) {
                    _optimisticGroupPolicyOverride.value = null
                }
                val firestoreErr = generateSequence(err) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                _operationError.value = when {
                    firestoreErr?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "رفضت قاعدة البيانات التعديل (PERMISSION_DENIED) — يجب تحديث قواعد groups في Firebase Console"
                    else -> err.localizedMessage ?: "تعذر حفظ القاعدة في السحابة، تم إرجاع الحالة"
                }
            }
        }
    }

    /**
     * Updates the configurable custom extra card (كارت القمداء) settings for the group.
     * Restricted to Primary Admin and Assistant Admins.
     */
    fun updateCustomCardSettings(config: CustomCardConfig) {
        val group = uiState.value.group ?: return
        if (!group.isUserAdmin(currentUserId)) {
            _operationError.value = "تعديل إعدادات الكارت الإضافي متاح لأمين المجموعة أو مساعده فقط"
            return
        }
        val cleanConfig = config.copy(
            name = config.rawTrimmedName.take(40),
            points = config.effectivePoints,
            icon = config.effectiveIcon.take(12),
            colorKey = config.colorKey.trim().ifBlank { "GOLD" }
        )
        val seq = ++policyMutationSeq
        _optimisticGroupPolicyOverride.value = group.copy(
            customCardEnabled = cleanConfig.enabled,
            customCardName = cleanConfig.name,
            customCardPoints = cleanConfig.points.toLong(),
            customCardIcon = cleanConfig.icon,
            customCardColorKey = cleanConfig.colorKey,
            customCardFrequency = cleanConfig.frequency.id
        )

        viewModelScope.launch {
            repository.updateCustomCardSettings(
                groupId = group.inviteCode,
                config = cleanConfig
            ).onSuccess {
                val displayLabel = if (cleanConfig.hasCustomName) {
                    cleanConfig.name
                } else {
                    "${cleanConfig.icon} كارت القمداء"
                }
                val statusLabel = if (cleanConfig.enabled) {
                    "تم حفظ وتفعيل '$displayLabel' (${cleanConfig.points} نقطة • ${cleanConfig.frequency.shortArabicLabel})"
                } else {
                    "تم حفظ إعدادات '$displayLabel' (متوقف حالياً)"
                }
                showBanner(statusLabel)
                delay(600L)
                if (policyMutationSeq == seq) {
                    _optimisticGroupPolicyOverride.value = null
                }
            }.onFailure { err ->
                if (policyMutationSeq == seq) {
                    _optimisticGroupPolicyOverride.value = null
                }
                _operationError.value = err.localizedMessage ?: "تعذر حفظ إعدادات الكارت الإضافي"
            }
        }
    }

    /**
     * Forces a full cloud refresh (`Source.SERVER`) for the active group so any changes made by
     * other servants (cards, attendance, students, blocks, settings) sync immediately.
     * Triggered by double-tapping the Home tab button or Pull-to-Refresh on Home.
     */
    fun forceFullCloudSync() {
        if (_isRefreshing.value) return
        val state = uiState.value
        val groupId = state.group?.inviteCode.orEmpty()
        if (!state.isOnline) {
            showBanner("لا يوجد اتصال بالإنترنت — يتم عرض آخر بيانات محفوظة")
            return
        }
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val result = kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                    repository.forceRefreshFromServer(
                        groupId = groupId,
                        userId = currentUserId
                    )
                }
                if (result == null) {
                    showBanner("استغرق الاتصال وقتاً أطول من المعتاد — تأكد من الشبكة")
                } else if (result.isSuccess) {
                    showBanner("تم تحديث ومزامنة البيانات مع باقي الخدام ✓")
                } else {
                    showBanner("تعذر المزامنة الآن — تأكد من اتصالك بالإنترنت")
                }
            } finally {
                _isRefreshing.value = false
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

    // --- Servant Public Profile (Phone Numbers & <50KB Avatar inside groups/{groupId}/members/{uid}) ---

    private fun resolveOwnServantDisplayName(state: KamScreenState): String {
        return state.blocks.find { it.servantId == currentUserId }?.servantName?.takeIf { it.isNotBlank() }
            ?: state.userProfile?.displayName?.takeIf { it.isNotBlank() }
            ?: initialDisplayName.ifBlank { "خادم" }
    }

    fun skipPhoneOnboarding() {
        val prefs = currentEffectiveLocalPrefs()
        val nextPrefs = prefs.copy(phoneOnboardingDone = true)
        _localPrefsOverride.value = nextPrefs
        LocalClassroomCache.saveUserPreferences(currentUserId, nextPrefs)
    }

    fun saveOwnPhoneNumbers(
        primaryPhone: String,
        secondaryPhone: String = "",
        @Suppress("UNUSED_PARAMETER") phoneHidden: Boolean = false
    ) {
        val cleanPrimary = if (primaryPhone.isNotBlank()) {
            PhoneNumberFormatHelper.toInternationalPhone(primaryPhone)
        } else {
            ""
        }
        val cleanSecondary = if (secondaryPhone.isNotBlank()) {
            PhoneNumberFormatHelper.toInternationalPhone(secondaryPhone)
        } else {
            ""
        }.let { sec -> if (sec == cleanPrimary) "" else sec }

        val prefs = currentEffectiveLocalPrefs()
        val nextPrefs = prefs.copy(
            phoneOnboardingDone = true,
            primaryPhone = cleanPrimary,
            secondaryPhone = cleanSecondary,
            phoneHidden = false
        )
        _localPrefsOverride.value = nextPrefs
        LocalClassroomCache.saveUserPreferences(currentUserId, nextPrefs)

        val state = uiState.value
        val group = state.group
        if (group != null) {
            val existing = state.memberProfileForServant(currentUserId)
            val displayName = resolveOwnServantDisplayName(state)
            val optimisticProfile = (existing ?: ServantMemberProfile(
                id = currentUserId,
                uid = currentUserId,
                groupId = group.inviteCode,
                displayName = displayName,
                memberUids = group.memberUids
            )).copy(
                displayName = displayName,
                phone = cleanPrimary,
                primaryPhone = cleanPrimary,
                secondaryPhone = cleanSecondary,
                phoneVerified = false,
                showPhone = true,
                phoneHidden = false
            )
            _optimisticMemberOverrides.update { it + (currentUserId to optimisticProfile) }

            viewModelScope.launch {
                repository.upsertOwnMemberProfile(
                    groupId = group.inviteCode,
                    displayName = displayName,
                    primaryPhone = cleanPrimary,
                    secondaryPhone = cleanSecondary,
                    phoneHidden = false,
                    avatarBase64 = null,
                    memberUids = group.memberUids,
                    existingProfile = existing,
                    waitForServerAck = false
                ).onSuccess {
                    _optimisticMemberOverrides.update { it - currentUserId }
                    showBanner(
                        if (cleanPrimary.isNotBlank()) "تم حفظ رقم الواتساب في بروفايل الخادم"
                        else "تم تحديث بيانات الرقم"
                    )
                }.onFailure { err ->
                    _optimisticMemberOverrides.update { it - currentUserId }
                    _operationError.value = err.localizedMessage ?: "تعذر مزامنة رقم الموبايل مع المجموعة"
                }
            }
        } else {
            showBanner("تم حفظ رقم الواتساب وسيتم مزامنته تلقائياً عند دخول المجموعة")
        }
    }

    fun editOwnDisplayName(newName: String) {
        val cleanName = newName.trim().take(80)
        if (cleanName.isBlank()) {
            showBanner("يرجى إدخال اسم صحيح")
            return
        }
        val state = uiState.value
        val ownBlocks = state.blocks.filter { it.servantId == currentUserId }
        val existingMember = state.memberProfileForServant(currentUserId)
        if (existingMember != null) {
            _optimisticMemberOverrides.update { it + (currentUserId to existingMember.copy(displayName = cleanName)) }
        }
        viewModelScope.launch {
            repository.updateOwnDisplayName(
                newName = cleanName,
                email = state.userProfile?.email?.ifBlank { initialEmail } ?: initialEmail,
                memberGroups = (state.userGroups + listOfNotNull(state.group)).distinctBy { it.inviteCode },
                ownBlocks = ownBlocks,
                waitForServerAck = false
            ).onSuccess {
                _optimisticMemberOverrides.update { it - currentUserId }
                showBanner("تم تحديث اسمك إلى '$cleanName'")
            }.onFailure { err ->
                _optimisticMemberOverrides.update { it - currentUserId }
                _operationError.value = err.localizedMessage ?: "تعذر تحديث الاسم"
            }
        }
    }

    fun savePersonalServantAlias(targetUid: String, alias: String) {
        if (targetUid.isBlank() || targetUid == currentUserId) return
        val state = uiState.value
        val currentAliases = state.servantAliases.toMutableMap()
        val cleanAlias = alias.trim().take(80)
        if (cleanAlias.isBlank()) {
            currentAliases.remove(targetUid)
        } else {
            currentAliases[targetUid] = cleanAlias
        }
        viewModelScope.launch {
            repository.updatePersonalServantAliases(currentAliases, waitForServerAck = false)
                .onSuccess {
                    showBanner(
                        if (cleanAlias.isBlank()) "تمت العودة للاسم الأصلي للخادم"
                        else "تم حفظ الاسم '$cleanAlias' في جهازك"
                    )
                }
                .onFailure { err ->
                    _operationError.value = err.localizedMessage ?: "تعذر حفظ التسمية"
                }
        }
    }

    fun updateGroupAvatar(avatarBase64: String) {
        val state = uiState.value
        val group = state.group ?: return
        val cleanAvatar = avatarBase64.trim().take(StudentAvatarImageHelper.MAX_FIRESTORE_AVATAR_CHARS)
        val hash = if (cleanAvatar.isNotBlank()) Student.computeAvatarHash(cleanAvatar) else ""
        StudentAvatarImageHelper.writeLocalAlbumRaw(null, "group_${group.inviteCode}", cleanAvatar)
        _optimisticGroupPolicyOverride.value = group.copy(avatarBase64 = cleanAvatar, avatarHash = hash)
        viewModelScope.launch {
            repository.updateGroupAvatar(
                groupId = group.inviteCode,
                avatarBase64 = cleanAvatar,
                waitForServerAck = false
            ).onSuccess {
                showBanner(if (cleanAvatar.isNotBlank()) "تم تحديث صورة الجروب" else "تم حذف صورة الجروب")
                delay(400L)
                _optimisticGroupPolicyOverride.value = null
            }.onFailure { err ->
                _optimisticGroupPolicyOverride.value = null
                _operationError.value = err.localizedMessage ?: "تعذر تحديث صورة الجروب"
            }
        }
    }

    // --- Shared Child Notes, Memorization, Visitation & Group Chat Notifications ---

    fun postChildGroupNotification(
        student: Student,
        category: NotificationCategory,
        messageText: String
    ) {
        val state = uiState.value
        val group = state.group ?: return
        val repo = chatRepository ?: return
        val cleanText = messageText.trim()
        if (cleanText.isBlank()) return
        val blkName = state.blockNameForStudent(student)
        val senderName = resolveOwnServantDisplayName(state)
        viewModelScope.launch(Dispatchers.IO) {
            repo.sendGroupMessage(
                groupId = group.inviteCode,
                senderName = senderName,
                text = cleanText,
                memberUids = group.memberUids,
                type = ChatMessageType.SYSTEM,
                notificationCategory = category.id,
                childId = student.id,
                childName = student.name,
                childBlockName = blkName,
                waitForServerAck = false
            )
        }
    }

    fun addChildComment(student: Student, text: String, notifyGroup: Boolean = true) {
        val state = uiState.value
        val group = state.group ?: return
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        val authorName = resolveOwnServantDisplayName(state)
        val blkName = state.blockNameForStudent(student)
        val nowMs = System.currentTimeMillis()
        val tempNote = ChildNote(
            id = "temp_${nowMs}",
            groupId = group.inviteCode,
            studentId = student.id,
            studentName = student.name,
            type = ChildNoteType.COMMENT.id,
            text = cleanText,
            authorUid = currentUserId,
            authorName = authorName,
            clientCreatedAtMillis = nowMs,
            memberUids = group.memberUids
        )
        _optimisticChildNotes.update { listOf(tempNote) + it }
        viewModelScope.launch {
            repository.addChildNote(
                groupId = group.inviteCode,
                student = student,
                type = ChildNoteType.COMMENT,
                text = cleanText,
                authorName = authorName,
                memberUids = group.memberUids,
                waitForServerAck = false
            ).onSuccess { saved ->
                _optimisticChildNotes.update { list -> list.filterNot { it.id == tempNote.id } + saved }
                showBanner("تم حفظ التعليق عن '${student.name}'")
                if (notifyGroup) {
                    chatRepository?.sendGroupMessage(
                        groupId = group.inviteCode,
                        senderName = authorName,
                        text = "💬 تعليق عن ${student.name} ($blkName): $cleanText",
                        memberUids = group.memberUids,
                        type = ChatMessageType.SYSTEM,
                        notificationCategory = NotificationCategory.CHILD_COMMENT.id,
                        childId = student.id,
                        childName = student.name,
                        childBlockName = blkName,
                        waitForServerAck = false
                    )
                }
            }.onFailure { err ->
                _optimisticChildNotes.update { list -> list.filterNot { it.id == tempNote.id } }
                _operationError.value = err.localizedMessage ?: "تعذر حفظ التعليق"
            }
        }
    }

    fun addMemorizationNote(student: Student, text: String, awardMemorizationCard: Boolean = false) {
        val state = uiState.value
        val group = state.group ?: return
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        val authorName = resolveOwnServantDisplayName(state)
        val blkName = state.blockNameForStudent(student)
        val nowMs = System.currentTimeMillis()
        val tempNote = ChildNote(
            id = "temp_mem_${nowMs}",
            groupId = group.inviteCode,
            studentId = student.id,
            studentName = student.name,
            type = ChildNoteType.MEMORIZATION.id,
            text = cleanText,
            authorUid = currentUserId,
            authorName = authorName,
            clientCreatedAtMillis = nowMs,
            memberUids = group.memberUids
        )
        _optimisticChildNotes.update { listOf(tempNote) + it }
        _optimisticStudentOverrides.update {
            it + (student.id to student.copy(
                lastMemorizedText = cleanText,
                lastMemorizedBy = authorName,
                lastMemorizedAtMillis = nowMs
            ))
        }
        viewModelScope.launch {
            repository.addChildNote(
                groupId = group.inviteCode,
                student = student,
                type = ChildNoteType.MEMORIZATION,
                text = cleanText,
                authorName = authorName,
                memberUids = group.memberUids,
                waitForServerAck = false
            ).onSuccess { saved ->
                _optimisticChildNotes.update { list -> list.filterNot { it.id == tempNote.id } + saved }
                _optimisticStudentOverrides.update { it - student.id }
                showBanner("تم تحديث حفظ '${student.name}'")
                chatRepository?.sendGroupMessage(
                    groupId = group.inviteCode,
                    senderName = authorName,
                    text = "📖 تسميع ${student.name} ($blkName): $cleanText",
                    memberUids = group.memberUids,
                    type = ChatMessageType.SYSTEM,
                    notificationCategory = NotificationCategory.MEMORIZATION.id,
                    childId = student.id,
                    childName = student.name,
                    childBlockName = blkName,
                    waitForServerAck = false
                )
            }.onFailure { err ->
                _optimisticChildNotes.update { list -> list.filterNot { it.id == tempNote.id } }
                _optimisticStudentOverrides.update { it - student.id }
                _operationError.value = err.localizedMessage ?: "تعذر حفظ التسميع"
            }
        }
        if (awardMemorizationCard) {
            awardCardToStudent(student, CardType.MEMORIZATION)
        }
    }

    fun recordVisitationNote(
        student: Student,
        status: VisitationStatus,
        noteText: String,
        notifyGroup: Boolean = true
    ) {
        val state = uiState.value
        val group = state.group ?: return
        val authorName = resolveOwnServantDisplayName(state)
        val blkName = state.blockNameForStudent(student)
        val effectiveText = noteText.trim().ifBlank { status.arabicLabel }
        val nowMs = System.currentTimeMillis()
        val tempNote = ChildNote(
            id = "temp_vis_${nowMs}",
            groupId = group.inviteCode,
            studentId = student.id,
            studentName = student.name,
            type = ChildNoteType.VISITATION.id,
            text = effectiveText,
            status = status.id,
            authorUid = currentUserId,
            authorName = authorName,
            clientCreatedAtMillis = nowMs,
            memberUids = group.memberUids
        )
        _optimisticChildNotes.update { listOf(tempNote) + it }
        viewModelScope.launch {
            repository.addChildNote(
                groupId = group.inviteCode,
                student = student,
                type = ChildNoteType.VISITATION,
                text = effectiveText,
                status = status.id,
                authorName = authorName,
                memberUids = group.memberUids,
                waitForServerAck = false
            ).onSuccess { saved ->
                _optimisticChildNotes.update { list -> list.filterNot { it.id == tempNote.id } + saved }
                showBanner("${status.arabicLabel}: ${student.name}")
                if (notifyGroup) {
                    val emoji = if (status == VisitationStatus.FOLLOWUP) "🔔" else "🏠"
                    chatRepository?.sendGroupMessage(
                        groupId = group.inviteCode,
                        senderName = authorName,
                        text = "$emoji افتقاد ${student.name} ($blkName) — ${status.arabicLabel}: $effectiveText",
                        memberUids = group.memberUids,
                        type = ChatMessageType.SYSTEM,
                        notificationCategory = NotificationCategory.VISITATION.id,
                        childId = student.id,
                        childName = student.name,
                        childBlockName = blkName,
                        waitForServerAck = false
                    )
                }
            }.onFailure { err ->
                _optimisticChildNotes.update { list -> list.filterNot { it.id == tempNote.id } }
                _operationError.value = err.localizedMessage ?: "تعذر حفظ الافتقاد"
            }
        }
    }

    fun deleteChildNote(note: ChildNote) {
        val group = uiState.value.group ?: return
        _optimisticDeletedNoteIds.update { it + note.id }
        viewModelScope.launch {
            repository.deleteChildNote(group.inviteCode, note.id, waitForServerAck = false)
                .onSuccess {
                    showBanner("تم حذف الملاحظة")
                }
                .onFailure { err ->
                    _optimisticDeletedNoteIds.update { it - note.id }
                    _operationError.value = err.localizedMessage ?: "تعذر حذف الملاحظة"
                }
        }
    }

    fun deleteOwnPhoneNumbers() {
        val prefs = currentEffectiveLocalPrefs()
        val nextPrefs = prefs.copy(
            phoneOnboardingDone = true,
            primaryPhone = "",
            secondaryPhone = "",
            phoneHidden = false
        )
        _localPrefsOverride.value = nextPrefs
        LocalClassroomCache.saveUserPreferences(currentUserId, nextPrefs)

        val state = uiState.value
        val group = state.group
        if (group != null) {
            val existing = state.memberProfileForServant(currentUserId)
            val displayName = resolveOwnServantDisplayName(state)
            val optimisticProfile = (existing ?: ServantMemberProfile(
                id = currentUserId,
                uid = currentUserId,
                groupId = group.inviteCode,
                displayName = displayName,
                memberUids = group.memberUids
            )).copy(
                displayName = displayName,
                phone = "",
                primaryPhone = "",
                secondaryPhone = "",
                phoneVerified = false,
                showPhone = true,
                phoneHidden = false
            )
            _optimisticMemberOverrides.update { it + (currentUserId to optimisticProfile) }

            viewModelScope.launch {
                repository.upsertOwnMemberProfile(
                    groupId = group.inviteCode,
                    displayName = displayName,
                    primaryPhone = "",
                    secondaryPhone = "",
                    phoneHidden = false,
                    avatarBase64 = null,
                    memberUids = group.memberUids,
                    existingProfile = existing,
                    waitForServerAck = false
                ).onSuccess {
                    _optimisticMemberOverrides.update { it - currentUserId }
                    showBanner("تم حذف رقم الموبايل من بروفايل الخادم")
                }.onFailure { err ->
                    _optimisticMemberOverrides.update { it - currentUserId }
                    _operationError.value = err.localizedMessage ?: "تعذر حذف رقم الموبايل"
                }
            }
        } else {
            showBanner("تم حذف رقم الموبايل")
        }
    }

    fun updateOwnServantAvatar(avatarBase64: String) {
        val cleanAvatar = avatarBase64.trim().take(StudentAvatarImageHelper.MAX_FIRESTORE_AVATAR_CHARS)
        val hasAvatar = cleanAvatar.isNotBlank()
        val newHash = if (hasAvatar) Student.computeAvatarHash(cleanAvatar) else ""
        StudentAvatarImageHelper.writeLocalAlbumRaw(null, "servant_$currentUserId", cleanAvatar)

        val prefs = currentEffectiveLocalPrefs()
        val nextPrefs = prefs.copy(servantAvatarHash = newHash)
        _localPrefsOverride.value = nextPrefs
        LocalClassroomCache.saveUserPreferences(currentUserId, nextPrefs)

        val state = uiState.value
        val group = state.group
        if (group != null) {
            val existing = state.memberProfileForServant(currentUserId)
            val displayName = resolveOwnServantDisplayName(state)
            val optimisticProfile = (existing ?: ServantMemberProfile(
                id = currentUserId,
                uid = currentUserId,
                groupId = group.inviteCode,
                displayName = displayName,
                phone = nextPrefs.primaryPhone,
                primaryPhone = nextPrefs.primaryPhone,
                secondaryPhone = nextPrefs.secondaryPhone,
                showPhone = true,
                phoneHidden = false,
                memberUids = group.memberUids
            )).copy(
                displayName = displayName,
                avatar = cleanAvatar,
                hasAvatar = hasAvatar,
                avatarHash = newHash,
                avatarBase64 = cleanAvatar
            )
            _optimisticMemberOverrides.update { it + (currentUserId to optimisticProfile) }

            viewModelScope.launch {
                repository.upsertOwnMemberProfile(
                    groupId = group.inviteCode,
                    displayName = displayName,
                    primaryPhone = nextPrefs.primaryPhone.takeIf { it.isNotBlank() } ?: existing?.effectivePrimaryPhone.orEmpty(),
                    secondaryPhone = nextPrefs.secondaryPhone.takeIf { it.isNotBlank() } ?: existing?.secondaryPhone.orEmpty(),
                    phoneHidden = false,
                    avatarBase64 = cleanAvatar,
                    memberUids = group.memberUids,
                    existingProfile = existing,
                    waitForServerAck = false
                ).onSuccess {
                    _optimisticMemberOverrides.update { it - currentUserId }
                    showBanner(if (hasAvatar) "تم تحديث صورة الخادم وحفظها في الجروب" else "تم حذف صورة الخادم")
                }.onFailure { err ->
                    _optimisticMemberOverrides.update { it - currentUserId }
                    _operationError.value = err.localizedMessage ?: "تعذر تحديث صورة الخادم"
                }
            }
        } else {
            showBanner(if (hasAvatar) "تم حفظ صورة الخادم محلياً" else "تم حذف صورة الخادم")
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
            val ownCachedMember = cached.memberProfiles.find { it.effectiveUid == currentUserId }
            val ownPrimary = localPrefs?.primaryPhone?.takeIf { it.isNotBlank() }
                ?: ownCachedMember?.effectivePrimaryPhone.orEmpty()
            val ownSecondary = localPrefs?.secondaryPhone?.takeIf { it.isNotBlank() }
                ?: ownCachedMember?.secondaryPhone.orEmpty()
            val ownHidden = false
            val ownAvatarHash = localPrefs?.servantAvatarHash?.takeIf { it.isNotBlank() }
                ?: ownCachedMember?.effectiveAvatarKey.orEmpty()
            val onboardingDone = (localPrefs?.phoneOnboardingDone == true) ||
                ownPrimary.isNotBlank() ||
                ownSecondary.isNotBlank()
            val cachedNotes = if (activeGroup == null) emptyList() else LocalClassroomCache.getMemoryChildNotes(activeGroup.inviteCode)
            val sanitizedCachedBlocks = if (activeGroup == null) {
                emptyList()
            } else {
                sanitizeLegacyAdminBlocks(
                    rawBlocks = cached.blocks,
                    activeGroup = activeGroup,
                    members = cached.memberProfiles,
                    profile = effectiveProfile
                ).first
            }
            return KamScreenState(
                isLoading = false,
                userProfile = effectiveProfile,
                group = activeGroup,
                userGroups = cached.userGroups,
                isAdmin = activeGroup?.isUserAdmin(currentUserId) == true,
                isPrimaryAdmin = activeGroup?.isPrimaryAdmin(currentUserId) == true,
                isAssistantAdmin = activeGroup?.isAssistantAdmin(currentUserId) == true,
                blocks = sanitizedCachedBlocks,
                memberProfiles = if (activeGroup == null) emptyList() else cached.memberProfiles,
                students = if (activeGroup == null) emptyList() else cached.students,
                cardEvents = if (activeGroup == null) emptyList() else cached.cardEvents.filter { it.monthKey == monthKey },
                monthlyBalances = if (activeGroup == null) emptyList() else cached.monthlyBalances.filter { it.monthKey == monthKey },
                allGroupCardEvents = if (activeGroup == null) emptyList() else cached.cardEvents,
                allGroupMonthlyBalances = if (activeGroup == null) emptyList() else cached.monthlyBalances,
                childNotes = cachedNotes,
                selectedMonthKey = monthKey,
                selectedWeekKey = weekKey,
                currentWeekKey = weekKey,
                shouldPromptPhoneOnboarding = localPrefs != null && activeGroup != null && !onboardingDone,
                ownPrimaryPhone = ownPrimary,
                ownSecondaryPhone = ownSecondary,
                ownPhoneHidden = ownHidden,
                ownAvatarHash = ownAvatarHash
            )
        }

        /**
         * Detects and repairs blocks that accidentally inherited `group.adminId` when the Admin
         * created blocks for other servants without selecting a member UID.
         */
        fun sanitizeLegacyAdminBlocks(
            rawBlocks: List<ServantBlock>,
            activeGroup: Group?,
            members: List<ServantMemberProfile>,
            profile: UserProfile?
        ): Pair<List<ServantBlock>, List<ServantBlock>> {
            if (activeGroup == null || rawBlocks.isEmpty() || activeGroup.adminId.isBlank()) {
                return rawBlocks to emptyList()
            }
            val adminId = activeGroup.adminId.trim()
            val adminBlocks = rawBlocks.filter { it.servantId.trim() == adminId }
            if (adminBlocks.size <= 1) {
                return rawBlocks to emptyList()
            }

            val adminMemberName = members.firstOrNull { it.effectiveUid == adminId }?.displayName?.trim().orEmpty()
            val adminProfileName = if (profile?.userId == adminId) profile.displayName.trim() else ""
            val adminGroupName = activeGroup.adminName.trim()
            val knownAdminNamesNorm = listOf(adminMemberName, adminProfileName, adminGroupName)
                .filter { it.isNotBlank() }
                .map { ArabicInitialsHelper.normalizeArabicName(it) }
                .filter { it.isNotBlank() }
                .toSet()

            val primaryAdminBlock = adminBlocks.firstOrNull { blk ->
                val normBlk = ArabicInitialsHelper.normalizeArabicName(blk.servantName)
                normBlk.isNotBlank() && normBlk in knownAdminNamesNorm
            } ?: adminBlocks.minWithOrNull(compareBy<ServantBlock> { it.sortOrder }.thenBy { it.id })

            val repairedList = ArrayList<ServantBlock>(rawBlocks.size)
            val cloudRepairs = ArrayList<ServantBlock>()

            for (blk in rawBlocks) {
                if (blk.servantId.trim() == adminId && blk.id != primaryAdminBlock?.id) {
                    val normBlkName = ArabicInitialsHelper.normalizeArabicName(blk.servantName)
                    val isExplicitlyAdminName = normBlkName.isNotBlank() && normBlkName in knownAdminNamesNorm
                    if (!isExplicitlyAdminName) {
                        val matchedMember = members.firstOrNull { member ->
                            val mUid = member.effectiveUid.trim()
                            mUid.isNotBlank() &&
                                mUid != adminId &&
                                normBlkName.isNotBlank() &&
                                ArabicInitialsHelper.normalizeArabicName(member.displayName) == normBlkName
                        }
                        val fixedServantId = matchedMember?.effectiveUid?.trim().orEmpty()
                        val fixedBlock = blk.copy(servantId = fixedServantId)
                        repairedList.add(fixedBlock)
                        cloudRepairs.add(fixedBlock)
                        continue
                    }
                }
                repairedList.add(blk)
            }
            return repairedList to cloudRepairs
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
