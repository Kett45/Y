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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.example.data.model.SaturdayCalendarHelper
import com.example.data.model.CardEvent
import com.example.data.model.CardType
import com.example.data.model.ServantBlock
import com.example.data.model.ServiceCalendarHelper
import com.example.data.model.Student
import com.example.ui.components.StudentAvatarCircle
import com.example.ui.theme.KamSemanticColors
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
    BackHandler(onBack = onBackToHome)

    LaunchedEffect(Unit) {
        KamPerformanceTracker.setInteractionContext(KamPerformanceTracker.CTX_LEADERBOARD, scrolling = false)
    }

    val initialMonth = SaturdayCalendarHelper.clampMonthKey(state.selectedMonthKey.ifBlank { "2026-11" })

    var selectedMonthKeys by rememberSaveable { mutableStateOf(setOf(initialMonth)) }
    var selectedWeekKeys by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var showCalendarTreeDialog by rememberSaveable { mutableStateOf(false) }

    var sortFilter by rememberSaveable { mutableStateOf(LeaderboardSortFilter.HIGHEST_TO_LOWEST) }
    var selectedBlockFilterId by rememberSaveable { mutableStateOf<String?>("ALL") }
    var localSearchQuery by rememberSaveable { mutableStateOf("") }

    val isCustomSelection = selectedMonthKeys != setOf(initialMonth) || selectedWeekKeys.isNotEmpty()
    val isWithinCurrentMonth = remember(selectedMonthKeys, selectedWeekKeys, initialMonth) {
        selectedMonthKeys == setOf(initialMonth) && (selectedWeekKeys.isEmpty() || selectedWeekKeys.all { it.startsWith(initialMonth) })
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

    // Classroom-wide summary counts (merged directly into the 4 filter buttons)
    val totalStudentsCount = allRankedEntries.size
    val activeStudentsCount by remember(allRankedEntries) { derivedStateOf { allRankedEntries.count { it.totalPoints > 0L } } }
    val absentStudentsCount by remember(allRankedEntries) { derivedStateOf { allRankedEntries.count { it.attendanceCount == 0 } } }
    val zeroPointStudentsCount by remember(allRankedEntries) { derivedStateOf { allRankedEntries.count { it.totalPoints == 0L } } }

    // Apply servant filters with tie-breaking: attendanceCount DESC -> attendancePoints DESC -> Arabic name ASC
    val filteredEntries = remember(
        allRankedEntries,
        sortFilter,
        selectedBlockFilterId,
        localSearchQuery
    ) {
        val byBlock = when (selectedBlockFilterId) {
            null, "ALL" -> allRankedEntries
            "UNASSIGNED" -> allRankedEntries.filter { it.student.blockId.isBlank() }
            else -> allRankedEntries.filter { it.student.blockId == selectedBlockFilterId }
        }
        val query = localSearchQuery.trim()
        val bySearch = if (query.isBlank()) {
            byBlock
        } else {
            byBlock.filter {
                it.student.name.contains(query, ignoreCase = true) ||
                    it.student.studentNumber.toString().contains(query)
            }
        }
        when (sortFilter) {
            LeaderboardSortFilter.HIGHEST_TO_LOWEST -> bySearch
            LeaderboardSortFilter.ACTIVE_ONLY -> bySearch.filter { it.totalPoints > 0L }
            LeaderboardSortFilter.ABSENT_ZERO_ATTENDANCE -> bySearch
                .filter { it.attendanceCount == 0 }
                .sortedWith(
                    compareByDescending<StudentDetailedLeaderboardEntry> { it.totalPoints }
                        .thenBy { ArabicInitialsHelper.normalizeArabicName(it.student.name) }
                        .thenBy { it.student.studentNumber }
                )
            LeaderboardSortFilter.WEAKEST_FIRST -> bySearch.sortedWith(
                compareBy<StudentDetailedLeaderboardEntry> { it.totalPoints }
                    .thenBy { it.attendanceCount }
                    .thenBy { it.attendancePoints }
                    .thenBy { ArabicInitialsHelper.normalizeArabicName(it.student.name) }
                    .thenBy { it.student.studentNumber }
            )
        }
    }

    val formattedSelectionLabel = remember(selectedMonthKeys, selectedWeekKeys) {
        SaturdayCalendarHelper.formatLeaderboardSelectionLabel(selectedMonthKeys, selectedWeekKeys)
    }

    val blockById = remember(state.blocks) { state.blocks.associateBy { it.id } }

    // Multi-Select Calendar Tree Modal Dialog
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("full_class_leaderboard_screen")
    ) {
        // --- 1. Compact Top Header Bar (Title + Calendar Tree Button + Small Reset Button) ---
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
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
                        tint = Color(0xFFF59E0B),
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
                            .clickable { showCalendarTreeDialog = true }
                            .testTag("toggle_leaderboard_period_selector_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "اختيار الفترة",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = formattedSelectionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        if (isFetchingHistoricalRange) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }

        // --- 2. Compact Controls & Full Student List (First student card visible without scrolling!) ---
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // A. Merged 4 Filter/Counter Pills + 1-Row Horizontal Block Filter + Compact Search Bar
            item(key = "compact_filters_and_search") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Row 1: Merged Stats & Sort/Filter Pills (No duplicate row below!)
                    ClassroomHealthSummaryCard(
                        totalStudents = totalStudentsCount,
                        activeStudents = activeStudentsCount,
                        absentStudents = absentStudentsCount,
                        zeroPointStudents = zeroPointStudentsCount,
                        activeSortFilter = sortFilter,
                        onSelectSortFilter = { sortFilter = it }
                    )

                    // Row 2: Single-Row Horizontal Scroll for Servant Blocks
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BlockFilterChip(
                            label = "الفصل (${allRankedEntries.size})",
                            selected = selectedBlockFilterId == "ALL",
                            accentColor = MaterialTheme.colorScheme.primary,
                            onClick = { selectedBlockFilterId = "ALL" }
                        )
                        state.blocks.forEach { block ->
                            val blockColor = KamSemanticColors.blockColor(block.colorHex)
                            val countInBlock = allRankedEntries.count { it.student.blockId == block.id }
                            BlockFilterChip(
                                label = "${block.servantName} ($countInBlock)",
                                selected = selectedBlockFilterId == block.id,
                                accentColor = blockColor,
                                onClick = { selectedBlockFilterId = block.id }
                            )
                        }
                        val unassignedCount = allRankedEntries.count { it.student.blockId.isBlank() }
                        if (unassignedCount > 0) {
                            BlockFilterChip(
                                label = "بدون بلوك ($unassignedCount)",
                                selected = selectedBlockFilterId == "UNASSIGNED",
                                accentColor = KamSemanticColors.unassignedBlock,
                                onClick = { selectedBlockFilterId = "UNASSIGNED" }
                            )
                        }
                    }

                    // Row 3: Compact 38.dp Search Field
                    CompactLeaderboardSearchBar(
                        query = localSearchQuery,
                        onQueryChange = { localSearchQuery = it }
                    )
                }
            }

            // B. Full Student List with Detailed Per-Card Points & Counts Breakdown
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
                    StudentDetailedRankCard(
                        entry = entry,
                        ownerBlock = ownerBlock,
                        onClick = { onOpenStudentDetails(entry.student) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactLeaderboardSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
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
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        modifier = Modifier.clickable(onClick = onClick)
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
            accentColor = Color(0xFF10B981),
            isSelected = activeSortFilter == LeaderboardSortFilter.ACTIVE_ONLY,
            onClick = { onSelectSortFilter(LeaderboardSortFilter.ACTIVE_ONLY) },
            testTag = "leaderboard_filter_ACTIVE_ONLY",
            modifier = Modifier.weight(1f)
        )
        SummaryStatPill(
            title = "غائبين",
            value = "$absentStudents",
            accentColor = Color(0xFFEF4444),
            isSelected = activeSortFilter == LeaderboardSortFilter.ABSENT_ZERO_ATTENDANCE,
            onClick = { onSelectSortFilter(LeaderboardSortFilter.ABSENT_ZERO_ATTENDANCE) },
            testTag = "leaderboard_filter_ABSENT_ZERO_ATTENDANCE",
            modifier = Modifier.weight(1f)
        )
        SummaryStatPill(
            title = "ضعفاء",
            value = "$zeroPointStudents",
            accentColor = Color(0xFFF59E0B),
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
    onClick: () -> Unit
) {
    val student = entry.student
    val blockAccent = if (ownerBlock != null) {
        KamSemanticColors.blockColor(ownerBlock.colorHex)
    } else {
        KamSemanticColors.unassignedBlock
    }

    val isAbsent = entry.attendanceCount == 0
    val isZeroPoints = entry.totalPoints == 0L

    val rankColor = when (entry.rank) {
        1 -> Color(0xFFF59E0B) // Gold
        2 -> Color(0xFF94A3B8) // Silver
        3 -> Color(0xFFD97706) // Bronze
        else -> if (isZeroPoints) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
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
            width = if (entry.rank <= 3 && entry.totalPoints > 0L) 1.5.dp else 1.dp,
            color = when {
                entry.rank == 1 && entry.totalPoints > 0L -> Color(0xFFF59E0B).copy(alpha = 0.75f)
                isAbsent -> Color(0xFFEF4444).copy(alpha = 0.45f)
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
            // Top Row: Rank + Avatar + Student Name & Block + Total Points Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Rank Badge
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

                // Total Points Pill on the side
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isZeroPoints) {
                        Color(0xFFEF4444).copy(alpha = 0.16f)
                    } else {
                        Color(0xFFF59E0B).copy(alpha = 0.20f)
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isZeroPoints) Color(0xFFEF4444) else Color(0xFFF59E0B)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${entry.totalPoints} نقطة",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isZeroPoints) Color(0xFFEF4444) else Color(0xFFF59E0B),
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Bottom Section: Detailed Points & Count for Every Card Type
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                entry.breakdownByCard.forEach { item ->
                    CardBreakdownChip(item = item)
                }
            }
        }
    }
}

@Composable
private fun CardBreakdownChip(item: StudentCardTypeBreakdown) {
    val cardColor = KamSemanticColors.cardColor(item.cardType)
    val isAttendanceZero = item.cardType == CardType.ATTENDANCE && item.count == 0
    val hasPoints = item.points > 0L

    val containerColor = when {
        isAttendanceZero -> Color(0xFFEF4444).copy(alpha = 0.14f)
        hasPoints -> cardColor.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val borderColor = when {
        isAttendanceZero -> Color(0xFFEF4444).copy(alpha = 0.65f)
        hasPoints -> cardColor.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    }
    val textColor = when {
        isAttendanceZero -> Color(0xFFEF4444)
        hasPoints -> cardColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val detailText = if (isAttendanceZero) {
        "${item.cardType.badgeSymbol} ${item.cardType.arabicTitle}: 0 (غائب)"
    } else if (item.count == 0) {
        "${item.cardType.badgeSymbol} ${item.cardType.arabicTitle}: 0 (0)"
    } else {
        "${item.cardType.badgeSymbol} ${item.cardType.arabicTitle}: ${item.points} نقطة (${item.count})"
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
    val availableYears = remember { SaturdayCalendarHelper.availableYears() }
    val initialYear = remember(currentMonthKey) {
        currentMonthKey.substringBefore("-").toIntOrNull()
            ?: availableYears.firstOrNull()
            ?: 2026
    }

    var expandedYear by rememberSaveable { mutableIntStateOf(initialYear) }
    var expandedMonthKey by rememberSaveable { mutableStateOf<String?>(currentMonthKey) }

    var draftMonthKeys by remember { mutableStateOf(initialMonthKeys.toMutableSet()) }
    var draftWeekKeys by remember { mutableStateOf(initialWeekKeys.toMutableSet()) }

    val yearTrees = remember(availableYears) {
        availableYears.associateWith { year -> SaturdayCalendarHelper.buildYearTree(year) }
    }

    val allPastAndCurrentMonths = remember(yearTrees, currentMonthKey) {
        yearTrees.values.flatMap { it.months }
            .filter { !it.isFutureMonth || it.isCurrentMonth }
            .map { it.monthKey }
            .toSet()
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
                            .clickable {
                                draftMonthKeys = allPastAndCurrentMonths.toMutableSet()
                                draftWeekKeys = mutableSetOf()
                            }
                            .testTag("select_all_past_months_button")
                    ) {
                        Text(
                            text = "كل الشهور السابقة",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            maxLines = 1
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                draftMonthKeys = mutableSetOf(currentMonthKey)
                                draftWeekKeys = mutableSetOf()
                            }
                            .testTag("select_current_month_only_button")
                    ) {
                        Text(
                            text = "الشهر الحالي",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            maxLines = 1
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(0.8f)
                            .clickable {
                                draftMonthKeys = mutableSetOf()
                                draftWeekKeys = mutableSetOf()
                            }
                            .testTag("clear_all_calendar_selection_button")
                    ) {
                        Text(
                            text = "مسح الكل",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            maxLines = 1
                        )
                    }
                }

                HorizontalDivider()

                // Year and Month Cards
                availableYears.forEach { year ->
                    val yearTree = yearTrees[year] ?: return@forEach
                    val isYearExpanded = expandedYear == year

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
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
                                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
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
                                                            .clickable {
                                                                if (isFullySelected) {
                                                                    draftMonthKeys = (draftMonthKeys - monthNode.monthKey).toMutableSet()
                                                                    draftWeekKeys = (draftWeekKeys - monthNode.weeks.map { it.weekKey }.toSet()).toMutableSet()
                                                                } else {
                                                                    draftMonthKeys = (draftMonthKeys + monthNode.monthKey).toMutableSet()
                                                                    draftWeekKeys = (draftWeekKeys - monthNode.weeks.map { it.weekKey }.toSet()).toMutableSet()
                                                                }
                                                            }
                                                    ) {
                                                        Checkbox(
                                                            checked = isFullySelected,
                                                            onCheckedChange = { checked ->
                                                                if (checked) {
                                                                    draftMonthKeys = (draftMonthKeys + monthNode.monthKey).toMutableSet()
                                                                    draftWeekKeys = (draftWeekKeys - monthNode.weeks.map { it.weekKey }.toSet()).toMutableSet()
                                                                } else {
                                                                    draftMonthKeys = (draftMonthKeys - monthNode.monthKey).toMutableSet()
                                                                    draftWeekKeys = (draftWeekKeys - monthNode.weeks.map { it.weekKey }.toSet()).toMutableSet()
                                                                }
                                                            },
                                                            modifier = Modifier.size(24.dp)
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
                                                        modifier = Modifier.size(36.dp)
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
                                                                    .clickable {
                                                                        if (isFullySelected) {
                                                                            draftMonthKeys = (draftMonthKeys - monthNode.monthKey).toMutableSet()
                                                                            val otherWeeks = monthNode.weeks
                                                                                .filter { it.weekKey != weekNode.weekKey }
                                                                                .map { it.weekKey }
                                                                            draftWeekKeys = (draftWeekKeys + otherWeeks).toMutableSet()
                                                                        } else if (draftWeekKeys.contains(weekNode.weekKey)) {
                                                                            draftWeekKeys = (draftWeekKeys - weekNode.weekKey).toMutableSet()
                                                                        } else {
                                                                            val newWeeks = (draftWeekKeys + weekNode.weekKey).toMutableSet()
                                                                            val allMonthWeeks = monthNode.weeks.map { it.weekKey }.toSet()
                                                                            if (allMonthWeeks.all { newWeeks.contains(it) }) {
                                                                                draftMonthKeys = (draftMonthKeys + monthNode.monthKey).toMutableSet()
                                                                                draftWeekKeys = (newWeeks - allMonthWeeks).toMutableSet()
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
                                                                            onCheckedChange = { checked ->
                                                                                if (checked) {
                                                                                    if (!isFullySelected) {
                                                                                        val newWeeks = (draftWeekKeys + weekNode.weekKey).toMutableSet()
                                                                                        val allMonthWeeks = monthNode.weeks.map { it.weekKey }.toSet()
                                                                                        if (allMonthWeeks.all { newWeeks.contains(it) }) {
                                                                                            draftMonthKeys = (draftMonthKeys + monthNode.monthKey).toMutableSet()
                                                                                            draftWeekKeys = (newWeeks - allMonthWeeks).toMutableSet()
                                                                                        } else {
                                                                                            draftWeekKeys = newWeeks
                                                                                        }
                                                                                    }
                                                                                } else {
                                                                                    if (isFullySelected) {
                                                                                        draftMonthKeys = (draftMonthKeys - monthNode.monthKey).toMutableSet()
                                                                                        val otherWeeks = monthNode.weeks
                                                                                            .filter { it.weekKey != weekNode.weekKey }
                                                                                            .map { it.weekKey }
                                                                                        draftWeekKeys = (draftWeekKeys + otherWeeks).toMutableSet()
                                                                                    } else {
                                                                                        draftWeekKeys = (draftWeekKeys - weekNode.weekKey).toMutableSet()
                                                                                    }
                                                                                }
                                                                            },
                                                                            modifier = Modifier.size(22.dp)
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
