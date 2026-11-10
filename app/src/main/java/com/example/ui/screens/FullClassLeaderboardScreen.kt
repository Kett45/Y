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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CardType
import com.example.data.model.ServantBlock
import com.example.data.model.Student
import com.example.ui.components.StudentAvatarCircle
import com.example.ui.theme.KamSemanticColors
import com.example.ui.viewmodel.KamScreenState
import com.example.ui.viewmodel.StudentCardTypeBreakdown
import com.example.ui.viewmodel.StudentDetailedLeaderboardEntry

enum class LeaderboardSortFilter(val arabicLabel: String) {
    HIGHEST_TO_LOWEST("🏆 الأعلى للأقل"),
    WEAKEST_FIRST("⚠️ الضعفاء والأقل أولاً"),
    ABSENT_ZERO_ATTENDANCE("🚫 غياب (0 حضور)"),
    ACTIVE_ONLY("✓ المتفاعلين (>0)")
}

/**
 * Dedicated Full-Screen Leaderboard & Class Points/Attendance Breakdown Screen (`FullClassLeaderboardScreen`).
 * - Replaces the old Top-5 inline card when tapping the Trophy icon on the Home Screen.
 * - Starts automatically on the month selected outside on the Home Screen (e.g., Month 10 -> sums Month 10 only).
 * - Provides a flexible period selector inside the page to combine multiple months (e.g., 10 + 11 + 12, 2 months,
 *   3 months, 5 months) OR narrow down to a single week (`أسبوع واحد فقط`).
 * - Lists ALL students in the classroom ranked with their total points and a complete per-card breakdown
 *   underneath (Attendance points/count, Quiet points/count, Participation, Praise, Gold, etc. even when 0)
 *   so servants can immediately identify weak and absent students.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullClassLeaderboardScreen(
    state: KamScreenState,
    onBackToHome: () -> Unit,
    onOpenStudentDetails: (Student) -> Unit
) {
    BackHandler(onBack = onBackToHome)

    // Initialize with the exact month selected outside on the Home Screen
    val initialMonth = state.selectedMonthKey.ifBlank { "2026-11" }
    var selectedMonths by remember(initialMonth) {
        mutableStateOf(setOf(initialMonth))
    }
    var isSingleWeekMode by rememberSaveable { mutableStateOf(false) }
    var singleWeekMonthKey by rememberSaveable(initialMonth) { mutableStateOf(initialMonth) }
    var selectedSingleWeekNumber by rememberSaveable { mutableStateOf(1) }
    var isPeriodSelectorExpanded by rememberSaveable { mutableStateOf(true) }

    var sortFilter by rememberSaveable { mutableStateOf(LeaderboardSortFilter.HIGHEST_TO_LOWEST) }
    var selectedBlockFilterId by rememberSaveable { mutableStateOf<String?>("ALL") }
    var localSearchQuery by rememberSaveable { mutableStateOf("") }

    // Build available months list (including the selected month outside, standard service months, and any months with events)
    val availableMonthKeys = remember(state.selectedMonthKey, state.effectiveAllCardEvents, state.effectiveAllMonthlyBalances) {
        val defaultServiceMonths = listOf(
            "2026-10",
            "2026-11",
            "2026-12",
            "2027-01",
            "2027-02",
            "2027-03",
            "2027-04",
            "2027-05"
        )
        val fromEvents = state.effectiveAllCardEvents.mapNotNull { it.monthKey.takeIf { m -> m.isNotBlank() } }
        val fromBalances = state.effectiveAllMonthlyBalances.mapNotNull { it.monthKey.takeIf { m -> m.isNotBlank() } }
        val currentOutside = listOfNotNull(state.selectedMonthKey.takeIf { it.isNotBlank() })
        (defaultServiceMonths + fromEvents + fromBalances + currentOutside)
            .distinct()
            .sorted()
    }

    val activeWeekKeyOverride = if (isSingleWeekMode) {
        "${singleWeekMonthKey}-W$selectedSingleWeekNumber"
    } else {
        null
    }

    // Compute full-class rankings and per-card breakdowns on Dispatchers.Default to keep UI 60fps
    val initialEntries = remember {
        state.computeClassLeaderboardEntries(
            selectedMonths = selectedMonths,
            selectedWeekOverride = activeWeekKeyOverride
        )
    }
    val allRankedEntries by produceState(
        initialValue = initialEntries,
        state.students,
        state.effectiveAllCardEvents,
        state.effectiveAllMonthlyBalances,
        state.enabledCardTypes,
        selectedMonths,
        activeWeekKeyOverride
    ) {
        value = withContext(Dispatchers.Default) {
            state.computeClassLeaderboardEntries(
                selectedMonths = selectedMonths,
                selectedWeekOverride = activeWeekKeyOverride
            )
        }
    }

    // Classroom-wide summary counts
    val totalStudentsCount = allRankedEntries.size
    val activeStudentsCount = remember(allRankedEntries) { allRankedEntries.count { it.totalPoints > 0L } }
    val absentStudentsCount = remember(allRankedEntries) { allRankedEntries.count { it.attendanceCount == 0 } }
    val zeroPointStudentsCount = remember(allRankedEntries) { allRankedEntries.count { it.totalPoints == 0L } }

    // Apply servant filters (Block filter + Search query + Sort/Filter mode)
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
            LeaderboardSortFilter.WEAKEST_FIRST -> bySearch.sortedWith(
                compareBy<StudentDetailedLeaderboardEntry> { it.totalPoints }
                    .thenBy { it.attendancePoints }
                    .thenBy { it.student.studentNumber }
            )
            LeaderboardSortFilter.ABSENT_ZERO_ATTENDANCE -> bySearch
                .filter { it.attendanceCount == 0 }
                .sortedWith(
                    compareBy<StudentDetailedLeaderboardEntry> { it.totalPoints }
                        .thenBy { it.student.studentNumber }
                )
            LeaderboardSortFilter.ACTIVE_ONLY -> bySearch.filter { it.totalPoints > 0L }
        }
    }

    val activeScopeDescription = remember(isSingleWeekMode, singleWeekMonthKey, selectedSingleWeekNumber, selectedMonths) {
        if (isSingleWeekMode) {
            "${formatArabicMonthChipLabel(singleWeekMonthKey)} • الأسبوع $selectedSingleWeekNumber فقط"
        } else {
            val sorted = selectedMonths.sorted()
            if (sorted.size == 1) {
                "نقاط ${formatArabicMonthChipLabel(sorted.first())} فقط"
            } else {
                "مجموع ${sorted.size} شهور (${sorted.joinToString(" + ") { formatShortMonthNumber(it) }})"
            }
        }
    }

    val blockById = remember(state.blocks) { state.blocks.associateBy { it.id } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("full_class_leaderboard_screen")
    ) {
        // --- 1. Top Header Bar ---
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("leaderboard_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع للواجهة الرئيسية",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ترتيب ونقاط الفصل بالكامل",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = activeScopeDescription,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Button to toggle Period Selector Card visibility
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .clickable { isPeriodSelectorExpanded = !isPeriodSelectorExpanded }
                        .testTag("toggle_leaderboard_period_selector_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "اختيار الفترة",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = if (isPeriodSelectorExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // --- 2. Scrollable Content (Period Selector + Summary Stats + Filters + All Students List) ---
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // A. Period & Scope Selector Card ("اختيار النقاط: شهر، عدة شهور، أو أسبوع")
            item(key = "period_selector_card") {
                AnimatedVisibility(visible = isPeriodSelectorExpanded) {
                    LeaderboardPeriodSelectorCard(
                        outsideSelectedMonthKey = initialMonth,
                        availableMonthKeys = availableMonthKeys,
                        selectedMonths = selectedMonths,
                        isSingleWeekMode = isSingleWeekMode,
                        singleWeekMonthKey = singleWeekMonthKey,
                        selectedSingleWeekNumber = selectedSingleWeekNumber,
                        onSwitchToMultiMonthMode = {
                            isSingleWeekMode = false
                        },
                        onSwitchToSingleWeekMode = {
                            isSingleWeekMode = true
                            singleWeekMonthKey = selectedMonths.firstOrNull() ?: initialMonth
                        },
                        onToggleMonthSelection = { monthKey ->
                            isSingleWeekMode = false
                            selectedMonths = if (monthKey in selectedMonths) {
                                if (selectedMonths.size > 1) selectedMonths - monthKey else selectedMonths
                            } else {
                                selectedMonths + monthKey
                            }
                        },
                        onSelectPresetMonths = { presetSet ->
                            isSingleWeekMode = false
                            selectedMonths = presetSet
                        },
                        onSelectSingleWeekMonth = { mKey ->
                            singleWeekMonthKey = mKey
                        },
                        onSelectSingleWeekNumber = { weekNum ->
                            selectedSingleWeekNumber = weekNum
                        }
                    )
                }
            }

            // B. Classroom Attendance & Weakness Summary Strip
            item(key = "summary_metrics_strip") {
                ClassroomHealthSummaryCard(
                    totalStudents = totalStudentsCount,
                    activeStudents = activeStudentsCount,
                    absentStudents = absentStudentsCount,
                    zeroPointStudents = zeroPointStudentsCount,
                    activeSortFilter = sortFilter,
                    onSelectSortFilter = { sortFilter = it }
                )
            }

            // C. Quick Sort/Filter Tabs + Block Filter + Search Input
            item(key = "filters_and_search_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Sort / Filter Mode Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LeaderboardSortFilter.entries.forEach { mode ->
                                val isSelected = sortFilter == mode
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    modifier = Modifier
                                        .clickable { sortFilter = mode }
                                        .testTag("leaderboard_filter_${mode.name}")
                                ) {
                                    Text(
                                        text = mode.arabicLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        // Servant Block Filter Chips
                        if (state.blocks.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BlockFilterChip(
                                    label = "كل الفصل (${allRankedEntries.size})",
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
                        }

                        // Compact Search Input inside Leaderboard
                        OutlinedTextField(
                            value = localSearchQuery,
                            onValueChange = { localSearchQuery = it },
                            placeholder = { Text("ابحث باسم الطالب أو رقمه داخل الترتيب...") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (localSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { localSearchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "مسح البحث", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("leaderboard_student_search_input")
                        )
                    }
                }
            }

            // D. Full Student List with Detailed Per-Card Points & Counts Breakdown
            if (filteredEntries.isEmpty()) {
                item(key = "empty_leaderboard_state") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "لا يوجد طلاب مطابقون للفلتر المختار حالياً",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "جرب اختيار «الأعلى للأقل» أو «كل الفصل» لعرض جميع الطلاب.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredEntries,
                    key = { it.student.id }
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

/**
 * Interactive Period & Multi-Month / Single-Week Selector Card inside the Leaderboard Screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeaderboardPeriodSelectorCard(
    outsideSelectedMonthKey: String,
    availableMonthKeys: List<String>,
    selectedMonths: Set<String>,
    isSingleWeekMode: Boolean,
    singleWeekMonthKey: String,
    selectedSingleWeekNumber: Int,
    onSwitchToMultiMonthMode: () -> Unit,
    onSwitchToSingleWeekMode: () -> Unit,
    onToggleMonthSelection: (String) -> Unit,
    onSelectPresetMonths: (Set<String>) -> Unit,
    onSelectSingleWeekMonth: (String) -> Unit,
    onSelectSingleWeekNumber: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leaderboard_period_selector_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "اختيار النقاط والفترة الزمنية (شهر • عدة شهور • أسبوع واحد):",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            // Mode Toggle: Multi-Month vs Single Week
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (!isSingleWeekMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onSwitchToMultiMonthMode)
                        .testTag("mode_multi_month_button")
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📅 شهر أو عدة شهور معاً",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (!isSingleWeekMode) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSingleWeekMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onSwitchToSingleWeekMode)
                        .testTag("mode_single_week_button")
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⏱️ أسبوع واحد فقط",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSingleWeekMode) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!isSingleWeekMode) {
                // Quick Multi-Month Presets (1 month, 2 months, 3 months, 5 months, all months)
                Text(
                    text = "اختصارات سريعة لدمج الشهور أو اضغط على الشهور بالأسفل لتحديدها معاً:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val anchorIdx = availableMonthKeys.indexOf(outsideSelectedMonthKey).coerceAtLeast(0)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PresetDurationChip(
                        label = "الشهر المختار بالخارج فقط",
                        isSelected = selectedMonths == setOf(outsideSelectedMonthKey),
                        onClick = { onSelectPresetMonths(setOf(outsideSelectedMonthKey)) },
                        testTag = "preset_single_outside_month"
                    )
                    PresetDurationChip(
                        label = "شهرين (2)",
                        isSelected = selectedMonths.size == 2,
                        onClick = {
                            val slice = availableMonthKeys.drop(anchorIdx).take(2).ifEmpty {
                                availableMonthKeys.takeLast(2)
                            }
                            onSelectPresetMonths(slice.toSet())
                        },
                        testTag = "preset_two_months"
                    )
                    PresetDurationChip(
                        label = "3 شهور (مثل 10+11+12)",
                        isSelected = selectedMonths.size == 3,
                        onClick = {
                            val slice = availableMonthKeys.take(3)
                            onSelectPresetMonths(slice.toSet())
                        },
                        testTag = "preset_three_months"
                    )
                    PresetDurationChip(
                        label = "5 شهور",
                        isSelected = selectedMonths.size == 5,
                        onClick = {
                            val slice = availableMonthKeys.take(5)
                            onSelectPresetMonths(slice.toSet())
                        },
                        testTag = "preset_five_months"
                    )
                    PresetDurationChip(
                        label = "كل الشهور",
                        isSelected = selectedMonths.size == availableMonthKeys.size && availableMonthKeys.size > 1,
                        onClick = { onSelectPresetMonths(availableMonthKeys.toSet()) },
                        testTag = "preset_all_months"
                    )
                }

                // Multi-Select Month Chips (Tap to toggle any combination of months e.g. 10, 11, 12)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableMonthKeys.forEach { mKey ->
                        val isSelected = mKey in selectedMonths
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            },
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                }
                            ),
                            modifier = Modifier
                                .clickable { onToggleMonthSelection(mKey) }
                                .testTag("leaderboard_month_chip_$mKey")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = formatArabicMonthChipLabel(mKey),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            } else {
                // Single-Week Mode Selector
                Text(
                    text = "اختر الشهر ثم الأسبوع المطلوب عرض نقاطه فقط:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableMonthKeys.forEach { mKey ->
                        val isSelected = mKey == singleWeekMonthKey
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .clickable { onSelectSingleWeekMonth(mKey) }
                                .testTag("single_week_month_chip_$mKey")
                        ) {
                            Text(
                                text = formatArabicMonthChipLabel(mKey),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..5).forEach { weekNum ->
                        val isSelected = selectedSingleWeekNumber == weekNum
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) {
                                Color(0xFFF59E0B)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectSingleWeekNumber(weekNum) }
                                .testTag("single_week_number_chip_$weekNum")
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "أسبوع $weekNum",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) Color(0xFF0F172A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetDurationChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0xFFF59E0B) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) Color(0xFF0F172A) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
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
        color = if (selected) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accentColor else Color.Transparent
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
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
            modifier = Modifier.weight(1f)
        )
        SummaryStatPill(
            title = "متفاعلين",
            value = "$activeStudents",
            accentColor = Color(0xFF10B981),
            isSelected = activeSortFilter == LeaderboardSortFilter.ACTIVE_ONLY,
            onClick = { onSelectSortFilter(LeaderboardSortFilter.ACTIVE_ONLY) },
            modifier = Modifier.weight(1f)
        )
        SummaryStatPill(
            title = "غائبين (0 حضور)",
            value = "$absentStudents",
            accentColor = Color(0xFFEF4444),
            isSelected = activeSortFilter == LeaderboardSortFilter.ABSENT_ZERO_ATTENDANCE,
            onClick = { onSelectSortFilter(LeaderboardSortFilter.ABSENT_ZERO_ATTENDANCE) },
            modifier = Modifier.weight(1f)
        )
        SummaryStatPill(
            title = "ضعفاء (0 نقطة)",
            value = "$zeroPointStudents",
            accentColor = Color(0xFFF59E0B),
            isSelected = activeSortFilter == LeaderboardSortFilter.WEAKEST_FIRST,
            onClick = { onSelectSortFilter(LeaderboardSortFilter.WEAKEST_FIRST) },
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
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accentColor else accentColor.copy(alpha = 0.35f)
        ),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accentColor,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Detailed Student Card inside the Full-Class Leaderboard:
 * - Row 1: Rank badge (#1, #2, #3...) + Avatar + Full Student Name (#number) + Servant Block + Total Points Pill.
 * - Row 2: Complete breakdown of every CardType showing exact points and number of times awarded
 *   (e.g., "✓ الحضور: 9 نقاط (3)" or "✓ الحضور: 0 نقطة (غائب)") so servants immediately see weak/absent students.
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
        shape = RoundedCornerShape(16.dp),
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        shape = RoundedCornerShape(10.dp),
                        color = rankColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, rankColor.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = rankBadgeText,
                            style = MaterialTheme.typography.labelLarge,
                            color = rankColor,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    StudentAvatarCircle(
                        student = student,
                        blockAccent = blockAccent,
                        size = 42.dp,
                        onClick = onClick,
                        testTag = "leaderboard_avatar_${student.id}",
                        blockLabel = ownerBlock?.let { "بلوك ${it.servantName}" } ?: "بدون بلوك"
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = student.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "رقم #${student.studentNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
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
                    shape = RoundedCornerShape(12.dp),
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
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${entry.totalPoints} نقطة",
                            style = MaterialTheme.typography.titleSmall,
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // Bottom Section: Detailed Points & Count for Every Card Type (Attendance, Quiet, Participation, Praise, Gold...)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
        "${item.cardType.badgeSymbol} ${item.cardType.arabicTitle}: 0 نقطة (غائب)"
    } else if (item.count == 0) {
        "${item.cardType.badgeSymbol} ${item.cardType.arabicTitle}: 0 نقطة (0)"
    } else {
        "${item.cardType.badgeSymbol} ${item.cardType.arabicTitle}: ${item.points} نقطة (${item.count})"
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = detailText,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = if (hasPoints || isAttendanceZero) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun formatArabicMonthChipLabel(monthKey: String): String {
    val parts = monthKey.split("-")
    val year = parts.getOrNull(0) ?: ""
    val monthNum = parts.getOrNull(1)?.toIntOrNull() ?: return monthKey
    val arabicMonthName = when (monthNum) {
        1 -> "يناير"
        2 -> "فبراير"
        3 -> "مارس"
        4 -> "أبريل"
        5 -> "مايو"
        6 -> "يونيو"
        7 -> "يوليو"
        8 -> "أغسطس"
        9 -> "سبتمبر"
        10 -> "أكتوبر"
        11 -> "نوفمبر"
        12 -> "ديسمبر"
        else -> "شهر $monthNum"
    }
    return "شهر $monthNum ($arabicMonthName $year)"
}

private fun formatShortMonthNumber(monthKey: String): String {
    val monthNum = monthKey.substringAfter("-", monthKey).toIntOrNull()
    return if (monthNum != null) "شهر $monthNum" else monthKey
}
