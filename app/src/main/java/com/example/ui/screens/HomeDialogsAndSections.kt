package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CardEvent
import com.example.data.model.CardType
import com.example.data.model.DuplicateCardAlert
import com.example.data.model.DuplicateStudentNameGroup
import com.example.data.model.SaturdayCalendarHelper
import com.example.data.model.ServantBlock
import com.example.data.model.Student
import com.example.ui.components.CompactCardButtonsRow
import com.example.ui.components.StudentAvatarCircle
import com.example.ui.theme.KamSemanticColors
import com.example.ui.viewmodel.KamScreenState
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Dedicated "نتائج البحث" section rendered ABOVE all Servant Blocks when searching.
 * Shows matching children even if their Servant Block is collapsed, displaying:
 * - Child Name + Number
 * - Servant Name (owner of the child's block)
 * - Enabled Card buttons for fast 1-tap recording
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchResultsWithBlockOwnerSection(
    query: String,
    matchingStudents: List<Student>,
    blocks: List<ServantBlock>,
    enabledCards: List<CardType>,
    state: KamScreenState,
    onClearSearch: () -> Unit,
    onAwardCard: (Student, CardType) -> Unit,
    onOpenMoveDialog: (Student) -> Unit,
    onOpenDetailsDialog: (Student) -> Unit,
    onOpenPhotoPicker: (Student) -> Unit = {}
) {
    val blockById = remember(blocks) { blocks.associateBy { it.id } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("search_results_section"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Search Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "نتائج البحث (${matchingStudents.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "تظهر هنا فوق كل البلوكات حتى لو البلوك مقفول • بحث عن: \"$query\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .clickable(onClick = onClearSearch)
                        .testTag("clear_search_section_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "مسح البحث",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "مسح البحث",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (matchingStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا يوجد طفل مطابق لـ \"$query\" في أي بلوك.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    matchingStudents.forEach { student ->
                        val ownerBlock = blockById[student.blockId]
                        val blockAccent = if (ownerBlock != null) {
                            KamSemanticColors.blockColor(ownerBlock.colorHex)
                        } else {
                            KamSemanticColors.unassignedBlock
                        }
                        val servantOwnerLabel = if (ownerBlock != null) {
                            "بلوك الخادم: ${ownerBlock.servantName}"
                        } else {
                            "بدون بلوك"
                        }
                        val pointsInScope = state.pointsForStudentInDisplayedScope(student.id)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_result_row_${student.id}"),
                            shape = RoundedCornerShape(16.dp),
                            color = KamSemanticColors.elevatedSurface,
                            border = BorderStroke(1.dp, blockAccent.copy(alpha = 0.55f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Line 1: Full-width Avatar + Child Name
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StudentAvatarCircle(
                                        student = student,
                                        blockAccent = blockAccent,
                                        size = 38.dp,
                                        onClick = { onOpenPhotoPicker(student) },
                                        testTag = "search_student_avatar_${student.id}",
                                        blockLabel = servantOwnerLabel
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Text(
                                        text = student.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onOpenDetailsDialog(student) }
                                    )
                                }

                                // Line 2: Number + Servant Owner Badge + Points Pill + Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    FlowRow(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "رقم #${student.studentNumber}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )

                                        // Servant Block Owner Pill
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = blockAccent.copy(alpha = 0.22f),
                                            border = BorderStroke(1.dp, blockAccent)
                                        ) {
                                            Text(
                                                text = servantOwnerLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }

                                        // Points Pill
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "$pointsInScope نقطة",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onOpenMoveDialog(student) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                                contentDescription = "نقل لبلوك آخر",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onOpenDetailsDialog(student) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = "سجل وتفاصيل الطفل",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // Row 2: Compact 44x44dp Card Buttons (Symbol only -> transforms to points number on tap)
                                CompactCardButtonsRow(
                                    studentId = student.id,
                                    enabledCards = enabledCards,
                                    packedCardCounts = state.packedCardCountsForStudent(student.id),
                                    tagPrefix = "search_card_btn",
                                    onCardClick = { card -> onAwardCard(student, card) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hierarchical Year ← Month ← Week Tree Dialog:
 * - Future months that haven't started yet are rendered gray and non-expandable ("رمادية ومش بتتفتح").
 * - Current and past months expand to show their Saturday-based weeks (starting Saturday 12:00 AM midnight).
 * - Works 100% offline from the device clock.
 * - Current week is marked ("الأسبوع الحالي ★"), and tapping any week displays that week's cards.
 */
@Composable
fun YearMonthWeekTreeDialog(
    selectedMonthKey: String,
    selectedWeekKey: String?,
    onDismiss: () -> Unit,
    onSelectMonthAndWeek: (monthKey: String, weekKey: String?) -> Unit
) {
    val availableYears = remember { SaturdayCalendarHelper.availableYears() }
    val initialYear = remember(selectedMonthKey) {
        selectedMonthKey.substringBefore("-").toIntOrNull()
            ?: availableYears.firstOrNull()
            ?: 2026
    }

    var expandedYear by rememberSaveable { mutableIntStateOf(initialYear) }
    var expandedMonthKey by rememberSaveable { mutableStateOf(selectedMonthKey) }

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
                        text = "شجرة السنة والشهور والأسابيع",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "بداية الأسبوع: السبت ١٢:٠٠ ص • يبدأ التقويم من شهر ١١ (نوفمبر ٢٠٢٦) مع الشهور القبطية",
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
                availableYears.forEach { year ->
                    val yearTree = remember(year) { SaturdayCalendarHelper.buildYearTree(year) }
                    val isYearExpanded = expandedYear == year

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tree_year_$year"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Level 1: Year Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedYear = if (isYearExpanded) -1 else year
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
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

                            // Level 2: Months under Year
                            AnimatedVisibility(visible = isYearExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    yearTree.months.forEach { monthNode ->
                                        val isMonthExpanded = expandedMonthKey == monthNode.monthKey && !monthNode.isFutureMonth
                                        val isMonthSelected = selectedMonthKey == monthNode.monthKey

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = when {
                                                monthNode.isFutureMonth -> MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                                                isMonthSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                                else -> MaterialTheme.colorScheme.surface
                                            },
                                            border = BorderStroke(
                                                width = if (isMonthSelected && !monthNode.isFutureMonth) 1.5.dp else 1.dp,
                                                color = when {
                                                    monthNode.isFutureMonth -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                                    isMonthSelected -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                                }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(
                                                    if (monthNode.isFutureMonth) {
                                                        // Future months: gray & non-expandable ("رمادية ومش بتتفتح")
                                                        Modifier
                                                    } else {
                                                        Modifier.clickable {
                                                            expandedMonthKey = if (isMonthExpanded) "" else monthNode.monthKey
                                                        }
                                                    }
                                                )
                                                .testTag("tree_month_${monthNode.monthKey}")
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Text(
                                                                text = "${SaturdayCalendarHelper.toArabicDigits(monthNode.monthNumber)}. ${monthNode.arabicMonthName}",
                                                                style = MaterialTheme.typography.titleSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (monthNode.isFutureMonth) {
                                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                                                } else {
                                                                    MaterialTheme.colorScheme.onSurface
                                                                }
                                                            )

                                                            if (monthNode.isCurrentMonth) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    color = MaterialTheme.colorScheme.primary
                                                                ) {
                                                                    Text(
                                                                        text = "الشهر الحالي",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        if (monthNode.copticMonthsLabel.isNotBlank()) {
                                                            Text(
                                                                text = "يقابله قبطياً: ${monthNode.copticMonthsLabel}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = if (monthNode.isFutureMonth) {
                                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                                                } else {
                                                                    MaterialTheme.colorScheme.primary
                                                                },
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }
                                                    }

                                                    if (monthNode.isFutureMonth) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = "لم يبدأ",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Icon(
                                                                imageVector = Icons.Default.Lock,
                                                                contentDescription = "شهر قادم مقفول",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    } else {
                                                        Icon(
                                                            imageVector = if (isMonthExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }

                                                // Level 3: Weeks inside expanded Month
                                                AnimatedVisibility(visible = isMonthExpanded && !monthNode.isFutureMonth) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(start = 14.dp, end = 10.dp, bottom = 10.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        HorizontalDivider()

                                                        // Option to view all weeks of this month combined
                                                        val isAllWeeksSelected = isMonthSelected && selectedWeekKey.isNullOrBlank()
                                                        Surface(
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = if (isAllWeeksSelected) {
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                            } else {
                                                                MaterialTheme.colorScheme.surfaceVariant
                                                            },
                                                            border = if (isAllWeeksSelected) {
                                                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                                            } else {
                                                                null
                                                            },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    onSelectMonthAndWeek(monthNode.monthKey, null)
                                                                    onDismiss()
                                                                }
                                                                .testTag("tree_all_weeks_${monthNode.monthKey}")
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = "كل أسابيع شهر ${monthNode.arabicMonthName} (إجمالي الشهر)",
                                                                    style = MaterialTheme.typography.labelLarge,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                                if (isAllWeeksSelected) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.CheckCircle,
                                                                        contentDescription = null,
                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        monthNode.weeks.forEach { weekNode ->
                                                            val isWeekSelected = isMonthSelected && selectedWeekKey == weekNode.weekKey
                                                            Surface(
                                                                shape = RoundedCornerShape(10.dp),
                                                                color = when {
                                                                    isWeekSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                                                    weekNode.isCurrentWeek -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                                },
                                                                border = when {
                                                                    isWeekSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                                                    weekNode.isCurrentWeek -> BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                                                                    else -> null
                                                                },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clickable {
                                                                        onSelectMonthAndWeek(monthNode.monthKey, weekNode.weekKey)
                                                                        onDismiss()
                                                                    }
                                                                    .testTag("tree_week_${weekNode.weekKey}")
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Column(
                                                                        modifier = Modifier.weight(1f),
                                                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                                                    ) {
                                                                        Row(
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = "↳ ${weekNode.arabicTitle}",
                                                                                style = MaterialTheme.typography.labelLarge,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = MaterialTheme.colorScheme.onSurface
                                                                            )
                                                                            if (weekNode.isCurrentWeek) {
                                                                                Surface(
                                                                                    shape = RoundedCornerShape(6.dp),
                                                                                    color = MaterialTheme.colorScheme.secondary
                                                                                ) {
                                                                                    Text(
                                                                                        text = "الأسبوع الحالي ★",
                                                                                        style = MaterialTheme.typography.labelSmall,
                                                                                        color = MaterialTheme.colorScheme.onSecondary,
                                                                                        fontWeight = FontWeight.Bold,
                                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                        Text(
                                                                            text = "ميلادي: السبت ١٢:٠٠ ص (${weekNode.dateRangeLabel})",
                                                                            style = MaterialTheme.typography.bodySmall,
                                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                        )
                                                                        if (weekNode.copticWeekLabel.isNotBlank()) {
                                                                            Text(
                                                                                text = "قبطي: ${weekNode.copticWeekLabel}",
                                                                                style = MaterialTheme.typography.bodySmall,
                                                                                color = MaterialTheme.colorScheme.primary,
                                                                                fontWeight = FontWeight.SemiBold
                                                                            )
                                                                        }
                                                                        // Distinctive Coptic Month Transition Badge when a new Coptic month starts mid-week
                                                                        weekNode.copticTransitionBadge?.let { transitionBadgeText ->
                                                                            Surface(
                                                                                shape = RoundedCornerShape(8.dp),
                                                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                                                                modifier = Modifier
                                                                                    .padding(top = 2.dp)
                                                                                    .testTag("coptic_transition_badge_${weekNode.weekKey}")
                                                                            ) {
                                                                                Text(
                                                                                    text = transitionBadgeText,
                                                                                    style = MaterialTheme.typography.labelSmall,
                                                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                                    fontWeight = FontWeight.Bold,
                                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                                                )
                                                                            }
                                                                        }
                                                                    }

                                                                    if (isWeekSelected) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.CheckCircle,
                                                                            contentDescription = "محدد حالياً",
                                                                            tint = MaterialTheme.colorScheme.primary,
                                                                            modifier = Modifier.size(18.dp)
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}

/**
 * In-App Duplicate Card Alerts Dialog (opened via the Bell + Badge icon in the header).
 * Trigger condition: Same child + Same card type + 2 different servants in the same week.
 * Exact text format: "الطفل [اسم] أخد كارت [اسم الكارت] مرتين: [خادم 1] و[خادم 2]"
 */
@Composable
fun DuplicateCardAlertsDialog(
    alerts: List<DuplicateCardAlert>,
    isAdmin: Boolean,
    currentUserId: String,
    canServantUndoOwnEvents: Boolean,
    onDismiss: () -> Unit,
    onUndoCardEvent: (CardEvent) -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("dd/MM hh:mm a", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تنبيهات الكروت المكررة (${alerts.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (alerts.isEmpty()) {
                    Text(
                        text = "لا توجد أي كروت مكررة بين الخدام في الفترة المحددة حالياً.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "يظهر هذا التنبيه تلقائياً بعد المزامنة عند تسجيل نفس الكارت لنفس الطفل من خادمين مختلفين في نفس الأسبوع:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    alerts.forEach { alert ->
                        val cardColor = KamSemanticColors.cardColor(alert.cardType)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("duplicate_alert_item_${alert.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = alert.formattedMessage,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )

                                alert.events.forEach { evt ->
                                    val canUndo = isAdmin || (canServantUndoOwnEvents && evt.servantId == currentUserId)
                                    val timeStr = evt.createdAt?.toDate()?.let { timeFormatter.format(it) } ?: "محفوظ"
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${alert.cardType.badgeSymbol} ${alert.cardType.arabicTitle} • الخادم: ${evt.servantName}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = cardColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "التوقيت: $timeStr",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (canUndo) {
                                            TextButton(
                                                onClick = { onUndoCardEvent(evt) },
                                                modifier = Modifier.testTag("undo_duplicate_event_${evt.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                                    contentDescription = "إلغاء الكارت المكرر",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "إلغاء",
                                                    color = MaterialTheme.colorScheme.error,
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}

/**
 * Side-by-side manual comparison dialog for duplicate student names discovered during
 * migration or simultaneous additions within the same group.
 * Allows choosing which student record to keep (merging any card events into it)
 * or keeping both as distinct students.
 */
@Composable
fun DuplicateStudentComparisonDialog(
    duplicateGroups: List<DuplicateStudentNameGroup>,
    blocks: List<ServantBlock>,
    state: KamScreenState,
    onKeepStudentAndMerge: (keepStudent: Student, removeStudent: Student) -> Unit,
    onKeepBothAsSeparate: (normalizedName: String) -> Unit,
    onDismiss: () -> Unit
) {
    val blockNameById = remember(blocks) { blocks.associate { it.id to it.servantName } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "أسماء أطفال متطابقة (${duplicateGroups.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (duplicateGroups.isEmpty()) {
                    Text(
                        text = "لا توجد أسماء أطفال مكررة تحتاج إلى مراجعة حالياً.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "تم اكتشاف أسماء متطابقة داخل المجموعة. قارن بين السجلين واختر السجل المراد الاحتفاظ به (سيتم دمج جميع الكروت والنقاط تلقائياً دون فقدان أي كارت)، أو احتفظ بهما كطفلين منفصلين:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    duplicateGroups.forEach { group ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("duplicate_name_group_${group.normalizedName}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "الاسم المتطابق: \"${group.displayName}\" (${group.records.size} سجلات)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                group.records.forEachIndexed { idx, candidate ->
                                    val blockLabel = if (candidate.blockId.isBlank()) {
                                        "بدون بلوك"
                                    } else {
                                        "بلوك ${blockNameById[candidate.blockId] ?: "خادم"}"
                                    }
                                    val candidatePoints = state.pointsForStudentInSelectedMonth(candidate.id)
                                    val candidateEventsCount = state.eventsForStudent(candidate.id).size
                                    val otherCandidates = group.records.filter { it.id != candidate.id }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "سجل ${idx + 1}: ${candidate.name} (رقم #${candidate.studentNumber})",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "$blockLabel • النقاط: $candidatePoints • الكروت: $candidateEventsCount",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            if (otherCandidates.isNotEmpty()) {
                                                Button(
                                                    onClick = {
                                                        otherCandidates.forEach { duplicate ->
                                                            onKeepStudentAndMerge(candidate, duplicate)
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("keep_duplicate_student_${candidate.id}"),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text("الاحتفاظ بهذا السجل (#${candidate.studentNumber}) ودمج الكروت")
                                                }
                                            }
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { onKeepBothAsSeparate(group.normalizedName) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("keep_both_students_${group.normalizedName}"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("الإبقاء على الاسمين كطفلين منفصلين")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}

