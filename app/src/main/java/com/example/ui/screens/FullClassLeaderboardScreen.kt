package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ArabicInitialsHelper
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.CardEvent
import com.example.data.model.CardType
import com.example.data.model.CustomCardConfig
import com.example.data.model.SaturdayCalendarHelper
import com.example.data.model.ServantBlock
import com.example.data.model.Student
import com.example.ui.components.StudentAvatarCircle
import com.example.ui.theme.KamSemanticColors
import com.example.ui.theme.LocalKamExtendedColors
import com.example.ui.viewmodel.KamScreenState
import com.example.ui.viewmodel.KamViewModel
import com.example.ui.viewmodel.StudentCardTypeBreakdown
import com.example.ui.viewmodel.StudentDetailedLeaderboardEntry
import com.example.util.KamPerformanceTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LeaderboardSortFilter(val arabicLabel: String) {
    HIGHEST_TO_LOWEST("كل الفصل"),
    ACTIVE_ONLY("متفاعلين"),
    ABSENT_ZERO_ATTENDANCE("غائبين"),
    WEAKEST_FIRST("ضعفاء")
}

/**
 * Dedicated Full-Screen Leaderboard & Class Points/Attendance Breakdown Screen (`FullClassLeaderboardScreen`).
 * - Compact top bar with a single DateRangePicker button (e.g. "٣ نوفمبر ← ١١ نوفمبر") and a small "إعادة" reset button.
 * - Computes points strictly from `cardEvents` by `createdAt` (using the existing listener when inside the current month,
 *   or a single cached `get()` when the range extends outside the current month, with automatic `weekKey`/`monthKey` fallback).
 * - Merges the 4 summary counters directly with the filters, keeps blocks in 1 horizontal row, and uses a compact search bar
 *   so the first student card appears immediately on screen without scrolling.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullClassLeaderboardScreen(
    state: KamScreenState,
    onBackToHome: () -> Unit,
    onOpenStudentDetails: (Student) -> Unit
) {
    BackHandler(
        enabled = com.example.ui.theme.LocalIsTabActive.current,
        onBack = onBackToHome
    )

    LaunchedEffect(Unit) {
        KamPerformanceTracker.setInteractionContext(KamPerformanceTracker.CTX_LEADERBOARD, scrolling = false)
    }

    val initialMonth = SaturdayCalendarHelper.clampMonthKey(
        state.selectedMonthKey.ifBlank { SaturdayCalendarHelper.currentYearMonth() }
    )

    val customCardConfig = state.customCardConfig
    val thisWeekKey = remember { SaturdayCalendarHelper.weekKeyWeeksAgo(0) }
    val lastWeekKey = remember { SaturdayCalendarHelper.weekKeyWeeksAgo(1) }
    val twoWeeksAgoKey = remember { SaturdayCalendarHelper.weekKeyWeeksAgo(2) }

    var selectedMonthKeys by rememberSaveable { mutableStateOf(setOf(initialMonth)) }
    var selectedWeekKeys by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var showCalendarTreeDialog by rememberSaveable { mutableStateOf(false) }

    var sortFilter by rememberSaveable { mutableStateOf(LeaderboardSortFilter.HIGHEST_TO_LOWEST) }
    // Multi-select filters: empty set = All
    var selectedBlockFilterIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var selectedStudentIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var selectedCardTypeIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var attendanceOnlyMode by rememberSaveable { mutableStateOf(false) }
    var showStudentPickerDialog by rememberSaveable { mutableStateOf(false) }
    var localSearchQuery by rememberSaveable { mutableStateOf("") }
    var debouncedLocalSearchQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(localSearchQuery) {
        val trimmed = localSearchQuery.trim()
        if (trimmed.isEmpty()) {
            debouncedLocalSearchQuery = ""
        } else {
            delay(180L)
            debouncedLocalSearchQuery = trimmed
        }
    }

    val isCustomSelection = selectedMonthKeys != setOf(initialMonth) || selectedWeekKeys.isNotEmpty()
    val hasActiveMultiFilters = selectedBlockFilterIds.isNotEmpty() ||
        selectedStudentIds.isNotEmpty() ||
        selectedCardTypeIds.isNotEmpty() ||
        attendanceOnlyMode
    val isWithinCurrentMonth = remember(selectedMonthKeys, selectedWeekKeys, initialMonth) {
        (selectedMonthKeys.isEmpty() || selectedMonthKeys == setOf(initialMonth)) &&
            (selectedWeekKeys.isEmpty() || selectedWeekKeys.all { it.startsWith(initialMonth) })
    }

    // Fetch out-of-month historical events via a single one-shot get() (cached locally) ONLY when selection exits current month
    var historicalExtraEvents by remember { mutableStateOf<List<CardEvent>>(emptyList()) }
    var isFetchingHistoricalRange by remember { mutableStateOf(false) }

    LaunchedEffect(selectedMonthKeys, selectedWeekKeys, isWithinCurrentMonth, state.group?.inviteCode) {
        if (isWithinCurrentMonth) {
            historicalExtraEvents = emptyList()
            isFetchingHistoricalRange = false
        } else {
            val groupId = state.group?.inviteCode.orEmpty()
            val fetcher = KamViewModel.historicalRangeFetcher
            if (groupId.isNotBlank() && fetcher != null) {
                isFetchingHistoricalRange = true
                val (startBound, endBound) = SaturdayCalendarHelper.computeBoundsForSelection(selectedMonthKeys, selectedWeekKeys)
                historicalExtraEvents = withContext(Dispatchers.IO) {
                    fetcher(groupId, startBound, endBound)
                }
                isFetchingHistoricalRange = false
            }
        }
    }

    // Fast path: if already cached in PrecomputedClassroomIndices, return in 0ms without launching a coroutine
    val initialCachedEntries = remember(selectedMonthKeys, selectedWeekKeys, historicalExtraEvents.size, state) {
        KamPerformanceTracker.markLeaderboardOpenStart()
        if (state.isLeaderboardSelectionCached(selectedMonthKeys, selectedWeekKeys, historicalExtraEvents.size)) {
            val cached = state.computeClassLeaderboardEntriesForSelection(
                selectedMonthKeys = selectedMonthKeys,
                selectedWeekKeys = selectedWeekKeys,
                extraHistoricalEvents = historicalExtraEvents
            )
            KamPerformanceTracker.markLeaderboardOpenReady(cached.size, fromCache = true)
            cached
        } else {
            emptyList()
        }
    }

    val allRankedEntries by produceState(
        initialValue = initialCachedEntries,
        state.students,
        state.effectiveAllCardEvents,
        state.enabledCardTypes,
        customCardConfig,
        selectedMonthKeys,
        selectedWeekKeys,
        historicalExtraEvents
    ) {
        if (state.isLeaderboardSelectionCached(selectedMonthKeys, selectedWeekKeys, historicalExtraEvents.size)) {
            value = state.computeClassLeaderboardEntriesForSelection(
                selectedMonthKeys = selectedMonthKeys,
                selectedWeekKeys = selectedWeekKeys,
                extraHistoricalEvents = historicalExtraEvents
            )
        } else {
            val computed = withContext(Dispatchers.Default) {
                state.computeClassLeaderboardEntriesForSelection(
                    selectedMonthKeys = selectedMonthKeys,
                    selectedWeekKeys = selectedWeekKeys,
                    extraHistoricalEvents = historicalExtraEvents
                )
            }
            KamPerformanceTracker.markLeaderboardOpenReady(computed.size, fromCache = false)
            value = computed
        }
    }

    // Actual AttendanceRecords in the selected period (separate from the Attendance Card)
    val periodAttendanceRecords = remember(state.allAttendanceRecords, selectedMonthKeys, selectedWeekKeys) {
        state.attendanceRecordsForSelection(selectedMonthKeys, selectedWeekKeys)
    }
    val attendanceByStudentId = remember(periodAttendanceRecords) {
        periodAttendanceRecords.groupBy { it.studentId }
    }

    // Available card types for the multi-card filter bar (includes CUSTOM_ELDER when enabled or has events)
    val availableFilterCardTypes = remember(state.enabledCardTypes, customCardConfig, allRankedEntries) {
        val hasCustomEvents = allRankedEntries.any { entry ->
            entry.breakdownByCard.any { it.cardType == CardType.CUSTOM_ELDER && it.count > 0 }
        }
        CardType.entries.filter { card ->
            if (card == CardType.CUSTOM_ELDER) {
                customCardConfig.enabled || hasCustomEvents
            } else {
                true
            }
        }
    }

    // Entries projected by selectedCardTypeIds (when multi-card filter is active)
    val cardProjectedEntries = remember(allRankedEntries, selectedCardTypeIds, attendanceOnlyMode) {
        if (selectedCardTypeIds.isEmpty() || attendanceOnlyMode) {
            allRankedEntries
        } else {
            allRankedEntries
                .map { entry ->
                    val filteredBreakdown = entry.breakdownByCard.filter { it.cardType.id in selectedCardTypeIds }
                    val pts = filteredBreakdown.sumOf { it.points }
                    val cnt = filteredBreakdown.sumOf { it.count }
                    entry.copy(
                        totalPoints = pts,
                        totalCardsCount = cnt,
                        breakdownByCard = filteredBreakdown
                    )
                }
                .sortedWith(
                    compareByDescending<StudentDetailedLeaderboardEntry> { it.totalPoints }
                        .thenByDescending { it.totalCardsCount }
                        .thenByDescending { it.attendanceCount }
                        .thenBy { ArabicInitialsHelper.normalizeArabicName(it.student.name) }
                        .thenBy { it.student.studentNumber }
                )
                .mapIndexed { idx, entry -> entry.copy(rank = idx + 1) }
        }
    }

    // Scope before sortFilter (blocks + selected students + search) — used for bottom summary accuracy
    val scopedEntries = remember(
        cardProjectedEntries,
        selectedBlockFilterIds,
        selectedStudentIds,
        debouncedLocalSearchQuery
    ) {
        val byBlocks = if (selectedBlockFilterIds.isEmpty()) {
            cardProjectedEntries
        } else {
            cardProjectedEntries.filter { entry ->
                val blkKey = entry.student.blockId.ifBlank { "UNASSIGNED" }
                blkKey in selectedBlockFilterIds
            }
        }
        val byStudents = if (selectedStudentIds.isEmpty()) {
            byBlocks
        } else {
            byBlocks.filter { it.student.id in selectedStudentIds }
        }
        val query = debouncedLocalSearchQuery.trim()
        if (query.isBlank()) {
            byStudents
        } else {
            val normQuery = ArabicInitialsHelper.normalizeArabicName(query)
            byStudents.filter {
                ArabicInitialsHelper.normalizeArabicName(it.student.name).contains(normQuery, ignoreCase = true) ||
                    it.student.studentNumber.toString().contains(query)
            }
        }
    }

    // Classroom-wide summary counts (merged directly into the 4 filter buttons)
    val totalStudentsCount = scopedEntries.size
    val activeStudentsCount by remember(scopedEntries, attendanceOnlyMode, attendanceByStudentId) {
        derivedStateOf {
            if (attendanceOnlyMode) {
                scopedEntries.count { entry ->
                    attendanceByStudentId[entry.student.id].orEmpty().any { it.attendanceStatus == AttendanceStatus.PRESENT }
                }
            } else {
                scopedEntries.count { it.totalPoints > 0L }
            }
        }
    }
    val absentStudentsCount by remember(scopedEntries, attendanceOnlyMode, attendanceByStudentId) {
        derivedStateOf {
            if (attendanceOnlyMode) {
                scopedEntries.count { entry ->
                    val recs = attendanceByStudentId[entry.student.id].orEmpty()
                    recs.none { it.attendanceStatus == AttendanceStatus.PRESENT }
                }
            } else {
                scopedEntries.count { it.attendanceCount == 0 }
            }
        }
    }
    val zeroPointStudentsCount by remember(scopedEntries) {
        derivedStateOf { scopedEntries.count { it.totalPoints == 0L } }
    }

    // Apply servant filters with tie-breaking
    val filteredEntries = remember(
        scopedEntries,
        sortFilter,
        attendanceOnlyMode,
        attendanceByStudentId
    ) {
        if (attendanceOnlyMode) {
            val base = when (sortFilter) {
                LeaderboardSortFilter.HIGHEST_TO_LOWEST -> scopedEntries
                LeaderboardSortFilter.ACTIVE_ONLY -> scopedEntries.filter { entry ->
                    attendanceByStudentId[entry.student.id].orEmpty().any { it.attendanceStatus == AttendanceStatus.PRESENT }
                }
                LeaderboardSortFilter.ABSENT_ZERO_ATTENDANCE -> scopedEntries.filter { entry ->
                    attendanceByStudentId[entry.student.id].orEmpty().none { it.attendanceStatus == AttendanceStatus.PRESENT }
                }
                LeaderboardSortFilter.WEAKEST_FIRST -> scopedEntries
            }
            base.sortedWith(
                if (sortFilter == LeaderboardSortFilter.WEAKEST_FIRST) {
                    compareBy<StudentDetailedLeaderboardEntry> { entry ->
                        attendanceByStudentId[entry.student.id].orEmpty().count { it.attendanceStatus == AttendanceStatus.PRESENT }
                    }.thenBy { ArabicInitialsHelper.normalizeArabicName(it.student.name) }
                } else {
                    compareByDescending<StudentDetailedLeaderboardEntry> { entry ->
                        attendanceByStudentId[entry.student.id].orEmpty().count { it.attendanceStatus == AttendanceStatus.PRESENT }
                    }.thenBy { entry ->
                        attendanceByStudentId[entry.student.id].orEmpty().count { it.attendanceStatus != AttendanceStatus.PRESENT }
                    }.thenBy { ArabicInitialsHelper.normalizeArabicName(it.student.name) }
                }
            ).mapIndexed { idx, entry -> entry.copy(rank = idx + 1) }
        } else {
            when (sortFilter) {
                LeaderboardSortFilter.HIGHEST_TO_LOWEST -> scopedEntries
                LeaderboardSortFilter.ACTIVE_ONLY -> scopedEntries.filter { it.totalPoints > 0L }
                LeaderboardSortFilter.ABSENT_ZERO_ATTENDANCE -> scopedEntries
                    .filter { it.attendanceCount == 0 }
                    .sortedWith(
                        compareByDescending<StudentDetailedLeaderboardEntry> { it.totalPoints }
                            .thenBy { ArabicInitialsHelper.normalizeArabicName(it.student.name) }
                            .thenBy { it.student.studentNumber }
                    )
                LeaderboardSortFilter.WEAKEST_FIRST -> scopedEntries.sortedWith(
                    compareBy<StudentDetailedLeaderboardEntry> { it.totalPoints }
                        .thenBy { it.attendanceCount }
                        .thenBy { it.attendancePoints }
                        .thenBy { ArabicInitialsHelper.normalizeArabicName(it.student.name) }
                        .thenBy { it.student.studentNumber }
                )
            }
        }
    }

    // Bottom Period Summary Metrics
    val summaryPresentCount by remember(scopedEntries, attendanceByStudentId) {
        derivedStateOf {
            scopedEntries.count { entry ->
                attendanceByStudentId[entry.student.id].orEmpty().any { it.attendanceStatus == AttendanceStatus.PRESENT }
            }
        }
    }
    val summaryAbsentCount by remember(scopedEntries, attendanceByStudentId) {
        derivedStateOf {
            scopedEntries.count { entry ->
                val recs = attendanceByStudentId[entry.student.id].orEmpty()
                recs.isNotEmpty() && recs.none { it.attendanceStatus == AttendanceStatus.PRESENT }
            }
        }
    }
    val summaryUnrecordedCount = (scopedEntries.size - summaryPresentCount - summaryAbsentCount).coerceAtLeast(0)
    val summaryFilteredPeriodPoints by remember(filteredEntries) {
        derivedStateOf { filteredEntries.sumOf { it.totalPoints } }
    }
    val summaryAppTotalPeriodPoints by remember(allRankedEntries) {
        derivedStateOf { allRankedEntries.sumOf { it.totalPoints } }
    }

    val formattedSelectionLabel = remember(selectedMonthKeys, selectedWeekKeys) {
        SaturdayCalendarHelper.formatLeaderboardSelectionLabel(selectedMonthKeys, selectedWeekKeys)
    }

    val blockById = remember(state.blocks) { state.blocks.associateBy { it.id } }

    // Multi-Select Calendar Tree Modal Dialog (UNTOUCHED)
    if (showCalendarTreeDialog) {
        LeaderboardMultiSelectCalendarTreeDialog(
            initialMonthKeys = selectedMonthKeys,
            initialWeekKeys = selectedWeekKeys,
            currentMonthKey = initialMonth,
            onDismiss = { showCalendarTreeDialog = false },
            onConfirm = { pickedMonths, pickedWeeks ->
                selectedMonthKeys = if (pickedMonths.isEmpty() && pickedWeeks.isEmpty()) setOf(initialMonth) else pickedMonths
                selectedWeekKeys = pickedWeeks
                showCalendarTreeDialog = false
            }
        )
    }

    // Multi-Select Students Picker Dialog
    if (showStudentPickerDialog) {
        ReportMultiSelectStudentsDialog(
            students = state.students,
            blockById = blockById,
            initialSelectedIds = selectedStudentIds,
            onDismiss = { showStudentPickerDialog = false },
            onConfirm = { pickedIds ->
                selectedStudentIds = pickedIds
                showStudentPickerDialog = false
            }
        )
    }

    val extColors = LocalKamExtendedColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("full_class_leaderboard_screen")
    ) {
        // --- 1. Compact Top Header Bar (Title + Calendar Tree Button + Small Reset Button) ---
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBackToHome,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("leaderboard_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع للواجهة الرئيسية",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = extColors.rankGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "ترتيب الفصل",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Calendar Tree Trigger Button + Small "إعادة" Reset Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isCustomSelection) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clickable {
                                    selectedMonthKeys = setOf(initialMonth)
                                    selectedWeekKeys = emptySet()
                                }
                                .testTag("reset_leaderboard_period_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "إعادة للشهر الحالي",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "إعادة",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showCalendarTreeDialog = true }
                            .testTag("toggle_leaderboard_period_selector_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "اختيار الفترة",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = formattedSelectionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = extColors.hairline)

        if (isFetchingHistoricalRange) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }

        // --- 2. Compact Controls & Full Student List (First student card visible without scrolling!) ---
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // A. Quick Week Presets + Merged Filter/Counter Pills + Multi-Block + Multi-Student + Multi-Card/Attendance Filters
            item(key = "compact_filters_and_search") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Row 0: Quick Period Presets ("الأسبوع ده", "الأسبوع اللي فات", "الأسبوع اللي قبله", "الشهر الحالي")
                    val isThisWeek = selectedMonthKeys.isEmpty() && selectedWeekKeys == setOf(thisWeekKey)
                    val isLastWeek = selectedMonthKeys.isEmpty() && selectedWeekKeys == setOf(lastWeekKey)
                    val isTwoWeeksAgo = selectedMonthKeys.isEmpty() && selectedWeekKeys == setOf(twoWeeksAgoKey)
                    val isCurrentMonthDefault = selectedMonthKeys == setOf(initialMonth) && selectedWeekKeys.isEmpty()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BlockFilterChip(
                            label = "الأسبوع ده",
                            selected = isThisWeek,
                            accentColor = MaterialTheme.colorScheme.primary,
                            onClick = {
                                selectedMonthKeys = emptySet()
                                selectedWeekKeys = setOf(thisWeekKey)
                            },
                            testTag = "quick_period_this_week"
                        )
                        BlockFilterChip(
                            label = "الأسبوع اللي فات",
                            selected = isLastWeek,
                            accentColor = MaterialTheme.colorScheme.primary,
                            onClick = {
                                selectedMonthKeys = emptySet()
                                selectedWeekKeys = setOf(lastWeekKey)
                            },
                            testTag = "quick_period_last_week"
                        )
                        BlockFilterChip(
                            label = "الأسبوع اللي قبله",
                            selected = isTwoWeeksAgo,
                            accentColor = MaterialTheme.colorScheme.primary,
                            onClick = {
                                selectedMonthKeys = emptySet()
                                selectedWeekKeys = setOf(twoWeeksAgoKey)
                            },
                            testTag = "quick_period_two_weeks_ago"
                        )
                        BlockFilterChip(
                            label = "الشهر الحالي",
                            selected = isCurrentMonthDefault,
                            accentColor = extColors.rankGold,
                            onClick = {
                                selectedMonthKeys = setOf(initialMonth)
                                selectedWeekKeys = emptySet()
                            },
                            testTag = "quick_period_current_month"
                        )
                    }

                    // Row 1: Merged Stats & Sort/Filter Pills
                    ClassroomHealthSummaryCard(
                        totalStudents = totalStudentsCount,
                        activeStudents = activeStudentsCount,
                        absentStudents = absentStudentsCount,
                        zeroPointStudents = zeroPointStudentsCount,
                        activeSortFilter = sortFilter,
                        onSelectSortFilter = { sortFilter = it }
                    )

                    // Row 2: Multi-Select Servant Blocks + Multi-Select Students Picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BlockFilterChip(
                            label = if (selectedStudentIds.isEmpty()) {
                                "👥 تحديد أطفال"
                            } else {
                                "👥 أطفال (${selectedStudentIds.size})"
                            },
                            selected = selectedStudentIds.isNotEmpty(),
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            onClick = { showStudentPickerDialog = true },
                            testTag = "report_multi_student_picker_chip"
                        )
                        BlockFilterChip(
                            label = "كل البلوكات (${allRankedEntries.size})",
                            selected = selectedBlockFilterIds.isEmpty(),
                            accentColor = MaterialTheme.colorScheme.primary,
                            onClick = { selectedBlockFilterIds = emptySet() },
                            testTag = "report_block_all_chip"
                        )
                        state.blocks.forEach { block ->
                            val blockColor = KamSemanticColors.blockColor(block.colorHex)
                            val countInBlock = allRankedEntries.count { it.student.blockId == block.id }
                            val isBlockPicked = block.id in selectedBlockFilterIds
                            BlockFilterChip(
                                label = "${if (isBlockPicked) "✓ " else ""}${block.servantName} ($countInBlock)",
                                selected = isBlockPicked,
                                accentColor = blockColor,
                                onClick = {
                                    selectedBlockFilterIds = if (isBlockPicked) {
                                        selectedBlockFilterIds - block.id
                                    } else {
                                        selectedBlockFilterIds + block.id
                                    }
                                },
                                testTag = "report_block_chip_${block.id}"
                            )
                        }
                        val unassignedCount = allRankedEntries.count { it.student.blockId.isBlank() }
                        if (unassignedCount > 0) {
                            val isUnassignedPicked = "UNASSIGNED" in selectedBlockFilterIds
                            BlockFilterChip(
                                label = "${if (isUnassignedPicked) "✓ " else ""}بدون بلوك ($unassignedCount)",
                                selected = isUnassignedPicked,
                                accentColor = KamSemanticColors.unassignedBlock,
                                onClick = {
                                    selectedBlockFilterIds = if (isUnassignedPicked) {
                                        selectedBlockFilterIds - "UNASSIGNED"
                                    } else {
                                        selectedBlockFilterIds + "UNASSIGNED"
                                    }
                                },
                                testTag = "report_block_chip_unassigned"
                            )
                        }
                    }

                    // Row 3: Multi-Select Card Types + "الحضور لوحده" (Attendance Only)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BlockFilterChip(
                            label = "${if (attendanceOnlyMode) "✓ " else ""}📋 الحضور لوحده",
                            selected = attendanceOnlyMode,
                            accentColor = extColors.onlineGreen,
                            onClick = {
                                val next = !attendanceOnlyMode
                                attendanceOnlyMode = next
                                if (next) selectedCardTypeIds = emptySet()
                            },
                            testTag = "report_attendance_only_chip"
                        )
                        BlockFilterChip(
                            label = "كل الكروت",
                            selected = !attendanceOnlyMode && selectedCardTypeIds.isEmpty(),
                            accentColor = MaterialTheme.colorScheme.primary,
                            onClick = {
                                attendanceOnlyMode = false
                                selectedCardTypeIds = emptySet()
                            },
                            testTag = "report_all_cards_chip"
                        )
                        availableFilterCardTypes.forEach { card ->
                            val isCardSelected = !attendanceOnlyMode && card.id in selectedCardTypeIds
                            val cardAccent = KamSemanticColors.cardColor(card, customCardConfig)
                            val cardIcon = card.displaySymbol(customCardConfig)
                            val cardTitle = card.displayTitle(customCardConfig)
                            BlockFilterChip(
                                label = "${if (isCardSelected) "✓ " else ""}$cardIcon $cardTitle",
                                selected = isCardSelected,
                                accentColor = cardAccent,
                                onClick = {
                                    attendanceOnlyMode = false
                                    selectedCardTypeIds = if (isCardSelected) {
                                        selectedCardTypeIds - card.id
                                    } else {
                                        selectedCardTypeIds + card.id
                                    }
                                },
                                testTag = "report_card_filter_${card.id}"
                            )
                        }
                    }

                    // Active multi-filter clear strip when any advanced filter is engaged
                    if (hasActiveMultiFilters) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val parts = buildList {
                                if (selectedBlockFilterIds.isNotEmpty()) add("${selectedBlockFilterIds.size} بلوك")
                                if (selectedStudentIds.isNotEmpty()) add("${selectedStudentIds.size} طفل")
                                if (attendanceOnlyMode) add("الحضور لوحده")
                                else if (selectedCardTypeIds.isNotEmpty()) add("${selectedCardTypeIds.size} كارت")
                            }
                            Text(
                                text = "فلاتر نشطة: ${parts.joinToString(" • ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "مسح الفلاتر",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        selectedBlockFilterIds = emptySet()
                                        selectedStudentIds = emptySet()
                                        selectedCardTypeIds = emptySet()
                                        attendanceOnlyMode = false
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("clear_report_multi_filters_button")
                            )
                        }
                    }

                    // Row 4: Compact 38.dp Search Field
                    CompactLeaderboardSearchBar(
                        query = localSearchQuery,
                        onQueryChange = { localSearchQuery = it }
                    )
                }
            }

            // B. Full Student List with Detailed Per-Card Points & Counts Breakdown (or Attendance-Only View)
            if (filteredEntries.isEmpty()) {
                item(key = "empty_leaderboard_state") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "لا يوجد طلاب مطابقون للفلتر المختار",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredEntries,
                    key = { it.student.id },
                    contentType = { "leaderboard_student_card" }
                ) { entry ->
                    val ownerBlock = blockById[entry.student.blockId]
                    val studentAttendance = attendanceByStudentId[entry.student.id].orEmpty()
                    StudentDetailedRankCard(
                        entry = entry,
                        ownerBlock = ownerBlock,
                        customCardConfig = customCardConfig,
                        attendanceOnlyMode = attendanceOnlyMode,
                        studentAttendanceRecords = studentAttendance,
                        onClick = { onOpenStudentDetails(entry.student) }
                    )
                }
            }

            // C. Bottom Period Summary Card (Present, Absent, Filtered Period Points, App Total Period Points)
            item(key = "report_period_bottom_summary") {
                ReportPeriodBottomSummaryCard(
                    periodLabel = formattedSelectionLabel,
                    presentCount = summaryPresentCount,
                    absentCount = summaryAbsentCount,
                    unrecordedCount = summaryUnrecordedCount,
                    filteredPeriodPoints = summaryFilteredPeriodPoints,
                    appTotalPeriodPoints = summaryAppTotalPeriodPoints
                )
            }
        }
    }
}

@Composable
private fun CompactLeaderboardSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    val extColors = LocalKamExtendedColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extColors.hairline),
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "ابحث باسم الطالب أو رقمه...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("leaderboard_student_search_input")
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "مسح البحث",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onQueryChange("") }
                )
            }
        }
    }
}

@Composable
private fun BlockFilterChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String = ""
) {
    val extColors = LocalKamExtendedColors.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) accentColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accentColor else extColors.hairline
        ),
        modifier = Modifier
            .clickable(onClick = onClick)
            .let { if (testTag.isNotBlank()) it.testTag(testTag) else it }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ClassroomHealthSummaryCard(
    totalStudents: Int,
    activeStudents: Int,
    absentStudents: Int,
    zeroPointStudents: Int,
    activeSortFilter: LeaderboardSortFilter,
    onSelectSortFilter: (LeaderboardSortFilter) -> Unit
) {
    val extColors = LocalKamExtendedColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SummaryStatPill(
            title = "كل الفصل",
            value = "$totalStudents",
            accentColor = MaterialTheme.colorScheme.primary,
            isSelected = activeSortFilter == LeaderboardSortFilter.HIGHEST_TO_LOWEST,
            onClick = { onSelectSortFilter(LeaderboardSortFilter.HIGHEST_TO_LOWEST) },
            testTag = "leaderboard_filter_HIGHEST_TO_LOWEST",
            modifier = Modifier.weight(1f)
        )
        SummaryStatPill(
            title = "متفاعلين",
            value = "$activeStudents",
            accentColor = extColors.onlineGreen,
            isSelected = activeSortFilter == LeaderboardSortFilter.ACTIVE_ONLY,
            onClick = { onSelectSortFilter(LeaderboardSortFilter.ACTIVE_ONLY) },
            testTag = "leaderboard_filter_ACTIVE_ONLY",
            modifier = Modifier.weight(1f)
        )
        SummaryStatPill(
            title = "غائبين",
            value = "$absentStudents",
            accentColor = MaterialTheme.colorScheme.error,
            isSelected = activeSortFilter == LeaderboardSortFilter.ABSENT_ZERO_ATTENDANCE,
            onClick = { onSelectSortFilter(LeaderboardSortFilter.ABSENT_ZERO_ATTENDANCE) },
            testTag = "leaderboard_filter_ABSENT_ZERO_ATTENDANCE",
            modifier = Modifier.weight(1f)
        )
        SummaryStatPill(
            title = "ضعفاء",
            value = "$zeroPointStudents",
            accentColor = extColors.offlineAmber,
            isSelected = activeSortFilter == LeaderboardSortFilter.WEAKEST_FIRST,
            onClick = { onSelectSortFilter(LeaderboardSortFilter.WEAKEST_FIRST) },
            testTag = "leaderboard_filter_WEAKEST_FIRST",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryStatPill(
    title: String,
    value: String,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accentColor else accentColor.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = accentColor,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Detailed Student Card inside the Full-Class Leaderboard:
 * - Row 1: Rank badge (#1, #2, #3...) + Avatar + Full Student Name (#number) + Servant Block + Total Points Pill.
 * - Row 2: Complete breakdown of every CardType showing exact points and number of times awarded.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentDetailedRankCard(
    entry: StudentDetailedLeaderboardEntry,
    ownerBlock: ServantBlock?,
    customCardConfig: CustomCardConfig,
    attendanceOnlyMode: Boolean,
    studentAttendanceRecords: List<AttendanceRecord>,
    onClick: () -> Unit
) {
    val extColors = LocalKamExtendedColors.current
    val student = entry.student
    val blockAccent = if (ownerBlock != null) {
        KamSemanticColors.blockColor(ownerBlock.colorHex)
    } else {
        KamSemanticColors.unassignedBlock
    }

    val presentRecCount = studentAttendanceRecords.count { it.attendanceStatus == AttendanceStatus.PRESENT }
    val absentRecCount = studentAttendanceRecords.count { it.attendanceStatus == AttendanceStatus.ABSENT }
    val issueRecCount = studentAttendanceRecords.count { it.attendanceStatus == AttendanceStatus.ISSUE }
    val isAbsent = if (attendanceOnlyMode) presentRecCount == 0 else entry.attendanceCount == 0
    val isZeroPoints = entry.totalPoints == 0L

    val rankColor = when (entry.rank) {
        1 -> extColors.rankGold
        2 -> extColors.rankSilver
        3 -> extColors.rankBronze
        else -> if (isZeroPoints && !attendanceOnlyMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    }

    val rankBadgeText = when (entry.rank) {
        1 -> "🥇 #1"
        2 -> "🥈 #2"
        3 -> "🥉 #3"
        else -> "#${entry.rank}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("leaderboard_student_card_${student.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (entry.rank <= 3 && (entry.totalPoints > 0L || presentRecCount > 0)) 1.5.dp else 1.dp,
            color = when {
                entry.rank == 1 && (entry.totalPoints > 0L || presentRecCount > 0) -> extColors.rankGold.copy(alpha = 0.72f)
                isAbsent -> MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                else -> blockAccent.copy(alpha = 0.35f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top Row: Rank + Avatar + Student Name & Block + Total Points (or Attendance Count) Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = rankColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, rankColor.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = rankBadgeText,
                            style = MaterialTheme.typography.labelMedium,
                            color = rankColor,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    StudentAvatarCircle(
                        student = student,
                        blockAccent = blockAccent,
                        size = 38.dp,
                        onClick = onClick,
                        testTag = "leaderboard_avatar_${student.id}",
                        blockLabel = ownerBlock?.let { "بلوك ${it.servantName}" } ?: "بدون بلوك"
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = student.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "رقم #${student.studentNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = ownerBlock?.servantName ?: "بدون بلوك",
                                style = MaterialTheme.typography.labelSmall,
                                color = blockAccent,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (attendanceOnlyMode) {
                    val attAccent = when {
                        presentRecCount > 0 -> extColors.onlineGreen
                        absentRecCount > 0 || issueRecCount > 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = attAccent.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, attAccent.copy(alpha = 0.65f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = when {
                                    presentRecCount > 0 -> "حضر ($presentRecCount)"
                                    absentRecCount > 0 || issueRecCount > 0 -> "غائب"
                                    else -> "لم يُسجل"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = attAccent,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "الحضور الفعلي",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isZeroPoints) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                        } else {
                            extColors.rankGold.copy(alpha = 0.16f)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isZeroPoints) MaterialTheme.colorScheme.error else extColors.rankGold
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${entry.totalPoints} نقطة",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isZeroPoints) MaterialTheme.colorScheme.error else extColors.rankGold,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${entry.totalCardsCount} كارت",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = extColors.hairline)

            if (attendanceOnlyMode) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    AttendanceStatusChip(
                        label = "✓ حضر: $presentRecCount",
                        accentColor = extColors.onlineGreen,
                        highlighted = presentRecCount > 0
                    )
                    AttendanceStatusChip(
                        label = "✕ غاب: $absentRecCount",
                        accentColor = MaterialTheme.colorScheme.error,
                        highlighted = absentRecCount > 0
                    )
                    if (issueRecCount > 0) {
                        AttendanceStatusChip(
                            label = "⚠ مشكلة: $issueRecCount",
                            accentColor = extColors.offlineAmber,
                            highlighted = true
                        )
                    }
                    if (studentAttendanceRecords.isEmpty()) {
                        AttendanceStatusChip(
                            label = "لم يُسجل حضوره في الفترة",
                            accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            highlighted = false
                        )
                    }
                }
            } else {
                // Detailed Points & Count for Every Card Type (including Custom Elder Card)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    entry.breakdownByCard.forEach { item ->
                        CardBreakdownChip(item = item, customCardConfig = customCardConfig)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceStatusChip(
    label: String,
    accentColor: Color,
    highlighted: Boolean
) {
    val extColors = LocalKamExtendedColors.current
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = if (highlighted) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, if (highlighted) accentColor.copy(alpha = 0.55f) else extColors.hairline)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun CardBreakdownChip(
    item: StudentCardTypeBreakdown,
    customCardConfig: CustomCardConfig
) {
    val extColors = LocalKamExtendedColors.current
    val cardColor = KamSemanticColors.cardColor(item.cardType, customCardConfig)
    val cardIcon = item.cardType.displaySymbol(customCardConfig)
    val cardTitle = item.cardType.displayTitle(customCardConfig)
    val isAttendanceZero = item.cardType == CardType.ATTENDANCE && item.count == 0
    val hasPoints = item.points > 0L

    val containerColor = when {
        isAttendanceZero -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        hasPoints -> cardColor.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val borderColor = when {
        isAttendanceZero -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        hasPoints -> cardColor.copy(alpha = 0.6f)
        else -> extColors.hairline
    }
    val textColor = when {
        isAttendanceZero -> MaterialTheme.colorScheme.error
        hasPoints -> cardColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val detailText = if (isAttendanceZero) {
        "$cardIcon $cardTitle: 0 (غائب)"
    } else if (item.count == 0) {
        "$cardIcon $cardTitle: 0 (0)"
    } else {
        "$cardIcon $cardTitle: ${item.points} نقطة (${item.count})"
    }

    Surface(
        shape = RoundedCornerShape(7.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = detailText,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = if (hasPoints || isAttendanceZero) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ReportPeriodBottomSummaryCard(
    periodLabel: String,
    presentCount: Int,
    absentCount: Int,
    unrecordedCount: Int,
    filteredPeriodPoints: Long,
    appTotalPeriodPoints: Long
) {
    val extColors = LocalKamExtendedColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .testTag("report_period_bottom_summary_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 ملخص الفترة المختارة",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = periodLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            HorizontalDivider(color = extColors.hairline)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportSummaryMetricTile(
                    label = "عدد الحاضرين",
                    value = "$presentCount",
                    accentColor = extColors.onlineGreen,
                    testTag = "summary_present_count",
                    modifier = Modifier.weight(1f)
                )
                ReportSummaryMetricTile(
                    label = "عدد الغايبين",
                    value = "$absentCount",
                    accentColor = MaterialTheme.colorScheme.error,
                    testTag = "summary_absent_count",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportSummaryMetricTile(
                    label = "نقاط الفلتر في الفترة",
                    value = "$filteredPeriodPoints",
                    accentColor = MaterialTheme.colorScheme.primary,
                    testTag = "summary_filtered_points",
                    modifier = Modifier.weight(1f)
                )
                ReportSummaryMetricTile(
                    label = "مجموع نقاط التطبيق كله",
                    value = "$appTotalPeriodPoints",
                    accentColor = extColors.rankGold,
                    testTag = "summary_app_total_points",
                    modifier = Modifier.weight(1f)
                )
            }

            if (unrecordedCount > 0) {
                Text(
                    text = "• لم يُسجل حضورهم أو غيابهم بعد في هذه الفترة: $unrecordedCount طفل",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReportSummaryMetricTile(
    label: String,
    value: String,
    accentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
        modifier = modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ReportMultiSelectStudentsDialog(
    students: List<Student>,
    blockById: Map<String, ServantBlock>,
    initialSelectedIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var draftIds by remember { mutableStateOf(initialSelectedIds) }
    var search by remember { mutableStateOf("") }

    val sortedFiltered = remember(students, search) {
        val q = search.trim()
        students
            .filter {
                q.isBlank() ||
                    it.name.contains(q, ignoreCase = true) ||
                    it.studentNumber.toString().contains(q)
            }
            .sortedBy { ArabicInitialsHelper.normalizeArabicName(it.name) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تحديد أطفال (${draftIds.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                if (draftIds.isNotEmpty()) {
                    TextButton(onClick = { draftIds = emptySet() }) {
                        Text("مسح الكل", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactLeaderboardSearchBar(
                    query = search,
                    onQueryChange = { search = it }
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sortedFiltered, key = { it.id }) { stu ->
                        val isChecked = stu.id in draftIds
                        val blockName = blockById[stu.blockId]?.servantName ?: "بدون بلوك"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    draftIds = if (isChecked) draftIds - stu.id else draftIds + stu.id
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    draftIds = if (checked) draftIds + stu.id else draftIds - stu.id
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${stu.name} (#${stu.studentNumber})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = blockName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(draftIds) },
                modifier = Modifier.testTag("confirm_multi_student_picker_button")
            ) {
                Text("تطبيق (${draftIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

/**
 * Interactive Multi-Select Calendar Tree Dialog for the Leaderboard:
 * - Starts strictly from Year 2026, Month 11 (November). Older months never exist.
 * - Dynamically computes forward into the future through [SaturdayCalendarHelper].
 * - Allows servants to check (✓) entire months, or expand any month to check/uncheck individual weeks independently.
 * - Includes quick actions: "كل الشهور السابقة" (selects all months up to now), "الشهر الحالي" (default), and "مسح الكل".
 */
@Composable
fun LeaderboardMultiSelectCalendarTreeDialog(
    initialMonthKeys: Set<String>,
    initialWeekKeys: Set<String>,
    currentMonthKey: String,
    onDismiss: () -> Unit,
    onConfirm: (selectedMonths: Set<String>, selectedWeeks: Set<String>) -> Unit
) {
    val availableYears = remember(currentMonthKey, initialMonthKeys, initialWeekKeys) {
        val extraYears = buildSet {
            currentMonthKey.substringBefore("-").toIntOrNull()?.let { add(it) }
            initialMonthKeys.forEach { it.substringBefore("-").toIntOrNull()?.let { y -> add(y) } }
            initialWeekKeys.forEach { it.substringBefore("-").toIntOrNull()?.let { y -> add(y) } }
        }
        SaturdayCalendarHelper.availableYears(extraYears = extraYears)
    }
    val initialYear = remember(currentMonthKey, availableYears) {
        currentMonthKey.substringBefore("-").toIntOrNull()
            ?: availableYears.firstOrNull()
            ?: SaturdayCalendarHelper.APP_START_YEAR
    }

    var expandedYear by remember(currentMonthKey) { mutableIntStateOf(initialYear) }
    var expandedMonthKey by remember(currentMonthKey) { mutableStateOf<String?>(currentMonthKey) }

    var draftMonthKeys by remember { mutableStateOf(initialMonthKeys.toSet()) }
    var draftWeekKeys by remember { mutableStateOf(initialWeekKeys.toSet()) }

    val yearTrees = remember(availableYears) {
        availableYears.associateWith { year -> SaturdayCalendarHelper.buildYearTree(year) }
    }

    val allPastAndCurrentMonths = remember(yearTrees, currentMonthKey) {
        yearTrees.values.flatMap { it.months }
            .filter { !it.isFutureMonth || it.isCurrentMonth }
            .map { it.monthKey }
            .toSet()
            .ifEmpty { setOf(currentMonthKey) }
    }

    val selectedCount = draftMonthKeys.size + draftWeekKeys.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "شجرة الشهور والأسابيع",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "علّم علامة صح (✓) على أي شهر بالكامل أو افتحه لاختيار أسابيع محددة",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick Action Presets Row: All Past Months | Current Month | Clear All
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .weight(1.2f)
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                draftMonthKeys = allPastAndCurrentMonths
                                draftWeekKeys = emptySet()
                            }
                            .testTag("select_all_past_months_button")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
                            Text(
                                text = "كل الشهور السابقة",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                draftMonthKeys = setOf(currentMonthKey)
                                draftWeekKeys = emptySet()
                            }
                            .testTag("select_current_month_only_button")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
                            Text(
                                text = "الشهر الحالي",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(0.8f)
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                draftMonthKeys = emptySet()
                                draftWeekKeys = emptySet()
                            }
                            .testTag("clear_all_calendar_selection_button")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
                            Text(
                                text = "مسح الكل",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }

                HorizontalDivider()

                val extColors = LocalKamExtendedColors.current
                // Year and Month Cards
                availableYears.forEach { year ->
                    val yearTree = yearTrees[year] ?: return@forEach
                    val isYearExpanded = expandedYear == year

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = extColors.elevatedSurface
                        ),
                        border = BorderStroke(1.dp, extColors.hairline)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Year Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedYear = if (isYearExpanded) -1 else year }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = yearTree.arabicYearLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (isYearExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Months inside Year
                            AnimatedVisibility(visible = isYearExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    yearTree.months.forEach { monthNode ->
                                        val isFullySelected = draftMonthKeys.contains(monthNode.monthKey)
                                        val selectedWeeksInMonth = monthNode.weeks.filter { draftWeekKeys.contains(it.weekKey) }
                                        val isPartiallySelected = !isFullySelected && selectedWeeksInMonth.isNotEmpty()
                                        val isMonthExpanded = expandedMonthKey == monthNode.monthKey

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = when {
                                                isFullySelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                isPartiallySelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                                                else -> MaterialTheme.colorScheme.surface
                                            },
                                            border = BorderStroke(
                                                width = if (isFullySelected || isPartiallySelected) 1.5.dp else 1.dp,
                                                color = when {
                                                    isFullySelected -> MaterialTheme.colorScheme.primary
                                                    isPartiallySelected -> MaterialTheme.colorScheme.secondary
                                                    else -> extColors.hairline
                                                }
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                // Month Row
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Checkbox & Month Name
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .heightIn(min = 44.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                val monthWeekKeys = monthNode.weeks.map { it.weekKey }.toSet()
                                                                if (isFullySelected) {
                                                                    draftMonthKeys = draftMonthKeys - monthNode.monthKey
                                                                    draftWeekKeys = draftWeekKeys - monthWeekKeys
                                                                } else {
                                                                    draftMonthKeys = draftMonthKeys + monthNode.monthKey
                                                                    draftWeekKeys = draftWeekKeys - monthWeekKeys
                                                                }
                                                            }
                                                            .padding(end = 6.dp)
                                                    ) {
                                                        Checkbox(
                                                            checked = isFullySelected,
                                                            onCheckedChange = null
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column {
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                Text(
                                                                    text = "${SaturdayCalendarHelper.toArabicDigits(monthNode.monthNumber)}. ${monthNode.arabicMonthName}",
                                                                    style = MaterialTheme.typography.titleSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                                if (monthNode.isCurrentMonth) {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(4.dp),
                                                                        color = MaterialTheme.colorScheme.primary
                                                                    ) {
                                                                        Text(
                                                                            text = "الحالي",
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            if (monthNode.copticMonthsLabel.isNotBlank()) {
                                                                Text(
                                                                    text = "قبطي: ${monthNode.copticMonthsLabel}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                            }
                                                            if (isPartiallySelected) {
                                                                Text(
                                                                    text = "محدد: ${SaturdayCalendarHelper.toArabicDigits(selectedWeeksInMonth.size)} من ${SaturdayCalendarHelper.toArabicDigits(monthNode.weeks.size)} أسابيع",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.secondary,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // Arrow button to expand/collapse weeks inside the month
                                                    IconButton(
                                                        onClick = {
                                                            expandedMonthKey = if (isMonthExpanded) null else monthNode.monthKey
                                                        },
                                                        modifier = Modifier.size(44.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isMonthExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                            contentDescription = "عرض أسابيع الشهر",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }

                                                // Expanded Weeks inside the Month
                                                AnimatedVisibility(visible = isMonthExpanded) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(start = 28.dp, end = 10.dp, bottom = 8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
                                                        monthNode.weeks.forEach { weekNode ->
                                                            val isWeekSelected = isFullySelected || draftWeekKeys.contains(weekNode.weekKey)

                                                            Surface(
                                                                shape = RoundedCornerShape(8.dp),
                                                                color = if (isWeekSelected) {
                                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                                } else {
                                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                                                },
                                                                border = if (isWeekSelected) {
                                                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                                                } else {
                                                                    null
                                                                },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .heightIn(min = 44.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .clickable {
                                                                        if (isFullySelected) {
                                                                            draftMonthKeys = draftMonthKeys - monthNode.monthKey
                                                                            val otherWeeks = monthNode.weeks
                                                                                .filter { it.weekKey != weekNode.weekKey }
                                                                                .map { it.weekKey }
                                                                                .toSet()
                                                                            draftWeekKeys = draftWeekKeys + otherWeeks
                                                                        } else if (draftWeekKeys.contains(weekNode.weekKey)) {
                                                                            draftWeekKeys = draftWeekKeys - weekNode.weekKey
                                                                        } else {
                                                                            val newWeeks = draftWeekKeys + weekNode.weekKey
                                                                            val allMonthWeeks = monthNode.weeks.map { it.weekKey }.toSet()
                                                                            if (allMonthWeeks.all { newWeeks.contains(it) }) {
                                                                                draftMonthKeys = draftMonthKeys + monthNode.monthKey
                                                                                draftWeekKeys = newWeeks - allMonthWeeks
                                                                            } else {
                                                                                draftWeekKeys = newWeeks
                                                                            }
                                                                        }
                                                                    }
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                                ) {
                                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                                        Checkbox(
                                                                            checked = isWeekSelected,
                                                                            onCheckedChange = null
                                                                        )
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Column {
                                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                                Text(
                                                                                    text = weekNode.arabicTitle,
                                                                                    style = MaterialTheme.typography.labelMedium,
                                                                                    fontWeight = FontWeight.Bold,
                                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                                )
                                                                                if (weekNode.isCurrentWeek) {
                                                                                    Surface(
                                                                                        shape = RoundedCornerShape(4.dp),
                                                                                        color = MaterialTheme.colorScheme.secondary
                                                                                    ) {
                                                                                        Text(
                                                                                            text = "الحالي ★",
                                                                                            style = MaterialTheme.typography.labelSmall,
                                                                                            color = MaterialTheme.colorScheme.onSecondary,
                                                                                            fontWeight = FontWeight.Bold,
                                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                                        )
                                                                                    }
                                                                                }
                                                                            }
                                                                            Text(
                                                                                text = "ميلادي: ${weekNode.dateRangeLabel}",
                                                                                style = MaterialTheme.typography.bodySmall,
                                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                            )
                                                                            if (weekNode.copticDateRangeLabel.isNotBlank()) {
                                                                                Text(
                                                                                    text = "قبطي: ${weekNode.copticDateRangeLabel}",
                                                                                    style = MaterialTheme.typography.labelSmall,
                                                                                    color = MaterialTheme.colorScheme.primary
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(draftMonthKeys, draftWeekKeys)
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_calendar_tree_selection_button")
            ) {
                Text(
                    text = if (selectedCount > 0) {
                        "تطبيق الاختيار (${SaturdayCalendarHelper.toArabicDigits(selectedCount)})"
                    } else {
                        "تطبيق الاختيار"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
